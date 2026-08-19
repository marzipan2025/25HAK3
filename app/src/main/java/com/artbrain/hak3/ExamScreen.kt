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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.animation.core.AnimationSpec
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private val HANJA = Regex("[\\u3400-\\u4DBF\\u4E00-\\u9FFF\\uF900-\\uFAFF]")

/**
 * 줄은 띄어쓰기에서만 갈린다.
 *
 * 한글은 글자 하나하나가 줄바꿈 자리가 될 수 있어서 그냥 두면 '바 / 람직한' 처럼
 * 낱말 한가운데가 갈린다. 글자 사이에 이음쇠(U+2060)를 끼워 그 자리를 막으면
 * 남는 자리는 띄어쓰기뿐이다. 이음쇠는 폭이 없어 눈에도, 글자 수에도 잡히지 않는다.
 * 한 낱말이 한 줄보다 길면 그때는 안드로이드가 알아서 잘라 준다.
 */
private const val GLUE = '\u2060'

private fun glue(s: String, before: Char = ' '): String = buildString {
    var prev = before
    for (c in s) {
        if (!c.isWhitespace() && !prev.isWhitespace()) append(GLUE)
        append(c)
        prev = c
    }
}
private val OUTER = 8.dp

private class Page(val section: Section, val item: Item)

private fun borderColor(m: Mark?) = when (m) {
    Mark.AMBER -> Hak3.Amber
    Mark.KNOWN -> Hak3.Green
    null -> Hak3.Rule
}

/**
 * 표시는 한 축 위에 놓인다 — 노랑(애매) ← 일반 → 초록(외움).
 * 위로 밀면 초록 쪽으로 한 칸, 아래로 밀면 노랑 쪽으로 한 칸.
 * 양 끝에서 더 밀어도 그 자리에 머문다.
 */
private fun step(m: Mark?, up: Boolean): Mark? = if (up) when (m) {
    Mark.AMBER -> null
    null -> Mark.KNOWN
    Mark.KNOWN -> Mark.KNOWN
} else when (m) {
    Mark.KNOWN -> null
    null -> Mark.AMBER
    Mark.AMBER -> Mark.AMBER
}

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

/**
 * 묻는 말이 차지하는 폭을 어림한다. 한자와 한글은 한 칸, 괄호·기호는 반 칸,
 * 빈칸은 그보다 좁게 친다. `( )却` 처럼 괄호가 절반인 문항이 글자 수만으로는
 * 길어 보여 쓸데없이 작아지던 것을 막는다.
 */
private fun span(s: String): Float {
    var w = 0f
    for (c in s) {
        w += when {
            c.isWhitespace() -> 0.35f
            c in '\uAC00'..'\uD7A3' -> 1f          // 한글
            c in '\u3400'..'\u9FFF' -> 1f          // 한자
            c in '\uF900'..'\uFAFF' -> 1f          // 호환 한자
            else -> 0.5f                           // 괄호·기호·숫자
        }
    }
    return w
}

