// app/src/main/java/com/jjangdol/biorhythm/ui/admin/AdminResultsFragment.kt
package com.jjangdol.biorhythm.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jjangdol.biorhythm.R
import com.jjangdol.biorhythm.databinding.FragmentAdminResultsBinding
import com.jjangdol.biorhythm.data.ResultsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@AndroidEntryPoint
class AdminResultsFragment : Fragment(R.layout.fragment_admin_results) {

    private var _b: FragmentAdminResultsBinding? = null
    private val b get() = _b!!

    @Inject lateinit var repo: ResultsRepository

    private val riskAdapter = ResultsAdapter()
    private val safeAdapter = ResultsAdapter()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _b = FragmentAdminResultsBinding.bind(view)

        // 오늘 날짜 표시
        b.tvDate.text = LocalDate.now()
            .format(DateTimeFormatter.ISO_DATE)

        // 위험군 RecyclerView
        b.rvRisk.layoutManager = LinearLayoutManager(requireContext())
        b.rvRisk.adapter = riskAdapter

        // 비위험군 RecyclerView
        b.rvSafe.layoutManager = LinearLayoutManager(requireContext())
        b.rvSafe.adapter = safeAdapter

        // 실시간 구독
        lifecycleScope.launch {
            repo.watchTodayResults().collectLatest { list ->
                if (list.isEmpty()) {
                    Toast.makeText(requireContext(), "오늘의 결과가 없습니다", Toast.LENGTH_SHORT).show()
                }
                val threshold = 50
                // finalScore 로 분기
                val riskList = list.filter  { it.finalScore <  threshold }
                val safeList = list.filter { it.finalScore >= threshold }

                riskAdapter.submitList(riskList)
                safeAdapter.submitList(safeList)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
