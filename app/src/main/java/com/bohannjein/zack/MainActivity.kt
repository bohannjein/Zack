package com.bohannjein.zack

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.work.*
import com.bohannjein.zack.ui.components.EmptyStateHistory
import com.bohannjein.zack.ui.components.EmptyStateServers
import com.bohannjein.zack.utils.isValidPort
import com.bohannjein.zack.ui.components.ZackTextField
import com.bohannjein.zack.utils.NsdHelper
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.bohannjein.zack.utils.SecureStorage
import com.bohannjein.zack.utils.isIpChar
import com.bohannjein.zack.utils.isValidIp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val ZackYellow = Color(0xFFFFD54F)
val ZackAmber = Color(0xFFFFB300)

data class AccentPreset(val color: Color, val onColor: Color, val name: String)
val accentPresets = listOf(
    AccentPreset(Color(0xFFFFB300), Color.Black,  "Amber"),
    AccentPreset(Color(0xFF2196F3), Color.White,  "Blue"),
    AccentPreset(Color(0xFF4CAF50), Color.Black,  "Green"),
    AccentPreset(Color(0xFF9C27B0), Color.White,  "Purple"),
    AccentPreset(Color(0xFFF44336), Color.White,  "Red"),
    AccentPreset(Color(0xFF00BCD4), Color.Black,  "Teal"),
    AccentPreset(Color(0xFFFF5722), Color.White,  "Deep Orange"),
    AccentPreset(Color(0xFFE91E63), Color.White,  "Pink"),
)

// Note: isIpChar() moved to utils/ValidationUtils.kt

fun Context.findActivity(): FragmentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}

// Note: NsdHelper moved to utils/NsdHelper.kt

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val sharedUris = mutableListOf<Uri>()
        intent?.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.let { sharedUris.add(it) }
        intent?.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { sharedUris.addAll(it) }

        setContent {
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("zack_settings", Context.MODE_PRIVATE) }

            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                prefs.edit().putBoolean("notifications_enabled", isGranted).apply()
            }
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            var themeMode by remember { mutableIntStateOf(prefs.getInt("theme_mode", 0)) }
            var accentIndex by remember { mutableIntStateOf(prefs.getInt("accent_color", 0).coerceIn(accentPresets.indices)) }
            val accent = accentPresets[accentIndex]
            val useDark = when (themeMode) { 1 -> false; 2, 3 -> true; else -> isSystemInDarkTheme() }
            val isAmoled = themeMode == 3
            var isAuthorized by remember { mutableStateOf(!prefs.getBoolean("app_lock_enabled", false)) }

            SideEffect {
                context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !useDark }
            }

            val colors = if (useDark) {
                if (isAmoled) darkColorScheme(primary = accent.color, onPrimary = accent.onColor, secondary = accent.color, background = Color.Black, onBackground = Color.White, surface = Color.Black, onSurface = Color.White, surfaceVariant = Color(0xFF0D0D0D), surfaceContainer = Color.Black, surfaceContainerLow = Color.Black, surfaceContainerLowest = Color.Black, surfaceContainerHigh = Color(0xFF111111), surfaceContainerHighest = Color(0xFF1A1A1A))
                else darkColorScheme(primary = accent.color, onPrimary = accent.onColor, secondary = accent.color, surface = Color(0xFF141414), surfaceContainer = Color(0xFF1E1E1E))
            } else lightColorScheme(primary = accent.color, onPrimary = accent.onColor, secondary = accent.color, surface = Color(0xFFF9F9F9), surfaceContainer = Color(0xFFFFFFFF))

            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!isAuthorized) LockScreen { isAuthorized = true }
                    else if (sharedUris.isNotEmpty()) ShareBottomSheetScreen(sharedUris)
                    else MainNavigationContainer(
                        themeMode, { m -> themeMode = m; prefs.edit().putInt("theme_mode", m).apply() },
                        accentIndex, { i -> accentIndex = i; prefs.edit().putInt("accent_color", i).apply() },
                        { finish() }
                    )
                }
            }
        }
    }
}

