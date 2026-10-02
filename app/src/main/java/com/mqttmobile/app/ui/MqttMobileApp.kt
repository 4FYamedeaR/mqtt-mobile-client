@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.mqttmobile.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mqttmobile.app.service.MqttKeepAliveService
import com.mqttmobile.app.data.local.CredentialStore
import com.mqttmobile.app.data.model.BrokerProfile
import com.mqttmobile.app.data.model.ConnectionLog
import com.mqttmobile.app.data.model.ConnectionStatus
import com.mqttmobile.app.data.model.LogLevel
import com.mqttmobile.app.data.model.MessageDirection
import com.mqttmobile.app.data.model.MqttMessageRecord
import com.mqttmobile.app.data.model.MqttVersion
import com.mqttmobile.app.data.model.PayloadFormat
import com.mqttmobile.app.data.model.Subscription
import com.mqttmobile.app.util.PayloadFormatter
import com.mqttmobile.app.util.MessageExportFormat
import com.mqttmobile.app.util.MessageExportFormatter
import com.mqttmobile.app.util.CertificateInspector
import com.mqttmobile.app.util.CertificateSummary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.delay

private enum class AppScreen { HOME, EDITOR, WORKSPACE }
private enum class CertificateTarget { CA, CLIENT_CERTIFICATE, CLIENT_PRIVATE_KEY }

private data class PublishDraft(
    val topic: String,
    val payload: String,
    val format: PayloadFormat,
    val qos: Int,
    val retain: Boolean,
    val messageExpirySeconds: Long?,
    val contentType: String?,
    val responseTopic: String?
)

@Composable
fun MqttMobileApp(viewModel: MqttViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var screen by rememberSaveable { mutableStateOf(AppScreen.HOME) }
    var editingProfileId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingExportContent by remember { mutableStateOf<String?>(null) }
    var pendingProfileExport by remember { mutableStateOf<String?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var batteryOptimizationDialogOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = pendingExportContent
        if (content == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        }.onSuccess { viewModel.showMessage("消息已导出") }
            .onFailure { viewModel.showMessage("导出失败：${it.message ?: "无法写入文件"}") }
    }
    val profileExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = pendingProfileExport ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        }.onSuccess { viewModel.showMessage("连接配置已导出（密码和私钥未包含）") }
            .onFailure { viewModel.showMessage("配置导出失败：${it.message ?: "无法写入文件"}") }
    }
    val profileImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val content = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        if (content == null) {
            viewModel.showMessage("配置导入失败：无法读取文件")
        } else {
            viewModel.importProfiles(content)
        }
    }

    BackHandler(enabled = screen != AppScreen.HOME) {
        screen = AppScreen.HOME
    }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeSnackbar()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (screen) {
            AppScreen.HOME -> HomeScreen(
                profiles = state.profiles,
                activeProfileId = state.activeProfileId,
                connectionStatus = state.connectionStatus,
                connectionStatuses = state.connectionStatuses,
                modifier = Modifier.padding(padding),
                onKeepAliveSettings = { batteryOptimizationDialogOpen = true },
                onSettings = { settingsOpen = true },
                onImport = { profileImportLauncher.launch(arrayOf("application/json", "text/plain")) },
                onExport = {
                    pendingProfileExport = viewModel.exportProfiles()
                    profileExportLauncher.launch("mqtt-profiles.json")
                },
                onCreate = { editingProfileId = null; screen = AppScreen.EDITOR },
                onEdit = { profile -> editingProfileId = profile.id; screen = AppScreen.EDITOR },
                onConnect = { profile ->
                    val connectionIsActive = state.activeProfileId == profile.id &&
                        state.connectionStatus in setOf(
                            ConnectionStatus.CONNECTED,
                            ConnectionStatus.CONNECTING,
                            ConnectionStatus.RECONNECTING
                        )
                    if (!connectionIsActive) viewModel.connect(profile)
                    screen = AppScreen.WORKSPACE
                },
                onDelete = viewModel::deleteProfile
            )
            AppScreen.EDITOR -> ProfileEditorScreen(
                initialProfile = state.profiles.firstOrNull { it.id == editingProfileId },
                modifier = Modifier.padding(padding),
                onBack = { screen = AppScreen.HOME },
                onSave = { profile, password, privateKey ->
                    if (viewModel.saveProfile(profile, password, privateKey)) {
                        screen = AppScreen.HOME
                    }
                },
                onSaveAndConnect = { profile, password, privateKey ->
                    if (viewModel.saveProfile(profile, password, privateKey)) {
                        viewModel.connect(profile)
                        screen = AppScreen.WORKSPACE
                    }
                },
                onTestConnection = { profile, password, privateKey ->
                    if (viewModel.saveProfile(profile, password, privateKey)) {
                        viewModel.showMessage("正在测试连接")
                        viewModel.connect(profile)
                        screen = AppScreen.WORKSPACE
                    }
                },
                onClearCredentials = { profileId -> viewModel.clearCredentials(profileId) }
            )
            AppScreen.WORKSPACE -> WorkspaceScreen(
                state = state,
                modifier = Modifier.padding(padding),
                onBack = { screen = AppScreen.HOME },
                onReconnect = { state.activeProfile?.let(viewModel::connect) },
                onDisconnect = viewModel::disconnect,
                onSubscribe = viewModel::addSubscription,
                onUpdateSubscription = viewModel::updateSubscription,
                onRemoveSubscription = viewModel::removeSubscription,
                onViewSubscriptionMessages = viewModel::viewSubscriptionMessages,
                onClearSubscriptionMessageFilter = viewModel::clearSubscriptionMessageFilter,
                onPublish = viewModel::publish,
                onSearchChanged = viewModel::setSearchQuery,
                onDirectionChanged = viewModel::setDirectionFilter,
                onJsonOnlyChanged = viewModel::setJsonOnly,
                onMessageLimitChanged = viewModel::setMessageLimit,
                onSortAscendingChanged = viewModel::setSortAscending,
                onPauseChanged = viewModel::setPaused,
                onClearMessages = viewModel::clearMessages,
                onClearLogs = viewModel::clearLogs,
                onExportMessage = { message ->
                    pendingExportContent = MessageExportFormatter.format(listOf(message), MessageExportFormat.JSON)
                    exportLauncher.launch("mqtt-message-${message.receivedAt}.json")
                },
                onExportMessages = { format ->
                    if (state.visibleMessages.isEmpty()) {
                        viewModel.showMessage("当前没有可导出的消息")
                    } else {
                        pendingExportContent = MessageExportFormatter.format(state.visibleMessages, format)
                        exportLauncher.launch("mqtt-messages-${format.extension}")
                    }
                }
            )
        }
        if (settingsOpen) {
            SettingsDialog(
                defaultDisplayFormat = state.defaultDisplayFormat,
                messageLimit = state.messageLimit,
                logLimit = state.logLimit,
                onDefaultDisplayFormatChanged = viewModel::setDefaultDisplayFormat,
                onMessageLimitChanged = viewModel::setMessageLimit,
                onLogLimitChanged = viewModel::setLogLimit,
                onDismiss = { settingsOpen = false }
            )
        }
        if (batteryOptimizationDialogOpen) {
            BatteryOptimizationDialog(
                onDismiss = { batteryOptimizationDialogOpen = false },
                onOpenSettings = {
                    batteryOptimizationDialogOpen = false
                    MqttKeepAliveService.openBatteryOptimizationSettings(context)
                }
            )
        }
    }
}

