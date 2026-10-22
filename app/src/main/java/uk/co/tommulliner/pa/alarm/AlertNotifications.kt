package uk.co.tommulliner.pa.alarm

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import uk.co.tommulliner.pa.MainActivity
import uk.co.tommulliner.pa.R
import uk.co.tommulliner.pa.data.ReminderEntity

object AlertNotifications {
    const val ALARMS="pa_alarm_v1"
    const val NUDGES="pa_nudge_v1"
    const val SOUND="pa_sound_v1"
    fun createChannels(context: Context) {
        val manager=context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(ALARMS,"Alarms",NotificationManager.IMPORTANCE_HIGH).apply {
            description="Full-screen check reminders. Alarm sound is played while the alarm service is active."
            setSound(null,null); enableVibration(false); lockscreenVisibility=Notification.VISIBILITY_PRIVATE
        })
        manager.createNotificationChannel(NotificationChannel(NUDGES,"Reminders",NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(SOUND,"Alarm playback",NotificationManager.IMPORTANCE_LOW).apply { setSound(null,null) })
    }
    fun permitted(context: Context) =
        (Build.VERSION.SDK_INT<33 || ContextCompat.checkSelfPermission(context,Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    fun fullScreenAllowed(context: Context) = Build.VERSION.SDK_INT<34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    fun openIntent(context: Context,id: String): PendingIntent = PendingIntent.getActivity(context,0,
        Intent(context,MainActivity::class.java).setAction("OPEN_ALARM").setData(Uri.parse("pa://open/${Uri.encode(id)}")).putExtra("alarmId",id)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun action(context: Context,id: String,action: String) = PendingIntent.getBroadcast(context,0,
        Intent(context,AlarmReceiver::class.java).setAction(action).setData(Uri.parse("pa://action/$action/${Uri.encode(id)}")).putExtra("id",id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    fun build(context: Context,row: ReminderEntity,playback: Boolean=false): Notification {
        val alarm=row.style=="ALARM"
        val builder=NotificationCompat.Builder(context,if(playback) SOUND else if(alarm) ALARMS else NUDGES)
            .setSmallIcon(R.drawable.ic_alarm).setContentTitle(row.title)
            .setContentText("${row.localDate} · ${row.localTime.take(5)} London time")
            .setCategory(if(alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(openIntent(context,row.id)).setAutoCancel(false)
            .addAction(0,"Done",action(context,row.id,"DONE"))
            .addAction(0,"Snooze",action(context,row.id,"SNOOZE"))
            .addAction(0,"Dismiss",action(context,row.id,"DISMISS"))
            .setDeleteIntent(action(context,row.id,"DISMISS"))
        if(alarm && !playback && fullScreenAllowed(context)) builder.setFullScreenIntent(openIntent(context,row.id),true)
        if(playback) builder.setOngoing(true).setSilent(true)
        return builder.build()
    }
    fun show(context: Context,row: ReminderEntity) {
        if(!permitted(context)) return
        try { NotificationManagerCompat.from(context).notify(row.id,0,build(context,row)) } catch(_: SecurityException) { }
    }
    fun cancel(context: Context,id: String) { NotificationManagerCompat.from(context).cancel(id,0) }
}
