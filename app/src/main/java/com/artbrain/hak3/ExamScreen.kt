package com.artbrain.hak3

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val HANJA = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")
private val OUTER = 4.dp

private class Page(val section: Section, val item: Item)

/**
 * 문항을 '묻는 식'과 '딸린 설명'으로 가른다.
 *   收穫            + 밑줄 친 지문
 *   竊(  )          + 밑줄 친 지문
 *   銳(  ) ↔ 鈍濁    (설명 없음)
 *   漸入佳(  )       + 들어갈수록 점점 경치가 좋음.
 * 앞쪽은 크게 세우고 뒤쪽은 본문 크기로 붙인다.
 */
private fun split(item: Item): Pair<String, String?> {
    val body = item.html ?: item.question
    val plain = body.replace(Regex("</?u>"), "")
    val target = item.target
    // 지문 안의 한 낱말을 묻는 문항 — 낱말이 머리, 지문이 꼬리
    if (target != null && plain != target) return target to body
    // '식 : 뜻풀이' / '식 - 뜻풀이' 로 적힌 문항
    Regex("\\s[:\\-]\\s").find(plain)?.let { m ->
        return plain.take(m.range.first).trim() to plain.substring(m.range.last + 1).trim()
    }
    return plain to null
}

private fun headSize(len: Int) = when {
    len <= 2 -> 98.sp
    len <= 4 -> 78.sp
    len <= 7 -> 56.sp
    len <= 12 -> 42.sp
    else -> 32.sp
}


@Composable
fun ExamScreen(round: Int, db: ExamDb, onBack: () -> Unit) {
    val context = LocalContext.current
    val all = remember(round) {
        db.sections(round).flatMap { s -> s.items.map { Page(s, it) } }
    }
    val marks = remember(round) { Marks(context.applicationContext, round) }
    var filter by remember(round) { mutableStateOf<Mark?>(null) }
    val open = remember(round) { mutableStateMapOf<Int, Boolean>() }

    // 필터를 걸면 그 목록에 담긴 문항만 넘긴다. 목록에서 빠지면 이 리스트도 곧바로 줄어든다.
    val pages by remember(all) {
        derivedStateOf {
            filter?.let { f -> all.filter { marks.state[it.item.no] == f } } ?: all
        }
    }
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val radius = (screenCornerRadius() - OUTER).coerceAtLeast(0.dp)
    val index = pager.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val page = pages.getOrNull(index)

    Column(
        Modifier
            .fillMaxSize()
            .background(Hak3.Ground)
            .padding(top = OUTER)
    ) {
        TopBar(
            round = round,
            section = page?.section,
            filter = filter,
            amber = marks.count(Mark.AMBER),
            red = marks.count(Mark.RED),
            onFilter = { f ->
                filter = if (filter == f) null else f
                scope.launch { pager.scrollToPage(0) }
            },
            onBack = onBack,
        )

        if (pages.isEmpty()) {
            EmptyList(filter, Modifier.weight(1f).padding(horizontal = OUTER), radius)
        } else {
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = OUTER),
                pageSpacing = 6.dp,
            ) { i ->
                pages.getOrNull(i)?.let { p ->
                    QuestionPage(
                        page = p,
                        revealed = open[p.item.no] == true,
                        mark = marks.state[p.item.no],
                        border = filter,
                        radius = radius,
                    ) { open[p.item.no] = open[p.item.no] != true }
                }
            }
        }

        // 카드와 바닥 줄 사이는 판정 원 지름의 1/4
        Spacer(Modifier.height(BAR / 4))

        BottomBar(
            filter = filter,
            enabled = page != null,
            label = page?.item?.label ?: "",
            lastNo = if (filter == null) all.lastOrNull()?.item?.spanEnd else null,
            index = index,
            total = pages.size,
            onSeek = { scope.launch { pager.scrollToPage(it) } },
        ) { chosen ->
            val no = page?.item?.no ?: return@BottomBar
            marks.set(no, chosen)
            // 평상시에는 표시하고 다음 문항으로. 필터 중에는 이 문항이 목록에서
            // 빠지면서 다음 문항이 저절로 그 자리로 올라온다.
            if (filter == null && index < pages.size - 1) {
                scope.launch { pager.animateScrollToPage(index + 1) }
            }
        }
    }
}

