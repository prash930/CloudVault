package com.cloudbox.app.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

object TokenManager {
    private const val PREF_NAME = "cloudbox_secure_prefs"
    private const val KEY_TOKEN = "auth_token"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_display_name"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_AUTO_BACKUP = "auto_backup_enabled"
    
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
}
