package com.jjangdol.biorhythm.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jjangdol.biorhythm.databinding.ItemAdminChecklistBinding
import com.jjangdol.biorhythm.model.ChecklistConfig

class AdminChecklistAdapter(
    private val onWeightChanged: (Int, Int) -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onEdit: (Int, String) -> Unit
) : ListAdapter<ChecklistConfig, AdminChecklistAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAdminChecklistBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), position)
    }

    inner class ViewHolder(
        private val binding: ItemAdminChecklistBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private var isEditMode = false
        private var originalQuestion = ""

        fun bind(item: ChecklistConfig, position: Int) {
            with(binding) {
                // 문항 번호
                tvQuestionNumber.text = (position + 1).toString()

                // 문항 내용
                etQuestion.setText(item.question)
                originalQuestion = item.question

                // 가중치
                sbWeight.progress = item.weight
                tvWeightValue.text = item.weight.toString()

                // 가중치 변경 리스너
                sbWeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            tvWeightValue.text = progress.toString()
                            onWeightChanged(position, progress)
                        }
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })

                // 수정 버튼
                btnEdit.setOnClickListener {
                    if (!isEditMode) {
                        enterEditMode()
                    }
                }

                // 삭제 버튼
                btnDelete.setOnClickListener {
                    onDelete(position)
                }

                // 수정 취소 버튼
                btnCancel.setOnClickListener {
                    exitEditMode()
                    etQuestion.setText(originalQuestion)
                }

                // 수정 저장 버튼
                btnSaveEdit.setOnClickListener {
                    val newQuestion = etQuestion.text.toString().trim()
                    if (newQuestion.isNotEmpty()) {
                        onEdit(position, newQuestion)
                        originalQuestion = newQuestion
                        exitEditMode()
                    }
                }
            }
        }

        private fun enterEditMode() {
            isEditMode = true
            with(binding) {
                etQuestion.isEnabled = true
                etQuestion.setBackgroundResource(android.R.drawable.edit_text)
                etQuestion.requestFocus()
                etQuestion.setSelection(etQuestion.text?.length ?: 0)

                btnEdit.visibility = View.GONE
                btnDelete.visibility = View.GONE
                editButtonsLayout.visibility = View.VISIBLE
                sbWeight.isEnabled = false
            }
        }

        private fun exitEditMode() {
            isEditMode = false
            with(binding) {
                etQuestion.isEnabled = false
                etQuestion.setBackgroundResource(android.R.color.transparent)

                btnEdit.visibility = View.VISIBLE
                btnDelete.visibility = View.VISIBLE
                editButtonsLayout.visibility = View.GONE
                sbWeight.isEnabled = true
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<ChecklistConfig>() {
        override fun areItemsTheSame(oldItem: ChecklistConfig, newItem: ChecklistConfig): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChecklistConfig, newItem: ChecklistConfig): Boolean {
            return oldItem == newItem
        }
    }
}