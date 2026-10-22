package uk.co.tommulliner.pa.alarm

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uk.co.tommulliner.pa.core.*
import uk.co.tommulliner.pa.data.*
import java.time.*
import java.util.UUID

/** Room is authoritative. One OS alarm dispatches the earliest persisted occurrence(s). */
class AlarmEngine(private val context: Context,private val db: PADatabase,private val settings: PASettings) {
    private val lock = Mutex()
    private val dao = db.reminders()
    private val scheduler = AlarmScheduler(context)
    suspend fun refresh(rebase: Boolean = false) = lock.withLock {
        val now = Instant.now()
        reconcile(now,rebase)
        armNext(now)
    }
    private suspend fun reconcile(now: Instant,rebase: Boolean = false) {
        val config = settings.get()
        val overrides = dao.overrides().associate { LocalDate.parse(it.date) to ShiftOverride.valueOf(it.kind) }
        val rota = RotaCalculator(config.anchor)
        val planned = if(config.enabled) CheckPlanner.plan(now,config.checkTimes,rota,overrides) else emptyList()
        val desired = planned.map { it.id }.toSet()
        val today = LondonTime.local(now).toLocalDate()
        // Cancel obsolete future checks, but retain Done/Dismissed history and current-day active checks.
        for(row in dao.pending()) {
            val expired = row.routine && LocalDate.parse(row.localDate) < today
            val removed = row.routine && (!config.enabled || !rota.works(LocalDate.parse(row.localDate),overrides[LocalDate.parse(row.localDate)]) || (row.state == "SCHEDULED" && row.nextTrigger!! > now.toEpochMilli() && row.id !in desired))
            if(expired || removed) {
                dao.upsert(row.copy(state="CANCELLED",nextTrigger=null))
                AlertNotifications.cancel(context,row.id)
                AlarmSoundService.silence(context,row.id)
            } else if(rebase && row.state == "SCHEDULED") {
                dao.upsert(row.copy(nextTrigger=LondonTime.instant(LocalDate.parse(row.localDate),LocalTime.parse(row.localTime)).toEpochMilli()))
            }
        }
        // Re-enable previously cancelled future checks after a setting/rota edit without resurrecting Done.
        for(item in planned) {
            val existing = dao.get(item.id)
            if(existing == null || existing.state=="CANCELLED") dao.upsert(ReminderEntity(item.id,"Slip & trip check",item.date.toString(),item.time.toString(),nextTrigger=item.instant.toEpochMilli(),routine=true))
        }
    }
    private suspend fun armNext(now: Instant) {
        val next = dao.pending().firstOrNull()?.nextTrigger
        if(next == null || !scheduler.permitted()) scheduler.cancel()
        else scheduler.schedule(maxOf(next,now.toEpochMilli()+1000))
    }
    suspend fun test(style: String): String = lock.withLock {
        require(style in setOf("ALARM","NOTIFICATION"))
        val at = Instant.now().plusSeconds(20)
        val local = LondonTime.local(at)
        val id = "test:${UUID.randomUUID()}"
        dao.upsert(ReminderEntity(id,"P.A. test ${style.lowercase()}",local.toLocalDate().toString(),local.toLocalTime().toString(),
            style=style,nextTrigger=at.toEpochMilli(),repeatUntilDone=style=="ALARM"))
        armNext(Instant.now()); id
    }
    suspend fun fire() = lock.withLock {
        val now = Instant.now()
        // Leave a recovery dispatch armed while processing the consumed OS alarm.
        scheduler.schedule(now.plusSeconds(60).toEpochMilli())
        reconcile(now)
        val config = settings.get()
        val alerts = mutableListOf<ReminderEntity>()
        for(row in dao.pending().filter { it.nextTrigger!! <= now.toEpochMilli() }) {
            val result = ReminderPolicy.transition(ReminderState.valueOf(row.state),ReminderAction.FIRE,now,row.repeatUntilDone,config.graceMinutes,config.repeatMinutes,config.snoozeMinutes)
            db.withTransaction {
                dao.upsert(row.copy(state=result.state.name,nextTrigger=result.next?.toEpochMilli(),deliveredAt=now.toEpochMilli()))
                dao.event(ReminderEvent(reminderId=row.id,action="FIRED",atEpochMillis=now.toEpochMilli()))
            }
            alerts += row
        }
        armNext(now)
        for(row in alerts) {
            AlertNotifications.show(context,row)
            if(row.style=="ALARM" && AlertNotifications.permitted(context)) AlarmSoundService.ring(context,row.id)
        }
    }
    suspend fun action(id: String,action: ReminderAction) = lock.withLock {
        require(action != ReminderAction.FIRE)
        val row = dao.get(id) ?: return@withLock
        if(row.state in setOf("DONE","DISMISSED","CANCELLED")) return@withLock
        val now = Instant.now()
        val config = settings.get()
        val result = ReminderPolicy.transition(ReminderState.valueOf(row.state),action,now,row.repeatUntilDone,config.graceMinutes,config.repeatMinutes,config.snoozeMinutes)
        db.withTransaction {
            dao.upsert(row.copy(state=result.state.name,nextTrigger=result.next?.toEpochMilli(),
                completedAt=if(action==ReminderAction.DONE) now.toEpochMilli() else row.completedAt))
            dao.event(ReminderEvent(reminderId=id,action=action.name,atEpochMillis=now.toEpochMilli()))
        }
        AlertNotifications.cancel(context,id)
        AlarmSoundService.silence(context,id)
        reconcile(now)
        armNext(now)
    }
}
