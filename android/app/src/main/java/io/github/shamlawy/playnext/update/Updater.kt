package io.github.shamlawy.playnext.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.shamlawy.playnext.BuildConfig
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.net.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * The app updates itself from GitHub releases: check, download (resumable), verify (SHA-256, same signer, higher
 * versionCode), then install while the app isn't on screen. Android 12+ lets an app update itself with no tap; when
 * Android still asks, it's one tap the next time the app is open.
 */
object Updater {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Downloading(val code: Int, val pct: Int) : State
        data class Waiting(val code: Int) : State // installs as soon as the app goes to the background
        data class NeedsTap(val code: Int) : State
        data class Blocked(val why: String) : State
        data class Failed(val why: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val busy = Mutex()
    private const val API = "https://api.github.com/repos/${BuildConfig.REPO}/releases?per_page=10"
    private const val LATEST_TXT = "${ReleaseFeed.REPO_URL}/releases/latest/download/android-latest.txt"

    internal fun set(s: State) { _state.value = s }

    /** The every-6-hours check (WorkManager), the backstop when the app isn't opened. */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("update", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** At start and on coming back after 10+ min: never blocks a screen, never installs while the app is in use. */
    suspend fun checkInBackground(ctx: Context, force: Boolean = false) {
        val last = Store.prefs.getLong("updChecked", 0L)
        if (!force && System.currentTimeMillis() - last < 10 * 60_000L) return
        run(ctx, now = false)
    }

    /** Safe mode: install at once (the owner is looking, so a confirm dialog can show). */
    suspend fun installNowFromSafeMode(ctx: Context): String {
        run(ctx, now = true)
        return when (val s = state.value) {
            State.UpToDate -> "No update yet. Claude is told about the problem through the report."
            is State.Waiting, is State.NeedsTap -> "Installing v${(s as? State.Waiting)?.code ?: (s as State.NeedsTap).code}…"
            is State.Blocked -> s.why
            is State.Failed -> "Couldn't update: ${s.why}"
            else -> "Looking…"
        }
    }

    suspend fun run(ctx: Context, now: Boolean) = busy.withLock {
        set(State.Checking)
        try {
            val rel = find()
            Store.prefs.edit().putLong("updChecked", System.currentTimeMillis()).apply()
            if (rel == null) {
                set(State.UpToDate)
                return@withLock
            }
            val apk = download(ctx, rel)
            verify(ctx, apk, rel.code)?.let { why -> apk.delete(); set(State.Failed(why)); return@withLock }
            Install.start(ctx, apk, rel.code, now)
        } catch (e: IOException) {
            set(State.Failed(e.message ?: "no connection"))
        }
    }

    /** The newest android-vN above this one: the API with its ETag first (a 304 costs nothing), else the text asset. */
    private suspend fun find(): Release? {
        val etag = Store.prefs.getString("relEtag", null)
        val api = Request.Builder().url(API).header("Accept", "application/vnd.github+json")
            .apply { if (etag != null) header("If-None-Match", etag) }.build()
        val body: String? = runCatching {
            Net.call(api) { r ->
                when {
                    r.code == 304 -> Store.prefs.getString("relBody", null)
                    r.isSuccessful -> r.body.string().also { b ->
                        Store.prefs.edit().putString("relEtag", r.header("ETag")).putString("relBody", b).apply()
                    }
                    else -> null // 403/429: the API is busy, use the text asset
                }
            }
        }.getOrNull()
        val current = BuildConfig.VERSION_CODE
        if (body != null) return ReleaseFeed.pick(body, current)
        val txt = Net.call(Request.Builder().url(LATEST_TXT).build()) { r -> if (r.isSuccessful) r.body.string() else null } ?: return null
        return ReleaseFeed.pickLatestTxt(txt, current)
    }

    /** Resumes a part-downloaded file; the hash comes from the .sha256 asset (the source of truth) or the release. */
    private suspend fun download(ctx: Context, rel: Release): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.filesDir, "update").apply { mkdirs() }
        dir.listFiles()?.filter { !it.name.contains("-v${rel.code}.") }?.forEach { it.delete() }
        val done = File(dir, ReleaseFeed.apkName(rel.code))
        val sha = rel.shaUrl?.let { u -> Net.call(Request.Builder().url(u).build()) { r -> if (r.isSuccessful) ReleaseFeed.shaOf(r.body.string()) else null } }
            ?: rel.sha256 ?: throw IOException("the release has no SHA-256")
        if (done.exists() && sha256(done) == sha) return@withContext done
        val part = File(dir, done.name + ".part")
        val from = if (part.exists()) part.length() else 0L
        val req = Request.Builder().url(rel.apkUrl).apply { if (from > 0) header("Range", "bytes=$from-") }.build()
        Net.call(req) { r ->
            if (!r.isSuccessful) throw IOException("download ${r.code}")
            val append = from > 0 && r.code == 206
            val total = if (rel.size > 0) rel.size else (r.body.contentLength() + if (append) from else 0)
            java.io.FileOutputStream(part, append).use { out ->
                val src = r.body.byteStream()
                val buf = ByteArray(64 * 1024)
                var got = if (append) from else 0L
                var lastPct = -1
                while (true) {
                    val n = src.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    got += n
                    val pct = if (total > 0) (got * 100 / total).toInt() else 0
                    if (pct != lastPct) { lastPct = pct; set(State.Downloading(rel.code, pct)) }
                }
            }
        }
        if (sha256(part) != sha) { part.delete(); throw IOException("the download didn't match its SHA-256") }
        part.renameTo(done)
        done
    }

