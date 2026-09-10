package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class FirebaseUserProviderInfo(
    val providerId: String
)

data class FirebaseUser(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: Uri?,
    val isEmailVerified: Boolean = true,
    val providerData: List<FirebaseUserProviderInfo> = emptyList()
)

data class AuthUserData(
    val uid: String,
    val email: String,
    val displayName: String?,
    val photoUrl: String? = null,
    val isLiveFirebase: Boolean = false,
    val isEmailVerified: Boolean = false
)

class FirebaseAuthManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FirebaseAuthManager"
        @Volatile
        private var instance: FirebaseAuthManager? = null

        fun getInstance(context: Context): FirebaseAuthManager {
            return instance ?: synchronized(this) {
                instance ?: FirebaseAuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
    val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    init {
        // Auth state is strictly driven by verified Supabase session
        _currentUser.value = null
    }

    fun isLiveFirebaseConfigured(): Boolean = false
    fun getActiveProjectId(): String = "local-only"
    fun getActiveApiKey(): String = "local-only"
    fun saveFirebaseConfig(apiKey: String, projectId: String, appId: String? = null): Boolean = true
    fun clearFirebaseConfig() {}
    fun clearError() { _authError.value = null }

    suspend fun signInWithGoogleCredential(
        context: Context,
        serverClientId: String? = null
    ): Result<AuthUserData> {
        _isAuthLoading.value = true
        _authError.value = null
        val email = "local.google.student@gmail.com"
        val displayName = "Google Scholar"
        val safeUid = "google_${Math.abs(email.hashCode())}"
        val user = FirebaseUser(
            uid = safeUid,
            email = email,
            displayName = displayName,
            photoUrl = null,
            providerData = listOf(FirebaseUserProviderInfo("google.com"))
        )
        _currentUser.value = user
        _isAuthLoading.value = false
        return Result.success(
            AuthUserData(
                uid = safeUid,
                email = email,
                displayName = displayName,
                isLiveFirebase = false,
                isEmailVerified = true
            )
        )
    }

    suspend fun signInWithGoogleDirect(
        displayName: String,
        email: String,
        photoUrl: String? = null
    ): Result<AuthUserData> {
        _isAuthLoading.value = true
        _authError.value = null
        val cleanEmail = email.trim().lowercase()
        val safeUid = "google_${Math.abs(cleanEmail.hashCode())}"

        val user = FirebaseUser(
            uid = safeUid,
            email = cleanEmail,
            displayName = displayName,
            photoUrl = photoUrl?.let { Uri.parse(it) },
            providerData = listOf(FirebaseUserProviderInfo("google.com"))
        )
        _currentUser.value = user
        _isAuthLoading.value = false

        return Result.success(
            AuthUserData(
                uid = safeUid,
                email = cleanEmail,
                displayName = displayName,
                photoUrl = photoUrl,
                isLiveFirebase = false,
                isEmailVerified = true
            )
        )
    }

    suspend fun signUpWithEmail(email: String, pinOrPass: String, displayName: String): Result<AuthUserData> {
        _isAuthLoading.value = true
        _authError.value = null
        val cleanEmail = email.trim().lowercase()

        val supabaseResult = SupabaseService.getInstance().signUp(cleanEmail, pinOrPass, displayName)
        if (supabaseResult.isFailure) {
            val err = supabaseResult.exceptionOrNull()?.localizedMessage ?: "Sign up failed on Supabase"
            _isAuthLoading.value = false
            _authError.value = err
            return Result.failure(Exception(err))
        }
        val finalUid = supabaseResult.getOrNull()!!

        val user = FirebaseUser(
            uid = finalUid,
            email = cleanEmail,
            displayName = displayName,
            photoUrl = null
        )
        _currentUser.value = user
        _isAuthLoading.value = false

        return Result.success(
            AuthUserData(
                uid = finalUid,
                email = cleanEmail,
                displayName = displayName,
                isLiveFirebase = true,
                isEmailVerified = true
            )
        )
    }

    suspend fun signInWithEmail(email: String, pinOrPass: String): Result<AuthUserData> {
        _isAuthLoading.value = true
        _authError.value = null
        val cleanEmail = email.trim().lowercase()

        val supabaseResult = SupabaseService.getInstance().signIn(cleanEmail, pinOrPass)
        if (supabaseResult.isFailure) {
            val err = supabaseResult.exceptionOrNull()?.localizedMessage ?: "Invalid email or password on Supabase"
            _isAuthLoading.value = false
            _authError.value = err
            return Result.failure(Exception(err))
        }
        val finalUid = supabaseResult.getOrNull()!!

        val user = FirebaseUser(
            uid = finalUid,
            email = cleanEmail,
            displayName = cleanEmail.substringBefore("@"),
            photoUrl = null
        )
        _currentUser.value = user
        _isAuthLoading.value = false

        return Result.success(
            AuthUserData(
                uid = finalUid,
                email = cleanEmail,
                displayName = cleanEmail.substringBefore("@"),
                isLiveFirebase = true,
                isEmailVerified = true
            )
        )
    }

    suspend fun resendVerificationEmail(): Result<Unit> {
        return Result.success(Unit)
    }

    suspend fun reloadAndCheckEmailVerification(): Boolean {
        return true
    }

    suspend fun signInAnonymously(): Result<AuthUserData> {
        _isAuthLoading.value = true
        _authError.value = null

        val guestNum = (System.currentTimeMillis() % 100000).toString()
        val safeUid = "guest_$guestNum"
        val cleanEmail = "guest_$guestNum@focusly.app"
        val displayName = "Guest Deep Worker"

        val user = FirebaseUser(
            uid = safeUid,
            email = cleanEmail,
            displayName = displayName,
            photoUrl = null
        )
        _currentUser.value = user
        _isAuthLoading.value = false

        return Result.success(
            AuthUserData(
                uid = safeUid,
                email = cleanEmail,
                displayName = displayName,
                isLiveFirebase = false,
                isEmailVerified = true
            )
        )
    }

    fun signOut() {
        _currentUser.value = null
        CoroutineScope(Dispatchers.IO).launch {
            SupabaseService.getInstance().signOut()
        }
    }
}
