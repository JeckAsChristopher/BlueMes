package com.bluemes.app.data.local.dao

import androidx.room.*
import com.bluemes.app.data.local.entities.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId=:id ORDER BY timestamp ASC")
    fun getMessages(id: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE messageId=:id LIMIT 1")
    suspend fun getMessage(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(m: MessageEntity)

    @Query("UPDATE messages SET isRead=1 WHERE conversationId=:id AND NOT isMine")
    suspend fun markAllRead(id: String)

    @Query("DELETE FROM messages WHERE conversationId=:id")
    suspend fun deleteFor(id: String)

    @Query("DELETE FROM messages")
    suspend fun deleteAll()
}
