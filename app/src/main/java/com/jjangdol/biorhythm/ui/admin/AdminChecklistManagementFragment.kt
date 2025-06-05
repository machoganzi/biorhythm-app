package com.jjangdol.biorhythm.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jjangdol.biorhythm.R
import com.jjangdol.biorhythm.databinding.FragmentAdminChecklistManagementBinding
import com.jjangdol.biorhythm.vm.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class AdminChecklistManagementFragment : Fragment(R.layout.fragment_admin_checklist_management) {

    private var _binding: FragmentAdminChecklistManagementBinding? = null
    private val binding get() = _binding!!
    private val viewModel: SettingsViewModel by viewModels()
    private lateinit var adapter: AdminChecklistAdapter
    private var newQuestionWeight = 50

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentAdminChecklistManagementBinding.bind(view)

        setupUI()
        setupRecyclerView()
        setupClickListeners()
        observeData()
    }

    private fun setupUI() {
        // 새 문항 가중치 SeekBar 설정
        binding.sbNewWeight.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                newQuestionWeight = progress
                binding.tvNewWeightValue.text = progress.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun setupRecyclerView() {
        adapter = AdminChecklistAdapter(
            onWeightChanged = { position, weight ->
                viewModel.updateWeight(position, weight)
            },
            onDelete = { position ->
                showDeleteConfirmDialog(position)
            },
            onEdit = { position, newQuestion ->
                viewModel.updateQuestion(position, newQuestion)
                Toast.makeText(requireContext(), "문항이 수정되었습니다", Toast.LENGTH_SHORT).show()
            }
        )

        binding.rvWeights.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = this@AdminChecklistManagementFragment.adapter
        }
    }

    private fun setupClickListeners() {
        // 새 문항 추가
        binding.btnAddQuestion.setOnClickListener {
            val question = binding.etNewQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                viewModel.addQuestionWithWeight(question, newQuestionWeight)
                binding.etNewQuestion.text?.clear()
                // 가중치 초기화
                binding.sbNewWeight.progress = 50
                Toast.makeText(requireContext(), "문항이 추가되었습니다", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "문항 내용을 입력해주세요", Toast.LENGTH_SHORT).show()
            }
        }

        // 저장 버튼
        binding.btnSave.setOnClickListener {
            viewModel.save()
            AlertDialog.Builder(requireContext())
                .setTitle("저장 완료")
                .setMessage("체크리스트 문항 설정이 저장되었습니다.")
                .setPositiveButton("확인", null)
                .show()
        }
    }

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.items.collectLatest { items ->
                adapter.submitList(items)
                binding.tvQuestionCount.text = "총 ${items.size}개"

                // 빈 상태 처리
                if (items.isEmpty()) {
                    binding.rvWeights.visibility = View.GONE
                    binding.emptyLayout.visibility = View.VISIBLE
                } else {
                    binding.rvWeights.visibility = View.VISIBLE
                    binding.emptyLayout.visibility = View.GONE
                }
            }
        }
    }

    private fun showDeleteConfirmDialog(position: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle("문항 삭제")
            .setMessage("이 문항을 삭제하시겠습니까?")
            .setPositiveButton("삭제") { _, _ ->
                viewModel.removeQuestion(position)
                Toast.makeText(requireContext(), "문항이 삭제되었습니다", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

}