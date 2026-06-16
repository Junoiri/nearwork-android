package com.example.nearworkthesis.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import com.example.nearworkthesis.data.demo.AssetDemoRepository
import com.example.nearworkthesis.data.notifications.DataStoreNotificationHistoryRepository
import com.example.nearworkthesis.data.notifications.WorkManagerNotificationScheduler
import com.example.nearworkthesis.data.local.NearworkDatabase
import com.example.nearworkthesis.data.repository.RoomImportSessionRepository
import com.example.nearworkthesis.data.repository.RoomMeasurementRepository
import com.example.nearworkthesis.data.repository.RoomProfileRepository
import com.example.nearworkthesis.domain.ImportStatusRepository
import com.example.nearworkthesis.domain.analysis.DiopterHoursCalculator
import com.example.nearworkthesis.domain.analysis.NearworkRiskScoreCalculator
import com.example.nearworkthesis.domain.device.DeviceConfigRepository
import com.example.nearworkthesis.domain.demo.DemoRepository
import com.example.nearworkthesis.domain.notifications.NotificationHistoryRepository
import com.example.nearworkthesis.domain.notifications.NotificationScheduler
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.domain.repository.ProfileRepository
import com.example.nearworkthesis.importing.ActiveProfileImportStatusRepository
import com.example.nearworkthesis.importing.CsvBytesImportInteractor
import com.example.nearworkthesis.importing.ImportSamplesInteractor
import com.example.nearworkthesis.importing.RoomImportTransactionRunner
import com.example.nearworkthesis.importing.SampleCsvParser
import com.example.nearworkthesis.importing.howfar.AndroidHowfarStorageRepository
import com.example.nearworkthesis.importing.howfar.HowfarDataParser
import com.example.nearworkthesis.importing.howfar.HowfarDeviceConfigRepository
import com.example.nearworkthesis.importing.howfar.HowfarImportInteractor
import com.example.nearworkthesis.importing.howfar.HowfarStorageRepository
import com.example.nearworkthesis.importing.howfar.HowfarUf2Archive
import com.example.nearworkthesis.importing.howfar.AndroidHowfarUf2Archive
import com.example.nearworkthesis.navigation.AppNavGraph
import com.example.nearworkthesis.navigation.Route
import com.example.nearworkthesis.settings.ActiveProfileStore
import com.example.nearworkthesis.settings.DataStoreActiveProfileStore
import com.example.nearworkthesis.settings.DataStoreSettingsStore
import com.example.nearworkthesis.settings.SettingsStore
import kotlinx.coroutines.flow.first

