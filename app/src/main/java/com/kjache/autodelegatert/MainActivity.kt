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
import com.kjache.runtime.QnnConfig
import com.kjache.runtime.RuntimeEngine
import com.kjache.runtime.SessionOptions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val engine = RuntimeEngine(applicationContext)
        val session = engine.createSession(
            SessionOptions(
                qnnConfig = QnnConfig()
            )
        )
        val backendInfo = session.backendInfo()
        val backendSummary =
            "Selected backend: ${backendInfo.selectedBackend.name}\n" +
                "Attempted backend: ${backendInfo.attemptedBackend?.name ?: "NONE"}\n" +
                "Fallback used: ${backendInfo.usedFallback}\n" +
                "QNN prepared: ${backendInfo.qnnPrepared}\n" +
                "Native attach attempted: ${backendInfo.nativeAttachAttempted}\n" +
                "Native attach succeeded: ${backendInfo.nativeAttachSucceeded}\n" +
                "Failure reason: ${backendInfo.failureReason?.name ?: "NONE"}\n" +
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
        Greeting(
            "Selected backend: CPU\n" +
                "Attempted backend: QNN_HTP\n" +
                "Fallback used: true\n" +
                "QNN prepared: true\n" +
                "Native attach attempted: true\n" +
                "Native attach succeeded: false\n" +
                "Failure reason: DELEGATE_ATTACH_FAILED\n" +
                "QNN assets prepared."
        )
    }
}
