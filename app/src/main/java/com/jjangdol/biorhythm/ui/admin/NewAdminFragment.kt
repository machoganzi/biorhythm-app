package com.jjangdol.biorhythm.ui.admin

import android.app.DatePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.jjangdol.biorhythm.R
import com.jjangdol.biorhythm.databinding.FragmentNewAdminBinding
import com.jjangdol.biorhythm.data.ResultsRepository
import com.jjangdol.biorhythm.model.ChecklistResult
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class NewAdminFragment : Fragment(R.layout.fragment_new_admin) {

    private var _binding: FragmentNewAdminBinding? = null
    private val binding get() = _binding!!

    @Inject lateinit var resultsRepository: ResultsRepository

    // Firebase Firestore 인스턴스
    private val firestore = FirebaseFirestore.getInstance()

    private lateinit var adminResultsAdapter: AdminResultsAdapter
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedScoreFilter: ScoreFilter = ScoreFilter.ALL
    private var allResults: List<ChecklistResult> = emptyList() // 캐시된 결과

    enum class ScoreFilter {
        ALL, DANGER, CAUTION, SAFE
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentNewAdminBinding.bind(view)

        setupUI()
        setupRecyclerView()
        setupClickListeners()
        observeData() // loadData() 대신 observeData() 사용
    }

    private fun setupUI() {
        // 로그인 시간 표시
        val currentTime = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        binding.tvLoginTime.text = "로그인 시간: $currentTime"

        // 현재 날짜 표시
        binding.btnDateFilter.text = selectedDate.format(DateTimeFormatter.ofPattern("MM-dd"))

    }

    private fun setupRecyclerView() {
        adminResultsAdapter = AdminResultsAdapter { result ->
            // 클릭 시 상세 정보 다이얼로그 표시
            showResultDetailDialog(result)
        }

        binding.recyclerViewResults.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = adminResultsAdapter
        }
    }

    private fun setupClickListeners() {
        // 점수 필터 칩 그룹
        binding.chipGroupScore.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedScoreFilter = when (checkedIds.firstOrNull()) {
                R.id.chipDanger -> ScoreFilter.DANGER
                R.id.chipCaution -> ScoreFilter.CAUTION
                R.id.chipSafe -> ScoreFilter.SAFE
                else -> ScoreFilter.ALL
            }
            applyFilters() // 필터만 다시 적용
        }

        // 날짜 필터 버튼
        binding.btnDateFilter.setOnClickListener {
            showDatePickerDialog()
        }

        // 새로고침 버튼
        binding.btnRefresh.setOnClickListener {
            observeData() // 데이터 다시 로드
            Toast.makeText(requireContext(), "데이터를 새로고침했습니다", Toast.LENGTH_SHORT).show()
        }

        // 알림 관리 버튼
        binding.btnManageNotifications.setOnClickListener {
            // 알림 관리 화면으로 이동
            try {
                findNavController().navigate(R.id.action_admin_to_notification_management)
            } catch (e: Exception) {
                // Fragment를 직접 교체하는 방법
                parentFragmentManager.beginTransaction()
                    .replace(android.R.id.content, NotificationManagementFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

        // 비밀번호 변경 버튼
        binding.btnChangePassword.setOnClickListener {
            showChangePasswordDialog()
        }
        

        // 체크리스트 문항 관리 버튼
        binding.btnManageChecklist.setOnClickListener {
            try {
                findNavController().navigate(R.id.action_admin_to_checklist_management)
            } catch (e: Exception) {
                // Fragment를 직접 교체하는 방법
                parentFragmentManager.beginTransaction()
                    .replace(android.R.id.content, AdminChecklistManagementFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    // 실제 데이터 관찰
    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 선택된 날짜에 따라 다른 메서드 사용
                resultsRepository.watchResultsByDate(selectedDate).collectLatest { results ->
                    allResults = results
                    applyFilters()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "데이터 로딩 실패: ${e.message}", Toast.LENGTH_LONG).show()

                // 에러 시 빈 상태 표시
                allResults = emptyList()
                applyFilters()
            }
        }
    }

    // 필터 적용
    private fun applyFilters() {
        val filteredResults = when (selectedScoreFilter) {
            ScoreFilter.DANGER -> allResults.filter { it.finalSafetyScore < 50 }
            ScoreFilter.CAUTION -> allResults.filter { it.finalSafetyScore in 50..69 }
            ScoreFilter.SAFE -> allResults.filter { it.finalSafetyScore >= 70 }
            ScoreFilter.ALL -> allResults
        }

        // 통계 업데이트 (전체 결과 기준)
        updateStatistics(allResults)

        // 리스트 업데이트 (ChecklistResult 타입으로)
        adminResultsAdapter.submitList(filteredResults)
        binding.tvResultCount.text = "총 ${filteredResults.size}건 (${selectedDate.format(DateTimeFormatter.ofPattern("MM-dd"))})"

        // 빈 상태 처리
        binding.emptyLayout.visibility =
            if (filteredResults.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateStatistics(results: List<ChecklistResult>) {
        val dangerCount = results.count { it.finalSafetyScore < 50 }
        val cautionCount = results.count { it.finalSafetyScore in 50..69 }
        val safeCount = results.count { it.finalSafetyScore >= 70 }

        binding.tvDangerCount.text = dangerCount.toString()
        binding.tvCautionCount.text = cautionCount.toString()
        binding.tvSafeCount.text = safeCount.toString()
    }

    private fun showDatePickerDialog() {
        val picker = DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                selectedDate = LocalDate.of(year, month + 1, dayOfMonth)
                binding.btnDateFilter.text = selectedDate.format(DateTimeFormatter.ofPattern("MM-dd"))

                // 날짜 변경 시 새로운 데이터 로드
                observeData()
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        )
        picker.show()
    }

    private fun showResultDetailDialog(result: ChecklistResult) {
        val message = buildString {
            appendLine("=== 기본 정보 ===")
            appendLine("이름: ${result.name}")
            appendLine("부서: ${result.dept}")
            appendLine("날짜: ${result.date}")
            appendLine()

            appendLine("=== 점수 상세 ===")
            appendLine("최종 안전 점수: ${result.finalSafetyScore}점")
            appendLine("체크리스트 점수: ${result.checklistScore}점")
            appendLine("바이오리듬 지수: ${result.biorhythmIndex}")
            if (result.finalScore != 0) {
                appendLine("기본 최종 점수: ${result.finalScore}점")
            }
            appendLine()

            appendLine("=== 생체신호 측정 ===")
            appendLine("맥박(PPG) 점수: ${result.ppgScore}점")
            appendLine("동공 측정 점수: ${result.pupilScore}점")
            appendLine("손떨림 측정 점수: ${result.tremorScore}점")

            if (result.timestamp != 0L) {
                appendLine()
                appendLine("측정 시간: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(result.timestamp))}")
            }
        }

        AlertDialog.Builder(requireContext())
            .setTitle("측정 결과 상세")
            .setMessage(message)
            .setPositiveButton("확인", null)
            .show()
    }

    private fun showChangePasswordDialog() {
        val layout = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(50, 40, 50, 10)
        }

        val currentPasswordEdit = EditText(requireContext()).apply {
            hint = "현재 비밀번호"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val newPasswordEdit = EditText(requireContext()).apply {
            hint = "새 비밀번호"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val confirmPasswordEdit = EditText(requireContext()).apply {
            hint = "새 비밀번호 확인"
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                    android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        layout.addView(currentPasswordEdit)
        layout.addView(newPasswordEdit)
        layout.addView(confirmPasswordEdit)

        AlertDialog.Builder(requireContext())
            .setTitle("비밀번호 변경")
            .setView(layout)
            .setPositiveButton("변경") { _, _ ->
                val currentPassword = currentPasswordEdit.text.toString()
                val newPassword = newPasswordEdit.text.toString()
                val confirmPassword = confirmPasswordEdit.text.toString()

                changePassword(currentPassword, newPassword, confirmPassword)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // Firebase를 사용한 비밀번호 확인
    private fun checkCurrentPassword(inputPassword: String, callback: (Boolean) -> Unit) {
        firestore.collection("password")
            .document("admin")
            .get()
            .addOnSuccessListener { document ->
                val savedPassword = document.getString("password") ?: "admin123"
                callback(inputPassword == savedPassword)
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "비밀번호 확인 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
                callback(false)
            }
    }

    // Firebase를 사용한 비밀번호 업데이트
    private fun updatePasswordInFirebase(newPassword: String) {
        val passwordData = mapOf("password" to newPassword)

        firestore.collection("password")
            .document("admin")
            .set(passwordData)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "비밀번호가 성공적으로 변경되었습니다", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Toast.makeText(requireContext(), "비밀번호 변경 실패: ${exception.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun changePassword(current: String, new: String, confirm: String) {
        when {
            new.length < 4 -> {
                Toast.makeText(requireContext(), "새 비밀번호는 4자 이상이어야 합니다", Toast.LENGTH_SHORT).show()
            }
            new != confirm -> {
                Toast.makeText(requireContext(), "새 비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
            }
            else -> {
                // Firebase에서 현재 비밀번호 확인
                checkCurrentPassword(current) { isValid ->
                    if (isValid) {
                        // 현재 비밀번호가 맞으면 새 비밀번호로 업데이트
                        updatePasswordInFirebase(new)
                    } else {
                        Toast.makeText(requireContext(), "현재 비밀번호가 올바르지 않습니다", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }



    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}