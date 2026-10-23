package com.papertong.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.Modifier
import com.papertong.app.ui.PaperTongRoot
import com.papertong.app.ui.theme.PaperTongTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            PaperTongTheme {
                // enableEdgeToEdge 后内容会绘制到状态栏/导航栏下方，
                // 在这里统一避开系统栏与输入法，各页面无需重复处理。
                PaperTongRoot(modifier = Modifier.safeDrawingPadding())
            }
        }
    }
}
