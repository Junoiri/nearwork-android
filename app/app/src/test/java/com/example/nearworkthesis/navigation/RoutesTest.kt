package com.example.nearworkthesis.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoutesTest {

    @Test
    fun dailyRoute_buildsPlainAndEncodedPaths() {
        assertEquals(Route.Daily.path, Route.Daily.withDate(null))
        assertEquals(Route.Daily.path, Route.Daily.withDate(""))
        assertEquals("daily?date=2026-06-15", Route.Daily.withDate("2026-06-15"))
        assertEquals("daily?date=2026-06-15%2007%3A05", Route.Daily.withDate("2026-06-15 07:05"))

        assertEquals(Route.Daily.deepLinkBase, Route.Daily.deepLink(null).toString())
        assertEquals(
            "nearwork://daily?date=2026-06-15%2007%3A05",
            Route.Daily.deepLink("2026-06-15 07:05").toString()
        )
        assertTrue(Route.Daily.deepLinkPattern.contains("date={date}"))
    }

    @Test
    fun settingsAndAnalysisRoutes_encodeOptionalQueryParameters() {
        assertEquals(Route.Settings.path, Route.Settings.withFocus(null))
        assertEquals("settings?focus=notifications", Route.Settings.withFocus(Route.Settings.focusNotifications))
        assertEquals("analysis?date=2026-06-15", Route.DataAnalysis.withDate("2026-06-15"))
        assertEquals("analysis?date=2026-06-15%2007%3A05", Route.DataAnalysis.withDate("2026-06-15 07:05"))
        assertEquals(Route.DataAnalysis.path, Route.DataAnalysis.withDate(" "))
    }

    @Test
    fun allStaticRoutes_keepExpectedPaths() {
        assertEquals("splash", Route.Splash.path)
        assertEquals("home_import_graph", Route.HomeImportGraph.path)
        assertEquals("home", Route.Home.path)
        assertEquals("import", Route.Import.path)
        assertEquals("weekly", Route.Weekly.path)
        assertEquals("history", Route.History.path)
        assertEquals("profiles", Route.Profiles.path)
        assertEquals("methods_assumptions", Route.MethodsAssumptions.path)
        assertEquals("about_research", Route.AboutResearch.path)
        assertEquals("data_formats", Route.DataFormats.path)
        assertEquals("export", Route.Export.path)
        assertEquals("device_config", Route.DeviceConfig.path)
    }
}
