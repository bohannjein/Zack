package com.bohannjein.zack

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
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
import androidx.compose.ui.res.painterResource
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

// Helper für IP Input Filter
fun String.isIpChar(): Boolean = this.all { it.isDigit() || it == '.' }

fun Context.findActivity(): FragmentActivity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is FragmentActivity) return context
        context = context.baseContext
    }
    return null
}

class NsdHelper(context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    fun startDiscovery(onDeviceFound: (String, String) -> Unit) {
        stopDiscovery()
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        val host = si.host
                        if (host != null) {
                            onDeviceFound(si.serviceName, host.hostAddress ?: "")
                        }
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) { stopDiscovery() }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) { stopDiscovery() }
        }
        nsdManager.discoverServices("_smb._tcp.", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    fun stopDiscovery() {
        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        discoveryListener = null
    }
}

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
            val useDark = when (themeMode) { 1 -> false; 2, 3 -> true; else -> isSystemInDarkTheme() }
            val isAmoled = themeMode == 3
            var isAuthorized by remember { mutableStateOf(!prefs.getBoolean("app_lock_enabled", false)) }

            SideEffect {
                context.findActivity()?.window?.let { WindowCompat.getInsetsController(it, it.decorView).isAppearanceLightStatusBars = !useDark }
            }

            val colors = if (useDark) {
                if (isAmoled) darkColorScheme(primary = ZackYellow, onPrimary = Color.Black, secondary = ZackAmber, surface = Color.Black, surfaceContainer = Color.Black)
                else darkColorScheme(primary = ZackYellow, onPrimary = Color.Black, secondary = ZackAmber, surface = Color(0xFF141414), surfaceContainer = Color(0xFF1E1E1E))
            } else lightColorScheme(primary = ZackAmber, onPrimary = Color.White, secondary = ZackYellow, surface = Color(0xFFF9F9F9), surfaceContainer = Color(0xFFFFFFFF))

            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (!isAuthorized) LockScreen { isAuthorized = true }
                    else if (sharedUris.isNotEmpty()) ShareBottomSheetScreen(sharedUris)
                    else MainNavigationContainer(themeMode, { m -> themeMode = m; prefs.edit().putInt("theme_mode", m).apply() }, { finish() })
                }
            }
        }
    }
}

