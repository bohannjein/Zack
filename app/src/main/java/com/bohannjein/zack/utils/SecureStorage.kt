package com.bohannjein.zack.utils

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Secure storage for sensitive data like passwords
 * Uses EncryptedSharedPreferences with AES256-GCM encryption
 */
class SecureStorage(context: Context) {
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val preferences = EncryptedSharedPreferences.create(
        "secure_prefs",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    /**
     * Save password for a server
     * @param serverId Server ID
     * @param password Password to encrypt and store
     */
    fun savePassword(serverId: Long, password: String) {
        preferences.edit().putString("pwd_$serverId", password).apply()
    }
    
    /**
     * Retrieve password for a server
     * @param serverId Server ID
     * @return Decrypted password or null if not found
     */
    fun getPassword(serverId: Long): String? {
        return preferences.getString("pwd_$serverId", null)
    }
}
