package com.jjangdol.biorhythm.model

/**
 * Firestore에 저장된 한 사람의 체크리스트 + 바이오리듬 최종 점수 결과
 */
data class ChecklistResult(
    val userId: String = "",
    val name: String = "",
    val dept: String = "",
    val checklistScore: Int = 0,
    val biorhythmIndex: Int = 0,
    val finalScore: Int = 0,
    val date: String = ""
)