    /** Null when the file is safe to install: same package, same signer, higher versionCode. */
    fun verify(ctx: Context, apk: File, code: Int): String? {
        val pm = ctx.packageManager
        val info = pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES) ?: return "the file isn't an app"
        if (info.packageName != ctx.packageName) return "the file is another app"
        if (info.longVersionCode <= BuildConfig.VERSION_CODE || info.longVersionCode != code.toLong()) return "the file isn't a newer version"
        val mine = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray().sha() }.toSet()
        val theirs = info.signingInfo?.apkContentsSigners.orEmpty().map { it.toByteArray().sha() }.toSet()
        if (mine.isEmpty() || mine != theirs) return "the file was signed by a different key"
        return null
    }

    private fun ByteArray.sha() = MessageDigest.getInstance("SHA-256").digest(this).joinToString("") { "%02x".format(it) }

    fun sha256(f: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s -> val b = ByteArray(64 * 1024); while (true) { val n = s.read(b); if (n < 0) break; md.update(b, 0, n) } }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Android may need "Install unknown apps" for Play next once; Home shows a button when it's off. */
    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()
}

/** The PackageInstaller session. Never mid-use: Android installs after the app leaves the screen. */
internal object Install {
    private const val DAY = 24 * 60 * 60_000L

    fun start(ctx: Context, apk: File, code: Int, now: Boolean) {
        val pi = ctx.packageManager.packageInstaller
        pi.mySessions.forEach { runCatching { pi.abandonSession(it.sessionId) } }
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(ctx.packageName)
            setSize(apk.length())
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER)
            setInstallReason(PackageManager.INSTALL_REASON_USER)
        }
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
        }
        val sender = PendingIntent.getBroadcast(
            ctx, id, Intent(ctx, InstallResultReceiver::class.java).putExtra("code", code),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        ).intentSender
        Store.prefs.edit().putInt("updSession", id).putInt("updCode", code).apply()
        if (now) {
            pi.openSession(id).use { it.commit(sender) }
            return
        }
        Updater.set(Updater.State.Waiting(code))
        try {
            val c = PackageInstaller.InstallConstraints.Builder().setAppNotForegroundRequired().build()
            pi.commitSessionAfterInstallConstraintsAreMet(id, sender, c, DAY)
        } catch (e: SecurityException) {
            // Not the installer of record: commit ourselves the moment the app goes to the background.
            OnBackground.commit(ctx, id, sender)
        }
    }

    private object OnBackground {
        fun commit(ctx: Context, id: Int, sender: android.content.IntentSender) {
            val app = ctx.applicationContext
            val owner = ProcessLifecycleOwner.get()
            owner.lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onStop(o: LifecycleOwner) {
                    owner.lifecycle.removeObserver(this)
                    runCatching { app.packageManager.packageInstaller.openSession(id).use { it.commit(sender) } }
                }
            })
        }
    }
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Updater.run(applicationContext, now = false)
        return Result.success()
    }
}
