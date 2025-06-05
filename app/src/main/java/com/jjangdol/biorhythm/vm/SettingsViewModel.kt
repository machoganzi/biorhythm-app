package com.jjangdol.biorhythm.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.jjangdol.biorhythm.model.ChecklistConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor() : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()
    private val _items = MutableStateFlow<List<ChecklistConfig>>(emptyList())
    val items: StateFlow<List<ChecklistConfig>> = _items.asStateFlow()

    init {
        loadItems()
    }

    private fun loadItems() {
        viewModelScope.launch {
            try {
                val snapshot = firestore.collection("checklist_config")
                    .orderBy("order")
                    .get()
                    .await()

                val itemsList = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(ChecklistConfig::class.java)?.copy(id = doc.id)
                }

                _items.value = itemsList
            } catch (e: Exception) {
                // 에러 처리
            }
        }
    }

    fun addQuestion(question: String) {
        addQuestionWithWeight(question, 50) // 기본 가중치 50
    }

    fun addQuestionWithWeight(question: String, weight: Int) {
        viewModelScope.launch {
            try {
                val newItem = ChecklistConfig(
                    id = "", // Firestore가 자동 생성
                    question = question,
                    weight = weight,
                    order = _items.value.size
                )

                val docRef = firestore.collection("checklist_config")
                    .add(newItem)
                    .await()

                // 로컬 상태 업데이트 (ID 포함)
                _items.value = _items.value + newItem.copy(id = docRef.id)
            } catch (e: Exception) {
                // 에러 처리
            }
        }
    }

    fun updateWeight(position: Int, weight: Int) {
        viewModelScope.launch {
            try {
                val items = _items.value.toMutableList()
                if (position in items.indices) {
                    items[position] = items[position].copy(weight = weight)
                    _items.value = items

                    // Firestore 업데이트
                    updateItemInFirestore(items[position])
                }
            } catch (e: Exception) {
                // 에러 처리
            }
        }
    }

    fun updateQuestion(position: Int, newQuestion: String) {
        viewModelScope.launch {
            try {
                val items = _items.value.toMutableList()
                if (position in items.indices) {
                    items[position] = items[position].copy(question = newQuestion)
                    _items.value = items

                    // Firestore 업데이트
                    updateItemInFirestore(items[position])
                }
            } catch (e: Exception) {
                // 에러 처리
            }
        }
    }

    fun removeQuestion(position: Int) {
        viewModelScope.launch {
            try {
                val items = _items.value.toMutableList()
                if (position in items.indices) {
                    val itemToRemove = items[position]
                    items.removeAt(position)

                    // 순서 재정렬
                    items.forEachIndexed { index, item ->
                        items[index] = item.copy(order = index)
                    }

                    _items.value = items

                    // Firestore에서 삭제
                    deleteItemFromFirestore(itemToRemove)

                    // 순서 업데이트
                    updateAllOrdersInFirestore(items)
                }
            } catch (e: Exception) {
                // 에러 처리
            }
        }
    }

    private suspend fun updateItemInFirestore(item: ChecklistConfig) {
        try {
            if (item.id.isNotEmpty()) {
                firestore.collection("checklist_config")
                    .document(item.id)
                    .set(item)
                    .await()
            }
        } catch (e: Exception) {
            // 에러 처리
        }
    }

    private suspend fun deleteItemFromFirestore(item: ChecklistConfig) {
        try {
            if (item.id.isNotEmpty()) {
                firestore.collection("checklist_config")
                    .document(item.id)
                    .delete()
                    .await()
            }
        } catch (e: Exception) {
            // 에러 처리
        }
    }

    private suspend fun updateAllOrdersInFirestore(items: List<ChecklistConfig>) {
        try {
            items.forEach { item ->
                updateItemInFirestore(item)
            }
        } catch (e: Exception) {
            // 에러 처리
        }
    }

    fun save() {
        // 이미 실시간으로 저장되므로 추가 작업 필요 없음
        // UI 피드백용으로만 유지
    }
}