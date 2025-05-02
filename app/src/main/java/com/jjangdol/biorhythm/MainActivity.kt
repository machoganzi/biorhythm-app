package com.jjangdol.biorhythm
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI.setupActionBarWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.jjangdol.biorhythm.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ① 툴바를 액션바로 설정
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // ② NavController 초기화
        val host = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = host.navController

        // ③ 액션바와 NavController 연동 (업 버튼, 타이틀 자동 변경)
        setupActionBarWithNavController(this, navController)
    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()
}
