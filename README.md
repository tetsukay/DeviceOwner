# Kiosk DO

使っていない Android 端末を、特定アプリ（オーディオシステム操作アプリなど）の専用機にするための Device Owner 兼ホームアプリです。
指定した対象アプリを Lock Task Mode で常時表示します。自分専用で、Play Store には公開しません。

- applicationId: `app.tetsukay.deviceowner`
- minSdk 28 / targetSdk 36
- Kotlin + Jetpack Compose

## ビルド

```sh
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # ロジック部分の単体テスト
```

debug ビルドはリポジトリ直下の `debug.keystore`（alias `androiddebugkey`、パスワードはいずれも `android`）で署名します。どの PC でビルドしても同じ署名になるので、別の PC でビルドした APK でもアンインストールせずに `adb install -r` で上書きできます。Device Owner を外さずにアプリを更新するときに必要です。

debug ビルドだけ `android:testOnly="true"` が付きます。そのため `adb install -t` が必要で、`adb shell dpm remove-active-admin` で Device Owner を外せます。

## セットアップ手順

1. **端末を初期化し、アカウントを追加せずにセットアップを完了する**
   セットアップウィザードで Google アカウントのログインはスキップします。アカウントがあると `dpm set-device-owner` が失敗します。
2. **開発者オプションと USB デバッグを有効にする**
   設定 → デバイス情報 → ビルド番号を 7 回タップし、開発者オプションで「USB デバッグ」をオンにします。
3. **APK をインストールする**
   ```sh
   adb install -t app-debug.apk
   ```
4. **Device Owner に設定する**
   ```sh
   adb shell dpm set-device-owner app.tetsukay.deviceowner/.AdminReceiver
   ```
   ホームアプリの選択を求められたら本アプリ（Kiosk DO）を選びます。この時点ではまだキオスクは無効です。
5. **Google アカウントを追加し、Play Store から対象アプリをインストールする**
   Device Owner 設定後であればアカウントを追加しても問題ありません。
6. **対象アプリの初期設定を済ませる**
   ペアリング、ログイン、権限の許可などは、キオスクを有効にする前に済ませておきます。キオスク中は権限ダイアログや外部ブラウザでのログインが行えないことがあります。
7. **本アプリで PIN と対象アプリを設定し、キオスクを有効にする**
   - Kiosk DO を開き「管理メニューを開く」→ PIN（数字 4〜12 桁）を設定
   - 「対象アプリ」で一覧から選択
   - 必要なら「追加の許可パッケージ」を登録
   - 「キオスクを有効にする」をオンにして「ランチャーに戻る」
   - 3 秒のカウントダウンの後、対象アプリが Lock Task で起動します

## 使い方

- 対象アプリが終了してホームに戻るたびに、3 秒のカウントダウン後に再起動します。
- **カウントダウン中に画面右上をロングタップ**すると、カウントダウンが止まって PIN 入力ダイアログが出ます。ロングタップの判定時間は端末の設定に従います（デフォルト 400ms）。キャンセルするとカウントダウンを最初からやり直します。認証に成功すると管理メニューが開き、自動起動は止まります。
- 管理メニューでできること
  - 状態表示（Device Owner かどうか、Lock Task の状態、対象パッケージ、アプリのバージョン）
  - 対象アプリの選択、追加許可パッケージの編集、PIN の変更
  - メンテナンスモード: Lock Task とステータスバーの無効化を一時的に解除し、Wi-Fi / Bluetooth などの設定画面を開けるようにします。ランチャーに戻るとキオスクを再開します。
  - キオスクの有効/無効
  - Device Owner の解除

## 解除方法

- **管理メニューから**: 「Device Owner を解除」→ 確認ダイアログで「解除する」。Lock Task 許可リスト、恒久ホーム設定、keyguard、ステータスバー、画面常時点灯の設定を戻してから `clearDeviceOwnerApp` を呼びます。その後は通常どおりアンインストールできます。
- **debug ビルドなら adb から**:
  ```sh
  adb shell dpm remove-active-admin app.tetsukay.deviceowner/.AdminReceiver
  ```
  この方法ではアプリ側の後始末が走らず、keyguard の無効化や恒久ホーム設定が残る可能性があります。先に管理メニューでキオスクを無効にしてから実行してください。

