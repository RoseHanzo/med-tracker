package com.rosehanzo.medtracker.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
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
 * Every build is published to the "latest" release with a version.json and the title "Cadence build N".
 * The repository has to be public while checking and downloading.
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
        /** Downloaded and handed to Android's installer. [path] lets the user reopen it. */
        data class Downloaded(val build: Int, val path: String) : State()
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
        val busy = _state.value
        if (busy is State.Downloading || busy is State.Downloaded) return
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
            if (code == 404) error("not found, is the repository public?")
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

    /** Call with the Activity context so Android's install screen can open on top of the app. */
    suspend fun downloadAndInstall(ctx: Context, update: Available) {
        val file = try {
            _state.value = State.Downloading(0)
            download(ctx, update.url)
        } catch (e: Exception) {
            _state.value = State.Failed("The update didn't download (${e.message ?: "no connection"}). Try again.")
            return
        }
        _state.value = State.Downloaded(update.build, file.path)
        openInstaller(ctx, file)
    }

    /** Opens Android's own "update this app?" screen for the downloaded file. */
    fun openInstaller(ctx: Context, file: File) {
        if (!file.exists()) {
            _state.value = State.Failed("The downloaded update is missing. Check for updates again.")
            return
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", file)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            _state.value = State.Failed("Android's installer didn't open. Download Cadence.apk from GitHub instead.")
        } catch (e: SecurityException) {
            _state.value = State.Failed("Allow Cadence to install apps, then tap Install.")
        }
    }

    private suspend fun download(ctx: Context, url: String): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "Cadence.apk")
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.useCaches = false
        conn.setRequestProperty("User-Agent", "Cadence/$currentBuild (Android)")
        try {
            val code = conn.responseCode
            if (code == 404) error("not found, is the repository public?")
            if (code != 200) error("GitHub replied $code")
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
}
