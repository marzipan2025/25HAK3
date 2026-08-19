package com.artbrain.hak3

import android.content.Context

/**
 * 손에 맞춰 두는 것들. 지금은 바닥 두 단추의 좌우 뿐이다.
 * 기록(Marks)과 달리 회차를 가리지 않으므로 한 자리에 둔다.
 */
object Settings {
    private const val PREFS = "settings"
    private const val KEY_MARK_LEFT = "mark_left"

    /** 노란 판정 단추를 왼쪽에 둘 것인가. 기본은 오른쪽이다. */
    fun markOnLeft(c: Context): Boolean =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_MARK_LEFT, false)

    fun setMarkOnLeft(c: Context, left: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_MARK_LEFT, left).apply()
    }
}
