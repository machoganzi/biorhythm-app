// app/src/main/java/com/jjangdol/biorhythm/model/BiorhythmData.kt
package com.jjangdol.biorhythm.model

import java.time.LocalDate

data class BiorhythmData(
    val date: LocalDate,
    val physical: Float,
    val emotional: Float,
    val intellectual: Float
)
