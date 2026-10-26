package io.github.shamlawy.playnext.update

import io.github.shamlawy.playnext.Repo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class ReleaseFeedTest {
    private val sha = "ab".repeat(32)

    private fun rel(tag: String, code: Int?, draft: Boolean = false, pre: Boolean = false, withApk: Boolean = true) = """
        {"tag_name":"$tag","draft":$draft,"prerelease":$pre,"assets":[
          ${if (withApk && code != null) """{"name":"play-next-android-v$code.apk","browser_download_url":"https://x/$code.apk","size":1234,"digest":"sha256:$sha"},""" else ""}
          {"name":"notes.txt","browser_download_url":"https://x/n","size":1}
        ]}
    """

    @Test
    fun picksTheNewestPublishedAndroidRelease() {
        val body = "[" + listOf(
            rel("android-v5", 5, draft = true),
            rel("android-v4", 4, withApk = false),
            rel("v327", null),
            rel("android-v3", 3),
            rel("android-v2", 2),
            rel("android-v9", 9, pre = true),
        ).joinToString(",") + "]"
        val r = ReleaseFeed.pick(body, current = 1)!!
        assertEquals(3, r.code)
        assertEquals("https://x/3.apk", r.apkUrl)
        assertEquals(sha, r.sha256)
    }

    @Test
    fun nothingNewerMeansNull() {
        assertNull(ReleaseFeed.pick("[" + rel("android-v3", 3) + "]", current = 3))
        assertNull(ReleaseFeed.pick("[]", current = 1))
    }

    @Test
    fun theTextAssetIsTheFallback() {
        val r = ReleaseFeed.pickLatestTxt("7 ${sha.uppercase()} 5555 android-v7\n", current = 6)!!
        assertEquals(7, r.code)
        assertEquals(sha, r.sha256)
        assertEquals("${ReleaseFeed.REPO_URL}/releases/download/android-v7/play-next-android-v7.apk", r.apkUrl)
        assertNull(ReleaseFeed.pickLatestTxt("7 nothex 5555", current = 6))
        assertNull(ReleaseFeed.pickLatestTxt("7 $sha 5555", current = 7))
    }

    @Test
    fun shaFile() {
        assertEquals(sha, ReleaseFeed.shaOf("$sha  play-next-android-v7.apk\n"))
        assertNull(ReleaseFeed.shaOf("nothing here"))
    }
}

class WhatsNewTest {
    @Test
    fun parsesBlocks() {
        val e = WhatsNew.parse("# note\nv=2\ngo=home\nat=\nsay=Hello = world\ntryIt=Tap it\n\n\nv=x\nsay=bad\n\nv=3\nsay=Next")
        assertEquals(listOf(2, 3), e.map { it.v })
        assertEquals("Hello = world", e[0].say)
    }

    /** The same rule release-guard holds a merge on: this release has its block, with go, say and tryIt. */
    @Test
    fun theCurrentReleaseHasItsEntry() {
        val release = Properties().apply { load(Repo.text("android/version.properties").reader()) }.getProperty("release").trim().toInt()
        val e = WhatsNew.parse(Repo.text("android/app/src/main/assets/whatsnew.txt")).firstOrNull { it.v == release }
        assertTrue("whatsnew.txt has no v=$release block", e != null)
        assertTrue(e!!.go.isNotBlank() && e.say.isNotBlank() && e.tryIt.isNotBlank())
    }
}