private fun headSize(w: Float) = when {
    w <= 2.2f -> 98.sp
    w <= 3.2f -> 88.sp
    w <= 4.4f -> 66.sp
    w <= 6.2f -> 52.sp
    w <= 8.5f -> 42.sp
    else -> 34.sp
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

    // 목록을 열면 그때의 구성을 붙잡아 둔다. 판정으로 목록에서 빠져도 카드는 그 자리에
    // 남아, 노랑을 한 번 올려 일반으로, 한 번 더 올려 초록으로 이어 갈 수 있다.
    // 담긴 수는 위 필터 원의 숫자가 곧바로 알려 준다.
    var listed by remember(round) { mutableStateOf<List<Page>?>(null) }
    val pages = listed ?: all
    val start = remember(round, all) {
        val no = Marks.lastSeen(context, round)
        all.indexOfFirst { it.item.no == no }.coerceAtLeast(0)
    }
    val pager = rememberPagerState(initialPage = start, pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val radius = (screenCornerRadius() - OUTER).coerceAtLeast(0.dp)
    // 카드를 들고 있는 동안만 카드층을 맨 위로. 그동안 두 줄은 눌리지 않는다.
    var lifted by remember(round) { mutableStateOf(false) }
    var settings by remember(round) { mutableStateOf(false) }
    var markOnLeft by remember(round) { mutableStateOf(Settings.markOnLeft(context)) }
    val index = pager.currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    val page = pages.getOrNull(index)
    // 이 회차를 다음에 열 때 여기서부터 보여 준다
    LaunchedEffect(round, page?.item?.no) {
        page?.item?.no?.let { Marks.setLastSeen(context, round, it) }
    }

    // 카드가 캡슐 위로 날아가야 하므로 pager 를 화면 전체로 깔고, 카드만 캡슐·바닥 줄
    // 안쪽으로 밀어 둔다. pager 는 제 영역 밖을 잘라내기 때문에 이렇게 하지 않으면
    // 카드가 캡슐께에서 잘려 사라진다. 바닥 줄은 pager 뒤에 두어 조작을 뺏기지 않는다.
    // 위로는 캡슐과 OUTER 만큼, 아래로도 바닥 줄과 OUTER 만큼. 같은 간격이다.
    val inset = PaddingValues(top = TOP + OUTER, bottom = BAR + OUTER)
    Box(
        Modifier
            .fillMaxSize()
            .background(Hak3.Ground)
            .padding(OUTER)
    ) {
        if (pages.isEmpty()) {
            EmptyList(filter, Modifier.fillMaxSize().padding(inset), radius)
        } else {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize().zIndex(if (lifted) 1f else 0f),
                pageSpacing = 6.dp,
            ) { i ->
                pages.getOrNull(i)?.let { p ->
                    Box(Modifier.fillMaxSize().padding(inset)) {
                        QuestionPage(
                            page = p,
                            revealed = open[p.item.no] == true,
                            mark = marks.state[p.item.no],
                            radius = radius,
                            onLifted = { lifted = it },
                            onMark = { m -> marks.set(p.item.no, m) },
                            // 표시가 실제로 바뀌었을 때만 넘어간다. 양 끝에서 더 민
                            // 경우(초록을 또 위로)는 바뀐 게 없으니 그 자리에 머문다.
                            onAdvance = {
                                if (i < pages.size - 1) {
                                    scope.launch { pager.animateScrollToPage(i + 1) }
                                }
                            },
                        ) { open[p.item.no] = open[p.item.no] != true }
                    }
                }
            }
        }

        // 캡슐과 바닥 줄은 pager 뒤에 둔다 — 앞에 두면 pager 가 눌림을 가로챈다.
        // pager 는 화면 전체를 차지하므로 카드는 잘리지 않고 이 줄들 아래로 미끄러져 나간다.
        TopBar(
            modifier = Modifier.align(Alignment.TopStart),
            round = round,
            section = page?.section,
            filter = filter,
            amber = marks.count(Mark.AMBER),
            onFilter = { f ->
                val next = if (filter == f) null else f
                filter = next
                listed = next?.let { m -> all.filter { marks.state[it.item.no] == m } }
                scope.launch { pager.scrollToPage(0) }
            },
            onBack = onBack,
        )


        BottomBar(
            modifier = Modifier.align(Alignment.BottomStart),
            enabled = page != null,
            markOnLeft = markOnLeft,
            label = page?.item?.label ?: "",
            lastNo = if (filter == null) all.lastOrNull()?.item?.spanEnd else null,
            index = index,
            total = pages.size,
            onSeek = { scope.launch { pager.scrollToPage(it) } },
        ) {
            val no = page?.item?.no ?: return@BottomBar
            marks.set(no, Mark.AMBER)
            if (index < pages.size - 1) {
                scope.launch { pager.animateScrollToPage(index + 1) }
            }
        }

        // 설정을 열면 남색 단추만 남기고 모두 덮는다
        if (settings) {
            SettingsPanel(
                built = db.meta()["built"],
                markOnLeft = markOnLeft,
                onMarkSide = { left ->
                    markOnLeft = left
                    Settings.setMarkOnLeft(context, left)
                },
            )
            BackHandler { settings = false }
        }
        SettingsDot(
            Modifier.align(if (markOnLeft) Alignment.BottomEnd else Alignment.BottomStart),
        ) { settings = !settings }
    }
}

