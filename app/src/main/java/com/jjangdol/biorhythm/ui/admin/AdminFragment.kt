// app/src/main/java/com/jjangdol/biorhythm/ui/admin/AdminFragment.kt
package com.jjangdol.biorhythm.ui.admin

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.jjangdol.biorhythm.R
import com.jjangdol.biorhythm.databinding.FragmentAdminBinding
import com.jjangdol.biorhythm.vm.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.jjangdol.biorhythm.ui.admin.SettingsAdapter

@AndroidEntryPoint
class AdminFragment : Fragment(R.layout.fragment_admin) {

    private var _b: FragmentAdminBinding? = null
    private val b get() = _b!!
    private val vm: SettingsViewModel by viewModels()
    private lateinit var adapter: SettingsAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        _b = FragmentAdminBinding.bind(view)

        // RecyclerView 세팅
        b.rvWeights.layoutManager = LinearLayoutManager(requireContext())
        adapter = SettingsAdapter(
            emptyList(),
            onWeightChanged = { pos, w -> vm.updateWeight(pos, w) },
            onDelete = { pos        -> vm.removeQuestion(pos) }
        )
        b.rvWeights.adapter = adapter

        // 실시간 구독해서 어댑터 갱신
        viewLifecycleOwner.lifecycleScope.launch {
            vm.items.collectLatest { adapter.updateItems(it) }
        }

        // 새 문항 추가
        b.btnAddQuestion.setOnClickListener {
            val q = b.etNewQuestion.text.toString().trim()
            if (q.isNotEmpty()) {
                vm.addQuestion(q)
                b.etNewQuestion.text?.clear()
            } else {
                Toast.makeText(requireContext(), "문항을 입력하세요", Toast.LENGTH_SHORT).show()
            }
        }

        // 저장 알림 (실제 DB 반영은 위에서 즉시 일어남)
        b.btnSave.setOnClickListener {
            vm.save()
            AlertDialog.Builder(requireContext())
                .setMessage("문항 설정이 저장되었습니다.")
                .setPositiveButton("확인", null)
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _b = null
    }
}
