
/**
 * Whether [ReviewsSec] draws anything for [g] (the pair beside Prices needs to know before it lays out): the card for a
 * game you haven't played once there's a guess or it's fresh out, else the critics alone.
 */
internal fun reviewsShow(g: Game, page: Page, lib: Library, t: Long, zone: ZoneId): Boolean {
    val q = page.q
    val card = !(Reviews.off(lib) || g.comp || q.rk.isPlayed(g) || g.rawState == "dropped")
    if (card && (q.rvFor(g) != null || (Reviews.want(lib, q.rk, g, t, zone) && (Reviews.age(g, t, zone) ?: -1) >= 0))) return true
    return Backup.num(g["critic"], 0.0, -1e9, 1e9) > 0
}
