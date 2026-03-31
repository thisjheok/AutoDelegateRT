package com.kjache.autodelegatert

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kjache.autodelegatert.ui.theme.AutoDelegateRTTheme
import com.kjache.runtime.RuntimeEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = RuntimeEngine()
        val session = engine.createSession()
        val backendInfo = session.backendInfo()
        val backendSummary =
            "Selected backend: ${backendInfo.selectedBackend.name}\n" +
                "Fallback used: ${backendInfo.usedFallback}\n" +
                backendInfo.message

        enableEdgeToEdge()
        setContent {
            AutoDelegateRTTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        backendSummary = backendSummary,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun Greeting(backendSummary: String, modifier: Modifier = Modifier) {
    Text(
        text = backendSummary,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    AutoDelegateRTTheme {
        Greeting("Selected backend: CPU\nFallback used: false\nCPU baseline session created.")
    }
}