// Enum für Screen Management
enum class ScreenState { HOME, SETTINGS, SETUP, PROTOCOL_SELECT }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigationContainer(themeMode: Int, onThemeChange: (Int) -> Unit, onExit: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val db = remember { DatabaseInstance.get(context) }

    var currentScreen by remember { mutableStateOf(ScreenState.HOME) }
    var serverToEdit by remember { mutableStateOf<NetworkServer?>(null) }

    val selectedIds = remember { mutableStateListOf<Long>() }
    val history by db.historyDao().getAllHistory().collectAsState(initial = emptyList())
    val servers by db.serverDao().getAllServers().collectAsState(initial = emptyList())

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
            ScreenState.SETTINGS -> SettingsScreen(themeMode, onThemeChange, { currentScreen = ScreenState.HOME })
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
                                    if (selectedIds.isNotEmpty()) "${selectedIds.size} selected"
                                    else if (pagerState.currentPage == 0) "History"
                                    else "Servers",
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { if (selectedIds.isNotEmpty()) selectedIds.clear() else currentScreen = ScreenState.SETTINGS }) {
                                    Icon(
                                        imageVector = if (selectedIds.isNotEmpty()) Icons.Filled.Close else Icons.Filled.Settings,
                                        contentDescription = null
                                    )
                                }
                            },
                            actions = {
                                if (selectedIds.isEmpty()) {
                                    Surface(
                                        color = ZackAmber,
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
                                            Icon(imageVector = Icons.Filled.Add, contentDescription = null, tint = Color.Black)
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
                            else ExpressiveListScreen(servers.map { it.toZackItem() }, selectedIds, isServer = true, db, onEdit = { serverToEdit = it; currentScreen = ScreenState.SETUP }, emptyState = { EmptyStateServers() })
                        }

                        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                            AnimatedContent(targetState = selectedIds.isNotEmpty(), label = "island") { isSel ->
                                if (isSel) SelectionIsland(selectedIds, pagerState.currentPage, servers, db, history, { serverToEdit = it; currentScreen = ScreenState.SETUP })
                                else CenteredNavPill(pagerState.currentPage) { scope.launch { pagerState.animateScrollToPage(it) } }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CenteredNavPill(currentIndex: Int, onTabClick: (Int) -> Unit) {
    val indicatorOffset by animateDpAsState(targetValue = if (currentIndex == 0) 4.dp else 64.dp, animationSpec = tween(300), label = "glide")
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape, shadowElevation = 6.dp, modifier = Modifier.height(64.dp).width(128.dp)) {
        Box(Modifier.padding(4.dp)) {
            Box(Modifier.offset(x = indicatorOffset).size(56.dp).clip(CircleShape).background(ZackAmber))
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onTabClick(0) }, Alignment.Center) { Icon(imageVector = Icons.Filled.History, contentDescription = null, tint = if (currentIndex == 0) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant) }
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onTabClick(1) }, Alignment.Center) { Icon(imageVector = Icons.Filled.Dns, contentDescription = null, tint = if (currentIndex == 1) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant) }
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
    Surface(color = ZackAmber, shape = CircleShape, shadowElevation = 8.dp, modifier = Modifier.height(72.dp)) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage == 0) {
                IconButton(onClick = {
                    selectedIds.clear()
                    selectedIds.addAll(history.map { it.id })
                }) { Icon(imageVector = Icons.Filled.SelectAll, contentDescription = null, tint = Color.Black) }
            }

            if (currentPage == 1 && selectedIds.size == 1) {
                IconButton(onClick = { val s = servers.find { it.id == selectedIds.first() }; s?.let { onEdit(it) }; selectedIds.clear() }) { Icon(imageVector = Icons.Filled.Edit, contentDescription = null, tint = Color.Black) }
            }
            IconButton(onClick = { selectedIds.clear() }) { Icon(imageVector = Icons.Filled.Close, contentDescription = null, tint = Color.Black) }

            var confirm by remember { mutableStateOf(false) }
            IconButton(onClick = { confirm = true }) { Icon(imageVector = Icons.Filled.Delete, contentDescription = null, tint = Color.Black) }

            if (confirm) AlertDialog(
                onDismissRequest = { confirm = false },
                title = { Text("Delete?") },
                confirmButton = {
                    Button(
                        onClick = { scope.launch(Dispatchers.IO) { if (currentPage == 0) db.historyDao().deleteEntries(selectedIds.toList()) else db.serverDao().deleteServers(selectedIds.toList()); withContext(Dispatchers.Main) { selectedIds.clear(); confirm = false } } },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text("Delete") }
                },
                dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtocolSelectionDialog(onProtocolSelected: (String) -> Unit, onDismiss: () -> Unit) {
    Scaffold(
        topBar = { LargeTopAppBar(title = { Text("Add Server") }, navigationIcon = { IconButton(onClick = onDismiss) { Icon(imageVector = Icons.Filled.Close, contentDescription = null) } }) }
    ) { p ->
        Column(Modifier.padding(p).padding(16.dp).verticalScroll(rememberScrollState())) {
            Text("Select Protocol", style = MaterialTheme.typography.labelLarge, color = ZackAmber, modifier = Modifier.padding(bottom = 16.dp))

            val protocols = listOf(
                "Local Scan" to Icons.Filled.Radar,
                "SMB" to Icons.Filled.FolderShared,
                "SFTP" to Icons.Filled.Lock,
                "FTP" to Icons.Filled.Cloud,
                "WebDAV" to Icons.Filled.Http
            )

            protocols.forEach { (name, icon) ->
                Card(
                    onClick = { onProtocolSelected(name) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = icon, contentDescription = null, tint = ZackAmber, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.width(16.dp))
                        Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                        color = ZackAmber,
                        modifier = Modifier.padding(bottom = 12.dp))

                    if (discoveredDevices.isEmpty()) {
                        Box(Modifier.fillMaxWidth().height(100.dp), Alignment.Center) {
                            CircularProgressIndicator(color = ZackAmber, modifier = Modifier.size(24.dp))
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
                                Icon(imageVector = Icons.Filled.Storage, contentDescription = null, tint = ZackAmber)
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
                        Text("Device not found? Enter IP manually", color = ZackAmber)
                    }
                }
            } else {
                ZackTextField(value = name, onValueChange = { name = it }, label = "Display Name (Optional)", imeAction = ImeAction.Next)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        ZackTextField(
                            value = host,
                            onValueChange = { if (it.isIpChar() || currentProtocol != "SMB") host = it },
                            label = if (currentProtocol == "WebDAV") "URL / Host" else "IP / Host",
                            keyboardType = if (currentProtocol == "WebDAV") KeyboardType.Uri else KeyboardType.Ascii,
                            imeAction = ImeAction.Next
                        )
                    }
                    if (needsPort) {
                        Box(modifier = Modifier.width(100.dp)) {
                            ZackTextField(
                                value = port,
                                onValueChange = { if (it.all { c -> c.isDigit() }) port = it },
                                label = "Port",
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next
                            )
                        }
                    }
                }

                ZackTextField(value = path, onValueChange = { path = it }, label = pathLabel, imeAction = ImeAction.Next)

                if (needsDomain) {
                    ZackTextField(value = domain, onValueChange = { domain = it }, label = "Workgroup / Domain", imeAction = ImeAction.Next)
                }

                HorizontalDivider(Modifier.padding(vertical = 16.dp))
                Text("Authentication", style = MaterialTheme.typography.labelMedium, color = ZackAmber, modifier = Modifier.padding(bottom = 8.dp))

                ZackTextField(value = user, onValueChange = { user = it }, label = "Username", imeAction = ImeAction.Next)

                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                )

                Spacer(Modifier.height(32.dp))

                Button(onClick = { scope.launch(Dispatchers.IO) {
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
                } }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = ZackAmber)) { Text("Save Server", fontWeight = FontWeight.Bold, color = Color.Black) }
            }
            Spacer(Modifier.height(50.dp))
        }
    }
}

