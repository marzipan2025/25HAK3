package com.artbrain.hak3

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 기출 데이터는 앱에 넣지 않고 폰에서 읽어 온다.
 *
 * 이렇게 갈라 두면 앱과 데이터가 서로를 붙들지 않는다. 앱은 코드만 담게 되어
 * 공개된 곳에 올려도 잃을 것이 없고(그래야 스토어 없이 갱신을 받을 수 있다),
 * 데이터는 폰 안에만 머문다. 나중에 데이터를 인터넷으로 옮기더라도 읽어 오는
 * 자리만 바뀔 뿐 앱의 나머지는 그대로다.
 *
 * 가리키는 방법은 두 가지다. 안드로이드 11부터 다운로드 폴더는 통째로 지정할 수
 * 없게 막혀 있으므로(하위 폴더는 된다),
 *   폴더  – 다운로드 안에 25HAK3 같은 폴더를 만들어 지정한다. 그 뒤로는 파일만
 *          떨어뜨리면 되고 다시 고를 일이 없다.
 *   파일  – 다운로드에 그냥 둔 파일을 직접 고른다. 같은 이름으로 덮어쓰면 계속 읽힌다.
 * 어느 쪽이든 권한을 따로 요구하지 않는다. 사용자가 고른 그 자리만 읽는다.
 */
object DataFile {

    /** 다운로드 폴더에 이 이름으로 두면 된다. 여럿이면 가장 최근 것을 쓴다. */
    const val PREFIX = "hanja3"
    const val SUFFIX = ".db"

    private const val PREFS = "datafile"
    private const val KEY_URI = "uri"
    private const val KEY_KIND = "kind"     // tree | file
    private const val KEY_STAMP = "stamp"

    sealed interface Result {
        data class Ok(val file: File, val name: String) : Result
        data object NoFolder : Result
        data object NoFile : Result
        data class Failed(val why: String) : Result
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun source(c: Context): Pair<Uri, String>? {
        val uri = prefs(c).getString(KEY_URI, null)?.let(Uri::parse) ?: return null
        return uri to (prefs(c).getString(KEY_KIND, "tree") ?: "tree")
    }

    fun remember(c: Context, uri: Uri, kind: String) {
        c.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs(c).edit()
            .putString(KEY_URI, uri.toString())
            .putString(KEY_KIND, kind)
            .remove(KEY_STAMP)
            .apply()
    }

    fun forget(c: Context) {
        prefs(c).edit().clear().apply()
        local(c).delete()
    }

    private fun local(c: Context) = File(c.filesDir, "hanja3.db")

    /**
     * 지정한 폴더에서 데이터 파일을 찾아 앱 안으로 들인다.
     * SQLite 는 실제 파일이어야 열리므로 한 번 베껴 둔다. 원본이 그대로면 베끼지 않는다.
     */
    suspend fun sync(c: Context): Result = withContext(Dispatchers.IO) {
        val (uri, kind) = source(c) ?: return@withContext Result.NoFolder
        val found = runCatching {
            if (kind == "file") {
                DocumentFile.fromSingleUri(c, uri)?.takeIf { it.canRead() }
            } else {
                DocumentFile.fromTreeUri(c, uri)?.takeIf { it.canRead() }?.listFiles()
                    ?.filter {
                        it.isFile && (it.name ?: "").startsWith(PREFIX) &&
                            (it.name ?: "").endsWith(SUFFIX)
                    }
                    ?.maxByOrNull { it.lastModified() }
            }
        }.getOrNull()
        val pick = found
            ?: return@withContext if (kind == "file") Result.NoFolder else Result.NoFile

        val name = pick.name ?: "hanja3.db"
        val stamp = "$name:${pick.length()}:${pick.lastModified()}"
        val out = local(c)
        if (out.exists() && prefs(c).getString(KEY_STAMP, null) == stamp) {
            return@withContext Result.Ok(out, name)
        }
        try {
            c.contentResolver.openInputStream(pick.uri).use { input ->
                if (input == null) return@withContext Result.Failed("파일을 열 수 없습니다.")
                out.outputStream().use { input.copyTo(it) }
            }
            prefs(c).edit().putString(KEY_STAMP, stamp).apply()
            Result.Ok(out, name)
        } catch (e: Exception) {
            Result.Failed(e.message ?: "읽는 중 문제가 생겼습니다.")
        }
    }
}
