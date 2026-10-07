package app.tetsukay.deviceowner.kiosk

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityOptions
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import app.tetsukay.deviceowner.AdminReceiver
import app.tetsukay.deviceowner.LauncherActivity
import app.tetsukay.deviceowner.TAG

/**
 * DevicePolicyManager と Lock Task の操作をここに集約する。
 *
 * 各操作は冪等で、失敗しても例外を投げずにエラーメッセージのリストを返す（空なら成功）。
 */
class KioskPolicyManager(context: Context) {

    private val appContext = context.applicationContext
    private val dpm = appContext.getSystemService(DevicePolicyManager::class.java)
    private val am = appContext.getSystemService(ActivityManager::class.java)
    private val packageName = appContext.packageName
    private val admin: ComponentName = AdminReceiver.componentName(appContext)
    private val home = ComponentName(appContext, LauncherActivity::class.java)

    fun isDeviceOwner(): Boolean = dpm.isDeviceOwnerApp(packageName)

    fun lockTaskModeState(): Int = am.lockTaskModeState

    fun isInLockTask(): Boolean = lockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE

    /** キオスク用のポリシーをすべて適用する。 */
    fun applyKioskPolicies(lockTaskPackages: List<String>): List<String> {
        if (!isDeviceOwner()) return listOf(NOT_DEVICE_OWNER)
        return buildList {
            attempt("setLockTaskPackages") {
                if (dpm.getLockTaskPackages(admin).toSet() != lockTaskPackages.toSet()) {
                    dpm.setLockTaskPackages(admin, lockTaskPackages.toTypedArray())
                }
            }
            attempt("setLockTaskFeatures") {
                if (dpm.getLockTaskFeatures(admin) != DevicePolicyManager.LOCK_TASK_FEATURE_NONE) {
                    dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
                }
            }
            attempt("addPersistentPreferredActivity") {
                // 同じ filter の重複登録を避けるため、一度クリアしてから登録する
                dpm.clearPackagePersistentPreferredActivities(admin, packageName)
                dpm.addPersistentPreferredActivity(admin, homeIntentFilter(), home)
            }
            attempt("setKeyguardDisabled") {
                check(dpm.setKeyguardDisabled(admin, true)) {
                    "false が返りました（画面ロックに PIN/パスワードが設定されている可能性があります）"
                }
            }
            attempt("setStatusBarDisabled") {
                check(dpm.setStatusBarDisabled(admin, true)) {
                    "false が返りました（画面ロックに PIN/パスワードが設定されている可能性があります）"
                }
            }
            attempt("setGlobalSetting(STAY_ON_WHILE_PLUGGED_IN)") {
                dpm.setGlobalSetting(
                    admin,
                    Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                    STAY_ON_ALL_SOURCES.toString(),
                )
            }
        }
    }

    /** キオスク用のポリシーを元に戻す。 */
    fun clearKioskPolicies(): List<String> {
        if (!isDeviceOwner()) return listOf(NOT_DEVICE_OWNER)
        return buildList {
            attempt("setLockTaskPackages(empty)") { dpm.setLockTaskPackages(admin, emptyArray()) }
            attempt("clearPackagePersistentPreferredActivities") {
                dpm.clearPackagePersistentPreferredActivities(admin, packageName)
            }
            attempt("setKeyguardDisabled(false)") { dpm.setKeyguardDisabled(admin, false) }
            attempt("setStatusBarDisabled(false)") { dpm.setStatusBarDisabled(admin, false) }
            attempt("setGlobalSetting(STAY_ON_WHILE_PLUGGED_IN=0)") {
                dpm.setGlobalSetting(admin, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "0")
            }
        }
    }

    /** メンテナンスモード用。ステータスバーだけを一時的に切り替える。 */
    fun setStatusBarDisabled(disabled: Boolean): List<String> {
        if (!isDeviceOwner()) return listOf(NOT_DEVICE_OWNER)
        return buildList { attempt("setStatusBarDisabled($disabled)") { dpm.setStatusBarDisabled(admin, disabled) } }
    }

    /** ポリシーをすべて戻してから Device Owner を解除する。 */
    fun clearDeviceOwner(): List<String> {
        if (!isDeviceOwner()) return listOf(NOT_DEVICE_OWNER)
        val errors = clearKioskPolicies().toMutableList()
        errors.attempt("clearDeviceOwnerApp") {
            @Suppress("DEPRECATION")
            dpm.clearDeviceOwnerApp(packageName)
        }
        return errors
    }

    /** 呼び出し元 Activity のタスクを Lock Task にする。既に Lock Task 中なら何もしない。 */
    fun startLockTask(activity: Activity): List<String> = buildList {
        if (isInLockTask()) return@buildList
        attempt("startLockTask") {
            check(dpm.isLockTaskPermitted(packageName)) { "自パッケージが Lock Task 許可リストにありません" }
            activity.startLockTask()
        }
    }

    fun stopLockTask(activity: Activity): List<String> = buildList {
        if (!isInLockTask()) return@buildList
        attempt("stopLockTask") { activity.stopLockTask() }
    }

    /** 対象アプリを Lock Task のまま起動するための ActivityOptions。 */
    fun lockTaskLaunchOptions(): Bundle =
        ActivityOptions.makeBasic().setLockTaskEnabled(true).toBundle()

    private fun homeIntentFilter() = IntentFilter(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    private inline fun MutableList<String>.attempt(name: String, block: () -> Unit) {
        try {
            block()
            Log.d(TAG, "$name: ok")
        } catch (e: Exception) {
            Log.w(TAG, "$name failed", e)
            add("$name: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    companion object {
        private const val NOT_DEVICE_OWNER = "Device Owner ではないためポリシーを操作できません"
        private const val STAY_ON_ALL_SOURCES = BatteryManager.BATTERY_PLUGGED_AC or
            BatteryManager.BATTERY_PLUGGED_USB or
            BatteryManager.BATTERY_PLUGGED_WIRELESS

        fun lockTaskStateLabel(state: Int): String = when (state) {
            ActivityManager.LOCK_TASK_MODE_NONE -> "なし"
            ActivityManager.LOCK_TASK_MODE_LOCKED -> "LOCKED（キオスク）"
            ActivityManager.LOCK_TASK_MODE_PINNED -> "PINNED（画面固定）"
            else -> "不明 ($state)"
        }
    }
}