/**
 * 설정. 바닥의 남색 단추 하나만 남기고 화면을 덮는다.
 * 적는 말은 영어로 둔다 — 짧고, 줄바꿈에 흔들리지 않는다.
 */
@Composable
private fun SettingsPanel(
    built: String?,
    markOnLeft: Boolean,
    onMarkSide: (Boolean) -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Hak3.Ground)
            // 뒤의 카드가 눌리지 않게 이 층에서 손짓을 삼킨다
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {}
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column {
            Text("25HAK3", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Hak3.Text)
            Spacer(Modifier.height(10.dp))
            Text(
                "Data ${built ?: "unknown"}",
                fontSize = 14.sp,
                color = Hak3.TextDim,
            )
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                fontSize = 14.sp,
                color = Hak3.TextDim,
            )

            Spacer(Modifier.height(40.dp))
            Text(
                "MARK BUTTON",
                fontSize = 11.sp,
                letterSpacing = 1.2.sp,
                color = Hak3.TextDim,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Side("Left", markOnLeft) { onMarkSide(true) }
                Side("Right", !markOnLeft) { onMarkSide(false) }
            }
        }
    }
}

/** 고른 쪽은 앰버로 테를 두른다. */
@Composable
private fun Side(label: String, on: Boolean, onPick: () -> Unit) {
    Text(
        label,
        fontSize = 15.sp,
        color = if (on) Hak3.Amber else Hak3.TextDim,
        modifier = Modifier
            .border(1.dp, if (on) Hak3.Amber else Hak3.Rule, RoundedCornerShape(10.dp))
            .clickable(onClick = onPick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
private fun TopBar(
    modifier: Modifier,
    round: Int,
    section: Section?,
    filter: Mark?,
    amber: Int,
    onFilter: (Mark) -> Unit,
    onBack: () -> Unit,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(TOP)
            .background(Hak3.Surface, CircleShape)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterDot(Hak3.Amber, filter == Mark.AMBER, amber) { onFilter(Mark.AMBER) }
        }
        // 회차와 문항 구간은 한 덩이로 캡슐 한가운데
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("제${round}회", fontSize = 14.sp, color = Hak3.Text)
            if (section != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    "問 ${section.start}–${section.end}",
                    fontSize = 14.sp,
                    color = Hak3.Hanja,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.CenterEnd)
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
            when (filter) {
                Mark.AMBER -> "애매한 문항이 없습니다."
                else -> "외운 문항이 없습니다."
            },
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
    radius: Dp,
    onLifted: (Boolean) -> Unit,
    onMark: (Mark?) -> Unit,
    onAdvance: () -> Unit,
    onTap: () -> Unit,
) {
    val item = page.item
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    /*
     * 카드를 얼마나 들어 올렸는가. 문항이 바뀌면 처음부터.
     *
     * 손짓은 코루틴을 거치지 않고 이 값에 곧바로 쌓는다. 한 손짓마다 코루틴을 띄워
     * 옮기면, 손을 뗀 뒤에야 도착한 마지막 손짓이 제자리로 돌아가던 애니메이션을
     * 밀어내고 카드를 어중간한 자리에 세워 놓는다.
     */
    var lift by remember(item.no) { mutableFloatStateOf(0f) }
    var wide by remember(item.no) { mutableFloatStateOf(1000f) }
    // 잡은 손이 카드 가운데에서 얼마나 치우쳤는가. -1(왼쪽 끝) ~ +1(오른쪽 끝).
    var arm by remember(item.no) { mutableFloatStateOf(0f) }
    // 한 번 끄는 동안 판정은 한 번뿐이다. 문턱을 넘나들며 색이 뒤집히지 않게.
    var settled by remember(item.no) { mutableStateOf(false) }
    // 제자리로 돌아가는 중인 몸짓. 카드를 다시 잡으면 멈춘다.
    var homing by remember(item.no) { mutableStateOf<Job?>(null) }
    val reach = with(density) { REACH.toPx() }
    val tilt = with(density) { TILT.toPx() }
    // 잡은 자리를 축 삼아 도는 시늉. 왼쪽 아래를 잡고 올리면 오른쪽으로 기운다.
    // 문턱과 따로 두어 기울기는 예전 손맛 그대로다.
    val spin = (arm * (lift / tilt) * 5f).coerceIn(-12f, 12f)

    /** 어느 자리에 있든 0으로 돌려놓는다. 돌아가던 것이 있으면 그것부터 접는다. */
    fun home(spec: AnimationSpec<Float>, then: (suspend () -> Unit)? = null) {
        homing?.cancel()
        homing = scope.launch {
            animate(lift, 0f, animationSpec = spec) { v, _ -> lift = v }
            lift = 0f
            then?.invoke()
        }
    }

    // 들려 있는 동안에는 캡슐과 바닥 줄 위로 올라온다
    LaunchedEffect(lift != 0f) { onLifted(lift != 0f) }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = lift
                rotationZ = spin
            }
            .background(Hak3.Surface, RoundedCornerShape(radius))
            // 표시가 없으면 1픽셀, 담긴 카드는 1dp. 어느 쪽이든 카드 경계 안쪽에 붙는다.
            .border(if (mark == null) Dp.Hairline else 1.dp, borderColor(mark), RoundedCornerShape(radius))
            .onSizeChanged { wide = it.width.toFloat() }
            // 위로 밀면 초록 쪽으로, 아래로 밀면 노랑 쪽으로 한 칸. 카드는 제자리로 돌아온다.
            .draggable(
                state = rememberDraggableState { dy ->
                    if (settled) return@rememberDraggableState
                    lift += dy
                    // 판정은 손가락이 문턱을 넘는 그 자리에서 바로.
                    if (kotlin.math.abs(lift) > reach) {
                        settled = true
                        val turned = step(mark, lift < 0f)
                        if (turned != mark) onMark(turned)
                        home(RETURN) {
                            // 제자리에 앉는 것을 보고 나서 넘긴다
                            if (turned != mark) {
                                delay(HOLD)
                                onAdvance()
                            }
                        }
                    }
                },
                orientation = Orientation.Vertical,
                onDragStarted = { at ->
                    // 돌아가는 중이더라도 다시 잡으면 손을 따른다
                    homing?.cancel()
                    arm = ((at.x / wide) * 2f - 1f).coerceIn(-1f, 1f)
                },
                onDragStopped = {
                    // 문턱을 못 넘고 손을 뗐으면 제자리로. 넘었으면 이미 돌아가는 중이다.
                    if (!settled) home(SETTLE)
                    settled = false
                },
            )
            .pointerInput(item.no) {
                detectTapGestures { onTap() }
            },
        contentAlignment = Alignment.Center,
    ) {
        // 무엇을 묻는 문제인지는 카드 맨 위 한가운데 따로 세운다.
        // 아래 것들과 한 흐름으로 두지 않는다 — 자리도 정렬도 따로 간다.
        Text(
            glue(page.section.instruction),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = Hak3.TextDim,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth(0.7f)
                .padding(top = 14.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                // 기준점을 위로. 카드 한가운데가 아니라 그보다 60dp 높은 자리에 선다.
                .offset(y = -LEAD)
                .padding(horizontal = 24.dp, vertical = 26.dp),
        ) {
            // 어느 목록에 담겼는지는 번호에 입힌 색으로 알린다
            Text(
                item.label,
                fontSize = 17.sp,
                color = if (mark != null) borderColor(mark) else Hak3.TextDim,
            )
            Spacer(Modifier.height(6.dp))

            // 묻는 것은 언제나 같은 자리, 같은 글꼴로 크게. 길이에 따라 크기만 준다.
            // 한자부터 아래로는 한 덩이로 끌어올린다 — 번호와의 사이를 좁히기 위해서다.
            val (head, tail) = split(item)
            Column(Modifier.offset(y = -TIGHTEN)) {
                Text(
                    head,
                    fontFamily = ThinHanja,
                    fontWeight = FontWeight.Thin,
                    fontSize = headSize(span(head)),
                    lineHeight = headSize(span(head)) * 1.18f,
                    color = Hak3.Hanja,
                )
                if (tail != null) {
                    // 지문만 위로 당긴다. 아래 정답 자리는 그만큼 도로 벌려 두어
                    // 점과 정답의 좌표는 그대로 있게 한다.
                    Spacer(Modifier.height(2.dp))
                    Text(
                        underlined(tail, Hak3.Hanja),
                        fontSize = 22.sp,
                        lineHeight = 36.sp,
                        color = Hak3.TextDim,
                    )
                }

                Spacer(Modifier.height(if (tail != null) 38.dp else 26.dp))
                AnswerSlot(item, revealed)
            }
        }
    }
}

@Composable
private fun AnswerSlot(item: Item, revealed: Boolean) {
    val a = item.answer
    val hanja = a != null && HANJA.containsMatchIn(a)
    // 원은 언제나 같은 크기로 자리를 지킨다. 정답은 그 아래에 겹쳐 그리므로
    // 펼쳐도 위의 한자와 지문이 밀리지 않는다.
    Box(
        Modifier
            .size(DOT)
            .background(if (revealed) Hak3.Neon else Hak3.Rule, CircleShape)
    ) {
        if (!revealed) return@Box
        // 폭도 높이도 없는 자리를 하나 두고, 그 안에서만 제 크기를 갖게 한다.
        // 원의 28dp 제약에 갇히면 큰 글자가 잘린다.
        Box(
            Modifier
                .offset(x = DOT + 18.dp, y = DROP - (if (hanja) INK_HANJA else INK_HANGUL))
                .size(0.dp)
                .wrapContentSize(align = Alignment.TopStart, unbounded = true)
        ) {
        Column(Modifier.width(ANSWER)) {
            Text(
                a ?: "정답 없음",
                fontFamily = if (hanja) ThinHanja else FontFamily.Default,
                fontWeight = if (hanja) FontWeight.Thin else FontWeight.Normal,
                fontSize = if (hanja) 56.sp else 30.sp,
                lineHeight = if (hanja) 70.sp else 40.sp,
                color = if (a != null) Hak3.Neon else Hak3.TextDim,
                style = FLUSH_TOP,
            )
            item.gloss?.let { g ->
                Spacer(Modifier.height(6.dp))
                Text(g, fontSize = 17.sp, lineHeight = 27.sp, color = Hak3.Neon.copy(alpha = 0.66f))
            }
        }
        }
    }
}

private val BAR = 64.dp
private val DOT = 28.dp
private val TOP = 52.dp

/** 기준점을 카드 한가운데보다 이만큼 위로 올린다. */
private val LEAD = 60.dp

/** 번호와 한자 사이를 이만큼 좁힌다. 한자 아래의 것들도 함께 딸려 올라온다. */
private val TIGHTEN = 16.dp

/**
 * 첫 줄 위에 붙는 여백을 걷어낸다. 글자 상자의 윗변이 곧 글자의 윗선이 되어
 * 옆에 놓인 점과 눈으로 수평이 맞는다.
 */
private val FLUSH_TOP = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Top,
        trim = LineHeightStyle.Trim.FirstLineTop,
    ),
)

