import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.util.List;

fun uploadEntriesFromAssets(context: Context) {
    val db = FirebaseFirestore.getInstance()
    val inputStream = context.assets.open("entries.json")
    val reader = InputStreamReader(inputStream)

    val entryType = object : TypeToken<List<Entry>>() {}.type
    val entries: List<Entry> = Gson().fromJson(reader, entryType)

    for (entry in entries) {
        db.collection("results")
            .add(entry)
            .addOnSuccessListener {
                println("✅ 성공: ${entry.name}")
            }
            .addOnFailureListener {
                println("❌ 실패: ${it.message}")
            }
    }
}
