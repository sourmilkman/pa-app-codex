package uk.co.tommulliner.pa.alarm

import android.content.*
import android.util.Log
import android.os.PowerManager
import kotlinx.coroutines.launch
import uk.co.tommulliner.pa.PAApplication
import uk.co.tommulliner.pa.core.ReminderAction

class AlarmReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as PAApplication
        val wake = context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PA:receiver").apply { acquire(25_000) }
        app.scope.launch {
            try {
                when(intent.action) {
                    "uk.co.tommulliner.pa.FIRE" -> app.engine.fire()
                    "DONE", "SNOOZE", "DISMISS" -> intent.getStringExtra("id")?.let { app.engine.action(it,ReminderAction.valueOf(intent.action!!)) }
                }
            } catch(e: Exception) { Log.e("PAAlarm","Alarm operation failed",e) }
            finally { if(wake.isHeld) wake.release();pending.finish() }
        }
    }
}
class RestoreReceiver: BroadcastReceiver() {
    override fun onReceive(context: Context,intent: Intent) {
        if(intent.action !in setOf(Intent.ACTION_BOOT_COMPLETED,Intent.ACTION_TIME_CHANGED,Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_MY_PACKAGE_REPLACED,"android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) return
        val pending = goAsync()
        val app = context.applicationContext as PAApplication
        val wake = context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PA:receiver").apply { acquire(25_000) }
        app.scope.launch {
            try { app.engine.refresh(rebase=true) }
            catch(e: Exception) { Log.e("PAAlarm","Rescheduling failed",e) }
            finally { if(wake.isHeld) wake.release();pending.finish() }
        }
    }
}
