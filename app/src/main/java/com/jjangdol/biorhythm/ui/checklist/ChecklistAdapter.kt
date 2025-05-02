// app/src/main/java/com/jjangdol/biorhythm/ui/checklist/ChecklistAdapter.kt
package com.jjangdol.biorhythm.ui.checklist

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jjangdol.biorhythm.databinding.ItemChecklistBinding
import com.jjangdol.biorhythm.model.ChecklistItem

class ChecklistAdapter(
    private var items: List<ChecklistItem>,
    private val onAnswerChanged: (pos: Int, yes: Boolean) -> Unit
) : RecyclerView.Adapter<ChecklistAdapter.VH>() {

    inner class VH(private val b: ItemChecklistBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(item: ChecklistItem) {
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
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemChecklistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ChecklistItem>) {
        items = newItems
        notifyDataSetChanged()
    }
}
