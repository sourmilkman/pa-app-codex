package uk.co.tommulliner.pa

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import uk.co.tommulliner.pa.ui.*
import kotlinx.coroutines.launch

class MainActivity: ComponentActivity() {
    private var alarmId by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge();accept(intent)
        setContent {
            PATheme {
                val app=application as PAApplication
                var resumeKey by remember { mutableIntStateOf(0) }
                val owner=LocalLifecycleOwner.current
                DisposableEffect(owner) {
                    val observer=LifecycleEventObserver { _,event ->
                        if(event==Lifecycle.Event.ON_RESUME) {
                            resumeKey++
                            app.scope.launch { app.engine.refresh() }
                        }
                    }
                    owner.lifecycle.addObserver(observer)
                    onDispose { owner.lifecycle.removeObserver(observer) }
                }
                FoundationScreen(app,resumeKey,alarmId) { alarmId=null;setShowWhenLocked(false);setTurnScreenOn(false) }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent);setIntent(intent);accept(intent) }
    private fun accept(intent: Intent) {
        alarmId=intent.getStringExtra("alarmId")
        setShowWhenLocked(alarmId!=null);setTurnScreenOn(alarmId!=null)
    }
}
