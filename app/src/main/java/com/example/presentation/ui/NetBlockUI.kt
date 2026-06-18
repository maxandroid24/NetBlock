package com.example.presentation.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import java.util.Locale
import android.widget.ImageView
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.domain.model.AppInfo
import com.example.presentation.viewmodel.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.cos
import kotlin.math.sin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.VpnService
import android.app.Activity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

val LocalVpnToggler = staticCompositionLocalOf<(Boolean) -> Unit> {
    { _ -> }
}

/**
 * Main application navigation router and overlay container.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetBlockMainScreen(viewModel: NetBlockViewModel) {
    val context = LocalContext.current
    val currentScreen by viewModel.activeScreen.collectAsState()
    val isVpnActive by viewModel.isVpnActive.collectAsState()
    val totalBlocked by viewModel.totalBlockedCount.collectAsState()
    val isRefreshing by viewModel.isRefreshingApps.collectAsState()

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.setMasterToggleState(context, true)
        } else {
            Toast.makeText(context, "VPN Permission is required to block app connections.", Toast.LENGTH_LONG).show()
        }
    }

    val toggleVpn: (Boolean) -> Unit = { active ->
        if (active) {
            val prepareIntent = VpnService.prepare(context)
            if (prepareIntent != null) {
                vpnLauncher.launch(prepareIntent)
            } else {
                viewModel.setMasterToggleState(context, true)
            }
        } else {
            viewModel.setMasterToggleState(context, false)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadApps(context)
    }

    CompositionLocalProvider(LocalVpnToggler provides toggleVpn) {
        // Main background structure
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .drawBehind {
                    // Liquid glowing gradient background accents for premium atmosphere
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(PrimaryBlue.copy(alpha = 0.15f), Color.Transparent),
                            center = Offset(canvasWidth * 0.2f, canvasHeight * 0.15f),
                            radius = canvasWidth * 0.8f
                        ),
                        center = Offset(canvasWidth * 0.2f, canvasHeight * 0.15f),
                        radius = canvasWidth * 0.8f
                    )

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(AccentLight.copy(alpha = 0.15f), Color.Transparent),
                            center = Offset(canvasWidth * 0.9f, canvasHeight * 0.75f),
                            radius = canvasWidth * 0.8f
                        ),
                        center = Offset(canvasWidth * 0.9f, canvasHeight * 0.75f),
                        radius = canvasWidth * 0.8f
                    )
                }
        ) {
            AnimatedContent(
                targetState = currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(250))
                },
                label = "ScreenTransition"
            ) { screen ->
                when (screen) {
                    is Screen.Onboarding -> OnboardingScreen(viewModel)
                    is Screen.VpnPermission -> VpnPermissionScreen(viewModel)
                    is Screen.Home -> HomeDashboard(viewModel)
                    is Screen.AppDetails -> AppDetailsScreen(viewModel)
                    is Screen.FilterSort -> FilterSortScreen(viewModel)
                    is Screen.Settings -> SettingsScreen(viewModel)
                }
            }
        }
    }
}

// ==========================================
// 1. SCREEN 1: ONBOARDING & SPLASH SCREEN
// ==========================================
@Composable
fun OnboardingScreen(viewModel: NetBlockViewModel) {
    val gradient = Brush.verticalGradient(listOf(PrimaryBlue, GradientMid, AccentLight))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // App Identity Header
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 40.dp)
        ) {
            // Elegant pulsing shield symbol Custom Drawing
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .drawBehind {
                        // Shadow glow
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(PrimaryBlue.copy(alpha = 0.25f), Color.Transparent)
                            ),
                            radius = size.width / 1.5f
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.size(120.dp).semantics { testTag = "shield_shield" }) {
                    val path = Path().apply {
                        val w = size.width
                        val h = size.height
                        moveTo(w * 0.5f, h * 0.05f)
                        quadraticTo(w * 0.85f, h * 0.05f, w * 0.9f, h * 0.15f)
                        quadraticTo(w * 0.9f, h * 0.55f, w * 0.5f, h * 0.95f)
                        quadraticTo(w * 0.1f, h * 0.55f, w * 0.1f, h * 0.15f)
                        quadraticTo(w * 0.15f, h * 0.05f, w * 0.5f, h * 0.05f)
                        close()
                    }
                    drawPath(path, brush = gradient)

                    // Inner glossy lining design
                    val innerPath = Path().apply {
                        val w = size.width
                        val h = size.height
                        moveTo(w * 0.5f, h * 0.15f)
                        quadraticTo(w * 0.78f, h * 0.15f, w * 0.82f, h * 0.22f)
                        quadraticTo(w * 0.82f, h * 0.52f, w * 0.5f, h * 0.85f)
                        quadraticTo(w * 0.18f, h * 0.52f, w * 0.18f, h * 0.22f)
                        quadraticTo(w * 0.22f, h * 0.15f, w * 0.5f, h * 0.15f)
                        close()
                    }
                    drawPath(innerPath, color = Color.White.copy(alpha = 0.25f))
                }

                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(45.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "NetBlock",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Block internet access for selected apps",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }

        // Onboarding Lists Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OnboardingStepItem(
                icon = Icons.Default.Block,
                title = "Block internet for any app",
                description = "Restrict access specifically to Wi-Fi, Mobile Data, or both."
            )
            OnboardingStepItem(
                icon = Icons.Default.SignalCellularAlt,
                title = "Save mobile data plan",
                description = "Stop sneaky background updates and save bandwidth."
            )
            OnboardingStepItem(
                icon = Icons.Default.PrivacyTip,
                title = "Improve privacy and focus",
                description = "Work completely offline without distractions or logging."
            )
        }

        // CTA Button
        Button(
            onClick = { viewModel.navigateTo(Screen.VpnPermission) },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .semantics { testTag = "onboarding_btn" },
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
        ) {
            Text(
                text = "Get Started",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = Color.White
            )
        }
    }
}

@Composable
fun OnboardingStepItem(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
            .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(PrimaryBlue.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = PrimaryBlue,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

// ==========================================
// 2. SCREEN 2: VPN PERMISSION EXPLANATION
// ==========================================
@Composable
fun VpnPermissionScreen(viewModel: NetBlockViewModel) {
    val context = LocalContext.current
    val gradient = Brush.verticalGradient(listOf(AccentLight, PrimaryBlue))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(
                text = "Protect Your Apps",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Graphical network nodes VPN visualization
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .drawBehind {
                        // Drawing connecting lines to signify Secure VPN nodes
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = size.width / 3f
                        val points = listOf(
                            Offset(center.x + radius * cos(0f), center.y + radius * sin(0f)),
                            Offset(center.x + radius * cos(2f), center.y + radius * sin(2f)),
                            Offset(center.x + radius * cos(4f), center.y + radius * sin(4f))
                        )

                        // Draw lines between nodes
                        for (i in points.indices) {
                            drawLine(
                                color = GradientMid.copy(alpha = 0.4f),
                                start = center,
                                end = points[i],
                                strokeWidth = 3.dp.toPx()
                            )
                            for (j in i + 1 until points.size) {
                                drawLine(
                                    color = PrimaryBlue.copy(alpha = 0.3f),
                                    start = points[i],
                                    end = points[j],
                                    strokeWidth = 2.dp.toPx()
                                )
                            }
                        }

                        // Circles at nodes
                        drawCircle(PrimaryBlue, radius = 10.dp.toPx(), center = center)
                        points.forEach { pt ->
                            drawCircle(AccentLight, radius = 7.dp.toPx(), center = pt)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = PrimaryBlue,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "NetBlock creates a local VPN connection to monitor and block internet traffic for selected applications. No data ever leaves your device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }

        // Trust Indicators Block
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TrustIndicatorRow(text = "Privacy First: No personal data collected or shared")
            TrustIndicatorRow(text = "Local Tunnel: Complete offline device filtering")
            TrustIndicatorRow(text = "Root-Free: Standard protected Android APIs")
        }

        // CTA Decisions
        val vpnToggler = LocalVpnToggler.current

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    // Start Master Switch / VPN and navigate
                    vpnToggler(true)
                    viewModel.completeOnboarding(context)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .semantics { testTag = "permission_accept" }
            ) {
                Text(
                    text = "Enable Protection",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            TextButton(
                onClick = { viewModel.completeOnboarding(context) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Maybe Later",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun TrustIndicatorRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = GreenAllowed,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

// ==========================================
// 3. SCREEN 3: HOME DASHBOARD & APP LIST
// ==========================================
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeDashboard(viewModel: NetBlockViewModel) {
    val context = LocalContext.current
    val isVpnActive by viewModel.isVpnActive.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val currentFilter by viewModel.selectedFilter.collectAsState()
    val appsList by viewModel.appsList.collectAsState()
    val totalBlockedAmt by viewModel.totalBlockedCount.collectAsState()

    var currentHomeTab by remember { mutableStateOf(0) } // 0: Firewall Applist, 1: Settings

    // Calculate aggregated telemetry
    val totalBlockedCountDb = totalBlockedAmt

    LaunchedEffect(Unit) {
        viewModel.syncVpnStatus()
    }

    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = {
            // High fidelity bottom nav bar with active pill indicators
            NavigationBar(
                containerColor = if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            ) {
                NavigationBarItem(
                    selected = (currentHomeTab == 0),
                    onClick = { currentHomeTab = 0 },
                    icon = { Icon(Icons.Default.Shield, contentDescription = "Firewall") },
                    label = { Text("Firewall", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlue.copy(alpha = 0.12f)
                    )
                )

                NavigationBarItem(
                    selected = (currentHomeTab == 1),
                    onClick = { currentHomeTab = 1 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PrimaryBlue,
                        selectedTextColor = PrimaryBlue,
                        indicatorColor = PrimaryBlue.copy(alpha = 0.12f)
                    )
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentHomeTab) {
                0 -> {
                    // FIREWALL LIST VIEW
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isWideScreen = maxWidth > 650.dp
                        if (isWideScreen) {
                            // Dual pane widescreen layout
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                // Left Column: Title + PROTECTION STATUS MODULE
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    // Title bar
                                    Column(modifier = Modifier.padding(vertical = 12.dp)) {
                                        Text(
                                            text = "NetBlock",
                                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                        Text(
                                            text = "V3.2 PRODUCTION",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.6.sp,
                                                fontSize = 10.sp
                                            ),
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                        )
                                    }

                                    // Large Liquid Glass status card
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(
                                                if (isVpnActive) {
                                                    Brush.verticalGradient(listOf(PrimaryBlue, GradientMid, AccentLight))
                                                } else {
                                                    SolidColor(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                                                }
                                            )
                                            .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
                                            .padding(20.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(52.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isVpnActive) Color.White.copy(alpha = 0.2f) else PrimaryBlue.copy(alpha = 0.15f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isVpnActive) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                                            contentDescription = null,
                                                            tint = if (isVpnActive) Color.White else PrimaryBlue,
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(16.dp))

                                                    Column {
                                                        Text(
                                                            text = "PROTECTION STATUS",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                letterSpacing = 1.sp,
                                                                fontSize = 9.sp
                                                            ),
                                                            color = if (isVpnActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                        )

                                                        Text(
                                                            text = if (isVpnActive) "Active" else "Idle",
                                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                                            color = if (isVpnActive) Color.White else MaterialTheme.colorScheme.onBackground
                                                        )
                                                    }
                                                }

                                                val vpnToggler = LocalVpnToggler.current
                                                Switch(
                                                    checked = isVpnActive,
                                                    onCheckedChange = { vpnToggler(it) },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = AccentLight,
                                                        uncheckedThumbColor = PrimaryBlue,
                                                        uncheckedTrackColor = Color.LightGray.copy(alpha = 0.2f)
                                                    ),
                                                    modifier = Modifier.semantics { testTag = "master_firewall_toggle" }
                                                )
                                            }

                                            // Quick Statistics nested grid (High Density styling)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                StatusCardStatBlock(
                                                    flex = 1f,
                                                    value = "$totalBlockedCountDb",
                                                    label = "Blocked Apps",
                                                    isVpnActive = isVpnActive
                                                )
                                                StatusCardStatBlock(
                                                    flex = 0.0f,
                                                    value = "",
                                                    label = "Saved-to-delete",
                                                    isVpnActive = isVpnActive
                                                )
                                                StatusCardStatBlock(
                                                    flex = 0.0f,
                                                    value = "",
                                                    label = "Requests",
                                                    isVpnActive = isVpnActive
                                                )
                                            }
                                        }
                                    }
                                }

                                // Right Column: Search + Filter chips + Virtualized list of applications
                                Column(
                                    modifier = Modifier
                                        .weight(1.2f)
                                        .fillMaxHeight(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Search Bar
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { viewModel.setSearchQuery(it) },
                                            placeholder = { Text("Search system or user apps...") },
                                            prefix = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.padding(end = 4.dp)) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .semantics { testTag = "search_input" },
                                            shape = RoundedCornerShape(20.dp),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = PrimaryBlue,
                                                unfocusedBorderColor = if (isSystemInDarkTheme()) BorderDark else BorderLight,
                                                focusedContainerColor = if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight,
                                                unfocusedContainerColor = if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight
                                            )
                                        )

                                        IconButton(
                                            onClick = { viewModel.navigateTo(Screen.FilterSort) },
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                                                .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(20.dp))
                                        ) {
                                            Icon(Icons.Default.FilterList, contentDescription = "Filter and Sort settings")
                                        }
                                    }

                                    // Filter Chips
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChipSelectionItem("All", currentFilter == FilterSelection.ALL) { viewModel.setFilter(FilterSelection.ALL) }
                                        FilterChipSelectionItem("Blocked", currentFilter == FilterSelection.BLOCKED) { viewModel.setFilter(FilterSelection.BLOCKED) }
                                        FilterChipSelectionItem("Allowed", currentFilter == FilterSelection.ALLOWED) { viewModel.setFilter(FilterSelection.ALLOWED) }
                                        FilterChipSelectionItem("System Apps", currentFilter == FilterSelection.SYSTEM) { viewModel.setFilter(FilterSelection.SYSTEM) }
                                        FilterChipSelectionItem("User Apps", currentFilter == FilterSelection.USER) { viewModel.setFilter(FilterSelection.USER) }
                                    }

                                    // Apps list text count
                                    Text(
                                        text = "Installed Applications (${appsList.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )

                                    // Apps lists list
                                    Box(modifier = Modifier.weight(1f)) {
                                        if (appsList.isEmpty()) {
                                            Column(
                                                modifier = Modifier.fillMaxSize(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    Icons.Default.Info,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                                    modifier = Modifier.size(64.dp)
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Text(
                                                    "No matching apps found",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                )
                                            }
                                        } else {
                                            LazyColumn(
                                                modifier = Modifier.fillMaxSize(),
                                                verticalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                items(appsList, key = { it.packageName }) { app ->
                                                    AppItemViewRow(app = app, viewModel = viewModel)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // Standard 1-column layout for phones/compact screens
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .semantics { testTag = "apps_lazy_list" },
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Title bar
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .statusBarsPadding()
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "NetBlock",
                                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
                                                color = MaterialTheme.colorScheme.onBackground
                                            )
                                            Text(
                                                text = "V3.2 PRODUCTION",
                                                style = MaterialTheme.typography.labelSmall.copy(
                                                    fontWeight = FontWeight.Bold,
                                                    letterSpacing = 1.6.sp,
                                                    fontSize = 10.sp
                                                ),
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                            )
                                        }

                                        IconButton(
                                            onClick = { currentHomeTab = 1 },
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                                        ) {
                                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                                        }
                                    }
                                }

                                // Large Liquid Glass status card
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(28.dp))
                                            .background(
                                                if (isVpnActive) {
                                                    Brush.verticalGradient(listOf(PrimaryBlue, GradientMid, AccentLight))
                                                } else {
                                                    SolidColor(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                                                }
                                            )
                                            .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
                                            .padding(20.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(52.dp)
                                                            .clip(CircleShape)
                                                            .background(if (isVpnActive) Color.White.copy(alpha = 0.2f) else PrimaryBlue.copy(alpha = 0.15f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isVpnActive) Icons.Default.Shield else Icons.Default.ShieldMoon,
                                                            contentDescription = null,
                                                            tint = if (isVpnActive) Color.White else PrimaryBlue,
                                                            modifier = Modifier.size(30.dp)
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.width(16.dp))

                                                    Column {
                                                        Text(
                                                            text = "PROTECTION STATUS",
                                                            style = MaterialTheme.typography.labelSmall.copy(
                                                                fontWeight = FontWeight.Bold,
                                                                letterSpacing = 1.sp,
                                                                fontSize = 9.sp
                                                            ),
                                                            color = if (isVpnActive) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                                        )

                                                        Text(
                                                            text = if (isVpnActive) "Active" else "Idle",
                                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                                                            color = if (isVpnActive) Color.White else MaterialTheme.colorScheme.onBackground
                                                        )
                                                    }
                                                }

                                                val vpnToggler = LocalVpnToggler.current
                                                Switch(
                                                    checked = isVpnActive,
                                                    onCheckedChange = { vpnToggler(it) },
                                                    colors = SwitchDefaults.colors(
                                                        checkedThumbColor = Color.White,
                                                        checkedTrackColor = AccentLight,
                                                        uncheckedThumbColor = PrimaryBlue,
                                                        uncheckedTrackColor = Color.LightGray.copy(alpha = 0.2f)
                                                    ),
                                                    modifier = Modifier.semantics { testTag = "master_firewall_toggle" }
                                                )
                                            }

                                            // Quick Statistics nested grid (High Density styling)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                StatusCardStatBlock(
                                                    flex = 1f,
                                                    value = "$totalBlockedCountDb",
                                                    label = "Blocked Apps",
                                                    isVpnActive = isVpnActive
                                                )
                                            }
                                        }
                                    }
                                }

                                // Search and Filter row
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = searchQuery,
                                            onValueChange = { viewModel.setSearchQuery(it) },
                                            placeholder = { Text("Search system or user apps...") },
                                            prefix = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.padding(end = 4.dp)) },
                                            modifier = Modifier
                                                .weight(1f)
                                                .semantics { testTag = "search_input" },
                                            shape = RoundedCornerShape(20.dp),
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = PrimaryBlue,
                                                unfocusedBorderColor = if (isSystemInDarkTheme()) BorderDark else BorderLight,
                                                focusedContainerColor = if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight,
                                                unfocusedContainerColor = if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight
                                            )
                                        )

                                        IconButton(
                                            onClick = { viewModel.navigateTo(Screen.FilterSort) },
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                                                .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(20.dp))
                                        ) {
                                            Icon(Icons.Default.FilterList, contentDescription = "Filter and Sort settings")
                                        }
                                    }
                                }

                                // Quick Horizontal scrollable chips selection
                                item {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        FilterChipSelectionItem("All", currentFilter == FilterSelection.ALL) { viewModel.setFilter(FilterSelection.ALL) }
                                        FilterChipSelectionItem("Blocked", currentFilter == FilterSelection.BLOCKED) { viewModel.setFilter(FilterSelection.BLOCKED) }
                                        FilterChipSelectionItem("Allowed", currentFilter == FilterSelection.ALLOWED) { viewModel.setFilter(FilterSelection.ALLOWED) }
                                        FilterChipSelectionItem("System Apps", currentFilter == FilterSelection.SYSTEM) { viewModel.setFilter(FilterSelection.SYSTEM) }
                                        FilterChipSelectionItem("User Apps", currentFilter == FilterSelection.USER) { viewModel.setFilter(FilterSelection.USER) }
                                    }
                                }

                                // Installed lists
                                item {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Installed Applications (${appsList.size})",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }

                                // Apps rendering list
                                if (appsList.isEmpty()) {
                                    item {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 48.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                Icons.Default.Info,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f),
                                                modifier = Modifier.size(64.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                "No matching apps found",
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                            )
                                            Text(
                                                "Try revising search queries or quick filter chips",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                } else {
                                    items(appsList, key = { it.packageName }) { app ->
                                        AppItemViewRow(app = app, viewModel = viewModel)
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // SETTINGS PAGE
                    SettingsScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun FilterChipSelectionItem(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSelected) PrimaryBlue else (if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight))
            .border(1.dp, if (isSelected) Color.Transparent else (if (isSystemInDarkTheme()) BorderDark else BorderLight), RoundedCornerShape(20.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun RowScope.DashboardStatItem(flex: Float, value: String, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(
        modifier = Modifier
            .width(0.dp)
            .weight(flex)
            .clip(RoundedCornerShape(20.dp))
            .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
            .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(20.dp))
            .padding(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RowScope.StatusCardStatBlock(
    flex: Float,
    value: String,
    label: String,
    isVpnActive: Boolean
) {
    if (flex > 0.01f) {
        val bg = if (isVpnActive) {
            Color.White.copy(alpha = 0.15f)
        } else {
            if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.03f)
        }
        val borderColor = if (isVpnActive) {
            Color.White.copy(alpha = 0.25f)
        } else {
            if (isSystemInDarkTheme()) BorderDark else BorderLight
        }
        val valueColor = if (isVpnActive) Color.White else MaterialTheme.colorScheme.onBackground
        val labelColor = if (isVpnActive) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)

        Column(
            modifier = Modifier
                .weight(flex)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(12.dp)
        ) {
            Text(
                text = label.uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 9.sp,
                    letterSpacing = 0.5.sp
                ),
                color = labelColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = valueColor
            )
        }
    }
}

@Composable
fun AppItemViewRow(app: AppInfo, viewModel: NetBlockViewModel) {
    val context = LocalContext.current
    val isAnyBlocked = app.blockedWifi || app.blockedMobileData

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
            .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(24.dp))
            .clickable {
                viewModel.selectAppDetails(app)
                viewModel.navigateTo(Screen.AppDetails)
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            // Native system app icon loading delegator
            if (app.icon != null) {
                AppIconView(
                    drawable = app.icon,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryBlue.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = app.appName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryBlue
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.44f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val badgeBgColor = if (isAnyBlocked) {
                        RedBlocked.copy(alpha = 0.1f)
                    } else {
                        GreenAllowed.copy(alpha = 0.12f)
                    }
                    val badgeTextColor = if (isAnyBlocked) RedBlocked else GreenAllowed
                    val badgeBorderColor = if (isAnyBlocked) RedBlocked.copy(alpha = 0.2f) else GreenAllowed.copy(alpha = 0.2f)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(badgeBgColor)
                            .border(1.dp, badgeBorderColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(badgeTextColor)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAnyBlocked) "BLOCKED" else "ALLOWED",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                ),
                                color = badgeTextColor
                            )
                        }
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Block overall quickly
            IconButton(
                onClick = {
                    val newState = !isAnyBlocked
                    viewModel.toggleAppBlock(context, app, newState, newState)
                    Toast.makeText(context, "${app.appName} rule updated", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = if (isAnyBlocked) Icons.Default.Block else Icons.Default.NetworkCheck,
                    contentDescription = "Quick Toggle Firewall block rules",
                    tint = if (isAnyBlocked) RedBlocked else GreenAllowed.copy(alpha = 0.7f)
                )
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            )
        }
    }
}

// ==========================================
// 4. SCREEN 5: APP DETAILS VIEW
// ==========================================
@Composable
fun AppDetailsScreen(viewModel: NetBlockViewModel) {
    val context = LocalContext.current
    val appSelected by viewModel.selectedApp.collectAsState()
    val appsList by viewModel.appsList.collectAsState()

    // Sync app state modifications
    val app = appsList.find { it.packageName == appSelected?.packageName } ?: appSelected

    if (app == null) {
        viewModel.navigateTo(Screen.Home)
        return
    }

    val isAnyBlocked = app.blockedWifi || app.blockedMobileData
    val dataUsedMB = String.format(Locale.getDefault(), "%.2f", app.dataUsageBytes.toDouble() / (1024.0 * 1024.0))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(Screen.Home) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go back")
            }

            Text(
                text = "App Details",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        // App Meta Glass Surface Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (app.icon != null) {
                    AppIconView(
                        drawable = app.icon,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(PrimaryBlue.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = app.appName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = PrimaryBlue
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Block Internet Switch Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Block Internet Access",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Block all network sockets for this app",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                    )
                }

                Switch(
                    checked = isAnyBlocked,
                    onCheckedChange = { isChecked ->
                        viewModel.toggleAppBlock(context, app, isChecked, isChecked)
                    },
                    modifier = Modifier.semantics { testTag = "app_block_toggle" }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Connection Type Checkboxes Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "Connection Controls",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                // WiFi check
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.toggleAppBlock(context, app, !app.blockedWifi, app.blockedMobileData)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Block Wi-Fi Connection", style = MaterialTheme.typography.bodyLarge)
                    }
                    Checkbox(
                        checked = app.blockedWifi,
                        onCheckedChange = { viewModel.toggleAppBlock(context, app, it, app.blockedMobileData) }
                    )
                }

                // Mobile block
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.toggleAppBlock(context, app, app.blockedWifi, !app.blockedMobileData)
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = PrimaryBlue)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Block Mobile Data Connection", style = MaterialTheme.typography.bodyLarge)
                    }
                    Checkbox(
                        checked = app.blockedMobileData,
                        onCheckedChange = { viewModel.toggleAppBlock(context, app, app.blockedWifi, it) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // App stats summary
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Usage Metrics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                AppStatMetricRow(label = "Total Shared bandwidth", value = "$dataUsedMB MB")
                AppStatMetricRow(label = "Estimated blocked packages", value = if (isAnyBlocked) "52 intercepted" else "0")
                AppStatMetricRow(label = "Size on disk", value = String.format("%.1f MB", app.sizeOnDiskBytes.toDouble() / (1024.0 * 1024.0)))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Danger resetting zone
        Button(
            onClick = {
                viewModel.toggleAppBlock(context, app, false, false)
                Toast.makeText(context, "Firewall rules reset successfully", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = RedBlocked),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Icon(Icons.Default.Restore, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset App Firewall Rules", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}

@Composable
fun AppStatMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground)
    }
}

/*
// ==========================================
// 5. SCREEN 6: STATISTICS TAB
// ==========================================
@Composable
fun StatisticsScreen(viewModel: NetBlockViewModel) {
    val liveStats by viewModel.liveStatistics.collectAsState()
    var selectedTabTime by remember { mutableStateOf(0) } // 0: Day, 1: Week, 2: Month, 3: Year

    val totalBlockedRequests = liveStats.sumOf { it.blockedRequests }
    val totalBandwidthSavedBytes = liveStats.sumOf { it.dataSavedBytes }
    val dataSavedMB = String.format(Locale.getDefault(), "%.1f", totalBandwidthSavedBytes.toDouble() / (1024.0 * 1024.0))

    // Aggregate stats by app
    val appStatsList = remember(liveStats) {
        liveStats.groupBy { it.packageName }
            .map { (pkg, stats) ->
                val name = stats.firstOrNull()?.appName ?: pkg
                val requests = stats.sumOf { it.blockedRequests }
                val bytes = stats.sumOf { it.dataSavedBytes }
                Triple(name, requests, bytes)
            }
            .sortedByDescending { it.second }
            .take(5)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Blocked Statistics",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // Time chips filtering
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChipSelectionItem("Day", selectedTabTime == 0) { selectedTabTime = 0 }
            FilterChipSelectionItem("Week", selectedTabTime == 1) { selectedTabTime = 1 }
            FilterChipSelectionItem("Month", selectedTabTime == 2) { selectedTabTime = 2 }
            FilterChipSelectionItem("Year", selectedTabTime == 3) { selectedTabTime = 3 }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Total saved metrics bubble
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(PrimaryBlue, AccentLight)))
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Total Network Data Saved",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f)
                )

                Text(
                    text = "$dataSavedMB Megabytes",
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "$totalBlockedRequests threats blocked dynamically",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Custom drawn graphical charts container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = "Filter activity intercept trends",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                // GORGEOUS LINE CHART CANVAS
                AnimatedLineChartCanvas(liveStats)

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Hourly intercept statistics",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(16.dp))

                // COLUMN BAR CHART
                AnimatedBarChart(liveStats)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Top Restricted Apps List
        Text(
            text = "Most Blocked Applications",
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (appStatsList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No logs recorded yet", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                }
            } else {
                appStatsList.forEach { (name, count, bytes) ->
                    val appProgressVal = count.toFloat() / (totalBlockedRequests.coerceAtLeast(1))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                            .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(20.dp))
                            .padding(16.dp)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onBackground
                                )

                                Text(
                                    text = "$count blocks",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = PrimaryBlue
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Custom animated slider indicator progress bar
                            LinearProgressIndicator(
                                progress = appProgressVal,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = GradientMid,
                                trackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Custom line chart drawn using Paint Path Canvas.
 */
