package com.bluemes.app.ui.history.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bluemes.app.data.local.entities.ConversationEntity
import com.bluemes.app.databinding.ItemConversationBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationAdapter(
    private val onClick: (ConversationEntity) -> Unit
) : ListAdapter<ConversationEntity, ConversationAdapter.ViewHolder>(DIFF) {

    private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())

    inner class ViewHolder(private val binding: ItemConversationBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(conv: ConversationEntity) {
            binding.userName.text = conv.userName
            binding.lastMessage.text = conv.lastMessage.ifBlank { "No messages yet" }
            binding.timestamp.text = if (conv.lastMessageTimestamp > 0)
                dateFormat.format(Date(conv.lastMessageTimestamp)) else ""
            binding.unreadBadge.visibility = if (conv.unreadCount > 0) View.VISIBLE else View.GONE
            binding.unreadBadge.text = conv.unreadCount.coerceAtMost(99).toString()
            binding.root.setOnClickListener { onClick(conv) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemConversationBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<ConversationEntity>() {
            override fun areItemsTheSame(a: ConversationEntity, b: ConversationEntity) =
                a.deviceAddress == b.deviceAddress
            override fun areContentsTheSame(a: ConversationEntity, b: ConversationEntity) = a == b
        }
    }
}
