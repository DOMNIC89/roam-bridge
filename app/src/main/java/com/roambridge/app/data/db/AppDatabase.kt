package com.roambridge.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.roambridge.app.data.classifier.SmsTopic

class Converters {
    @TypeConverter
    fun fromTopic(topic: SmsTopic): String = topic.name

    @TypeConverter
    fun toTopic(value: String): SmsTopic = SmsTopic.fromString(value)

    @TypeConverter
    fun fromDirection(direction: MessageDirection): String = direction.name

    @TypeConverter
    fun toDirection(value: String): MessageDirection = MessageDirection.valueOf(value)

    @TypeConverter
    fun fromRelayStatus(status: RelayStatus): String = status.name

    @TypeConverter
    fun toRelayStatus(value: String): RelayStatus = RelayStatus.valueOf(value)
}

@Database(entities = [SmsLogEntity::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun smsLogDao(): SmsLogDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "roambridge.db"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
