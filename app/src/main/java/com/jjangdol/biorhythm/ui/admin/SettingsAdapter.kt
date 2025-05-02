// app/src/main/java/com/jjangdol/biorhythm/ui/admin/SettingsAdapter.kt
package com.jjangdol.biorhythm.ui.admin

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.View.OnFocusChangeListener
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jjangdol.biorhythm.databinding.ItemSettingsBinding
import com.jjangdol.biorhythm.model.ChecklistWeight

class SettingsAdapter(
    private var list: List<ChecklistWeight>,
    private val onWeightChanged: (pos: Int, newWeight: Int) -> Unit,
    private val onDelete: (pos: Int) -> Unit
) : RecyclerView.Adapter<SettingsAdapter.Holder>() {

    inner class Holder(private val binding: ItemSettingsBinding) : RecyclerView.ViewHolder(binding.root) {
        private var weightWatcher: TextWatcher? = null

        fun bind(item: ChecklistWeight) {
            // 질문 텍스트 설정
            binding.tvQuestion.text = item.question

            // 이전에 붙인 watcher 제거
            weightWatcher?.let { binding.etWeight.removeTextChangedListener(it) }
            // EditText 에 기존 가중치 설정
            binding.etWeight.setText(item.weight.toString())

            // 포커스가 떠났을 때만 서버에 저장 콜백 호출
            binding.etWeight.onFocusChangeListener = OnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) {
                    val pos = adapterPosition
                    val newText = binding.etWeight.text.toString()
                    val newWeight = newText.toIntOrNull()
                    if (pos != RecyclerView.NO_POSITION && newWeight != null) {
                        onWeightChanged(pos, newWeight)
                    }
                }
            }

            weightWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {}
            }
            binding.etWeight.addTextChangedListener(weightWatcher)

            // 삭제 버튼 클릭
            binding.btnDelete.setOnClickListener {
                val pos = adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDelete(pos)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemSettingsBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(list[position])
    }

    override fun getItemCount(): Int = list.size

    fun updateItems(newList: List<ChecklistWeight>) {
        list = newList
        notifyDataSetChanged()
    }
}
