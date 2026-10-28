package io.github.shamlawy.playnext.data

import android.content.Context
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.crash.swapIn
import io.github.shamlawy.playnext.net.Net
import io.github.shamlawy.playnext.report.ReportRules
import io.github.shamlawy.playnext.report.Reporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.time.ZoneId

/**
 * Your games from the cloud backup (docs/ANDROID-PLAN.md "Your games come with you"). Until switch day the app only
 * reads: it opens the last copy it fetched (instantly, no signal needed), then asks the helper whether a newer one is
 * there, at start and whenever you come back to it. The copy stays sealed on the phone exactly as the helper keeps it,
 * so it only opens with your code; the code itself is in [CodeStore].
 */
object Games {
    sealed interface State {
        /** Reading this phone's copy: a moment at start. */
        data object Starting : State

        /** No restore code yet: the restore screen. */
        data object NoCode : State

        /** A code, but no copy on the phone yet (fetching, or [why] it didn't come). */
        data class Fetching(val why: String? = null) : State

        /** Your games. [copyAt] is when the web app made this copy (the helper's time), [checking] while looking for a newer one. */
        data class Ready(val lib: Library, val copyAt: Long, val checking: Boolean = false, val note: String? = null) : State
    }

    /** One copy in the vault (`/vault/list`). */
    class Copy(val k: String, val t: Long, val n: Long)

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()
    private val _state = MutableStateFlow<State>(State.Starting)
    val state: StateFlow<State> = _state.asStateFlow()

    const val NOT_A_CODE = "That isn't a restore code. It looks like PN-XXXX-XXXX-XXXX-XXXX-XXXX."

    private var keys: Code.Keys? = null
    private var code: String = ""
    private const val CHECK_GAP = 60_000L

    private fun cache(ctx: Context) = File(ctx.filesDir, "backup/latest.bin")

    /** At start: this phone's copy first (the splash waits for it, never for the network), then a check. */
    fun start(ctx: Context) {
        val app = ctx.applicationContext
        scope.launch {
            lock.withLock { openLocal(app) }
            if (_state.value !is State.NoCode) check(app, force = true)
        }
    }

    /** Coming back to the app: look for a newer copy if the last look was over a minute ago. */
    fun onOpen(ctx: Context) {
        if (keys == null) return
        if (System.currentTimeMillis() - Store.prefs.getLong("copyChecked", 0) < CHECK_GAP) return
        scope.launch { check(ctx.applicationContext, force = false) }
    }

    /** The restore screen: null when your games came, else the line to show. */
    suspend fun restore(ctx: Context, typed: String): String? = lock.withLock {
        val c = Code.normalize(typed)
        if (c.isEmpty()) return@withLock NOT_A_CODE
        val k = Code.keys(c)
        val why = fetch(ctx, k, c) ?: run {
            // a key store that won't make a key must not crash the screen your games just came to
            val saved = withContext(Dispatchers.IO) { runCatching { CodeStore.save(ctx, c) } }
            return@withLock if (saved.isSuccess) null else "Your games came, but this phone couldn't keep the code locked, so it asks again next time."
        }
        why
    }

    /** "Use a different code": the code and the copy go; the restore screen comes back. */
    fun forget(ctx: Context) {
        scope.launch {
            lock.withLock {
                CodeStore.clear(ctx)
                cache(ctx).delete()
                Store.prefs.edit().remove("copyT").remove("copyChecked").apply()
                keys = null
                code = ""
                Reporter.names = emptyList()
                Reporter.secrets = emptyList()
                Reporter.lib = ""
                _state.value = State.NoCode
            }
        }
    }

    /** "Check for a newer copy" in Settings. */
    fun checkNow(ctx: Context) {
        scope.launch { check(ctx.applicationContext, force = true) }
    }

    private suspend fun openLocal(ctx: Context) {
        val c = withContext(Dispatchers.IO) { CodeStore.read(ctx) }
        if (c == null) {
            cache(ctx).delete()
            _state.value = State.NoCode
            return
        }
        val k = Code.keys(c)
        hold(k, c)
        val f = cache(ctx)
        val t = Store.prefs.getLong("copyT", 0)
        if (!f.isFile || t == 0L) { _state.value = State.Fetching(); return }
        val lib = runCatching { build(Seal.open(f.readBytes(), k.key)) }.getOrNull()
        if (lib == null) {
            f.delete()
            _state.value = State.Fetching()
            return
        }
        hold(k, c) // again, now that build() has read the backup's RAWG key: reports scrub it too
        _state.value = State.Ready(lib, t)
    }

    private suspend fun check(ctx: Context, force: Boolean) {
        lock.withLock {
            val k = keys ?: return
            val cur = _state.value
            if (!force && System.currentTimeMillis() - Store.prefs.getLong("copyChecked", 0) < CHECK_GAP) return
            if (cur is State.Ready) _state.value = cur.copy(checking = true, note = null)
            val why = fetch(ctx, k, code)
            val now = _state.value
            if (why != null) {
                _state.value = if (now is State.Ready) now.copy(checking = false, note = why) else State.Fetching(why)
            }
        }
    }

