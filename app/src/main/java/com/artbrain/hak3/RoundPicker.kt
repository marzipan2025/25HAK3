package com.artbrain.hak3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun RoundPicker(exams: List<ExamRow>, meta: Map<String, String>, onPick: (Int) -> Unit) {
    val span = exams.mapNotNull { it.date?.take(4) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 14.dp, top = 12.dp)) {
                Text(
                    "한자능력검정 ${meta["grade"] ?: "3급"}",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Normal,
                    color = Hak3.Text,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "기출 ${exams.size}회 · ${span.minOrNull() ?: ""}–${span.maxOrNull() ?: ""}" +
                        (meta["built"]?.let { " · 데이터 $it" } ?: ""),
                    fontSize = 12.sp,
                    color = Hak3.TextDim,
                )
                Spacer(Modifier.height(10.dp))
                UpdateLine()
            }
        }
        items(exams, key = { it.round }) { e -> RoundCell(e, onPick) }
    }
}

@Composable
private fun RoundCell(e: ExamRow, onPick: (Int) -> Unit) {
    val context = LocalContext.current
    val counts = remember(e.round) { Marks.counts(context, e.round) }
    val live = e.complete || e.items > 0
    Column(
        Modifier
            .fillMaxWidth()
            .border(1.dp, Hak3.Rule, RoundedCornerShape(10.dp))
            .background(Hak3.Surface, RoundedCornerShape(10.dp))
            .clickable(enabled = live) { onPick(e.round) }
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${e.round}",
                fontFamily = ThinHanja,
                fontWeight = FontWeight.Thin,
                fontSize = 40.sp,
                color = if (live) Hak3.Hanja else Hak3.HanjaDim,
            )
            Text(
                "회",
                fontSize = 12.sp,
                color = Hak3.TextDim,
                modifier = Modifier.padding(start = 3.dp, bottom = 7.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        // 갈라 둔 게 있으면 날짜 자리에 그 개수를 대신 적는다 — 셀 높이는 늘 같다
        if (counts.any) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tally(Hak3.Green, counts.known)
                Tally(Hak3.Amber, counts.amber)
                Tally(Hak3.Red, counts.red)
            }
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

@Composable
private fun Tally(color: Color, n: Int) {
    if (n == 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text("$n", fontSize = 11.sp, color = color)
    }
}

/**
 * 판 번호를 적어 두고, 새 판이 있으면 눌러서 받도록 한다.
 * 받은 뒤에는 시스템 설치 화면이 뜨고, 설치할지는 거기서 사용자가 정한다.
 */
@Composable
private fun UpdateLine() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<Updater.Status>(Updater.Status.Checking) }
    var progress by remember { mutableStateOf(-2f) }   // -2 = 받기 전

    LaunchedEffect(Unit) { status = Updater.check(BuildConfig.VERSION_NAME) }

    val here = "v ${BuildConfig.VERSION_NAME}"
    val fresh = status as? Updater.Status.Available

    if (fresh == null) {
        Text(here, fontSize = 11.sp, color = Hak3.TextDim)
        return
    }
    Text(
        when {
            progress == -2f -> "$here · ${fresh.release.version} 새 판 받기"
            progress < 0f -> "받는 중…"
            progress < 1f -> "받는 중 ${(progress * 100).toInt()}%"
            else -> "설치를 눌러 주세요"
        },
        fontSize = 11.sp,
        color = Hak3.Amber,
        modifier = Modifier
            .border(1.dp, Hak3.Amber.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .clickable(enabled = progress == -2f) {
                val url = fresh.release.apkUrl
                if (url == null) {
                    Updater.openReleasesPage(context)
                    return@clickable
                }
                progress = -1f
                scope.launch {
                    val apk = Updater.download(context, url, fresh.release.version) { progress = it }
                    if (apk == null) {
                        progress = -2f
                        Updater.openReleasesPage(context)
                    } else {
                        progress = 1f
                        Updater.install(context, apk)
                    }
                }
            }
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}
