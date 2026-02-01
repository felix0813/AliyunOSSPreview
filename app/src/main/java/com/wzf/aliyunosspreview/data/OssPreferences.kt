package com.wzf.aliyunosspreview.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.firstOrNull

private val Context.ossDataStore by preferencesDataStore(name = "oss_preferences")

class OssPreferences(private val context: Context) {
    private val accessKeyIdKey = stringPreferencesKey("access_key_id")
    private val accessKeySecretKey = stringPreferencesKey("access_key_secret")
    private val endpointKey = stringPreferencesKey("endpoint")
    private val regionKey = stringPreferencesKey("region")

    suspend fun loadCredentials(): OssCredentials? {
        val prefs = context.ossDataStore.data.firstOrNull() ?: return null
        val accessKeyId = prefs[accessKeyIdKey].orEmpty()
        val accessKeySecret = prefs[accessKeySecretKey].orEmpty()
        val endpoint = prefs[endpointKey].orEmpty()
        val region = prefs[regionKey].orEmpty()
        if (accessKeyId.isBlank() || accessKeySecret.isBlank() || endpoint.isBlank() || region.isBlank()) {
            return null
        }
        return OssCredentials(
            accessKeyId = accessKeyId,
            accessKeySecret = accessKeySecret,
            endpoint = endpoint,
            region = region,
        )
    }

    suspend fun saveCredentials(credentials: OssCredentials) {
        context.ossDataStore.edit { prefs ->
            prefs[accessKeyIdKey] = credentials.accessKeyId
            prefs[accessKeySecretKey] = credentials.accessKeySecret
            prefs[endpointKey] = credentials.endpoint
            prefs[regionKey] = credentials.region
        }
    }
}
