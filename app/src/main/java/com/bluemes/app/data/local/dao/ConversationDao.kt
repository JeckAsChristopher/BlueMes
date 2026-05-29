package com.bluemes.app.data.local.dao

import androidx.room.*
import com.bluemes.app.data.local.entities.ConversationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY lastMessageTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE deviceAddress = :address LIMIT 1")
    suspend fun getConversation(address: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(c: ConversationEntity)

    @Query("UPDATE conversations SET lastMessage=:msg, lastMessageTimestamp=:ts WHERE deviceAddress=:address")
    suspend fun updateLastMessage(address: String, msg: String, ts: Long)

    @Query("UPDATE conversations SET unreadCount = unreadCount + 1 WHERE deviceAddress=:address")
    suspend fun incrementUnread(address: String)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE deviceAddress=:address")
    suspend fun clearUnread(address: String)

    @Query("DELETE FROM conversations WHERE deviceAddress=:address")
    suspend fun delete(address: String)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("SELECT * FROM conversations WHERE userName LIKE '%'||:q||'%' ORDER BY lastMessageTimestamp DESC")
    fun search(q: String): Flow<List<ConversationEntity>>
}
