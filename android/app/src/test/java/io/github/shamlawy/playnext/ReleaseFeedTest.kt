package io.github.shamlawy.playnext

import io.github.shamlawy.playnext.update.ReleaseFeed
import io.github.shamlawy.playnext.update.WhatsNew
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Properties

class ReleaseFeedTest {
    private val sha = "ab".repeat(32)

    /** GitHub's releases list as the API answers it: web tags, drafts and a release without its APK mixed in. */
    private val body = """
        [
          {"tag_name":"android-v4","draft":true,"prerelease":false,"assets":[{"name":"play-next-android-v4.apk","browser_download_url":"https://x/4.apk","size":9}]},
          {"tag_name":"android-v3","draft":false,"prerelease":false,"assets":[{"name":"notes.txt","browser_download_url":"https://x/n","size":1}]},
          {"tag_name":"v327","draft":false,"prerelease":false,"assets":[]},
          {"tag_name":"android-v2","draft":false,"prerelease":false,"html_url":"https://x","assets":[
            {"name":"play-next-android-v2.apk","browser_download_url":"https://x/2.apk","size":1234,"digest":"sha256:$sha"},
            {"name":"play-next-android-v2.apk.sha256","browser_download_url":"https://x/2.apk.sha256","size":90}]},
          {"tag_name":"android-v1","draft":false,"prerelease":true,"assets":[]}
        ]
    """.trimIndent()

    @Test
    fun picksTheNewestPublishedApkAboveThisVersion() {
        val r = ReleaseFeed.pick(body, current = 1)!!
        assertEquals(2, r.code)
        assertEquals("https://x/2.apk", r.apkUrl)
        assertEquals(1234L, r.size)
        assertEquals(sha, r.sha256)
        assertEquals("https://x/2.apk.sha256", r.shaUrl)
        assertNull("nothing newer", ReleaseFeed.pick(body, current = 2))
        assertNull(ReleaseFeed.pick("[]", current = 0))
    }

    @Test
    fun theTextAssetIsTheFallback() {
        val r = ReleaseFeed.pickLatestTxt("7 ${sha.uppercase()} 5555 android-v7\n", current = 6)!!
        assertEquals(7, r.code)
        assertEquals(sha, r.sha256)
        assertEquals("https://github.com/Shamlawy/Play-next/releases/download/android-v7/play-next-android-v7.apk", r.apkUrl)
        assertNull(ReleaseFeed.pickLatestTxt("7 $sha 5555 android-v7", current = 7))
        assertNull("a bad hash is no release", ReleaseFeed.pickLatestTxt("8 nothex 1 android-v8", current = 1))
        assertNull(ReleaseFeed.pickLatestTxt("", current = 1))
    }

    @Test
    fun shaFiles() {
        assertEquals(sha, ReleaseFeed.shaOf("${sha.uppercase()}  play-next-android-v2.apk\n"))
        assertNull(ReleaseFeed.shaOf("nope"))
    }

    /** The release the repo is about to make has its what's-new block, the way release-guard and android-release.yml read it. */
    @Test
    fun whatsNewHasTheCurrentRelease() {
        val n = Properties().apply { load(Repo.text("android/version.properties").reader()) }.getProperty("release").trim().toInt()
        val all = WhatsNew.parse(Repo.text("android/app/src/main/assets/whatsnew.txt"))
        val mine = all.filter { it.v == n }
        assertTrue("whatsnew.txt has no block with v=$n", mine.isNotEmpty())
        for (e in mine) {
            assertTrue("v=$n lacks go/say/tryIt: $e", e.go.isNotBlank() && e.say.isNotBlank() && e.tryIt.isNotBlank())
            assertTrue("go must be a screen the app has: ${e.go}", e.go in setOf("home", "probe"))
        }
        assertEquals("versions only go up", all.map { it.v }.sorted(), all.map { it.v })
    }

    @Test
    fun whatsNewParsing() {
        val e = WhatsNew.parse("# c\nv=3\ngo=home\nat=\nsay=Hi = there\ntryIt=x\n\n\nv=x\nsay=skipped\n\nv=4\ngo=probe\nsay=s\ntryIt=t")
        assertEquals(listOf(3, 4), e.map { it.v })
        assertEquals("Hi = there", e[0].say)
        assertEquals("", e[0].at)
    }
}
