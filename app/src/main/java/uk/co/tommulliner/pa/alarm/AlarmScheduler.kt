package uk.co.tommulliner.pa.alarm

import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import uk.co.tommulliner.pa.MainActivity

class AlarmScheduler(private val context: Context) {
    private val manager = context.getSystemService(AlarmManager::class.java)
    fun permitted() = Build.VERSION.SDK_INT < 31 || manager.canScheduleExactAlarms()
    private fun dispatch() = PendingIntent.getBroadcast(context, 0,
        Intent(context,AlarmReceiver::class.java).setAction("uk.co.tommulliner.pa.FIRE").setData(Uri.parse("pa://dispatch")),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun cancel() = manager.cancel(dispatch())
    fun schedule(epochMillis: Long): Boolean {
        if (!permitted()) return false
        val show = PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return try {
            manager.setAlarmClock(AlarmManager.AlarmClockInfo(epochMillis,show),dispatch()); true
        } catch (_: SecurityException) { false }
    }
}
