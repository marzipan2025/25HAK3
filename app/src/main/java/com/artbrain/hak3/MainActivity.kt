package com.artbrain.hak3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Hak3Theme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Hak3.Ground)
                        .systemBarsPadding()
                ) {
                    Root()
                }
            }
        }
    }
}

@Composable
private fun Root() {
    val context = LocalContext.current
    var db by remember { mutableStateOf<ExamDb?>(null) }
    var state by remember { mutableStateOf<DataFile.Result?>(null) }
    var reload by remember { mutableStateOf(0) }
    var open by remember { mutableStateOf<Int?>(null) }
    var words by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            DataFile.remember(context, uri, "tree")
            reload++
        }
    }
    val pickFile = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            DataFile.remember(context, uri, "file")
            reload++
        }
    }

    LaunchedEffect(reload) {
        val r = DataFile.sync(context)
        state = r
        db?.close()
        db = (r as? DataFile.Result.Ok)?.let { ExamDb.open(it.file) }
        // 단어장을 만들기 전에 찍어 둔 노랑도 살려 둔다
        db?.let { Collect.seed(context, it) }
    }

    val ready = db
    if (ready == null) {
        Setup(
            state = state,
            onFolder = { pickFolder.launch(null) },
            onFile = { pickFile.launch(arrayOf("*/*")) },
        )
        return
    }

    val round = open
    when {
        words -> {
            BackHandler { words = false }
            WordScreen(ready) { words = false }
        }
        round != null -> {
            BackHandler { open = null }
            ExamScreen(round, ready) { open = null }
        }
        else -> RoundPicker(ready.exams(), onPick = { open = it }, onWords = { words = true })
    }
}

/** 데이터 파일이 아직 없을 때. 무엇을 어디에 두면 되는지만 적는다. */
@Composable
private fun Setup(state: DataFile.Result?, onFolder: () -> Unit, onFile: () -> Unit) {
    val trouble = when (state) {
        DataFile.Result.NoFolder, null -> null
        DataFile.Result.NoFile ->
            "그 폴더에서 ${DataFile.PREFIX}…${DataFile.SUFFIX} 파일을 찾지 못했습니다."
        is DataFile.Result.Failed -> state.why
        is DataFile.Result.Ok -> "파일을 읽었지만 기출 데이터 형식이 아닙니다."
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        Text("25HAK3", fontSize = 26.sp, color = Hak3.Text)
        Spacer(Modifier.height(14.dp))
        Text(
            "기출 데이터가 앱과 따로 있습니다.\n" +
                "다운로드 폴더에 25HAK3 폴더를 만들고 그 안에 " +
                "${DataFile.PREFIX}${DataFile.SUFFIX} 를 둔 뒤, 그 폴더를 지정해 주세요.\n" +
                "안드로이드가 다운로드 폴더 자체는 지정하지 못하게 막아 둔 탓입니다.",
            fontSize = 15.sp,
            lineHeight = 25.sp,
            color = Hak3.TextDim,
        )
        if (trouble != null) {
            Spacer(Modifier.height(12.dp))
            Text(trouble, fontSize = 14.sp, lineHeight = 21.sp, color = Hak3.Red)
        }
        Spacer(Modifier.height(24.dp))
        Box(
            Modifier
                .border(1.dp, Hak3.Amber, RoundedCornerShape(10.dp))
                .clickable(onClick = onFolder)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text("폴더 지정", fontSize = 15.sp, color = Hak3.Amber, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier
                .border(1.dp, Hak3.Rule, RoundedCornerShape(10.dp))
                .clickable(onClick = onFile)
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            Text("파일 직접 고르기", fontSize = 15.sp, color = Hak3.TextDim, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(18.dp))
        Text(
            "폴더를 지정해 두면 다음부터는 파일만 떨어뜨리면 됩니다. 파일을 직접 고른 " +
                "경우에는 같은 이름으로 덮어써야 계속 읽힙니다.",
            fontSize = 12.sp,
            lineHeight = 19.sp,
            color = Hak3.TextDim,
        )
    }
}
