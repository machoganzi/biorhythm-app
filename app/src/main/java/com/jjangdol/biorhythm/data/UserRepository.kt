package com.jjangdol.biorhythm.data

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest
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

    /** 사용자 정보를 기반으로 고유 ID 생성 */
    private fun generateUserId(dept: String, name: String, dob: String): String {
        val input = "$dept-$name-$dob"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    /** 사용자 정보 기반으로 로그인 후 프로필 저장 */
    suspend fun signInAndSaveProfile(dept: String, name: String, dob: String) {
        // 1) 사용자 정보 기반 고유 ID 생성
        val userId = generateUserId(dept, name, dob)

        // 2) 기존 사용자 확인
        val existingUser = db.collection("users")
            .document(userId)
            .get()
            .await()

        // 3) 사용자 프로필 생성/업데이트
        val profile = UserProfile(dept, name, dob)

        if (existingUser.exists()) {
            // 기존 사용자 - 정보 업데이트 (필요시)
            db.collection("users")
                .document(userId)
                .set(profile)
                .await()
        } else {
            // 새 사용자 - 프로필 생성
            db.collection("users")
                .document(userId)
                .set(profile)
                .await()
        }
    }

    /** 현재 사용자 프로필 가져오기 */
    suspend fun getCurrentUserProfile(dept: String, name: String, dob: String): UserProfile? {
        val userId = generateUserId(dept, name, dob)
        val document = db.collection("users")
            .document(userId)
            .get()
            .await()

        return if (document.exists()) {
            document.toObject(UserProfile::class.java)
        } else {
            null
        }
    }

    /** 사용자 ID 가져오기 */
    fun getUserId(dept: String, name: String, dob: String): String {
        return generateUserId(dept, name, dob)
    }
}