package uk.co.tommulliner.pa.alarm

import android.app.*
import android.content.*
import android.media.*
import android.os.*
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import uk.co.tommulliner.pa.PAApplication

/** Short, bounded playback; retry cadence remains in AlarmManager, never a timer loop. */
class AlarmSoundService: Service() {
    private var currentId: String?=null
    private var player: MediaPlayer?=null
    private var vibrator: Vibrator?=null
    private var wakeLock: PowerManager.WakeLock?=null
    private val handler=Handler(Looper.getMainLooper())
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main)
    private val timeout=Runnable { stopSelf() }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?,flags: Int,startId: Int): Int {
        if(intent?.action=="SILENCE") {
            if(currentId==intent.getStringExtra("id")) stopSelf()
            return START_NOT_STICKY
        }
        val id=intent?.getStringExtra("id") ?: return START_NOT_STICKY
        // Foreground promotion happens immediately, before database IO.
        val temporary=Notification.Builder(this,AlertNotifications.SOUND).setSmallIcon(uk.co.tommulliner.pa.R.drawable.ic_alarm)
            .setContentTitle("P.A. alarm").setContentText("Opening reminder…").build()
        startForeground(9000,temporary)
        currentId=id
        scope.launch {
            val row=withContext(Dispatchers.IO) { (application as PAApplication).database.reminders().get(id) }
            if(currentId!=id) return@launch
            if(row==null || row.state!="ALERTING") { stopSelf(); return@launch }
            startForeground(9000,AlertNotifications.build(this@AlarmSoundService,row,playback=true))
            releaseSound()
            val audio=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            try {
                player=MediaPlayer().apply {
                    setAudioAttributes(audio)
                    setDataSource(this@AlarmSoundService,RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                    isLooping=true; prepare(); start()
                }
                vibrator=getSystemService(Vibrator::class.java).also { it.vibrate(VibrationEffect.createWaveform(longArrayOf(0,500,500),0),audio) }
                wakeLock=getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"PA:alarm").apply { acquire(65_000) }
            } catch(e: Exception) { Log.e("PAAlarm","Sound unavailable; notification remains",e) }
            handler.removeCallbacks(timeout);handler.postDelayed(timeout,60_000)
        }
        return START_NOT_STICKY
    }
    private fun releaseSound() {
        player?.release();player=null;vibrator?.cancel();vibrator=null
        wakeLock?.let { if(it.isHeld) it.release() };wakeLock=null
    }
    override fun onDestroy() {
        activeInstance=null;handler.removeCallbacksAndMessages(null);scope.cancel();releaseSound();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy()
    }
    companion object {
        fun ring(context: Context,id: String) {
            try { ContextCompat.startForegroundService(context,Intent(context,AlarmSoundService::class.java).putExtra("id",id)) }
            catch(e: RuntimeException) { Log.e("PAAlarm","Playback service unavailable; notification remains",e) }
        }
        fun silence(context: Context,id: String) {
            // Explicit broadcast actions can stop the currently sounding alarm without starting a new FGS.
            if(activeInstance?.currentId==id) activeInstance?.stopSelf()
        }
        @Volatile private var activeInstance: AlarmSoundService?=null
    }
    override fun onCreate() { super.onCreate();activeInstance=this }
}
