package com.example.nearworkthesis.app

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.nearworkthesis.domain.analysis.DiopterHoursCalculator
import com.example.nearworkthesis.domain.analysis.NearworkRiskScoreCalculator
import com.example.nearworkthesis.domain.device.DeviceConfigRepository
import com.example.nearworkthesis.domain.demo.DemoRepository
import com.example.nearworkthesis.domain.notifications.NotificationScheduler
import com.example.nearworkthesis.domain.notifications.NotificationHistoryRepository
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.importing.howfar.HowfarStorageRepository
import com.example.nearworkthesis.settings.ActiveProfileStore
import com.example.nearworkthesis.settings.SettingsStore

val LocalMeasurementRepository = staticCompositionLocalOf<MeasurementRepository> {
    error("MeasurementRepository not provided")
}

val LocalProfileRepository = staticCompositionLocalOf<ProfileRepository> {
    error("ProfileRepository not provided")
}

val LocalActiveProfileStore = staticCompositionLocalOf<ActiveProfileStore> {
    error("ActiveProfileStore not provided")
}

val LocalSettingsStore = staticCompositionLocalOf<SettingsStore> {
    error("SettingsStore not provided")
}

val LocalDeviceConfigRepository = staticCompositionLocalOf<DeviceConfigRepository> {
    error("DeviceConfigRepository not provided")
}

val LocalDemoRepository = staticCompositionLocalOf<DemoRepository> {
    error("DemoRepository not provided")
}

val LocalNotificationScheduler = staticCompositionLocalOf<NotificationScheduler> {
    error("NotificationScheduler not provided")
}

val LocalNotificationHistoryRepository = staticCompositionLocalOf<NotificationHistoryRepository> {
    error("NotificationHistoryRepository not provided")
}

val LocalNearworkRiskScoreCalculator = staticCompositionLocalOf<NearworkRiskScoreCalculator> {
    error("NearworkRiskScoreCalculator not provided")
}

val LocalDiopterHoursCalculator = staticCompositionLocalOf<DiopterHoursCalculator> {
    error("DiopterHoursCalculator not provided")
}

val LocalHowfarStorageRepository = staticCompositionLocalOf<HowfarStorageRepository> {
    error("HowfarStorageRepository not provided")
}
