package app.tetsukay.deviceowner.kiosk

/**
 * 対象アプリの起動失敗・即終了が続いたときに、次の起動までの待ち時間を伸ばす。
 *
 * - 起動後 [quickExitWindowMs] 以内にランチャーへ戻ってきたら「即終了」として失敗に数える
 * - それより長く対象アプリが動いていたら失敗回数をリセットする
 */
class LaunchBackoff(
    private val stepsMs: List<Long> = DEFAULT_STEPS_MS,
    private val quickExitWindowMs: Long = DEFAULT_QUICK_EXIT_WINDOW_MS,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    init {
        require(stepsMs.isNotEmpty())
    }

    var consecutiveFailures: Int = 0
        private set

    private var lastLaunchAtMs: Long? = null

    /** 次の起動までの待ち時間（カウントダウン長）。 */
    fun nextDelayMs(): Long = stepsMs[consecutiveFailures.coerceAtMost(stepsMs.lastIndex)]

    /** 対象アプリの startActivity に成功したとき。 */
    fun onLaunched() {
        lastLaunchAtMs = clock()
    }

    /** startActivity が例外などで失敗したとき。 */
    fun onLaunchFailed() {
        lastLaunchAtMs = null
        consecutiveFailures++
    }

    /** ランチャーが前面に戻ってきたとき（onResume）。 */
    fun onReturnedToLauncher() {
        val launchedAt = lastLaunchAtMs ?: return
        lastLaunchAtMs = null
        if (clock() - launchedAt < quickExitWindowMs) {
            consecutiveFailures++
        } else {
            consecutiveFailures = 0
        }
    }

    fun reset() {
        lastLaunchAtMs = null
        consecutiveFailures = 0
    }

    companion object {
        val DEFAULT_STEPS_MS = listOf(3_000L, 10_000L, 30_000L, 60_000L)
        const val DEFAULT_QUICK_EXIT_WINDOW_MS = 15_000L
    }
}
