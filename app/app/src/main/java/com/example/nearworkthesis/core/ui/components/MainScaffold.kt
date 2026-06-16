package com.example.nearworkthesis.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.nearworkthesis.app.LocalActiveProfileStore
import com.example.nearworkthesis.app.LocalProfileRepository
import com.example.nearworkthesis.app.LocalSettingsStore
import com.example.nearworkthesis.core.ui.theme.BrightSnow
import com.example.nearworkthesis.core.ui.theme.Graphite
import com.example.nearworkthesis.domain.model.Profile
import com.example.nearworkthesis.navigation.Route

private data class MainDestination(
    val route: Route,
    val title: String,
    val activeIcon: ImageVector,
    val inactiveIcon: ImageVector
)

private val mainDestinations = listOf(
    MainDestination(
        route = Route.Home,
        title = "Home",
        activeIcon = Icons.Filled.Home,
        inactiveIcon = Icons.Outlined.Home
    ),
    MainDestination(
        route = Route.Daily,
        title = "Daily",
        activeIcon = Icons.Filled.Today,
        inactiveIcon = Icons.Outlined.Today
    ),
    MainDestination(
        route = Route.Weekly,
        title = "Weekly",
        activeIcon = Icons.Filled.DateRange,
        inactiveIcon = Icons.Outlined.DateRange
    ),
    MainDestination(
        route = Route.History,
        title = "History",
        activeIcon = Icons.Filled.History,
        inactiveIcon = Icons.Outlined.History
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScaffold(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    topBarActions: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentRouteBase = currentRoute?.substringBefore("?")
    val currentTitle = mainDestinations.firstOrNull { it.route.path == currentRouteBase }?.title.orEmpty()

    val profileRepository = LocalProfileRepository.current
    val activeProfileStore = LocalActiveProfileStore.current
    val settingsStore = LocalSettingsStore.current
    val viewModel: MainScaffoldViewModel = viewModel(
        factory = MainScaffoldViewModel.factory(
            profileRepository = profileRepository,
            settingsStore = settingsStore,
            activeProfileStore = activeProfileStore
        )
    )
    val uiState by viewModel.uiState.collectAsState()
    val activeProfileName = uiState.activeProfile?.name ?: "Profile"

    val selectedDate = when (currentRouteBase) {
        Route.Daily.path -> navBackStackEntry?.arguments?.getString(Route.Daily.paramDate) ?: "latest"
        else -> "\u2014"
    }
    val debugLine = remember(
        activeProfileName,
        selectedDate,
        uiState.lowLightThresholdLux,
        uiState.nearworkDistanceThresholdCm
    ) {
        "Profile: $activeProfileName • Date: $selectedDate • LL ${uiState.lowLightThresholdLux}lx • NW ${uiState.nearworkDistanceThresholdCm}cm"
    }

    var isProfileSheetOpen by remember { mutableStateOf(false) }
    var isEditMode by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }

    fun dismissProfileSheet() {
        isEditMode = false
        deleteTarget = null
        isProfileSheetOpen = false
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent,
        topBar = {
            CenterAlignedTopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = currentTitle,
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (uiState.showDebugOverlay) {
                            Text(
                                text = debugLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    topBarActions()
                    AssistChip(
                        onClick = { isProfileSheetOpen = true },
                        label = { Text(activeProfileName) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            )
        },
        bottomBar = {
            MainBottomNavigationBar(
                currentRouteBase = currentRouteBase,
                onNavigate = { route ->
                    navController.navigate(route) {
                        launchSingleTop = true
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        GradientBackground {
            content(innerPadding)
        }
    }

    if (isProfileSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { dismissProfileSheet() }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(text = "Switch profile")
                if (uiState.profiles.size >= 2) {
                    IconButton(
                        onClick = {
                            isEditMode = !isEditMode
                            if (!isEditMode) {
                                deleteTarget = null
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Filled.Check else Icons.Filled.Edit,
                            contentDescription = if (isEditMode) "Done editing profiles" else "Edit profiles"
                        )
                    }
                }
            }
            uiState.profiles.forEach { profile ->
                val isActive = profile.id == uiState.activeProfileId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = profile.name,
                        modifier = Modifier.weight(1f)
                    )
                    if (isEditMode && uiState.profiles.size >= 2) {
                        IconButton(
                            onClick = { deleteTarget = profile }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete ${profile.name}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else if (isActive) {
                        AssistChip(onClick = {}, label = { Text("Active") })
                    } else {
                        TextButton(
                            onClick = {
                                viewModel.setActiveProfile(profile.id)
                                dismissProfileSheet()
                            }
                        ) {
                            Text("Select")
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            Button(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                onClick = {
                    dismissProfileSheet()
                    navController.navigate(Route.Profiles.path) { launchSingleTop = true }
                }
            ) {
                Text("Manage profiles")
            }
            Spacer(modifier = Modifier.size(24.dp))
        }
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null) },
            title = { Text("Delete profile?") },
            text = {
                Text(
                    "This deletes \"${profile.name}\" permanently, including all measurements."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteProfile(profile.id)
                        deleteTarget = null
                    }
                ) {
                    Text(
                        text = "Delete",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun MainBottomNavigationBar(
    currentRouteBase: String?,
    onNavigate: (String) -> Unit
) {
    val isDarkTheme = isSystemInDarkTheme()
    val selectedContainerColor = if (isDarkTheme) BrightSnow else Graphite
    val selectedContentColor = if (isDarkTheme) Graphite else BrightSnow
    val selectedCircleSize = 84.dp * 0.9f
    val selectedIconSize = 24.dp * 1.2f
    val selectedLabelStyle = MaterialTheme.typography.labelMedium.copy(
        fontSize = MaterialTheme.typography.labelMedium.fontSize * 1.2f,
        fontWeight = FontWeight.SemiBold
    )

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            mainDestinations.forEach { destination ->
                val isSelected = destination.route.path == currentRouteBase
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(84.dp)
                        .clickable {
                            if (!isSelected) {
                                onNavigate(destination.route.path)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Surface(
                            shape = CircleShape,
                            color = selectedContainerColor,
                            modifier = Modifier.size(selectedCircleSize)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = destination.activeIcon,
                                    contentDescription = destination.title,
                                    tint = selectedContentColor,
                                    modifier = Modifier.size(selectedIconSize)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = destination.title,
                                    style = selectedLabelStyle,
                                    color = selectedContentColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = destination.inactiveIcon,
                                contentDescription = destination.title,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = destination.title,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun format1(value: Double): String = String.format(java.util.Locale.US, "%.1f", value)

