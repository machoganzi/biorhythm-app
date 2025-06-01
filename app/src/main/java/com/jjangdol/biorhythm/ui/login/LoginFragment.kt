package com.jjangdol.biorhythm.ui.login

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.jjangdol.biorhythm.R
import com.jjangdol.biorhythm.databinding.FragmentLoginBinding
import com.jjangdol.biorhythm.vm.LoginState
import com.jjangdol.biorhythm.vm.LoginViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.content.Context

@AndroidEntryPoint
class LoginFragment : Fragment(R.layout.fragment_login) {

    private val vm: LoginViewModel by viewModels()
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val dateFormatter = DateTimeFormatter.ISO_DATE

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentLoginBinding.bind(view)

        // 부서 목록 설정
        val depts = listOf("--부서 선택--", "생산", "품질", "안전", "관리")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, depts)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerDept.adapter = adapter

        // 생년월일 선택
        binding.tvDob.setOnClickListener {
            val today = LocalDate.now()
            DatePickerDialog(requireContext(),
                { _, y, m, d ->
                    val dob = LocalDate.of(y, m + 1, d)
                    binding.tvDob.text = dob.format(dateFormatter)
                },
                today.year, today.monthValue - 1, today.dayOfMonth
            ).show()
        }

        // 로그인 버튼 클릭
        binding.btnLogin.setOnClickListener {
            val dept = binding.spinnerDept.selectedItem?.toString() ?: ""
            val name = binding.etName.text.toString()
            val dob = binding.tvDob.text.toString()

            vm.login(dept, name, dob)

            // SharedPreferences 저장
            val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("dob", dob)
                .putString("user_name", name)
                .putString("user_dept", dept)
                .apply()
        }

        // 상태 관찰
        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.state.collectLatest { state ->
                when (state) {
                    is LoginState.Loading -> {
                        binding.btnLogin.isEnabled = false
                        binding.btnLogin.text = "로그인 중…"
                    }
                    is LoginState.Success -> {
                        findNavController().navigate(R.id.action_login_to_main)
                    }
                    is LoginState.Error -> {
                        binding.btnLogin.isEnabled = true
                        binding.btnLogin.text = getString(R.string.login)
                        Toast.makeText(requireContext(), state.message, Toast.LENGTH_SHORT).show()
                    }
                    else -> Unit
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
