package com.jjangdol.biorhythm.ui.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.jjangdol.biorhythm.R
import com.jjangdol.biorhythm.databinding.FragmentMainBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainFragment : Fragment(R.layout.fragment_main) {

    private var _binding: FragmentMainBinding? = null
    private val binding get() = _binding!!

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentMainBinding.bind(view)

        // 2) childFragmentManager 에서 NavController 가져오기
        val navHost = childFragmentManager
            .findFragmentById(R.id.bottomNavHost) as NavHostFragment
        val navController = navHost.navController

        // 3) BottomNavigationView 와 NavController 연결
        binding.bottomNav.setupWithNavController(navController)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