@Composable
private fun BatteryOptimizationDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("允许后台运行") },
        text = {
            Text("为了保持 MQTT 连接并接收后台消息，请在系统设置中允许 MQTT 移动客户端在后台运行，并关闭电池优化。")
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) { Text("打开系统设置") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后") }
        }
    )
}

@Composable
private fun SettingsDialog(
    defaultDisplayFormat: PayloadFormat,
    messageLimit: Int,
    logLimit: Int,
    onDefaultDisplayFormatChanged: (PayloadFormat) -> Unit,
    onMessageLimitChanged: (Int) -> Unit,
    onLogLimitChanged: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("默认消息显示格式", style = MaterialTheme.typography.labelLarge)
                ChoiceField(
                    label = "格式",
                    value = defaultDisplayFormat.label,
                    options = PayloadFormat.entries.toList(),
                    text = { it.label },
                    onSelected = onDefaultDisplayFormatChanged
                )
                Text("消息历史保留数量", style = MaterialTheme.typography.labelLarge)
                ChoiceField(
                    label = "数量",
                    value = messageLimit.toString(),
                    options = listOf(100, 500, 1000, 2000, 5000),
                    text = Int::toString,
                    onSelected = onMessageLimitChanged
                )
                Text("日志设置", style = MaterialTheme.typography.labelLarge)
                ChoiceField(
                    label = "日志保留数量",
                    value = logLimit.toString(),
                    options = listOf(100, 300, 1000),
                    text = Int::toString,
                    onSelected = onLogLimitChanged
                )
                HorizontalDivider()
        Text("MQTT 移动客户端 · v0.1.0", style = MaterialTheme.typography.bodySmall)
                Text("用于现场连接、订阅、发布和消息诊断。", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

@Composable
private fun HomeScreen(
    profiles: List<BrokerProfile>,
    activeProfileId: String?,
    connectionStatus: ConnectionStatus,
    connectionStatuses: Map<String, ConnectionStatus>,
    modifier: Modifier = Modifier,
    onKeepAliveSettings: () -> Unit,
    onSettings: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onCreate: () -> Unit,
    onEdit: (BrokerProfile) -> Unit,
    onConnect: (BrokerProfile) -> Unit,
    onDelete: (BrokerProfile) -> Unit
) {
    var profileSearch by rememberSaveable { mutableStateOf("") }
    var topBarMenuExpanded by rememberSaveable { mutableStateOf(false) }
    val isCompactTopBar = LocalConfiguration.current.screenWidthDp < 400
    val visibleProfiles = profiles.filter { profile ->
        profileSearch.isBlank() ||
            profile.name.contains(profileSearch, ignoreCase = true) ||
            profile.host.contains(profileSearch, ignoreCase = true)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "MQTT 移动客户端",
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (isCompactTopBar) {
                        Box {
                            IconButton(onClick = { topBarMenuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(
                                expanded = topBarMenuExpanded,
                                onDismissRequest = { topBarMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("导入") },
                                    onClick = {
                                        topBarMenuExpanded = false
                                        onImport()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("导出") },
                                    onClick = {
                                        topBarMenuExpanded = false
                                        onExport()
                                    }
                                )
                            }
                        }
                    } else {
                        TextButton(onClick = onImport) {
                            Text("导入", maxLines = 1, softWrap = false)
                        }
                        TextButton(onClick = onExport) {
                            Text("导出", maxLines = 1, softWrap = false)
                        }
                    }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "设置") }
                    IconButton(onClick = onKeepAliveSettings) {
                        Icon(Icons.Default.BatteryFull, "保活设置")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Default.Add, "新建连接")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp, padding.calculateTopPadding() + 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("最近连接", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("选择一个 Broker 配置开始查看实时消息", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = profileSearch,
                    onValueChange = { profileSearch = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("搜索配置名称或 Host") },
                    singleLine = true
                )
            }
            if (profiles.isEmpty()) {
                item {
                    EmptyState(
                        icon = Icons.Default.Wifi,
                        title = "还没有 Broker 配置",
                        description = "创建一个连接配置，开始 MQTT 调试。",
                        actionLabel = "新建连接",
                        onAction = onCreate
                    )
                }
            } else if (visibleProfiles.isEmpty()) {
                item { EmptyState(Icons.Default.Search, "没有匹配的配置", "换一个名称或 Host 关键词试试。", null, {}) }
            } else {
                items(visibleProfiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        isActive = profile.id == activeProfileId,
                        connectionStatus = connectionStatuses[profile.id]
                            ?: if (profile.id == activeProfileId) connectionStatus else ConnectionStatus.DISCONNECTED,
                        onConnect = onConnect,
                        onEdit = onEdit,
                        onDelete = onDelete
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(
    profile: BrokerProfile,
    isActive: Boolean,
    connectionStatus: ConnectionStatus,
    onConnect: (BrokerProfile) -> Unit,
    onEdit: (BrokerProfile) -> Unit,
    onDelete: (BrokerProfile) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Link, null, tint = MaterialTheme.colorScheme.primary) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${profile.host}:${profile.port}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "更多") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("编辑") }, onClick = { menuExpanded = false; onEdit(profile) }, leadingIcon = { Icon(Icons.Default.Edit, null) })
                        DropdownMenuItem(text = { Text("删除") }, onClick = { menuExpanded = false; onDelete(profile) }, leadingIcon = { Icon(Icons.Default.Delete, null) })
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallTag(profile.mqttVersion.label)
                SmallTag(if (profile.tlsEnabled) "TLS" else "TCP")
                if (isActive) SmallTag(connectionStatus.label, MaterialTheme.colorScheme.primaryContainer)
                Spacer(Modifier.weight(1f))
                Button(onClick = { onConnect(profile) }) {
                    Icon(Icons.Default.Wifi, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (isActive && connectionStatus == ConnectionStatus.CONNECTED) "查看" else "连接")
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorScreen(
    initialProfile: BrokerProfile?,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onSave: (BrokerProfile, String, String) -> Unit,
    onSaveAndConnect: (BrokerProfile, String, String) -> Unit,
    onTestConnection: (BrokerProfile, String, String) -> Unit,
    onClearCredentials: (String) -> Unit
) {
    val context = LocalContext.current
    val privateKeyAlreadyStored = remember(initialProfile?.id) {
        initialProfile?.id?.let { CredentialStore(context).hasPrivateKey(it) } == true
    }
    var name by remember(initialProfile?.id) { mutableStateOf(initialProfile?.name ?: "") }
    var host by remember(initialProfile?.id) { mutableStateOf(initialProfile?.host ?: "") }
    var port by remember(initialProfile?.id) { mutableStateOf((initialProfile?.port ?: 1883).toString()) }
    var clientId by remember(initialProfile?.id) { mutableStateOf(initialProfile?.clientId ?: "mqtt-mobile-${UUID.randomUUID().toString().take(8)}") }
    var username by remember(initialProfile?.id) { mutableStateOf(initialProfile?.username.orEmpty()) }
    var password by remember(initialProfile?.id) { mutableStateOf("") }
    var version by remember(initialProfile?.id) { mutableStateOf(initialProfile?.mqttVersion ?: MqttVersion.MQTT_5) }
    var tlsEnabled by remember(initialProfile?.id) { mutableStateOf(initialProfile?.tlsEnabled ?: false) }
    var keepAlive by remember(initialProfile?.id) { mutableStateOf((initialProfile?.keepAliveSeconds ?: 60).toString()) }
    var cleanStart by remember(initialProfile?.id) { mutableStateOf(initialProfile?.cleanStart ?: true) }
    var autoReconnect by remember(initialProfile?.id) { mutableStateOf(initialProfile?.autoReconnect ?: true) }
    var caCertificatePem by remember(initialProfile?.id) { mutableStateOf(initialProfile?.caCertificatePem.orEmpty()) }
    var clientCertificatePem by remember(initialProfile?.id) { mutableStateOf(initialProfile?.clientCertificatePem.orEmpty()) }
    var clientPrivateKeyPem by remember(initialProfile?.id) { mutableStateOf(initialProfile?.clientPrivateKeyPem.orEmpty()) }
    var certificateTarget by remember { mutableStateOf<CertificateTarget?>(null) }
    val pemPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val target = certificateTarget ?: return@rememberLauncherForActivityResult
        val content = uri?.let { selectedUri ->
            runCatching { context.contentResolver.openInputStream(selectedUri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
        } ?: return@rememberLauncherForActivityResult
        when (target) {
            CertificateTarget.CA -> caCertificatePem = content
            CertificateTarget.CLIENT_CERTIFICATE -> clientCertificatePem = content
            CertificateTarget.CLIENT_PRIVATE_KEY -> clientPrivateKeyPem = content
        }
    }
    val caCertificateInfo = remember(caCertificatePem) { CertificateInspector.inspect(caCertificatePem) }
    val clientCertificateInfo = remember(clientCertificatePem) { CertificateInspector.inspect(clientCertificatePem) }

    fun pickCertificate(target: CertificateTarget) {
        certificateTarget = target
        pemPicker.launch(arrayOf("application/x-pem-file", "text/plain", "application/octet-stream"))
    }

    fun currentProfile() = BrokerProfile(
        id = initialProfile?.id ?: UUID.randomUUID().toString(),
        name = name.trim(),
        host = host.trim(),
        port = port.toIntOrNull() ?: if (tlsEnabled) 8883 else 1883,
        mqttVersion = version,
        clientId = clientId.trim().ifBlank { "mqtt-mobile-${UUID.randomUUID().toString().take(8)}" },
        username = username.trim().ifBlank { null },
        tlsEnabled = tlsEnabled,
        caCertificatePem = caCertificatePem.trim().ifBlank { null },
        clientCertificatePem = clientCertificatePem.trim().ifBlank { null },
        clientPrivateKeyPem = clientPrivateKeyPem.trim().ifBlank { null },
        keepAliveSeconds = keepAlive.toIntOrNull()?.coerceIn(0, 65535) ?: 60,
        cleanStart = cleanStart,
        autoReconnect = autoReconnect
    )

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(if (initialProfile == null) "新建连接" else "编辑连接") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                actions = { TextButton(onClick = { onSave(currentProfile(), password, clientPrivateKeyPem) }) { Text("保存") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionTitle("Broker", "连接地址与协议")
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("配置名称") }, singleLine = true)
            OutlinedTextField(host, { host = it }, Modifier.fillMaxWidth(), label = { Text("Host") }, placeholder = { Text("broker.example.com") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(port, { port = it.filter(Char::isDigit) }, Modifier.weight(1f), label = { Text("Port") }, singleLine = true)
                ChoiceField("协议版本", version.label, MqttVersion.entries.toList(), { it.label }, { version = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("TLS / MQTTS", fontWeight = FontWeight.SemiBold); Text("默认校验证书", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(checked = tlsEnabled, onCheckedChange = { enabled -> tlsEnabled = enabled; if (port == "1883" || port == "8883") port = if (enabled) "8883" else "1883" })
            }
            if (tlsEnabled) {
                SectionTitle("TLS 证书（可选）", "导入 PEM 文件；同时填写客户端证书和私钥即可启用双向 TLS")
                CertificatePickerRow("自定义 CA", caCertificatePem.isNotBlank(), caCertificateInfo, { pickCertificate(CertificateTarget.CA) })
                CertificatePickerRow("客户端证书", clientCertificatePem.isNotBlank(), clientCertificateInfo, { pickCertificate(CertificateTarget.CLIENT_CERTIFICATE) })
                CertificatePickerRow("客户端私钥", clientPrivateKeyPem.isNotBlank() || privateKeyAlreadyStored, null, { pickCertificate(CertificateTarget.CLIENT_PRIVATE_KEY) })
            }

            SectionTitle("认证", "密码保存在 Android Keystore 加密存储中")
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("用户名（可选）") }, singleLine = true)
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text(if (initialProfile == null) "密码（可选）" else "密码（留空保持不变）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            if (initialProfile != null) {
                TextButton(onClick = { onClearCredentials(initialProfile.id) }) { Text("清除已保存密码和客户端私钥") }
            }

            SectionTitle("MQTT 高级选项", "连接会话与重连行为")
            OutlinedTextField(clientId, { clientId = it }, Modifier.fillMaxWidth(), label = { Text("Client ID") }, supportingText = { Text("Client ID 必须保持唯一，否则可能踢掉其他客户端") }, singleLine = true)
            OutlinedTextField(keepAlive, { keepAlive = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("Keep Alive（秒）") }, singleLine = true)
            ToggleRow("Clean Start", "连接时创建新的会话", cleanStart) { cleanStart = it }
            ToggleRow("自动重连", "网络恢复后自动重试并恢复连接", autoReconnect) { autoReconnect = it }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { onTestConnection(currentProfile(), password, clientPrivateKeyPem) },
                    enabled = name.isNotBlank() && host.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Text("测试连接") }
                Button(
                    onClick = { onSaveAndConnect(currentProfile(), password, clientPrivateKeyPem) },
                    enabled = name.isNotBlank() && host.isNotBlank(),
                    modifier = Modifier.weight(1f).height(52.dp)
                ) { Icon(Icons.Default.Wifi, null); Spacer(Modifier.width(8.dp)); Text("保存并连接") }
            }
        }
    }
}

@Composable
private fun WorkspaceScreen(
    state: MqttUiState,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onReconnect: () -> Unit,
    onDisconnect: () -> Unit,
    onSubscribe: (String, Int, Boolean, Boolean, Int) -> Unit,
    onUpdateSubscription: (Subscription, String, Int, Boolean, Boolean, Int) -> Unit,
    onRemoveSubscription: (com.mqttmobile.app.data.model.Subscription) -> Unit,
    onViewSubscriptionMessages: (Subscription) -> Unit,
    onClearSubscriptionMessageFilter: () -> Unit,
    onPublish: (String, String, PayloadFormat, Int, Boolean, Long?, String?, String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onDirectionChanged: (MessageDirection?) -> Unit,
    onJsonOnlyChanged: (Boolean) -> Unit,
    onMessageLimitChanged: (Int) -> Unit,
    onSortAscendingChanged: (Boolean) -> Unit,
    onPauseChanged: (Boolean) -> Unit,
    onClearMessages: () -> Unit,
    onClearLogs: () -> Unit,
    onExportMessage: (MqttMessageRecord) -> Unit,
    onExportMessages: (MessageExportFormat) -> Unit
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedMessage by remember { mutableStateOf<MqttMessageRecord?>(null) }
    var publishPrefill by remember { mutableStateOf<MqttMessageRecord?>(null) }
    val isTablet = LocalConfiguration.current.screenWidthDp >= 600
    val tabs = listOf("消息", "订阅", "发布", "日志")
    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(state.activeProfile?.name ?: "MQTT 工作区", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(state.connectionStatus)
                                Spacer(Modifier.width(6.dp))
                                AnimatedContent(
                                    targetState = state.connectionStatus,
                                    transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                                    label = "connection-status-label"
                                ) { status ->
                                    Text(status.label, style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") } },
                    actions = {
                        if (state.connectionStatus == ConnectionStatus.CONNECTED || state.connectionStatus == ConnectionStatus.RECONNECTING) {
                            IconButton(onClick = onDisconnect) { Icon(Icons.Default.Close, "断开") }
                        } else {
                            IconButton(onClick = onReconnect) { Icon(Icons.Default.Refresh, "重连") }
                        }
                    }
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, label -> Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label) }) }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("订阅 ${state.subscriptions.size}", style = MaterialTheme.typography.labelSmall)
                    Text("消息 ${state.messages.size}/${state.messageLimit}", style = MaterialTheme.typography.labelSmall)
                    Text("接收 ${state.messages.count { it.direction == MessageDirection.RECEIVED }}", style = MaterialTheme.typography.labelSmall)
                    Text("发布 ${state.messages.count { it.direction == MessageDirection.PUBLISHED }}", style = MaterialTheme.typography.labelSmall)
                }
                val lastConnection = state.logs.firstOrNull { it.message.startsWith("连接成功") }
                val lastError = state.logs.firstOrNull { it.level == LogLevel.ERROR }
                if (lastConnection != null || lastError != null) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                        lastConnection?.let { Text("最近连接：${formatTime(it.createdAt)}", style = MaterialTheme.typography.labelSmall) }
                        lastError?.let { Text("最近错误：${it.message}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                }
            }
        }
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                if (targetState > initialState) {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 8 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { -it / 12 })
                } else {
                    (fadeIn(tween(220)) + slideInHorizontally(tween(220)) { -it / 8 }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(140)) { it / 12 })
                }
            },
            label = "workspace-tab-content"
        ) { tab ->
            when (tab) {
            0 -> MessagesTab(
                state = state,
                modifier = Modifier.padding(padding),
                onSearchChanged = onSearchChanged,
                onDirectionChanged = onDirectionChanged,
                onJsonOnlyChanged = onJsonOnlyChanged,
                onMessageLimitChanged = onMessageLimitChanged,
                onSortAscendingChanged = onSortAscendingChanged,
                onPauseChanged = onPauseChanged,
                onClearMessages = onClearMessages,
                onClearSubscriptionMessageFilter = onClearSubscriptionMessageFilter,
                onExportMessages = onExportMessages,
                onOpen = { selectedMessage = it },
                selectedMessage = selectedMessage,
                defaultDisplayFormat = state.defaultDisplayFormat,
                isTablet = isTablet,
                onExportMessage = { message -> onExportMessage(message) },
                onDismissSelected = { selectedMessage = null },
                onUseAsPublish = { message ->
                    publishPrefill = message
                    selectedMessage = null
                    selectedTab = 2
                }
            )
            1 -> SubscriptionsTab(state, Modifier.padding(padding), onSubscribe, onUpdateSubscription, onRemoveSubscription) { subscription ->
                onViewSubscriptionMessages(subscription)
                selectedTab = 0
            }
            2 -> PublishTab(
                modifier = Modifier.padding(padding),
                mqttVersion = state.activeProfile?.mqttVersion,
                initialMessage = publishPrefill,
                onPublish = onPublish
            )
            else -> LogsTab(state.logs, state.activeProfile?.name, Modifier.padding(padding), onClearLogs)
            }
        }
    }
    selectedMessage?.takeIf { !isTablet }?.let { message ->
        MessageDetailDialog(
            message = message,
            defaultDisplayFormat = state.defaultDisplayFormat,
            onDismiss = { selectedMessage = null },
            onExport = { onExportMessage(message) },
            onUseAsPublish = {
                publishPrefill = message
                selectedMessage = null
                selectedTab = 2
            }
        )
    }
}

