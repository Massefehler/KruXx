package it.fast4x.rimusic.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SplashLightTest {
    /** One sample per millisecond of a 1.2-second pass. */
    private val phases = (0..1_200).map { it / 1_200f }

    @Test
    fun lightTravelsStrictlyFromLeftToRight() {
        phases.zipWithNext().forEach { (a, b) ->
            assertTrue("light must move right between $a and $b", SplashLight.x(b) > SplashLight.x(a))
        }
        // Each pass enters from outside the left edge and leaves beyond the right edge.
        assertTrue(SplashLight.x(0f) < 0f)
        assertTrue(SplashLight.x(1f) > 1f)
    }

    @Test
    fun noDisplayFrameMovesTheLightByMoreThanTwoPercentOfTheArtwork() {
        // The former frames 5 -> 6 moved it by 40 % of the artwork in one 120 ms step.
        val frameAt60Hz = 1_000f / 60f / 1_200f
        var phase = 0f
        while (phase + frameAt60Hz <= 1f) {
            val step = SplashLight.x(phase + frameAt60Hz) - SplashLight.x(phase)
            assertTrue("step $step at phase $phase", step > 0f && step < 0.02f)
            phase += frameAt60Hz
        }
    }

    @Test
    fun restartBetweenPassesIsInvisible() {
        assertEquals(0f, SplashLight.intensity(0f), 0f)
        assertEquals(0f, SplashLight.intensity(1f), 0f)
        assertEquals(1f, SplashLight.intensity(0.5f), 0f)
        phases.zipWithNext().forEach { (a, b) ->
            assertTrue(abs(SplashLight.intensity(b) - SplashLight.intensity(a)) < 0.02f)
        }
    }

    @Test
    fun glowPeaksWhileTheLightCrossesTheMiddle() {
        val peak = phases.maxBy { SplashLight.pulse(it) }
        assertEquals(0.5f, SplashLight.x(peak), 0.01f)
        assertEquals(0f, SplashLight.pulse(0f), 1e-6f)
        assertEquals(0f, SplashLight.pulse(1f), 1e-6f)
    }
}
