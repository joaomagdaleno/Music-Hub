package com.joaomagdaleno.music_hub.ui.compose

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SimpleComposeScreen() {
    Column(modifier = Modifier.fillMaxSize()) {
        BasicText(text = "Hello, Jetpack Compose!")
    }
}

@Preview
@Composable
fun PreviewSimpleComposeScreen() {
    SimpleComposeScreen()
}
