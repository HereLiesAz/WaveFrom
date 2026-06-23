package com.hereliesaz.wavefrom.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.hereliesaz.wavefrom.ui.arview.ArScreen
import com.hereliesaz.wavefrom.ui.permissions.PermissionGate
import com.hereliesaz.wavefrom.ui.theme.WaveFromTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WaveFromTheme {
                Surface(Modifier.fillMaxSize()) {
                    PermissionGate {
                        ArScreen()
                    }
                }
            }
        }
    }
}
