package com.jjangdol.biorhythm.ui.login

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
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

        // 1) Spinner에 부서 목록 세팅
        val depts = listOf("생산", "품질", "안전", "관리")
        binding.spinnerDept.adapter =
            ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, depts)
                .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        // 2) DatePickerDialog 연결
        binding.tvDob.setOnClickListener {
            val today = LocalDate.now()
            DatePickerDialog(requireContext(),
                { _, y, m, d ->
                    val dob = LocalDate.of(y, m+1, d)
                    binding.tvDob.text = dob.format(dateFormatter)
                },
                today.year, today.monthValue - 1, today.dayOfMonth
            ).show()
        }

        // 3) 로그인 버튼
        binding.btnLogin.setOnClickListener {
            val dept = binding.spinnerDept.selectedItem as String
            val name = binding.etName.text.toString()
            val dob = binding.tvDob.text.toString()
            vm.login(dept, name, dob)

            // 로그인 정보 저장
            val prefs = requireContext().getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
            prefs.edit()
                .putString("dob", dob)
                .putString("user_name", name)
                .putString("user_dept", dept)
                .apply()

        }

        // 4) ViewModel 상태 관찰
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
                        binding.btnLogin.text = "로그인"
                        Toast.makeText(requireContext(), state.message, LENGTH_SHORT).show()
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
