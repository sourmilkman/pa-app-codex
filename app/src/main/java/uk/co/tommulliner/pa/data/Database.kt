package uk.co.tommulliner.pa.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="reminders")
data class ReminderEntity(
    @PrimaryKey val id: String,
    val title: String,
    val localDate: String,
    val localTime: String,
    val style: String = "ALARM",
    val state: String = "SCHEDULED",
    val nextTrigger: Long?,
    val repeatUntilDone: Boolean = true,
    val routine: Boolean = false,
    val deliveredAt: Long? = null,
    val completedAt: Long? = null,
)
@Entity(tableName="events", indices=[Index("reminderId")])
data class ReminderEvent(
    @PrimaryKey(autoGenerate=true) val id: Long = 0,
    val reminderId: String,
    val action: String,
    val atEpochMillis: Long,
)
@Entity(tableName="shift_overrides")
data class OverrideEntity(@PrimaryKey val date: String, val kind: String)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY localDate, localTime") fun observe(): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders WHERE id=:id") suspend fun get(id: String): ReminderEntity?
    @Query("SELECT * FROM reminders WHERE nextTrigger IS NOT NULL ORDER BY nextTrigger") suspend fun pending(): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE state='ALERTING'") suspend fun alerting(): List<ReminderEntity>
    @Insert(onConflict=OnConflictStrategy.IGNORE) suspend fun insert(reminders: List<ReminderEntity>)
    @Upsert suspend fun upsert(reminder: ReminderEntity)
    @Insert suspend fun event(event: ReminderEvent)
    @Query("SELECT * FROM events ORDER BY atEpochMillis") suspend fun events(): List<ReminderEvent>
    @Query("SELECT * FROM shift_overrides") suspend fun overrides(): List<OverrideEntity>
    @Upsert suspend fun override(override: OverrideEntity)
}
@Database(entities=[ReminderEntity::class,ReminderEvent::class,OverrideEntity::class],version=1,exportSchema=true)
abstract class PADatabase: RoomDatabase() { abstract fun reminders(): ReminderDao }
