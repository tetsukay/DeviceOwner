package app.tetsukay.deviceowner.kiosk

import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LaunchBackoffTest {

    private var now = 0L
    private lateinit var backoff: LaunchBackoff

    @Before
    fun setUp() {
        now = 1_000_000L
        backoff = LaunchBackoff(clock = { now })
    }

    @Test
    fun initialDelayIsThreeSeconds() {
        assertEquals(3_000L, backoff.nextDelayMs())
    }

    @Test
    fun quickExitsIncreaseDelayUpToMaximum() {
        val expected = listOf(10_000L, 30_000L, 60_000L, 60_000L, 60_000L)
        for (delay in expected) {
            backoff.onLaunched()
            now += 2_000
            backoff.onReturnedToLauncher()
            assertEquals(delay, backoff.nextDelayMs())
        }
        assertEquals(5, backoff.consecutiveFailures)
    }

    @Test
    fun launchFailuresIncreaseDelay() {
        backoff.onLaunchFailed()
        assertEquals(10_000L, backoff.nextDelayMs())
        backoff.onLaunchFailed()
        assertEquals(30_000L, backoff.nextDelayMs())
    }

    @Test
    fun longRunResetsFailures() {
        backoff.onLaunchFailed()
        backoff.onLaunchFailed()
        backoff.onLaunched()
        now += LaunchBackoff.DEFAULT_QUICK_EXIT_WINDOW_MS
        backoff.onReturnedToLauncher()
        assertEquals(0, backoff.consecutiveFailures)
        assertEquals(3_000L, backoff.nextDelayMs())
    }

    @Test
    fun resumeWithoutLaunchDoesNotChangeState() {
        backoff.onLaunchFailed()
        backoff.onReturnedToLauncher()
        backoff.onReturnedToLauncher()
        assertEquals(1, backoff.consecutiveFailures)
    }

    @Test
    fun returnIsCountedOnlyOncePerLaunch() {
        backoff.onLaunched()
        now += 1_000
        backoff.onReturnedToLauncher()
        backoff.onReturnedToLauncher()
        assertEquals(1, backoff.consecutiveFailures)
    }

    @Test
    fun resetClearsFailures() {
        backoff.onLaunchFailed()
        backoff.reset()
        assertEquals(3_000L, backoff.nextDelayMs())
    }
}
