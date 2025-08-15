package com.ccslay.safecircles

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.ccslay.safecircles.zone.LocationCircle

class SavedLocationsAdapter(
    private val circles: MutableList<LocationCircle>,
    private val onNotificationToggle: (LocationCircle, Boolean) -> Unit,
    private val onDelete: (LocationCircle) -> Unit
) : RecyclerView.Adapter<SavedLocationsAdapter.SavedLocationViewHolder>() {

    class SavedLocationViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val locationNameTv: TextView = itemView.findViewById(R.id.savedLocationName_Tv)
        val notificationBtn: ImageButton = itemView.findViewById(R.id.notif_Btn)
        val deleteBtn: Button = itemView.findViewById(R.id.delete_Btn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SavedLocationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_saved_location, parent, false)
        return SavedLocationViewHolder(view)
    }

    override fun onBindViewHolder(holder: SavedLocationViewHolder, position: Int) {
        val circle = circles[position]

        // Set location name (you might want to add a name property to LocationCircle)
        holder.locationNameTv.text = getLocationDisplayName(circle)

        // Set notification button state
//        updateNotificationButton(holder.notificationBtn, circle.isNotificationEnabled)

        // Handle notification button click
//        holder.notificationBtn.setOnClickListener {
//            val newState = !circle.isNotificationEnabled
//            circle.isNotificationEnabled = newState
//            updateNotificationButton(holder.notificationBtn, newState)
//            onNotificationToggle(circle, newState)
//        }

        // Handle delete button click
        holder.deleteBtn.setOnClickListener {
            onDelete(circle)
            circles.removeAt(position)
            notifyItemRemoved(position)
            notifyItemRangeChanged(position, circles.size)
        }
    }

    override fun getItemCount(): Int = circles.size

    private fun getLocationDisplayName(circle: LocationCircle): String {
        // You might want to add a name property to LocationCircle
        // For now, we'll create a display name based on coordinates and type
        val type = if (circle.isDisaster) "Disaster Zone" else "Watch Area"
        val coords = "${String.format("%.4f", circle.center.latitude)}, ${String.format("%.4f", circle.center.longitude)}"
        return "$type - $coords"
    }

    private fun updateNotificationButton(button: ImageButton, isEnabled: Boolean) {
        if (isEnabled) {
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#FFC8A8")
            )
            button.alpha = 1.0f
        } else {
            button.backgroundTintList = android.content.res.ColorStateList.valueOf(
                android.graphics.Color.parseColor("#CCCCCC")
            )
            button.alpha = 0.5f
        }
    }
}