package com.example

import android.os.Bundle
import android.util.Log
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

        // Robust crash prevention for background socket/ExoPlayer exceptions on Android TV
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MainActivity", "Caught uncaught exception on thread ${thread.name}: ${throwable.message}", throwable)
            if (throwable is OutOfMemoryError) {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

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
