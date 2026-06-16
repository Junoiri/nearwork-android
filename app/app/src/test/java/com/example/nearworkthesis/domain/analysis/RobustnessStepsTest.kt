package com.example.nearworkthesis.domain.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobustnessStepsTest {

    @Test
    fun nonFiniteValueGuard_rejectsSentinelTimestamps_whenEnabled() {
        val robustness = RobustnessConfig()

        assertTrue(NonFiniteValueGuard.shouldRejectTimestamp(Long.MIN_VALUE, robustness))
        assertTrue(NonFiniteValueGuard.shouldRejectTimestamp(Long.MAX_VALUE, robustness))
        assertFalse(NonFiniteValueGuard.shouldRejectTimestamp(1_000L, robustness))
    }

    @Test
    fun nonFiniteValueGuard_allowsSentinelTimestamps_whenDisabled() {
        val robustness = RobustnessConfig(
            guardNonFiniteValues = false
        )

        assertFalse(NonFiniteValueGuard.shouldRejectTimestamp(Long.MIN_VALUE, robustness))
        assertFalse(NonFiniteValueGuard.shouldRejectTimestamp(Long.MAX_VALUE, robustness))
    }

    @Test
    fun nonFiniteValueGuard_rejectsAndAcceptsNumbers_basedOnConfiguration() {
        val enabled = RobustnessConfig()
        val disabled = RobustnessConfig(
            guardNonFiniteValues = false
        )

        assertTrue(NonFiniteValueGuard.shouldRejectNumber(Double.NaN, enabled))
        assertTrue(NonFiniteValueGuard.shouldRejectNumber(Double.POSITIVE_INFINITY, enabled))
        assertFalse(NonFiniteValueGuard.shouldRejectNumber(42.0, enabled))

        assertFalse(NonFiniteValueGuard.shouldRejectNumber(Double.NaN, disabled))
        assertTrue(NonFiniteValueGuard.isAcceptableDistance(Double.NaN, disabled))
        assertTrue(NonFiniteValueGuard.isAcceptableLux(Double.POSITIVE_INFINITY, disabled))
        assertFalse(NonFiniteValueGuard.isAcceptableDistance(Double.NaN, enabled))
        assertFalse(NonFiniteValueGuard.isAcceptableLux(Double.NEGATIVE_INFINITY, enabled))
    }
}