@Composable
fun AnimatedLineChartCanvas(stats: List<StatisticsEntity>) {
    val points = remember(stats) {
        if (stats.isEmpty()) {
            listOf(50f, 120f, 80f, 200f, 140f, 250f, 190f)
        } else {
            stats.takeLast(7).map { it.blockedRequests.toFloat().coerceIn(10f, 400f) }
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    ) {
        val width = size.width
        val height = size.height
        val interval = width / (points.size - 1).coerceAtLeast(1)

        val maxVal = points.maxOrNull()?.coerceAtLeast(1f) ?: 100f
        val scalePoints = points.map { height - (it / maxVal) * (height * 0.8f) }

        // Draw background grid lines
        for (i in 0..4) {
            val y = height - (i * height / 4f)
            drawLine(
                color = Color.LightGray.copy(alpha = 0.2f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }

        val strokePath = Path().apply {
            moveTo(0f, scalePoints.firstOrNull() ?: 0f)
            for (i in 1 until scalePoints.size) {
                val currentX = i * interval
                val prevX = (i - 1) * interval
                // Bezier curve calculations for majestic liquid curves
                val controlX1 = prevX + interval / 2f
                val controlY1 = scalePoints[i - 1]
                val controlX2 = prevX + interval / 2f
                val controlY2 = scalePoints[i]

                cubicTo(controlX1, controlY1, controlX2, controlY2, currentX, scalePoints[i])
            }
        }

        // Draw transparent bottom gradient under curve
        val fillPath = Path().apply {
            addPath(strokePath)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }

        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(PrimaryBlue.copy(alpha = 0.3f), Color.Transparent)
            )
        )

        // Draw solid stroke line
        drawPath(
            path = strokePath,
            color = PrimaryBlue,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
        )

        // Plot node dots
        for (i in scalePoints.indices) {
            drawCircle(
                color = AccentLight,
                radius = 5.dp.toPx(),
                center = Offset(i * interval, scalePoints[i])
            )
            drawCircle(
                color = PrimaryBlue,
                radius = 3.dp.toPx(),
                center = Offset(i * interval, scalePoints[i])
            )
        }
    }
}

/**
 * Custom-drawn Column Bar Chart.
 */
@Composable
fun AnimatedBarChart(stats: List<StatisticsEntity>) {
    val barPoints = remember(stats) {
        if (stats.isEmpty()) {
            listOf(200f, 140f, 320f, 180f, 250f, 210f, 290f)
        } else {
            stats.groupBy { it.dateString }
                .map { (_, items) -> items.sumOf { it.blockedRequests }.toFloat().coerceIn(10f, 500f) }
                .takeLast(7)
        }
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(150.dp)
    ) {
        val width = size.width
        val height = size.height
        val barCount = barPoints.size
        val blockWidth = width / barCount
        val barWidth = blockWidth * 0.45f

        val maxValue = barPoints.maxOrNull()?.coerceAtLeast(1f) ?: 100f

        for (i in barPoints.indices) {
            val barHeight = (barPoints[i] / maxValue) * (height * 0.85f)
            val left = i * blockWidth + (blockWidth - barWidth) / 2f
            val top = height - barHeight

            drawRoundRect(
                brush = Brush.verticalGradient(listOf(PrimaryBlue, GradientMid)),
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
            )
        }
    }
}

*/
// ==========================================
// 6. SCREEN 7: SORT & FILTER PAGE
// ==========================================
@Composable
fun FilterSortScreen(viewModel: NetBlockViewModel) {
    val currentFilter by viewModel.selectedFilter.collectAsState()
    val currentSort by viewModel.selectedSort.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.navigateTo(Screen.Home) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to dashboard")
            }

            Text(
                text = "Filter & Sort Options",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
                modifier = Modifier.padding(start = 12.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filters Container
        Text(
            text = "Active Application Filtering",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterRadioOption("All Systems & User Applications", currentFilter == FilterSelection.ALL) { viewModel.setFilter(FilterSelection.ALL) }
                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                FilterRadioOption("Firewall Intercepted Blocks", currentFilter == FilterSelection.BLOCKED) { viewModel.setFilter(FilterSelection.BLOCKED) }
                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                FilterRadioOption("Allowed Normal Access", currentFilter == FilterSelection.ALLOWED) { viewModel.setFilter(FilterSelection.ALLOWED) }
                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                FilterRadioOption("System Protected Applications Only", currentFilter == FilterSelection.SYSTEM) { viewModel.setFilter(FilterSelection.SYSTEM) }
                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                FilterRadioOption("User Installed Only", currentFilter == FilterSelection.USER) { viewModel.setFilter(FilterSelection.USER) }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sorting options
        Text(
            text = "Applications Sorting Queue",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SortRadioOption("Alphabetical Order (A - Z)", currentSort == SortType.NAME_A_Z) { viewModel.setSort(SortType.NAME_A_Z) }
                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                SortRadioOption("Alphabetical Order (Z - A)", currentSort == SortType.NAME_Z_A) { viewModel.setSort(SortType.NAME_Z_A) }
                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                SortRadioOption("Heaviest Data Traffic High-Low", currentSort == SortType.DATA_USAGE_HIGH) { viewModel.setSort(SortType.DATA_USAGE_HIGH) }
                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                SortRadioOption("Lightest Data Traffic Low-High", currentSort == SortType.DATA_USAGE_LOW) { viewModel.setSort(SortType.DATA_USAGE_LOW) }
                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))
                SortRadioOption("Release / First Installation Date", currentSort == SortType.INSTALL_DATE) { viewModel.setSort(SortType.INSTALL_DATE) }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Save
        Button(
            onClick = { viewModel.navigateTo(Screen.Home) },
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Apply Filter Settings", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun FilterRadioOption(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        RadioButton(selected = isSelected, onClick = onClick)
    }
}

@Composable
fun SortRadioOption(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = text, style = MaterialTheme.typography.bodyLarge)
        RadioButton(selected = isSelected, onClick = onClick)
    }
}