// Enum für Screen Management
enum class ScreenState { HOME, SETTINGS, SETUP, PROTOCOL_SELECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationContainer(themeMode: Int, onThemeChange: (Int) -> Unit, accentIndex: Int, onAccentChange: (Int) -> Unit, onExit: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { DatabaseInstance.get(context) }
    val prefs = remember { context.getSharedPreferences("zack_settings", Context.MODE_PRIVATE) }

    var currentScreen by remember { mutableStateOf(ScreenState.HOME) }
    var serverToEdit by remember { mutableStateOf<NetworkServer?>(null) }

    val selectedIds = remember { mutableStateListOf<Long>() }
    val history by db.historyDao().getAllHistory().collectAsState(initial = emptyList())
    val servers by db.serverDao().getAllServers().collectAsState(initial = emptyList())
    val uploadingCount = history.count { it.status == "Uploading" }

    LaunchedEffect(Unit) {
        val days = prefs.getInt("history_retention_days", 0)
        if (days > 0) {
            val cutoff = System.currentTimeMillis() - days * 86_400_000L
            db.historyDao().deleteOlderThan(cutoff)
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (!uris.isNullOrEmpty()) scope.launch(Dispatchers.IO) {
            val target = db.serverDao().getDefaultServer() ?: db.serverDao().getFirstServer()
            target?.let { prepareAndUpload(context, it.id, uris) }
        }
    }

    BackHandler {
        if (selectedIds.isNotEmpty()) selectedIds.clear()
        else if (currentScreen != ScreenState.HOME) {
            currentScreen = ScreenState.HOME; serverToEdit = null
        }
        else if (pagerState.currentPage != 0) scope.launch { pagerState.animateScrollToPage(0) }
        else onExit()
    }

    AnimatedContent(
        targetState = currentScreen,
        transitionSpec = {
            fadeIn(animationSpec = tween(400)) togetherWith
                    fadeOut(animationSpec = tween(400))
        },
        label = "ScreenTransition"
    ) { targetScreen ->
        when (targetScreen) {
            ScreenState.SETTINGS -> SettingsScreen(themeMode, onThemeChange, accentIndex, onAccentChange, { currentScreen = ScreenState.HOME })
            ScreenState.PROTOCOL_SELECT -> ProtocolSelectionDialog(
                onProtocolSelected = { protocol ->
                    serverToEdit = NetworkServer(protocol = protocol, displayName = "", hostIp = "", port = "", shareName = "")
                    currentScreen = ScreenState.SETUP
                },
                onDismiss = { currentScreen = ScreenState.HOME }
            )
            ScreenState.SETUP -> ServerSetupScreen(serverToEdit, { currentScreen = ScreenState.HOME })
            ScreenState.HOME -> {
                Scaffold(
                    topBar = {
                        LargeTopAppBar(
                            title = {
                                Text(
                                    if (selectedIds.isNotEmpty()) stringResource(R.string.items_selected, selectedIds.size)
                                    else if (pagerState.currentPage == 0) stringResource(R.string.screen_history)
                                    else stringResource(R.string.screen_servers),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { if (selectedIds.isNotEmpty()) selectedIds.clear() else currentScreen = ScreenState.SETTINGS }) {
                                    Icon(
                                        imageVector = if (selectedIds.isNotEmpty()) Icons.Filled.Close else Icons.Filled.Settings,
                                        contentDescription = if (selectedIds.isNotEmpty()) stringResource(R.string.cd_close) else stringResource(R.string.cd_settings)
                                    )
                                }
                            },
                            actions = {
                                if (selectedIds.isEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = CircleShape,
                                        modifier = Modifier
                                            .padding(end = 16.dp)
                                            .size(40.dp)
                                            .clickable {
                                                if (pagerState.currentPage == 0) picker.launch(arrayOf("*/*"))
                                                else currentScreen = ScreenState.PROTOCOL_SELECT
                                            }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(R.string.cd_add), tint = MaterialTheme.colorScheme.onPrimary)
                                        }
                                    }
                                }
                            }
                        )
                    }
                ) { p ->
                    Box(Modifier.padding(p).fillMaxSize()) {
                        HorizontalPager(state = pagerState) { page ->
                            if (page == 0) ExpressiveListScreen(history.map { it.toZackItem() }, selectedIds, isServer = false, db, emptyState = { EmptyStateHistory() })
                            else ExpressiveListScreen(servers.map { it.toZackItem() }, selectedIds, isServer = true, db, onEdit = { serverToEdit = it; currentScreen = ScreenState.SETUP }, emptyState = { EmptyStateServers(onAddServer = { currentScreen = ScreenState.PROTOCOL_SELECT }) })
                        }

                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                            AnimatedContent(targetState = selectedIds.isNotEmpty(), label = "island") { isSel ->
                                if (isSel) SelectionIsland(selectedIds, pagerState.currentPage, servers, db, history, { serverToEdit = it; currentScreen = ScreenState.SETUP })
                                else CenteredNavPill(pagerState.currentPage, uploadingCount) { scope.launch { pagerState.animateScrollToPage(it) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CenteredNavPill(currentIndex: Int, uploadingCount: Int = 0, onTabClick: (Int) -> Unit) {
    // Pill: 128dp wide, 4dp padding each side → 120dp inner content area.
    // Children start at x=0 within content area. Two 56dp icons + 8dp gap = 120dp.
    // Icon 0 at content-x=0, icon 1 at content-x=64.
    val indicatorOffset by animateDpAsState(targetValue = if (currentIndex == 0) 0.dp else 64.dp, animationSpec = tween(300), label = "glide")
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape, shadowElevation = 6.dp, modifier = Modifier.height(64.dp).width(128.dp)) {
        Box(Modifier.padding(4.dp)) {
            Box(Modifier.offset(x = indicatorOffset).size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(56.dp).clip(CircleShape).clickable { onTabClick(0) },
                    contentAlignment = Alignment.Center
                ) {
                    BadgedBox(badge = {
                        if (uploadingCount > 0) Badge(containerColor = MaterialTheme.colorScheme.error) {
                            Text("$uploadingCount", style = MaterialTheme.typography.labelSmall)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = stringResource(R.string.cd_history),
                            tint = if (currentIndex == 0) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Box(
                    Modifier.size(56.dp).clip(CircleShape).clickable { onTabClick(1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Dns,
                        contentDescription = stringResource(R.string.cd_servers),
                        tint = if (currentIndex == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SelectionIsland(
    selectedIds: MutableList<Long>,
    currentPage: Int,
    servers: List<NetworkServer>,
    db: AppDatabase,
    history: List<UploadEntry>,
    onEdit: (NetworkServer) -> Unit
) {
    val scope = rememberCoroutineScope()
    Surface(color = MaterialTheme.colorScheme.primary, shape = CircleShape, shadowElevation = 8.dp, modifier = Modifier.height(72.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage == 0) {
                IconButton(onClick = {
                    selectedIds.clear()
                    selectedIds.addAll(history.map { it.id })
                }) { Icon(imageVector = Icons.Filled.SelectAll, contentDescription = stringResource(R.string.cd_select_all), tint = MaterialTheme.colorScheme.onPrimary) }
            }

            if (currentPage == 1 && selectedIds.size == 1) {
                IconButton(onClick = { val s = servers.find { it.id == selectedIds.first() }; s?.let { onEdit(it) }; selectedIds.clear() }) { Icon(imageVector = Icons.Filled.Edit, contentDescription = stringResource(R.string.cd_edit), tint = MaterialTheme.colorScheme.onPrimary) }
            }
            IconButton(onClick = { selectedIds.clear() }) { Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close), tint = MaterialTheme.colorScheme.onPrimary) }

            var confirm by remember { mutableStateOf(false) }
            IconButton(onClick = { confirm = true }) { Icon(imageVector = Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.onPrimary) }

            if (confirm) AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text(stringResource(R.string.dialog_delete_title)) },
                confirmButton = {
                    Button(
                        onClick = { scope.launch(Dispatchers.IO) { if (currentPage == 0) db.historyDao().deleteEntries(selectedIds.toList()) else db.serverDao().deleteServers(selectedIds.toList()); withContext(Dispatchers.Main) { selectedIds.clear(); confirm = false } } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(stringResource(R.string.action_delete)) }
                },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.action_cancel)) } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolSelectionDialog(onProtocolSelected: (String) -> Unit, onDismiss: () -> Unit) {
    Scaffold(
        topBar = { LargeTopAppBar(title = { Text(stringResource(R.string.screen_add_server)) }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(imageVector = Icons.Filled.Close, contentDescription = stringResource(R.string.cd_close)) } }) }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text(stringResource(R.string.protocol_select), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 16.dp))

            data class ProtocolEntry(val name: String, val icon: ImageVector, val available: Boolean)
            val protocols = listOf(
                ProtocolEntry("Local Scan", Icons.Filled.Radar, true),
                ProtocolEntry("SMB", Icons.Filled.FolderShared, true),
                ProtocolEntry("SFTP", Icons.Filled.Lock, false),
                ProtocolEntry("FTP", Icons.Filled.Cloud, false),
                ProtocolEntry("WebDAV", Icons.Filled.Http, false)
            )

            protocols.forEach { entry ->
                Card(
                    onClick = { if (entry.available) onProtocolSelected(entry.name) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).alpha(if (entry.available) 1f else 0.45f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    enabled = entry.available
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = entry.icon, contentDescription = stringResource(R.string.cd_protocol_icon), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        if (!entry.available) {
                            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                                Text(stringResource(R.string.protocol_coming_soon), modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupScreen(edit: NetworkServer?, onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var currentProtocol by remember { mutableStateOf(edit?.protocol ?: "SMB") }
    val isLocalScan = currentProtocol == "Local Scan"

    val needsPort = currentProtocol != "SMB" && currentProtocol != "Local Scan"
    val needsDomain = currentProtocol == "SMB"
    val pathLabel = if (currentProtocol == "SMB") "Remote Path" else "Remote Path"

    var name by remember { mutableStateOf(edit?.displayName ?: "") }
    var host by remember { mutableStateOf(edit?.hostIp ?: "") }
    var port by remember { mutableStateOf(edit?.port ?: "") }
    var path by remember { mutableStateOf(edit?.shareName ?: "") }
    var domain by remember { mutableStateOf(edit?.domain ?: "WORKGROUP") }
    var user by remember { mutableStateOf(edit?.username ?: "") }
    var pass by remember { mutableStateOf("") }

    var hostError by remember { mutableStateOf<String?>(null) }
    var pathError by remember { mutableStateOf<String?>(null) }
    var portError by remember { mutableStateOf<String?>(null) }
    var passwordVisible by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    val isEditing = edit != null && edit.id != 0L

    Scaffold(
        topBar = { LargeTopAppBar(title = { Text(if (edit?.id == 0L || edit == null) "New $currentProtocol" else "Edit $currentProtocol") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(imageVector = Icons.Filled.Close, contentDescription = null) } }) }
    ) { p ->
        Column(Modifier.padding(p).padding(horizontal = 24.dp).fillMaxSize().verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(16.dp))

            if (isLocalScan) {
                val nsdHelper = remember { NsdHelper(ctx) }
                val discoveredDevices = remember { mutableStateListOf<Pair<String, String>>() }

                DisposableEffect(Unit) {
                    nsdHelper.startDiscovery { dName, dIp ->
                        if (discoveredDevices.none { it.second == dIp }) {
                            discoveredDevices.add(dName to dIp)
                        }
                    }
                    onDispose { nsdHelper.stopDiscovery() }
                }

                Column(Modifier.fillMaxWidth()) {
                    Text("Scanning for network storages...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 12.dp))

                    if (discoveredDevices.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        }
                    }

                    discoveredDevices.forEach { (deviceName, deviceIp) ->
                        Card(
                            onClick = {
                                host = deviceIp
                                name = deviceName
                                currentProtocol = "SMB"
                            },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                        ) {
                            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Column {
                                    Text(deviceName, fontWeight = FontWeight.Bold)
                                    Text(deviceIp, style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.weight(1f))
                                Icon(imageVector = Icons.Filled.ChevronRight, contentDescription = null, modifier = Modifier.alpha(0.5f))
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    TextButton(onClick = { currentProtocol = "SMB" }, Modifier.align(Alignment.CenterHorizontally)) {
                        Text("Device not found? Enter IP manually", color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else {
                ZackTextField(value = name, onValueChange = { name = it }, label = "Display Name (Optional)", imeAction = ImeAction.Next)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        val focusManager2 = LocalFocusManager.current
                        OutlinedTextField(
                            value = host,
                            onValueChange = { if (it.isIpChar() || currentProtocol != "SMB") { host = it; hostError = null } },
                            label = { Text(if (currentProtocol == "WebDAV") "URL / Host" else "IP / Host") },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            singleLine = true,
                            isError = hostError != null,
                            supportingText = hostError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                            keyboardOptions = KeyboardOptions(keyboardType = if (currentProtocol == "WebDAV") KeyboardType.Uri else KeyboardType.Ascii, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager2.moveFocus(FocusDirection.Down) })
                        )
                    }
                    if (needsPort) {
                        Box(modifier = Modifier.width(110.dp)) {
                            val focusManager3 = LocalFocusManager.current
                            OutlinedTextField(
                                value = port,
                                onValueChange = { if (it.all { c -> c.isDigit() }) { port = it; portError = null } },
                                label = { Text("Port") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                                singleLine = true,
                                isError = portError != null,
                                supportingText = portError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager3.moveFocus(FocusDirection.Down) })
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it; pathError = null },
                    label = { Text(pathLabel) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    singleLine = true,
                    isError = pathError != null,
                    supportingText = pathError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                )

                if (needsDomain) {
                    ZackTextField(value = domain, onValueChange = { domain = it }, label = "Workgroup / Domain", imeAction = ImeAction.Next)
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("Authentication", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(bottom = 8.dp))

                ZackTextField(value = user, onValueChange = { user = it }, label = "Username", imeAction = ImeAction.Next)

                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, contentDescription = null)
                        }
                    },
                    supportingText = if (isEditing) { { Text(stringResource(R.string.server_password_hint_edit), style = MaterialTheme.typography.bodySmall) } } else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                if (currentProtocol == "SMB" && host.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            testing = true; testResult = null
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val testPass = pass.ifBlank { if (isEditing) SecureStorage(ctx).getPassword(edit!!.id) ?: "" else "" }
                                    val config = SmbConfig.builder().withTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                                    SMBClient(config).connect(host).use { conn ->
                                        val auth = AuthenticationContext(user.ifBlank { "guest" }, testPass.toCharArray(), domain.ifBlank { "WORKGROUP" })
                                        conn.authenticate(auth)
                                    }
                                    withContext(Dispatchers.Main) { testResult = ctx.getString(R.string.server_test_success) }
                                } catch (e: Exception) {
                                    val msg = e.message?.lowercase() ?: ""
                                    val friendly = when {
                                        "authentication" in msg || "logon" in msg -> "Wrong username or password"
                                        "timed out" in msg || "timeout" in msg -> "Connection timed out"
                                        "refused" in msg -> "Server not reachable"
                                        else -> "Connection failed"
                                    }
                                    withContext(Dispatchers.Main) { testResult = friendly }
                                } finally {
                                    withContext(Dispatchers.Main) { testing = false }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !testing
                    ) {
                        if (testing) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.server_test_testing))
                        } else {
                            Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.server_test_connection))
                        }
                    }
                    testResult?.let { result ->
                        val isSuccess = result == ctx.getString(R.string.server_test_success)
                        Text(
                            result,
                            color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = {
                        val currentHostError = when {
                            host.isBlank() -> ctx.getString(R.string.error_host_required)
                            currentProtocol == "SMB" && !host.isValidIp() -> ctx.getString(R.string.error_invalid_ip)
                            else -> null
                        }
                        val currentPathError = if (path.isBlank()) ctx.getString(R.string.error_path_required) else null
                        val currentPortError = if (needsPort && port.isNotBlank() && !port.isValidPort()) ctx.getString(R.string.error_invalid_port) else null

                        hostError = currentHostError
                        pathError = currentPathError
                        portError = currentPortError

                        if (currentHostError == null && currentPathError == null && currentPortError == null) {
                            scope.launch(Dispatchers.IO) {
                                val db = DatabaseInstance.get(ctx)
                                val display = if (name.isNotBlank()) name else "$currentProtocol @ $host"
                                val n = NetworkServer(
                                    id = edit?.id ?: 0,
                                    protocol = currentProtocol,
                                    displayName = display,
                                    hostIp = host,
                                    port = port,
                                    shareName = path,
                                    domain = domain,
                                    username = user,
                                    isDefault = edit?.isDefault ?: false
                                )
                                val newId = if (edit?.id == 0L || edit == null) db.serverDao().insertServer(n) else { db.serverDao().updateServer(n); n.id }
                                if (pass.isNotBlank()) SecureStorage(ctx).savePassword(newId, pass)
                                withContext(Dispatchers.Main) { onDismiss() }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) { Text("Save Server", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary) }
            }
            Spacer(Modifier.height(50.dp))
        }
    }
}

// Note: ZackTextField moved to ui/components/ZackTextField.kt

// WICHTIG: Hier ist der Fix für dein Problem!
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpressiveListScreen(items: List<ZackItem>, selectedIds: MutableList<Long>, isServer: Boolean = false, db: AppDatabase? = null, onEdit: (NetworkServer) -> Unit = {}, emptyState: @Composable () -> Unit) {
    if (items.isEmpty()) emptyState() else {
        var refreshing by remember { mutableStateOf(false) }
        val haptic = LocalHapticFeedback.current
        val ctx = LocalContext.current
        val scope = rememberCoroutineScope()

        if (!isServer) {
            val prevUploadingIds = remember { mutableSetOf<Long>() }
            SideEffect {
                val justFinished = items.filter { !it.isUploading && !it.isError && it.id in prevUploadingIds }
                if (justFinished.isNotEmpty()) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                prevUploadingIds.clear()
                prevUploadingIds.addAll(items.filter { it.isUploading }.map { it.id })
            }
        }

        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ refreshing = false }, 800) }) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)) {
                items(items, key = { it.id }) { item ->
                    val selected = selectedIds.contains(item.id)
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart && selectedIds.isEmpty()) {
                                scope.launch(Dispatchers.IO) {
                                    if (isServer) db?.serverDao()?.deleteServers(listOf(item.id))
                                    else db?.historyDao()?.deleteEntries(listOf(item.id))
                                }
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        modifier = Modifier.animateItem(),
                        enableDismissFromStartToEnd = false,
                        backgroundContent = {
                            val color by androidx.compose.animation.animateColorAsState(
                                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surfaceContainer,
                                label = "swipe_bg"
                            )
                            Box(
                                Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(24.dp)).background(color),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete), modifier = Modifier.padding(end = 24.dp), tint = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    ) {
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else if (item.isError) MaterialTheme.colorScheme.errorContainer
                                else MaterialTheme.colorScheme.surfaceContainer
                            ),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(24.dp)).combinedClickable(
                                onClick = { if (selectedIds.isNotEmpty()) { if (selected) selectedIds.remove(item.id) else selectedIds.add(item.id); haptic.performHapticFeedback(HapticFeedbackType.LongPress) } },
                                onLongClick = { if (!selected) { selectedIds.add(item.id); haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }
                            )
                        ) {
                            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)), Alignment.Center) {
                                    if (item.isUploading) {
                                        CircularProgressIndicator(Modifier.size(24.dp), MaterialTheme.colorScheme.primary, 3.dp)
                                    } else if (selected) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Icon(
                                            imageVector = item.icon,
                                            contentDescription = null,
                                            tint = if (item.isError) MaterialTheme.colorScheme.error else if (item.isSpecial) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.title, fontWeight = if (item.isSpecial) FontWeight.Bold else FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                    Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = if (item.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                if (!isServer && item.isUploading && item.workTag.isNotBlank() && selectedIds.isEmpty()) {
                                    IconButton(onClick = {
                                        WorkManager.getInstance(ctx).cancelAllWorkByTag(item.workTag)
                                        scope.launch(Dispatchers.IO) { db?.historyDao()?.updateStatusAndError(item.id, "Failed", "Cancelled") }
                                    }) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.upload_cancel), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                if (isServer && selectedIds.isEmpty()) {
                                    IconButton(onClick = { val s = CoroutineScope(Dispatchers.IO); s.launch { db?.serverDao()?.toggleDefault(item.id, !item.isSpecial) } }) {
                                        Icon(
                                            imageVector = if (item.isSpecial) Icons.Filled.Star else Icons.Outlined.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShareBottomSheetScreen(uris: List<Uri>) {
    val ctx = LocalContext.current; val activity = (ctx as? Activity)
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val db = DatabaseInstance.get(ctx)
            val defaults = db.serverDao().getDefaultServers()
            if (defaults.isNotEmpty()) { defaults.forEach { s -> prepareAndUpload(ctx, s.id, uris) }; withContext(Dispatchers.Main) { Toast.makeText(ctx, "Zack!", Toast.LENGTH_SHORT).show(); activity?.finish() } }
            else if (db.serverDao().getServerCount() == 1) { val s = db.serverDao().getFirstServer(); s?.let { prepareAndUpload(ctx, it.id, uris) }; withContext(Dispatchers.Main) { Toast.makeText(ctx, "Zack!", Toast.LENGTH_SHORT).show(); activity?.finish() } }
        }
    }
    val servers by DatabaseInstance.get(ctx).serverDao().getAllServers().collectAsState(initial = emptyList())
    if (servers.size > 1 && servers.none { it.isDefault }) ServerSelectionSheet(uris, { activity?.finish() }) else Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectionSheet(uris: List<Uri>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val db = remember { DatabaseInstance.get(ctx) }
    val servers by db.serverDao().getAllServers().collectAsState(initial = emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Text("Zack to...", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(24.dp))
            LazyColumn { items(servers) { s -> Card(onClick = { scope.launch { prepareAndUpload(ctx, s.id, uris); onDismiss() } }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Row(Modifier.padding(16.dp)) { Icon(imageVector = Icons.Filled.Storage, contentDescription = null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(16.dp)); Text(s.displayName) } } } }
        }
    }
}

suspend fun prepareAndUpload(context: Context, serverId: Long, uris: List<Uri>) {
    val prefs = context.getSharedPreferences("zack_settings", Context.MODE_PRIVATE)
    val autoRename = prefs.getBoolean("auto_rename", false)

    withContext(Dispatchers.IO) {
        uris.forEach { uri ->
            try {
                var fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst()) cursor.getString(nameIndex) else null
                } ?: "file_${System.currentTimeMillis()}"

                if (autoRename) {
                    val timestamp = SimpleDateFormat("_yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val dotIndex = fileName.lastIndexOf('.')
                    fileName = if (dotIndex != -1) fileName.substring(0, dotIndex) + timestamp + fileName.substring(dotIndex)
                               else fileName + timestamp
                }

                val cacheFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(cacheFile).use { output -> input.copyTo(output) } }
                val workTag = java.util.UUID.randomUUID().toString()
                startUploadWork(context, cacheFile.absolutePath, serverId, workTag)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}

fun startUploadWork(c: Context, filePath: String, serverId: Long, workTag: String) {
    val constraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
    val d = Data.Builder()
        .putString("file_path", filePath)
        .putLong("server_id", serverId)
        .putString("work_tag", workTag)
        .build()
    val r = OneTimeWorkRequestBuilder<UploadWorker>()
        .setInputData(d)
        .addTag(workTag)
        .setConstraints(constraints)
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(c).enqueueUniqueWork(workTag, ExistingWorkPolicy.REPLACE, r)
}

// Note: EmptyState composables moved to ui/components/EmptyStates.kt

@Composable
fun LockScreen(onAuth: (Boolean) -> Unit) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return
    val executor = remember { ContextCompat.getMainExecutor(context) }
    val biometricPrompt = remember {
        BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onAuth(true) }
        })
    }
    val promptInfo = remember {
        BiometricPrompt.PromptInfo.Builder().setTitle("Zack Locked").setSubtitle("Authenticate to access").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
    }
    LaunchedEffect(Unit) { biometricPrompt.authenticate(promptInfo) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { biometricPrompt.authenticate(promptInfo) }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) { Text("Unlock", color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(themeMode: Int, onThemeChange: (Int) -> Unit, accentIndex: Int, onAccentChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val prefs = remember { context.getSharedPreferences("zack_settings", Context.MODE_PRIVATE) }
    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notifications_enabled", true)) }
    var autoRename by remember { mutableStateOf(prefs.getBoolean("auto_rename", false)) }
    var retentionDays by remember { mutableIntStateOf(prefs.getInt("history_retention_days", 0)) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var showRetentionMenu by remember { mutableStateOf(false) }

    fun authenticateToToggle(targetState: Boolean) {
        if (activity == null) return
        val executor = ContextCompat.getMainExecutor(context)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                lockEnabled = targetState
                prefs.edit().putBoolean("app_lock_enabled", targetState).apply()
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Zack Security").setSubtitle("Confirm change").setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL).build()
        prompt.authenticate(info)
    }

    Scaffold(topBar = { LargeTopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = null) } }) }) { p ->
        Column(Modifier.padding(p).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Appearance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
            ListItem(modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showThemeMenu = true }, headlineContent = { Text("Theme") }, supportingContent = { Text(when(themeMode){ 1 -> "Light"; 2 -> "Dark"; 3 -> "AMOLED"; else -> "System Default"}) }, leadingContent = { Icon(imageVector = Icons.Filled.Palette, contentDescription = null) })

            Text(stringResource(R.string.settings_accent_color), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp))
            Row(Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                accentPresets.forEachIndexed { i, preset ->
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(preset.color)
                            .then(if (accentIndex == i) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                            .clickable { onAccentChange(i) }
                    )
                }
            }

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Uploads", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
            ListItem(
                headlineContent = { Text("Auto-Rename Files") },
                supportingContent = { Text("Appends timestamp to filename") },
                leadingContent = { Icon(imageVector = Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                trailingContent = { Switch(checked = autoRename, onCheckedChange = { autoRename = it; prefs.edit().putBoolean("auto_rename", it).apply() }, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)) }
            )
            val retentionLabel = when (retentionDays) { 7 -> "7 days"; 30 -> "30 days"; 90 -> "90 days"; else -> "Forever" }
            ListItem(modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showRetentionMenu = true }, headlineContent = { Text(stringResource(R.string.settings_keep_history)) }, supportingContent = { Text(retentionLabel) }, leadingContent = { Icon(imageVector = Icons.Filled.DeleteSweep, contentDescription = null) })

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Security", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
            ListItem(headlineContent = { Text("App Lock") }, supportingContent = { Text("PIN/Fingerprint required") }, leadingContent = { Icon(imageVector = Icons.Filled.Fingerprint, contentDescription = null) }, trailingContent = { Switch(checked = lockEnabled, onCheckedChange = { authenticateToToggle(it) }, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)) })

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Notifications", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(12.dp))
            ListItem(headlineContent = { Text("Notifications") }, supportingContent = { Text("Show status updates") }, leadingContent = { Icon(imageVector = Icons.Filled.Notifications, contentDescription = null) }, trailingContent = { Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it; prefs.edit().putBoolean("notifications_enabled", it).apply() }, colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.primary)) })
        }
        if (showThemeMenu) ModalBottomSheet(onDismissRequest = { showThemeMenu = false }) {
            Column(Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("Select Theme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                listOf("System Default", "Light", "Dark", "AMOLED").forEachIndexed { i, label -> Row(Modifier.fillMaxWidth().clickable { onThemeChange(i); showThemeMenu = false }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = themeMode == i, onClick = { onThemeChange(i); showThemeMenu = false }, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)); Text(label, Modifier.padding(start = 16.dp)) } }
            }
        }
        if (showRetentionMenu) ModalBottomSheet(onDismissRequest = { showRetentionMenu = false }) {
            Column(Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text(stringResource(R.string.settings_keep_history), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                listOf(0 to stringResource(R.string.retention_forever), 7 to stringResource(R.string.retention_7), 30 to stringResource(R.string.retention_30), 90 to stringResource(R.string.retention_90)).forEach { (days, label) ->
                    Row(Modifier.fillMaxWidth().clickable { retentionDays = days; prefs.edit().putInt("history_retention_days", days).apply(); showRetentionMenu = false }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = retentionDays == days, onClick = { retentionDays = days; prefs.edit().putInt("history_retention_days", days).apply(); showRetentionMenu = false }, colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary))
                        Text(label, Modifier.padding(start = 16.dp))
                    }
                }
            }
        }
    }
}