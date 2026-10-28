package io.github.shamlawy.playnext.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.shamlawy.playnext.BuildConfig
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.crash.swapIn
import io.github.shamlawy.playnext.net.Net
import io.github.shamlawy.playnext.report.Reporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * What to do with a verified download. Android lets Play next update itself with no tap only when Play next installed
 * the copy that's there (the installer of record); the first install came from Chrome, so the first self-update needs
 * Android's one tap, and Android can only show it while the app is on screen. android-v1 waited for the background and
 * looped (Oct 27): a tap-needing update now asks right away when the owner tapped, or at the next open.
 */
object UpdatePlan {
    /** Who started the run: a check by itself, Check now, or the owner asking to install (Install, an open, safe mode). */
    enum class Ask { Background, Check, Install }

    enum class Next { InstallNow, WhenLeft, TapNextOpen }

    fun next(ask: Ask, selfOwned: Boolean): Next = when {
        ask == Ask.Install -> Next.InstallNow
        selfOwned -> Next.WhenLeft
        ask == Ask.Check -> Next.InstallNow
        else -> Next.TapNextOpen
    }

    /** Opens with an update still not installed before it's reported as stuck. */
    const val STUCK_OPENS = 3
}

/**
 * The app updates itself from GitHub releases: check, download (resumable), verify (SHA-256, same signer, higher
 * versionCode), then install (UpdatePlan): silently once the app is off screen when Play next installed its own copy,
 * else with Android's one tap while the app is open, never mid-use.
 */