/**
 * 글자 상자의 윗변과 글자의 실제 윗선 사이에 남는 만큼. 서체가 글자 위에 두는
 * 여백이라 크기마다 다르다. 화면에서 재어 맞춘 값이다.
 */
private val INK_HANJA = 19.dp
private val INK_HANGUL = 10.dp

/** 윗선을 맞춘 뒤 눈에 맞게 조금 내려 앉히는 만큼. */
private val DROP = 4.dp

/** 펼친 정답이 쓰이는 폭. 원 아래에 겹쳐 그리므로 제 폭을 스스로 정한다. */
private val ANSWER = 260.dp

/** 이만큼 밀어야 판정이 한 칸 움직인다. */
private val REACH = 120.dp

/** 기울기의 기준. 문턱과 따로 두어 도는 맛은 예전 그대로 둔다. */
private val TILT = 60.dp

/** 판정하고 제자리로. 색이 바뀐 것을 보고 나서 천천히 내려앉는다. */
private val RETURN = tween<Float>(450, easing = FastOutSlowInEasing)

/** 문턱을 못 넘고 손을 뗐을 때. */
private val SETTLE = tween<Float>(320, easing = FastOutSlowInEasing)

/** 카드가 다 돌아온 뒤 다음 문항으로 넘어가기까지 쉬는 참. */
private const val HOLD = 260L

