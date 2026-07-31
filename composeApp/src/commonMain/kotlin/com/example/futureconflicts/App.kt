package com.example.futureconflicts

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.futureconflicts.ui.GameScreen

@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        GameScreen(Modifier)
    }
}
