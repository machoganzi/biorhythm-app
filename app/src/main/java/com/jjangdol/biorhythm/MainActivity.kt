package com.jjangdol.biorhythm
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.NavigationUI.setupActionBarWithNavController
import com.google.android.material.appbar.MaterialToolbar
import com.jjangdol.biorhythm.R
import dagger.hilt.android.AndroidEntryPoint
import uploadEntriesFromAssets

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private lateinit var navController: NavController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 데이터 베이스 업로드 함수
//        uploadEntriesFromAssets(this)
        setContentView(R.layout.activity_main)

        //  NavController 초기화
        val host = supportFragmentManager
            .findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = host.navController

    }

    override fun onSupportNavigateUp(): Boolean =
        navController.navigateUp() || super.onSupportNavigateUp()

}