@Composable
fun NearworkApp(
    skipSplash: Boolean = false,
    database: NearworkDatabase = rememberDatabase(),
    settingsStore: SettingsStore = rememberSettingsStore(),
    measurementRepository: MeasurementRepository = rememberMeasurementRepository(database, settingsStore),
    profileRepository: ProfileRepository = rememberProfileRepository(database),
    activeProfileStore: ActiveProfileStore = rememberActiveProfileStore(),
    howfarStorageRepository: HowfarStorageRepository = rememberHowfarStorageRepository(),
    howfarUf2Archive: HowfarUf2Archive = rememberHowfarUf2Archive(),
    csvImporter: CsvBytesImportInteractor = rememberCsvImporter(
        database = database,
        measurementRepository = measurementRepository,
        settingsStore = settingsStore,
        profileRepository = profileRepository
    ),
    howfarImportInteractor: HowfarImportInteractor = rememberHowfarImportInteractor(
        howfarStorageRepository = howfarStorageRepository,
        howfarUf2Archive = howfarUf2Archive,
        activeProfileStore = activeProfileStore,
        csvImporter = csvImporter
    ),
    deviceConfigRepository: DeviceConfigRepository = rememberDeviceConfigRepository(howfarStorageRepository),
    demoRepository: DemoRepository = rememberDemoRepository(database, measurementRepository, settingsStore, profileRepository),
    notificationScheduler: NotificationScheduler = rememberNotificationScheduler(
        settingsStore = settingsStore,
        activeProfileStore = activeProfileStore,
        profileRepository = profileRepository
    ),
    notificationHistoryRepository: NotificationHistoryRepository = rememberNotificationHistoryRepository(),
    diopterHoursCalculator: DiopterHoursCalculator = rememberDiopterHoursCalculator(),
    nearworkRiskScoreCalculator: NearworkRiskScoreCalculator = rememberNearworkRiskScoreCalculator(),
    importStatusRepository: ImportStatusRepository = rememberImportStatusRepository(
        database = database,
        measurementRepository = measurementRepository,
        activeProfileStore = activeProfileStore,
        settingsStore = settingsStore,
        profileRepository = profileRepository,
        csvImporter = csvImporter
    )
) {
    val navController = androidx.navigation.compose.rememberNavController()
    DisposableEffect(Unit) {
        onDispose { howfarStorageRepository.close() }
    }

    LaunchedEffect(Unit) {
        val profiles = profileRepository.getProfiles()
        if (profiles.isEmpty()) {
            val createdId = profileRepository.insertProfile(
                name = "Profile 1",
                createdAtEpochMillis = System.currentTimeMillis()
            )
            activeProfileStore.setActiveProfileId(createdId)
            return@LaunchedEffect
        }

        val currentId = activeProfileStore.observeActiveProfileId().first()
        val exists = currentId != null && profiles.any { it.id == currentId }
        if (!exists) {
            activeProfileStore.setActiveProfileId(profiles.first().id)
        }
    }

    val startDestination = Route.Home.path

    CompositionLocalProvider(
        LocalMeasurementRepository provides measurementRepository,
        LocalProfileRepository provides profileRepository,
        LocalActiveProfileStore provides activeProfileStore,
        LocalSettingsStore provides settingsStore,
        LocalDeviceConfigRepository provides deviceConfigRepository,
        LocalDemoRepository provides demoRepository,
        LocalNotificationScheduler provides notificationScheduler,
        LocalNotificationHistoryRepository provides notificationHistoryRepository,
        LocalDiopterHoursCalculator provides diopterHoursCalculator,
        LocalNearworkRiskScoreCalculator provides nearworkRiskScoreCalculator,
        LocalHowfarStorageRepository provides howfarStorageRepository,
        LocalHowfarUf2Archive provides howfarUf2Archive
    ) {
        AppNavGraph(
            navController = navController,
            startDestination = startDestination,
            skipSplash = skipSplash,
            importStatusRepository = importStatusRepository,
            measurementRepository = measurementRepository,
            storageRepository = howfarStorageRepository,
            howfarImportInteractor = howfarImportInteractor
        )
    }
}

@Composable
private fun rememberImportStatusRepository(
    database: NearworkDatabase,
    measurementRepository: MeasurementRepository,
    activeProfileStore: ActiveProfileStore,
    settingsStore: SettingsStore,
    profileRepository: ProfileRepository,
    csvImporter: CsvBytesImportInteractor
): ImportStatusRepository {
    val context = LocalContext.current
    return remember {
        val importSessionRepository = RoomImportSessionRepository(database.nearworkDao())
        val interactor = ImportSamplesInteractor(
            assetManager = context.assets,
            database = database,
            importSessionRepository = importSessionRepository,
            measurementRepository = measurementRepository,
            settingsStore = settingsStore,
            profileRepository = profileRepository
        )

        ActiveProfileImportStatusRepository(
            interactor = interactor,
            csvImporter = csvImporter,
            measurementRepository = measurementRepository,
            activeProfileStore = activeProfileStore
        )
    }
}

@Composable
private fun rememberCsvImporter(
    database: NearworkDatabase,
    measurementRepository: MeasurementRepository,
    settingsStore: SettingsStore,
    profileRepository: ProfileRepository
): CsvBytesImportInteractor {
    return remember {
        val importSessionRepository = RoomImportSessionRepository(database.nearworkDao())
        val transactionRunner = RoomImportTransactionRunner(database)
        CsvBytesImportInteractor(
            transactionRunner = transactionRunner,
            importSessionRepository = importSessionRepository,
            measurementRepository = measurementRepository,
            settingsStore = settingsStore,
            profileRepository = profileRepository,
            parser = SampleCsvParser()
        )
    }
}

