package com.jjangdol.biorhythm.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjangdol.biorhythm.data.ResultsRepository
import com.jjangdol.biorhythm.model.ChecklistResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdminResultsViewModel @Inject constructor(
    private val repo: ResultsRepository
) : ViewModel() {

    private val _allResults = MutableStateFlow<List<ChecklistResult>>(emptyList())
    val allResults: StateFlow<List<ChecklistResult>> = _allResults

    init {
        viewModelScope.launch {
            repo.watchTodayResults().collectLatest {
                _allResults.value = it
            }
        }
    }
}