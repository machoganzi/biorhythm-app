// app/src/main/java/com/jjangdol/biorhythm/vm/BiorhythmViewModel.kt
package com.jjangdol.biorhythm.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jjangdol.biorhythm.model.BiorhythmData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlin.math.sin

@HiltViewModel
class BiorhythmViewModel @Inject constructor(): ViewModel() {

    private val _data = MutableStateFlow<List<BiorhythmData>>(emptyList())
    val data: StateFlow<List<BiorhythmData>> = _data.asStateFlow()

    /** dob: 생년월일, daysRange: 과거 n일 ~ 미래 m일 */
    fun load(dob: LocalDate, daysPast: Int = 15, daysFuture: Int = 15) {
        viewModelScope.launch {
            val today = LocalDate.now()
            val list = (-daysPast..daysFuture).map { offset ->
                val date = today.plusDays(offset.toLong())
                val days = ChronoUnit.DAYS.between(dob, date).toFloat()
                val physical    = sin(2 * Math.PI.toFloat() * days / 23f)
                val emotional   = sin(2 * Math.PI.toFloat() * days / 28f)
                val intellectual= sin(2 * Math.PI.toFloat() * days / 33f)
                BiorhythmData(date, physical, emotional, intellectual)
            }
            _data.value = list
        }
    }
}
