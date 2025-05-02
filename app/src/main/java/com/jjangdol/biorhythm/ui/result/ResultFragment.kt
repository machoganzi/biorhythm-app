package com.jjangdol.biorhythm.ui.result

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.jjangdol.biorhythm.R
import com.jjangdol.biorhythm.databinding.FragmentResultBinding
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ResultFragment : Fragment(R.layout.fragment_result) {

    private var _binding: FragmentResultBinding? = null
    private val binding get() = _binding!!

    private val auth = Firebase.auth
    private val db   = Firebase.firestore
    private val dateFormatter = DateTimeFormatter.ISO_DATE

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _binding = FragmentResultBinding.bind(view)

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(requireContext(), "로그인이 필요합니다", Toast.LENGTH_SHORT).show()
            return
        }
        val today = LocalDate.now().format(dateFormatter)

        // 오늘자 결과 문서 읽기
        db.collection("results")
            .document(uid)
            .collection("daily")
            .document(today)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.exists()) {
                    val checklistScore = snap.getLong("checklistScore")?.toInt() ?: 0
                    val bioIndex       = snap.getLong("biorhythmIndex")?.toInt() ?: 0
                    val finalScore     = snap.getLong("finalScore")?.toInt() ?: 0

                    binding.tvChecklistScore.text = checklistScore.toString()
                    binding.tvBioIndex.text       = bioIndex.toString()
                    binding.tvFinalScore.text     = finalScore.toString()

                    // 차트 데이터 세팅
                    val entries = listOf(
                        Entry(0f, checklistScore.toFloat()),
                        Entry(1f, bioIndex.toFloat()),
                        Entry(2f, finalScore.toFloat())
                    )
                    val dataSet = LineDataSet(entries, "점수 흐름").apply {
                        setDrawCircles(true)
                        setDrawValues(false)
                    }
                    binding.lineChart.apply {
                        data = LineData(dataSet)
                        description.isEnabled = false
                        legend.isEnabled = false
                        invalidate()
                    }
                } else {
                    Toast.makeText(requireContext(), "오늘의 결과가 없습니다", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "결과 로드 실패", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
