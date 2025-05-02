// app/src/main/java/com/jjangdol/biorhythm/ui/admin/AdminResultsAdapter.kt
package com.jjangdol.biorhythm.ui.admin

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jjangdol.biorhythm.databinding.ItemAdminResultBinding
import com.jjangdol.biorhythm.model.ChecklistResult

class AdminResultsAdapter(
    private var items: List<ChecklistResult>
) : RecyclerView.Adapter<AdminResultsAdapter.VH>() {

    inner class VH(val b: ItemAdminResultBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(r: ChecklistResult) {
            b.tvName.text = r.name
            b.tvDept.text = r.dept
            b.tvScore.text = r.finalScore.toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemAdminResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<ChecklistResult>) {
        items = newItems
        notifyDataSetChanged()
    }
}
