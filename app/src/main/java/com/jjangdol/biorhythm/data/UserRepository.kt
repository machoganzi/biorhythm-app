package com.jjangdol.biorhythm.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class UserProfile(
    val dept: String = "",
    val name: String = "",
    val dob: String = ""  // "yyyy-MM-dd" 형식
)

class UserRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val db: FirebaseFirestore
) {

    /** 익명 로그인 후, 프로필을 Firestore에 저장 */
    suspend fun signInAndSaveProfile(dept: String, name: String, dob: String) {
        // 1) 익명 인증
        auth.signInAnonymously().await()
        val uid = auth.currentUser!!.uid

        // 2) Firestore에 사용자 정보 저장
        val profile = UserProfile(dept, name, dob)
        db.collection("users")
            .document(uid)
            .set(profile)
            .await()
    }
}