object Updater {
    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Downloading(val code: Int, val pct: Int) : State
        data class Waiting(val code: Int) : State // installs as soon as the app goes to the background
        data class NeedsTap(val code: Int) : State
        data class Installing(val code: Int) : State // Android's install screen is up (or the install is running)
        data class Blocked(val why: String) : State
        data class Failed(val why: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private val busy = Mutex()

    /** A run (a check, a download, an install) holds the lock: Check now says so instead of waiting on it in silence. */
    val running: Boolean get() = busy.isLocked
    private const val API = "https://api.github.com/repos/${BuildConfig.REPO}/releases?per_page=10"
    private const val LATEST_TXT = "${ReleaseFeed.REPO_URL}/releases/latest/download/android-latest.txt"

    internal fun set(s: State) { _state.value = s }

    /** Android wants its one tap: kept across restarts, asked for at the next open (once per version), then in Settings. */
    internal fun needsTap(code: Int) {
        Store.prefs.edit().putInt("updTap", code).apply()
        set(State.NeedsTap(code))
    }

    /** Android installs only the copy that Play next itself installed with no tap (Chrome made the first one). */
    fun selfOwned(ctx: Context): Boolean = runCatching {
        ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName == ctx.packageName
    }.getOrDefault(false)

    /**
     * Every open (MainActivity.onStart): an update waiting for Android's tap asks for it now, at the open, once per
     * version; one still not installed after STUCK_OPENS opens files a report by itself (the loop the owner found in
     * android-v1). Then the usual check.
     */
    suspend fun onOpen(ctx: Context) {
        val p = Store.prefs
        val me = BuildConfig.VERSION_CODE
        val pend = p.getInt("updPend", 0)
        if (pend > me) {
            val opens = p.getInt("updPendOpens", 0) + 1
            p.edit().putInt("updPendOpens", opens).apply()
            if (opens >= UpdatePlan.STUCK_OPENS && p.getInt("updPendSaid", 0) != pend) {
                p.edit().putInt("updPendSaid", pend).apply()
                Reporter.report("error", "The update to v$pend hasn't installed after $opens opens", note = why(ctx))
            }
        }
        val tap = p.getInt("updTap", 0)
        if (tap > me && p.getInt("updTapAsked", 0) != tap) {
            p.edit().putInt("updTapAsked", tap).apply()
            run(ctx, UpdatePlan.Ask.Install)
            return
        }
        if (tap > me) set(State.NeedsTap(tap))
        checkInBackground(ctx)
    }

    private fun why(ctx: Context): String {
        val by = runCatching { ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName }.getOrNull()
        return "state ${state.value}, installed by ${by ?: "unknown"}, install unknown apps ${canInstall(ctx)}"
    }

    private fun pending(code: Int) {
        val p = Store.prefs
        if (p.getInt("updPend", 0) != code) p.edit().putInt("updPend", code).putInt("updPendOpens", 0).apply()
    }

    /** The every-6-hours check (WorkManager), the backstop when the app isn't opened. */
    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<UpdateWorker>(6, TimeUnit.HOURS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("update", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** At start and on coming back after 10+ min: never blocks a screen, never installs while the app is in use. */
    suspend fun checkInBackground(ctx: Context) {
        val last = Store.prefs.getLong("updChecked", 0L)
        if (System.currentTimeMillis() - last < 10 * 60_000L) return
        run(ctx, UpdatePlan.Ask.Background)
    }

    /** Safe mode: install at once (the owner is looking, so a confirm dialog can show). */
    suspend fun installNowFromSafeMode(ctx: Context): String {
        run(ctx, UpdatePlan.Ask.Install)
        return when (val s = state.value) {
            State.UpToDate -> "No update yet. Claude is told about the problem through the report."
            is State.Installing -> "Installing v${s.code}…"
            is State.Waiting -> "Installing v${s.code}…"
            is State.NeedsTap -> "v${s.code} is ready: tap Install on Android's screen."
            is State.Blocked -> s.why
            is State.Failed -> "Couldn't update: ${s.why}"
            else -> "Looking…"
        }
    }

    /** The whole run off the main thread: the release list's decode and the APK's checks are real work. */
    suspend fun run(ctx: Context, ask: UpdatePlan.Ask) = withContext(Dispatchers.IO) {
        busy.withLock {
            attempt {
                val rel = find()
                Store.prefs.edit().putLong("updChecked", System.currentTimeMillis()).apply()
                if (rel == null) {
                    set(State.UpToDate)
                    return@attempt
                }
                val apk = download(ctx, rel)
                verify(ctx, apk, rel.code)?.let { why -> apk.delete(); set(State.Failed(why)); return@attempt }
                pending(rel.code)
                when (UpdatePlan.next(ask, selfOwned(ctx))) {
                    UpdatePlan.Next.InstallNow -> Install.start(ctx, apk, rel.code, now = true)
                    UpdatePlan.Next.WhenLeft -> Install.start(ctx, apk, rel.code, now = false)
                    UpdatePlan.Next.TapNextOpen -> needsTap(rel.code)
                }
            }
        }
    }

    /** One run's work: anything it throws ends as Failed (never a crash, not even in safe mode), cancelling stays cancelling. */
    internal suspend fun attempt(work: suspend () -> Unit) {
        set(State.Checking)
        try {
            work()
            Store.prefs.edit().remove("updFail").remove("updFails").apply()
        } catch (e: CancellationException) {
            // Settings closed mid-download: the next run carries on from the part file
            if (state.value is State.Checking || state.value is State.Downloading) set(State.Idle)
            throw e
        } catch (e: Exception) {
            failed(e)
        }
    }

    /**
     * Failed, and told once when it's more than a bad signal: a bug at once, the same failure on [REPEATS] runs in a
     * row (the .part that asked for bytes past its end failed every run, silently).
     */
    private fun failed(e: Exception) {
        val why = (if (e is IOException) e.message else null) ?: (e::class.java.simpleName + (e.message?.let { ": $it" } ?: ""))
        set(State.Failed(if (e is IOException) why else "something went wrong ($why)"))
        val p = Store.prefs
        val n = if (p.getString("updFail", null) == why) p.getInt("updFails", 0) + 1 else 1
        p.edit().putString("updFail", why).putInt("updFails", n).apply()
        val offline = e is UnknownHostException || e is ConnectException || e is InterruptedIOException
        val tell = if (e is IOException) !offline && n == REPEATS else n == 1
        if (tell) Reporter.report("error", "The updater failed $n time" + (if (n > 1) "s in a row: " else ": ") + why, stack = e.stackTraceToString(), note = "state ${state.value}")
    }

    private const val REPEATS = 3

    /** What the release list said: [rel] is the pick (null: nothing newer). */
    private class Found(val rel: Release?)

    /**
     * The newest android-vN above this one: the API with its ETag first (a 304 costs nothing), else the text asset.
     * Only the pick is kept for the next 304, never the list itself (95 KB in SharedPreferences, read at every start).
     */
    private suspend fun find(): Release? {
        val p = Store.prefs
        if (p.contains("relBody")) p.edit().remove("relBody").remove("relEtag").apply() // kept until android-v10
        val current = BuildConfig.VERSION_CODE
        val etag = p.getString("relEtag", null)?.takeIf { p.contains("relPick") }
        val api = Request.Builder().url(API).header("Accept", "application/vnd.github+json")
            .apply { if (etag != null) header("If-None-Match", etag) }.build()
        val found: Found? = try {
            Net.call(api) { r ->
                when {
                    r.code == 304 -> Found(ReleaseFeed.kept(p.getString("relPick", null), current))
                    r.isSuccessful -> Found(ReleaseFeed.pick(r.body.string(), current)).also { f ->
                        // stored once it's read: a list that doesn't parse leaves the old pick and ETag alone
                        p.edit().putString("relEtag", r.header("ETag")).putString("relPick", ReleaseFeed.keep(f.rel)).apply()
                    }
                    else -> null // 403/429: the API is busy, use the text asset
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null // no answer or one that doesn't read: the text asset
        }
        if (found != null) return found.rel
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
        fetch(dir, rel, sha)
    }

    /** Brings the APK into [dir], resuming a part file; it's returned only once it matches [sha]. */
    internal suspend fun fetch(dir: File, rel: Release, sha: String): File {
        val done = File(dir, ReleaseFeed.apkName(rel.code))
        if (done.exists() && sha256(done) == sha) return done
        val part = File(dir, done.name + ".part")
        // a part as long as the APK is whole (a run stopped between its last byte and the swap): asking for the bytes
        // after it is a 416, which failed every later run; one that doesn't match is deleted and fetched again
        if (rel.size > 0 && part.length() >= rel.size && keep(part, done, sha)) return done
        var from = 0L
        // the Range is worked out per try: a try that stopped mid-body has added to the part since
        Net.call({
            from = if (part.exists()) part.length() else 0L
            Request.Builder().url(rel.apkUrl).apply { if (from > 0) header("Range", "bytes=$from-") }.build()
        }) { r ->
            if (r.code == 416 && from > 0) return@call // nothing past what's here: the check says whether it's whole
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
        if (!keep(part, done, sha)) throw IOException("the download didn't match its SHA-256")
        return done
    }

    /** The check and the swap, finished even when the run is cancelled; a part that doesn't match is deleted. */
    private suspend fun keep(part: File, done: File, sha: String): Boolean = withContext(NonCancellable) {
        if (sha256(part) != sha) {
            part.delete()
            false
        } else {
            swapIn(part, done)
            true
        }
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

    /** Android may need "Install unknown apps" for Play next once; Settings shows a button when it's off. */
    fun canInstall(ctx: Context) = ctx.packageManager.canRequestPackageInstalls()
}

/**
 * The PackageInstaller session, written off the main thread. now: committed at once, while the owner is looking, so
 * Android's install screen can show (InstallResultReceiver opens it). Otherwise Android commits it once the app is off
 * screen, with no tap: only for a copy Play next installed itself (Updater.selfOwned).
 */
internal object Install {
    private const val DAY = 24 * 60 * 60_000L

    suspend fun start(ctx: Context, apk: File, code: Int, now: Boolean) {
        val pi = ctx.packageManager.packageInstaller
        val id = withContext(Dispatchers.IO) {
            pi.mySessions.forEach { runCatching { pi.abandonSession(it.sessionId) } }
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(ctx.packageName)
                setSize(apk.length())
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                setPackageSource(PackageInstaller.PACKAGE_SOURCE_OTHER)
                setInstallReason(PackageManager.INSTALL_REASON_USER)
            }
            val sid = pi.createSession(params)
            pi.openSession(sid).use { s ->
                s.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; s.fsync(out) }
            }
            sid
        }
        val sender = PendingIntent.getBroadcast(
            ctx, id, Intent(ctx, InstallResultReceiver::class.java).putExtra("code", code),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        ).intentSender
        Store.prefs.edit().putInt("updSession", id).putInt("updCode", code).apply()
        if (now) {
            Updater.set(Updater.State.Installing(code))
            pi.openSession(id).use { it.commit(sender) }
            return
        }
        try {
            val c = PackageInstaller.InstallConstraints.Builder().setAppNotForegroundRequired().build()
            pi.commitSessionAfterInstallConstraintsAreMet(id, sender, c, DAY)
            Updater.set(Updater.State.Waiting(code))
        } catch (e: SecurityException) {
            // Android doesn't count Play next as this copy's installer after all: ask for the tap at the next open
            runCatching { pi.abandonSession(id) }
            Updater.needsTap(code)
        }
    }
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Updater.run(applicationContext, UpdatePlan.Ask.Background)
        return Result.success()
    }
}
