package com.jjangdol.biorhythm.ui.measurement

import android.Manifest
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.*
import android.util.Log
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.navArgs
import android.view.*
import com.jjangdol.biorhythm.R
import com.jjangdol.biorhythm.databinding.FragmentPpgMeasurementBinding
import com.jjangdol.biorhythm.model.MeasurementState
import com.jjangdol.biorhythm.model.MeasurementType
import com.jjangdol.biorhythm.ui.view.PPGWaveformView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

@AndroidEntryPoint
class PPGMeasurementFragment : BaseMeasurementFragment() {

    /* ────────────── ViewBinding ────────────── */
    private var _binding: FragmentPpgMeasurementBinding? = null
    private val binding get() = _binding!!

    /* ───────────── Safe-Args ───────────── */
    private val args: PPGMeasurementFragmentArgs by navArgs()

    /* ───── BaseMeasurement 설정 ───── */
    override val measurementType = MeasurementType.PPG
    override val requiredPermissions = arrayOf(Manifest.permission.CAMERA)
    override val nextNavigationAction: Int? = null

    /* ────────── Camera2 변수 ────────── */
    private lateinit var cameraManager: CameraManager
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private lateinit var bgThread: HandlerThread
    private lateinit var bgHandler: Handler

    /* ──────────── 측정 데이터 ─────────── */
    private val rawPPGSignal = mutableListOf<Float>()
    private val timestamps = mutableListOf<Long>()
    private val lumaValues = mutableListOf<Float>()  // LUMA 컴포넌트 저장

    // Seeing Red 연구 기반 추가 데이터
    private val redChannel = mutableListOf<Float>()
    private val greenChannel = mutableListOf<Float>()
    private val blueChannel = mutableListOf<Float>()

    private var measurementTimer: CountDownTimer? = null
    private var signalQualityBuffer = mutableListOf<Float>()

    /* ───────────── Constants ───────────── */
    private val MEASUREMENT_TIME = 30_000L            // 30초 (논문과 동일)
    private val SAMPLING_RATE = 30                    // 30 FPS
    private val MIN_ACCEPTABLE_SIGNAL_LENGTH = 15_000L // 최소 15초 신호

    // 개선된 신호 품질 임계값 (정규화 기반)
    private val MIN_QUALITY_THRESHOLD = 40f            // 40%로 상향 조정
    private val MAX_MOTION_THRESHOLD = 25f             // 최대 허용 움직임

