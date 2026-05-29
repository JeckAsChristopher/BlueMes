package com.bluemes.app.ui.history.adapters

import android.view.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bluemes.app.data.local.entities.ConversationEntity
import com.bluemes.app.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.*

class ConversationAdapter(
    private val onClick: (ConversationEntity) -> Unit
) : ListAdapter<ConversationEntity, ConversationAdapter.VH>(DIFF) {
    private val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    inner class VH(private val b: ItemConversationBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(c: ConversationEntity) {
            b.userName.text = c.userName
            b.lastMessage.text = c.lastMessage.ifBlank { "No messages yet" }
            b.timestamp.text = if (c.lastMessageTimestamp > 0) fmt.format(Date(c.lastMessageTimestamp)) else ""
            b.unreadBadge.visibility = if (c.unreadCount > 0) View.VISIBLE else View.GONE
            b.unreadBadge.text = c.unreadCount.coerceAtMost(99).toString()
            b.root.setOnClickListener { onClick(c) }
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemConversationBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ConversationEntity>() {
            override fun areItemsTheSame(a: ConversationEntity, b: ConversationEntity) = a.deviceAddress == b.deviceAddress
            override fun areContentsTheSame(a: ConversationEntity, b: ConversationEntity) = a == b
        }
    }
}
