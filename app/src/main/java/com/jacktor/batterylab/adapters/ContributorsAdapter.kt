package com.jacktor.batterylab.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.jacktor.batterylab.R
import com.jacktor.batterylab.interfaces.ContributorsInterface
import com.jacktor.batterylab.models.ContributorsModel
import com.squareup.picasso.Picasso

class ContributorsAdapter(
    private val context: Context,
    private val list: List<ContributorsModel>,
    private val recyclerViewClickListener: ContributorsInterface
) : RecyclerView.Adapter<ContributorsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.name)
        val username: TextView = view.findViewById(R.id.username)
        val contributions: TextView = view.findViewById(R.id.contributions)
        val avatar: ImageView = view.findViewById(R.id.iv)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view =
            LayoutInflater.from(context).inflate(R.layout.about_contributors_item, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val data = list[position]
        holder.name.text = data.name
        holder.username.text = data.username
        holder.contributions.text =
            context.getString(R.string.contributions, data.contributions.toString())
        Picasso.get().load(data.avatarUrl).into(holder.avatar)

        holder.itemView.setOnClickListener {
            recyclerViewClickListener.onItemClick(data)
        }
    }
}
