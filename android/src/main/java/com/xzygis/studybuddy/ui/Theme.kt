package com.xzygis.studybuddy.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val StudyGreen = Color(0xFF176B5A)
val StudyOrange = Color(0xFFE87920)
val AppBackground = Color(0xFFF6F7F8)

private val colors = lightColorScheme(
    primary = StudyGreen,
    onPrimary = Color.White,
    secondary = StudyOrange,
    background = AppBackground,
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EEEC),
    outline = Color(0xFFC8D0CD),
)

@Composable
fun StudyBuddyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = colors,
        content = content,
    )
}
