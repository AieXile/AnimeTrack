package com.aiexile.animetrack.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import com.aiexile.animetrack.ui.components.SquircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aiexile.animetrack.R
import com.aiexile.animetrack.data.SettingsRepository
import com.aiexile.animetrack.util.formatDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDAVSyncScreen(
    settingsRepository: SettingsRepository,
    onBack: () -> Unit,
    onNavigateAutoSync: () -> Unit = {}
) {
    val viewModel: DataManageViewModel = viewModel(factory = DataManageViewModel.Factory())
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val lastSyncTime by settingsRepository.webdavLastSyncTime.collectAsState(0L)
    val lastAutoSyncTime by settingsRepository.webdavLastAutoSyncTime.collectAsState(0L)

    val webdavUrl by viewModel.webdavUrl.collectAsState()
    val webdavUsername by viewModel.webdavUsername.collectAsState()
    val webdavPassword by viewModel.webdavPassword.collectAsState()
    val backupStrategy by viewModel.backupStrategy.collectAsState()
    val restoreMode by viewModel.restoreMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val loadingMessage by viewModel.loadingMessage.collectAsState()
    val snackbarMessage by viewModel.snackbarMessage.collectAsState()

    var configLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadConfig()
        configLoaded = true
    }

    if (configLoaded) {
        LaunchedEffect(backupStrategy, restoreMode) {
            viewModel.saveConfig()
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
            viewModel.clearSnackbar()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.webdav_sync_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                item {
                    SettingsGroup(title = stringResource(R.string.webdav_sync_title)) {
                        Column {
                            OutlinedTextField(
                                value = webdavUrl,
                                onValueChange = { viewModel.webdavUrl.value = it },
                                label = { Text(stringResource(R.string.webdav_sync_server_address)) },
                                placeholder = { Text("https://dav.jianguoyun.com/dav/") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = webdavUsername,
                                onValueChange = { viewModel.webdavUsername.value = it },
                                label = { Text(stringResource(R.string.webdav_sync_username)) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = webdavPassword,
                                onValueChange = { viewModel.webdavPassword.value = it },
                                label = { Text(stringResource(R.string.webdav_sync_password)) },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    keyboardController?.hide()
                                    viewModel.testConnection()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = SquircleShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Text(stringResource(R.string.webdav_sync_test_connection))
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.webdav_sync_backup_strategy),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = stringResource(R.string.webdav_sync_strategy_affects_restore),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = backupStrategy == 0,
                                    onClick = { viewModel.backupStrategy.value = 0 }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.backupStrategy.value = 0 }
                                ) {
                                    Text(text = stringResource(R.string.webdav_sync_format_json))
                                    Text(
                                        text = stringResource(R.string.webdav_sync_format_json_desc),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                RadioButton(
                                    selected = backupStrategy == 1,
                                    onClick = { viewModel.backupStrategy.value = 1 }
                                )
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.backupStrategy.value = 1 }
                                ) {
                                    Text(text = stringResource(R.string.webdav_sync_format_zip))
                                    Text(
                                        text = stringResource(R.string.webdav_sync_format_zip_desc),
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = stringResource(R.string.webdav_sync_restore_mode),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = restoreMode == 0,
                                    onClick = { viewModel.restoreMode.value = 0 }
                                )
                                Text(
                                    text = stringResource(R.string.webdav_sync_restore_overwrite),
                                    modifier = Modifier.clickable { viewModel.restoreMode.value = 0 }
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                RadioButton(
                                    selected = restoreMode == 1,
                                    onClick = { viewModel.restoreMode.value = 1 }
                                )
                                Text(
                                    text = stringResource(R.string.webdav_sync_restore_merge),
                                    modifier = Modifier.clickable { viewModel.restoreMode.value = 1 }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.backupNow() },
                                    modifier = Modifier.weight(1f),
                                    shape = SquircleShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.webdav_sync_backup_now))
                                }
                                Button(
                                    onClick = { viewModel.restoreNow() },
                                    modifier = Modifier.weight(1f),
                                    shape = SquircleShape(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(stringResource(R.string.webdav_sync_restore_now))
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = onNavigateAutoSync,
                                modifier = Modifier.fillMaxWidth(),
                                shape = SquircleShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CloudUpload,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.webdav_sync_auto_sync))
                            }

                            if (lastSyncTime > 0L) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = stringResource(R.string.webdav_sync_last_manual_sync, formatDateTime(lastSyncTime)),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (lastAutoSyncTime > 0L) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.webdav_sync_last_auto_sync, formatDateTime(lastAutoSyncTime)),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(80.dp)) }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = loadingMessage,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}
