package com.example.nearworkthesis.feature

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.nearworkthesis.app.LocalActiveProfileStore
import com.example.nearworkthesis.app.LocalMeasurementRepository
import com.example.nearworkthesis.app.LocalProfileRepository
import com.example.nearworkthesis.core.ui.theme.NearworkTheme

@Composable
fun ProfilesScreen(
    modifier: Modifier = Modifier
) {
    val profileRepository = LocalProfileRepository.current
    val measurementRepository = LocalMeasurementRepository.current
    val activeProfileStore = LocalActiveProfileStore.current

    val viewModel: ProfilesViewModel = viewModel(
        factory = ProfilesViewModel.factory(profileRepository, measurementRepository, activeProfileStore)
    )
    val state by viewModel.uiState.collectAsState()

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        when (state) {
            is ProfilesUiState.Loading -> ProfilesLoading()
            is ProfilesUiState.Error -> ProfilesError(
                message = (state as ProfilesUiState.Error).message
            )
            is ProfilesUiState.Data -> ProfilesContent(
                profiles = (state as ProfilesUiState.Data).profiles,
                onSetActive = viewModel::setActive,
                onAdd = viewModel::addProfile,
                onRename = viewModel::renameProfile,
                onDelete = viewModel::deleteProfile
            )
        }
    }
}

@Composable
private fun ProfilesLoading() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.size(16.dp))
        Text("Loading profiles\u2026", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProfilesError(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.size(12.dp))
        Text("Unable to load profiles", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.size(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProfilesContent(
    profiles: List<ProfileListItem>,
    onSetActive: (Long) -> Unit,
    onAdd: (String, String?) -> Unit,
    onRename: (Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    val showAddDialog = remember { mutableStateOf(false) }
    val renameTarget = remember { mutableStateOf<ProfileListItem?>(null) }
    val deleteTarget = remember { mutableStateOf<ProfileListItem?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column {
                Text("Profiles", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "Keep participants separated",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = { showAddDialog.value = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add profile")
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(profiles, key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    onClick = { onSetActive(profile.id) },
                    onRename = { renameTarget.value = profile },
                    onDelete = { deleteTarget.value = profile }
                )
            }
        }
    }

    if (showAddDialog.value) {
        var name by remember { mutableStateOf("Profile ${profiles.size + 1}") }
        // I keep DOB as plain ISO text for now so we can store it without dragging in a full picker flow.
        var dateOfBirth by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAddDialog.value = false },
            title = { Text("Add profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        label = { Text("Name") }
                    )
                    OutlinedTextField(
                        value = dateOfBirth,
                        onValueChange = { dateOfBirth = it },
                        singleLine = true,
                        // I spell out the ISO format here so the stored value stays migration-friendly.
                        label = { Text("Date of birth (optional)") },
                        placeholder = { Text("YYYY-MM-DD") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        // I pass null here when left blank so older profiles and quick adds still work cleanly.
                        onAdd(name, dateOfBirth.ifBlank { null })
                        showAddDialog.value = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog.value = false }) { Text("Cancel") }
            }
        )
    }

    renameTarget.value?.let { profile ->
        var name by remember(profile.id) { mutableStateOf(profile.name) }
        AlertDialog(
            onDismissRequest = { renameTarget.value = null },
            title = { Text("Rename profile") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(profile.id, name)
                        renameTarget.value = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget.value = null }) { Text("Cancel") }
            }
        )
    }

    deleteTarget.value?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget.value = null },
            icon = { Icon(Icons.Default.Warning, contentDescription = null) },
            title = { Text("Delete profile?") },
            text = {
                Text(
                    "This deletes \"${profile.name}\" and permanently removes its measurements and import sessions.\n\n" +
                        "${profile.dayCount} days \u2022 ${profile.sampleCount} samples"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(profile.id)
                        deleteTarget.value = null
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget.value = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: ProfileListItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    if (profile.isActive) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(profile.name, style = MaterialTheme.typography.titleLarge)
                }
                if (profile.isActive) {
                    AssistChip(onClick = {}, label = { Text("Active") })
                }
            }

            Text(
                "${profile.dayCount} days \u2022 ${profile.sampleCount} samples",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "Rename")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ProfilesScreenPreview() {
    val sample = listOf(
        ProfileListItem(
            id = 1L,
            name = "Profile 1",
            isActive = true,
            dayCount = 7,
            sampleCount = 12_345
        ),
        ProfileListItem(
            id = 2L,
            name = "Participant B",
            isActive = false,
            dayCount = 3,
            sampleCount = 4_100
        )
    )
    NearworkTheme {
        ProfilesContent(
            profiles = sample,
            onSetActive = {},
            onAdd = { _, _ -> },
            onRename = { _, _ -> },
            onDelete = {}
        )
    }
}

