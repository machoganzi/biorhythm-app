package com.jjangdol.biorhythm.ui.checklist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jjangdol.biorhythm.databinding.ItemChecklistBinding
import com.jjangdol.biorhythm.model.ChecklistItem

class ChecklistAdapter(
    private var items: List<ChecklistItem>,
    private val onAnswerChanged: (pos: Int, yes: Boolean) -> Unit
) : RecyclerView.Adapter<ChecklistAdapter.VH>() {

    inner class VH(private val b: ItemChecklistBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ChecklistItem, isLast: Boolean) {
            b.tvQuestion.text = item.question
            b.btnYes.isChecked = item.answeredYes == true
            b.btnNo.isChecked  = item.answeredYes == false

            b.btnYes.setOnClickListener {
                adapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?.let { onAnswerChanged(it, true) }
            }
            b.btnNo.setOnClickListener {
                adapterPosition.takeIf { it != RecyclerView.NO_POSITION }
                    ?.let { onAnswerChanged(it, false) }
            }

            // 마지막 항목이면 구분선 숨기기
            b.viewDivider.visibility = if (isLast) View.GONE else View.VISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemChecklistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val isLast = position == items.lastIndex
        holder.bind(items[position], isLast)
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ChecklistItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
