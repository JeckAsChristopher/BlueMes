package com.bluemes.app.ui.chat.adapters

import android.view.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bluemes.app.data.local.entities.MessageEntity
import com.bluemes.app.databinding.ItemMessageInBinding
import com.bluemes.app.databinding.ItemMessageOutBinding
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter : ListAdapter<MessageEntity, RecyclerView.ViewHolder>(DIFF) {
    private val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    companion object {
        private const val OUT = 1; private const val IN = 0
        val DIFF = object : DiffUtil.ItemCallback<MessageEntity>() {
            override fun areItemsTheSame(a: MessageEntity, b: MessageEntity) = a.messageId == b.messageId
            override fun areContentsTheSame(a: MessageEntity, b: MessageEntity) = a == b
        }
    }
    override fun getItemViewType(pos: Int) = if (getItem(pos).isMine) OUT else IN

    override fun onCreateViewHolder(p: ViewGroup, t: Int): RecyclerView.ViewHolder =
        if (t == OUT) OutVH(ItemMessageOutBinding.inflate(LayoutInflater.from(p.context), p, false))
        else          InVH (ItemMessageInBinding .inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: RecyclerView.ViewHolder, pos: Int) {
        val m = getItem(pos)
        when (h) {
            is OutVH -> h.bind(m)
            is InVH  -> h.bind(m)
        }
    }

    inner class OutVH(private val b: ItemMessageOutBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(m: MessageEntity) {
            b.messageText.text = m.content
            b.timeText.text = fmt.format(Date(m.timestamp))
            b.root.alpha = 0f; b.root.animate().alpha(1f).setDuration(180).start()
        }
    }
    inner class InVH(private val b: ItemMessageInBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(m: MessageEntity) {
            b.messageText.text = m.content
            b.timeText.text = fmt.format(Date(m.timestamp))
            b.senderName.text = m.senderName
            b.root.alpha = 0f; b.root.animate().alpha(1f).setDuration(180).start()
        }
    }
}
