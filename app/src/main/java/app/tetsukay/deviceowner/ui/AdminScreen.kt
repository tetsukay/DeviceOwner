package app.tetsukay.deviceowner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tetsukay.deviceowner.data.KioskSettings
import app.tetsukay.deviceowner.data.PinHasher
import app.tetsukay.deviceowner.kiosk.AppEntry

data class AdminStatus(
    val isDeviceOwner: Boolean = false,
    val lockTaskState: String = "",
    val versionName: String = "",
)

class AdminActions(
    val onSetPin: (String) -> Unit,
    val loadApps: () -> List<AppEntry>,
    val labelOf: (String) -> String?,
    val onSelectTarget: (String) -> Unit,
    val onAddExtra: (String) -> Unit,
    val onRemoveExtra: (String) -> Unit,
    val onKioskEnabledChange: (Boolean) -> Unit,
    val onStartMaintenance: () -> Unit,
    val onOpenWifiSettings: () -> Unit,
    val onOpenBluetoothSettings: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onBackToLauncher: () -> Unit,
    val onClearDeviceOwner: () -> Unit,
)

private val PACKAGE_NAME_REGEX = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+$")

@Composable
fun AdminScreen(
    settings: KioskSettings?,
    status: AdminStatus,
    maintenance: Boolean,
    messages: List<String>,
    actions: AdminActions,
) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("管理メニュー", style = MaterialTheme.typography.headlineMedium)

            if (messages.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(12.dp).fillMaxWidth()) {
                        messages.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            if (settings == null) return@Column

            StatusSection(settings, status, actions.labelOf)

            if (!settings.hasPin) {
                // 初回は PIN 設定を必須にし、他の操作は出さない
                PinSection(title = "PIN を設定してください（必須）", onSetPin = actions.onSetPin)
                return@Column
            }

            TargetSection(settings, actions)
            ExtraPackagesSection(settings, actions)
            KioskSection(settings, status, maintenance, actions)
            PinSection(title = "PIN の変更", onSetPin = actions.onSetPin)
            DeviceOwnerSection(status, actions)

            Button(onClick = actions.onBackToLauncher, modifier = Modifier.fillMaxWidth()) {
                Text("ランチャーに戻る")
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun StatusSection(settings: KioskSettings, status: AdminStatus, labelOf: (String) -> String?) {
    Section("現在の状態") {
        Text("Device Owner: ${if (status.isDeviceOwner) "はい" else "いいえ"}")
        Text("Lock Task: ${status.lockTaskState}")
        Text("キオスク: ${if (settings.kioskEnabled) "有効" else "無効"}")
        Text("対象パッケージ: ${settings.targetPackage?.let { "${labelOf(it) ?: "（未インストール）"} / $it" } ?: "未設定"}")
        Text("アプリのバージョン: ${status.versionName}")
    }
}

@Composable
private fun TargetSection(settings: KioskSettings, actions: AdminActions) {
    var picking by remember { mutableStateOf(false) }
    Section("対象アプリ") {
        Text(settings.targetPackage ?: "未設定")
        Button(onClick = { picking = true }) { Text("一覧から選択") }
    }
    if (picking) {
        AppPickerDialog(
            title = "対象アプリを選択",
            loadApps = actions.loadApps,
            onPick = { actions.onSelectTarget(it.packageName); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun ExtraPackagesSection(settings: KioskSettings, actions: AdminActions) {
    var input by remember { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    val valid = PACKAGE_NAME_REGEX.matches(input.trim())

    Section("追加の許可パッケージ") {
        Text(
            "対象アプリが内部で開く別アプリ（ブラウザ、認証アプリなど）を Lock Task 中に許可します。",
            style = MaterialTheme.typography.bodySmall,
        )
        if (settings.extraPackages.isEmpty()) Text("（なし）")
        settings.extraPackages.sorted().forEach { pkg ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(actions.labelOf(pkg) ?: "（未インストール）", style = MaterialTheme.typography.bodyMedium)
                    Text(pkg, style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { actions.onRemoveExtra(pkg) }) { Text("削除") }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("パッケージ名") },
                singleLine = true,
                isError = input.isNotBlank() && !valid,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { actions.onAddExtra(input.trim()); input = "" },
                enabled = valid,
            ) { Text("追加") }
        }
        OutlinedButton(onClick = { picking = true }) { Text("一覧から追加") }
    }
    if (picking) {
        AppPickerDialog(
            title = "許可するアプリを選択",
            loadApps = actions.loadApps,
            onPick = { actions.onAddExtra(it.packageName); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun KioskSection(
    settings: KioskSettings,
    status: AdminStatus,
    maintenance: Boolean,
    actions: AdminActions,
) {
    Section("キオスク") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("キオスクを有効にする", Modifier.weight(1f))
            Switch(
                checked = settings.kioskEnabled,
                onCheckedChange = actions.onKioskEnabledChange,
                enabled = status.isDeviceOwner && (settings.kioskEnabled || settings.targetPackage != null),
            )
        }
        if (!status.isDeviceOwner) {
            Text("Device Owner に設定するまで有効にできません。", style = MaterialTheme.typography.bodySmall)
        } else if (settings.targetPackage == null) {
            Text("先に対象アプリを選択してください。", style = MaterialTheme.typography.bodySmall)
        }

        if (settings.kioskEnabled) {
            Text("メンテナンスモード", style = MaterialTheme.typography.titleSmall)
            Text(
                "Lock Task を一時的に解除して設定アプリを開けるようにします。ランチャーに戻るとキオスクを再開します。",
                style = MaterialTheme.typography.bodySmall,
            )
            if (!maintenance) {
                OutlinedButton(onClick = actions.onStartMaintenance) { Text("メンテナンスモードに入る") }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = actions.onOpenWifiSettings) { Text("Wi-Fi") }
                    OutlinedButton(onClick = actions.onOpenBluetoothSettings) { Text("Bluetooth") }
                    OutlinedButton(onClick = actions.onOpenSettings) { Text("設定") }
                }
            }
        }
    }
}

@Composable
private fun PinSection(title: String, onSetPin: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val valid = PinHasher.isValidPin(pin) && pin == confirm

    Section(title) {
        Text(
            "数字 ${PinHasher.MIN_LENGTH}〜${PinHasher.MAX_LENGTH} 桁",
            style = MaterialTheme.typography.bodySmall,
        )
        PinInputField(value = pin, onValueChange = { pin = it }, label = "新しい PIN")
        PinInputField(value = confirm, onValueChange = { confirm = it }, label = "確認のためもう一度")
        if (confirm.isNotEmpty() && pin != confirm) {
            Text("PIN が一致しません", color = MaterialTheme.colorScheme.error)
        }
        Button(
            onClick = { onSetPin(pin); pin = ""; confirm = "" },
            enabled = valid,
        ) { Text("保存") }
    }
}

@Composable
private fun DeviceOwnerSection(status: AdminStatus, actions: AdminActions) {
    var confirming by remember { mutableStateOf(false) }
    Section("Device Owner") {
        Text(
            "ポリシーをすべて元に戻してから Device Owner を解除します。アカウント追加済みの端末では、再設定に端末の初期化が必要です。",
            style = MaterialTheme.typography.bodySmall,
        )
        Button(
            onClick = { confirming = true },
            enabled = status.isDeviceOwner,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) { Text("Device Owner を解除") }
    }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Device Owner を解除しますか？") },
            text = { Text("キオスクを無効にし、すべてのポリシーを戻してから解除します。アカウント追加済みの端末では、再度 Device Owner にするには初期化が必要です。") },
            confirmButton = {
                TextButton(onClick = { confirming = false; actions.onClearDeviceOwner() }) { Text("解除する") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("キャンセル") }
            },
        )
    }
}
