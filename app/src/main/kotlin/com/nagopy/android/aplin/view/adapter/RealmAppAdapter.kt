/*
 * Copyright (C) 2015 75py
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nagopy.android.aplin.view.adapter

import android.content.Context
import android.support.v4.content.ContextCompat
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import com.nagopy.android.aplin.R
import com.nagopy.android.aplin.constants.Constants
import com.nagopy.android.aplin.entity.AppEntity
import com.nagopy.android.aplin.gone
import com.nagopy.android.aplin.model.Category
import com.nagopy.android.aplin.model.DisplayItem
import com.nagopy.android.aplin.model.IconHelper
import com.nagopy.android.aplin.visible
import io.realm.RealmResults

public class RealmAppAdapter(
        val context: Context
        , val category: Category
        , val iconHelper: IconHelper
        , val onListItemClicked: (app: AppEntity) -> Unit
        , val onListItemLongClicked: (app: AppEntity) -> Unit
) : RecyclerView.Adapter<RealmAppAdapter.ViewHolder>() {

    var realmResults: RealmResults<AppEntity>? = null
    var displayItems: List<DisplayItem> = emptyList()

    fun updateRealmResult(realmResults: RealmResults<AppEntity>) {
        this.realmResults?.removeChangeListeners()
        this.realmResults = realmResults
        realmResults.removeChangeListeners()
        realmResults.addChangeListener { notifyDataSetChanged() }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView?) {
        super.onAttachedToRecyclerView(recyclerView)
        realmResults?.removeChangeListeners()
        realmResults?.addChangeListener {
            notifyDataSetChanged()
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView?) {
        super.onDetachedFromRecyclerView(recyclerView)
        realmResults?.removeChangeListeners()
    }

    override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): ViewHolder? {
        val view = LayoutInflater.from(context).inflate(R.layout.list_item, parent, false)
        val holder = ViewHolder(view)

        view.setOnClickListener { view ->
            onListItemClicked(realmResults!![holder.adapterPosition])
        }
        view.setOnLongClickListener { view ->
            onListItemLongClicked(realmResults!![holder.adapterPosition])
            return@setOnLongClickListener true
        }

        holder.icon.scaleType = ImageView.ScaleType.FIT_CENTER
        holder.icon.layoutParams.width = iconHelper.iconSize
        holder.icon.layoutParams.height = iconHelper.iconSize

        return holder
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entity = realmResults!![position]

        val textColor = ContextCompat.getColor(context,
                if (entity.isEnabled) R.color.text_color else R.color.textColorTertiary)

        holder.label.text = entity.label
        holder.label.setTextColor(textColor)

        holder.packageName.text = entity.packageName
        holder.packageName.setTextColor(textColor)

        val sb = StringBuilder()
        for (item in displayItems) {
            if (item.append(context, sb, entity)) {
                sb.append(Constants.LINE_SEPARATOR)
            }
        }
        if (sb.length > 0) {
            sb.setLength(sb.length - 1)
            var infoString = sb.toString().trim()
            infoString = infoString.replace((Constants.LINE_SEPARATOR + "+").toRegex(), Constants.LINE_SEPARATOR)
            holder.status.text = infoString
            holder.status.visible()
        } else {
            holder.status.text = ""
            holder.status.gone()
        }
        holder.status.setTextColor(textColor)

        holder.icon.setImageDrawable(iconHelper.getIcon(entity))
    }

    override fun getItemCount(): Int = realmResults?.size ?: 0

    class ViewHolder : RecyclerView.ViewHolder {

        val icon: ImageView
        val label: TextView
        val status: TextView
        val packageName: TextView

        constructor(parentView: View) : super(parentView) {
            icon = parentView.findViewById(R.id.icon) as ImageView
            label = parentView.findViewById(R.id.label) as TextView
            status = parentView.findViewById(R.id.status) as TextView
            packageName = parentView.findViewById(R.id.packageName) as TextView
        }

    }
}
