package io.github.shamlawy.playnext.net

/**
 * Until switch day the app is a practice copy: it reads, it never changes the owner's records. Every request goes
 * through [check]. The helper has an explicit list (android/parity/0-map/1-data.md §3); other hosts get reads only.
 * A new helper route fails RoutesTest until it is listed here.
 */
object Routes {
    const val HELPER_HOST = "playnext-helper.hussamnabil48.workers.dev"
    const val SYNC_HOST = "playnext-sync.hussamnabil48.workers.dev"

    sealed interface Verdict
    data object Allow : Verdict
    data class Block(val why: String) : Verdict

    /** Exact helper paths: method -> allowed. `*` = any method. */
    private val EXACT: Map<String, Map<String, Boolean>> = mapOf(
        "/why" to mapOf("GET" to true, "POST" to true),
        "/chat" to mapOf("POST" to true),
        "/chat/memory" to mapOf("POST" to true),
        "/similar" to mapOf("POST" to true),
        "/recap" to mapOf("POST" to true),
        "/review" to mapOf("POST" to true),
        "/version" to mapOf("*" to true),
        "/fx" to mapOf("*" to true),
        "/prices" to mapOf("POST" to true), // only without id/watch/off/all, see check()
        "/steam" to mapOf("POST" to true),
        "/steam/me" to mapOf("*" to false),
        "/steam/sync" to mapOf("*" to false),
        "/reviews" to mapOf("POST" to true),
        "/hours" to mapOf("POST" to true),
        "/art" to mapOf("POST" to true),
        "/news" to mapOf("POST" to true),
        "/ost" to mapOf("POST" to true),
        "/ost/clip" to mapOf("GET" to true),
        "/yt" to mapOf("POST" to true),
        "/vault/list" to mapOf("GET" to true),
        "/vault/get" to mapOf("GET" to true),
        "/vault/put" to mapOf("*" to false),
        "/vault/del" to mapOf("*" to false),
        "/cal/put" to mapOf("*" to false),
        "/nudge/key" to mapOf("*" to false), // a GET that makes the push key
        "/nudge" to mapOf("*" to false),
        "/nudge/test" to mapOf("*" to false),
        "/announce" to mapOf("*" to false), // pings every phone
        "/bug" to mapOf("POST" to true), // the app's own problem reports
        "/usage" to mapOf("*" to false),
        "/book/key" to mapOf("*" to false),
        "/book/keys" to mapOf("*" to false),
        "/bugs" to mapOf("*" to false),
        "/bugs/ack" to mapOf("*" to false),
    )

    /** Helper path prefixes (the dispatcher's startsWith checks). */
    private val PREFIX: Map<String, Map<String, Boolean>> = mapOf(
        "/sgdb/" to mapOf("*" to true),
        "/vault/" to mapOf("*" to false), // list/get are exact above; any other vault path is a write
        "/cal/" to mapOf("GET" to true), // the .ics feed (read)
    )

    /** Every path and prefix this file classifies; RoutesTest compares it with worker/helper.js. */
    val KNOWN: Set<String> get() = EXACT.keys + PREFIX.keys

    private val PRICE_WRITES = Regex("\"(id|watch|off|all)\"\\s*:")

    fun check(method: String, host: String, path: String, body: String? = null): Verdict {
        val m = method.uppercase()
        if (host == HELPER_HOST) {
            val rule = EXACT[path] ?: PREFIX.entries.firstOrNull { path.startsWith(it.key) }?.value
                ?: return Block("unknown helper route $m $path")
            val ok = rule[m] ?: rule["*"] ?: false
            if (!ok) return Block("practice copy: $m $path changes your records")
            if (path == "/prices" && body != null && PRICE_WRITES.containsMatchIn(body)) {
                return Block("practice copy: price watching writes")
            }
            return Allow
        }
        // Friend sync and every other host: reads only.
        return if (m == "GET" || m == "HEAD") Allow else Block("practice copy: $m to $host")
    }

    /** The helper only answers calls that come from the web app's address. */
    fun wantsOrigin(host: String) = host == HELPER_HOST || host == SYNC_HOST
}
