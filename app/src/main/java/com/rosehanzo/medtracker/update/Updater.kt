package com.rosehanzo.medtracker.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.IntentCompat
import com.rosehanzo.medtracker.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * One-tap updates from the app's GitHub releases.
 * Every build is published to the "latest" release with the title "Cadence build N".
 */
object Updater {
    private const val RELEASE_API = "https://api.github.com/repos/RoseHanzo/med-tracker/releases/tags/latest"
    private const val DOWNLOADS = "https://github.com/RoseHanzo/med-tracker/releases/download/latest"
    private const val VERSION_URL = "$DOWNLOADS/version.json"
    private const val APK_URL = "$DOWNLOADS/Cadence.apk"

    data class Available(val build: Int, val url: String)

    sealed class State {
        data object Idle : State()
        data object Checking : State()
        data object UpToDate : State()
        data class Ready(val update: Available) : State()
        data class Downloading(val percent: Int) : State()
        data object Installing : State()
        data class Failed(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private var lastCheck = 0L

    val currentBuild: Int get() = BuildConfig.VERSION_CODE

    /** Checks GitHub at most every few hours unless [force] is true. */
    suspend fun check(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheck < 3 * 60 * 60 * 1000L) return
        if (_state.value is State.Downloading || _state.value is State.Installing) return
        lastCheck = now
        _state.value = State.Checking
        _state.value = try {
            val found = fetchLatest()
            if (found.build > currentBuild) State.Ready(found) else State.UpToDate
        } catch (e: Exception) {
            // Never report "up to date" when the check itself failed.
            if (force) State.Failed("Couldn't check for updates (${e.message ?: "no connection"}).") else State.Idle
        }
    }

    /**
     * Reads version.json from the release downloads first: it isn't subject to the API's
     * limit on anonymous requests, which shared mobile networks often hit.
     * Falls back to the GitHub API.
     */
    private suspend fun fetchLatest(): Available = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject(getText(VERSION_URL))
            Available(json.getInt("build"), APK_URL)
        } catch (first: Exception) {
            val json = try {
                JSONObject(getText(RELEASE_API))
            } catch (second: Exception) {
                throw IllegalStateException(second.message ?: first.message ?: "unknown error")
            }
            val title = json.optString("name")
            val build = Regex("build\\s+(\\d+)", RegexOption.IGNORE_CASE).find(title)
                ?.groupValues?.get(1)?.toIntOrNull() ?: error("no build number on GitHub")
            Available(build, APK_URL)
        }
    }

    private fun getText(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        conn.useCaches = false
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", "Cadence/$currentBuild (Android)")
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code != 200) error("GitHub replied $code")
            return conn.inputStream.bufferedReader().readText()
        } finally {
            conn.disconnect()
        }
    }

    /** Android asks once whether Cadence may install updates. */
    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(i) }
    }

    suspend fun downloadAndInstall(ctx: Context, update: Available) {
        try {
            _state.value = State.Downloading(0)
            val file = download(ctx, update.url)
            _state.value = State.Installing
            install(ctx, file)
        } catch (e: Exception) {
            _state.value = State.Failed("The update didn't download. Try again in a moment.")
        }
    }

    private suspend fun download(ctx: Context, url: String): File = withContext(Dispatchers.IO) {
        val out = File(ctx.cacheDir, "update.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong
            var done = 0L
            conn.inputStream.use { input ->
                out.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        done += n
                        if (total > 0) _state.value = State.Downloading((done * 100 / total).toInt())
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
        out
    }

    private suspend fun install(ctx: Context, apk: File) = withContext(Dispatchers.IO) {
        val installer = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        if (Build.VERSION.SDK_INT >= 31) {
            // Once Cadence has installed itself, later updates can go through without a prompt.
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("cadence.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = if (Build.VERSION.SDK_INT >= 31) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val callback = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, InstallReceiver::class.java), flags)
            session.commit(callback.intentSender)
        }
    }

    internal fun onInstallResult(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { ctx.startActivity(confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> _state.value = State.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = State.Idle
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown error"
                _state.value = State.Failed("The update couldn't install ($msg).")
                Toast.makeText(ctx, "Update failed: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}

class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Updater.onInstallResult(context, intent)
    }
}
