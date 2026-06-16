package com.example.nearworkthesis.domain.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NearworkSessionDetectorTest {

    private val detector = NearworkSessionDetector()

    @Test
    fun sessionsSplit_whenBreakLongerThanBreakGapOccurs() {
        val base = 0L
        val samples = listOf(
            // I use real millisecond spacing here so this test cannot accidentally pass on unit bugs.
            NearworkSample(timestampMillis = base + 0L, distanceCm = 40.0, lux = 500.0),
            NearworkSample(timestampMillis = base + 30_000L, distanceCm = 40.0, lux = 500.0),
            NearworkSample(timestampMillis = base + 60_000L, distanceCm = 40.0, lux = 500.0),
            NearworkSample(timestampMillis = base + 200_000L, distanceCm = 40.0, lux = 500.0),
            NearworkSample(timestampMillis = base + 230_000L, distanceCm = 40.0, lux = 500.0),
            NearworkSample(timestampMillis = base + 260_000L, distanceCm = 40.0, lux = 500.0)
        )

        val sessions = detector.detectSessions(
            processedSamples = samples,
            lowLightThresholdLux = 300,
            config = NearworkSessionDetector.Config(
                nearworkDistanceThresholdCm = 60.0,
                breakGapSeconds = 60,
                minSessionDurationSeconds = 1
            )
        )

        assertEquals(2, sessions.size)
    }

    @Test
    fun sessionDoesNotSplit_whenGapIsShorterThanConfiguredBreakGap() {
        val base = 0L
        val samples = listOf(
            // I keep this gap at 30 seconds because that is the regression case we explicitly want to protect.
            NearworkSample(timestampMillis = base + 0L, distanceCm = 40.0, lux = 500.0),
            NearworkSample(timestampMillis = base + 5_000L, distanceCm = 40.0, lux = 500.0),
            NearworkSample(timestampMillis = base + 35_000L, distanceCm = 40.0, lux = 500.0),
            NearworkSample(timestampMillis = base + 40_000L, distanceCm = 40.0, lux = 500.0)
        )

        val sessions = detector.detectSessions(
            processedSamples = samples,
            lowLightThresholdLux = 300,
            config = NearworkSessionDetector.Config(
                nearworkDistanceThresholdCm = 60.0,
                breakGapSeconds = 60,
                minSessionDurationSeconds = 1
            )
        )

        assertEquals(1, sessions.size)
        assertEquals(40L, sessions.single().durationSeconds)
    }

    @Test
    fun shortSequenceIsDiscarded_whenBelowMinimumSessionDuration() {
        val base = 0L
        val samples = listOf(
            // I cap this span at 5 seconds so it should be filtered out by a 10-second minimum.
            NearworkSample(timestampMillis = base + 0L, distanceCm = 40.0, lux = 500.0),
            NearworkSample(timestampMillis = base + 5_000L, distanceCm = 40.0, lux = 500.0)
        )

        val sessions = detector.detectSessions(
            processedSamples = samples,
            lowLightThresholdLux = 300,
            config = NearworkSessionDetector.Config(
                nearworkDistanceThresholdCm = 60.0,
                breakGapSeconds = 60,
                minSessionDurationSeconds = 10
            )
        )

        assertEquals(0, sessions.size)
    }

    @Test
    fun longestSession_isComputedCorrectly() {
        val base = 0L
        val samples = buildList {
            // I space these in real milliseconds so the stored duration should round back to whole seconds cleanly.
            add(NearworkSample(timestampMillis = base + 0L, distanceCm = 45.0, lux = 500.0))
            add(NearworkSample(timestampMillis = base + 60_000L, distanceCm = 45.0, lux = 500.0))

            // I make the break bigger than 60 seconds so this must start a new session.
            add(NearworkSample(timestampMillis = base + 200_000L, distanceCm = 45.0, lux = 500.0))
            // I add an intermediate point so this 120-second session is built from legal <=60-second in-session gaps.
            add(NearworkSample(timestampMillis = base + 260_000L, distanceCm = 45.0, lux = 500.0))
            add(NearworkSample(timestampMillis = base + 320_000L, distanceCm = 45.0, lux = 500.0)) // Session 2: 200-320s (120s)

            // I keep this final session shorter so the longest-session assertion stays unambiguous.
            add(NearworkSample(timestampMillis = base + 500_000L, distanceCm = 45.0, lux = 500.0))
            add(NearworkSample(timestampMillis = base + 540_000L, distanceCm = 45.0, lux = 500.0)) // Session 3: 500-540s (40s)
        }

        val sessions = detector.detectSessions(
            processedSamples = samples,
            lowLightThresholdLux = 300,
            config = NearworkSessionDetector.Config(
                nearworkDistanceThresholdCm = 60.0,
                breakGapSeconds = 60,
                minSessionDurationSeconds = 1
            )
        )

        val longest = detector.findLongestSession(sessions)
        assertNotNull(longest)
        assertEquals(120L, longest!!.durationSeconds)
        assertEquals(base + 200_000L, longest.startTimestampMillis)
        assertEquals(base + 320_000L, longest.endTimestampMillis)
    }

    @Test
    fun lowLightSecondsInSession_areStoredInSecondsNotMillis() {
        val base = 0L
        val samples = listOf(
            NearworkSample(timestampMillis = base + 0L, distanceCm = 40.0, lux = 100.0),
            NearworkSample(timestampMillis = base + 5_000L, distanceCm = 40.0, lux = 500.0),
            NearworkSample(timestampMillis = base + 10_000L, distanceCm = 40.0, lux = 500.0)
        )

        val session = detector.detectSessions(
            processedSamples = samples,
            lowLightThresholdLux = 300,
            config = NearworkSessionDetector.Config(
                nearworkDistanceThresholdCm = 60.0,
                breakGapSeconds = 60,
                minSessionDurationSeconds = 1
            )
        ).single()

        assertEquals(5L, session.lowLightSecondsInSession)
    }
}
