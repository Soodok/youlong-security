package com.youlong.hd

import android.webkit.WebView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.viewinterop.AndroidView


fun ComposeView.setMainContent(
    webView: WebView,
    onTabSelected: (Int) -> Unit = {}
) {
    setContent {
        MainScreen(webView = webView)
    }
}


@Composable
fun MainScreen(webView: WebView) {
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
