package uk.co.tommulliner.pa

import android.app.Application
import androidx.room.Room
import kotlinx.coroutines.*
import uk.co.tommulliner.pa.alarm.*
import uk.co.tommulliner.pa.data.*

class PAApplication: Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy { Room.databaseBuilder(this, PADatabase::class.java,"pa.db").build() }
    val settings by lazy { PASettings(this) }
    val engine by lazy { AlarmEngine(this, database, settings) }
    override fun onCreate() { super.onCreate(); AlertNotifications.createChannels(this) }
}
