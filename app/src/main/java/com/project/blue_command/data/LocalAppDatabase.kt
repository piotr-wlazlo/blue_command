package com.project.blue_command.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        CommandMessageEntity::class,
        GroupEntity::class,
        UserEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(StringListConverter::class)
abstract class LocalAppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: LocalAppDatabase? = null

        fun getDatabase(context: Context): LocalAppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    LocalAppDatabase::class.java,
                    "blue_command_offline_db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
