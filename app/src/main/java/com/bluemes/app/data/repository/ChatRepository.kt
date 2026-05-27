package com.bluemes.app.data.repository

import com.bluemes.app.data.local.dao.ConversationDao
import com.bluemes.app.data.local.dao.MessageDao
import com.bluemes.app.data.local.entities.ConversationEntity
import com.bluemes.app.data.local.entities.MessageEntity
import com.bluemes.app.models.MessagePacket
import com.bluemes.app.models.PacketType
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) {
    fun getAllConversations(): Flow<List<ConversationEntity>> =
        conversationDao.getAllConversations()

    fun searchConversations(query: String): Flow<List<ConversationEntity>> =
        conversationDao.searchConversations(query)

    fun getMessages(conversationId: String): Flow<List<MessageEntity>> =
        messageDao.getMessagesForConversation(conversationId)

    suspend fun ensureConversation(address: String, userName: String) {
        val existing = conversationDao.getConversation(address)
        if (existing == null) {
            conversationDao.insertOrUpdate(
                ConversationEntity(deviceAddress = address, userName = userName)
            )
        } else if (existing.userName != userName) {
            conversationDao.insertOrUpdate(existing.copy(userName = userName))
        }
    }

    suspend fun saveIncomingMessage(packet: MessagePacket) {
        if (packet.type != PacketType.TEXT_MESSAGE) return
        ensureConversation(packet.senderAddress, packet.senderName)
        val entity = MessageEntity(
            messageId = packet.id,
            conversationId = packet.senderAddress,
            senderAddress = packet.senderAddress,
            senderName = packet.senderName,
            content = packet.content,
            timestamp = packet.timestamp,
            isMine = false
        )
        messageDao.insert(entity)
        conversationDao.updateLastMessage(packet.senderAddress, packet.content, packet.timestamp)
        conversationDao.incrementUnread(packet.senderAddress)
    }

    suspend fun saveOutgoingMessage(
        recipientAddress: String,
        recipientName: String,
        messageId: String,
        content: String,
        senderAddress: String,
        senderName: String,
        timestamp: Long
    ) {
        ensureConversation(recipientAddress, recipientName)
        val entity = MessageEntity(
            messageId = messageId,
            conversationId = recipientAddress,
            senderAddress = senderAddress,
            senderName = senderName,
            content = content,
            timestamp = timestamp,
            isMine = true,
            isRead = true
        )
        messageDao.insert(entity)
        conversationDao.updateLastMessage(recipientAddress, content, timestamp)
    }

    suspend fun markConversationRead(address: String) {
        messageDao.markAllRead(address)
        conversationDao.clearUnread(address)
    }

    suspend fun deleteConversation(address: String) {
        conversationDao.delete(address)
    }

    suspend fun deleteAllHistory() {
        messageDao.deleteAll()
        conversationDao.deleteAll()
    }
}
