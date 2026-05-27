package com.bluemes.app.ui.chat.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bluemes.app.data.local.entities.MessageEntity
import com.bluemes.app.databinding.ItemMessageInBinding
import com.bluemes.app.databinding.ItemMessageOutBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MessageAdapter : ListAdapter<MessageEntity, RecyclerView.ViewHolder>(DIFF) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    companion object {
        private const val VIEW_INCOMING = 0
        private const val VIEW_OUTGOING = 1

        val DIFF = object : DiffUtil.ItemCallback<MessageEntity>() {
            override fun areItemsTheSame(a: MessageEntity, b: MessageEntity) =
                a.messageId == b.messageId
            override fun areContentsTheSame(a: MessageEntity, b: MessageEntity) = a == b
        }
    }

    override fun getItemViewType(position: Int) =
        if (getItem(position).isMine) VIEW_OUTGOING else VIEW_INCOMING

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_OUTGOING) {
            OutViewHolder(
                ItemMessageOutBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        } else {
            InViewHolder(
                ItemMessageInBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is OutViewHolder -> holder.bind(item)
            is InViewHolder -> holder.bind(item)
        }
    }

    inner class OutViewHolder(private val binding: ItemMessageOutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: MessageEntity) {
            binding.messageText.text = msg.content
            binding.timeText.text = timeFormat.format(Date(msg.timestamp))
            binding.root.alpha = 0f
            binding.root.animate().alpha(1f).setDuration(200).start()
        }
    }

    inner class InViewHolder(private val binding: ItemMessageInBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(msg: MessageEntity) {
            binding.messageText.text = msg.content
            binding.timeText.text = timeFormat.format(Date(msg.timestamp))
            binding.senderName.text = msg.senderName
            binding.root.alpha = 0f
            binding.root.animate().alpha(1f).setDuration(200).start()
        }
    }
}
