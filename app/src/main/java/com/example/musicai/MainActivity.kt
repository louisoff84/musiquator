package com.example.musicai

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private lateinit var studio: Studio

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        studio = Studio(applicationContext)
        setContent {
            MusiquatorTheme {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Bg)
                        .systemBarsPadding()
                        .imePadding()
                ) {
                    BackHandler(enabled = studio.project != null) { studio.close() }
                    if (studio.project == null) HomeScreen(studio) else StudioScreen(studio)
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        studio.stopRec()
        studio.flush()
    }

    override fun onDestroy() {
        studio.release()
        super.onDestroy()
    }
}
