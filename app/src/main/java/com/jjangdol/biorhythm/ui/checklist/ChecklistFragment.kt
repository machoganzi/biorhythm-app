// app/src/main/java/com/jjangdol/biorhythm/ui/checklist/ChecklistFragment.kt
package com.jjangdol.biorhythm.ui.checklist

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jjangdol.biorhythm.R
import com.jjangdol.biorhythm.databinding.FragmentChecklistBinding
import com.jjangdol.biorhythm.model.ChecklistResult
import com.jjangdol.biorhythm.model.ChecklistItem
import com.jjangdol.biorhythm.util.ScoreCalculator
import com.jjangdol.biorhythm.vm.BiorhythmViewModel
import com.jjangdol.biorhythm.vm.ChecklistViewModel
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@AndroidEntryPoint
class ChecklistFragment : Fragment(R.layout.fragment_checklist) {

    private var _b: FragmentChecklistBinding? = null
    private val b get() = _b!!

    private val vm: ChecklistViewModel by viewModels()
    private val bioVm: BiorhythmViewModel by viewModels()

    private val dateFormatter = DateTimeFormatter.ISO_DATE

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _b = FragmentChecklistBinding.bind(view)

        // RecyclerView
        b.rvChecklist.layoutManager = LinearLayoutManager(requireContext())
        val adapter = ChecklistAdapter(emptyList()) { pos, yes ->
            vm.answerChanged(pos, yes)
        }
        b.rvChecklist.adapter = adapter

        // 실시간 항목 구독
        viewLifecycleOwner.lifecycleScope.launch {
            vm.items.collect { list ->
                adapter.updateItems(list)
            }
        }

        // SharedPreferences에서 유저 정보 로드
        val prefs = requireContext()
            .getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val dobStr = prefs.getString("dob", LocalDate.now().format(dateFormatter))!!
        val name   = prefs.getString("user_name", "")!!
        val dept   = prefs.getString("user_dept", "")!!

        // 바이오리듬 로드
        val dob = LocalDate.parse(dobStr, dateFormatter)
        bioVm.load(dob)

        // 제출 버튼
        b.btnSubmit.setOnClickListener {
            lifecycleScope.launch {
                // 체크리스트 점수
                val items = vm.items.value
                val checklistScore = ScoreCalculator.calcChecklistScore(items)

                // 오늘자 바이오리듬
                val todayDate = LocalDate.now()
                val todayData = bioVm.data.value
                    .firstOrNull { it.date == todayDate }
                if (todayData == null) {
                    Toast.makeText(requireContext(), "바이오리듬 데이터 없음", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val bioIndex = ScoreCalculator.calcBiorhythmIndex(todayData)

                // 최종점수
                val finalScore = ScoreCalculator.calcFinalScore(checklistScore, bioIndex)

                // Firestore에 저장 (results/{date}/entries/{uid})
                val today = todayDate.format(dateFormatter)
                val uid   = Firebase.auth.currentUser!!.uid

                val result = ChecklistResult(
                    userId = uid,
                    name = name,
                    dept = dept,
                    checklistScore = checklistScore,
                    biorhythmIndex = bioIndex,
                    finalScore = finalScore,
                    date = today
                )

                Firebase.firestore
                    .collection("results")
                    .document(today)
                    .collection("entries")
                    .document(uid)
                    .set(result)
                    .addOnSuccessListener {
                        Toast.makeText(requireContext(), "제출 완료", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(requireContext(), "제출 실패", Toast.LENGTH_SHORT).show()
                    }
                Firebase.firestore
                    .collection("results")
                    .document(uid)
                    .collection("daily")
                    .document(today)
                    .set(result)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
