package com.artbrain.hak3

import android.content.Context

/**
 * 노랑으로 담은 문항에 나온 한자들을 한자리에 쌓아 둔 곳.
 *
 * 회차를 가로질러 한 글자씩 모으고, 이미 있는 글자는 다시 넣지 않는다. 새 글자는
 * 맨 앞에 붙으므로 최근에 만난 것부터 보인다. 여기의 노랑·일반·초록은 회차의 표시와
 * 아무 상관이 없다 — 회차에서 노랑을 풀어도 여기 쌓인 글자는 그대로 남는다.
 *
 * 적는 형식은 Marks 와 같은 결로 둔다. `order` 는 글자를 쉼표로 이은 줄,
 * `marks` 는 `濕:A,潤:K` 꼴이다. 사람이 읽고 고칠 수 있는 편이 뒤탈이 적다.
 */
object Collect {
    private const val PREFS = "collect"
    private const val KEY_ORDER = "order"
    private const val KEY_MARKS = "marks"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** 쌓인 순서. 앞이 최근에 들어온 글자다. */
    fun order(c: Context): List<String> =
        prefs(c).getString(KEY_ORDER, "")!!.split(',').filter { it.isNotEmpty() }

    /** 없던 글자만 앞에 붙인다. 여럿이면 넘겨준 순서를 지킨다. */
    fun register(c: Context, hanja: List<String>) {
        val had = order(c)
        val fresh = hanja.filter { it.isNotEmpty() && it !in had }.distinct()
        if (fresh.isEmpty()) return
        prefs(c).edit().putString(KEY_ORDER, (fresh + had).joinToString(",")).apply()
    }

    fun marks(c: Context): Map<String, Mark> = buildMap {
        prefs(c).getString(KEY_MARKS, "")!!.split(',').forEach { part ->
            val (han, tag) = part.split(':').takeIf { it.size == 2 } ?: return@forEach
            when (tag) {
                "A" -> Mark.AMBER
                "K" -> Mark.KNOWN
                else -> null
            }?.let { put(han, it) }
        }
    }

    fun set(c: Context, han: String, mark: Mark?) {
        val m = marks(c).toMutableMap()
        if (mark == null) m.remove(han) else m[han] = mark
        val text = m.entries.joinToString(",") {
            "${it.key}:${if (it.value == Mark.AMBER) "A" else "K"}"
        }
        prefs(c).edit().putString(KEY_MARKS, text).apply()
    }

    /**
     * 이미 노랑으로 담아 둔 문항들을 훑어 빠진 글자를 채운다.
     * 단어장을 만들기 전에 찍어 둔 표시를 살리기 위한 것이라, 앱을 열 때 한 번만 돈다.
     * 없던 글자만 들어가므로 여러 번 돌아도 달라지는 것은 없다.
     */
    fun seed(c: Context, db: ExamDb) {
        val found = mutableListOf<String>()
        for (round in Marks.rounds(c)) {                     // 최신 회차부터
            for ((no, mark) in Marks.of(c, round)) {
                if (mark != Mark.AMBER) continue
                val (_, item) = db.pick(round, no) ?: continue
                found += db.hanjaOf(item)
            }
        }
        register(c, found)
    }

    /** 회차 목록의 모음 카드에 적을 수. */
    fun size(c: Context): Int = order(c).size
}
