package io.github.shamlawy.playnext

import java.io.File

/** The repo's own files, for tests that check the app against them (the path comes from app/build.gradle.kts). */
object Repo {
    val dir: File = File(System.getProperty("pn.repo") ?: "../..").absoluteFile

    fun text(path: String): String = File(dir, path).readText()
}
