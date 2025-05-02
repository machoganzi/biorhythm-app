// app/src/main/java/com/jjangdol/biorhythm/data/SettingsRepository.kt
package com.jjangdol.biorhythm.data

import com.google.firebase.firestore.FirebaseFirestore
import com.jjangdol.biorhythm.model.ChecklistWeight
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@ViewModelScoped
class SettingsRepository @Inject constructor(
    private val firestore: FirebaseFirestore
) {
    private val col = firestore
        .collection("settings")
        .document("weights")
        .collection("checklist")

    /** 실시간으로 문항 리스트를 스트림으로 방출 */
    fun observeWeights(): Flow<List<ChecklistWeight>> = callbackFlow {
        val sub = col
            .orderBy("__name__")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    close(err); return@addSnapshotListener
                }
                val items = snap?.documents
                    ?.map { doc ->
                        ChecklistWeight(
                            id = doc.id,
                            question = doc.getString("question") ?: "",
                            weight = (doc.getLong("weight") ?: 1L).toInt()
                        )
                    } ?: emptyList()
                trySend(items)
            }
        awaitClose { sub.remove() }
    }

    /** 가중치 업데이트 */
    suspend fun updateWeight(id: String, weight: Int) {
        col.document(id).update("weight", weight).await()
    }

    /** 새 문항 추가 (기본 weight=1) */
    suspend fun addItem(question: String) {
        col.add(mapOf(
            "question" to question,
            "weight"   to 1
        )).await()
    }

    /** 문항 삭제 */
    suspend fun removeItem(id: String) {
        col.document(id).delete().await()
    }
}
