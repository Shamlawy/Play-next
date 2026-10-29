package io.github.shamlawy.playnext.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import io.github.shamlawy.playnext.BuildConfig
import io.github.shamlawy.playnext.Store
import io.github.shamlawy.playnext.crash.CrashGuard
import io.github.shamlawy.playnext.report.Reporter

/** What Android says about an install session. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Store.init(ctx)
        val code = intent.getIntExtra("code", 0)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> Unit // this process is replaced; PackageReplacedReceiver runs in the new one
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android wants one tap. Only an app on screen may show it; otherwise the next open asks (kept in Store).
                val confirm = intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                val shown = confirm != null && ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                    runCatching { ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                if (!shown) Updater.needsTap(code)
            }
            PackageInstaller.STATUS_FAILURE_BLOCKED -> {
                val why = "Android blocked the update. Settings › Security and privacy › Auto Blocker › Off, then open Play next again."
                Updater.set(Updater.State.Blocked(why))
                Reporter.report("error", "An update to v$code was blocked by Android", note = msg)
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.needsTap(code) // "Cancel" on Android's screen: Settings keeps the Install button
            else -> {
                Updater.set(Updater.State.Failed("Android said: ${msg.ifBlank { "status $status" }}"))
                Reporter.report("error", "An update to v$code failed with status $status", note = msg)
            }
        }
    }
}

/** Runs in the new version right after an update: the next open says "Updated to vN" and the crash count starts over. */
class PackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return // only the system's own broadcast (lint)
        Store.init(ctx)
        Store.prefs.edit().putInt("updatedTo", BuildConfig.VERSION_CODE)
            .remove("updSession").remove("updTap").remove("updPend").remove("updPendOpens").apply()
        runCatching { CrashGuard.counter.calm() }
    }
}
