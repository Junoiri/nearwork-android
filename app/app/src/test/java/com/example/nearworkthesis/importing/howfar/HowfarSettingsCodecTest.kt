package com.example.nearworkthesis.importing.howfar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HowfarSettingsCodecTest {

    @Test
    fun defaults_useExpectedThesisBaselineValues() {
        val settings = HowfarSettings.defaults(nowEpochSeconds = 1234L)

        assertEquals(2024091701L, settings.settingsVersion)
        assertTrue(settings.featureAls)
        assertTrue(settings.featureTof)
        assertFalse(settings.featureDcdcSleep)
        assertTrue(settings.featureStorage)
        assertFalse(settings.flagEraseDatabase)
        assertEquals(1234L, settings.timestampSeconds)
        assertEquals(10L, settings.measurementIntervalSeconds)
        assertEquals("OPTODATA", settings.examinationIdentifier)
    }

    @Test
    fun toByteArray_andFromByteArray_roundTripAllFields() {
        val settings = HowfarSettings(
            settingsVersion = 99L,
            featureAls = false,
            featureTof = true,
            featureDcdcSleep = true,
            featureStorage = false,
            flagEraseDatabase = true,
            timestampSeconds = 777L,
            tofDistanceMode = 2L,
            tofTimingBudget = 33L,
            measurementIntervalSeconds = 15L,
            examinationIdentifier = "ABCD1234"
        )

        val decoded = HowfarSettingsCodec.fromByteArray(HowfarSettingsCodec.toByteArray(settings))

        assertEquals(settings, decoded)
    }

    @Test
    fun toByteArray_truncatesLongExamIdentifiers_toEightCharacters() {
        val settings = HowfarSettings.defaults().copy(
            examinationIdentifier = "LONGER_THAN_EIGHT"
        )

        val decoded = HowfarSettingsCodec.fromByteArray(HowfarSettingsCodec.toByteArray(settings))

        assertEquals("LONGER_T", decoded.examinationIdentifier)
    }

    @Test
    fun toByteArray_padsShortExamIdentifiers_andDecoderTrimsSpaces() {
        val settings = HowfarSettings.defaults().copy(
            examinationIdentifier = "ABC"
        )

        val decoded = HowfarSettingsCodec.fromByteArray(HowfarSettingsCodec.toByteArray(settings))

        assertEquals("ABC", decoded.examinationIdentifier)
    }

    @Test(expected = IllegalArgumentException::class)
    fun fromByteArray_rejectsPayloadsThatAreTooSmall() {
        HowfarSettingsCodec.fromByteArray(ByteArray(4))
    }
}