/**
 * 화면 바닥에 붙는 한 줄 — 왼쪽 설정, 가운데 슬라이더, 오른쪽 노랑 단추.
 * 셋이 너비를 꽉 채운다. 문항 번호는 슬라이더 한가운데에 얹는다.
 *
 * 초록(외웠음)은 단추로 두지 않는다. 카드를 위로 미는 것이 그 자리다.
 */
@Composable
private fun BottomBar(
    modifier: Modifier,
    enabled: Boolean,
    markOnLeft: Boolean,
    label: String,
    lastNo: Int?,
    index: Int,
    total: Int,
    onSeek: (Int) -> Unit,
    onAmber: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(BAR),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 설정 단추는 이 줄에 넣지 않고 화면 맨 위층에 따로 띄운다 — 설정을 열면
        // 그 단추만 남고 나머지가 덮이기 때문이다. 여기서는 자리만 비워 둔다.
        if (markOnLeft) AmberDot(enabled, onAmber) else Spacer(Modifier.size(BAR))
        Scrubber(
            Modifier.weight(1f),
            index = index,
            total = total,
            text = if (lastNo != null) "$label / $lastNo" else "${label}번 · ${index + 1} / $total",
            onSeek = onSeek,
        )
        if (markOnLeft) Spacer(Modifier.size(BAR)) else AmberDot(enabled, onAmber)
    }
}

