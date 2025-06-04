// app/src/main/java/com/jjangdol/biorhythm/data/repository/UserNotificationRepository.kt
package com.jjangdol.biorhythm.data.repository

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용자별 알림 상태 관리 Repository
 * 사용자가 읽은 알림 상태를 관리
 */
@Singleton
class UserNotificationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val firestore: FirebaseFirestore = Firebase.firestore
    private val userNotificationsCollection = firestore.collection("userNotifications")

    // 현재 사용자 ID (실제로는 로그인 시스템에서 가져와야 함)
    private fun getCurrentUserId(): String {
        val prefs = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
        val name = prefs.getString("user_name", "") ?: ""
        val dept = prefs.getString("user_dept", "") ?: ""
        return "${dept}_${name}".replace(" ", "_")
    }

    /**
     * 사용자가 읽은 알림 ID 목록을 실시간으로 관찰
     */
    fun getReadNotificationIds(): Flow<Set<String>> = callbackFlow {
        val userId = getCurrentUserId()
        val listener = userNotificationsCollection
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }

                val readIds = try {
                    snapshot?.get("readNotifications") as? List<String> ?: emptyList()
                } catch (e: Exception) {
                    emptyList<String>()
                }

                trySend(readIds.toSet())
            }

        awaitClose { listener.remove() }
    }

    /**
     * 알림을 읽음으로 표시
     */
    suspend fun markAsRead(notificationId: String): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
            val docRef = userNotificationsCollection.document(userId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val readList = (snapshot.get("readNotifications") as? List<String>)?.toMutableList()
                    ?: mutableListOf()

                if (notificationId !in readList) {
                    readList.add(notificationId)
                    transaction.set(docRef, mapOf(
                        "readNotifications" to readList,
                        "lastUpdated" to com.google.firebase.Timestamp.now()
                    ), com.google.firebase.firestore.SetOptions.merge())
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 모든 알림을 읽음으로 표시
     */
    suspend fun markAllAsRead(): Result<Unit> {
        return try {
            val userId = getCurrentUserId()

            // 모든 활성 알림 ID 가져오기
            val allNotificationIds = mutableListOf<String>()
            firestore.collection("notifications")
                .whereEqualTo("active", true)
                .get()
                .await()
                .documents
                .forEach { doc -> allNotificationIds.add(doc.id) }

            val docRef = userNotificationsCollection.document(userId)
            docRef.set(mapOf(
                "readNotifications" to allNotificationIds,
                "lastUpdated" to com.google.firebase.Timestamp.now()
            ), com.google.firebase.firestore.SetOptions.merge()).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 여러 알림을 읽음으로 표시
     */
    suspend fun markMultipleAsRead(notificationIds: List<String>): Result<Unit> {
        return try {
            val userId = getCurrentUserId()
            val docRef = userNotificationsCollection.document(userId)

            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val readList = (snapshot.get("readNotifications") as? List<String>)?.toMutableSet()
                    ?: mutableSetOf()

                readList.addAll(notificationIds)
                transaction.set(docRef, mapOf(
                    "readNotifications" to readList.toList(),
                    "lastUpdated" to com.google.firebase.Timestamp.now()
                ), com.google.firebase.firestore.SetOptions.merge())
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}