@Composable
fun ZackTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Default
) {
    val focusManager = LocalFocusManager.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) },
            onDone = { focusManager.clearFocus() }
        )
    )
}

// WICHTIG: Hier ist der Fix für dein Problem!
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpressiveListScreen(items: List<ZackItem>, selectedIds: MutableList<Long>, isServer: Boolean = false, db: AppDatabase? = null, onEdit: (NetworkServer) -> Unit = {}, emptyState: @Composable () -> Unit) {
    if (items.isEmpty()) emptyState() else {
        var refreshing by remember { mutableStateOf(false) }; val haptic = LocalHapticFeedback.current
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true; android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ refreshing = false }, 800) }) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 16.dp, bottom = 120.dp)) {
                items(items, key = { it.id }) { item ->
                    val selected = selectedIds.contains(item.id)
                    Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = if (selected) ZackYellow.copy(alpha = 0.2f) else if (item.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer), modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(24.dp)).combinedClickable(onClick = { if (selectedIds.isNotEmpty()) { if (selected) selectedIds.remove(item.id) else selectedIds.add(item.id); haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }, onLongClick = { if (!selected) { selectedIds.add(item.id); haptic.performHapticFeedback(HapticFeedbackType.LongPress) } }).animateItem()) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)), Alignment.Center) {
                                if (item.isUploading) {
                                    CircularProgressIndicator(Modifier.size(24.dp), ZackAmber, 3.dp)
                                } else {
                                    // FIX: Separate Logik für selected vs unselected
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            tint = ZackAmber
                                        )
                                    } else {
                                        // Hier wird item.icon verwendet - stelle sicher dass ZackItem.icon ein ImageVector ist!
                                        // Wenn es ein Int (R.drawable) ist, ändere zu: painter = painterResource(id = item.icon)
                                        Icon(
                                            imageVector = item.icon,  // Muss ImageVector sein!
                                            contentDescription = null,
                                            tint = if (item.isError) MaterialTheme.colorScheme.error else if (item.isSpecial) ZackAmber else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) { Text(item.title, fontWeight = if (item.isSpecial) FontWeight.Bold else FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium); Text(item.subtitle, style = MaterialTheme.typography.bodyMedium, color = if (item.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant) }

                            if (isServer && selectedIds.isEmpty()) {
                                IconButton(onClick = { val scope = CoroutineScope(Dispatchers.IO); scope.launch { db?.serverDao()?.toggleDefault(item.id, !item.isSpecial) } }) {
                                    Icon(
                                        imageVector = if (item.isSpecial) Icons.Filled.Star else Icons.Outlined.Star,
                                        contentDescription = null,
                                        tint = ZackAmber
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
    if (servers.size > 1 && servers.none { it.isDefault }) ServerSelectionSheet(uris, { activity?.finish() }) else Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(color = ZackAmber) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSelectionSheet(uris: List<Uri>, onDismiss: () -> Unit) {
    val ctx = LocalContext.current; val scope = rememberCoroutineScope(); val db = remember { DatabaseInstance.get(ctx) }
    val servers by db.serverDao().getAllServers().collectAsState(initial = emptyList())
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp).fillMaxWidth()) {
            Text("Zack to...", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = ZackAmber)
            Spacer(Modifier.height(24.dp))
            LazyColumn { items(servers) { s -> Card(onClick = { scope.launch { prepareAndUpload(ctx, s.id, uris); onDismiss() } }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Row(Modifier.padding(16.dp)) { Icon(imageVector = Icons.Filled.Storage, contentDescription = null, tint = ZackAmber); Spacer(Modifier.width(16.dp)); Text(s.displayName) } } } }
        }
    }
}

suspend fun prepareAndUpload(context: Context, serverId: Long, uris: List<Uri>) {
    val prefs = context.getSharedPreferences("zack_settings", Context.MODE_PRIVATE)
    val autoRename = prefs.getBoolean("auto_rename", false)

    withContext(Dispatchers.IO) {
        uris.forEach { uri ->
            try {
                var fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor -> val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (cursor.moveToFirst()) cursor.getString(nameIndex) else null } ?: "file_${System.currentTimeMillis()}"

                if (autoRename) {
                    val timestamp = SimpleDateFormat("_yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val dotIndex = fileName.lastIndexOf('.')
                    fileName = if (dotIndex != -1) {
                        fileName.substring(0, dotIndex) + timestamp + fileName.substring(dotIndex)
                    } else {
                        fileName + timestamp
                    }
                }

                val cacheFile = File(context.cacheDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(cacheFile).use { output -> input.copyTo(output) } }
                startUploadWork(context, cacheFile.absolutePath, serverId)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
}

fun startUploadWork(c: Context, filePath: String, s: Long) {
    val d = Data.Builder().putString("file_path", filePath).putLong("server_id", s).build()
    val r = OneTimeWorkRequestBuilder<UploadWorker>().setInputData(d).addTag(filePath).build()
    WorkManager.getInstance(c).enqueueUniqueWork(filePath, ExistingWorkPolicy.REPLACE, r)
}

// FIX: Vereinfachte Version ohne riskante Resource-Abfrage
@Composable
fun EmptyStateHistory() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Nutze direkt das Material Icon statt dynamischem Laden
            Icon(
                imageVector = Icons.Filled.History,
                contentDescription = null,
                modifier = Modifier.size(200.dp).alpha(0.6f),
                tint = ZackAmber.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(24.dp))
            Text("No history yet", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun EmptyStateServers() {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = Icons.Filled.Dns, contentDescription = null, modifier = Modifier.size(80.dp), tint = ZackAmber.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))
            Text("No servers yet")
        }
    }
}

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
            Icon(imageVector = Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = ZackAmber)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { biometricPrompt.authenticate(promptInfo) }, colors = ButtonDefaults.buttonColors(containerColor = ZackAmber)) { Text("Unlock", color = Color.Black) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(themeMode: Int, onThemeChange: (Int) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.findActivity() }
    val prefs = remember { context.getSharedPreferences("zack_settings", Context.MODE_PRIVATE) }
    var lockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var notificationsEnabled by remember { mutableStateOf(prefs.getBoolean("notifications_enabled", true)) }
    var autoRename by remember { mutableStateOf(prefs.getBoolean("auto_rename", false)) }
    var showThemeMenu by remember { mutableStateOf(false) }

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
            Text("Appearance", style = MaterialTheme.typography.labelLarge, color = ZackAmber, modifier = Modifier.padding(12.dp))
            ListItem(modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { showThemeMenu = true }, headlineContent = { Text("Theme") }, supportingContent = { Text(when(themeMode){ 1 -> "Light"; 2 -> "Dark"; 3 -> "AMOLED"; else -> "System Default"}) }, leadingContent = { Icon(imageVector = Icons.Filled.Palette, contentDescription = null) })

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Uploads", style = MaterialTheme.typography.labelLarge, color = ZackAmber, modifier = Modifier.padding(12.dp))
            ListItem(
                headlineContent = { Text("Auto-Rename Files") },
                supportingContent = { Text("Appends timestamp to filename") },
                leadingContent = { Icon(imageVector = Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                trailingContent = { Switch(checked = autoRename, onCheckedChange = { autoRename = it; prefs.edit().putBoolean("auto_rename", it).apply() }, colors = SwitchDefaults.colors(checkedTrackColor = ZackAmber)) }
            )

            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            Text("Security & Notifications", style = MaterialTheme.typography.labelLarge, color = ZackAmber, modifier = Modifier.padding(12.dp))
            ListItem(headlineContent = { Text("App Lock") }, supportingContent = { Text("PIN/Fingerprint required") }, leadingContent = { Icon(imageVector = Icons.Filled.Fingerprint, contentDescription = null) }, trailingContent = { Switch(checked = lockEnabled, onCheckedChange = { authenticateToToggle(it) }, colors = SwitchDefaults.colors(checkedTrackColor = ZackAmber)) })
            ListItem(headlineContent = { Text("Notifications") }, supportingContent = { Text("Show status updates") }, leadingContent = { Icon(imageVector = Icons.Filled.Notifications, contentDescription = null) }, trailingContent = { Switch(checked = notificationsEnabled, onCheckedChange = { notificationsEnabled = it; prefs.edit().putBoolean("notifications_enabled", it).apply() }, colors = SwitchDefaults.colors(checkedTrackColor = ZackAmber)) })
        }
        if (showThemeMenu) ModalBottomSheet(onDismissRequest = { showThemeMenu = false }) {
            Column(Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("Select Theme", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))
                listOf("System Default", "Light", "Dark", "AMOLED").forEachIndexed { i, label -> Row(Modifier.fillMaxWidth().clickable { onThemeChange(i); showThemeMenu = false }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = themeMode == i, onClick = { onThemeChange(i); showThemeMenu = false }, colors = RadioButtonDefaults.colors(selectedColor = ZackAmber)); Text(label, Modifier.padding(start = 16.dp)) } }
            }
        }
    }
}