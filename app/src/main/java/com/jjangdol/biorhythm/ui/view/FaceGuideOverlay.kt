package com.jjangdol.biorhythm.ui.view

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.jjangdol.biorhythm.R

/**
 * 피로도 측정을 위한 얼굴 가이드 오버레이
 */
class FaceGuideOverlay @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // 얼굴 인식 상태
    enum class FaceStatus {
        NO_FACE,      // 얼굴 없음
        FACE_TOO_FAR, // 얼굴이 너무 멀음
        FACE_TOO_CLOSE, // 얼굴이 너무 가까움
        FACE_NOT_CENTERED, // 얼굴이 중앙에 없음
        FACE_PERFECT   // 완벽한 위치
    }

    private var faceStatus = FaceStatus.NO_FACE
    private var faceRect: RectF? = null

    // 가이드 영역
    private var guideRect = RectF()
    private var centerX = 0f
    private var centerY = 0f

    // 페인트 설정
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#80000000") // 반투명 검정
        style = Paint.Style.FILL
    }

    private val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        strokeCap = Paint.Cap.ROUND
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 48f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(4f, 0f, 2f, Color.BLACK)
    }

    private val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f

        // 가이드 영역 설정 (타원형)
        val guideWidth = w * 0.6f
        val guideHeight = h * 0.7f
        guideRect.set(
            centerX - guideWidth / 2,
            centerY - guideHeight / 2,
            centerX + guideWidth / 2,
            centerY + guideHeight / 2
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 배경 오버레이 (가이드 영역 제외)
        drawOverlayWithHole(canvas)

        // 얼굴 가이드
        drawFaceGuide(canvas)

        // 상태에 따른 UI
        drawStatusUI(canvas)

        // 실제 얼굴이 감지되면 표시
        faceRect?.let { drawDetectedFace(canvas, it) }
    }

    private fun drawOverlayWithHole(canvas: Canvas) {
        val path = Path().apply {
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addOval(guideRect, Path.Direction.CCW)
        }
        canvas.drawPath(path, overlayPaint)
    }

    private fun drawFaceGuide(canvas: Canvas) {
        // 가이드 색상 설정
        val guideColor = when (faceStatus) {
            FaceStatus.FACE_PERFECT -> Color.parseColor("#4CAF50") // 녹색
            FaceStatus.NO_FACE -> Color.parseColor("#FFC107") // 노란색
            else -> Color.parseColor("#FF5722") // 빨간색
        }

        guidePaint.color = guideColor
        cornerPaint.color = guideColor

        // 타원형 가이드
        canvas.drawOval(guideRect, guidePaint)

        // 모서리 표시
        drawCorners(canvas)

        // 중앙 십자선
        drawCenterCross(canvas)

        // 눈 가이드
        drawEyeGuides(canvas)
    }

    private fun drawCorners(canvas: Canvas) {
        val cornerLength = 30f
        val margin = 10f

        // 좌상단
        canvas.drawLine(
            guideRect.left - margin, guideRect.top + cornerLength,
            guideRect.left - margin, guideRect.top - margin,
            cornerPaint
        )
        canvas.drawLine(
            guideRect.left - margin, guideRect.top - margin,
            guideRect.left + cornerLength, guideRect.top - margin,
            cornerPaint
        )

        // 우상단
        canvas.drawLine(
            guideRect.right + margin, guideRect.top + cornerLength,
            guideRect.right + margin, guideRect.top - margin,
            cornerPaint
        )
        canvas.drawLine(
            guideRect.right + margin, guideRect.top - margin,
            guideRect.right - cornerLength, guideRect.top - margin,
            cornerPaint
        )

        // 좌하단
        canvas.drawLine(
            guideRect.left - margin, guideRect.bottom - cornerLength,
            guideRect.left - margin, guideRect.bottom + margin,
            cornerPaint
        )
        canvas.drawLine(
            guideRect.left - margin, guideRect.bottom + margin,
            guideRect.left + cornerLength, guideRect.bottom + margin,
            cornerPaint
        )

        // 우하단
        canvas.drawLine(
            guideRect.right + margin, guideRect.bottom - cornerLength,
            guideRect.right + margin, guideRect.bottom + margin,
            cornerPaint
        )
        canvas.drawLine(
            guideRect.right + margin, guideRect.bottom + margin,
            guideRect.right - cornerLength, guideRect.bottom + margin,
            cornerPaint
        )
    }

    private fun drawCenterCross(canvas: Canvas) {
        val crossSize = 20f
        val crossPaint = Paint(cornerPaint).apply {
            strokeWidth = 2f
            alpha = 150
        }

        canvas.drawLine(
            centerX - crossSize, centerY,
            centerX + crossSize, centerY,
            crossPaint
        )
        canvas.drawLine(
            centerX, centerY - crossSize,
            centerX, centerY + crossSize,
            crossPaint
        )
    }

    private fun drawEyeGuides(canvas: Canvas) {
        eyePaint.color = when (faceStatus) {
            FaceStatus.FACE_PERFECT -> Color.parseColor("#804CAF50")
            else -> Color.parseColor("#80FFC107")
        }

        val eyeY = centerY - guideRect.height() * 0.1f
        val eyeDistance = guideRect.width() * 0.25f
        val eyeRadius = 25f

        // 왼쪽 눈
        canvas.drawCircle(centerX - eyeDistance, eyeY, eyeRadius, eyePaint)

        // 오른쪽 눈
        canvas.drawCircle(centerX + eyeDistance, eyeY, eyeRadius, eyePaint)
    }

    private fun drawStatusUI(canvas: Canvas) {
        val statusText = when (faceStatus) {
            FaceStatus.NO_FACE -> "얼굴을 가이드에 맞춰주세요"
            FaceStatus.FACE_TOO_FAR -> "조금 더 가까이 와주세요"
            FaceStatus.FACE_TOO_CLOSE -> "조금 더 멀리 가주세요"
            FaceStatus.FACE_NOT_CENTERED -> "얼굴을 중앙으로 맞춰주세요"
            FaceStatus.FACE_PERFECT -> "완벽합니다! 자연스럽게 깜빡여주세요"
        }

        textPaint.color = when (faceStatus) {
            FaceStatus.FACE_PERFECT -> Color.parseColor("#4CAF50")
            FaceStatus.NO_FACE -> Color.WHITE
            else -> Color.parseColor("#FF5722")
        }

        // 텍스트 배경
        val textBounds = Rect()
        textPaint.getTextBounds(statusText, 0, statusText.length, textBounds)

        val bgRect = RectF(
            centerX - textBounds.width() / 2 - 20f,
            guideRect.bottom + 40f,
            centerX + textBounds.width() / 2 + 20f,
            guideRect.bottom + 100f
        )

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#80000000")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(bgRect, 15f, 15f, bgPaint)

        // 상태 텍스트
        textPaint.textSize = 32f
        canvas.drawText(statusText, centerX, guideRect.bottom + 75f, textPaint)
    }

    private fun drawDetectedFace(canvas: Canvas, face: RectF) {
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when (faceStatus) {
                FaceStatus.FACE_PERFECT -> Color.parseColor("#4CAF50")
                else -> Color.parseColor("#FF5722")
            }
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        canvas.drawRect(face, facePaint)

        // 얼굴 중심점
        val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = facePaint.color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(face.centerX(), face.centerY(), 5f, centerPaint)
    }

    /**
     * 얼굴 상태 업데이트
     */
    fun updateFaceStatus(status: FaceStatus, faceRect: RectF? = null) {
        this.faceStatus = status
        this.faceRect = faceRect
        invalidate()
    }

    /**
     * 가이드 숨기기/보이기
     */
    fun setGuideVisible(visible: Boolean) {
        visibility = if (visible) View.VISIBLE else View.GONE
    }
}