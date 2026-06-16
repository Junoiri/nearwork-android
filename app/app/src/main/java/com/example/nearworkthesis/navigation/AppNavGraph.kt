package com.example.nearworkthesis.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import androidx.compose.runtime.remember
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import com.example.nearworkthesis.core.ui.components.MainScaffold
import com.example.nearworkthesis.core.ui.components.AppScaffold
import com.example.nearworkthesis.domain.ImportStatusRepository
import com.example.nearworkthesis.domain.repository.MeasurementRepository
import com.example.nearworkthesis.feature.*
import com.example.nearworkthesis.importing.howfar.HowfarImportInteractor
import com.example.nearworkthesis.importing.howfar.HowfarStorageRepository

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    skipSplash: Boolean = false,
    importStatusRepository: ImportStatusRepository,
    measurementRepository: MeasurementRepository,
    storageRepository: HowfarStorageRepository,
    howfarImportInteractor: HowfarImportInteractor
) {
    val dailyRoute = "${Route.Daily.path}?${Route.Daily.paramDate}={${Route.Daily.paramDate}}"
    val analysisRoute = "${Route.DataAnalysis.path}?${Route.DataAnalysis.paramDate}={${Route.DataAnalysis.paramDate}}"
    val settingsRoute = "${Route.Settings.path}?${Route.Settings.paramFocus}={${Route.Settings.paramFocus}}"
    val contentStartDestination = if (startDestination == Route.Home.path) {
        Route.HomeImportGraph.path
    } else {
        startDestination
    }
    NavHost(
        navController = navController,
        startDestination = if (skipSplash) contentStartDestination else Route.Splash.path
    ) {
        composable(Route.Splash.path) {
            SplashScreen(
                onFinished = {
                    navController.navigate(contentStartDestination) {
                        popUpTo(Route.Splash.path) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
        navigation(
            startDestination = Route.Home.path,
            route = Route.HomeImportGraph.path
        ) {
            composable(Route.Home.path) { backStackEntry ->
                val importGraphEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Route.HomeImportGraph.path)
                }
                val sharedImportViewModel: ImportViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    viewModelStoreOwner = importGraphEntry,
                    factory = ImportViewModel.factory(
                        importStatusRepository = importStatusRepository,
                        storageRepository = storageRepository,
                        howfarImportInteractor = howfarImportInteractor,
                        measurementRepository = measurementRepository,
                        notificationScheduler = com.example.nearworkthesis.app.LocalNotificationScheduler.current,
                        activeProfileStore = com.example.nearworkthesis.app.LocalActiveProfileStore.current
                    )
                )
                MainScaffold(
                    navController = navController,
                    topBarActions = {
                        IconButton(
                        onClick = { navController.navigate(Route.Settings.path) { launchSingleTop = true } }
                        ) {
                            Icon(imageVector = Icons.Outlined.Settings, contentDescription = "Open settings")
                        }
                    }
                ) { innerPadding ->
                    HomeScreen(
                        modifier = Modifier.padding(innerPadding),
                        importViewModel = sharedImportViewModel,
                        onOpenImport = {
                            navController.navigate(Route.Import.path) { launchSingleTop = true }
                        },
                        onOpenSettings = {
                            navController.navigate(Route.Settings.withFocus(Route.Settings.focusNotifications)) {
                                launchSingleTop = true
                            }
                        }
                    )
                }
            }
            composable(Route.Import.path) { backStackEntry ->
                val importGraphEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Route.HomeImportGraph.path)
                }
                val sharedImportViewModel: ImportViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
                    viewModelStoreOwner = importGraphEntry,
                    factory = ImportViewModel.factory(
                        importStatusRepository = importStatusRepository,
                        storageRepository = storageRepository,
                        howfarImportInteractor = howfarImportInteractor,
                        measurementRepository = measurementRepository,
                        notificationScheduler = com.example.nearworkthesis.app.LocalNotificationScheduler.current,
                        activeProfileStore = com.example.nearworkthesis.app.LocalActiveProfileStore.current
                    )
                )
                ImportScreen(
                    viewModel = sharedImportViewModel,
                    onImported = {
                        navController.navigate(Route.Daily.withDate(null)) {
                            popUpTo(Route.Import.path) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onBack = { navController.navigateUp() },
                    onOpenSettings = {
                        navController.navigate(Route.Settings.path) { launchSingleTop = true }
                    },
                    onOpenDeviceConfig = {
                        navController.navigate(Route.DeviceConfig.path) { launchSingleTop = true }
                    }
                )
            }
        }

        composable(Route.Daily.path, deepLinks = listOf(navDeepLink { uriPattern = Route.Daily.deepLinkBase })) {
            MainScaffold(navController = navController) { innerPadding ->
                DailyScreen(
                    modifier = Modifier.padding(innerPadding),
                    measurementRepository = measurementRepository,
                    selectedDate = null,
                    onGoToImport = {
                        navController.navigate(Route.Import.path) {
                            popUpTo(Route.Daily.path) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenAnalysis = { day ->
                        navController.navigate(Route.DataAnalysis.withDate(day)) { launchSingleTop = true }
                    },
                    onDeletedEmpty = {}
                )
            }
        }

        composable(
            route = dailyRoute,
            arguments = listOf(
                navArgument(Route.Daily.paramDate) {
                    nullable = true
                    defaultValue = null
                }
            ),
            deepLinks = listOf(navDeepLink { uriPattern = Route.Daily.deepLinkPattern })
        ) { backStackEntry ->
            val selectedDate = backStackEntry.arguments?.getString(Route.Daily.paramDate)
            MainScaffold(navController = navController) { innerPadding ->
                DailyScreen(
                    modifier = Modifier.padding(innerPadding),
                    measurementRepository = measurementRepository,
                    selectedDate = selectedDate,
                    onGoToImport = {
                        navController.navigate(Route.Import.path) {
                            popUpTo(Route.Daily.path) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onOpenAnalysis = { day ->
                        navController.navigate(Route.DataAnalysis.withDate(day)) { launchSingleTop = true }
                    },
                    onDeletedEmpty = { navController.navigateUp() }
                )
            }
        }

        composable(
            route = analysisRoute,
            arguments = listOf(
                navArgument(Route.DataAnalysis.paramDate) {
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val selectedDate = backStackEntry.arguments?.getString(Route.DataAnalysis.paramDate)
            DataAnalysisScreen(
                modifier = Modifier,
                measurementRepository = measurementRepository,
                selectedDate = selectedDate,
                onGoToImport = {
                    navController.navigate(Route.Import.path) {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.navigateUp() }
            )
        }

        composable(Route.DataAnalysis.path) {
            DataAnalysisScreen(
                modifier = Modifier,
                measurementRepository = measurementRepository,
                selectedDate = null,
                onGoToImport = {
                    navController.navigate(Route.Import.path) {
                        launchSingleTop = true
                    }
                },
                onBack = { navController.navigateUp() }
            )
        }
        composable(Route.Weekly.path) {
            MainScaffold(navController = navController) { innerPadding ->
                WeeklyScreen(
                    modifier = Modifier.padding(innerPadding),
                    onOpenDay = { day ->
                        navController.navigate(Route.Daily.withDate(day)) {
                            launchSingleTop = true
                        }
                    },
                    onInspectDay = { day ->
                        navController.navigate(Route.DataAnalysis.withDate(day)) {
                            launchSingleTop = true
                        }
                    },
                    onGoToImport = {
                        navController.navigate(Route.Import.path) {
                            popUpTo(Route.Weekly.path) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
        composable(Route.History.path) {
            MainScaffold(navController = navController) { innerPadding ->
                HistoryScreen(
                    modifier = Modifier.padding(innerPadding),
                    measurementRepository = measurementRepository,
                    onSelectDay = { day ->
                        navController.navigate(Route.Daily.withDate(day)) {
                            launchSingleTop = true
                        }
                    },
                    onInspectDay = { day ->
                        navController.navigate(Route.DataAnalysis.withDate(day)) {
                            launchSingleTop = true
                        }
                    },
                    onGoToImport = {
                        navController.navigate(Route.Import.path) {
                            popUpTo(Route.History.path) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
        composable(Route.Profiles.path) {
            AppScaffold(
                title = "Profiles",
                showBack = true,
                onBack = { navController.navigateUp() }
            ) { innerPadding ->
                ProfilesScreen(modifier = Modifier.padding(innerPadding))
            }
        }
        composable(
            route = settingsRoute,
            arguments = listOf(
                navArgument(Route.Settings.paramFocus) {
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val focus = backStackEntry.arguments?.getString(Route.Settings.paramFocus)
            val snackbarHostState = remember { SnackbarHostState() }
            var backSignal by remember { mutableStateOf(0) }
            AppScaffold(
                title = "Settings",
                showBack = true,
                onBack = { backSignal += 1 },
                snackbarHostState = snackbarHostState
            ) { innerPadding ->
                SettingsScreen(
                    modifier = Modifier.padding(innerPadding),
                    snackbarHostState = snackbarHostState,
                    backSignal = backSignal,
                    openNotificationsSection = focus == Route.Settings.focusNotifications,
                    onConfirmExit = { navController.navigateUp() },
                    onOpenDeviceConfig = {
                        navController.navigate(Route.DeviceConfig.path) { launchSingleTop = true }
                    },
                    onOpenMethodsAssumptions = {
                        navController.navigate(Route.MethodsAssumptions.path) { launchSingleTop = true }
                    },
                    onOpenAboutResearch = {
                        navController.navigate(Route.AboutResearch.path) { launchSingleTop = true }
                    }
                )
            }
        }
        composable(Route.Settings.path) {
            val snackbarHostState = remember { SnackbarHostState() }
            var backSignal by remember { mutableStateOf(0) }
            AppScaffold(
                title = "Settings",
                showBack = true,
                onBack = { backSignal += 1 },
                snackbarHostState = snackbarHostState
            ) { innerPadding ->
                SettingsScreen(
                    modifier = Modifier.padding(innerPadding),
                    snackbarHostState = snackbarHostState,
                    backSignal = backSignal,
                    onConfirmExit = { navController.navigateUp() },
                    onOpenDeviceConfig = {
                        navController.navigate(Route.DeviceConfig.path) { launchSingleTop = true }
                    },
                    onOpenMethodsAssumptions = {
                        navController.navigate(Route.MethodsAssumptions.path) { launchSingleTop = true }
                    },
                    onOpenAboutResearch = {
                        navController.navigate(Route.AboutResearch.path) { launchSingleTop = true }
                    }
                )
            }
        }
        composable(Route.MethodsAssumptions.path) {
            AppScaffold(
                title = "Methods & Assumptions",
                showBack = true,
                onBack = { navController.navigateUp() }
            ) { innerPadding ->
                MethodsAssumptionsScreen(modifier = Modifier.padding(innerPadding))
            }
        }
        composable(Route.AboutResearch.path) {
            AppScaffold(
                title = "About / Research",
                showBack = true,
                onBack = { navController.navigateUp() }
            ) { innerPadding ->
                AboutResearchScreen(
                    modifier = Modifier.padding(innerPadding),
                    onOpenMethodsAssumptions = {
                        navController.navigate(Route.MethodsAssumptions.path) { launchSingleTop = true }
                    },
                    onOpenDataFormats = {
                        navController.navigate(Route.DataFormats.path) { launchSingleTop = true }
                    }
                )
            }
        }
        composable(Route.DataFormats.path) {
            AppScaffold(
                title = "Data formats",
                showBack = true,
                onBack = { navController.navigateUp() }
            ) { innerPadding ->
                DataFormatsScreen(
                    modifier = Modifier.padding(innerPadding),
                    onOpenExport = {
                        navController.navigate(Route.Export.path) { launchSingleTop = true }
                    }
                )
            }
        }
        composable(Route.Export.path) {
            val snackbarHostState = remember { SnackbarHostState() }
            AppScaffold(
                title = "Export",
                showBack = true,
                onBack = { navController.navigateUp() },
                snackbarHostState = snackbarHostState
            ) { innerPadding ->
                ExportScreen(
                    modifier = Modifier.padding(innerPadding),
                    measurementRepository = measurementRepository,
                    snackbarHostState = snackbarHostState,
                    onGoToImport = {
                        navController.navigate(Route.Import.path) {
                            popUpTo(Route.Export.path) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }
        }
        composable(Route.DeviceConfig.path) {
            val snackbarHostState = remember { SnackbarHostState() }
            var backSignal by remember { mutableStateOf(0) }
            AppScaffold(
                title = "Device config",
                showBack = true,
                onBack = { backSignal += 1 },
                snackbarHostState = snackbarHostState
            ) { innerPadding ->
                DeviceConfigScreen(
                    modifier = Modifier.padding(innerPadding),
                    snackbarHostState = snackbarHostState,
                    backSignal = backSignal,
                    onConfirmExit = { navController.navigateUp() },
                    onGoToImport = {
                        navController.navigate(Route.Import.path) {
                            launchSingleTop = true
                        }
                    },
                    onClose = { navController.navigateUp() }
                )
            }
        }
    }
}






