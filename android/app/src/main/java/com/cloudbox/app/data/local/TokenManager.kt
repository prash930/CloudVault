package com.cloudbox.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

object TokenManager {
    private const val PREF_NAME = "cloudbox_secure_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_display_name"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_AUTO_BACKUP = "auto_backup_enabled"
    private const val KEY_HAS_AVATAR_1 = "has_avatar_1"
    private const val KEY_HAS_AVATAR_2 = "has_avatar_2"
    private const val KEY_LOCAL_AVATAR_1 = "local_avatar_1_path"
    private const val KEY_ONBOARDING = "onboarding_seen"
    private const val KEY_BACKED_UP_MEDIA = "backed_up_media_uris"
    
    private var prefs: SharedPreferences? = null
    
    fun init(context: Context) {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
            
        prefs = EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }
    
    fun saveToken(token: String) {
        prefs?.edit()?.putString(KEY_TOKEN, token)?.apply()
    }
    
    fun getToken(): String? {
        return prefs?.getString(KEY_TOKEN, null)
    }
    
    fun saveUserInfo(email: String, name: String, role: String) {
        prefs?.edit()?.apply {
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, name)
            putString(KEY_USER_ROLE, role)
        }?.apply()
    }
    
    fun getUserEmail(): String? = prefs?.getString(KEY_USER_EMAIL, null)
    fun getUserName(): String? = prefs?.getString(KEY_USER_NAME, null)
    fun getUserRole(): String? = prefs?.getString(KEY_USER_ROLE, null)

    fun saveAvatarFlags(has1: Boolean, has2: Boolean) {
        prefs?.edit()?.putBoolean(KEY_HAS_AVATAR_1, has1)?.putBoolean(KEY_HAS_AVATAR_2, has2)?.apply()
    }

    fun hasAvatar1(): Boolean = prefs?.getBoolean(KEY_HAS_AVATAR_1, false) ?: false
    fun hasAvatar2(): Boolean = prefs?.getBoolean(KEY_HAS_AVATAR_2, false) ?: false

    fun saveLocalAvatarPath(path: String) {
        prefs?.edit()?.putString(KEY_LOCAL_AVATAR_1, path)?.apply()
    }

    fun getLocalAvatarPath(): String? {
        return prefs?.getString(KEY_LOCAL_AVATAR_1, null)
    }

    fun markOnboardingSeen() {
        prefs?.edit()?.putBoolean(KEY_ONBOARDING, true)?.apply()
    }

    fun hasSeenOnboarding(): Boolean = prefs?.getBoolean(KEY_ONBOARDING, false) ?: false
    
    fun clearAll() {
        prefs?.edit()?.clear()?.apply()
    }
    
    fun isLoggedIn(): Boolean {
        return getToken() != null
    }

    fun isAutoBackupEnabled(): Boolean {
        return prefs?.getBoolean(KEY_AUTO_BACKUP, true) ?: true
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_BACKUP, enabled)?.apply()
    }

    fun clearBackedUpMedia() {
        prefs?.edit()?.putStringSet(KEY_BACKED_UP_MEDIA, emptySet())?.apply()
    }

    fun getBackedUpMediaUris(): Set<String> {
        return prefs?.getStringSet(KEY_BACKED_UP_MEDIA, emptySet()) ?: emptySet()
    }

    fun addBackedUpMediaUris(uris: Collection<String>) {
        if (uris.isEmpty()) return
        val current = getBackedUpMediaUris().toMutableSet()
        current.addAll(uris)
        prefs?.edit()?.putStringSet(KEY_BACKED_UP_MEDIA, current)?.apply()
    }
}
