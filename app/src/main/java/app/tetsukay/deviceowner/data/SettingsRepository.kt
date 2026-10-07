package app.tetsukay.deviceowner.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.tetsukay.deviceowner.TAG
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

data class KioskSettings(
    val targetPackage: String? = null,
    val extraPackages: Set<String> = emptySet(),
    val pinHash: String? = null,
    val pinSalt: String? = null,
    val kioskEnabled: Boolean = false,
) {
    val hasPin: Boolean get() = pinHash != null && pinSalt != null

    /** Lock Task を許可するパッケージ（自パッケージ + 対象 + 追加許可）。 */
    fun lockTaskPackages(selfPackage: String): List<String> =
        (listOf(selfPackage) + listOfNotNull(targetPackage) + extraPackages.sorted()).distinct()
}

private val Context.kioskDataStore: DataStore<Preferences> by preferencesDataStore(name = "kiosk_settings")

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.kioskDataStore

    val settings: Flow<KioskSettings> = dataStore.data
        .catch { e ->
            if (e is IOException) {
                Log.e(TAG, "Failed to read settings", e)
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { p ->
            KioskSettings(
                targetPackage = p[KEY_TARGET],
                extraPackages = p[KEY_EXTRA] ?: emptySet(),
                pinHash = p[KEY_PIN_HASH],
                pinSalt = p[KEY_PIN_SALT],
                kioskEnabled = p[KEY_KIOSK_ENABLED] ?: false,
            )
        }

    suspend fun current(): KioskSettings = settings.first()

    suspend fun setTargetPackage(packageName: String?) {
        dataStore.edit { p ->
            if (packageName == null) p.remove(KEY_TARGET) else p[KEY_TARGET] = packageName
        }
    }

    suspend fun addExtraPackage(packageName: String) {
        dataStore.edit { p -> p[KEY_EXTRA] = (p[KEY_EXTRA] ?: emptySet()) + packageName }
    }

    suspend fun removeExtraPackage(packageName: String) {
        dataStore.edit { p -> p[KEY_EXTRA] = (p[KEY_EXTRA] ?: emptySet()) - packageName }
    }

    suspend fun setPin(pin: String) {
        require(PinHasher.isValidPin(pin)) { "invalid PIN format" }
        val hashed = PinHasher.hash(pin)
        dataStore.edit { p ->
            p[KEY_PIN_HASH] = hashed.hashHex
            p[KEY_PIN_SALT] = hashed.saltHex
        }
    }

    suspend fun verifyPin(pin: String): Boolean {
        val s = current()
        val hash = s.pinHash ?: return false
        val salt = s.pinSalt ?: return false
        return PinHasher.verify(pin, hash, salt)
    }

    suspend fun setKioskEnabled(enabled: Boolean) {
        dataStore.edit { p -> p[KEY_KIOSK_ENABLED] = enabled }
    }

    private companion object {
        val KEY_TARGET = stringPreferencesKey("target_package")
        val KEY_EXTRA = stringSetPreferencesKey("extra_packages")
        val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        val KEY_PIN_SALT = stringPreferencesKey("pin_salt")
        val KEY_KIOSK_ENABLED = booleanPreferencesKey("kiosk_enabled")
    }
}
