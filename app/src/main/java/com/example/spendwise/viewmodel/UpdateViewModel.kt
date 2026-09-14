package com.example.spendwise.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.spendwise.BuildConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

data class VersionInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val notes: String,
    val publishedAt: String,
)

enum class UpdatePhase { IDLE, CHECKING, UPDATE_AVAILABLE, NO_UPDATE, DOWNLOADING, READY, ERROR }

/**
 * In-app updater — checks the R2-hosted version.json manifest and, when a
 * newer versionCode exists, downloads the release APK and hands it to the
 * system package installer. Same flow as Veena's app update.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    app: Application,
) : AndroidViewModel(app) {

    var phase by mutableStateOf(UpdatePhase.IDLE)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var latestVersion by mutableStateOf<VersionInfo?>(null)
        private set
    var downloadProgress by mutableStateOf(0f)
        private set

    val installedVersion: String = BuildConfig.VERSION_NAME
    val installedCode: Int = BuildConfig.VERSION_CODE

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var apkFile: File? = null

    fun checkForUpdate() {
        if (phase == UpdatePhase.CHECKING) return
        phase = UpdatePhase.CHECKING
        error = null

        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { fetchVersionInfo() }
                if (info.apkUrl.isBlank() || info.versionCode <= 0) {
                    throw Exception("Invalid version manifest")
                }
                latestVersion = info
                phase = if (info.versionCode > installedCode) {
                    UpdatePhase.UPDATE_AVAILABLE
                } else {
                    UpdatePhase.NO_UPDATE
                }
            } catch (e: Exception) {
                error = e.message ?: "Failed to check for updates"
                phase = UpdatePhase.ERROR
            }
        }
    }

    fun downloadAndInstall() {
        val info = latestVersion ?: return
        if (phase == UpdatePhase.DOWNLOADING) return
        phase = UpdatePhase.DOWNLOADING
        error = null
        downloadProgress = 0f

        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { downloadApk(info.apkUrl) }
                apkFile = file
                phase = UpdatePhase.READY
                installApk(file)
            } catch (e: Exception) {
                error = e.message ?: "Download failed"
                phase = UpdatePhase.ERROR
            }
        }
    }

    fun installApk(file: File? = apkFile) {
        val f = file ?: return
        val context = getApplication<Application>()
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileProvider",
            f,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun fetchVersionInfo(): VersionInfo {
        val url = BuildConfig.UPDATE_MANIFEST_URL
        if (url.isBlank()) throw Exception("Update URL not configured")
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            val obj = JSONObject(resp.body?.string().orEmpty())
            return VersionInfo(
                versionName = obj.optString("versionName", ""),
                versionCode = obj.optInt("versionCode", 0),
                apkUrl = obj.optString("apkUrl", ""),
                notes = obj.optString("notes", ""),
                publishedAt = obj.optString("publishedAt", ""),
            )
        }
    }

    private fun downloadApk(apkUrl: String): File {
        val context = getApplication<Application>()
        val updatesDir = context.getExternalFilesDir("updates") ?: context.cacheDir
        val file = File(updatesDir, "spendwise-update.apk")
        file.delete()

        val req = Request.Builder().url(apkUrl).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("Download HTTP ${resp.code}")
            val body = resp.body ?: throw Exception("Empty response")
            val total = body.contentLength()
            var downloaded = 0L
            file.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        out.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) downloadProgress = downloaded.toFloat() / total
                    }
                }
            }
        }

        if (!file.exists() || file.length() == 0L) throw Exception("APK file is empty")
        return file
    }
}