/** 아이콘 없는 남색 원. 설정을 여닫는다. */
@Composable
private fun SettingsDot(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .size(BAR)
            .background(Hak3.Navy, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
private fun AmberDot(enabled: Boolean, onPick: () -> Unit) {
    Box(
        Modifier
            .size(BAR)
            .background(if (enabled) Hak3.Amber else Hak3.Amber.copy(alpha = 0.2f), CircleShape)
            .clickable(enabled = enabled, onClick = onPick)
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

/** 다음 <u> 또는 </u> 가 시작되는 자리. 없으면 끝. */
private fun nextTag(s: String, from: Int): Int {
    var k = from
    while (k < s.length) {
        if (s.startsWith("<u>", k) || s.startsWith("</u>", k)) return k
        k++
    }
    return s.length
}

/**
 * question_html 의 <u> 표시를 그대로 밑줄로 옮긴다.
 * 이음쇠는 뒤따르는 토막과 같은 꾸밈으로 붙여 밑줄이 끊기지 않게 한다.
 */
private fun underlined(html: String, mark: Color): AnnotatedString = buildAnnotatedString {
    val line = SpanStyle(color = mark, textDecoration = TextDecoration.Underline)
    var i = 0
    var open = false
    var prev = ' '
    while (i < html.length) {
        when {
            html.startsWith("<u>", i) -> { open = true; i += 3 }
            html.startsWith("</u>", i) -> { open = false; i += 4 }
            else -> {
                val j = nextTag(html, i)
                val chunk = html.substring(i, j)
                val glued = glue(chunk, prev)
                if (open) withStyle(line) { append(glued) } else append(glued)
                prev = chunk.lastOrNull() ?: prev
                i = j
            }
        }
    }
}
