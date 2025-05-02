// app/src/main/java/com/jjangdol/biorhythm/ui/admin/ResultsAdapter.kt
package com.jjangdol.biorhythm.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jjangdol.biorhythm.databinding.ItemResultBinding
import com.jjangdol.biorhythm.model.ChecklistResult

class ResultsAdapter :
    ListAdapter<ChecklistResult, ResultsAdapter.VH>(object : DiffUtil.ItemCallback<ChecklistResult>() {
        override fun areItemsTheSame(a: ChecklistResult, b: ChecklistResult) = a.userId == b.userId
        override fun areContentsTheSame(a: ChecklistResult, b: ChecklistResult) = a == b
    }) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = VH(
        ItemResultBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    )

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(getItem(position))
    }

    class VH(private val b: ItemResultBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: ChecklistResult) {
            b.tvName.text           = r.name
            b.tvDept.text           = r.dept
            b.tvChecklistScore.text = r.checklistScore.toString()
            b.tvBioIndex.text       = r.biorhythmIndex.toString()
            b.tvFinalScore.text     = r.finalScore.toString()
            b.tvDate.text           = r.date
        }
    }
}
