package com.pingucodu.us.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the session token + logged-in username across app restarts. */
@Singleton
class TokenStore @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val USERNAME = stringPreferencesKey("username")
    }

    val token: Flow<String?> = dataStore.data.map { it[Keys.TOKEN] }
    val username: Flow<String?> = dataStore.data.map { it[Keys.USERNAME] }

    suspend fun save(token: String, username: String) {
        dataStore.edit {
            it[Keys.TOKEN] = token
            it[Keys.USERNAME] = username
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }
}