@Composable
private fun MessagesTab(
    state: MqttUiState,
    modifier: Modifier,
    onSearchChanged: (String) -> Unit,
    onDirectionChanged: (MessageDirection?) -> Unit,
    onJsonOnlyChanged: (Boolean) -> Unit,
    onMessageLimitChanged: (Int) -> Unit,
    onSortAscendingChanged: (Boolean) -> Unit,
    onPauseChanged: (Boolean) -> Unit,
    onClearMessages: () -> Unit,
    onClearSubscriptionMessageFilter: () -> Unit,
    onExportMessages: (MessageExportFormat) -> Unit,
    onOpen: (MqttMessageRecord) -> Unit,
    selectedMessage: MqttMessageRecord?,
    defaultDisplayFormat: PayloadFormat,
    isTablet: Boolean,
    onExportMessage: (MqttMessageRecord) -> Unit,
    onDismissSelected: () -> Unit,
    onUseAsPublish: (MqttMessageRecord) -> Unit
) {
    var exportMenuExpanded by remember { mutableStateOf(false) }
    var limitMenuExpanded by remember { mutableStateOf(false) }
    var searchFocused by remember { mutableStateOf(false) }
    val searchScale by animateFloatAsState(
        targetValue = if (searchFocused) 1.008f else 1f,
        animationSpec = tween(180),
        label = "search-focus-scale"
    )
    Box(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(if (isTablet && selectedMessage != null) 0.58f else 1f).fillMaxHeight()) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = onSearchChanged,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .onFocusChanged { searchFocused = it.isFocused }
                            .graphicsLayer {
                                scaleX = searchScale
                                scaleY = searchScale
                            },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索消息") },
                        placeholder = { Text(if (state.subscriptionTopicFilter == null) "按 Topic / Payload 筛选" else "当前订阅筛选中") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onPauseChanged(!state.isPaused) }) {
                            Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, if (state.isPaused) "继续接收" else "暂停接收")
                        }
                        IconButton(onClick = onClearMessages) { Icon(Icons.Default.Clear, "清空消息") }
                        Box {
                            TextButton(onClick = { exportMenuExpanded = true }) { Text("导出") }
                            DropdownMenu(expanded = exportMenuExpanded, onDismissRequest = { exportMenuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("导出 JSON") },
                                    onClick = { exportMenuExpanded = false; onExportMessages(MessageExportFormat.JSON) }
                                )
                                DropdownMenuItem(
                                    text = { Text("导出 CSV") },
                                    onClick = { exportMenuExpanded = false; onExportMessages(MessageExportFormat.CSV) }
                                )
                            }
                        }
                        Box {
                            TextButton(onClick = { limitMenuExpanded = true }) { Text("保留 ${state.messageLimit}") }
                            DropdownMenu(expanded = limitMenuExpanded, onDismissRequest = { limitMenuExpanded = false }) {
                                listOf(100, 500, 1000, 2000, 5000).forEach { limit ->
                                    DropdownMenuItem(
                                        text = { Text("保留 $limit 条") },
                                        onClick = { limitMenuExpanded = false; onMessageLimitChanged(limit) }
                                    )
                                }
                            }
                        }
                        TextButton(onClick = { onSortAscendingChanged(!state.sortAscending) }) {
                            Text(if (state.sortAscending) "正序" else "倒序")
                        }
                    }
                }
                state.subscriptionTopicFilter?.let { filter ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("当前订阅：$filter", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = onClearSubscriptionMessageFilter) { Text("清除") }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(selected = state.directionFilter == null, onClick = { onDirectionChanged(null) }, label = { Text("全部 ${state.messages.size}") })
                    FilterChip(selected = state.directionFilter == MessageDirection.RECEIVED, onClick = { onDirectionChanged(MessageDirection.RECEIVED) }, label = { Text("已接收") })
                    FilterChip(selected = state.directionFilter == MessageDirection.PUBLISHED, onClick = { onDirectionChanged(MessageDirection.PUBLISHED) }, label = { Text("已发布") })
                    FilterChip(selected = state.jsonOnly, onClick = { onJsonOnlyChanged(!state.jsonOnly) }, label = { Text("仅 JSON") })
                }
                HorizontalDivider(Modifier.padding(top = 4.dp))
                if (state.visibleMessages.isEmpty()) {
                    EmptyState(Icons.Default.BugReport, "暂无消息", "连接并订阅 Topic 后，收到的消息会显示在这里。", null, {})
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(state.visibleMessages, key = { _, message -> message.id }) { index, message ->
                            MessageCard(message, onOpen, entranceDelayMillis = (index.coerceAtMost(5)) * 45)
                        }
                    }
                }
            }
            if (isTablet) {
                selectedMessage?.let { message ->
                    MessageDetailPanel(
                        message = message,
                        defaultDisplayFormat = defaultDisplayFormat,
                        modifier = Modifier.weight(0.42f),
                        onExport = { onExportMessage(message) },
                        onUseAsPublish = { onUseAsPublish(message) },
                        onDismiss = onDismissSelected
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: MqttMessageRecord,
    onOpen: (MqttMessageRecord) -> Unit,
    entranceDelayMillis: Int = 0
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(message.id) {
        delay(entranceDelayMillis.toLong())
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(260, delayMillis = entranceDelayMillis)) +
            slideInVertically(tween(280, delayMillis = entranceDelayMillis)) { it / 5 } +
            scaleIn(initialScale = 0.985f, animationSpec = tween(280, delayMillis = entranceDelayMillis)),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.985f, animationSpec = tween(160))
    ) {
        OutlinedCard(modifier = Modifier.fillMaxWidth().clickable { onOpen(message) }) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallTag(message.direction.label, if (message.direction == MessageDirection.RECEIVED) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(message.topic, Modifier.weight(1f), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(formatTime(message.receivedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(7.dp))
                Text(PayloadFormatter.summary(message.payload), maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(7.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SmallTag("QoS ${message.qos}"); if (message.retain) SmallTag("Retain") }
            }
        }
    }
}

@Composable
private fun MessageDetailPanel(
    message: MqttMessageRecord,
    defaultDisplayFormat: PayloadFormat,
    modifier: Modifier,
    onExport: () -> Unit,
    onUseAsPublish: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var displayFormatName by rememberSaveable("panel", message.id, defaultDisplayFormat.name) { mutableStateOf(defaultDisplayFormat.name) }
    var jsonExpanded by rememberSaveable("panel-json", message.id) { mutableStateOf(true) }
    var showFullPayload by rememberSaveable("panel-payload", message.id) { mutableStateOf(false) }
    val displayFormat = runCatching { PayloadFormat.valueOf(displayFormatName) }.getOrDefault(defaultDisplayFormat)
    Surface(modifier = modifier.fillMaxHeight().padding(8.dp), tonalElevation = 2.dp, shape = RoundedCornerShape(16.dp)) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("消息详情", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭详情") }
            }
            DetailRow("Topic", message.topic)
            DetailRow("接收时间", formatTime(message.receivedAt))
            DetailRow("方向", message.direction.label)
            DetailRow("Payload 长度", "${message.payload.size} bytes")
            DetailRow("QoS / Retain", "${message.qos} / ${message.retain}")
            if (message.duplicate) DetailRow("Duplicate", "true")
            if (message.properties.isNotEmpty()) {
                HorizontalDivider()
                Text("MQTT 5 属性", style = MaterialTheme.typography.labelLarge)
                message.properties.forEach { (key, value) -> DetailRow(key, value) }
            }
            HorizontalDivider()
            ChoiceField("显示格式", displayFormat.label, PayloadFormat.entries.toList(), { it.label }) { displayFormatName = it.name }
            if (displayFormat == PayloadFormat.JSON && PayloadFormatter.prettyJson(message.payload) != null) {
                TextButton(onClick = { jsonExpanded = !jsonExpanded }) { Text(if (jsonExpanded) "折叠 JSON" else "展开 JSON") }
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(animationSpec = tween(260))
                    .clickable(onClickLabel = "查看完整 Payload") { showFullPayload = true },
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Payload", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text("点击查看完整", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (displayFormat == PayloadFormat.JSON && PayloadFormatter.jsonValue(message.payload) != null && jsonExpanded) {
                        JsonValueView(PayloadFormatter.jsonValue(message.payload)!!)
                    } else {
                        val value = if (displayFormat == PayloadFormat.JSON && !jsonExpanded) {
                            PayloadFormatter.summary(message.payload, 240)
                        } else {
                            PayloadFormatter.display(message.payload, displayFormat)
                        }
                        Text(value, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { clipboard.setText(AnnotatedString(message.topic)) }) { Text("复制 Topic") }
                TextButton(onClick = { clipboard.setText(AnnotatedString(message.payload.toString(Charsets.UTF_8))) }) { Text("复制 Payload") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onExport) { Text("导出") }
                TextButton(onClick = onUseAsPublish) { Text("使用当前消息发布") }
            }
        }
    }
    if (showFullPayload) {
        FullPayloadDialog(
            message = message,
            displayFormat = displayFormat,
            onDismiss = { showFullPayload = false }
        )
    }
}

