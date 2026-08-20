package com.artbrain.hak3

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf

/**
 * 문항을 어느 목록에 담아 두었는가.
 *
 * 노랑 ← 일반 → 초록 한 축이다. 없음이 가운데고 양쪽 끝이 이 둘이다.
 */
enum class Mark { AMBER, KNOWN }

/**
 * 회차마다 '애매하게 모름 · 외움' 두 목록을 들고 있는다.
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
            Mark.KNOWN -> "K"
        }

        private fun read(prefs: SharedPreferences, key: String): Map<Int, Mark> = buildMap {
            prefs.getString(key, "")!!.split(',').forEach { part ->
                val (no, t) = part.split(':').takeIf { it.size == 2 } ?: return@forEach
                // R 은 없앤 '아예 모름'. 남아 있으면 버린다.
                val mark = when (t) {
                    "A" -> Mark.AMBER
                    "K" -> Mark.KNOWN
                    else -> null
                }
                no.toIntOrNull()?.let { n -> mark?.let { put(n, it) } }
            }
        }

        private fun write(state: Map<Int, Mark>) = state.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}:${tag(it.value)}" }

        /** 그 회차의 표시들. 모음을 꾸릴 때 회차를 훑는 데 쓴다. */
        fun of(context: Context, round: Int): Map<Int, Mark> =
            read(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), keyOf(round))

        /** 표시가 하나라도 남아 있는 회차들. */
        fun rounds(context: Context): List<Int> =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.keys
                .mapNotNull { it.removePrefix("round_").takeIf { n -> it != n }?.toIntOrNull() }
                .sortedDescending()

        /**
         * 여러 회차의 문항을 한꺼번에 같은 표시로 바꾼다.
         * 회차마다 한 번씩만 적는다 — 같은 문제가 여러 회차에 걸쳐 있을 때 쓴다.
         */
        fun setAll(context: Context, keys: List<Pair<Int, Int>>, mark: Mark?) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val edit = prefs.edit()
            keys.groupBy({ it.first }, { it.second }).forEach { (round, nos) ->
                val key = keyOf(round)
                val m = read(prefs, key).toMutableMap()
                nos.forEach { no -> if (mark == null) m.remove(no) else m[no] = mark }
                edit.putString(key, write(m))
            }
            edit.apply()
        }

        /** 그 회차에서 마지막으로 보던 문항 번호. */
        fun lastSeen(context: Context, round: Int): Int =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("last_$round", 0)

        fun setLastSeen(context: Context, round: Int, no: Int) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putInt("last_$round", no).apply()
        }

        /** 회차 목록에 표기할 개수. */
        fun counts(context: Context, round: Int): Counts {
            val m = read(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE), keyOf(round))
            return Counts(
                amber = m.count { it.value == Mark.AMBER },
                known = m.count { it.value == Mark.KNOWN },
            )
        }
    }
}

data class Counts(val amber: Int, val known: Int) {
    val any: Boolean get() = amber > 0 || known > 0
}
