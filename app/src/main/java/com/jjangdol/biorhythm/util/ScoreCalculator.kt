package com.jjangdol.biorhythm.util

import com.jjangdol.biorhythm.model.ChecklistItem
import com.jjangdol.biorhythm.model.BiorhythmData
import kotlin.math.absoluteValue

/**
 * 체크리스트 점수, 바이오리듬 인덱스, 최종 점수 계산 로직을 담은 유틸 객체
 */
object ScoreCalculator {

    /**
     * 체크리스트에서 “예”로 답한 항목들의 weight 합을 점수로 반환
     */
    fun calcChecklistScore(items: List<ChecklistItem>): Int {
        return items.sumOf { item ->
            if (item.answeredYes == true) item.weight else 0
        }
    }

    /**
     * 바이오리듬 데이터를 0~100 사이의 인덱스로 환산
     * 예시: 물리·감정·지성 요소 절댓값 평균 * 100
     */
    fun calcBiorhythmIndex(data: BiorhythmData): Int {
        // BiorhythmData에 physical, emotional, intellectual 프로퍼티가 있다고 가정
        val sum = data.physical.absoluteValue +
                data.emotional.absoluteValue +
                data.intellectual.absoluteValue
        val avg = sum / 3.0
        return (avg * 100).toInt()
    }

    /**
     * 체크리스트 점수와 바이오리듬 인덱스를 결합하여 최종 점수 산출
     * 예시: 단순 평균
     */
    fun calcFinalScore(checklistScore: Int, bioIndex: Int): Int {
        return (checklistScore + bioIndex) / 2
    }
}
