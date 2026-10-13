package io.github.hziqzmin.wildguardai.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "wildguard_user_prefs")

data class MedicalId(
    val bloodType: String = "O+",
    val allergies: String = "None",
    val emergencyContact: String = "+60 12-345 6789",
    val medicalNotes: String = "No known chronic conditions"
)

data class UserSession(
    val email: String = "",
    val displayName: String = "hziqzmin",
    val planName: String = "Premium",
    val isLoggedIn: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    companion object {
        private val KEY_EMAIL = stringPreferencesKey("user_email")
        private val KEY_DISPLAY_NAME = stringPreferencesKey("user_display_name")
        private val KEY_PLAN_NAME = stringPreferencesKey("user_plan_name")
        private val KEY_IS_LOGGED_IN = booleanPreferencesKey("is_logged_in")

        private val KEY_BLOOD_TYPE = stringPreferencesKey("med_blood_type")
        private val KEY_ALLERGIES = stringPreferencesKey("med_allergies")
        private val KEY_EMERGENCY_CONTACT = stringPreferencesKey("med_emergency_contact")
        private val KEY_MED_NOTES = stringPreferencesKey("med_notes")
    }

    val userSessionFlow: Flow<UserSession> = context.dataStore.data.map { prefs ->
        UserSession(
            email = prefs[KEY_EMAIL] ?: "",
            displayName = prefs[KEY_DISPLAY_NAME] ?: "hziqzmin",
            planName = prefs[KEY_PLAN_NAME] ?: "Premium",
            isLoggedIn = prefs[KEY_IS_LOGGED_IN] ?: false
        )
    }

    val medicalIdFlow: Flow<MedicalId> = context.dataStore.data.map { prefs ->
        MedicalId(
            bloodType = prefs[KEY_BLOOD_TYPE] ?: "O+",
            allergies = prefs[KEY_ALLERGIES] ?: "None",
            emergencyContact = prefs[KEY_EMERGENCY_CONTACT] ?: "+60 12-345 6789",
            medicalNotes = prefs[KEY_MED_NOTES] ?: "No known chronic conditions"
        )
    }

    suspend fun saveSession(email: String, displayName: String, planName: String = "Premium") {
        context.dataStore.edit { prefs ->
            prefs[KEY_EMAIL] = email
            prefs[KEY_DISPLAY_NAME] = displayName
            prefs[KEY_PLAN_NAME] = planName
            prefs[KEY_IS_LOGGED_IN] = true
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { prefs ->
            prefs[KEY_EMAIL] = ""
            prefs[KEY_IS_LOGGED_IN] = false
        }
    }

    suspend fun saveMedicalId(bloodType: String, allergies: String, emergencyContact: String, notes: String = "") {
        context.dataStore.edit { prefs ->
            prefs[KEY_BLOOD_TYPE] = bloodType
            prefs[KEY_ALLERGIES] = allergies
            prefs[KEY_EMERGENCY_CONTACT] = emergencyContact
            prefs[KEY_MED_NOTES] = notes
        }
    }
}
