package io.github.shamlawy.playnext

import java.io.File

/** The repo's root in unit tests (app/build.gradle.kts passes it as pn.repo). */
object Repo {
    val root: File = File(System.getProperty("pn.repo") ?: "../..")
    fun text(path: String): String = File(root, path).readText()
}
