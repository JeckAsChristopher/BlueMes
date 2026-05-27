package com.bluemes.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.bluemes.app.data.local.dao.ConversationDao
import com.bluemes.app.data.local.dao.MessageDao
import com.bluemes.app.data.local.entities.ConversationEntity
import com.bluemes.app.data.local.entities.MessageEntity

@Database(
    entities = [ConversationEntity::class, MessageEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BlueMesDatabase : RoomDatabase() {

    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile
        private var INSTANCE: BlueMesDatabase? = null

        fun getInstance(context: Context): BlueMesDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BlueMesDatabase::class.java,
                    "bluemes_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
