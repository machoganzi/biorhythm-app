// app/src/main/java/com/jjangdol/biorhythm/data/model/Notification.kt
package com.jjangdol.biorhythm.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Notification(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val content: String = "",
    val priority: NotificationPriority = NotificationPriority.NORMAL,
    val active: Boolean = true,  // isActive -> active 변경
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null,
    val createdBy: String = "admin"
) {
    // Firestore용 빈 생성자
    constructor() : this(
        id = "",
        title = "",
        content = "",
        priority = NotificationPriority.NORMAL,
        active = true,  // isActive -> active 변경
        createdAt = null,
        updatedAt = null,
        createdBy = "admin"
    )
}

enum class NotificationPriority(val displayName: String, val colorRes: String) {
    HIGH("긴급", "#F44336"),      // Red
    NORMAL("일반", "#2196F3"),    // Blue
    LOW("안내", "#4CAF50")        // Green
}