@Composable
private fun TopBar(
    round: Int,
    section: Section?,
    filter: Mark?,
    amber: Int,
    red: Int,
    onFilter: (Mark) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterDot(Hak3.Amber, filter == Mark.AMBER, amber) { onFilter(Mark.AMBER) }
        Spacer(Modifier.width(8.dp))
        FilterDot(Hak3.Red, filter == Mark.RED, red) { onFilter(Mark.RED) }
        Spacer(Modifier.width(14.dp))
        Text("제${round}회", fontSize = 15.sp, color = Hak3.Text)
        if (section != null) {
            Spacer(Modifier.width(9.dp))
            Text(
                "問 ${section.start}–${section.end}",
                fontSize = 12.sp,
                letterSpacing = 0.8.sp,
                color = Hak3.Hanja,
            )
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(DOT)
                .background(Hak3.Rule, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("✕", fontSize = 13.sp, color = Hak3.Text)
        }
    }
}

/** 목록 필터 단추. 원 한가운데에 담긴 문항 수를 적는다. */
@Composable
private fun FilterDot(color: Color, on: Boolean, count: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .size(DOT)
            .background(color, CircleShape)
            // 켜져 있으면 흰 테를 두른다 — 아래 판정 원과 색이 같으므로 상태는 테로 가른다
            .border(if (on) 2.dp else 0.dp, if (on) Color.White else Color.Transparent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (count > 0) {
            Text("$count", fontSize = 12.sp, color = Hak3.Ground)
        }
    }
}

@Composable
private fun EmptyList(filter: Mark?, modifier: Modifier, radius: Dp) {
    Box(
        modifier
            .fillMaxWidth()
            .background(Hak3.Surface, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (filter == Mark.AMBER) "애매한 문항이 없습니다." else "모르는 문항이 없습니다.",
            fontSize = 16.sp,
            color = Hak3.TextDim,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QuestionPage(
    page: Page,
    revealed: Boolean,
    mark: Mark?,
    border: Mark?,
    radius: Dp,
    onTap: () -> Unit,
) {
    val item = page.item
    val edge = when (border) {
        Mark.AMBER -> Hak3.Amber
        Mark.RED -> Hak3.Red
        null -> Hak3.Rule
    }
    Box(
        Modifier
            .fillMaxSize()
            .background(Hak3.Surface, RoundedCornerShape(radius))
            .border(if (border == null) 1.dp else 2.dp, edge, RoundedCornerShape(radius))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 26.dp),
        ) {
            Text(
                page.section.instruction,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = Hak3.TextDim,
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.label, fontSize = 17.sp, color = Hak3.TextDim)
                // 필터를 걸지 않았을 때도 이 문항이 어느 목록에 있는지 보이게 한다
                if (mark != null && border == null) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(9.dp)
                            .background(
                                if (mark == Mark.AMBER) Hak3.Amber else Hak3.Red,
                                CircleShape,
                            )
                    )
                }
            }
            Spacer(Modifier.height(18.dp))

            // 묻는 것은 언제나 같은 자리, 같은 글꼴로 크게. 길이에 따라 크기만 준다.
            val (head, tail) = split(item)
            Text(
                head,
                fontFamily = ThinHanja,
                fontWeight = FontWeight.Thin,
                fontSize = headSize(head.length),
                lineHeight = headSize(head.length) * 1.18f,
                color = Hak3.Hanja,
            )
            if (tail != null) {
                Spacer(Modifier.height(18.dp))
                Text(
                    underlined(tail, Hak3.Hanja),
                    fontSize = 22.sp,
                    lineHeight = 36.sp,
                    color = Hak3.TextDim,
                )
            }

            Spacer(Modifier.height(26.dp))
            AnswerSlot(item, revealed)
        }
    }
}

