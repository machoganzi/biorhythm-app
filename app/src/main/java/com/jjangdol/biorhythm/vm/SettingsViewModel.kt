// app/src/main/java/com/jjangdol/biorhythm/vm/SettingsViewModel.kt
package com.jjangdol.biorhythm.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjangdol.biorhythm.data.SettingsRepository
import com.jjangdol.biorhythm.model.ChecklistWeight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository
) : ViewModel() {

    /** Firestore 의 실시간 스트림을 StateFlow 로 변환 */
    val items: StateFlow<List<ChecklistWeight>> =
        repo.observeWeights()
            .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun updateWeight(pos: Int, newWeight: Int) {
        val id = items.value[pos].id
        viewModelScope.launch {
            repo.updateWeight(id, newWeight)
        }
    }

    fun addQuestion(question: String) {
        viewModelScope.launch {
            repo.addItem(question)
        }
    }

    fun removeQuestion(pos: Int) {
        val id = items.value[pos].id
        viewModelScope.launch {
            repo.removeItem(id)
        }
    }
    fun save() { /* no-op */ }
}
