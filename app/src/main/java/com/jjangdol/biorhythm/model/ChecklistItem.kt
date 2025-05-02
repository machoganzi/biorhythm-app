package com.jjangdol.biorhythm.model

/**
 * UI에서 실제 사용자가 보는 체크리스트 항목 모델.
 * ChecklistConfig 기반으로 생성되며, 사용자의 Yes/No 선택(answeredYes)을 담음.
 */
data class ChecklistItem(
    val id: String,             // ChecklistConfig.id
    val question: String,       // ChecklistConfig.question
    var weight: Int,            // ChecklistConfig.weight
    var answeredYes: Boolean? = null  // 사용자의 답변
)
