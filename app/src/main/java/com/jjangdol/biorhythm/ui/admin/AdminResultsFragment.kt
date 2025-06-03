package com.jjangdol.biorhythm.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
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

        // 오늘 날짜 출력
        b.tvDate.text = LocalDate.now().format(DateTimeFormatter.ISO_DATE)

        // RecyclerView 설정
        b.rvRisk.layoutManager = LinearLayoutManager(requireContext())
        b.rvRisk.adapter = riskAdapter

        b.rvSafe.layoutManager = LinearLayoutManager(requireContext())
        b.rvSafe.adapter = safeAdapter

        // 데이터 수신 및 분류
        lifecycleScope.launch {
            repo.watchTodayResults().collectLatest { list ->
                if (list.isEmpty()) {
                    Toast.makeText(requireContext(), "오늘의 결과가 없습니다", Toast.LENGTH_SHORT).show()
                }
                val threshold = 50
                val riskList = list.filter { it.finalScore < threshold }
                val safeList = list.filter { it.finalScore >= threshold }

                riskAdapter.setData(riskList)
                safeAdapter.setData(safeList)
            }
        }

        // 이름 검색 필터
        b.etSearch.addTextChangedListener {
            val query = it?.toString() ?: ""
            riskAdapter.filter(query)
            safeAdapter.filter(query)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
