package uk.co.tommulliner.pa.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import uk.co.tommulliner.pa.core.CheckPlanner
import java.time.*

private val Context.paPreferences by preferencesDataStore("pa_settings")
data class AlarmSettings(
    val enabled: Boolean = false,
    val anchor: LocalDate = LocalDate.of(2026,10,31),
    val checkTimes: List<LocalTime> = CheckPlanner.defaults,
    val graceMinutes: Long = 10,
    val repeatMinutes: Long = 5,
    val snoozeMinutes: Long = 5,
)
class PASettings(context: Context) {
    private val store = context.paPreferences
    private val enabled = booleanPreferencesKey("checks_enabled")
    private val times = stringPreferencesKey("check_times")
    private val anchor = stringPreferencesKey("working_saturday")
    private val grace = longPreferencesKey("grace_minutes")
    private val repeat = longPreferencesKey("repeat_minutes")
    private val snooze = longPreferencesKey("snooze_minutes")
    val flow: Flow<AlarmSettings> = store.data.map { p ->
        AlarmSettings(p[enabled] ?: false,
            p[anchor]?.let(LocalDate::parse) ?: LocalDate.of(2026,10,31),
            p[times]?.split(',')?.filter { it.isNotBlank() }?.map(LocalTime::parse) ?: CheckPlanner.defaults,
            p[grace] ?: 10, p[repeat] ?: 5, p[snooze] ?: 5)
    }
    suspend fun get() = flow.first()
    suspend fun save(value: AlarmSettings) {
        require(value.anchor.dayOfWeek == DayOfWeek.SATURDAY)
        require(value.graceMinutes in 1..120 && value.repeatMinutes in 1..120 && value.snoozeMinutes in 1..120)
        require(value.checkTimes.isNotEmpty())
        store.edit { p ->
            p[enabled]=value.enabled; p[times]=value.checkTimes.distinct().sorted().joinToString(",")
            p[anchor]=value.anchor.toString();p[grace]=value.graceMinutes;p[repeat]=value.repeatMinutes;p[snooze]=value.snoozeMinutes
        }
    }
}