@Composable
private fun AnswerSlot(item: Item, revealed: Boolean) {
    if (!revealed) {
        Text(
            "눌러서 정답 보기",
            fontSize = 14.sp,
            letterSpacing = 0.5.sp,
            color = Hak3.TextDim,
            modifier = Modifier
                .border(1.dp, Hak3.Rule, RoundedCornerShape(9.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
        return
    }
    val a = item.answer
    val hanja = a != null && HANJA.containsMatchIn(a)
    Column(
        Modifier
            .background(Hak3.MarkSoft, RoundedCornerShape(9.dp))
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Text(
            a ?: "정답 없음",
            fontFamily = if (hanja) ThinHanja else FontFamily.Default,
            fontWeight = if (hanja) FontWeight.Thin else FontWeight.Normal,
            fontSize = if (hanja) 56.sp else 30.sp,
            lineHeight = if (hanja) 70.sp else 40.sp,
            color = if (a != null) Hak3.Mark else Hak3.TextDim,
        )
        item.gloss?.let { g ->
            Spacer(Modifier.height(6.dp))
            Text(g, fontSize = 17.sp, lineHeight = 27.sp, color = Hak3.MarkDim)
        }
    }
}

private class Verdict(val color: Color, val mark: Mark?)

private val BAR = 64.dp
private val DOT = 28.dp

/**
 * 화면 바닥에 붙는 한 줄 — 왼쪽 판정 원, 가운데 슬라이더, 오른쪽 판정 원.
 * 셋이 너비를 꽉 채운다. 문항 번호는 슬라이더 한가운데에 얹는다.
 *
 * 판정 단추가 무엇을 묻는지는 지금 보고 있는 목록에 따라 달라진다.
 *   평상시    – 애매하게 모름(노랑) / 아예 모름(빨강)
 *   노랑 목록 – 외웠음(초록) / 아예 모름(빨강, 빨강 목록으로 넘김)
 *   빨강 목록 – 외웠음(초록) / 애매하게 앎(노랑, 노랑 목록으로 넘김)
 * 어느 경우든 누르면 지금 보고 있는 목록에서는 빠진다.
 */
@Composable
private fun BottomBar(
    filter: Mark?,
    enabled: Boolean,
    label: String,
    lastNo: Int?,
    index: Int,
    total: Int,
    onSeek: (Int) -> Unit,
    onPick: (Mark?) -> Unit,
) {
    val pair = when (filter) {
        null -> listOf(Verdict(Hak3.Amber, Mark.AMBER), Verdict(Hak3.Red, Mark.RED))
        Mark.AMBER -> listOf(Verdict(Hak3.Green, null), Verdict(Hak3.Red, Mark.RED))
        Mark.RED -> listOf(Verdict(Hak3.Green, null), Verdict(Hak3.Amber, Mark.AMBER))
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(BAR),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VerdictDot(pair[0], enabled, onPick)
        Scrubber(
            Modifier.weight(1f),
            index = index,
            total = total,
            text = if (lastNo != null) "$label / $lastNo" else "${label}번 · ${index + 1} / $total",
            onSeek = onSeek,
        )
        VerdictDot(pair[1], enabled, onPick)
    }
}

@Composable
private fun VerdictDot(v: Verdict, enabled: Boolean, onPick: (Mark?) -> Unit) {
    Box(
        Modifier
            .size(BAR)
            .background(if (enabled) v.color else v.color.copy(alpha = 0.2f), CircleShape)
            .clickable(enabled = enabled) { onPick(v.mark) }
    )
}

/**
 * 채워진 부분이 0%에서는 높이와 같은 지름의 정원, 100%에서는 가운데 영역을
 * 꽉 채우는 알약이 된다. 손잡이를 따로 두지 않고 채워진 끝이 곧 위치다.
 */
@Composable
private fun Scrubber(
    modifier: Modifier,
    index: Int,
    total: Int,
    text: String,
    onSeek: (Int) -> Unit,
) {
    val inset = 4.dp                       // 아래 트랙이 이만큼 테두리로 보인다
    BoxWithConstraints(
        modifier
            .height(BAR)
            .background(Hak3.Surface, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val d = LocalDensity.current
        val knob = BAR - inset * 2         // 0%일 때의 정원 지름
        val span = (maxWidth - inset * 2 - knob).coerceAtLeast(0.dp)
        val frac = if (total <= 1) 0f else index.toFloat() / (total - 1)
        val insetPx = with(d) { inset.toPx() }
        val knobPx = with(d) { knob.toPx() }
        val spanPx = with(d) { span.toPx() }
        val seek: (Float) -> Unit = { x ->
            val at = if (spanPx <= 0f) 0f else (x - insetPx - knobPx) / spanPx
            onSeek((at.coerceIn(0f, 1f) * (total - 1)).roundToInt())
        }
        Box(
            Modifier
                .matchParentSize()
                .padding(inset),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .width(knob + span * frac)
                    .height(knob)
                    .background(Hak3.Hanja, CircleShape)
            )
        }
        Text(text, fontSize = 14.sp, color = Hak3.Text)
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(total, maxWidth) {
                    detectTapGestures { seek(it.x) }
                }
                .pointerInput(total, maxWidth) {
                    detectHorizontalDragGestures { change, _ -> seek(change.position.x) }
                }
        )
    }
}

/** question_html 의 <u> 표시를 그대로 밑줄로 옮긴다. */
private fun underlined(html: String, mark: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < html.length) {
        val open = html.indexOf("<u>", i)
        if (open < 0) {
            append(html.substring(i)); return@buildAnnotatedString
        }
        append(html.substring(i, open))
        val close = html.indexOf("</u>", open)
        if (close < 0) {
            append(html.substring(open + 3)); return@buildAnnotatedString
        }
        withStyle(SpanStyle(color = mark, textDecoration = TextDecoration.Underline)) {
            append(html.substring(open + 3, close))
        }
        i = close + 4
    }
}
