package com.example.nearworkthesis.core.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.Color
import com.example.nearworkthesis.navigation.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickExportTypesTest {

    @Test
    fun quickExportTypes_exposeExpectedDefaults() {
        assertEquals(listOf(QuickExportFormat.Csv, QuickExportFormat.Uf2), QuickExportFormat.entries)
        assertEquals(listOf(QuickExportSeries.Raw, QuickExportSeries.Processed), QuickExportSeries.entries)

        val colors = QuickExportCardColors(accent = Color.Red, onAccent = Color.White)
        val success = QuickExportState.Success("day.csv")
        val error = QuickExportState.Error("boom")

        assertEquals(Color.Red, colors.buttonAccent)
        assertEquals(Color.White, colors.onButtonAccent)
        assertEquals("day.csv", success.filename)
        assertEquals("boom", error.message)
        assertSame(QuickExportState.Idle, QuickExportState.Idle)
        assertSame(QuickExportState.Exporting, QuickExportState.Exporting)
    }

    @Test
    fun mainScaffoldTypes_resolveActiveProfile_andDestinationShape() {
        val uiState = MainScaffoldUiState(
            profiles = listOf(
                com.example.nearworkthesis.domain.model.Profile(1L, "Alpha", 0L, "UTC", null),
                com.example.nearworkthesis.domain.model.Profile(2L, "Beta", 0L, "UTC", null)
            ),
            activeProfileId = 2L,
            showDebugOverlay = false,
            lowLightThresholdLux = 100,
            nearworkDistanceThresholdCm = 60
        )
        val fallbackUiState = uiState.copy(activeProfileId = 99L)
        val destinationClass = Class.forName("com.example.nearworkthesis.core.ui.components.MainDestination")
        val destination = destinationClass
            .getDeclaredConstructor(Route::class.java, String::class.java, androidx.compose.ui.graphics.vector.ImageVector::class.java, androidx.compose.ui.graphics.vector.ImageVector::class.java)
            .apply { isAccessible = true }
            .newInstance(Route.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home)

        assertEquals("Beta", uiState.activeProfile?.name)
        assertEquals("Alpha", fallbackUiState.activeProfile?.name)
        assertEquals("Home", destination.javaClass.getDeclaredMethod("getTitle").apply { isAccessible = true }.invoke(destination))
    }

    @Test
    fun mainScaffoldUiState_handlesNoProfiles() {
        val uiState = MainScaffoldUiState(
            profiles = emptyList(),
            activeProfileId = null,
            showDebugOverlay = true,
            lowLightThresholdLux = 1,
            nearworkDistanceThresholdCm = 2
        )

        assertEquals(null, uiState.activeProfile)
        assertEquals(1, uiState.lowLightThresholdLux)
        assertEquals(2, uiState.nearworkDistanceThresholdCm)
    }

    @Test
    fun mainScaffoldDestinations_matchExpectedRoutesAndTitles() {
        val field = Class.forName("com.example.nearworkthesis.core.ui.components.MainScaffoldKt")
            .getDeclaredField("mainDestinations")
            .apply { isAccessible = true }
        val destinations = field.get(null) as List<*>
        val titles = destinations.map { destination ->
            destination!!.javaClass.getDeclaredMethod("getTitle").apply { isAccessible = true }.invoke(destination) as String
        }
        val routes = destinations.map { destination ->
            destination!!.javaClass.getDeclaredMethod("getRoute").apply { isAccessible = true }.invoke(destination) as Route
        }

        assertEquals(listOf("Home", "Daily", "Weekly", "History"), titles)
        assertEquals(listOf(Route.Home, Route.Daily, Route.Weekly, Route.History), routes)
        assertTrue(destinations.isNotEmpty())
        assertEquals(routes.map { it.path }.distinct().size, routes.size)
        destinations.forEach { destination ->
            assertNotNull(destination!!.javaClass.getDeclaredMethod("getActiveIcon").apply { isAccessible = true }.invoke(destination))
            assertNotNull(destination.javaClass.getDeclaredMethod("getInactiveIcon").apply { isAccessible = true }.invoke(destination))
        }
    }

    @Test
    fun mainScaffoldPrivateFormatter_usesSingleDecimalUsLocale() {
        val method = Class.forName("com.example.nearworkthesis.core.ui.components.MainScaffoldKt")
            .getDeclaredMethod("format1", Double::class.javaPrimitiveType)
            .apply { isAccessible = true }

        assertEquals("12.3", method.invoke(null, 12.34))
    }
}
