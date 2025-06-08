data class Entry(
    val biorhythmIndex: Double,
    val checklistScore: Int,
    val date: String,
    val dept: String,
    val finalSafetyScore: Double,
    val name: String,
    val ppgScore: Int,
    val pupilScore: Double,
    val recommendations: List<String>,
    val safetyLevel: String,
    val timestamp: Long,
    val tremorScore: Double,
    val userId: String
)