    /* ───────────── Lifecycle ──────────── */
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPpgMeasurementBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        cameraManager = requireContext()
            .getSystemService(android.content.Context.CAMERA_SERVICE) as CameraManager
        setupUI()
    }

    /* ─────────── UI & 버튼 ─────────── */
    private fun setupUI() = with(binding) {
        btnStart.setOnClickListener { startMeasurement() }
        btnSkip.setOnClickListener { skipMeasurement() }
        btnRetry.setOnClickListener {
            resetMeasurement()
            startMeasurement()
        }
        btnNext.setOnClickListener {
            // 결과 저장 로직
            onMeasurementComplete(0f, "") // 실제 데이터로 교체 필요
        }
    }

    /* ─────────── 측정 시작 ─────────── */
    override fun startMeasurement() {
        updateState(MeasurementState.Preparing)
        startBgThread()

        // 센서 캘리브레이션을 위한 초기 지연
        Handler(Looper.getMainLooper()).postDelayed({
            openCamera()
        }, 500)
    }

    /* ---------- Background Thread ---------- */
    private fun startBgThread() {
        bgThread = HandlerThread("CameraBg", Process.THREAD_PRIORITY_URGENT_DISPLAY).apply { start() }
        bgHandler = Handler(bgThread.looper)
    }

    private fun stopBgThread() {
        bgThread.quitSafely()
        try {
            bgThread.join()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
    }

    /* ─────────── Camera 열기 ─────────── */
    private fun openCamera() {
        try {
            val camId = findBestCamera() ?: throw Exception("적합한 카메라를 찾을 수 없습니다")

            val characteristics = cameraManager.getCameraCharacteristics(camId)
            if (!characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE)!!) {
                updateState(MeasurementState.Error("플래시가 없는 기기입니다"))
                return
            }

            // 카메라 성능 확인
            val hardware = characteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL)
            if (hardware == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
                showWarning("카메라 성능이 낮아 측정 정확도가 떨어질 수 있습니다")
            }

            cameraManager.openCamera(camId, stateCallback, bgHandler)

        } catch (se: SecurityException) {
            updateState(MeasurementState.Error("카메라 권한이 필요합니다"))
        } catch (e: Exception) {
            updateState(MeasurementState.Error("카메라 오류: ${e.message}"))
        }
    }

    private fun findBestCamera(): String? {
        return cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                    chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(cam: CameraDevice) {
            cameraDevice = cam
            createSession()
        }

        override fun onDisconnected(cam: CameraDevice) {
            cam.close()
            cameraDevice = null
        }

        override fun onError(cam: CameraDevice, err: Int) {
            cam.close()
            cameraDevice = null
            updateState(MeasurementState.Error("카메라 오류 코드: $err"))
        }
    }

    /* ────────── CaptureSession ────────── */
    private fun createSession() {
        try {
            // Seeing Red 기준 해상도 (YUV_420_888)
            imageReader = ImageReader.newInstance(
                640, 480, ImageFormat.YUV_420_888, 3
            ).apply {
                setOnImageAvailableListener(enhancedImageListener, bgHandler)
            }

            val yuvSurface = imageReader!!.surface

            // 캡처 요청 빌더 - 자동 제어 모드
            val reqBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            ).apply {
                addTarget(yuvSurface)

                // 자동 제어 (수동 제어 제거)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                set(CaptureRequest.CONTROL_AWB_MODE, CaptureRequest.CONTROL_AWB_MODE_AUTO)

                // 프레임 레이트를 30 FPS로 설정
                set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(SAMPLING_RATE, SAMPLING_RATE))
            }

            cameraDevice!!.createCaptureSession(
                listOf(yuvSurface),
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        captureSession = session

                        // 플래시 켜기 전 대기 시간 단축
                        Handler(Looper.getMainLooper()).postDelayed({
                            startActualMeasurement()

                            // 플래시 켜고 캡처 시작
                            val torchReq = reqBuilder.apply {
                                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                            }.build()

                            session.setRepeatingRequest(torchReq, captureCallback, bgHandler)
                        }, 500) // 1000ms → 500ms로 단축
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        updateState(MeasurementState.Error("카메라 설정 실패"))
                    }
                },
                bgHandler
            )

        } catch (e: Exception) {
            updateState(MeasurementState.Error("카메라 설정 오류: ${e.message}"))
        }
    }

    // 캡처 콜백 (프레임 타이밍 모니터링)
    private val captureCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            // 프레임 타이밍 정보 수집은 필요하다면 여기에 추가
        }
    }

    /* ---------- Enhanced Image Processing ---------- */
    private val enhancedImageListener = ImageReader.OnImageAvailableListener { reader ->
        reader.acquireLatestImage()?.let { image ->
            try {
                processImage(image)
            } finally {
                image.close()
            }
        }
    }

    private fun processImage(image: android.media.Image) {
        val currentTime = System.currentTimeMillis()

        // Y 평면에서 LUMA 추출 (Seeing Red 방식)
        val yPlane = image.planes[0]
        val yBuffer = yPlane.buffer
        val ySize = yBuffer.remaining()
        val yData = ByteArray(ySize)
        yBuffer.get(yData)

        // 중앙 영역만 사용 (손가락이 닿는 부분)
        val width = image.width
        val height = image.height
        val centerX = width / 2
        val centerY = height / 2
        val roiSize = min(width, height) / 3  // 중앙 1/3 영역

        var lumaSum = 0.0
        var pixelCount = 0

        // ROI 내 픽셀 평균
        for (y in (centerY - roiSize / 2)..(centerY + roiSize / 2)) {
            for (x in (centerX - roiSize / 2)..(centerX + roiSize / 2)) {
                val idx = y * width + x
                if (idx < ySize) {
                    lumaSum += (yData[idx].toInt() and 0xFF)
                    pixelCount++
                }
            }
        }

        val lumaMean = (lumaSum / pixelCount).toFloat()

        // 신호 저장 - 동기화 보장
        synchronized(this) {
            rawPPGSignal.add(lumaMean)
            timestamps.add(currentTime)
            lumaValues.add(lumaMean)

            // 크기 확인
            require(rawPPGSignal.size == timestamps.size) {
                "Signal and timestamp arrays size mismatch"
            }

            // 🔥 개선된 실시간 품질 평가 (정규화 기반)
            if (rawPPGSignal.size >= SAMPLING_RATE) {
                val recentSignal = rawPPGSignal.takeLast(SAMPLING_RATE)
                val quality = evaluateNormalizedSignalQuality(recentSignal)
                signalQualityBuffer.add(quality)

                requireActivity().runOnUiThread {
                    updateQualityIndicator(quality)
                    updatePPGVisualization(lumaMean, quality)
                }
            }
        }
    }

    /* ---------- 개선된 신호 품질 평가 (정규화 기반) ---------- */
    private fun evaluateNormalizedSignalQuality(signal: List<Float>): Float {
        if (signal.size < 10) return 0f

        // 🔥 핵심: 정규화된 신호로 품질 평가
        val normalizedSignal = normalizeSignal(signal)

        // 1. 패턴 품질 평가 (30%)
        val patternScore = evaluatePattern(normalizedSignal)

        // 2. 심박 주파수 일관성 (30%)
        val frequencyScore = evaluateFrequencyContent(normalizedSignal)

        // 3. 신호 안정성 (20%)
        val stabilityScore = calculateNormalizedStability(normalizedSignal)

        // 4. 피크 검출 성공률 (20%)
        val peakScore = evaluatePeakDetection(normalizedSignal)

        val totalScore = (patternScore * 0.3f + frequencyScore * 0.3f +
                stabilityScore * 0.2f + peakScore * 0.2f) * 100

        Log.d("PPG_Quality", "Pattern: $patternScore, Freq: $frequencyScore, " +
                "Stability: $stabilityScore, Peak: $peakScore, Total: $totalScore")

        return totalScore
    }

    private fun evaluatePattern(normalizedSignal: List<Float>): Float {
        // 정규화된 신호에서 심박 패턴의 규칙성 평가
        val peaks = detectRobustPeaks(normalizedSignal)

        if (peaks.size < 3) return 0.2f

        // 피크 간격의 일관성 (CV: Coefficient of Variation)
        val intervals = peaks.zipWithNext { a, b -> (b - a).toFloat() }
        val mean = intervals.average().toFloat()
        val std = intervals.standardDeviation()

        val cv = if (mean > 0) std / mean else Float.MAX_VALUE

        return when {
            cv < 0.15f -> 1.0f    // 매우 규칙적
            cv < 0.25f -> 0.8f    // 규칙적
            cv < 0.40f -> 0.6f    // 보통
            else -> 0.3f          // 불규칙
        }
    }

    private fun evaluatePeakDetection(normalizedSignal: List<Float>): Float {
        val peaks = detectRobustPeaks(normalizedSignal)
        val expectedPeaks = (normalizedSignal.size / SAMPLING_RATE.toFloat()) * 1.2f // 72 BPM 기준

        val detectionRatio = peaks.size / expectedPeaks

        return when {
            detectionRatio in 0.7f..1.3f -> 1.0f    // 적절한 피크 수
            detectionRatio in 0.5f..1.5f -> 0.7f    // 약간 부족/과다
            else -> 0.3f                             // 너무 적거나 많음
        }
    }

    private fun calculateNormalizedStability(normalizedSignal: List<Float>): Float {
        if (normalizedSignal.size < 5) return 0f

        // 정규화된 신호에서 연속 변화량의 일관성
        val diffs = normalizedSignal.zipWithNext { a, b -> abs(b - a) }
        val avgDiff = diffs.average().toFloat()
        val diffStd = diffs.standardDeviation()

        // 변화량의 일관성 (CV)
        val diffCV = if (avgDiff > 0) diffStd / avgDiff else Float.MAX_VALUE

        return when {
            diffCV < 0.5f -> 1.0f    // 매우 안정적
            diffCV < 1.0f -> 0.8f    // 안정적
            diffCV < 2.0f -> 0.6f    // 보통
            else -> 0.3f             // 불안정
        }
    }

    // 기존 SNR 계산도 정규화 신호 기반으로 변경
    private fun calculateSNR(signal: List<Float>): Float {
        val normalizedSignal = normalizeSignal(signal)
        val peaks = findLocalMaxima(normalizedSignal)
        if (peaks.isEmpty()) return 0f

        val signalPower = peaks.map { normalizedSignal[it] }.average().toFloat().pow(2)
        val noisePower = normalizedSignal.variance()

        return if (noisePower > 0) {
            (10 * log10(signalPower.toDouble() / noisePower.toDouble())).toFloat()
        } else 0f
    }

    private fun evaluateFrequencyContent(signal: List<Float>): Float {
        // 심박 주파수 범위 (0.5-3Hz) 내 신호 강도 평가
        val mean = signal.average().toFloat()
        val crossings = signal.zipWithNext().count { (a, b) ->
            (a <= mean && b > mean) || (a >= mean && b < mean)
        }

        val freq = crossings.toFloat() / (signal.size / SAMPLING_RATE.toFloat()) / 2
        return when {
            freq in 0.5f..3.0f -> 1.0f
            freq in 0.3f..4.0f -> 0.7f
            else -> 0.3f
        }
    }

    /* ---------- 실시간 시각화 & 품질 표시 ---------- */
    private fun updatePPGVisualization(value: Float, quality: Float) {
        binding.ppgWaveformView?.addDataPoint(value)
        binding.ppgWaveformView?.setSignalQuality(
            when {
                quality >= 65 -> PPGWaveformView.SignalQuality.EXCELLENT
                quality >= 50 -> PPGWaveformView.SignalQuality.GOOD
                quality >= 35 -> PPGWaveformView.SignalQuality.POOR
                else -> PPGWaveformView.SignalQuality.NONE
            },
            quality / 100f
        )

        val qualityText = when {
            quality >= 80 -> "매우 좋음"
            quality >= 65 -> "좋음"
            quality >= 50 -> "보통"
            quality >= 35 -> "주의"
            else -> "낮음"
        }

        binding.tvSignalQuality.apply {
            text = qualityText
            setTextColor(when {
                quality >= 80 -> requireContext().getColor(android.R.color.holo_green_dark)
                quality >= 65 -> requireContext().getColor(android.R.color.holo_blue_dark)
                quality >= 50 -> requireContext().getColor(android.R.color.holo_orange_light)
                quality >= 35 -> requireContext().getColor(android.R.color.holo_orange_dark)
                else -> requireContext().getColor(android.R.color.holo_red_dark)
            })
        }
    }

    private fun updateQualityIndicator(quality: Float) {
        // UI의 progress indicator는 별도로 처리하지 않음 (레이아웃에 없음)
    }

    /* ─────────── 실측 & 타이머 ─────────── */
    private fun startActualMeasurement() {
        requireActivity().runOnUiThread {
            updateState(MeasurementState.InProgress(0f))
            clearMeasurementData()

            binding.apply {
                tvInstruction.text = "카메라에 손가락을 올려주세요"
                fingerGuideImage.visibility = View.VISIBLE
                progressBar.visibility = View.VISIBLE
                tvProgress.visibility = View.VISIBLE
            }
        }

        // 초기 안정화 시간 단축
        Handler(bgHandler.looper).postDelayed({
            measurementTimer = object : CountDownTimer(MEASUREMENT_TIME, 200) {
                override fun onTick(msLeft: Long) {
                    val progress = ((MEASUREMENT_TIME - msLeft) / MEASUREMENT_TIME.toFloat()) * 100

                    requireActivity().runOnUiThread {
                        updateState(MeasurementState.InProgress(progress))
                        binding.progressBar.progress = progress.toInt()
                        binding.tvProgress.text = "측정 중... ${progress.toInt()}%"

                        // 남은 시간 표시
                        val remainingSeconds = (msLeft / 1000 + 1).toInt()
                        binding.tvTimer.text = "${remainingSeconds}초"

                        // 실시간 심박수 추정 (5초 이후로 단축)
                        if (rawPPGSignal.size > SAMPLING_RATE * 5) {
                            val instantHR = estimateInstantHeartRate()
                            binding.tvRealtimeBPM.text = instantHR.toInt().toString()
                        } else {
                            binding.tvRealtimeBPM.text = "--"
                        }
                    }
                }

                override fun onFinish() {
                    Log.d("PPG", "측정 완료. 총 신호 길이: ${rawPPGSignal.size}")
                    closeCamera()
                    analyzeCompleteMeasurement()
                }
            }.start()
        }, 2000) // 3000ms → 2000ms로 더 단축
    }

    private fun estimateInstantHeartRate(): Float {
        val recentSignal = rawPPGSignal.takeLast(SAMPLING_RATE * 10) // 최근 10초
        val filtered = preprocessSignal(recentSignal, true) // 간단한 전처리
        val peaks = detectRobustPeaks(filtered)

        return if (peaks.size >= 2) {
            val intervals = peaks.zipWithNext { a, b -> b - a }
            60f * SAMPLING_RATE / intervals.average().toFloat()
        } else 60f
    }

    /* ────────── Enhanced Signal Processing ────────── */
    private fun analyzeCompleteMeasurement() {
        lifecycleScope.launch {
            updateState(MeasurementState.InProgress(100f))
            binding.tvInstruction.text = "신호 분석 중..."

            val result = withContext(Dispatchers.Default) {
                processCompleteSignal()
            }

            if (result.isValid) {
                displayResults(result)
            } else {
                updateState(
                    MeasurementState.Error(
                        "측정 실패: ${result.errorMessage ?: "신호 품질이 부족합니다"}"
                    )
                )
            }
        }
    }

    private data class PPGAnalysisResult(
        val heartRate: Float,
        val hrv: Float,
        val signalQuality: Float,
        val score: Float,
        val isValid: Boolean,
        val errorMessage: String? = null,
        val additionalMetrics: Map<String, Float> = emptyMap()
    )

    private fun processCompleteSignal(): PPGAnalysisResult {
        Log.d("PPG", "===== 신호 분석 시작 =====")
        Log.d("PPG", "원본 신호 길이: ${rawPPGSignal.size}")

        // 🔥 개선된 신호 품질 검증 (정규화 기반)
        val avgQuality = signalQualityBuffer.average().toFloat()
        Log.d("PPG", "평균 신호 품질: ${avgQuality}%")

        if (avgQuality < MIN_QUALITY_THRESHOLD) {
            return PPGAnalysisResult(
                0f, 0f, avgQuality, 0f, false,
                "신호 품질이 너무 낮습니다 (${avgQuality.toInt()}% < $MIN_QUALITY_THRESHOLD%)"
            )
        }

        // 2. 신호 전처리
        val processed = preprocessSignal(rawPPGSignal)
        Log.d("PPG", "전처리 후 신호 길이: ${processed.size}")

        // 3. 최소 신호 길이 조건 완화
        val minSamples = (SAMPLING_RATE * 10).toInt() // 15초 → 10초로 더 완화
        if (processed.size < minSamples) {
            return PPGAnalysisResult(
                0f, 0f, avgQuality, 0f, false,
                "유효한 신호가 부족합니다 (${processed.size} < $minSamples 샘플)"
            )
        }

        // 4. 심박 검출 조건 완화
        val beats = detectHeartbeats(processed)
        Log.d("PPG", "최종 검출된 심박 수: ${beats.size}")

        if (beats.size < 5) { // 8 → 5로 더 완화
            // 더 공격적인 피크 검출 시도
            Log.d("PPG", "첫 번째 시도 실패, 더 공격적인 검출 시도")
            val aggressiveBeats = detectHeartbeatsAggressive(processed)
            Log.d("PPG", "공격적 검출 결과: ${aggressiveBeats.size}")

            if (aggressiveBeats.size < 5) {
                return PPGAnalysisResult(
                    0f, 0f, avgQuality, 0f, false,
                    "심박 검출 실패 (${aggressiveBeats.size} < 5 beats)"
                )
            }

            // 공격적 검출 결과 사용
            return processWithBeats(aggressiveBeats, avgQuality, processed)
        }

        // 5. 정상적인 처리
        return processWithBeats(beats, avgQuality, processed)
    }

    private fun processWithBeats(
        beats: List<Int>,
        avgQuality: Float,
        processed: List<Float>
    ): PPGAnalysisResult {
        // 메트릭 계산
        val hr = calculateHeartRate(beats)
        val hrv = calculateHRV(beats)
        val additionalMetrics = extractAdditionalFeatures(processed, beats)

        Log.d("PPG", "계산된 심박수: $hr BPM")
        Log.d("PPG", "계산된 HRV: $hrv ms")

        // 🔥 심박수 유효성 검증 강화
        if (hr < 40f || hr > 150f) {
            return PPGAnalysisResult(
                0f, 0f, avgQuality, 0f, false,
                "비정상적인 심박수: ${hr.toInt()} BPM (정상 범위: 40-150)"
            )
        }

        // 점수 계산
        val score = calculateBiometricScore(hr, hrv, avgQuality, additionalMetrics)

        return PPGAnalysisResult(
            heartRate = hr,
            hrv = hrv,
            signalQuality = avgQuality,
            score = score,
            isValid = true,
            additionalMetrics = additionalMetrics
        )
    }

    /* ─────────── Seeing Red 기반 전처리 파이프라인 ─────────── */
    private fun preprocessSignal(signal: List<Float>, simple: Boolean = false): List<Float> {
        if (signal.size < SAMPLING_RATE) return signal

        Log.d("PPG", "Seeing Red 전처리 시작. 원본 신호 길이: ${signal.size}")

        // 🔥 Seeing Red 방식: 1초 Rolling Average 디트렌딩
        val detrended = seeingRedDetrend(signal)
        Log.d("PPG", "Seeing Red 디트렌딩 완료. 길이: ${detrended.size}")

        if (simple) {
            // 간단한 전처리 (실시간용): 디트렌딩 + 가벼운 스무딩
            return lightSmoothing(detrended)
        }

        // 🔥 Seeing Red 방식: 4Hz 로우패스 필터 (240 BPM)
        val filtered = seeingRedLowPass(detrended)
        Log.d("PPG", "Seeing Red 4Hz 필터링 완료. 길이: ${filtered.size}")

        // 3. 정규화 (심박수 측정을 위해 유지)
        val normalized = normalizeSignal(filtered)
        Log.d("PPG", "정규화 완료. 길이: ${normalized.size}")

        return normalized
    }

    private fun seeingRedDetrend(signal: List<Float>): List<Float> {
        if (signal.size < SAMPLING_RATE) return signal

        // 🔥 Seeing Red 방식: 정확히 1초 윈도우 Rolling Average
        val windowSize = SAMPLING_RATE // 1초 = 30 프레임
        val result = mutableListOf<Float>()

        for (i in signal.indices) {
            val start = maxOf(0, i - windowSize / 2)
            val end = minOf(signal.size, i + windowSize / 2)
            val rollingAvg = signal.subList(start, end).average().toFloat()
            result.add(signal[i] - rollingAvg)
        }

        return result
    }

    private fun seeingRedLowPass(signal: List<Float>): List<Float> {
        // 🔥 Seeing Red 방식: 4Hz 컷오프 로우패스 필터 (240 BPM)
        // 30fps에서 4Hz = 7.5 샘플 주기 → 8 샘플 윈도우 사용
        val cutoffWindow = 8
        if (signal.size < cutoffWindow) return signal

        return signal.windowed(cutoffWindow, 1) { window ->
            window.average().toFloat()
        }
    }

    private fun lightSmoothing(signal: List<Float>): List<Float> {
        // 실시간용 가벼운 스무딩 (3점 이동평균)
        if (signal.size < 3) return signal

        return signal.windowed(3, 1) { window ->
            window.average().toFloat()
        }
    }

    private fun normalizeSignal(signal: List<Float>): List<Float> {
        val mean = signal.average().toFloat()
        val variance = signal.map { (it - mean).pow(2) }.average().toFloat()
        val std = sqrt(variance)
        return if (std > 0) {
            signal.map { (it - mean) / std }
        } else {
            signal
        }
    }

    private fun detectHeartbeats(signal: List<Float>): List<Int> {
        Log.d("PPG", "심박 검출 시작. 신호 길이: ${signal.size}")

        // Robust peak detection
        val peaks = detectRobustPeaks(signal)
        Log.d("PPG", "초기 피크 검출: ${peaks.size}개")

        // Post-processing: 생리학적 제약 적용
        val filtered = filterPhysiologicallyValidPeaks(peaks)
        Log.d("PPG", "필터링 후 피크: ${filtered.size}개")

        return filtered
    }

    // 더 공격적인 심박 검출 (백업용)
    private fun detectHeartbeatsAggressive(signal: List<Float>): List<Int> {
        Log.d("PPG", "공격적 심박 검출 시작")

        // 더 낮은 임계값으로 피크 검출
        val peaks = detectAggressivePeaks(signal)
        Log.d("PPG", "공격적 피크 검출: ${peaks.size}개")

        // 더 관대한 필터링
        val filtered = filterPhysiologicallyValidPeaksRelaxed(peaks)
        Log.d("PPG", "관대한 필터링 후: ${filtered.size}개")

        return filtered
    }

    /* ─────────── 피크 검출 알고리즘 개선 ─────────── */
    private fun detectRobustPeaks(signal: List<Float>): List<Int> {
        if (signal.size < 10) return emptyList()

        // 1. 더 적응적인 임계값 설정
        val sorted = signal.sorted()
        val percentile75 = sorted[(sorted.size * 0.75).toInt()]
        val percentile25 = sorted[(sorted.size * 0.25).toInt()]
        val iqr = percentile75 - percentile25

        // 임계값을 IQR 기반으로 설정 (더 안정적)
        val threshold = percentile75 - 0.5f * iqr

        // 2. 최소 거리를 더 짧게 (0.3초)
        val minDistance = (SAMPLING_RATE * 0.3).toInt()

        val peaks = mutableListOf<Int>()
        var lastPeak = -minDistance

        // 3. 더 간단한 피크 검출 조건
        for (i in 1 until signal.size - 1) {
            if (signal[i] > threshold &&
                signal[i] > signal[i - 1] &&
                signal[i] > signal[i + 1] &&
                i - lastPeak >= minDistance) {

                peaks.add(i)
                lastPeak = i
            }
        }

        Log.d("PPG", "Detected ${peaks.size} peaks with threshold: $threshold")
        return peaks
    }

    // 🔥 심박수 측정 최적화된 공격적 검출 (백업용)
    private fun detectAggressivePeaks(signal: List<Float>): List<Int> {
        if (signal.size < 10) return emptyList()

        // 더 관대한 Valley-Peak 방식
        val smoothed = signal.windowed(3, 1) { it.average().toFloat() }
        val valleys = findValleysRelaxed(smoothed)

        val peaks = mutableListOf<Int>()

        for (i in 0 until valleys.size - 1) {
            val segmentStart = valleys[i]
            val segmentEnd = minOf(valleys[i + 1], signal.size - 1)

            if (segmentEnd > segmentStart + 3) {
                val segment = signal.subList(segmentStart, segmentEnd)
                val maxIdx = segment.indexOf(segment.maxOrNull() ?: 0f)
                val absoluteIdx = segmentStart + maxIdx

                // 더 관대한 유효성 검사
                if (isValidPeakRelaxed(signal, absoluteIdx, peaks.lastOrNull())) {
                    peaks.add(absoluteIdx)
                }
            }
        }

        Log.d("PPG", "Aggressive hybrid: ${valleys.size} valleys → ${peaks.size} peaks")
        return peaks
    }

    private fun findValleysRelaxed(smoothed: List<Float>): List<Int> {
        val valleys = mutableListOf<Int>()

        // 더 민감한 valley 검출
        for (i in 1 until smoothed.size - 1) {
            if (smoothed[i] <= smoothed[i - 1] &&
                smoothed[i] <= smoothed[i + 1]) {  // <= 사용 (더 관대)
                valleys.add(i)
            }
        }

        // 더 짧은 최소 거리 (0.25초)
        val minDistance = (SAMPLING_RATE * 0.25).toInt()
        if (valleys.size < 2) return valleys

        val filtered = mutableListOf(valleys[0])
        for (i in 1 until valleys.size) {
            if (valleys[i] - filtered.last() >= minDistance) {
                filtered.add(valleys[i])
            }
        }
        return filtered
    }

    private fun isValidPeakRelaxed(signal: List<Float>, peakIdx: Int, lastPeakIdx: Int?): Boolean {
        if (peakIdx <= 0 || peakIdx >= signal.size - 1) return false

        // 더 관대한 로컬 최대값 확인
        if (signal[peakIdx] < signal[peakIdx - 1] ||
            signal[peakIdx] < signal[peakIdx + 1]) return false

        // 더 넓은 간격 허용
        if (lastPeakIdx != null) {
            val interval = peakIdx - lastPeakIdx
            val minInterval = (SAMPLING_RATE * 0.25).toInt() // 240 BPM 최대
            val maxInterval = (SAMPLING_RATE * 1.5).toInt()  // 40 BPM 최소

            if (interval < minInterval || interval > maxInterval) return false
        }

        // 더 낮은 신호 강도도 허용
        if (signal[peakIdx] < -1.0f) return false

        return true
    }

    private fun filterPhysiologicallyValidPeaks(peaks: List<Int>): List<Int> {
        if (peaks.size < 3) return peaks

        val intervals = peaks.zipWithNext { a, b -> b - a }
        val medianInterval = intervals.sorted()[intervals.size / 2]

        // 범위를 50%로 확대
        val filtered = mutableListOf(peaks[0])
        for (i in 1 until peaks.size) {
            val interval = peaks[i] - filtered.last()
            if (interval in (medianInterval * 0.5).toInt()..(medianInterval * 1.5).toInt()) {
                filtered.add(peaks[i])
            }
        }

        Log.d("PPG", "Filtered peaks: ${peaks.size} → ${filtered.size}")
        return filtered
    }

    // 더 관대한 필터링 (백업용)
    private fun filterPhysiologicallyValidPeaksRelaxed(peaks: List<Int>): List<Int> {
        if (peaks.size < 2) return peaks

        // 중앙값 대신 평균 사용
        val intervals = peaks.zipWithNext { a, b -> b - a }
        val avgInterval = intervals.average().toFloat()

        // 범위를 70%로 더 확대
        val filtered = mutableListOf(peaks[0])
        for (i in 1 until peaks.size) {
            val interval = peaks[i] - filtered.last()
            if (interval in (avgInterval * 0.3).toInt()..(avgInterval * 1.7).toInt()) {
                filtered.add(peaks[i])
            }
        }

        Log.d("PPG", "Relaxed filter: ${peaks.size} → ${filtered.size}")
        return filtered
    }

    private fun calculateHeartRate(beats: List<Int>): Float {
        if (beats.size < 2) return 0f

        // 샘플 인덱스 기반 계산 (타임스탬프 사용하지 않음)
        val intervals = beats.zipWithNext { a, b -> (b - a).toFloat() }
        val avgIntervalInSamples = intervals.average()

        // BPM = (샘플링레이트 * 60) / 평균 샘플 간격
        val rawBpm = ((SAMPLING_RATE * 60f) / avgIntervalInSamples).toFloat()

        // 🔥 심박수 측정 최적화: 안전 범위로 제한
        val clampedBpm = rawBpm.coerceIn(40f, 150f)

        Log.d("PPG", "Heart rate: raw=$rawBpm, clamped=$clampedBpm, intervals=${intervals.size}")
        return clampedBpm
    }

    private fun calculateHRV(beats: List<Int>): Float {
        if (beats.size < 3) return 0f

        // 샘플 기반 HRV 계산
        val intervals = beats.zipWithNext { a, b ->
            (b - a).toFloat() * (1000f / SAMPLING_RATE) // 샘플을 ms로 변환
        }

        val successiveDiffs = intervals.zipWithNext { a, b -> (b - a).pow(2) }
        val rmssd = sqrt(successiveDiffs.average().toFloat())

        Log.d("PPG", "HRV calculation: RMSSD = $rmssd ms")
        return rmssd
    }

    private fun extractAdditionalFeatures(signal: List<Float>, beats: List<Int>): Map<String, Float> {
        val features = mutableMapOf<String, Float>()

        // 1. Pulse Transit Time (PTT) 관련 특징
        if (beats.size >= 2) {
            val pttVariability = calculatePTTVariability(signal, beats)
            features["ptt_variability"] = pttVariability
        }

        // 2. Pulse Wave Morphology
        val morphologyFeatures = extractMorphologyFeatures(signal, beats)
        features.putAll(morphologyFeatures)

        // 3. Frequency Domain Features
        val freqFeatures = extractFrequencyFeatures(signal)
        features.putAll(freqFeatures)

        // 4. Signal Quality Metrics
        features["signal_snr"] = calculateSNR(signal)
        features["signal_stability"] = calculateNormalizedStability(signal)

        return features
    }

    private fun calculatePTTVariability(signal: List<Float>, beats: List<Int>): Float {
        if (beats.size < 3) return 0f

        val pttValues = mutableListOf<Float>()

        for (i in 0 until beats.size - 1) {
            val beatStart = beats[i]
            val beatEnd = minOf(beats[i + 1], signal.size - 1)

            if (beatEnd > beatStart) {
                val segment = signal.subList(beatStart, beatEnd)
                if (segment.isNotEmpty()) {
                    val maxIdx = segment.indexOf(segment.maxOrNull() ?: 0f)
                    val minIdx = segment.indexOf(segment.minOrNull() ?: 0f)

                    if (maxIdx >= 0 && minIdx > maxIdx) {
                        pttValues.add((minIdx - maxIdx).toFloat() / SAMPLING_RATE)
                    }
                }
            }
        }

        return if (pttValues.isNotEmpty()) {
            pttValues.standardDeviation()
        } else 0f
    }

    private fun extractMorphologyFeatures(signal: List<Float>, beats: List<Int>): Map<String, Float> {
        val features = mutableMapOf<String, Float>()

        if (beats.size < 2) return features

        // 평균 펄스 모양 추출
        val pulseShapes = mutableListOf<List<Float>>()

        for (i in 0 until beats.size - 1) {
            val start = beats[i]
            val end = minOf(beats[i + 1], signal.size)

            if (end - start > 10 && end <= signal.size) {
                val pulse = signal.subList(start, end)
                pulseShapes.add(pulse)
            }
        }

        if (pulseShapes.isNotEmpty()) {
            // 펄스 너비 (FWHM - Full Width at Half Maximum)
            val avgWidth = pulseShapes.mapNotNull { calculateFWHM(it) }
                .takeIf { it.isNotEmpty() }
                ?.average()?.toFloat() ?: 0f
            features["pulse_width"] = avgWidth

            // 펄스 진폭 변동성
            val amplitudes = pulseShapes.map {
                (it.maxOrNull() ?: 0f) - (it.minOrNull() ?: 0f)
            }
            features["amplitude_variability"] = amplitudes.standardDeviation()

            // Dicrotic notch 특징 (이중맥파 노치)
            val notchDepths = pulseShapes.mapNotNull { findDicroticNotchDepth(it) }
            if (notchDepths.isNotEmpty()) {
                features["dicrotic_notch_depth"] = notchDepths.average().toFloat()
            }
        }

        return features
    }

    private fun calculateFWHM(pulse: List<Float>): Float? {
        if (pulse.isEmpty()) return null

        val max = pulse.maxOrNull() ?: return null
        val min = pulse.minOrNull() ?: return null
        val halfMax = (max + min) / 2

        var firstHalf = -1
        var lastHalf = -1

        for (i in pulse.indices) {
            if (pulse[i] >= halfMax) {
                if (firstHalf == -1) firstHalf = i
                lastHalf = i
            }
        }

        return if (firstHalf >= 0 && lastHalf > firstHalf) {
            (lastHalf - firstHalf).toFloat() / SAMPLING_RATE
        } else null
    }

    private fun findDicroticNotchDepth(pulse: List<Float>): Float? {
        if (pulse.size < 10) return null

        val maxIdx = pulse.indexOf(pulse.maxOrNull() ?: return null)
        if (maxIdx < pulse.size * 0.3 || maxIdx > pulse.size * 0.7) return null

        // 최대값 이후의 local minimum 찾기
        val afterMax = pulse.subList(maxIdx, pulse.size)
        val localMinima = findLocalMinima(afterMax)

        return if (localMinima.isNotEmpty()) {
            val notchIdx = localMinima.first()
            val notchDepth = pulse[maxIdx] - afterMax[notchIdx]
            val amplitude = (pulse.maxOrNull() ?: 0f) - (pulse.minOrNull() ?: 0f)
            if (amplitude > 0) notchDepth / amplitude else null
        } else null
    }

    private fun findLocalMinima(signal: List<Float>): List<Int> {
        val minima = mutableListOf<Int>()

        for (i in 1 until signal.size - 1) {
            if (signal[i] < signal[i - 1] && signal[i] < signal[i + 1]) {
                minima.add(i)
            }
        }

        return minima
    }

    private fun extractFrequencyFeatures(signal: List<Float>): Map<String, Float> {
        val features = mutableMapOf<String, Float>()

        // 간단한 주파수 분석
        val powerSpectrum = calculatePowerSpectrum(signal)

        // LF/HF ratio (저주파/고주파 비율)
        val lfPower = powerSpectrum.filter { it.first in 0.04f..0.15f }
            .sumOf { it.second.toDouble() }
        val hfPower = powerSpectrum.filter { it.first in 0.15f..0.4f }
            .sumOf { it.second.toDouble() }

        if (hfPower > 0) {
            features["lf_hf_ratio"] = (lfPower / hfPower).toFloat()
        }

        // Dominant frequency
        val dominantFreq = powerSpectrum.maxByOrNull { it.second }?.first ?: 0f
        features["dominant_frequency"] = dominantFreq

        return features
    }

    private fun calculatePowerSpectrum(signal: List<Float>): List<Pair<Float, Float>> {
        val spectrum = mutableListOf<Pair<Float, Float>>()

        // 0.5Hz ~ 4Hz 범위에서 분석
        for (freq in 5..40) {
            val f = freq / 10f // 0.5 ~ 4.0 Hz
            val power = calculateFrequencyPower(signal, f)
            spectrum.add(f to power)
        }

        return spectrum
    }

    private fun calculateFrequencyPower(signal: List<Float>, freq: Float): Float {
        // Goertzel 알고리즘 근사
        val omega = 2 * PI * freq / SAMPLING_RATE
        val coeff = (2 * cos(omega)).toFloat()

        var s1 = 0.0f
        var s2 = 0.0f

        signal.forEach { sample ->
            val s0 = sample + coeff * s1 - s2
            s2 = s1
            s1 = s0
        }

        return s1 * s1 + s2 * s2 - s1 * s2 * coeff
    }

    private fun calculateBiometricScore(
        hr: Float,
        hrv: Float,
        quality: Float,
        features: Map<String, Float>
    ): Float {
        var score = 0f

        // 1. 심박수 점수 (40%)
        val hrScore = when {
            hr in 60f..80f -> 100f
            hr in 50f..90f -> 80f
            hr in 40f..100f -> 60f
            hr in 30f..120f -> 40f
            else -> 20f
        }
        score += hrScore * 0.4f

        // 2. HRV 점수 (30%)
        val hrvScore = when {
            hrv > 50 -> 100f
            hrv > 35 -> 80f
            hrv > 20 -> 60f
            hrv > 10 -> 40f
            else -> 20f
        }
        score += hrvScore * 0.3f

        // 3. 신호 품질 점수 (20%)
        score += quality * 0.2f

        // 4. 추가 특징 점수 (10%)
        val featureScore = calculateFeatureScore(features)
        score += featureScore * 0.1f

        return score.coerceIn(0f, 100f)
    }

    private fun calculateFeatureScore(features: Map<String, Float>): Float {
        var score = 50f // 기본 점수

        // LF/HF ratio (균형잡힌 자율신경계)
        features["lf_hf_ratio"]?.let { ratio ->
            score += when {
                ratio in 0.5f..2.0f -> 25f
                ratio in 0.3f..3.0f -> 15f
                else -> 0f
            }
        }

        // Signal stability
        features["signal_stability"]?.let { stability ->
            score += stability * 25f
        }

        return score.coerceIn(0f, 100f)
    }

    private fun displayResults(result: PPGAnalysisResult) {
        val rawData = buildRawDataJson(result)

        updateState(
            MeasurementState.Completed(
                com.jjangdol.biorhythm.model.MeasurementResult(
                    measurementType,
                    result.score,
                    rawData
                )
            )
        )

        requireActivity().runOnUiThread {
            binding.apply {
                // UI 상태 변경
                progressBar.visibility = View.GONE
                tvProgress.visibility = View.GONE
                measurementInfo.visibility = View.GONE
                resultCard.visibility = View.VISIBLE
                initialButtons.visibility = View.GONE
                resultButtons.visibility = View.VISIBLE

                // 메인 결과 표시
                tvResult.text = "심박 점수: ${result.score.toInt()}점"
                tvHeartRate.text = "${result.heartRate.toInt()} BPM"
                tvHRV.text = "${result.hrv.toInt()} ms"
                tvMeasurementTime.text = "30초"

                // 아이콘 설정
                val iconColor = when {
                    result.score >= 85 -> requireContext().getColor(R.color.safety_safe)
                    result.score >= 70 -> requireContext().getColor(R.color.primary_color)
                    result.score >= 55 -> requireContext().getColor(android.R.color.holo_orange_dark)
                    else -> requireContext().getColor(android.R.color.holo_red_dark)
                }
                ivResultIcon.setColorFilter(iconColor)

                // 상세 설명
                tvResultDetail.text = getDetailedDescription(result)

                // 버튼 설정
                btnNext.setOnClickListener {
                    onMeasurementComplete(result.score, rawData)
                }
            }
        }
    }

    private fun buildRawDataJson(result: PPGAnalysisResult): String {
        return """
        {
            "heartRate": ${result.heartRate},
            "hrv": ${result.hrv},
            "signalQuality": ${result.signalQuality},
            "score": ${result.score},
            "signalLength": ${rawPPGSignal.size},
            "duration": $MEASUREMENT_TIME,
            "samplingRate": $SAMPLING_RATE,
            "additionalMetrics": {
                ${result.additionalMetrics.entries.joinToString(",\n") {
            "\"${it.key}\": ${it.value}"
        }}
            },
            "timestamp": ${System.currentTimeMillis()}
        }
        """.trimIndent()
    }

    private fun getDetailedDescription(result: PPGAnalysisResult): String {
        return when {
            result.score >= 85 -> "심박이 매우 안정적입니다"
            result.score >= 70 -> "정상적인 심박 패턴을 보이고 있습니다"
            result.score >= 55 -> "보통 수준의 심박 상태입니다"
            result.score >= 40 -> "주의가 필요한 상태입니다"
            else -> "심박 상태에 주의가 필요합니다"
        }
    }

    /* ─────────── Helper Functions ─────────── */
    private fun List<Float>.variance(): Float {
        if (isEmpty()) return 0f
        val mean = average().toFloat()
        return map { (it - mean).pow(2) }.average().toFloat()
    }

    private fun List<Float>.standardDeviation(): Float = sqrt(variance())

    private fun findLocalMaxima(signal: List<Float>): List<Int> {
        val maxima = mutableListOf<Int>()
        for (i in 1 until signal.size - 1) {
            if (signal[i] > signal[i - 1] && signal[i] > signal[i + 1]) {
                maxima.add(i)
            }
        }
        return maxima
    }

    private fun showWarning(message: String) {
        requireActivity().runOnUiThread {
            binding.tvInstruction.apply {
                text = "⚠️ $message"
                setTextColor(requireContext().getColor(android.R.color.holo_orange_dark))
            }
        }
    }

    /* ─────────── 상태 UI 업데이트 ─────────── */
    override fun onStateChanged(state: MeasurementState) {
        when (state) {
            is MeasurementState.Preparing -> {
                binding.apply {
                    btnStart.isEnabled = false
                    tvInstruction.text = "카메라 준비 중..."
                    tvSignalQuality.text = "준비 중"
                    tvTimer.text = "30초"
                    tvRealtimeBPM.text = "--"
                }
            }

            is MeasurementState.InProgress -> {
                binding.apply {
                    progressBar.progress = state.progress.toInt()
                    tvProgress.text = "측정 중... ${state.progress.toInt()}%"
                }
            }

            is MeasurementState.Completed -> {
                // displayResults에서 처리됨
            }

            is MeasurementState.Error -> {
                binding.apply {
                    tvInstruction.text = state.message
                    tvInstruction.setTextColor(requireContext().getColor(android.R.color.holo_red_dark))
                    btnStart.isEnabled = true
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                    initialButtons.visibility = View.VISIBLE
                    resultButtons.visibility = View.GONE
                }
            }

            else -> { /* Idle 등 */ }
        }
    }

    /* ─────────── Reset / Cleanup ─────────── */
    private fun resetMeasurement() {
        measurementTimer?.cancel()
        clearMeasurementData()
        closeCamera()

        binding.apply {
            btnStart.isEnabled = true
            progressBar.visibility = View.GONE
            tvProgress.visibility = View.GONE
            measurementInfo.visibility = View.VISIBLE
            resultCard.visibility = View.GONE
            initialButtons.visibility = View.VISIBLE
            resultButtons.visibility = View.GONE
            tvInstruction.text = "카메라에 손가락을 올려주세요"
            tvInstruction.setTextColor(requireContext().getColor(R.color.text_primary))
            tvTimer.text = "30초"
            tvSignalQuality.text = "준비 중"
            tvRealtimeBPM.text = "--"
            fingerGuideImage.visibility = View.VISIBLE
        }
    }

    private fun clearMeasurementData() {
        rawPPGSignal.clear()
        timestamps.clear()
        lumaValues.clear()
        redChannel.clear()
        greenChannel.clear()
        blueChannel.clear()
        signalQualityBuffer.clear()
    }

    private fun closeCamera() {
        captureSession?.close()
        captureSession = null
        cameraDevice?.close()
        cameraDevice = null
        imageReader?.close()
        imageReader = null
    }

    override fun onDestroyView() {
        super.onDestroyView()
        measurementTimer?.cancel()
        closeCamera()
        if (::bgThread.isInitialized) stopBgThread()
        _binding = null
    }
}