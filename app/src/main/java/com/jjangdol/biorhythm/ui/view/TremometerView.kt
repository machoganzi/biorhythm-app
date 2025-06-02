package com.jjangdol.biorhythm.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.*

/**
 * 모던하고 아름다운 손떨림 측정 뷰
 */
class TremometerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 현재 센서 값
    private var x = 0f
    private var y = 0f
    private var z = 0f

    // 애니메이션을 위한 이전 값
    private var animatedX = 0f
    private var animatedY = 0f
    private var animatedZ = 0f

    // 중심점
    private var centerX = 0f
    private var centerY = 0f
    private var radius = 0f

    // 궤적 저장
    private val trajectory = mutableListOf<PointF>()
    private val maxTrajectoryPoints = 50

    // 떨림 강도 계산
    private val magnitudeHistory = mutableListOf<Float>()
    private val maxHistorySize = 20

    // 애니메이터
    private var rippleAnimator: ValueAnimator? = null
    private var rippleRadius = 0f

    // 색상 설정 (사진과 비슷하게)
    private val primaryColor = Color.parseColor("#10B981") // 민트/터쿠아즈
    private val accentColor = Color.parseColor("#10B981") // 안정 상태 색상
    private val warningColor = Color.parseColor("#F59E0B") // Amber
    private val dangerColor = Color.parseColor("#EF4444") // Red
    private val backgroundColor = Color.parseColor("white") // 흰색ㅁㄴ
    private val surfaceColor = Color.parseColor("#FFFFFF")
    private val textColor = Color.parseColor("#374151") // 진한 회색

    // 페인트 설정
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
        style = Paint.Style.FILL
    }

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = surfaceColor
        style = Paint.Style.FILL
        setShadowLayer(20f, 0f, 10f, Color.parseColor("#1F000000"))
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#10B981") // 민트색 테두리
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E2E8F0")
        strokeWidth = 1f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(8f, 8f), 0f)
    }

    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#94A3B8")
        style = Paint.Style.FILL
    }

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        setShadowLayer(15f, 0f, 5f, Color.parseColor("#40000000"))
    }

    private val trajectoryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 40f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
    }

    private val stabilityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 8f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        startRippleAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f - 40f
        radius = minOf(w, h) / 2f - 100f
    }

    private fun startRippleAnimation() {
        rippleAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = DecelerateInterpolator()
            addUpdateListener { animation ->
                rippleRadius = animation.animatedValue as Float * 30f
                invalidate()
            }
        }
        rippleAnimator?.start()
    }

    /**
     * 센서 값
     */
    fun updateValue(newX: Float, newY: Float, newZ: Float) {
        this.x = newX
        this.y = newY
        this.z = newZ

        // 부드러운 애니메이션을 위한 보간
        val alpha = 0.3f // 부드러움 정도
        animatedX = animatedX * (1 - alpha) + x * alpha
        animatedY = animatedY * (1 - alpha) + y * alpha
        animatedZ = animatedZ * (1 - alpha) + z * alpha

        // 궤적 추가
        addTrajectoryPoint()

        // 떨림 강도 계산
        updateMagnitudeHistory()

        invalidate()
    }

    private fun addTrajectoryPoint() {
        val scaleFactor = radius / 15f
        val point = PointF(
            centerX + animatedX * scaleFactor,
            centerY - animatedY * scaleFactor
        )

        trajectory.add(point)
        if (trajectory.size > maxTrajectoryPoints) {
            trajectory.removeAt(0)
        }
    }

    private fun updateMagnitudeHistory() {
        val magnitude = sqrt(animatedX * animatedX + animatedY * animatedY + animatedZ * animatedZ)
        magnitudeHistory.add(magnitude)
        if (magnitudeHistory.size > maxHistorySize) {
            magnitudeHistory.removeAt(0)
        }
    }

    private fun getStabilityLevel(): Float {
        if (magnitudeHistory.isEmpty()) return 1f

        val avgMagnitude = magnitudeHistory.average()
        val variance = magnitudeHistory.map { (it - avgMagnitude).pow(2) }.average()
        val stability = 1f / (1f + variance.toFloat())

        return stability.coerceIn(0f, 1f)
    }

    private fun getStabilityColor(): Int {
        val stability = getStabilityLevel()
        return when {
            stability > 0.8f -> accentColor
            stability > 0.6f -> warningColor
            else -> dangerColor
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 배경
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // 메인 원형 배경
        canvas.drawCircle(centerX, centerY, radius, circlePaint)
        canvas.drawCircle(centerX, centerY, radius, borderPaint)

        // 그리드
        drawModernGrid(canvas)

        // 안정성 링
        drawStabilityRing(canvas)

        // 궤적
        drawSmoothTrajectory(canvas)

        // 현재 위치
        drawModernPosition(canvas)

        // 중심점
        drawCenterPoint(canvas)

        // 상태 표시
        drawStatusIndicator(canvas)
    }

    private fun drawModernGrid(canvas: Canvas) {
        val gridCount = 4
        val step = radius * 2 / gridCount

        // 동심원
        for (i in 1..gridCount) {
            val r = radius * i / gridCount
            canvas.drawCircle(centerX, centerY, r, gridPaint)
        }

        // 십자선
        canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, gridPaint)
        canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, gridPaint)

        // 대각선
        val diagonal = radius * 0.7f
        canvas.drawLine(
            centerX - diagonal, centerY - diagonal,
            centerX + diagonal, centerY + diagonal, gridPaint
        )
        canvas.drawLine(
            centerX - diagonal, centerY + diagonal,
            centerX + diagonal, centerY - diagonal, gridPaint
        )
    }

    private fun drawStabilityRing(canvas: Canvas) {
        val stability = getStabilityLevel()
        val color = getStabilityColor()

        stabilityPaint.color = Color.parseColor("#20" + Integer.toHexString(color).substring(2))

        // 배경 링
        canvas.drawCircle(centerX, centerY, radius + 15f, stabilityPaint)

        // 진행 링
        stabilityPaint.color = color
        canvas.drawArc(
            centerX - radius - 15f,
            centerY - radius - 15f,
            centerX + radius + 15f,
            centerY + radius + 15f,
            -90f,
            360f * stability,
            false,
            stabilityPaint
        )
    }


    private fun drawSmoothTrajectory(canvas: Canvas) {
        if (trajectory.size < 2) return

        val path = Path()

        for (i in trajectory.indices) {
            val point = trajectory[i]
            val alpha = (i.toFloat() / trajectory.size * 255).toInt()

            trajectoryPaint.color = Color.parseColor("#${alpha.toString(16).padStart(2, '0')}6366F1")

            if (i == 0) {
                path.moveTo(point.x, point.y)
            } else {
                // 베지어 곡선으로 부드러운 선 그리기
                val prevPoint = trajectory[i - 1]
                val midX = (prevPoint.x + point.x) / 2
                val midY = (prevPoint.y + point.y) / 2

                if (i == 1) {
                    path.lineTo(midX, midY)
                } else {
                    path.quadTo(prevPoint.x, prevPoint.y, midX, midY)
                }
            }
        }

        canvas.drawPath(path, trajectoryPaint)
    }

    private fun drawModernPosition(canvas: Canvas) {
        val scaleFactor = radius / 15f
        val posX = centerX + animatedX * scaleFactor
        val posY = centerY - animatedY * scaleFactor

        val color = getStabilityColor()

        // 리플 효과
        ripplePaint.color = Color.parseColor("#40" + Integer.toHexString(color).substring(2))
        canvas.drawCircle(posX, posY, rippleRadius, ripplePaint)

        // 외부 글로우
        ballPaint.color = Color.parseColor("#60" + Integer.toHexString(color).substring(2))
        canvas.drawCircle(posX, posY, 25f, ballPaint)

        // 메인 볼
        ballPaint.color = color
        canvas.drawCircle(posX, posY, 18f, ballPaint)

        // 내부 하이라이트
        ballPaint.color = Color.parseColor("#80FFFFFF")
        canvas.drawCircle(posX - 6f, posY - 6f, 8f, ballPaint)

        // Z축 표시 (펄스 효과)
        val zIntensity = abs(animatedZ) / 10f
        val zRadius = 15f + zIntensity * 10f
        val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = if (animatedZ > 0)
                Color.parseColor("#4010B981") else Color.parseColor("#40EF4444")
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        canvas.drawCircle(posX, posY, zRadius, zPaint)
    }

    private fun drawCenterPoint(canvas: Canvas) {
        // 중심점을 좀 더 눈에 띄게
        centerPaint.color = Color.parseColor("#10B981")
        canvas.drawCircle(centerX, centerY, 6f, centerPaint)

        // 흰색 내부
        centerPaint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, 3f, centerPaint)
    }

    private fun drawStatusIndicator(canvas: Canvas) {
        val stability = getStabilityLevel()
        val statusText = when {
            stability > 0.8f -> "매우 안정"
            stability > 0.6f -> "안정"
            stability > 0.4f -> "약간 불안정"
            else -> "불안정"
        }

        val statusColor = getStabilityColor()

        // 상태 텍스트 (원 아래쪽에 충분한 공간)
        textPaint.color = statusColor
        textPaint.textSize = 36f
        canvas.drawText(
            statusText,
            centerX,
            centerY + radius + 60f,  // 충분한 여백
            textPaint
        )

        // 안정도 퍼센트 (더 작게, 더 아래에)
        textPaint.color = Color.parseColor("#9CA3AF")
        textPaint.textSize = 24f
        canvas.drawText(
            "${(stability * 100).toInt()}% 안정",
            centerX,
            centerY + radius + 95f,  // 더 아래로
            textPaint
        )
    }

    /**
     * 데이터 초기화
     */
    fun clear() {
        trajectory.clear()
        magnitudeHistory.clear()
        x = 0f
        y = 0f
        z = 0f
        animatedX = 0f
        animatedY = 0f
        animatedZ = 0f
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rippleAnimator?.cancel()
    }
}