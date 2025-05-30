package com.jjangdol.biorhythm.model

/**
 * Firestore 설정: 각 문항(question)에 부여된 가중치(weight)를 담는 데이터 모델
 * 문서 ID가 question 고유 ID로 사용.
 */
data class ChecklistConfig(
    val id: String = "",       // Firestore 문서 ID
    val question: String = "", // 문항 텍스트
    val weight: Int = 10       // 기본 가중치
)
 