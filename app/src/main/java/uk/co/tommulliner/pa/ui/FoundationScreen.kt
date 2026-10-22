package uk.co.tommulliner.pa.ui

import android.Manifest
import android.app.*
import android.content.*
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import uk.co.tommulliner.pa.*
import uk.co.tommulliner.pa.alarm.*
import uk.co.tommulliner.pa.core.*
import uk.co.tommulliner.pa.data.*
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun FoundationScreen(app: PAApplication,resumeKey: Int,alarmId: String?,closeAlarm: () -> Unit) {
    val config by app.settings.flow.collectAsStateWithLifecycle(AlarmSettings())
    val rows by app.database.reminders().observe().collectAsStateWithLifecycle(emptyList())
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val activity=androidx.compose.ui.platform.LocalContext.current
    var permissionTick by remember { mutableIntStateOf(0) }
    var notificationRequested by rememberSaveable { mutableStateOf(false) }
    val exact=remember(resumeKey,permissionTick) { AlarmScheduler(app).permitted() }
    val notifications=remember(resumeKey,permissionTick) { AlertNotifications.permitted(app) }
    val fullscreen=remember(resumeKey) { AlertNotifications.fullScreenAllowed(app) }
    val battery=remember(resumeKey) { app.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(app.packageName) }
    val notificationRequest=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }
    fun open(intent: Intent) {
        try { activity.startActivity(intent) } catch(_: ActivityNotFoundException) { Toast.makeText(activity,"This settings page is unavailable on this phone.",Toast.LENGTH_LONG).show() }
    }
    val active=rows.firstOrNull { it.id==alarmId }
    LaunchedEffect(active?.state) {
        if(active?.state in setOf("DONE","DISMISSED","CANCELLED","SNOOZED")) closeAlarm()
    }
    if(alarmId!=null && active!=null) {
        AlarmScreen(active,config.snoozeMinutes,
            done={scope.launch { app.engine.action(active.id,ReminderAction.DONE) }},
            snooze={scope.launch { app.engine.action(active.id,ReminderAction.SNOOZE) }},
            dismiss={scope.launch { app.engine.action(active.id,ReminderAction.DISMISS) }})
        BackHandler { closeAlarm() }
        return
    }
    BackHandler(enabled=settingsOpen) { settingsOpen=false }
    Scaffold(topBar={ TopAppBar(title={ Text(if(settingsOpen) "Alarm settings" else "P.A.") },actions={
        IconButton(onClick={settingsOpen=!settingsOpen}) { Icon(Icons.Outlined.Settings,contentDescription="Settings") }
    }) }) { inset ->
        Column(Modifier.fillMaxSize().padding(inset).imePadding().verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            if(settingsOpen) {
                Text("Make time work for you.",style=MaterialTheme.typography.headlineMedium)
                AlarmSettingsEditor(config) { changed -> scope.launch { app.settings.save(changed);app.engine.refresh(rebase=true) } }
                Text("Build ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",style=MaterialTheme.typography.bodyMedium)
                Text("All schedules use Europe/London. Snoozes use elapsed time across clock changes.",color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Your day,\non time.",style=MaterialTheme.typography.displaySmall)
                Text("Alarm foundation · Stage 1",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                val next=rows.filter { it.nextTrigger!=null }.minByOrNull { it.nextTrigger!! }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(if(exact && notifications) "REMINDERS READY" else "SETUP NEEDED",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                        Text(next?.title ?: "Your next check",style=MaterialTheme.typography.titleLarge)
                        Text(next?.nextTrigger?.let { LondonTime.local(Instant.ofEpochMilli(it)).format(DateTimeFormatter.ofPattern("EEE d MMM · HH:mm")) }
                            ?: "Enable checks below to start your rota.",style=MaterialTheme.typography.bodyLarge)
                        Text("Wed · Thu · Fri + alternate weekends",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("Allow your reminders",style=MaterialTheme.typography.titleLarge)
                Text("P.A. needs alarm and notification access to alert you while locked. Full-screen access opens the check screen; battery settings help it run reliably.",color=MaterialTheme.colorScheme.onSurfaceVariant)
                PermissionButton("Exact alarms",exact) { if(Build.VERSION.SDK_INT>=31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:${app.packageName}"))) }
                PermissionButton("Notifications",notifications) {
                    if(Build.VERSION.SDK_INT>=33 && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED && !notificationRequested) {
                        notificationRequested=true;notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,app.packageName))
                }
                PermissionButton("Full-screen alerts",fullscreen) { if(Build.VERSION.SDK_INT>=34) open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:${app.packageName}"))) }
                PermissionButton("Battery optimisation excluded",battery) { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                Text("On Samsung: set P.A. battery use to Unrestricted and add it to Never sleeping apps. Keep the alarm channel enabled and alarm volume audible.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) { Text("Slip & trip checks",style=MaterialTheme.typography.titleMedium);Text("8 reminders on each working day",style=MaterialTheme.typography.bodySmall) }
                    Switch(checked=config.enabled,onCheckedChange={ value -> scope.launch { app.settings.save(config.copy(enabled=value));app.engine.refresh() } },enabled=exact && notifications)
                }
                Button(onClick={scope.launch { app.engine.test("ALARM");Toast.makeText(activity,"Test alarm scheduled in 20 seconds. Lock the phone now.",Toast.LENGTH_LONG).show() }},enabled=exact && notifications,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("Test alarm in 20 seconds") }
                OutlinedButton(onClick={scope.launch { app.engine.test("NOTIFICATION") }},enabled=exact && notifications,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("Test notification in 20 seconds") }
                OutlinedButton(onClick={
                    val uri=Uri.parse("mailto:?subject=${Uri.encode("P.A. reminder")}&body=${Uri.encode("Please remember to complete this item.")}")
                    try { activity.startActivity(Intent(Intent.ACTION_SENDTO,uri).setPackage("com.google.android.gm")) }
                    catch(_: ActivityNotFoundException) { open(Intent(Intent.ACTION_SENDTO,uri)) }
                },modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("Open email nudge draft") }
                val recent=rows.filter { it.deliveredAt!=null || it.completedAt!=null }.takeLast(5)
                if(recent.isNotEmpty()) {
                    Text("Recent checks",style=MaterialTheme.typography.titleMedium)
                    recent.forEach { row -> Text("${row.localDate} ${row.localTime.take(5)} · ${row.state.lowercase()}"+ (row.completedAt?.let { " at ${LondonTime.local(Instant.ofEpochMilli(it)).toLocalTime().withNano(0)}" } ?: "")) }
                }
                Text("Today, Shifts, Car and Tasks will follow after this foundation is approved.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            Text("P.A. ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
    }
}
@Composable private fun PermissionButton(label: String,granted: Boolean,onClick: () -> Unit) {
    OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("$label · ${if(granted) "Allowed" else "Set up"}") }
}
@Composable private fun AlarmSettingsEditor(config: AlarmSettings,save: (AlarmSettings)->Unit) {
    var times by remember(config) { mutableStateOf(config.checkTimes.joinToString(", ") { it.toString() }) }
    var anchor by remember(config) { mutableStateOf(config.anchor.toString()) }
    var grace by remember(config) { mutableStateOf(config.graceMinutes.toString()) }
    var repeat by remember(config) { mutableStateOf(config.repeatMinutes.toString()) }
    var snooze by remember(config) { mutableStateOf(config.snoozeMinutes.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(times,{times=it},label={Text("Check times (HH:mm, comma separated)")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(anchor,{anchor=it},label={Text("Working Saturday (YYYY-MM-DD)")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(grace,{grace=it},label={Text("First re-alert after (minutes)")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(repeat,{repeat=it},label={Text("Then repeat every (minutes)")},modifier=Modifier.fillMaxWidth())
    OutlinedTextField(snooze,{snooze=it},label={Text("Snooze (minutes)")},modifier=Modifier.fillMaxWidth())
    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    Button(onClick={
        try {
            val parsed=times.split(',').map { LocalTime.parse(it.trim()) }.distinct().sorted()
            require(parsed.size in 1..48)
            val saturday=LocalDate.parse(anchor);require(saturday.dayOfWeek==DayOfWeek.SATURDAY)
            val values=listOf(grace.toLong(),repeat.toLong(),snooze.toLong());require(values.all { it in 1..120 })
            save(config.copy(anchor=saturday,checkTimes=parsed,graceMinutes=values[0],repeatMinutes=values[1],snoozeMinutes=values[2]));error=null
        } catch(_: Exception) { error="Use HH:mm times, a Saturday date, and intervals from 1 to 120 minutes." }
    },modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("Save alarm settings") }
}
@Composable private fun AlarmScreen(row: ReminderEntity,snoozeMinutes: Long,done: ()->Unit,snooze: ()->Unit,dismiss: ()->Unit) {
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
            Text("P.A. / CHECK IN",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            Text(row.localTime.take(5),style=MaterialTheme.typography.displayLarge)
            Text(row.title,style=MaterialTheme.typography.headlineLarge)
            Text("Complete the check yourself, then mark it done here.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Button(onClick=done,modifier=Modifier.fillMaxWidth().height(64.dp)) { Text("Done") }
            OutlinedButton(onClick=snooze,modifier=Modifier.fillMaxWidth().height(64.dp)) { Text("Snooze $snoozeMinutes min") }
            TextButton(onClick=dismiss,modifier=Modifier.fillMaxWidth().height(56.dp)) { Text("Dismiss this reminder") }
        }
    }
}
