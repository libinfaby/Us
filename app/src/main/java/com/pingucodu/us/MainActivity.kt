package com.pingucodu.us

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.pingucodu.us.ui.nav.PinguCoduApp
import com.pingucodu.us.ui.theme.UsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UsTheme {
                PinguCoduApp()
            }
        }
    }
}
