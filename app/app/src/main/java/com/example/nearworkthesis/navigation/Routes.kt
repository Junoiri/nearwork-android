package com.example.nearworkthesis.navigation

import android.net.Uri

sealed class Route(val path: String) {
    data object Splash : Route("splash")
    data object HomeImportGraph : Route("home_import_graph")
    data object Home : Route("home")
    data object Import : Route("import")
    data object Daily : Route("daily") {
        const val paramDate = "date"
        const val deepLinkBase = "nearwork://daily"
        val deepLinkPattern = "$deepLinkBase?$paramDate={$paramDate}"
        fun withDate(date: String?): String = if (date.isNullOrBlank()) {
            path
        } else {
            "$path?$paramDate=${Uri.encode(date)}"
        }
        fun deepLink(date: String?): Uri = if (date.isNullOrBlank()) {
            Uri.parse(deepLinkBase)
        } else {
            Uri.parse("$deepLinkBase?$paramDate=${Uri.encode(date)}")
        }
    }
    data object DataAnalysis : Route("analysis") {
        const val paramDate = "date"
        fun withDate(date: String?): String = if (date.isNullOrBlank()) {
            path
        } else {
            "$path?$paramDate=${Uri.encode(date)}"
        }
    }
    data object Weekly : Route("weekly")
    data object History : Route("history")
    data object Profiles : Route("profiles")
    data object Settings : Route("settings") {
        const val paramFocus = "focus"
        const val focusNotifications = "notifications"
        fun withFocus(focus: String?): String = if (focus.isNullOrBlank()) {
            path
        } else {
            "$path?$paramFocus=${Uri.encode(focus)}"
        }
    }
    data object MethodsAssumptions : Route("methods_assumptions")
    data object AboutResearch : Route("about_research")
    data object DataFormats : Route("data_formats")
    data object Export : Route("export")
    data object DeviceConfig : Route("device_config")
}
