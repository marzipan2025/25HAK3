package com.artbrain.hak3

import android.database.sqlite.SQLiteDatabase
import java.text.Normalizer

data class ExamRow(val round: Int, val date: String?, val items: Int, val complete: Boolean)

data class Item(
    val no: Int,
    val spanEnd: Int,
    val question: String,
    val html: String?,
    val target: String?,
    val answer: String?,
    /** 정답이 한자일 때 글자마다 붙는 訓音. 예) "妨 방해할 방 · 害 해할 해" */
    val gloss: String? = null,
) {
    /** 번호 표기 — 묶음 문제는 108-112처럼 범위로 나온다. */
    val label: String get() = if (spanEnd > no) "$no-$spanEnd" else "$no"
}

data class Section(
    val id: Int,
    val start: Int,
    val end: Int,
    val instruction: String,
    val items: List<Item>,
)

/**
 * 미리 만들어 둔 읽기 전용 DB를 assets에서 한 번 복사해 쓴다.
 * Room을 쓰지 않는 건 스키마 해시 검사를 통과시키려고 엔티티를 이중으로
 * 관리할 이유가 없어서다 — 이 DB는 앱이 쓰지 않고 읽기만 한다.
 */
private fun isHanja(c: Char) =
    c in '\u3400'..'\u4DBF' || c in '\u4E00'..'\u9FFF' || c in '\uF900'..'\uFAFF'

/** 답안지에는 金(U+F90A) 같은 호환 한자가 섞여 있다. 사전 키에 맞춰 정규화한다. */
private fun nfc(c: Char): String =
    Normalizer.normalize(c.toString(), Normalizer.Form.NFC)

class ExamDb private constructor(private val db: SQLiteDatabase) {

    fun exams(): List<ExamRow> = db.rawQuery(
        "SELECT round, exam_date, n_items, complete FROM exams ORDER BY round DESC", null
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(ExamRow(c.getInt(0), c.getString(1), c.getInt(2), c.getInt(3) == 1))
            }
        }
    }

    fun sections(round: Int): List<Section> {
        val secs = db.rawQuery(
            "SELECT id, start_no, end_no, instruction FROM sections WHERE round=? ORDER BY seq",
            arrayOf(round.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Section(c.getInt(0), c.getInt(1), c.getInt(2), c.getString(3) ?: "", emptyList()))
                }
            }
        }
        return secs.map { s -> s.copy(items = items(s.id).map(::withGloss)) }
    }

    /**
     * 문항에 나오는 한자를 모두 훑어 訓音을 붙인다. 문제 쪽(竊, 銳·鈍濁, 漸入佳)과
     * 정답 쪽을 함께 모으고, 나온 순서대로 늘어놓는다.
     * 訓音 자체를 묻는 문항만 건너뛴다 — 정답이 이미 訓音이라 덧붙일 게 없다.
     */
    private fun withGloss(item: Item): Item {
        val a = item.answer
        if (item.target?.length == 1 && a != null && ' ' in a && a.none(::isHanja)) return item

        val seen = LinkedHashSet<String>()
        listOfNotNull(item.question, item.target, a).forEach { src ->
            src.filter(::isHanja).forEach { seen.add(nfc(it)) }
        }
        val marks = seen.mapNotNull { c -> gloss(c)?.let { "$c $it" } }
        return if (marks.isEmpty()) item else item.copy(gloss = marks.joinToString("\n"))
    }

    private fun gloss(han: String): String? = db.rawQuery(
        "SELECT gloss FROM hunmeum WHERE han=?", arrayOf(han)
    ).use { c -> if (c.moveToNext()) c.getString(0) else null }

    private fun items(sectionId: Int): List<Item> = db.rawQuery(
        "SELECT no, span_end, question, question_html, target, answer FROM items" +
            " WHERE section_id=? ORDER BY no", arrayOf(sectionId.toString())
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    Item(
                        no = c.getInt(0),
                        spanEnd = c.getInt(1),
                        question = c.getString(2) ?: "",
                        html = c.getString(3),
                        target = c.getString(4),
                        answer = c.getString(5),
                    )
                )
            }
        }
    }

    fun meta(): Map<String, String> = db.rawQuery("SELECT key, value FROM meta", null).use { c ->
        buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
    }

    fun close() = db.close()

    companion object {
        /** 폰에서 들여온 파일을 읽기 전용으로 연다. 스키마가 아니면 null. */
        fun open(file: java.io.File): ExamDb? = try {
            val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
            db.rawQuery("SELECT count(*) FROM items", null).use { it.moveToFirst(); it.getInt(0) }
            ExamDb(db)
        } catch (_: Exception) {
            null
        }
    }
}
