package com.example.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.GiftRecord
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoiApp(viewModel: MoiViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("moi_prefs", android.content.Context.MODE_PRIVATE) }
    var showLandingOnboarding by remember { mutableStateOf(prefs.getBoolean("show_onboarding", true)) }
    var isSplashLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1800)
        isSplashLoading = false
    }

    var selectedTab by remember { mutableStateOf(0) } // 0 = Moi List, 1 = Insights
    val showImportOverlay by viewModel.triggerImportScreen.collectAsStateWithLifecycle()
    var showExportProgress by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportStatusText by remember { mutableStateOf("") }
    var exportedJsonText by remember { mutableStateOf("") }
    var showAddEditDialog by remember { mutableStateOf<GiftRecord?>(null) } // Non-null represents editing/adding dialog
    var showDetailsDialog by remember { mutableStateOf<GiftRecord?>(null) }
    var isNewRecord by remember { mutableStateOf(true) }

    val createJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(exportedJsonText.toByteArray(Charsets.UTF_8))
                }
                android.widget.Toast.makeText(context, "Saved backup file successfully!", android.widget.Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Error saving backup file: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(showExportProgress) {
        if (showExportProgress) {
            exportProgress = 0.05f
            exportStatusText = "Reading database records..."
            kotlinx.coroutines.delay(400)
            
            exportProgress = 0.35f
            exportStatusText = "Compiling schemas & structures..."
            kotlinx.coroutines.delay(400)
            
            exportProgress = 0.70f
            exportStatusText = "Formatting raw JSON stream..."
            viewModel.exportBackupJson { json ->
                exportedJsonText = json
            }
            kotlinx.coroutines.delay(400)
            
            exportProgress = 1.0f
            exportStatusText = "Serialization complete!"
            kotlinx.coroutines.delay(300)
            
            try {
                createJsonLauncher.launch("MyMoiBackup_${System.currentTimeMillis()}.json")
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Cannot save JSON file: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
            showExportProgress = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isSplashLoading) {
            SplashLoadingScreen()
        } else if (showLandingOnboarding) {
            LandingOnboardingScreen(
                onDismiss = {
                    showLandingOnboarding = false
                    prefs.edit().putBoolean("show_onboarding", false).apply()
                }
            )
        } else {
            Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "🎁",
                                style = MaterialTheme.typography.titleMedium
                             )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Moi",
                            fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                actions = {
                    var menuExpanded by remember { mutableStateOf(false) }
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.testTag("app_settings_gear_btn")
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Settings and Data Actions",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("Import File") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.resetImportState()
                                    viewModel.setTriggerImportScreen(true)
                                },
                                modifier = Modifier.testTag("gear_import_btn")
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("Export All Data") },
                                onClick = {
                                    menuExpanded = false
                                    showExportProgress = true
                                },
                                modifier = Modifier.testTag("gear_export_btn")
                            )
                            DropdownMenuItem(
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                text = { Text("Show Intro Guide") },
                                onClick = {
                                    menuExpanded = false
                                    showLandingOnboarding = true
                                },
                                modifier = Modifier.testTag("gear_intro_btn")
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .fillMaxWidth()
                    .height(52.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "Moi List" to Icons.Filled.List,
                        "Insights" to Icons.Filled.Star
                    ).forEachIndexed { index, (label, icon) ->
                        val isSelected = selectedTab == index
                        val contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { selectedTab = index }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = contentColor,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = label,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = contentColor
                                )
                            }
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 && !showImportOverlay) {
                FloatingActionButton(
                    onClick = {
                        isNewRecord = true
                        showAddEditDialog = GiftRecord(
                            personName = "",
                            eventType = "",
                            giftType = "Cash",
                            giftDescription = "",
                            amount = 0.0,
                            isReceived = true,
                            date = System.currentTimeMillis()
                        )
                    },
                    modifier = Modifier.testTag("add_record_fab"),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add New Record")
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (showImportOverlay) {
                ImportScreen(
                    viewModel = viewModel,
                    onBack = { viewModel.setTriggerImportScreen(false) }
                )
            } else {
                when (selectedTab) {
                    0 -> MoiListScreen(
                        viewModel = viewModel,
                        onEditRecord = { record ->
                            showDetailsDialog = record
                        }
                    )
                    1 -> InsightsScreen(viewModel = viewModel)
                }
            }
        }
    }

    // Manual Entry or Editing Dialog
    showAddEditDialog?.let { currentRecord ->
        AddEditRecordDialog(
            record = currentRecord,
            isNew = isNewRecord,
            onDismiss = { showAddEditDialog = null },
            onSave = { updatedRecord ->
                if (isNewRecord) {
                    viewModel.addRecord(updatedRecord)
                } else {
                    viewModel.updateRecord(updatedRecord)
                }
                showAddEditDialog = null
            }
        )
    }

    showDetailsDialog?.let { currentRecord ->
        GiftRecordDetailsDialog(
            record = currentRecord,
            onDismiss = { showDetailsDialog = null },
            onEdit = {
                showDetailsDialog = null
                isNewRecord = false
                showAddEditDialog = currentRecord
            }
        )
    }

    // Export Progress Popup Dialog
    if (showExportProgress) {
        Dialog(onDismissRequest = { /* Prevent dismiss during export process */ }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("export_progress_dialog"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Share,
                            contentDescription = "Exporting Status",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Text(
                        text = "Exporting Moi List",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "Preparing offline ledger format and custom structures...",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = exportProgress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = exportStatusText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${(exportProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MoiListScreen(
    viewModel: MoiViewModel,
    onEditRecord: (GiftRecord) -> Unit
) {
    val records by viewModel.filteredRecords.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedEventFilter by viewModel.eventTypeFilter.collectAsStateWithLifecycle()
    val selectedDirFilter by viewModel.directionFilter.collectAsStateWithLifecycle()
    val uniqueEvents by viewModel.uniqueEvents.collectAsStateWithLifecycle()
    val totals by viewModel.totalsState.collectAsStateWithLifecycle()

    var showDeleteConfirmation by remember { mutableStateOf<GiftRecord?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Geometric Balance (Symmetry top bar)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Received Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFE8DEF8)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "RECEIVED",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF21005D),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = formatCurrency(totals.first),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF21005D)
                    )
                }
            }

            // Given Card
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFFD9E3)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "GIVEN",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF31111D),
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Text(
                        text = formatCurrency(totals.second),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF31111D)
                    )
                }
            }
        }

        // Search Bar & Inline Tune icon in the same row
        var showFiltersPopup by remember { mutableStateOf(false) }
        val textStyle = MaterialTheme.typography.bodyMedium

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .testTag("search_field"),
                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search Icon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search friends, family, events, gifts...",
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            innerTextField()
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.updateSearchQuery("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            )

            val filterActive = selectedEventFilter != "All" || selectedDirFilter != "All"
            IconButton(
                onClick = { showFiltersPopup = true },
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = if (filterActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .testTag("search_filter_tune_btn")
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Filter Options",
                    tint = if (filterActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Active Filter Indicators Row (Visual Feedback)
        if (selectedEventFilter != "All" || selectedDirFilter != "All") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Active:",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold
                )
                if (selectedDirFilter != "All") {
                    Row(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.updateDirectionFilter("All") }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedDirFilter,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove direction filter",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
                if (selectedEventFilter != "All") {
                    Row(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.updateEventFilter("All") }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = selectedEventFilter,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Remove event filter",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "Clear All",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable {
                        viewModel.updateDirectionFilter("All")
                        viewModel.updateEventFilter("All")
                    }
                )
            }
        }

        // Popup filter dialog
        if (showFiltersPopup) {
            Dialog(onDismissRequest = { showFiltersPopup = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .testTag("filters_popup_dialog"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Filter Moi List",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            IconButton(onClick = { showFiltersPopup = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close Filters")
                            }
                        }

                        // Gift Direction Selection
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Gift Direction",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val directionsList = listOf("All", "Received", "Given")
                                directionsList.forEach { direction ->
                                    val isSelected = selectedDirFilter == direction
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                            .clickable { viewModel.updateDirectionFilter(direction) }
                                            .padding(vertical = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = direction,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }

                        // Event Selection
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Event Category",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            var eventDropExpanded by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .clickable { eventDropExpanded = true }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (selectedEventFilter == "All") "All Events" else selectedEventFilter,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Icon(
                                        imageVector = Icons.Filled.ArrowDropDown,
                                        contentDescription = "Expand event list"
                                    )
                                }
                                DropdownMenu(
                                    expanded = eventDropExpanded,
                                    onDismissRequest = { eventDropExpanded = false },
                                    modifier = Modifier.fillMaxWidth(0.6f)
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("All Events") },
                                        onClick = {
                                            viewModel.updateEventFilter("All")
                                            eventDropExpanded = false
                                        }
                                    )
                                    uniqueEvents.forEach { eventName ->
                                        DropdownMenuItem(
                                            text = { Text(eventName) },
                                            onClick = {
                                                viewModel.updateEventFilter(eventName)
                                                eventDropExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateDirectionFilter("All")
                                    viewModel.updateEventFilter("All")
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Reset")
                            }
                            Button(
                                onClick = { showFiltersPopup = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Apply")
                            }
                        }
                    }
                }
            }
        }

        // List Scroll
        if (records.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        "🎁",
                        style = MaterialTheme.typography.displayMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No gift records found",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (searchQuery.isNotEmpty() || selectedEventFilter != "All" || selectedDirFilter != "All") {
                            "Try clearing your search query or setting filters back to see all."
                        } else {
                            "Start tracking by adding a manual gift entry using the primary action button below or bulk importing from a CSV file."
                        },
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(records, key = { it.id }) { record ->
                    GiftRecordRow(
                        record = record,
                        onClick = { onEditRecord(record) },
                        onLongClick = { showDeleteConfirmation = record }
                    )
                }
            }
        }
    }

    // Delete confirmation Dialog
    showDeleteConfirmation?.let { recordToDelete ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = null },
            title = { Text("Delete gift record?") },
            text = { Text("Are you sure you want to delete the gift entries for \"${recordToDelete.personName}\" regarding event \"${recordToDelete.eventType}\"? This action cannot be reversed.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRecord(recordToDelete)
                        showDeleteConfirmation = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GiftRecordRow(
    record: GiftRecord,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val directionIndicatorColor = if (record.isReceived) Color(0xFF2E7D32) else Color(0xFFC62828)
    val avatarBgColor = if (record.isReceived) Color(0xFFE8DEF8) else Color(0xFFFFD9E3)
    val avatarTextColor = if (record.isReceived) Color(0xFF21005D) else Color(0xFF31111D)

    // Heuristics for event emojis mirroring the Geometric Balance activities list
    val eventEmoji = remember(record.eventType, record.giftDescription) {
        val combined = (record.eventType + " " + record.giftDescription).lowercase()
        when {
            combined.contains("wedding") || combined.contains("marriage") || combined.contains("reception") || combined.contains("ring") -> "💍"
            combined.contains("house") || combined.contains("warming") || combined.contains("grah") || combined.contains("home") -> "🏠"
            combined.contains("birthday") || combined.contains("born") || combined.contains("birth") -> "🎂"
            combined.contains("shower") || combined.contains("baby") || combined.contains("child") -> "🍼"
            combined.contains("diwali") || combined.contains("deepavali") || combined.contains("eid") || combined.contains("christmas") || combined.contains("pooja") || combined.contains("puja") -> "🪔"
            combined.contains("anniversary") -> "💝"
            combined.contains("cash") || combined.contains("money") || combined.contains("gold") -> "💰"
            else -> "🎁"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .testTag("gift_record_${record.id}"),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Distinct Geometric Circle Avatar Icon containing thematic emoji
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(avatarBgColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = eventEmoji,
                    style = MaterialTheme.typography.bodyLarge
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = record.personName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = record.eventType,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = if (record.giftDescription.isBlank()) record.giftType else record.giftDescription,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                val customMap = remember(record.customFields) { GiftRecord.parseCustomFields(record.customFields) }
                if (customMap.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = customMap.entries.joinToString("  •  ") { "${it.key}: ${it.value}" },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = if (record.isReceived) "+ ${formatCurrency(record.amount)}" else "- ${formatCurrency(record.amount)}",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = directionIndicatorColor
                )
                Text(
                    text = formatDate(record.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportScreen(viewModel: MoiViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val importUiState by viewModel.importUiState.collectAsStateWithLifecycle()

    // File launcher for files (CSV / Excel / JSON backups)
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                // Get filename
                var displayName = ""
                try {
                    if (it.scheme == "content") {
                        context.contentResolver.query(it, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1 && cursor.moveToFirst()) {
                                displayName = cursor.getString(nameIndex) ?: ""
                            }
                        }
                    }
                } catch (ignored: Exception) {}
                val actualName = if (displayName.isNotEmpty()) displayName else "document"
                viewModel.setImportFileName(actualName)

                val mimeType = context.contentResolver.getType(it) ?: ""
                val pathStr = it.path?.lowercase() ?: ""
                val uriStr = it.toString().lowercase()
                
                val isExcel = mimeType.contains("sheet") || 
                              mimeType.contains("excel") || 
                              mimeType.contains("spreadsheet") ||
                              pathStr.endsWith(".xlsx") || 
                              pathStr.endsWith(".xls") ||
                              uriStr.contains(".xlsx") || 
                              uriStr.contains(".xls")

                val isJson = mimeType.contains("json") ||
                             pathStr.endsWith(".json") ||
                             uriStr.contains(".json")

                val inputStream = context.contentResolver.openInputStream(it)
                if (inputStream != null) {
                    if (isExcel) {
                        viewModel.selectExcelFile(inputStream)
                    } else if (isJson) {
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val stringBuilder = StringBuilder()
                        var line: String? = reader.readLine()
                        while (line != null) {
                            stringBuilder.append(line).append("\n")
                            line = reader.readLine()
                        }
                        inputStream.close()
                        viewModel.importBackupJson(stringBuilder.toString()) { success, _ ->
                            if (success) {
                                onBack() // Smooth exit on successful restore
                            }
                        }
                    } else {
                        val reader = BufferedReader(InputStreamReader(inputStream))
                        val stringBuilder = StringBuilder()
                        var line: String? = reader.readLine()
                        while (line != null) {
                            stringBuilder.append(line).append("\n")
                            line = reader.readLine()
                        }
                        inputStream.close()
                        viewModel.selectCsvFile(stringBuilder.toString())
                    }
                }
            } catch (e: Exception) {
                // Ignore error quiet fail
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onBack() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Go back",
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Back to Moi List",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Data Schema Importer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Choose any CSV/Excel data to import",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        when (val state = importUiState) {
            is ImportUiState.Idle, is ImportUiState.Error, is ImportUiState.Success -> {
                item {
                    if (state is ImportUiState.Error) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(state.message, color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    if (state is ImportUiState.Success) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Success", tint = Color(0xFF2E7D32))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Import completed! Added ${state.count} records safely into Moi.", color = Color(0xFF1B5E20), fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.resetImportState() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Import Another Sheet")
                        }
                    }
                }

                if (state !is ImportUiState.Success) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Import Ledger / Backup File", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Supports Excel (.xlsx), CSV, or metadata JSON backups. The system recognizes formats, guides mapping selectors, and imports Moi records instantly.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Option 1: CSV/Excel
                                Button(
                                    onClick = { filePickerLauncher.launch("*/*") },
                                    modifier = Modifier.fillMaxWidth().testTag("select_excel_file_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Upload Sheet (Excel / CSV)", fontWeight = FontWeight.Bold)
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                // Option 2: JSON Backup
                                Button(
                                    onClick = { filePickerLauncher.launch("application/json") },
                                    modifier = Modifier.fillMaxWidth().testTag("select_json_file_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Restore JSON Backup (.json)", fontWeight = FontWeight.Bold)
                                }
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                TextButton(
                                    onClick = {
                                        viewModel.setImportFileName("sample_multi_sheet_template.xlsx")
                                        viewModel.loadSampleExcelTemplate()
                                    },
                                    modifier = Modifier.testTag("load_excel_sample_btn")
                                ) {
                                    Text("Load Multi-Sheet Excel Template Sample", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }

            ImportUiState.Loading -> {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            is ImportUiState.Parsed -> {
                item {
                    val fileName by viewModel.importFileName.collectAsStateWithLifecycle()
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (!fileName.isNullOrEmpty()) "Import Data from \"$fileName\"" else "Import Data Sheet",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (state.isExcel) "Excel • '${state.selectedSheetName}' • ${state.rows.size} rows" else "CSV Document • ${state.rows.size} rows",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                if (state.isExcel && state.sheetsList.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = "Excel Sheet Tab",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "We pre-selected the first tab. Tap to choose another sheet:",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                state.sheetsList.forEach { sheetName ->
                                    val isSelected = sheetName == state.selectedSheetName
                                    val btnColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    
                                    Surface(
                                        onClick = { viewModel.selectSheet(sheetName) },
                                        color = btnColor,
                                        contentColor = contentColor,
                                        shape = RoundedCornerShape(20.dp),
                                        border = androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                        ),
                                        modifier = Modifier.testTag("excel_sheet_tab_${sheetName}")
                                    ) {
                                        Text(
                                            text = sheetName,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Event Occasion title input (elevated level)
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Event Occasion Name",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "All imported cash gifts will be filed under this occasion title.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        OutlinedTextField(
                            value = state.mapping.defaultEventType,
                            onValueChange = {
                                viewModel.updateMapping(state.mapping.copy(defaultEventType = it))
                            },
                            placeholder = { Text("e.g. Wedding, Birthday, Anniversary") },
                            modifier = Modifier.fillMaxWidth().testTag("import_event_title_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
                        )
                    }
                }

                // Columns Mapping compact UI as beautiful nested Rows
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Column Pairing",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Connect spreadsheet columns to Moi List fields:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            CompactMappingRow(
                                icon = Icons.Filled.Person,
                                label = "Guest's Name (Required)",
                                subtitle = "Name of the person who gave or received the gift",
                                headers = state.headers,
                                selectedIndex = state.mapping.personNameCol,
                                onHeaderSelected = {
                                    viewModel.updateMapping(state.mapping.copy(personNameCol = it))
                                }
                            )

                            CompactMappingRow(
                                icon = Icons.Filled.Settings,
                                label = "Gift Amount (₹ Value)",
                                subtitle = "Numeric field representing cash value",
                                headers = state.headers,
                                selectedIndex = state.mapping.amountCol,
                                onHeaderSelected = {
                                    viewModel.updateMapping(state.mapping.copy(amountCol = it))
                                }
                            )

                            CompactMappingRow(
                                icon = Icons.Filled.Star,
                                label = "Gift Category, Type or Desc",
                                subtitle = "Details of item gifted, e.g. Gold chain, Silver vessel, Cash",
                                headers = state.headers,
                                selectedIndex = state.mapping.giftDescCol,
                                onHeaderSelected = {
                                    viewModel.updateMapping(state.mapping.copy(giftDescCol = it))
                                }
                            )

                            CompactMappingRow(
                                icon = Icons.Filled.Refresh,
                                label = "Given vs Received Status",
                                subtitle = "Tells us if you received it or gifted it to them",
                                headers = state.headers,
                                selectedIndex = state.mapping.directionCol,
                                onHeaderSelected = {
                                    viewModel.updateMapping(state.mapping.copy(directionCol = it))
                                }
                            )

                            CompactMappingRow(
                                icon = Icons.Filled.Star,
                                label = "Row Event Occasion Name",
                                subtitle = "(Optional) Overrides general occasion name if present in sheet",
                                headers = state.headers,
                                selectedIndex = state.mapping.eventTypeCol,
                                onHeaderSelected = {
                                    viewModel.updateMapping(state.mapping.copy(eventTypeCol = it))
                                }
                            )
                        }

                        // Fallback direction selector inline with mapping row
                        if (state.mapping.directionCol == -1) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Fallback Direction Status", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = if (state.mapping.defaultIsReceived) "Default to incoming (Received 📥)" else "Default to outgoing (Given 📤)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = state.mapping.defaultIsReceived,
                                        onCheckedChange = {
                                            viewModel.updateMapping(state.mapping.copy(defaultIsReceived = it))
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic Schema Custom/Extra Fields mapping panel
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Custom Fields",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Add extra columns like 'City' or 'Relation' to save as additional descriptive tags.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        var newFieldName by remember { mutableStateOf("") }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                if (state.mapping.customMappings.isEmpty()) {
                                    Text(
                                        "No custom fields currently added.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        state.mapping.customMappings.forEach { (fieldName, colIdx) ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Box(modifier = Modifier.weight(1.2f)) {
                                                    Text(
                                                        text = "$fieldName →",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                
                                                Box(modifier = Modifier.weight(2.5f)) {
                                                    SimpleSchemaDropdown(
                                                        headers = state.headers,
                                                        selectedIndex = colIdx,
                                                        onHeaderSelected = { selectedColumnIndex ->
                                                            viewModel.updateCustomFieldMapping(fieldName, selectedColumnIndex)
                                                        }
                                                    )
                                                }
                                                
                                                IconButton(
                                                    onClick = { viewModel.removeCustomFieldMapping(fieldName) },
                                                    modifier = Modifier.size(36.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.Delete,
                                                        contentDescription = "Remove",
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = newFieldName,
                                        onValueChange = { newFieldName = it },
                                        placeholder = { Text("e.g. City, Guest Relation", style = MaterialTheme.typography.labelMedium) },
                                        modifier = Modifier.weight(1f).testTag("custom_field_name_input"),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.secondary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                        ),
                                        textStyle = MaterialTheme.typography.bodySmall,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    Button(
                                        onClick = {
                                            if (newFieldName.isNotBlank()) {
                                                viewModel.addCustomFieldMapping(newFieldName.trim())
                                                newFieldName = ""
                                            }
                                        },
                                        enabled = newFieldName.isNotBlank(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                        modifier = Modifier.testTag("add_custom_field_btn")
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add", style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }

                // Header Mapping Preview
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Text(
                            text = "Live Import Preview",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "This preview updates instantly as you adjust columns pairing above:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Grid Preview Rows and Action Confirmation
                if (state.previewRecords.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "No valid records mapped yet. Choose at least 'Guest's Name' and 'Gift Amount' above to formulate entry lists.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                } else {
                    items(state.previewRecords.take(5)) { preview ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(if (preview.isReceived) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (preview.isReceived) "📥" else "📤",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preview.personName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${preview.eventType} • ${preview.giftDescription.ifBlank { "Cash" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = if (preview.isReceived) "+ ${formatCurrency(preview.amount)}" else "- ${formatCurrency(preview.amount)}",
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (preview.isReceived) Color(0xFF2E7D32) else Color(0xFFC62828),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    if (state.previewRecords.size > 5) {
                        item {
                            Text(
                                text = "... and ${state.previewRecords.size - 5} more entries waiting to be saved.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                        }
                    }

                    // Confirm big button
                    item {
                        Button(
                            onClick = { viewModel.confirmImport() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .testTag("confirm_mapped_import_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.Check, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Confirm Import of ${state.previewRecords.size} Records", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Reset and edit button
                item {
                    OutlinedButton(
                        onClick = { viewModel.resetImportState() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Reset & Back", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun CompactMappingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    subtitle: String,
    headers: List<String>,
    selectedIndex: Int,
    onHeaderSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(6.dp))
            
            Box {
                Button(
                    onClick = { expanded = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedIndex == -1) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        contentColor = if (selectedIndex == -1) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onPrimary
                        }
                    ),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.heightIn(min = 28.dp)
                ) {
                    Text(
                        text = if (selectedIndex in headers.indices) headers[selectedIndex] else "Choose...",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(12.dp))
                }
                
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Skip / Use fallback") },
                        onClick = {
                            onHeaderSelected(-1)
                            expanded = false
                        }
                    )
                    headers.forEachIndexed { index, header ->
                        DropdownMenuItem(
                            text = { Text(header) },
                            onClick = {
                                onHeaderSelected(index)
                                        expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SimpleSchemaDropdown(
    headers: List<String>,
    selectedIndex: Int,
    onHeaderSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (selectedIndex == -1) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                contentColor = if (selectedIndex == -1) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
            ),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier.heightIn(min = 36.dp)
        ) {
            Text(
                text = if (selectedIndex in headers.indices) headers[selectedIndex] else "Choose...",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Skip") },
                onClick = {
                    onHeaderSelected(-1)
                    expanded = false
                }
            )
            headers.forEachIndexed { i, header ->
                DropdownMenuItem(
                    text = { Text(header) },
                    onClick = {
                        onHeaderSelected(i)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun SchemaMappingField(
    systemLabel: String,
    headers: List<String>,
    selectedIndex: Int,
    onHeaderSelected: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(systemLabel, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
            
            Box {
                Button(
                    onClick = { expanded = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedIndex == -1) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (selectedIndex == -1) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    ),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (selectedIndex in headers.indices) headers[selectedIndex] else "Unmapped (Skip)",
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(14.dp))
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Unmapped (Use default fallback)") },
                        onClick = {
                            onHeaderSelected(-1)
                            expanded = false
                        }
                    )
                    headers.forEachIndexed { i, header ->
                        DropdownMenuItem(
                            text = { Text(header) },
                            onClick = {
                                onHeaderSelected(i)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun InsightsScreen(viewModel: MoiViewModel) {
    val records by viewModel.allRecords.collectAsStateWithLifecycle()
    val totals by viewModel.totalsState.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                "Insights & Performance Analytics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Key statistics from gift-giving records:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Summary Statistics Cards
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Event & Gift Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Total Logged Transactions", style = MaterialTheme.typography.bodyMedium)
                        Text("${records.size}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val eventsCount = records.map { it.eventType }.distinct().size
                        Text("Unique Family Events", style = MaterialTheme.typography.bodyMedium)
                        Text("$eventsCount", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val peopleCount = records.map { it.personName }.distinct().size
                        Text("Friends & Family Logged", style = MaterialTheme.typography.bodyMedium)
                        Text("$peopleCount", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val cashGifts = records.filter { it.giftType == "Cash" }.size
                        val otherGifts = records.filter { it.giftType != "Cash" }.size
                        Text("Contributions Format", style = MaterialTheme.typography.bodyMedium)
                        Text("$cashGifts Cash / $otherGifts Material", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Top Events list
        item {
            Text("Contributions Sorted By Event Occasions", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        }

        val eventSummaries = records.groupBy { it.eventType }.map { (event, list) ->
            val received = list.filter { it.isReceived }.sumOf { it.amount }
            val given = list.filter { !it.isReceived }.sumOf { it.amount }
            val volume = list.size
            Quad(event, received, given, volume)
        }.sortedByDescending { it.third + it.second } // Sort by total cash activity

        if (eventSummaries.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Text(
                        "No events logged yet. Add events from manual forms or mappings to view stats dashboards.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(eventSummaries) { (event, rec, giv, vol) ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(event, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                Text("$vol tx", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text("Received From Others", style = MaterialTheme.typography.labelSmall)
                                Text(formatCurrency(rec), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Given By User", style = MaterialTheme.typography.labelSmall)
                                Text(formatCurrency(giv), color = Color(0xFFC62828), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Data holder helper class for insights flow
data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditRecordDialog(
    record: GiftRecord,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (GiftRecord) -> Unit
) {
    var name by remember { mutableStateOf(record.personName) }
    var event by remember { mutableStateOf(record.eventType) }
    var giftType by remember { mutableStateOf(record.giftType) } // "Cash", "Gift"
    var giftDescription by remember { mutableStateOf(record.giftDescription) }
    var amount by remember { mutableStateOf(if (record.amount == 0.0) "" else record.amount.toString()) }
    var isReceived by remember { mutableStateOf(record.isReceived) }

    // Popular events list for quick tag selections
    val quickEventTags = listOf("Wedding", "Birthday", "Housewarming", "Baby Shower", "Ear Piercing", "Diwali")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .testTag("add_edit_record_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header
                Text(
                    text = if (isNew) "Add Moi List Entry" else "Edit Moi Entry",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Segmented tab toggle for direction
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isReceived) Color(0xFF2E7D32) else Color.Transparent)
                            .clickable { isReceived = true }
                            .testTag("direction_received_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "📥 Received",
                            fontWeight = FontWeight.Bold,
                            color = if (isReceived) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isReceived) Color(0xFFC62828) else Color.Transparent)
                            .clickable { isReceived = false }
                            .testTag("direction_given_btn"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "📤 Given",
                            fontWeight = FontWeight.Bold,
                            color = if (!isReceived) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Friend or Family Member's Name") },
                    modifier = Modifier.fillMaxWidth().testTag("add_record_name_input"),
                    singleLine = true,
                    placeholder = { Text("e.g., Ramesh Kumar, Aunty...") }
                )

                // Event occasion field
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(
                        value = event,
                        onValueChange = { event = it },
                        label = { Text("Event / Occasion Name") },
                        modifier = Modifier.fillMaxWidth().testTag("add_record_event_input"),
                        singleLine = true,
                        placeholder = { Text("Wedding, Puberty Ceremony, etc.") }
                    )
                    
                    // Quick tags row
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        quickEventTags.forEach { tag ->
                            SuggestionChip(
                                onClick = { event = tag },
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Segmented Toggle for Gift format
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (giftType == "Cash") MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { giftType = "Cash" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💵 Cash", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (giftType == "Gift") MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .clickable { giftType = "Gift" },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🎁 Material Gift", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                    }
                }

                // Gift Description input (required / useful especially for gifts)
                OutlinedTextField(
                    value = giftDescription,
                    onValueChange = { giftDescription = it },
                    label = { Text(if (giftType == "Cash") "Cash Details (Optional)" else "Gift Description (e.g., Gold Ring, Titan Watch)") },
                    modifier = Modifier.fillMaxWidth().testTag("add_record_description_input"),
                    singleLine = true,
                    placeholder = { Text(if (giftType == "Cash") "No comments" else "What was the item?") }
                )

                // Amount input
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount value / Price (Rs)") },
                    modifier = Modifier.fillMaxWidth().testTag("add_record_amount_input"),
                    leadingIcon = { Text("₹", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    placeholder = { Text("501, 1001, 2000...") }
                )

                // Dialog Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val doubleAmt = amount.toDoubleOrNull() ?: 0.0
                            val finalRecord = record.copy(
                                personName = name.trim(),
                                eventType = event.trim().ifBlank { "General Event" },
                                giftType = giftType,
                                giftDescription = giftDescription.trim(),
                                amount = doubleAmt,
                                isReceived = isReceived
                            )
                            onSave(finalRecord)
                        },
                        enabled = name.isNotBlank() && amount.isNotBlank() && (amount.toDoubleOrNull() ?: 0.0) >= 0.0,
                        modifier = Modifier.testTag("save_record_btn")
                    ) {
                        Text("Save Entry")
                    }
                }
            }
        }
    }
}

// Utility Helper functions for formatting
fun formatCurrency(amount: Double): String {
    return try {
        val format = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
        format.amountFormat(amount)
    } catch (e: Exception) {
        String.format("₹ %,.2f", amount)
    }
}

private fun NumberFormat.amountFormat(amount: Double): String {
    // Custom Indian formatting representation or standard fallback
    val formatted = this.format(amount)
    return formatted.replace("INR", "₹").replace("Rs.", "₹").trim()
}

fun formatDate(timestamp: Long): String {
    val formatter = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

@Composable
fun GiftRecordDetailsDialog(
    record: GiftRecord,
    onDismiss: () -> Unit,
    onEdit: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        val directionColor = if (record.isReceived) Color(0xFF2E7D32) else Color(0xFFC62828)
        val containerColor = if (record.isReceived) Color(0xFFE8DEF8) else Color(0xFFFFD9E3)
        val textColor = if (record.isReceived) Color(0xFF21005D) else Color(0xFF31111D)

        val customMap = remember(record.customFields) { GiftRecord.parseCustomFields(record.customFields) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("gift_record_details_dialog"),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Header Row: Details Title & Edit Icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Gift Entry Details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.testTag("details_edit_icon_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit Gift Entry",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Status chip (Received or Given)
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (record.isReceived) "RECEIVED" else "GIVEN",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDate(record.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Detail Items
                DetailRow(label = "Person Name", value = record.personName)
                DetailRow(label = "Event Type", value = record.eventType)
                DetailRow(label = "Gift Type", value = record.giftType)
                if (record.giftDescription.isNotBlank()) {
                    DetailRow(label = "Description", value = record.giftDescription)
                }
                DetailRow(
                    label = "Amount",
                    value = formatCurrency(record.amount),
                    valueColor = directionColor,
                    isBoldValue = true
                )

                if (record.notes.isNotBlank()) {
                    DetailRow(label = "Notes", value = record.notes)
                }

                if (customMap.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Custom Metadata Attributes",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    customMap.forEach { (key, value) ->
                        DetailRow(label = key, value = value)
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().testTag("details_close_btn"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun DetailRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    isBoldValue: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = valueColor,
            fontWeight = if (isBoldValue) FontWeight.ExtraBold else FontWeight.Normal
        )
    }
}

@Composable
fun SplashLoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Card(
                modifier = Modifier
                    .size(140.dp)
                    .testTag("splash_icon_container"),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = com.example.R.drawable.ic_launcher_foreground),
                        contentDescription = "Moi Logo",
                        modifier = Modifier.size(120.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Moi",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = (-1).sp
            )

            Text(
                text = "TRADITIONAL GIFT & MOI LEDGER",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(48.dp))

            CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Securely loading offline ledger...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun LandingOnboardingScreen(onDismiss: () -> Unit) {
    var currentPage by remember { mutableStateOf(0) }
    val totalPages = 3

    val slideTitle = listOf(
        "Welcome to Moi",
        "Seamless Smart Maps",
        "Celebratory Insights"
    )

    val slideDesc = listOf(
        "Securely track traditional Moi registries, celebratory gifts, and event balances. Say goodbye to messy notebook scribbles and coordinate all contributions with modern, high-precision ledgers.",
        "Import guests and collections in clicks! Seamlessly import spreadsheets, wedding lists, or Excel sheets (.xlsx) using the intelligent mapping system to automatically populate fields.",
        "Inspect distribution details, averages, and historic given/received metrics in intuitive graphs. Keep your traditional reciprocity perfectly balanced and elegant."
    )

    val slideEmoji = listOf("🎁", "📥", "📈")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalPages) { index ->
                        val isActive = index == currentPage
                        Box(
                            modifier = Modifier
                                .height(6.dp)
                                .width(if (isActive) 24.dp else 8.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                )
                                .animateContentSize()
                        )
                    }
                }

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("onboarding_skip_btn")
                ) {
                    Text(
                        "Skip",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 32.dp)
                    .testTag("onboarding_carousel_card"),
                shape = RoundedCornerShape(2.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                slideEmoji[currentPage],
                                style = MaterialTheme.typography.displayMedium
                            )
                        }

                        Spacer(modifier = Modifier.height(32.dp))

                        Text(
                            text = slideTitle[currentPage],
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = slideDesc[currentPage],
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage > 0) {
                    OutlinedButton(
                        onClick = { currentPage-- },
                        modifier = Modifier
                            .height(50.dp)
                            .testTag("onboarding_prev_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Previous")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = {
                        if (currentPage < totalPages - 1) {
                            currentPage++
                        } else {
                            onDismiss()
                        }
                    },
                    modifier = Modifier
                        .height(50.dp)
                        .testTag("onboarding_next_btn"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = if (currentPage == totalPages - 1) "Get Started" else "Next",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