    /**
     * list → the latest copy → (only when it's newer than the phone's) get → open → your games. Null when it worked,
     * else the line to show. Runs under [lock].
     */
    private suspend fun fetch(ctx: Context, k: Code.Keys, c: String): String? {
        val list = try {
            list(k)
        } catch (e: VaultError) {
            return e.why
        } catch (e: IOException) {
            return if (_state.value is State.Ready) "No connection, so this is the copy from last time." else "No connection. Try again when you're online."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "Couldn't read the helper's answer. Try again in a minute."
        }
        Store.prefs.edit().putLong("copyChecked", System.currentTimeMillis()).apply()
        val latest = list.firstOrNull { it.k == "latest" } ?: return "No backup found for that code."
        val have = Store.prefs.getLong("copyT", 0)
        val cur = _state.value
        if (latest.t == have && cur is State.Ready && keys === k) {
            _state.value = cur.copy(checking = false, note = null)
            return null
        }
        val bytes = try {
            get(k, "latest")
        } catch (e: VaultError) {
            return e.why
        } catch (e: IOException) {
            return "No connection. Try again when you're online."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "Couldn't read the helper's answer. Try again in a minute."
        }
        val lib = try {
            withContext(Dispatchers.Default) { build(Seal.open(bytes, k.key)) }
        } catch (e: SealError) {
            return e.why
        } catch (e: BadBackup) {
            return e.why
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "That copy didn't open: ${e.javaClass.simpleName}."
        }
        withContext(Dispatchers.IO) {
            val f = cache(ctx).apply { parentFile?.mkdirs() }
            val tmp = File(f.parentFile, "latest.new")
            tmp.writeBytes(bytes)
            swapIn(tmp, f)
        }
        Store.prefs.edit().putLong("copyT", latest.t).apply()
        hold(k, c)
        _state.value = State.Ready(lib, latest.t)
        return null
    }

    /** The code in use, in memory only; everything secret about it is scrubbed from reports. */
    private fun hold(k: Code.Keys, c: String) {
        keys = k
        code = c
        Reporter.secrets = listOfNotNull(c, Code.format(c), k.tok, k.id, rawg.ifEmpty { null })
    }

    @Volatile private var rawg: String = ""

    /** The RAWG key from the backup's first line (search, Similar and critic scores use it in later phases). */
    val rawgKey: String get() = rawg

    private class BadBackup(val why: String) : Exception(why)

    /** The opened text → your games, the way the web app restores them (Backup). */
    private fun build(text: String): Library {
        val parts = Seal.split(text)
        val rb = Backup.read(parts.state)
        if (!rb.ok || rb.data == null) throw BadBackup(rb.msg)
        val lib = Library(Backup.load(Backup.apply(rb.data), System.currentTimeMillis(), ZoneId.systemDefault()))
        rawg = parts.rawgKey
        Reporter.names = lib.privateNames
        Reporter.lib = ReportRules.libLine(lib, System.currentTimeMillis())
        return lib
    }

    // ---- the helper's vault, read-only (1-data.md §1.6, the web app's cbFetch) ----

    private class VaultError(val why: String) : Exception(why)

    private suspend fun list(k: Code.Keys): List<Copy> = Net.call(vault("list", k, "")) { r ->
        val body = r.body.string()
        if (!r.isSuccessful) throw VaultError(vaultWhy(r.code, body, list = true))
        val arr = (json.parseToJsonElement(body) as? JsonObject)?.get("list") as? JsonArray ?: JsonArray(emptyList())
        arr.mapNotNull { e ->
            val o = e as? JsonObject ?: return@mapNotNull null
            Copy(Backup.str(o["k"]), o["t"]?.jsonPrimitive?.longOrNull ?: 0, o["n"]?.jsonPrimitive?.longOrNull ?: 0)
        }
    }

    private suspend fun get(k: Code.Keys, which: String): ByteArray = Net.call(vault("get", k, "&k=$which")) { r ->
        if (!r.isSuccessful) throw VaultError(vaultWhy(r.code, r.body.string(), list = false))
        r.body.bytes()
    }

    private fun vault(op: String, k: Code.Keys, q: String) = Request.Builder()
        .url(Net.helper("/vault/$op?id=${k.id}$q"))
        .header("Authorization", "Bearer ${k.tok}")
        .header("Cache-Control", "no-store")
        .get()
        .build()

    private fun vaultWhy(status: Int, body: String, list: Boolean): String {
        val err = runCatching { Backup.str((json.parseToJsonElement(body) as? JsonObject)?.get("error")) }.getOrDefault("")
        return when {
            status == 404 && Regex("not found", RegexOption.IGNORE_CASE).containsMatchIn(err) -> "The helper needs an update first."
            status == 403 && err == "origin" -> "The helper turned the app away (origin). Claude has to look at this."
            status == 403 && list -> "That code doesn't match a backup."
            status == 403 -> "That code doesn't match this backup any more."
            status == 404 -> "No backup found for that code."
            else -> "The helper said ${err.ifEmpty { status.toString() }}. Try again in a minute."
        }
    }
}
