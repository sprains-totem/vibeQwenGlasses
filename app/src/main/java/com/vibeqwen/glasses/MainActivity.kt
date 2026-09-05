package com.vibeqwen.glasses

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.vibeqwen.glasses.ui.connect.ConnectScreen
import com.vibeqwen.glasses.ui.connect.ConnectViewModel
import com.vibeqwen.glasses.ui.logs.LogScreen
import com.vibeqwen.glasses.ui.record.RecordScreen
import com.vibeqwen.glasses.ui.recordings.RecordingsScreen
import com.vibeqwen.glasses.ui.theme.VibeQwenTheme

/** 单 Activity 应用入口：底部四 Tab（连接 / 录音 / 录音库 / 日志） */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.vibeqwen.glasses.util.LogCollector.init(applicationContext)
        setContent {
            VibeQwenTheme {
                MainScreen()
            }
        }
    }
}

@Composable
private fun MainScreen() {
    val context = LocalContext.current
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 连接/录音动作前会再次校验权限 */ }

    // 启动即申请运行时权限（蓝牙连接 / 通知，按 API 分级）
    LaunchedEffect(Unit) {
        val missing = ConnectViewModel.requiredPermissions().filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Bluetooth, contentDescription = null) },
                    label = { Text("连接") },
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
                    label = { Text("录音") },
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.MusicNote, contentDescription = null) },
                    label = { Text("录音库") },
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Description, contentDescription = null) },
                    label = { Text("日志") },
                )
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                0 -> ConnectScreen(onGoRecord = { tab = 1 })
                1 -> RecordScreen()
                2 -> RecordingsScreen()
                3 -> LogScreen()
            }
        }
    }
}