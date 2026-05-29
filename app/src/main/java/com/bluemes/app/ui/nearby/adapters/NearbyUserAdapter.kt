package com.bluemes.app.ui.nearby.adapters

import android.view.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bluemes.app.databinding.ItemNearbyUserBinding
import com.bluemes.app.models.ConnectionState
import com.bluemes.app.models.NearbyUser

class NearbyUserAdapter(
    private val onClick: (NearbyUser) -> Unit
) : ListAdapter<NearbyUser, NearbyUserAdapter.VH>(DIFF) {

    inner class VH(private val b: ItemNearbyUserBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(u: NearbyUser) {
            b.userName.text = u.userName
            b.deviceName.text = u.deviceName
            b.signalStrength.text = u.signalStrengthLabel()
            b.connectionState.text = when (u.connectionState) {
                ConnectionState.CONNECTED    -> "Connected"
                ConnectionState.CONNECTING   -> "Connecting…"
                ConnectionState.DISCONNECTED -> "Offline"
                ConnectionState.FAILED       -> "Failed"
                ConnectionState.DISCOVERED   -> "Nearby"
            }
            b.onlineDot.visibility = if (u.connectionState == ConnectionState.CONNECTED) View.VISIBLE else View.INVISIBLE
            b.root.setOnClickListener { onClick(u) }
            b.root.alpha = 0f; b.root.translationX = 30f
            b.root.animate().alpha(1f).translationX(0f).setDuration(220).start()
        }
    }

    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(ItemNearbyUserBinding.inflate(LayoutInflater.from(p.context), p, false))

    override fun onBindViewHolder(h: VH, pos: Int) = h.bind(getItem(pos))

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<NearbyUser>() {
            override fun areItemsTheSame(a: NearbyUser, b: NearbyUser) = a.deviceAddress == b.deviceAddress
            override fun areContentsTheSame(a: NearbyUser, b: NearbyUser) = a == b
        }
    }
}
