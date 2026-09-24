package com.example

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.TabloTvApp
import com.example.ui.theme.TabloTvTheme
import com.example.ui.theme.TvBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen awake for uninterrupted TV viewing on Fire TV
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            TabloTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = TvBackground
                ) {
                    TabloTvApp()
                }
            }
        }
    }
}