@Composable
private fun MessageDetailDialog(
    message: MqttMessageRecord,
    defaultDisplayFormat: PayloadFormat,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onUseAsPublish: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var displayFormatName by rememberSaveable(message.id, defaultDisplayFormat.name) { mutableStateOf(defaultDisplayFormat.name) }
    var jsonExpanded by rememberSaveable(message.id) { mutableStateOf(true) }
    var showFullPayload by rememberSaveable("dialog-payload", message.id) { mutableStateOf(false) }
    val displayFormat = runCatching { PayloadFormat.valueOf(displayFormatName) }.getOrDefault(PayloadFormat.JSON)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(220)) +
                scaleIn(initialScale = 0.94f, animationSpec = tween(280)) +
                slideInVertically(tween(280)) { it / 12 },
            label = "message-detail-dialog-enter"
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("消息详情", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text("查看消息元数据与 Payload", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭详情") }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailRow("Topic", message.topic)
                            DetailRow("接收时间", formatTime(message.receivedAt))
                            DetailRow("方向", message.direction.label)
                            DetailRow("Payload 长度", "${message.payload.size} bytes")
                            DetailRow("QoS / Retain", "${message.qos} / ${message.retain}")
                            if (message.duplicate) DetailRow("Duplicate", "true")
                        }
                    }
                    if (message.properties.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("MQTT 5 属性", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLowest
                            ) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    message.properties.forEach { (key, value) -> DetailRow(key, value) }
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChoiceField("显示格式", displayFormat.label, PayloadFormat.entries.toList(), { it.label }) { displayFormatName = it.name }
                        Spacer(Modifier.weight(1f))
                        if (displayFormat == PayloadFormat.JSON && PayloadFormatter.prettyJson(message.payload) != null) {
                            TextButton(onClick = { jsonExpanded = !jsonExpanded }) {
                                Text(if (jsonExpanded) "折叠 JSON" else "展开 JSON")
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 72.dp, max = 260.dp)
                            .animateContentSize(animationSpec = tween(260))
                            .clickable(onClickLabel = "查看完整 Payload") { showFullPayload = true },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest
                    ) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Payload", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.weight(1f))
                                Text("点击查看完整", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                if (displayFormat == PayloadFormat.JSON && PayloadFormatter.jsonValue(message.payload) != null && jsonExpanded) {
                                    JsonValueView(PayloadFormatter.jsonValue(message.payload)!!)
                                } else {
                                    val value = if (displayFormat == PayloadFormat.JSON && !jsonExpanded) {
                                        PayloadFormatter.summary(message.payload, 240)
                                    } else {
                                        PayloadFormatter.display(message.payload, displayFormat)
                                    }
                                    Text(value, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(message.topic)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("复制 Topic") }
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(message.payload.toString(Charsets.UTF_8))) },
                        modifier = Modifier.weight(1f)
                    ) { Text("复制 Payload") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { clipboard.setText(AnnotatedString(MessageExportFormatter.format(listOf(message), MessageExportFormat.JSON))) },
                        modifier = Modifier.weight(1f)
                    ) { Text("复制全部", maxLines = 1) }
                    TextButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("导出") }
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("关闭") }
                }
                Button(onClick = onUseAsPublish, modifier = Modifier.fillMaxWidth()) {
                    Text("使用当前消息发布")
                }
            }
            }
        }
    }
    if (showFullPayload) {
        FullPayloadDialog(
            message = message,
            displayFormat = displayFormat,
            onDismiss = { showFullPayload = false }
        )
    }
}

