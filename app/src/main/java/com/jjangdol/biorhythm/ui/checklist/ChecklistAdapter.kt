package com.jjangdol.biorhythm.ui.checklist

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
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

            // 기존 리스너 제거 (중복 반응 방지)
            b.btnYes.setOnCheckedChangeListener(null)
            b.btnNo.setOnCheckedChangeListener(null)

            // 체크 상태 반영
            b.btnYes.isChecked = item.answeredYes == true
            b.btnNo.isChecked = item.answeredYes == false

            // 변경된 후 리스너 재등록
            b.btnYes.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    adapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let {
                        onAnswerChanged(it, true)
                        notifyItemChanged(adapterPosition)
                    }
                }
            }

            b.btnNo.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    adapterPosition.takeIf { it != RecyclerView.NO_POSITION }?.let {
                        onAnswerChanged(it, false)
                        notifyItemChanged(adapterPosition)
                    }
                }
            }

            // 구분선 처리
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
        val diffCallback = object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition].id == newItems[newItemPosition].id
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return items[oldItemPosition] == newItems[newItemPosition]
            }
        }

        val diffResult = DiffUtil.calculateDiff(diffCallback)
        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }
}