// ==========================================
// 7. SCREEN 8: SETTINGS PANEL
// ==========================================
@Composable
fun SettingsScreen(viewModel: NetBlockViewModel) {
    val context = LocalContext.current
    var autoStart by remember { mutableStateOf(true) }
    var persistentNotify by remember { mutableStateOf(true) }
    var mockEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("netblock_prefs", Context.MODE_PRIVATE)
        autoStart = prefs.getBoolean("auto_start_firewall", true)
        persistentNotify = prefs.getBoolean("persistent_notification", true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Firewall Settings",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(vertical = 12.dp)
        )

        // General rules
        SettingsSectionHeader("CORE FIREWALL OPERATION")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SettingsSwitchRow(
                    icon = Icons.Default.PowerSettingsNew,
                    title = "Auto Start Firewall on Boot",
                    description = "Resume threat blocking rules when device boots up",
                    checked = autoStart
                ) { isChecked ->
                    autoStart = isChecked
                    val prefs = context.getSharedPreferences("netblock_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("auto_start_firewall", isChecked).apply()
                }

                Divider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f))

                SettingsSwitchRow(
                    icon = Icons.Default.NotificationsActive,
                    title = "Persistent Status Notification",
                    description = "Prevent system sleep from stopping firewall limits",
                    checked = persistentNotify
                ) { isChecked ->
                    persistentNotify = isChecked
                    val prefs = context.getSharedPreferences("netblock_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putBoolean("persistent_notification", isChecked).apply()
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Export Import
        SettingsSectionHeader("DATA RULES BACKUP")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        viewModel.exportRules(context) { success, result ->
                            if (success) {
                                Toast.makeText(context, "Rules exported successfully to storage", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Export failed: $result", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export Block Rules List", color = Color.White)
                }

                Button(
                    onClick = {
                        val path = File(context.filesDir, "netblock_rules_backup.txt").absolutePath
                        viewModel.importRules(context, path) { success, message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GradientMid),
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Import Rules Block Backup", color = Color.White)
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // About block
        SettingsSectionHeader("ABOUT INFORMATION")
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(if (isSystemInDarkTheme()) GlassCardDark else GlassCardLight)
                .border(1.dp, if (isSystemInDarkTheme()) BorderDark else BorderLight, RoundedCornerShape(28.dp))
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Product Build Version", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Text("1.0.0 (Production)", fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Privacy policy", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Text("Protected on-device only", color = PrimaryBlue, fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Developer", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                    Text("Internal NetBlock Core", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun SettingsSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp),
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingsSwitchRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
            }
        }

        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/**
 * Delegate AndroidView image loading wrapper.
 */
@Composable
fun AppIconView(drawable: Drawable, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageDrawable(drawable)
            }
        },
        update = { view ->
            view.setImageDrawable(drawable)
        },
        modifier = modifier
    )
}