@Composable
private fun FullPayloadDialog(
    message: MqttMessageRecord,
    displayFormat: PayloadFormat,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val payloadText = PayloadFormatter.display(message.payload, displayFormat).ifBlank { "（空 Payload）" }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(220)) +
                scaleIn(initialScale = 0.94f, animationSpec = tween(280)) +
                slideInVertically(tween(280)) { it / 12 },
            label = "full-payload-dialog-enter"
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 560.dp)
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("完整 Payload", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${message.payload.size} bytes · ${displayFormat.label}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭完整 Payload") }
                }
                Spacer(Modifier.height(12.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp, max = 560.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = payloadText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(payloadText)) },
                        modifier = Modifier.weight(1f)
                    ) { Text("复制内容") }
                    Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("完成") }
                }
            }
            }
        }
    }
}

@Composable
private fun SubscriptionsTab(
    state: MqttUiState,
    modifier: Modifier,
    onSubscribe: (String, Int, Boolean, Boolean, Int) -> Unit,
    onUpdateSubscription: (Subscription, String, Int, Boolean, Boolean, Int) -> Unit,
    onRemove: (com.mqttmobile.app.data.model.Subscription) -> Unit,
    onViewMessages: (Subscription) -> Unit
) {
    var topic by rememberSaveable { mutableStateOf("") }
    var qos by rememberSaveable { mutableIntStateOf(0) }
    var noLocal by rememberSaveable { mutableStateOf(false) }
    var retainAsPublished by rememberSaveable { mutableStateOf(false) }
    var retainHandling by rememberSaveable { mutableIntStateOf(0) }
    var editingSubscription by remember { mutableStateOf<Subscription?>(null) }
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("订阅管理", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(topic, { topic = it }, Modifier.weight(1f), label = { Text("Topic Filter") }, placeholder = { Text("device/+/report") }, singleLine = true)
            ChoiceField("QoS", qos.toString(), (0..2).toList(), Int::toString, { qos = it })
        }
        if (state.activeProfile?.mqttVersion == MqttVersion.MQTT_5) {
            Text("MQTT 5 高级订阅选项", style = MaterialTheme.typography.labelLarge)
            ToggleRow("No Local", "不接收此客户端自己发布的消息", noLocal) { noLocal = it }
            ToggleRow("Retain As Published", "保留 Broker 返回的 Retain 标志", retainAsPublished) { retainAsPublished = it }
            ChoiceField(
                "Retain Handling",
                when (retainHandling) {
                    1 -> "仅首次订阅发送 Retained"
                    2 -> "不发送 Retained"
                    else -> "始终发送 Retained"
                },
                listOf(0, 1, 2),
                { value -> when (value) { 1 -> "仅首次订阅发送 Retained"; 2 -> "不发送 Retained"; else -> "始终发送 Retained" } },
                { retainHandling = it }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    editingSubscription?.let { onUpdateSubscription(it, topic, qos, noLocal, retainAsPublished, retainHandling) }
                        ?: onSubscribe(topic, qos, noLocal, retainAsPublished, retainHandling)
                    topic = ""
                    qos = 0
                    noLocal = false
                    retainAsPublished = false
                    retainHandling = 0
                    editingSubscription = null
                },
                modifier = Modifier.weight(1f),
                enabled = topic.isNotBlank()
            ) { Icon(Icons.Default.Subscriptions, null); Spacer(Modifier.width(8.dp)); Text(if (editingSubscription == null) "添加订阅" else "保存修改") }
            if (editingSubscription != null) {
                OutlinedButton(onClick = { editingSubscription = null; topic = ""; qos = 0; noLocal = false; retainAsPublished = false; retainHandling = 0 }) { Text("取消") }
            }
        }
        HorizontalDivider()
        if (state.subscriptions.isEmpty()) {
            EmptyState(Icons.Default.Subscriptions, "暂无订阅", "添加 Topic Filter 后即可接收消息。", null, {})
        } else {
            state.subscriptions.forEach { subscription ->
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth().clickable { onViewMessages(subscription) }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(subscription.topicFilter, fontWeight = FontWeight.SemiBold); Text("QoS ${subscription.qos}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        IconButton(onClick = {
                            editingSubscription = subscription
                            topic = subscription.topicFilter
                            qos = subscription.qos
                            noLocal = subscription.noLocal
                            retainAsPublished = subscription.retainAsPublished
                            retainHandling = subscription.retainHandling
                        }) { Icon(Icons.Default.Edit, "编辑订阅") }
                        IconButton(onClick = { onRemove(subscription) }) { Icon(Icons.Default.Delete, "取消订阅") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PublishTab(
    modifier: Modifier,
    mqttVersion: MqttVersion?,
    initialMessage: MqttMessageRecord?,
    onPublish: (String, String, PayloadFormat, Int, Boolean, Long?, String?, String?) -> Unit
) {
    var topic by rememberSaveable { mutableStateOf("") }
    var payload by rememberSaveable { mutableStateOf("{\n  \"command\": \"restart\"\n}") }
    var format by rememberSaveable { mutableStateOf(PayloadFormat.JSON) }
    var qos by rememberSaveable { mutableIntStateOf(0) }
    var retain by rememberSaveable { mutableStateOf(false) }
    var messageExpiry by rememberSaveable { mutableStateOf("") }
    var contentType by rememberSaveable { mutableStateOf("") }
    var responseTopic by rememberSaveable { mutableStateOf("") }
    var pendingPublish by remember { mutableStateOf<PublishDraft?>(null) }
    LaunchedEffect(initialMessage?.id) {
        initialMessage?.let { message ->
            topic = message.topic
            payload = message.payload.toString(Charsets.UTF_8)
            format = if (PayloadFormatter.prettyJson(message.payload) != null) PayloadFormat.JSON else PayloadFormat.TEXT
            qos = message.qos
            retain = message.retain
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("发布测试消息", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(topic, { topic = it }, Modifier.fillMaxWidth(), label = { Text("Topic") }, placeholder = { Text("device/demo/command") }, singleLine = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ChoiceField("Payload 格式", format.label, PayloadFormat.entries.toList(), { it.label }, { format = it })
            ChoiceField("QoS", qos.toString(), (0..2).toList(), Int::toString, { qos = it })
        }
        OutlinedTextField(payload, { payload = it }, Modifier.fillMaxWidth().height(220.dp), label = { Text("Payload") }, textStyle = androidx.compose.ui.text.TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Retain", fontWeight = FontWeight.SemiBold); Text("让 Broker 保存此 Topic 的最后一条消息", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Switch(checked = retain, onCheckedChange = { retain = it })
        }
        if (mqttVersion == MqttVersion.MQTT_5) {
            Text("MQTT 5 消息属性", style = MaterialTheme.typography.labelLarge)
            OutlinedTextField(
                value = messageExpiry,
                onValueChange = { messageExpiry = it.filter(Char::isDigit) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Message Expiry（秒，可选）") },
                supportingText = { Text("留空表示不设置，最大 4294967295 秒") },
                singleLine = true
            )
            OutlinedTextField(
                value = contentType,
                onValueChange = { contentType = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Content Type（可选）") },
                placeholder = { Text("application/json") },
                singleLine = true
            )
            OutlinedTextField(
                value = responseTopic,
                onValueChange = { responseTopic = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Response Topic（可选）") },
                singleLine = true
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { PayloadFormatter.prettyJson(payload.toByteArray())?.let { payload = it } }, enabled = format == PayloadFormat.JSON) { Text("格式化 JSON") }
            OutlinedButton(onClick = { PayloadFormatter.compactJson(payload)?.let { payload = it } }, enabled = format == PayloadFormat.JSON) { Text("压缩 JSON") }
            Button(
                onClick = {
                    pendingPublish = PublishDraft(
                        topic = topic,
                        payload = payload,
                        format = format,
                        qos = qos,
                        retain = retain,
                        messageExpirySeconds = messageExpiry.toLongOrNull(),
                        contentType = contentType.trim().ifBlank { null },
                        responseTopic = responseTopic.trim().ifBlank { null }
                    )
                },
                enabled = topic.isNotBlank(),
                modifier = Modifier.weight(1f)
            ) { Icon(Icons.AutoMirrored.Filled.Send, null); Spacer(Modifier.width(8.dp)); Text("发布") }
        }
        if (topic.isNotBlank()) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Column(Modifier.padding(14.dp)) {
                    Text("发布前确认", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Topic: $topic\nQoS: $qos · Retain: $retain" +
                            if (mqttVersion == MqttVersion.MQTT_5) "\nExpiry: ${messageExpiry.ifBlank { "未设置" }} · Content-Type: ${contentType.ifBlank { "未设置" }}" else "",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
    pendingPublish?.let { draft ->
        AlertDialog(
            onDismissRequest = { pendingPublish = null },
            title = { Text("确认发布消息") },
            text = {
                Text(
                    buildString {
                        append("即将发布到：\n")
                        append("Topic: ${draft.topic}\n")
                        append("QoS: ${draft.qos} · Retain: ${draft.retain}\n")
                        if (mqttVersion == MqttVersion.MQTT_5) {
                            append("Expiry: ${draft.messageExpirySeconds?.let { "${it}s" } ?: "未设置"}\n")
                            append("Content Type: ${draft.contentType ?: "未设置"}\n")
                            append("Response Topic: ${draft.responseTopic ?: "未设置"}\n")
                        }
                        append("Payload：${PayloadFormatter.summary(draft.payload.toByteArray(Charsets.UTF_8), 240)}")
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPublish(
                            draft.topic,
                            draft.payload,
                            draft.format,
                            draft.qos,
                            draft.retain,
                            draft.messageExpirySeconds,
                            draft.contentType,
                            draft.responseTopic
                        )
                        pendingPublish = null
                    }
                ) { Text("确认发布") }
            },
            dismissButton = { TextButton(onClick = { pendingPublish = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun LogsTab(logs: List<ConnectionLog>, profileName: String?, modifier: Modifier, onClearLogs: () -> Unit) {
    if (logs.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            EmptyState(Icons.Default.BugReport, "暂无连接日志", "连接、订阅和发布结果会显示在这里。", null, {})
        }
    } else {
        Column(modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("连接日志（当前会话）", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClearLogs) { Text("清空") }
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(logs, key = { it.id }) { log ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        val status = when (log.level) {
                            LogLevel.ERROR -> ConnectionStatus.FAILED
                            LogLevel.WARNING -> ConnectionStatus.RECONNECTING
                            else -> ConnectionStatus.CONNECTED
                        }
                        StatusDot(status)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SmallTag(log.level.label)
                                Text(formatTime(log.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(log.message)
                            Text(
                                "配置=${profileName ?: log.profileId}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> ChoiceField(label: String, value: String, options: List<T>, text: (T) -> String, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text("$label: $value", maxLines = 1) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(text(option)) }, onClick = { expanded = false; onSelected(option) }) }
        }
    }
}

@Composable
private fun ToggleRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(label, fontWeight = FontWeight.SemiBold); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun CertificatePickerRow(label: String, selected: Boolean, summary: CertificateSummary?, onPick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    summary != null -> "主体：${summary.subject}\n有效期至：${summary.validUntil}\n指纹：${summary.fingerprint}"
                    selected -> "已导入 PEM"
                    else -> "未选择"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        OutlinedButton(onClick = onPick) { Text(if (selected) "重新选择" else "选择文件") }
    }
}

@Composable
private fun SectionTitle(title: String, description: String) {
    Column(Modifier.padding(top = 8.dp)) { Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun SmallTag(text: String, color: Color = MaterialTheme.colorScheme.secondaryContainer) {
    Surface(shape = RoundedCornerShape(50), color = color) { Text(text, Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall) }
}

@Composable
private fun StatusDot(status: ConnectionStatus) {
    val color = when (status) {
        ConnectionStatus.CONNECTED -> Color(0xFF2E9B65)
        ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> Color(0xFFE19A25)
        ConnectionStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.outline
    }
    val pulseTarget = when (status) {
        ConnectionStatus.CONNECTED -> 1.28f
        ConnectionStatus.CONNECTING, ConnectionStatus.RECONNECTING -> 1.16f
        else -> 1f
    }
    val pulse by rememberInfiniteTransition(label = "connection-dot-pulse").animateFloat(
        initialValue = 1f,
        targetValue = pulseTarget,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "connection-dot-scale"
    )
    Box(
        Modifier
            .size(8.dp)
            .graphicsLayer {
                scaleX = pulse
                scaleY = pulse
            }
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun EmptyState(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String, actionLabel: String?, onAction: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionLabel != null) Button(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun JsonValueView(value: Any?, depth: Int = 0) {
    when (value) {
        is org.json.JSONObject -> {
            val entries = value.keys().asSequence().toList()
            var expanded by rememberSaveable("json-object", value.toString(), depth) { mutableStateOf(true) }
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = (depth * 12).dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (expanded) "▾" else "▸", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Spacer(Modifier.width(6.dp))
                    Text(if (expanded) "对象 {" else "对象 {…}", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                if (expanded) {
                    entries.forEach { key ->
                        Row(Modifier.fillMaxWidth().padding(start = ((depth + 1) * 12).dp)) {
                            Text("$key: ", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            JsonValueView(value.opt(key), depth + 1)
                        }
                    }
                    Text("}".padStart(depth * 2 + 1), Modifier.padding(start = (depth * 12).dp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
        is org.json.JSONArray -> {
            var expanded by rememberSaveable("json-array", value.toString(), depth) { mutableStateOf(true) }
            Column(Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(start = (depth * 12).dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (expanded) "▾" else "▸", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                    Spacer(Modifier.width(6.dp))
                    Text(if (expanded) "数组 [" else "数组 […]", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
                if (expanded) {
                    for (index in 0 until value.length()) {
                        Row(Modifier.fillMaxWidth().padding(start = ((depth + 1) * 12).dp)) {
                            Text("[$index] ", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                            JsonValueView(value.opt(index), depth + 1)
                        }
                    }
                    Text("]".padStart(depth * 2 + 1), Modifier.padding(start = (depth * 12).dp), fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
                }
            }
        }
        org.json.JSONObject.NULL -> Text("null", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        else -> Text(value?.toString() ?: "null", fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
    }
}

private fun formatTime(timestamp: Long): String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
