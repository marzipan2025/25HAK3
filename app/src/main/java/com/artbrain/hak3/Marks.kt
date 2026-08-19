package com.artbrain.hak3

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf

/** 문항을 어느 목록에 담아 두었는가. */
enum class Mark { AMBER, RED, KNOWN }

/**
 * 회차마다 '애매하게 모름 · 아예 모름 · 외움' 세 목록을 들고 있는다.
 * 다음에 앱을 열었을 때도 남아 있어야 기록으로서 뜻이 있으므로 SharedPreferences에 적는다.
 * 저장 형식은 `12:A,37:R,55:K` — 사람이 읽고 고칠 수 있는 편이 뒤탈이 적다.
 */
class Marks(context: Context, round: Int) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val key = keyOf(round)

    val state: SnapshotStateMap<Int, Mark> =
        mutableStateMapOf<Int, Mark>().apply { putAll(read(prefs, key)) }

    fun set(no: Int, mark: Mark?) {
        if (mark == null) state.remove(no) else state[no] = mark
        prefs.edit().putString(key, write(state)).apply()
    }

    fun count(mark: Mark) = state.count { it.value == mark }

    companion object {
        private const val PREFS = "marks"

        private fun keyOf(round: Int) = "round_$round"

        private fun tag(m: Mark) = when (m) {
            Mark.AMBER -> "A"
            Mark.RED -> "R"
            Mark.KNOWN -> "K"
        }

        private fun read(prefs: SharedPreferences, key: String): Map<Int, Mark> = buildMap {
            prefs.getString(key, "")!!.split(',').forEach { part ->
                val (no, t) = part.split(':').takeIf { it.size == 2 } ?: return@forEach
                val mark = when (t) {
                    "A" -> Mark.AMBER
                    "R" -> Mark.RED
                    "K" -> Mark.KNOWN
                    else -> null
                }
                no.toIntOrNull()?.let { n -> mark?.let { put(n, it) } }
            }
        }

        private fun write(state: Map<Int, Mark>) = state.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}:${tag(it.value)}" }

        /** 회차 목록에 표기할 개수. */
        fun counts(context: Context, round: Int): Counts {
            val m = read(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), keyOf(round))
            return Counts(
                amber = m.count { it.value == Mark.AMBER },
                red = m.count { it.value == Mark.RED },
                known = m.count { it.value == Mark.KNOWN },
            )
        }
    }
}

data class Counts(val amber: Int, val red: Int, val known: Int) {
    val any: Boolean get() = amber > 0 || red > 0 || known > 0
}