@Composable
private fun rememberMeasurementRepository(
    database: NearworkDatabase,
    settingsStore: SettingsStore
): MeasurementRepository {
    return remember {
        RoomMeasurementRepository(
            nearworkDao = database.nearworkDao(),
            settingsStore = settingsStore
        )
    }
}

@Composable
private fun rememberProfileRepository(database: NearworkDatabase): ProfileRepository {
    return remember {
        RoomProfileRepository(database.nearworkDao())
    }
}

@Composable
private fun rememberActiveProfileStore(): ActiveProfileStore {
    val context = LocalContext.current
    return remember {
        DataStoreActiveProfileStore(context.applicationContext)
    }
}

@Composable
private fun rememberSettingsStore(): SettingsStore {
    val context = LocalContext.current
    return remember {
        DataStoreSettingsStore(context.applicationContext)
    }
}

@Composable
private fun rememberHowfarStorageRepository(): HowfarStorageRepository {
    val context = LocalContext.current
    return remember {
        AndroidHowfarStorageRepository(context.applicationContext)
    }
}

@Composable
private fun rememberHowfarImportInteractor(
    howfarStorageRepository: HowfarStorageRepository,
    howfarUf2Archive: HowfarUf2Archive,
    activeProfileStore: ActiveProfileStore,
    csvImporter: CsvBytesImportInteractor
): HowfarImportInteractor {
    return remember {
        HowfarImportInteractor(
            storageRepository = howfarStorageRepository,
            howfarUf2Archive = howfarUf2Archive,
            dataParser = HowfarDataParser(),
            activeProfileStore = activeProfileStore,
            csvImporter = csvImporter
        )
    }
}

@Composable
private fun rememberDeviceConfigRepository(
    howfarStorageRepository: HowfarStorageRepository
): DeviceConfigRepository {
    val context = LocalContext.current
    return remember {
        HowfarDeviceConfigRepository(
            appContext = context.applicationContext,
            storageRepository = howfarStorageRepository
        )
    }
}

@Composable
private fun rememberDemoRepository(
    database: NearworkDatabase,
    measurementRepository: MeasurementRepository,
    settingsStore: SettingsStore,
    profileRepository: ProfileRepository
): DemoRepository {
    val context = LocalContext.current
    return remember {
        AssetDemoRepository(
            context = context.applicationContext,
            database = database,
            measurementRepository = measurementRepository,
            settingsStore = settingsStore,
            profileRepository = profileRepository
        )
    }
}

@Composable
private fun rememberNotificationScheduler(
    settingsStore: SettingsStore,
    activeProfileStore: ActiveProfileStore,
    profileRepository: ProfileRepository
): NotificationScheduler {
    val context = LocalContext.current
    return remember {
        WorkManagerNotificationScheduler(
            context = context.applicationContext,
            settingsStore = settingsStore,
            activeProfileStore = activeProfileStore,
            profileRepository = profileRepository
        )
    }
}

@Composable
private fun rememberNotificationHistoryRepository(): NotificationHistoryRepository {
    val context = LocalContext.current
    return remember {
        DataStoreNotificationHistoryRepository(context.applicationContext)
    }
}

@Composable
private fun rememberNearworkRiskScoreCalculator(): NearworkRiskScoreCalculator {
    return remember { NearworkRiskScoreCalculator() }
}

@Composable
private fun rememberDiopterHoursCalculator(): DiopterHoursCalculator {
    return remember { DiopterHoursCalculator() }
}

@Composable
private fun rememberDatabase(): NearworkDatabase {
    val context = LocalContext.current
    return remember {
        Room.databaseBuilder(
            context,
            NearworkDatabase::class.java,
            "nearwork-db"
        )
            // I register the profile DOB migration here so existing installs keep their data.
            .addMigrations(
                com.example.nearworkthesis.data.local.Migrations.MIGRATION_2_3,
                com.example.nearworkthesis.data.local.Migrations.MIGRATION_3_4,
                com.example.nearworkthesis.data.local.Migrations.MIGRATION_4_5,
                com.example.nearworkthesis.data.local.Migrations.MIGRATION_5_6
            )
            .build()
    }
}

@Composable
private fun rememberHowfarUf2Archive(): HowfarUf2Archive {
    val context = LocalContext.current
    return remember {
        AndroidHowfarUf2Archive(context.applicationContext)
    }
}











