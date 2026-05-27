package com.bluemes.app.ui.nearby.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bluemes.app.databinding.ItemNearbyUserBinding
import com.bluemes.app.models.ConnectionState
import com.bluemes.app.models.NearbyUser

class NearbyUserAdapter(
    private val onUserClick: (NearbyUser) -> Unit
) : ListAdapter<NearbyUser, NearbyUserAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(private val binding: ItemNearbyUserBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(user: NearbyUser) {
            binding.userName.text = user.userName
            binding.deviceName.text = user.deviceName
            binding.signalStrength.text = user.signalStrengthLabel()

            val stateText = when (user.connectionState) {
                ConnectionState.CONNECTED -> "Connected"
                ConnectionState.CONNECTING -> "Connecting…"
                ConnectionState.DISCONNECTED -> "Offline"
                ConnectionState.FAILED -> "Failed"
                ConnectionState.DISCOVERED -> "Nearby"
            }
            binding.connectionState.text = stateText

            binding.onlineDot.visibility =
                if (user.connectionState == ConnectionState.CONNECTED) View.VISIBLE else View.INVISIBLE

            binding.root.setOnClickListener { onUserClick(user) }

            // Staggered entrance animation
            binding.root.alpha = 0f
            binding.root.translationX = 30f
            binding.root.animate().alpha(1f).translationX(0f).setDuration(250).start()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNearbyUserBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(getItem(position))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<NearbyUser>() {
            override fun areItemsTheSame(a: NearbyUser, b: NearbyUser) =
                a.deviceAddress == b.deviceAddress
            override fun areContentsTheSame(a: NearbyUser, b: NearbyUser) = a == b
        }
    }
}
