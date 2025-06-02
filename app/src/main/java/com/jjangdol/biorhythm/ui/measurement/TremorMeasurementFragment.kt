package com.jjangdol.biorhythm.ui.measurement

import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.navArgs
import com.jjangdol.biorhythm.R
import com.jjangdol.biorhythm.databinding.FragmentTremorMeasurementBinding
import com.jjangdol.biorhythm.model.MeasurementResult
import com.jjangdol.biorhythm.model.MeasurementState
import com.jjangdol.biorhythm.model.MeasurementType
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

@AndroidEntryPoint
class TremorMeasurementFragment : BaseMeasurementFragment(),
    SensorEventListener {

    private var _binding: FragmentTremorMeasurementBinding? = null
    private val binding get() = _binding!!

    private lateinit var sessionId: String

    override val measurementType = MeasurementType.TREMOR
    override val requiredPermissions = arrayOf<String>() // 센서는 권한 불필요
    override val nextNavigationAction = R.id.action_tremor_to_pupil

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null

    // 측정 데이터
    private val accelerationData = mutableListOf<Triple<Float, Float, Float>>()
    private var baselineAcceleration: Triple<Float, Float, Float>? = null
    private var isCalibrating = true
    private var measurementTimer: CountDownTimer? = null

    // 측정 설정
    private val CALIBRATION_TIME = 3000L // 3초 캘리브레이션
    private val MEASUREMENT_TIME = 10000L // 10초 측정
    private val SAMPLE_RATE = 50 // Hz

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTremorMeasurementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionId = arguments?.getString("sessionId") ?: ""

        setupSensor()
        setupUI()
    }

    private fun setupSensor() {
        sensorManager = requireContext().getSystemService(AppCompatActivity.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer == null) {
            binding.tvStatus.text = "가속도 센서를 사용할 수 없습니다"
            binding.btnSkip.visibility = View.VISIBLE
            binding.btnStart.isEnabled = false
        }
    }

    private fun setupUI() {
        binding.btnStart.setOnClickListener {
            startMeasurement()
        }

        binding.btnSkip.setOnClickListener {
            skipMeasurement()
        }

        binding.btnRetry.setOnClickListener {
            resetMeasurement()
            startMeasurement()
        }
    }

    override fun startMeasurement() {
        updateState(MeasurementState.Preparing)
        accelerationData.clear()
        isCalibrating = true

        // UI 상태 변경
        binding.initialButtons.visibility = View.GONE
        binding.measurementGuide.visibility = View.VISIBLE
        binding.tvTimer.visibility = View.VISIBLE

        // 센서 리스너 등록
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // 캘리브레이션 시작
        binding.tvStatus.text = "캘리브레이션 중..."
        binding.progressBar.visibility = View.VISIBLE
        binding.progressBar.isIndeterminate = true

        // 3초 후 실제 측정 시작
        measurementTimer = object : CountDownTimer(CALIBRATION_TIME, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000) + 1
                binding.tvTimer.text = "준비: ${seconds}초"
            }

            override fun onFinish() {
                startActualMeasurement()
            }
        }.start()
    }

    private fun startActualMeasurement() {
        isCalibrating = false
        calculateBaseline()
        updateState(MeasurementState.InProgress(0f))

        binding.tvStatus.text = "측정 중..."
        binding.progressBar.isIndeterminate = false
        binding.tvProgress.visibility = View.VISIBLE

        measurementTimer = object : CountDownTimer(MEASUREMENT_TIME, 100) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = ((MEASUREMENT_TIME - millisUntilFinished).toFloat() / MEASUREMENT_TIME) * 100
                updateState(MeasurementState.InProgress(progress))

                val seconds = (millisUntilFinished / 1000) + 1
                binding.tvTimer.text = "${seconds}초"
                binding.tvProgress.text = "진행률: ${progress.toInt()}%"
            }

            override fun onFinish() {
                Log.d("TremorTimer", "onFinish called")
                sensorManager.unregisterListener(this@TremorMeasurementFragment)
                analyzeTremor()
            }
        }.start()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_ACCELEROMETER) {
                val x = it.values[0]
                val y = it.values[1]
                val z = it.values[2]

                accelerationData.add(Triple(x, y, z))

                // 실시간 떨림 시각화 (옵션)
                if (!isCalibrating) {
                    updateRealtimeVisual(x, y, z)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // 필요시 정확도 변경 처리
    }

    private fun calculateBaseline() {
        if (accelerationData.isNotEmpty()) {
            val avgX = accelerationData.map { it.first }.average().toFloat()
            val avgY = accelerationData.map { it.second }.average().toFloat()
            val avgZ = accelerationData.map { it.third }.average().toFloat()
            baselineAcceleration = Triple(avgX, avgY, avgZ)

            // 측정 시작을 위해 데이터 초기화
            accelerationData.clear()
        }
    }

    private fun analyzeTremor() {
        val baseline = baselineAcceleration ?: return

        if (accelerationData.isEmpty()) {
            onMeasurementComplete(0f, "No data")
            return
        }

        // 떨림 분석
        val tremorMagnitudes = accelerationData.map { (x, y, z) ->
            val dx = x - baseline.first
            val dy = y - baseline.second
            val dz = z - baseline.third
            sqrt(dx.pow(2) + dy.pow(2) + dz.pow(2))
        }

        // 통계 계산
        val avgTremor = tremorMagnitudes.average()
        val maxTremor = tremorMagnitudes.maxOrNull() ?: 0f
        val stdDeviation = calculateStandardDeviation(tremorMagnitudes)

        // 주파수 분석 (간단한 버전 - 실제로는 FFT 사용)
        val peakFrequency = estimatePeakFrequency(accelerationData)

        // 점수 계산 (0-100)
        val score = calculateTremorScore(avgTremor, maxTremor, stdDeviation, peakFrequency)

        // 결과 JSON
        val rawData = """
            {
                "avgTremor": $avgTremor,
                "maxTremor": $maxTremor,
                "stdDeviation": $stdDeviation,
                "peakFrequency": $peakFrequency,
                "sampleCount": ${accelerationData.size}
            }
        """.trimIndent()

        updateState(MeasurementState.Completed(MeasurementResult(measurementType, score, rawData)))

        // UI 업데이트
        showResults(score)

        // 결과 저장 후 다음으로
        binding.btnNext.setOnClickListener {
            onMeasurementComplete(score, rawData)
        }
    }

    private fun showResults(score: Float) {
        // 프로그래스바와 측정 가이드 숨기기
        binding.progressBar.visibility = View.GONE
        binding.tvProgress.visibility = View.GONE
        binding.measurementGuide.visibility = View.GONE
        binding.tvTimer.visibility = View.GONE

        // 상태 업데이트
        binding.tvStatus.text = "측정 완료"

        // 결과 카드 표시
        binding.resultCard.visibility = View.VISIBLE

        // 결과 아이콘 설정
        binding.ivResultIcon.setImageResource(
            when {
                score >= 80 -> R.drawable.ic_check_circle
                score >= 60 -> R.drawable.ic_warning
                else -> R.drawable.ic_error
            }
        )

        // 결과 아이콘 색상 설정
        binding.ivResultIcon.setColorFilter(
            ContextCompat.getColor(requireContext(), when {
                score >= 80 -> R.color.safety_safe
                score >= 60 -> R.color.safety_caution
                else -> R.color.safety_danger
            })
        )

        // 결과 텍스트 설정
        binding.tvResult.text = "손떨림 점수: ${score.toInt()}점"
        binding.tvResultDetail.text = when {
            score >= 80 -> "매우 안정적입니다"
            score >= 60 -> "약간의 떨림이 감지됩니다"
            score >= 40 -> "주의가 필요한 수준입니다"
            else -> "휴식이 필요합니다"
        }

        // 버튼 그룹 변경
        binding.initialButtons.visibility = View.GONE
        binding.resultButtons.visibility = View.VISIBLE
    }

    private fun calculateStandardDeviation(values: List<Float>): Float {
        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance).toFloat()
    }

    private fun estimatePeakFrequency(data: List<Triple<Float, Float, Float>>): Float {
        // 간단한 zero-crossing 방법으로 주파수 추정
        // 실제로는 FFT를 사용하는 것이 더 정확함
        if (data.size < 2) return 0f

        val magnitudes = data.map { (x, y, z) ->
            val baseline = baselineAcceleration ?: Triple(0f, 0f, 0f)
            val dx = x - baseline.first
            val dy = y - baseline.second
            val dz = z - baseline.third
            sqrt(dx.pow(2) + dy.pow(2) + dz.pow(2))
        }

        var zeroCrossings = 0
        for (i in 1 until magnitudes.size) {
            if ((magnitudes[i-1] > 0 && magnitudes[i] <= 0) ||
                (magnitudes[i-1] < 0 && magnitudes[i] >= 0)) {
                zeroCrossings++
            }
        }

        val duration = MEASUREMENT_TIME / 1000f // 초 단위
        return (zeroCrossings / 2f) / duration // Hz
    }

    private fun calculateTremorScore(
        avgTremor: Double,
        maxTremor: Float,
        stdDev: Float,
        frequency: Float
    ): Float {
        // 정상 범위 기준값
        val normalAvgTremor = 0.5f
        val normalMaxTremor = 2.0f
        val normalStdDev = 0.3f
        val normalFrequency = 8f // 생리적 떨림은 보통 8-12Hz

        // 각 지표별 점수 계산 (떨림이 적을수록 높은 점수)
        val avgScore = when {
            avgTremor <= normalAvgTremor -> 100.0  // 정상 이하면 만점
            avgTremor <= normalAvgTremor * 2 -> 100 - ((avgTremor - normalAvgTremor) / normalAvgTremor * 50)  // 정상의 2배까지는 50점까지 감점
            else -> 50 - ((avgTremor - normalAvgTremor * 2) / normalAvgTremor * 25).coerceIn(0.0, 50.0)  // 그 이상은 추가 감점
        }

        val maxScore = when {
            maxTremor <= normalMaxTremor -> 100f
            maxTremor <= normalMaxTremor * 2 -> 100 - ((maxTremor - normalMaxTremor) / normalMaxTremor * 50)
            else -> 50 - ((maxTremor - normalMaxTremor * 2) / normalMaxTremor * 25).coerceIn(0f, 50f)
        }

        val stdScore = when {
            stdDev <= normalStdDev -> 100f
            stdDev <= normalStdDev * 2 -> 100 - ((stdDev - normalStdDev) / normalStdDev * 50)
            else -> 50 - ((stdDev - normalStdDev * 2) / normalStdDev * 25).coerceIn(0f, 50f)
        }

        // 주파수는 정상 범위(6-14Hz) 내에 있으면 만점
        val freqScore = when {
            frequency in 6f..14f -> 100f
            frequency in 4f..16f -> 80f
            frequency in 2f..18f -> 60f
            frequency == 0f -> 100f  // 떨림이 없으면 만점
            else -> 40f
        }

        // 가중 평균
        return (avgScore * 0.3 + maxScore * 0.3 + stdScore * 0.2 + freqScore * 0.2).toFloat()
    }

    private fun updateRealtimeVisual(x: Float, y: Float, z: Float) {
        // 실시간 시각화 (옵션)
        binding.tremoimeterView?.updateValue(x, y, z)
    }

    override fun onStateChanged(state: MeasurementState) {
        when (state) {
            is MeasurementState.Preparing -> {
                binding.btnStart.isEnabled = false
            }
            is MeasurementState.InProgress -> {
                binding.progressBar.progress = state.progress.toInt()
            }
            is MeasurementState.Completed -> {
                // showResults()에서 처리됨
            }
            is MeasurementState.Error -> {
                binding.tvStatus.text = state.message
                binding.btnStart.isEnabled = true
                binding.initialButtons.visibility = View.VISIBLE
                binding.resultButtons.visibility = View.GONE
                binding.progressBar.visibility = View.GONE
                binding.measurementGuide.visibility = View.GONE
            }
            else -> {}
        }
    }

    private fun resetMeasurement() {
        measurementTimer?.cancel()
        sensorManager.unregisterListener(this)
        accelerationData.clear()
        baselineAcceleration = null

        // UI 초기화
        binding.btnStart.isEnabled = true
        binding.initialButtons.visibility = View.VISIBLE
        binding.resultButtons.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.tvProgress.visibility = View.GONE
        binding.resultCard.visibility = View.GONE
        binding.measurementGuide.visibility = View.GONE
        binding.tvTimer.visibility = View.GONE
        binding.tvStatus.text = "측정 준비"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        measurementTimer?.cancel()
        sensorManager.unregisterListener(this)
        _binding = null
    }
}