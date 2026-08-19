package com.artbrain.hak3

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.Build
import android.view.RoundedCorner

/** 01haka 다크 모드에서 그대로 가져온 값 (ContentView.swift). */
object Hak3 {
    val Ground = Color(0xFF000000)          // 기기 화면과 이어지는 검은 바탕
    val Surface = Color(0xFF21252D)         // 카드
    val SurfaceHi = Color(0xFF262B34)       // 눌린 카드
    val Hanja = Color(0xD9A8BAD6)           // 한자 (#A8BAD6 @85%)
    val HanjaDim = Color(0xCC647185)        // 흐린 한자
    val Rule = Color(0x24BAD0E2)            // 경계선 틴트 (#BAD0E2 @14%)
    val Text = Color(0xF2FFFFFF)
    val TextDim = Color(0x61FFFFFF)         // 보조 텍스트 (백색 38%)
    val Mark = Color(0xFFFFBD2E)            // 정답 – 01haka 신호등 앰버
    val MarkSoft = Color(0x1FFFBD2E)
    val MarkDim = Color(0x99FFBD2E)         // 정답 밑 訓音

    // 01haka 신호등 세 색. 애매/모름/외움을 가른다.
    val Amber = Color(0xFFFFBD2E)           // 애매하게 모름
    val Red = Color(0xFFFF6157)             // 잘못된 것을 알릴 때
    val Green = Color(0xFF29C745)           // 외웠음
}

/**
 * 기기 화면의 실제 라운딩 반경. Android 12부터 WindowInsets로 읽을 수 있다.
 * 카드를 outerMargin 만큼 안으로 들이면 반경도 그만큼 줄여야 화면 곡률과 동심원이 된다.
 *
 * 창이 붙기 전에는 insets 가 비어 있다. 첫 조합에서 한 번만 읽고 말면 그 빈 값이
 * 굳어 fallback 이 그대로 남으므로, 값이 올 때까지 몇 프레임 더 들여다본다.
 * 기기 곡률이 fallback 보다 큰 폰에서는 이 차이가 눈에 띈다.
 */
@Composable
fun screenCornerRadius(fallback: Dp = 32.dp): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    val px by produceState<Int?>(null, view) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return@produceState
        repeat(60) {
            val r = view.rootWindowInsets
                ?.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius
            if (r != null && r > 0) {
                value = r
                return@produceState
            }
            withFrameNanos { }
        }
    }
    return px?.let { with(density) { it.toDp() } } ?: fallback
}

/** 한자를 크고 얇게 띄우기 위한 Thin(w100) 서체. 시스템 CJK에는 이 굵기가 없다. */
val ThinHanja = FontFamily(Font(R.font.noto_sans_kr_thin, FontWeight.Thin))

private val scheme = darkColorScheme(
    primary = Hak3.Hanja,
    background = Hak3.Ground,
    surface = Hak3.Surface,
    onBackground = Hak3.Text,
    onSurface = Hak3.Text,
)

private val type = Typography(
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    labelSmall = TextStyle(fontSize = 11.sp, letterSpacing = 0.8.sp),
)

@Composable
fun Hak3Theme(content: @Composable () -> Unit) {
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()  // 앱은 다크 톤 하나로 간다
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
