// app/src/main/java/com/jjangdol/biorhythm/data/ResultsRepository.kt
package com.jjangdol.biorhythm.data

import com.google.firebase.firestore.FirebaseFirestore
import com.jjangdol.biorhythm.model.ChecklistResult
import dagger.hilt.android.scopes.ActivityRetainedScoped
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
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

    /** 특정 날짜의 결과 리스트를 실시간 스트리밍 */
    fun watchResultsByDate(date: LocalDate): Flow<List<ChecklistResult>> = callbackFlow {
        val dateString = date.format(fmt)
        val col = db.collection("results")
            .document(dateString)
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

    /** 특정 날짜의 결과 리스트를 한 번만 가져오기 */
    suspend fun getResultsByDate(date: LocalDate): List<ChecklistResult> {
        return try {
            val dateString = date.format(fmt)
            val snapshot = db.collection("results")
                .document(dateString)
                .collection("entries")
                .get()
                .await()

            snapshot.documents.mapNotNull { ds ->
                ds.toObject(ChecklistResult::class.java)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** 여러 날짜 범위의 결과를 가져오기 (일주일, 한 달 등) */
    suspend fun getResultsByDateRange(startDate: LocalDate, endDate: LocalDate): List<ChecklistResult> {
        val results = mutableListOf<ChecklistResult>()

        var currentDate = startDate
        while (!currentDate.isAfter(endDate)) {
            val dateResults = getResultsByDate(currentDate)
            results.addAll(dateResults)
            currentDate = currentDate.plusDays(1)
        }

        return results
    }

    /** 지난 7일간의 결과를 가져오기 */
    suspend fun getLastWeekResults(): List<ChecklistResult> {
        val endDate = LocalDate.now()
        val startDate = endDate.minusDays(6)
        return getResultsByDateRange(startDate, endDate)
    }
}