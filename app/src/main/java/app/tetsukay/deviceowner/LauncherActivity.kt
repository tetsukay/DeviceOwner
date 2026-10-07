package app.tetsukay.deviceowner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.tetsukay.deviceowner.data.KioskSettings
import app.tetsukay.deviceowner.data.SettingsRepository
import app.tetsukay.deviceowner.kiosk.AppCatalog
import app.tetsukay.deviceowner.kiosk.KioskPolicyManager
import app.tetsukay.deviceowner.kiosk.LaunchBackoff
import app.tetsukay.deviceowner.ui.KioskTheme
import app.tetsukay.deviceowner.ui.LauncherMode
import app.tetsukay.deviceowner.ui.LauncherScreen
import app.tetsukay.deviceowner.ui.LauncherUiState
import app.tetsukay.deviceowner.ui.SET_DEVICE_OWNER_COMMAND
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ホームアプリ。キオスク有効時はポリシーを適用して Lock Task に入り、カウントダウン後に対象アプリを起動する。
 */
class LauncherActivity : ComponentActivity() {

    private lateinit var policy: KioskPolicyManager
    private lateinit var repo: SettingsRepository
    private lateinit var apps: AppCatalog

    private var uiState by mutableStateOf(LauncherUiState())
    private var flowJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        policy = KioskPolicyManager(this)
        repo = SettingsRepository(this)
        apps = AppCatalog(this)

        // ホームなので Back で終了させない
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = Unit
        })

        setContent {
            KioskTheme(dark = true) {
                LauncherScreen(
                    state = uiState,
                    onSecretLongPress = ::requestAdmin,
                    onOpenAdmin = ::requestAdmin,
                    onLaunchTargetNow = ::launchTargetWithoutLock,
                    onCopyCommand = ::copyCommand,
                    onPinSubmit = ::onPinSubmit,
                    onPinDismiss = ::onPinDismiss,
                )
            }
        }
    }

    // onCreate の後には必ず onResume が呼ばれるため、ポリシー適用は onResume に一本化している
    override fun onResume() {
        super.onResume()
        backoff.onReturnedToLauncher()
        startFlow()
    }

    override fun onPause() {
        super.onPause()
        flowJob?.cancel()
    }

    private fun startFlow() {
        flowJob?.cancel()
        uiState = uiState.copy(showPinDialog = false, pinError = null, countdownSec = null, message = null)
        flowJob = lifecycleScope.launch {
            val s = repo.current()
            val base = uiState.copy(
                hasPin = s.hasPin,
                targetLabel = s.targetPackage?.let { apps.label(it) ?: it },
                consecutiveFailures = backoff.consecutiveFailures,
                policyErrors = emptyList(),
            )
            uiState = when {
                !policy.isDeviceOwner() -> base.copy(mode = LauncherMode.NotDeviceOwner)
                !s.hasPin -> base.copy(mode = LauncherMode.NeedsSetup)
                !s.kioskEnabled -> base.copy(mode = LauncherMode.KioskDisabled)
                else -> base.copy(mode = LauncherMode.Kiosk)
            }
            if (uiState.mode == LauncherMode.Kiosk) runKiosk(s)
        }
    }

    private suspend fun runKiosk(s: KioskSettings) {
        val errors = policy.applyKioskPolicies(s.lockTaskPackages(packageName)) +
            policy.startLockTask(this)
        uiState = uiState.copy(policyErrors = errors)

        val target = s.targetPackage
        if (target == null) {
            uiState = uiState.copy(message = "対象アプリが未設定です。右上をロングタップして管理メニューから設定してください。")
            return
        }
        // 未インストールなら起動ループを起こさずエラー表示にとどめる
        if (apps.launchIntent(target) == null) {
            uiState = uiState.copy(message = "対象アプリ ($target) がインストールされていないか、起動できません。")
            return
        }

        while (true) {
            countdown(backoff.nextDelayMs())
            if (launchTarget(target)) return
            uiState = uiState.copy(consecutiveFailures = backoff.consecutiveFailures)
        }
    }

    private suspend fun countdown(totalMs: Long) {
        var remaining = ((totalMs + 999) / 1000).toInt()
        while (remaining > 0) {
            uiState = uiState.copy(countdownSec = remaining)
            delay(1_000)
            remaining--
        }
        uiState = uiState.copy(countdownSec = null)
    }

    private fun launchTarget(target: String): Boolean {
        val intent = apps.launchIntent(target)
        if (intent == null) {
            backoff.onLaunchFailed()
            uiState = uiState.copy(message = "対象アプリ ($target) の起動 Intent を取得できません。")
            return false
        }
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent, policy.lockTaskLaunchOptions())
            backoff.onLaunched()
            Log.i(TAG, "Launched $target")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $target", e)
            backoff.onLaunchFailed()
            uiState = uiState.copy(message = "対象アプリの起動に失敗しました: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** キオスク無効時に手動で対象アプリを開く（Lock Task なし）。 */
    private fun launchTargetWithoutLock() {
        lifecycleScope.launch {
            val target = repo.current().targetPackage ?: return@launch
            val intent = apps.launchIntent(target)
            if (intent == null) {
                uiState = uiState.copy(message = "対象アプリ ($target) を起動できません。")
                return@launch
            }
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch $target", e)
                uiState = uiState.copy(message = "起動に失敗しました: ${e.message}")
            }
        }
    }

    private fun requestAdmin() {
        flowJob?.cancel()
        if (uiState.hasPin) {
            uiState = uiState.copy(showPinDialog = true, pinError = null, countdownSec = null)
        } else {
            openAdmin()
        }
    }

    private fun onPinSubmit(pin: String) {
        lifecycleScope.launch {
            if (repo.verifyPin(pin)) {
                uiState = uiState.copy(showPinDialog = false, pinError = null)
                openAdmin()
            } else {
                Log.w(TAG, "PIN verification failed")
                uiState = uiState.copy(pinError = "PIN が違います")
            }
        }
    }

    private fun onPinDismiss() {
        // 自動起動を再開する
        startFlow()
    }

    private fun openAdmin() {
        backoff.reset()
        startActivity(Intent(this, AdminActivity::class.java))
    }

    private fun copyCommand() {
        getSystemService(ClipboardManager::class.java)
            .setPrimaryClip(ClipData.newPlainText("dpm command", SET_DEVICE_OWNER_COMMAND))
        Toast.makeText(this, "コピーしました", Toast.LENGTH_SHORT).show()
    }

    companion object {
        /** Activity の再生成をまたいで保持する（プロセスが生きている間）。 */
        private val backoff = LaunchBackoff()
    }
}
