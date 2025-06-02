package com.jjangdol.biorhythm.ui.measurement

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jjangdol.biorhythm.model.MeasurementResult
import com.jjangdol.biorhythm.model.MeasurementState
import com.jjangdol.biorhythm.model.MeasurementType
import com.jjangdol.biorhythm.vm.SafetyCheckViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 모든 측정 화면의 기본 클래스
 * 공통 기능: 권한 처리, 측정 상태 관리, 결과 저장, 네비게이션
 */
abstract class BaseMeasurementFragment : Fragment() {

    protected val safetyCheckViewModel: SafetyCheckViewModel by activityViewModels()

    abstract val measurementType: MeasurementType
    abstract val requiredPermissions: Array<String>
    abstract val nextNavigationAction: Int?

    protected var measurementJob: Job? = null
    protected var currentState: MeasurementState = MeasurementState.Idle

    // 권한 요청 런처
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onPermissionsGranted()
        } else {
            onPermissionsDenied()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkAndRequestPermissions()
    }

    /**
     * 권한 확인 및 요청
     */
    private fun checkAndRequestPermissions() {
        val notGranted = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) !=
                    PackageManager.PERMISSION_GRANTED
        }

        if (notGranted.isEmpty()) {
            onPermissionsGranted()
        } else {
            permissionLauncher.launch(requiredPermissions)
        }
    }

    /**
     * 권한이 승인되었을 때 호출 자동 시작 X
     */
    protected open fun onPermissionsGranted() {
//        startMeasurement()
    }

    /**
     * 권한이 거부되었을 때 호출
     */
    protected open fun onPermissionsDenied() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("권한 필요")
            .setMessage("${measurementType.displayName}을 위해 권한이 필요합니다.")
            .setPositiveButton("설정으로 이동") { _, _ ->
                // 앱 설정으로 이동
                openAppSettings()
            }
            .setNegativeButton("건너뛰기") { _, _ ->
                skipMeasurement()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * 측정 시작 - 하위 클래스에서 구현
     */
    protected abstract fun startMeasurement()

    /**
     * 측정 완료 처리
     */
    protected fun onMeasurementComplete(score: Float, rawData: String? = null) {
        val result = MeasurementResult(
            type = measurementType,
            score = score,
            rawData = rawData,
            metadata = getMeasurementMetadata()
        )

        // ViewModel에 결과 저장
        safetyCheckViewModel.addMeasurementResult(result)

        // 다음 화면으로 이동
        navigateToNext()
    }

    /**
     * 측정 건너뛰기
     */
    protected fun skipMeasurement() {
        val result = MeasurementResult(
            type = measurementType,
            score = 0f,
            metadata = mapOf("skipped" to "true")
        )

        safetyCheckViewModel.addMeasurementResult(result)
        navigateToNext()
    }

    /**
     * 다음 화면으로 이동
     */
    private fun navigateToNext() {
        nextNavigationAction?.let { actionId ->
            // ① 현재 프래그먼트 arguments 를 그대로 복사
            val nextArgs = Bundle(arguments)          // ← 핵심 한 줄
            findNavController().navigate(actionId, nextArgs)
        } ?: run {
            // ② 마지막 단계: PPG → 결과
            lifecycleScope.launch {
                safetyCheckViewModel.completeSession {
                    val action = PPGMeasurementFragmentDirections
                        .actionPpgToResult(
                            safetyCheckViewModel.currentSession.value?.sessionId
                        )
                    findNavController().navigate(action)
                }
            }
        }
    }


    /**
     * 측정 메타데이터 수집 - 하위 클래스에서 오버라이드 가능
     */
    protected open fun getMeasurementMetadata(): Map<String, String> {
        return mapOf(
            "device" to android.os.Build.MODEL,
            "os_version" to android.os.Build.VERSION.SDK_INT.toString()
        )
    }

    /**
     * 상태 업데이트
     */
    protected fun updateState(state: MeasurementState) {
        currentState = state
        onStateChanged(state)
    }

    /**
     * 상태 변경 시 UI 업데이트 - 하위 클래스에서 구현
     */
    protected abstract fun onStateChanged(state: MeasurementState)

    /**
     * 앱 설정 열기
     */
    private fun openAppSettings() {
        val intent = android.content.Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            android.net.Uri.fromParts("package", requireContext().packageName, null)
        )
        startActivity(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        measurementJob?.cancel()
    }
}