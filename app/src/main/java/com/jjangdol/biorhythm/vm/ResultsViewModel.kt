// app/src/main/java/com/jjangdol/biorhythm/vm/ResultsViewModel.kt
package com.jjangdol.biorhythm.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jjangdol.biorhythm.model.ChecklistResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ResultsViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth
) : ViewModel() {

    // UI에서 관찰할 dailyResults Flow
    private val _dailyResults = MutableStateFlow<List<ChecklistResult>>(emptyList())
    val dailyResults: StateFlow<List<ChecklistResult>> = _dailyResults

    init {
        fetchDailyResults()
    }

    /** Firestore 에서 “results/{uid}/daily” 문서들을 실시간으로 가져와서 _dailyResults 에 업데이트 */
    fun fetchDailyResults() {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrEmpty()) {
            Log.w(TAG, "fetchDailyResults: not logged in")
            return
        }
        firestore
            .collection("results")
            .document(uid)
            .collection("daily")
            .addSnapshotListener { snaps, err ->
                if (err != null) {
                    Log.e(TAG, "Error listening for daily results", err)
                    return@addSnapshotListener
                }
                val list = snaps
                    ?.documents
                    ?.mapNotNull { doc ->
                        // ChecklistResult 데이터 클래스로 바로 변환
                        doc.toObject(ChecklistResult::class.java)
                    }
                    ?: emptyList()

                viewModelScope.launch {
                    _dailyResults.value = list
                }
            }
    }

    companion object {
        private const val TAG = "ResultsViewModel"
    }
}
