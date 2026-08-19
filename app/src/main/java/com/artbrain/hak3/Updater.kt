package com.artbrain.hak3

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub 릴리스를 보고 새 판이 있으면 폰에서 바로 받아 설치한다.
 * 데스크탑 앱들(01haka·08FOSE)이 dmg로 하는 것과 같은 구조 — 스토어를 거치지 않는
 * 배포라 갱신도 직접 챙겨야 한다.
 *
 * 두 가지를 전제한다.
 *  1. 릴리스가 익명으로 읽히는 곳에 있어야 한다. 비공개 저장소면 토큰이 필요한데,
 *     APK에 넣은 토큰은 꺼내 쓸 수 있으므로 넣지 않는다.
 *  2. 새 APK가 지금 깔린 것과 같은 키로 서명돼 있어야 한다. 다르면 안드로이드가
 *     덮어쓰기를 거부한다(지우고 다시 깔아야 하고, 그러면 기록도 사라진다).
 */
object Updater {

    /** 릴리스를 읽어 올 곳. 저장소를 옮기면 여기만 고치면 된다. */
    private const val API = "https://api.github.com/repos/marzipan2025/25HAK3/releases/latest"
    const val RELEASES_PAGE = "https://github.com/marzipan2025/25HAK3/releases"

    data class Release(val version: String, val apkUrl: String?, val notes: String)

    sealed interface Status {
        data object Checking : Status
        data class UpToDate(val current: String) : Status
        data class Available(val release: Release) : Status
        data object Failed : Status
    }

    suspend fun check(current: String): Status = withContext(Dispatchers.IO) {
        val release = fetch() ?: return@withContext Status.Failed
        if (isNewer(release.version, current)) Status.Available(release)
        else Status.UpToDate(current)
    }

    private fun fetch(): Release? = try {
        val conn = (URL(API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        conn.use {
            if (it.responseCode !in 200..299) return@use null
            val json = JSONObject(it.inputStream.bufferedReader().readText())
            val assets = json.optJSONArray("assets")
            var apk: String? = null
            for (i in 0 until (assets?.length() ?: 0)) {
                val a = assets!!.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    apk = a.optString("browser_download_url"); break
                }
            }
            Release(
                version = json.optString("tag_name").removePrefix("v"),
                apkUrl = apk,
                notes = json.optString("body").trim(),
            )
        }
    } catch (_: Exception) {
        null
    }

    /** 0.1.10 이 0.1.9 보다 새것이도록 마디마다 숫자로 견준다. */
    fun isNewer(latest: String, current: String): Boolean {
        val a = latest.split('.').map { it.toIntOrNull() ?: 0 }
        val b = current.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /** APK를 받아 캐시에 둔다. onProgress 는 0..1, 길이를 모르면 -1. */
    suspend fun download(
        context: Context,
        url: String,
        version: String,
        onProgress: (Float) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        try {
            val out = File(context.cacheDir, "update").apply { mkdirs() }
                .resolve("25HAK3-$version.apk")
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                instanceFollowRedirects = true
            }
            conn.use { c ->
                if (c.responseCode !in 200..299) return@use null
                val total = c.contentLengthLong
                var read = 0L
                c.inputStream.use { input ->
                    out.outputStream().use { sink ->
                        val buf = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buf)
                            if (n <= 0) break
                            sink.write(buf, 0, n)
                            read += n
                            onProgress(if (total > 0) read.toFloat() / total else -1f)
                        }
                    }
                }
                out
            }
        } catch (_: Exception) {
            null
        }
    }

    /** 시스템 설치 화면을 띄운다. 설치 여부는 사용자가 그 화면에서 정한다. */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    }

    fun openReleasesPage(context: Context) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private inline fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T =
        try {
            block(this)
        } finally {
            disconnect()
        }
}
