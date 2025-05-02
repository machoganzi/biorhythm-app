// app/src/main/java/com/jjangdol/biorhythm/vm/ChecklistViewModel.kt
package com.jjangdol.biorhythm.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.jjangdol.biorhythm.model.ChecklistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChecklistViewModel @Inject constructor(
    private val firestore: FirebaseFirestore
) : ViewModel() {

    private val _items = MutableStateFlow<List<ChecklistItem>>(emptyList())
    val items: StateFlow<List<ChecklistItem>> = _items

    init {
        val col = firestore
            .collection("settings")
            .document("weights")
            .collection("checklist")

        col.addSnapshotListener { snaps, err ->
            if (err != null) {
                err.printStackTrace()
                return@addSnapshotListener
            }
            val list = snaps
                ?.documents
                ?.mapNotNull { doc ->
                    val question = doc.getString("question") ?: return@mapNotNull null
                    val weight   = doc.getLong("weight")?.toInt() ?: return@mapNotNull null
                    // 1) id로 doc.id 전달, 2) answeredYes 는 기본 null
                    ChecklistItem(
                        id = doc.id,
                        question = question,
                        weight = weight
                    )
                }
                ?: emptyList()

            viewModelScope.launch {
                _items.value = list
            }
        }
    }

    /**
     * 사용자가 pos 위치 항목에 Yes/No 선택(yes)을 변경했을 때 호출
     */
    fun answerChanged(pos: Int, yes: Boolean) {
        val current = _items.value.toMutableList()
        if (pos in current.indices) {
            // copy(answeredYes = yes) 로 변경
            current[pos] = current[pos].copy(answeredYes = yes)
            _items.value = current
        }
    }
}

