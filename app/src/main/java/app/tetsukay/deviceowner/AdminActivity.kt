package app.tetsukay.deviceowner

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.tetsukay.deviceowner.data.SettingsRepository
import app.tetsukay.deviceowner.kiosk.AppCatalog
import app.tetsukay.deviceowner.kiosk.KioskPolicyManager
import app.tetsukay.deviceowner.ui.AdminActions
import app.tetsukay.deviceowner.ui.AdminScreen
import app.tetsukay.deviceowner.ui.AdminStatus
import app.tetsukay.deviceowner.ui.KioskTheme
import kotlinx.coroutines.launch

/** 管理メニュー。LauncherActivity で PIN 認証した後にだけ開く（exported=false）。 */
class AdminActivity : ComponentActivity() {

    private lateinit var policy: KioskPolicyManager
    private lateinit var repo: SettingsRepository
    private lateinit var apps: AppCatalog

    private var status by mutableStateOf(AdminStatus())
    private var maintenance by mutableStateOf(false)
    private var messages by mutableStateOf(emptyList<String>())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        policy = KioskPolicyManager(this)
        repo = SettingsRepository(this)
        apps = AppCatalog(this)

        val actions = AdminActions(
            onSetPin = ::setPin,
            loadApps = apps::launchableApps,
            labelOf = apps::label,
            onSelectTarget = { pkg -> lifecycleScope.launch { repo.setTargetPackage(pkg); showInfo("対象アプリを $pkg に設定しました") } },
            onAddExtra = { pkg -> lifecycleScope.launch { repo.addExtraPackage(pkg) } },
            onRemoveExtra = { pkg -> lifecycleScope.launch { repo.removeExtraPackage(pkg) } },
            onKioskEnabledChange = ::setKioskEnabled,
            onStartMaintenance = ::startMaintenance,
            onOpenWifiSettings = { openSettings(Settings.ACTION_WIFI_SETTINGS) },
            onOpenBluetoothSettings = { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
            onOpenSettings = { openSettings(Settings.ACTION_SETTINGS) },
            onBackToLauncher = ::finish,
            onClearDeviceOwner = ::clearDeviceOwner,
        )

        setContent {
            val settings by repo.settings.collectAsState(initial = null)
            KioskTheme {
                AdminScreen(
                    settings = settings,
                    status = status,
                    maintenance = maintenance,
                    messages = messages,
                    actions = actions,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        status = AdminStatus(
            isDeviceOwner = policy.isDeviceOwner(),
            lockTaskState = KioskPolicyManager.lockTaskStateLabel(policy.lockTaskModeState()),
            versionName = apps.versionName(),
        )
    }

    private fun showErrors(errors: List<String>) {
        messages = errors
        refreshStatus()
    }

    private fun showInfo(message: String) {
        messages = listOf(message)
        refreshStatus()
    }

    private fun setPin(pin: String) {
        lifecycleScope.launch {
            try {
                repo.setPin(pin)
                showInfo("PIN を保存しました")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save PIN", e)
                showErrors(listOf("PIN の保存に失敗しました: ${e.message}"))
            }
        }
    }

    private fun setKioskEnabled(enabled: Boolean) {
        lifecycleScope.launch {
            if (enabled) {
                val s = repo.current()
                if (!policy.isDeviceOwner() || s.targetPackage == null || !s.hasPin) {
                    showErrors(listOf("Device Owner・PIN・対象アプリがそろっていないため有効にできません"))
                    return@launch
                }
                repo.setKioskEnabled(true)
                showInfo("キオスクを有効にしました。ランチャーに戻ると開始します。")
            } else {
                val errors = policy.stopLockTask(this@AdminActivity) + policy.clearKioskPolicies()
                repo.setKioskEnabled(false)
                maintenance = false
                if (errors.isEmpty()) showInfo("キオスクを無効にし、ポリシーを元に戻しました") else showErrors(errors)
            }
        }
    }

    private fun startMaintenance() {
        val errors = policy.stopLockTask(this) + policy.setStatusBarDisabled(false)
        maintenance = true
        if (errors.isEmpty()) {
            showInfo("メンテナンスモード: Lock Task を解除しました。ランチャーに戻るとキオスクを再開します。")
        } else {
            showErrors(errors)
        }
    }

    private fun openSettings(action: String) {
        try {
            startActivity(Intent(action))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open $action", e)
            showErrors(listOf("設定画面を開けませんでした: ${e.message}"))
        }
    }

    private fun clearDeviceOwner() {
        lifecycleScope.launch {
            val errors = policy.stopLockTask(this@AdminActivity) + policy.clearDeviceOwner()
            repo.setKioskEnabled(false)
            maintenance = false
            if (errors.isEmpty()) {
                showInfo("Device Owner を解除しました。アプリはアンインストールできます。")
            } else {
                showErrors(errors)
            }
        }
    }
}
