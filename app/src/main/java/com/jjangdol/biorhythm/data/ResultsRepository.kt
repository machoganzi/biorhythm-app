// app/src/main/java/com/jjangdol/biorhythm/data/ResultsRepository.kt
package com.jjangdol.biorhythm.data

import com.google.firebase.firestore.FirebaseFirestore
import com.jjangdol.biorhythm.model.ChecklistResult
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@ActivityRetainedScoped
class ResultsRepository @Inject constructor(
    private val db: FirebaseFirestore
) {
    private val fmt = DateTimeFormatter.ISO_DATE

    /** 오늘자 결과 리스트를 실시간 스트리밍 */
    fun watchTodayResults(): Flow<List<ChecklistResult>> = callbackFlow {
        val today = LocalDate.now().format(fmt)
        val col = db.collection("results")
            .document(today)
            .collection("entries")

        val sub = col.addSnapshotListener { snap, e ->
            if (e != null) {
                close(e)
                return@addSnapshotListener
            }
            val list = snap!!.documents.mapNotNull { ds ->
                ds.toObject(ChecklistResult::class.java)
            }
            trySend(list)
        }
        awaitClose { sub.remove() }
    }
}