## キオスク有効時に適用するポリシー

LauncherActivity の `onResume` のたびに、以下を冪等に適用します。失敗した項目は Logcat（タグ `KioskDO`）に出し、画面にも表示します。

| 項目 | 内容 |
|---|---|
| `setLockTaskPackages` | 自パッケージ + 対象アプリ + 追加許可パッケージ |
| `setLockTaskFeatures` | `LOCK_TASK_FEATURE_NONE`（ホーム・履歴・通知・電源メニューなどを無効化） |
| `addPersistentPreferredActivity` | 本アプリを恒久ホームに設定（再起動後の復帰に使う） |
| `setKeyguardDisabled(true)` | ロック画面を出さない |
| `setStatusBarDisabled(true)` | ステータスバーを引き出せないようにする |
| `STAY_ON_WHILE_PLUGGED_IN` | AC / USB / ワイヤレス給電中は画面を消さない |
| `startLockTask()` | 本アプリ自身を Lock Task に入れる |

対象アプリは `ActivityOptions.makeBasic().setLockTaskEnabled(true)` を付けて起動します。

## 既知の制約

- **対象アプリが外部アプリ（ブラウザ、認証アプリ、共有先など）を開く場合**、そのパッケージを「追加の許可パッケージ」に入れないと起動がブロックされます。どのパッケージが必要かは `adb logcat` で確認してください。
- `LOCK_TASK_FEATURE_NONE` のため、キオスク中は通知・電源メニュー・ホーム・履歴ボタンが使えません。電源ボタン長押しのメニューも出ません。
- 画面ロックに PIN / パスワード / パターンを設定していると `setKeyguardDisabled` / `setStatusBarDisabled` が失敗します。ロックは「なし」か「スワイプ」にしてください。
- 再起動後の復帰は恒久ホーム設定だけで行っています。`BOOT_COMPLETED` からの Activity 起動は使っていません（Android 10 以降のバックグラウンド起動制限のため）。
- 起動失敗や即終了（起動後 15 秒以内にホームへ戻る）が続くと、カウントダウンを 3 秒 → 10 秒 → 30 秒 → 60 秒と延ばします。対象アプリが 15 秒以上動けばリセットされます。
- 対象アプリが未設定・未インストールの場合はエラー表示だけで、自動起動はしません。右上をロングタップすると管理メニューに入れます。
- PIN の誤入力回数に制限はありません。

## 前提・仮定

- 「追加の許可パッケージ」は一覧からの選択に加えて、パッケージ名の直接入力でも追加できるようにしました。ランチャーに出ないアプリ（認証用のサービスなど）も許可できるようにするためです。
- LauncherActivity には `HOME` に加えて `LAUNCHER` の intent-filter も付けています。Device Owner 設定前でもアプリ一覧から開けるようにするためです。
- PIN 未設定のとき（初回）は、管理メニューを PIN なしで開けます。管理メニューでは PIN を設定するまで他の操作を表示しません。
- キオスクを有効にするには Device Owner・PIN・対象アプリの 3 つがそろっている必要があります。
- キオスク無効化と Device Owner 解除では、仕様で挙げられた keyguard・ステータスバー・恒久ホームに加えて、Lock Task 許可リストを空にし、`STAY_ON_WHILE_PLUGGED_IN` を 0 に戻します。
- 恒久ホーム設定の重複登録を避けるため、`addPersistentPreferredActivity` の前に毎回 `clearPackagePersistentPreferredActivities` を呼んでいます。
- メンテナンスモードでは Lock Task の解除に加えて、ステータスバーも一時的に有効にします（クイック設定を使えるようにするため）。ランチャーに戻ると再び無効になります。
- バックオフの状態はプロセス内のメモリに持っています。プロセスが再起動すると 3 秒に戻ります。
- 「即終了」の判定時間は 15 秒としました。
- PIN は SHA-256 を 1 回かけたものです（salt 16 バイト）。自分専用端末で、端末内の DataStore への攻撃までは想定していません。
- release ビルドの署名設定は入れていません。release を使う場合は各自で `signingConfigs` を設定してください。
- compileSdk / targetSdk は 36 にしています（ローカルにある最新の安定版プラットフォーム）。
