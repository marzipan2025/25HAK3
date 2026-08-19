package com.artbrain.hak3

import android.content.Context
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf

/** 문항을 어느 목록에 담아 두었는가. */
enum class Mark { AMBER, RED }

/**
 * 회차마다 '애매하게 모름 / 아예 모름' 목록을 들고 있는다.
 * 다음에 앱을 열었을 때도 남아 있어야 기록으로서 뜻이 있으므로 SharedPreferences에 적는다.
 * 저장 형식은 `12:A,37:R` — 사람이 읽고 고칠 수 있는 편이 뒤탈이 적다.
 */
class Marks(context: Context, private val round: Int) {
    private val prefs = context.getSharedPreferences("marks", Context.MODE_PRIVATE)
    private val key = "round_$round"

    val state: SnapshotStateMap<Int, Mark> = mutableStateMapOf<Int, Mark>().apply {
        prefs.getString(key, "")!!.split(',').forEach { part ->
            val (no, tag) = part.split(':').takeIf { it.size == 2 } ?: return@forEach
            val mark = when (tag) {
                "A" -> Mark.AMBER
                "R" -> Mark.RED
                else -> null
            }
            no.toIntOrNull()?.let { n -> mark?.let { put(n, it) } }
        }
    }

    fun set(no: Int, mark: Mark?) {
        if (mark == null) state.remove(no) else state[no] = mark
        prefs.edit()
            .putString(key, state.entries
                .sortedBy { it.key }
                .joinToString(",") { "${it.key}:${if (it.value == Mark.AMBER) "A" else "R"}" })
            .apply()
    }

    fun count(mark: Mark) = state.count { it.value == mark }
}
