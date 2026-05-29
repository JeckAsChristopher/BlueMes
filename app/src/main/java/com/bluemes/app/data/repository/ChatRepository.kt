package com.bluemes.app.data.repository

import com.bluemes.app.data.local.dao.ConversationDao
import com.bluemes.app.data.local.dao.MessageDao
import com.bluemes.app.data.local.entities.ConversationEntity
import com.bluemes.app.data.local.entities.MessageEntity
import com.bluemes.app.models.MessagePacket
import com.bluemes.app.models.PacketType
import kotlinx.coroutines.flow.Flow

class ChatRepository(
    private val convDao: ConversationDao,
    private val msgDao: MessageDao
) {
    fun getAllConversations()          = convDao.getAllConversations()
    fun searchConversations(q: String) = convDao.search(q)
    fun getMessages(id: String)        = msgDao.getMessages(id)

    suspend fun ensureConversation(address: String, userName: String) {
        val ex = convDao.getConversation(address)
        if (ex == null) convDao.insertOrUpdate(ConversationEntity(address, userName))
        else if (ex.userName != userName) convDao.insertOrUpdate(ex.copy(userName = userName))
    }

    suspend fun saveIncoming(packet: MessagePacket) {
        if (packet.type != PacketType.TEXT_MESSAGE) return
        ensureConversation(packet.senderAddress, packet.senderName)
        msgDao.insert(MessageEntity(
            messageId = packet.id, conversationId = packet.senderAddress,
            senderAddress = packet.senderAddress, senderName = packet.senderName,
            content = packet.content, timestamp = packet.timestamp, isMine = false
        ))
        convDao.updateLastMessage(packet.senderAddress, packet.content, packet.timestamp)
        convDao.incrementUnread(packet.senderAddress)
    }

    suspend fun saveOutgoing(
        recipientAddress: String, recipientName: String,
        messageId: String, content: String,
        senderAddress: String, senderName: String, timestamp: Long
    ) {
        ensureConversation(recipientAddress, recipientName)
        msgDao.insert(MessageEntity(
            messageId = messageId, conversationId = recipientAddress,
            senderAddress = senderAddress, senderName = senderName,
            content = content, timestamp = timestamp, isMine = true, isRead = true
        ))
        convDao.updateLastMessage(recipientAddress, content, timestamp)
    }

    suspend fun markRead(address: String) {
        msgDao.markAllRead(address)
        convDao.clearUnread(address)
    }

    suspend fun deleteConversation(address: String) = convDao.delete(address)
    suspend fun deleteAll() { msgDao.deleteAll(); convDao.deleteAll() }
}
