package com.retroplay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.retroplay.core.ui.theme.RetroPlayTheme
import com.retroplay.ui.shell.RetroPlayShell
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // A splash do sistema some assim que o primeiro frame é desenhado (< 1 s).
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            RetroPlayTheme {
                // Surface raiz: define fundo e cor de conteúdo padrão (texto/ícones claros).
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = SurfaceDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                ) {
                    RetroPlayShell()
                }
            }
        }
    }
}
