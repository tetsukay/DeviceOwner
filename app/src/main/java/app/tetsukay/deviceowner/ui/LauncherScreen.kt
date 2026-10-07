package app.tetsukay.deviceowner.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

enum class LauncherMode { Loading, NotDeviceOwner, NeedsSetup, KioskDisabled, Kiosk }

data class LauncherUiState(
    val mode: LauncherMode = LauncherMode.Loading,
    val hasPin: Boolean = false,
    val targetLabel: String? = null,
    val countdownSec: Int? = null,
    val consecutiveFailures: Int = 0,
    val message: String? = null,
    val policyErrors: List<String> = emptyList(),
    val showPinDialog: Boolean = false,
    val pinError: String? = null,
)

const val SET_DEVICE_OWNER_COMMAND =
    "adb shell dpm set-device-owner app.tetsukay.deviceowner/.AdminReceiver"

@Composable
fun LauncherScreen(
    state: LauncherUiState,
    onSecretLongPress: () -> Unit,
    onOpenAdmin: () -> Unit,
    onLaunchTargetNow: () -> Unit,
    onCopyCommand: () -> Unit,
    onPinSubmit: (String) -> Unit,
    onPinDismiss: () -> Unit,
) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (state.mode) {
                    LauncherMode.Loading -> CircularProgressIndicator()
                    LauncherMode.NotDeviceOwner -> NotDeviceOwnerContent(onCopyCommand, onOpenAdmin)
                    LauncherMode.NeedsSetup -> {
                        Text("初期設定が必要です", style = MaterialTheme.typography.headlineSmall)
                        Text("管理メニューで PIN を設定してください。")
                        Button(onClick = onOpenAdmin) { Text("管理メニューを開く") }
                    }
                    LauncherMode.KioskDisabled -> {
                        Text("キオスクは無効です", style = MaterialTheme.typography.headlineSmall)
                        state.targetLabel?.let { Text("対象アプリ: $it") }
                        Button(onClick = onOpenAdmin) { Text("管理メニューを開く") }
                        if (state.targetLabel != null) {
                            OutlinedButton(onClick = onLaunchTargetNow) { Text("対象アプリを起動") }
                        }
                    }
                    LauncherMode.Kiosk -> KioskContent(state)
                }

                state.message?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                if (state.policyErrors.isNotEmpty()) {
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("ポリシー適用エラー", style = MaterialTheme.typography.titleSmall)
                            state.policyErrors.forEach {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // 右上の隠しエリア。ロングタップでカウントダウンを止めて PIN 入力へ。
            SecretCorner(
                onTriggered = onSecretLongPress,
                modifier = Modifier.align(Alignment.TopEnd).size(120.dp),
            )
        }
    }

    if (state.showPinDialog) {
        PinDialog(error = state.pinError, onSubmit = onPinSubmit, onDismiss = onPinDismiss)
    }
}

@Composable
private fun KioskContent(state: LauncherUiState) {
    val label = state.targetLabel
    when {
        label == null -> Text("キオスク待機中", style = MaterialTheme.typography.headlineSmall)
        state.countdownSec != null -> {
            Text(label, style = MaterialTheme.typography.headlineSmall)
            Text("${state.countdownSec}", style = MaterialTheme.typography.displayLarge)
            Text("秒後に起動します")
            if (state.consecutiveFailures > 0) {
                Text(
                    "起動失敗・即終了が ${state.consecutiveFailures} 回続いたため待ち時間を延ばしています",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        else -> Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun NotDeviceOwnerContent(onCopyCommand: () -> Unit, onOpenAdmin: () -> Unit) {
    Text("Device Owner が未設定です", style = MaterialTheme.typography.headlineSmall)
    Text(
        "1. 端末を初期化し、Google アカウントを追加せずにセットアップを完了する\n" +
            "2. 開発者オプションで USB デバッグを有効にする\n" +
            "3. PC から次のコマンドを実行する",
    )
    Card {
        SelectionContainer {
            Text(
                SET_DEVICE_OWNER_COMMAND,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
    Button(onClick = onCopyCommand) { Text("コマンドをコピー") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onOpenAdmin) { Text("管理メニューを開く") }
}

@Composable
private fun SecretCorner(onTriggered: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnTriggered by rememberUpdatedState(onTriggered)
    Box(
        modifier.pointerInput(Unit) {
            // 判定時間は端末のロングタップ設定に従う（デフォルト 400ms）
            detectTapGestures(onLongPress = { currentOnTriggered() })
        },
    )
}
