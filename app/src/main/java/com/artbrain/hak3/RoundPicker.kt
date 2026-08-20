package com.artbrain.hak3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 첫 화면은 회차 목록뿐이다. 이름도 판 번호도 적지 않는다 — 그런 것들은 설정에 있다.
 * 새 판이 나온 것만 위에서 내려오는 알림으로 알린다.
 */
@Composable
fun RoundPicker(exams: List<ExamRow>, onPick: (Int) -> Unit) {
    // 상세 화면 카드와 같은 곡률
    val radius = (screenCornerRadius() - 8.dp).coerceAtLeast(0.dp)
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(112.dp),
            // 좌우 여백은 상세 화면 카드와 같은 8dp
            contentPadding = PaddingValues(8.dp, 8.dp, 8.dp, 32.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items(exams, key = { it.round }) { e -> RoundCell(e, radius, onPick) }
        }
        UpdateSnack(Modifier.align(Alignment.TopCenter))
    }
}

@Composable
private fun RoundCell(e: ExamRow, radius: Dp, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val counts = remember(e.round) { Marks.counts(context, e.round) }
    val live = e.complete || e.items > 0
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Hak3.Rule, RoundedCornerShape(radius))
            .background(Hak3.Surface, RoundedCornerShape(radius))
            .clickable(enabled = live) { onPick(e.round) }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "${e.round}",
            fontFamily = ThinHanja,
            fontWeight = FontWeight.Thin,
            fontSize = 40.sp,
            color = if (live) Hak3.Hanja else Hak3.HanjaDim,
        )
        Spacer(Modifier.height(6.dp))
        // 갈라 둔 게 있으면 날짜 자리에 그 개수를 대신 적는다 — 셀 높이는 늘 같다
        if (counts.any) {
            // 노랑 몇 · 초록 몇. 한쪽만 있으면 빗금도 없다.
            Text(
                buildAnnotatedString {
                    if (counts.amber > 0) {
                        withStyle(SpanStyle(color = Hak3.Amber)) { append("${counts.amber}") }
                    }
                    if (counts.amber > 0 && counts.known > 0) {
                        withStyle(SpanStyle(color = Hak3.TextDim)) { append(" / ") }
                    }
                    if (counts.known > 0) {
                        withStyle(SpanStyle(color = Hak3.Green)) { append("${counts.known}") }
                    }
                },
                fontSize = 13.sp,
            )
        } else {
            Text(
                if (live) e.date?.replace('-', '.') ?: "" else "본문 없음",
                fontSize = 11.sp,
                color = Hak3.TextDim,
                textAlign = TextAlign.Center,
            )
        }
    }
}


/**
 * 판 번호를 적어 두고, 새 판이 있으면 눌러서 받도록 한다.
 * 받은 뒤에는 시스템 설치 화면이 뜨고, 설치할지는 거기서 사용자가 정한다.
 */
/**
 * 새 판이 있을 때만 위에서 미끄러져 내려온다. 목록을 가리지 않도록 한 줄로 두고,
 * 누르면 받아서 설치까지 이어진다. 받는 동안에는 그 자리에서 몇 %인지 알린다.
 */
@Composable
private fun UpdateSnack(modifier: Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Updater.Status>(Updater.Status.Checking) }
    var progress by remember { mutableStateOf(-2f) }   // -2 = 받기 전

    LaunchedEffect(Unit) { status = Updater.check(BuildConfig.VERSION_NAME) }

    // 사라지는 동안에도 무엇을 적을지 알아야 하므로 한 번 잡은 것은 들고 있는다
    val fresh = (status as? Updater.Status.Available)?.release
    var shown by remember { mutableStateOf<Updater.Release?>(null) }
    LaunchedEffect(fresh) { if (fresh != null) shown = fresh }

    AnimatedVisibility(
        visible = fresh != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        val release = shown ?: return@AnimatedVisibility
        Text(
            when {
                progress == -2f -> "새 판 ${release.version} 받기"
                progress < 0f -> "받는 중…"
                progress < 1f -> "받는 중 ${(progress * 100).toInt()}%"
                else -> "설치를 눌러 주세요"
            },
            fontSize = 14.sp,
            color = Hak3.Amber,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(8.dp)
                .fillMaxWidth()
                .background(Hak3.Surface, CircleShape)
                .border(Dp.Hairline, Hak3.Amber.copy(alpha = 0.5f), CircleShape)
                .clickable(enabled = progress == -2f) {
                    val url = release.apkUrl
                    if (url == null) {
                        Updater.openReleasesPage(context)
                        return@clickable
                    }
                    progress = -1f
                    scope.launch {
                        val apk = Updater.download(context, url, release.version) { progress = it }
                        if (apk == null) {
                            progress = -2f
                            Updater.openReleasesPage(context)
                        } else {
                            progress = 1f
                            Updater.install(context, apk)
                        }
                    }
                }
                .padding(horizontal = 18.dp, vertical = 15.dp),
        )
    }
}
