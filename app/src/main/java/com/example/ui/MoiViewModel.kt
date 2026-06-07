package com.example.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.GiftRecord
import com.example.data.GiftRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ColumnMapping(
    val personNameCol: Int = -1,
    val eventTypeCol: Int = -1,
    val giftTypeCol: Int = -1,
    val giftDescCol: Int = -1,
    val amountCol: Int = -1,
    val directionCol: Int = -1,
    val customMappings: Map<String, Int> = emptyMap(), // Custom attributes e.g., "Place" mapped to column index
    
    val defaultEventType: String = "Family Event",
    val defaultGiftType: String = "Cash",
    val defaultAmount: Double = 0.0,
    val defaultIsReceived: Boolean = true,
    val defaultGiftDescription: String = "Gift"
)

sealed interface ImportUiState {
    object Idle : ImportUiState
    data class Parsed(
        val headers: List<String>,
        val rows: List<List<String>>,
        val mapping: ColumnMapping,
        val previewRecords: List<GiftRecord>,
        val isExcel: Boolean = false,
        val selectedSheetName: String = "",
        val sheetsList: List<String> = emptyList(),
        val allExcelSheetsData: Map<String, List<List<String>>> = emptyMap()
    ) : ImportUiState
    object Loading : ImportUiState
    data class Success(val count: Int) : ImportUiState
    data class Error(val message: String) : ImportUiState
}

class MoiViewModel(private val repository: GiftRepository) : ViewModel() {

    // Filter states
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _eventTypeFilter = MutableStateFlow("All")
    val eventTypeFilter = _eventTypeFilter.asStateFlow()

    private val _directionFilter = MutableStateFlow("All") // "All", "Received", "Given"
    val directionFilter = _directionFilter.asStateFlow()

    // Current records matching search and filters
    val allRecords: StateFlow<List<GiftRecord>> = repository.allRecords
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val filteredRecords: StateFlow<List<GiftRecord>> = combine(
        _searchQuery,
        _eventTypeFilter,
        _directionFilter,
        repository.allRecords
    ) { query, eventFilter, dirFilter, records ->
        records.filter { record ->
            val matchesQuery = query.isBlank() || 
                    record.personName.contains(query, ignoreCase = true) ||
                    record.eventType.contains(query, ignoreCase = true) ||
                    record.giftDescription.contains(query, ignoreCase = true)
            
            val matchesEvent = eventFilter == "All" || record.eventType.equals(eventFilter, ignoreCase = true)
            
            val matchesDirection = when (dirFilter) {
                "Received" -> record.isReceived
                "Given" -> !record.isReceived
                else -> true
            }

            matchesQuery && matchesEvent && matchesDirection
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Unique Event Types for filters
    val uniqueEvents: StateFlow<List<String>> = repository.allRecords
        .combine(MutableStateFlow(emptyList<String>())) { records, _ ->
            records.map { it.eventType }.distinct().sorted()
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Aggregate statistics
    val totalsState = combine(
        _eventTypeFilter,
        _searchQuery,
        _directionFilter,
        repository.allRecords
    ) { eventFilter, searchQuery, dirFilter, records ->
        val filtered = records.filter { record ->
            val matchesQuery = searchQuery.isBlank() || 
                    record.personName.contains(searchQuery, ignoreCase = true) ||
                    record.eventType.contains(searchQuery, ignoreCase = true) ||
                    record.giftDescription.contains(searchQuery, ignoreCase = true)
            
            val matchesEvent = eventFilter == "All" || record.eventType.equals(eventFilter, ignoreCase = true)
            
            matchesQuery && matchesEvent
        }
        val totalReceived = filtered.filter { it.isReceived }.sumOf { it.amount }
        val totalGiven = filtered.filter { !it.isReceived }.sumOf { it.amount }
        val netBalance = totalReceived - totalGiven
        Triple(totalReceived, totalGiven, netBalance)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Triple(0.0, 0.0, 0.0))

    // CSV Import UI state
    private val _importUiState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importUiState: StateFlow<ImportUiState> = _importUiState.asStateFlow()

    // Flag indicating whether to show the Import Screen/Overlay
    private val _triggerImportScreen = MutableStateFlow(false)
    val triggerImportScreen: StateFlow<Boolean> = _triggerImportScreen.asStateFlow()

    private val _importFileName = MutableStateFlow<String?>(null)
    val importFileName: StateFlow<String?> = _importFileName.asStateFlow()

    fun setTriggerImportScreen(show: Boolean) {
        _triggerImportScreen.value = show
    }

    fun setImportFileName(name: String?) {
        _importFileName.value = name
    }

    fun handleIncomingUri(context: android.content.Context, uri: android.net.Uri) {
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        try {
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(uri) ?: ""
            val pathStr = uri.path?.lowercase() ?: ""
            val uriStr = uri.toString().lowercase()

            var displayName = ""
            try {
                if (uri.scheme == "content") {
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1 && cursor.moveToFirst()) {
                            displayName = cursor.getString(nameIndex) ?: ""
                        }
                    }
                }
            } catch (ignored: Exception) {
            }
            val displayNameLower = displayName.lowercase()

            val isExcel = mimeType.contains("sheet") || 
                          mimeType.contains("excel") || 
                          mimeType.contains("spreadsheet") ||
                          pathStr.endsWith(".xlsx") || 
                          pathStr.endsWith(".xls") ||
                          uriStr.contains(".xlsx") || 
                          uriStr.contains(".xls") ||
                          displayNameLower.endsWith(".xlsx") ||
                          displayNameLower.endsWith(".xls")

            val isJson = mimeType.contains("json") ||
                         pathStr.endsWith(".json") ||
                         uriStr.contains(".json") ||
                         displayNameLower.endsWith(".json")

            val actualName = if (displayName.isNotEmpty()) displayName else "shared document"
            _importFileName.value = actualName
            mainHandler.post {
                android.widget.Toast.makeText(context, "Opening $actualName...", android.widget.Toast.LENGTH_SHORT).show()
            }

            val inputStream = contentResolver.openInputStream(uri)
            if (inputStream != null) {
                if (isExcel) {
                    selectExcelFile(inputStream)
                } else if (isJson) {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                    val stringBuilder = StringBuilder()
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line).append("\n")
                        line = reader.readLine()
                    }
                    inputStream.close()
                    importBackupJson(stringBuilder.toString()) { success, message ->
                        mainHandler.post {
                            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    val reader = java.io.BufferedReader(java.io.InputStreamReader(inputStream))
                    val stringBuilder = StringBuilder()
                    var line: String? = reader.readLine()
                    while (line != null) {
                        stringBuilder.append(line).append("\n")
                        line = reader.readLine()
                    }
                    inputStream.close()
                    selectCsvFile(stringBuilder.toString())
                }
                _triggerImportScreen.value = true
            } else {
                mainHandler.post {
                    android.widget.Toast.makeText(context, "Error: Could not open the file.", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val errMsg = e.message ?: "Unknown error"
            mainHandler.post {
                android.widget.Toast.makeText(context, "Could not load shared file: $errMsg", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun updateEventFilter(event: String) {
        _eventTypeFilter.value = event
    }

    fun updateDirectionFilter(dirIndex: String) {
        _directionFilter.value = dirIndex
    }

    // CRUD operations
    fun addRecord(record: GiftRecord) {
        viewModelScope.launch {
            repository.insertRecord(record)
        }
    }

    fun updateRecord(record: GiftRecord) {
        viewModelScope.launch {
            repository.updateRecord(record)
        }
    }

    fun deleteRecord(record: GiftRecord) {
        viewModelScope.launch {
            repository.deleteRecord(record)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.deleteAllRecords()
        }
    }

    // CSV and Excel Import Preset Templates
    val sampleExcelSheets = mapOf(
        "Wedding Moi List" to listOf(
            listOf("Invitee Name", "Gift Category", "Amount Value", "Is Received", "Present Detail"),
            listOf("Rahul Sharma", "Cash", "5000", "Received", "Enveloped Gift"),
            listOf("Amit Varma", "Gift Item", "0", "Received", "Silver Spoon Set"),
            listOf("Vikram Rathore", "Cash", "2000", "Given", "Sangeet Shagun envelope"),
            listOf("Priya Saxena", "Gift Item", "1500", "Given", "Wall Clock")
        ),
        "Housewarming & Pooja" to listOf(
            listOf("Person", "Occasion", "Sum Total", "Status Action", "Material Gift"),
            listOf("Karan Johar", "Grihapravesh", "10000", "Received", "Decorative Ganesha"),
            listOf("Deepika Padukone", "Office Opening", "25000", "Given", "Golden Frame"),
            listOf("Siddharth Roy", "Festival Pooja", "3100", "Received", "Sweet Box with cash"),
            listOf("Alia Bhatt", "Engagement", "11000", "Given", "Saree")
        ),
        "Birthday & Baby Shower" to listOf(
            listOf("Friend Name", "Party Type", "Monetary Value", "Sum Type", "Gift Title"),
            listOf("Neha Kakkar", "Baby Shower", "501", "Given", "Baby Walker"),
            listOf("Arun Kumar", "1st Birthday", "1000", "Received", "Lego block set"),
            listOf("Varun Malhotra", "Anniversary", "2100", "Given", "Bed sheet set")
        )
    )

    // Dynamic Custom Field Mapping Schema Add/Remove Operations
    fun addCustomFieldMapping(fieldName: String) {
        val currentState = _importUiState.value
        if (currentState is ImportUiState.Parsed) {
            val updatedMappings = currentState.mapping.customMappings.toMutableMap()
            if (!updatedMappings.containsKey(fieldName)) {
                updatedMappings[fieldName] = -1 // default unmapped
                val newMapping = currentState.mapping.copy(customMappings = updatedMappings)
                updateMapping(newMapping)
            }
        }
    }

    fun removeCustomFieldMapping(fieldName: String) {
        val currentState = _importUiState.value
        if (currentState is ImportUiState.Parsed) {
            val updatedMappings = currentState.mapping.customMappings.toMutableMap()
            if (updatedMappings.containsKey(fieldName)) {
                updatedMappings.remove(fieldName)
                val newMapping = currentState.mapping.copy(customMappings = updatedMappings)
                updateMapping(newMapping)
            }
        }
    }

    fun updateCustomFieldMapping(fieldName: String, colIdx: Int) {
        val currentState = _importUiState.value
        if (currentState is ImportUiState.Parsed) {
            val updatedMappings = currentState.mapping.customMappings.toMutableMap()
            updatedMappings[fieldName] = colIdx
            val newMapping = currentState.mapping.copy(customMappings = updatedMappings)
            updateMapping(newMapping)
        }
    }

    // Data Backup Portability Operations (Backup raw JSON format logs)
    fun exportBackupJson(onResult: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val records = repository.allRecords.first()
                val jsonArray = org.json.JSONArray()
                records.forEach { r ->
                    val obj = org.json.JSONObject()
                    obj.put("personName", r.personName)
                    obj.put("eventType", r.eventType)
                    obj.put("giftType", r.giftType)
                    obj.put("giftDescription", r.giftDescription)
                    obj.put("amount", r.amount)
                    obj.put("isReceived", r.isReceived)
                    obj.put("date", r.date)
                    obj.put("notes", r.notes)
                    obj.put("customFields", r.customFields)
                    jsonArray.put(obj)
                }
                onResult(jsonArray.toString(2))
            } catch (e: Exception) {
                onResult("")
            }
        }
    }

    fun importBackupJson(jsonString: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Loading
            try {
                val jsonArray = org.json.JSONArray(jsonString)
                val list = mutableListOf<GiftRecord>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        GiftRecord(
                            personName = obj.optString("personName", ""),
                            eventType = obj.optString("eventType", "Imported Event"),
                            giftType = obj.optString("giftType", "Cash"),
                            giftDescription = obj.optString("giftDescription", ""),
                            amount = obj.optDouble("amount", 0.0),
                            isReceived = obj.optBoolean("isReceived", true),
                            date = obj.optLong("date", System.currentTimeMillis()),
                            notes = obj.optString("notes", ""),
                            customFields = obj.optString("customFields", "")
                        )
                    )
                }
                if (list.isEmpty()) {
                    _importUiState.value = ImportUiState.Error("No valid records found in the backup file.")
                    onResult(false, "No valid records found in the backup file.")
                    return@launch
                }
                // Overwrite database to restore state as a fresh backup import
                repository.deleteAllRecords()
                repository.insertRecords(list)
                _importUiState.value = ImportUiState.Success(list.size)
                onResult(true, "Successfully restored ${list.size} records from backup!")
            } catch (e: Exception) {
                _importUiState.value = ImportUiState.Error("Failed to parse backup JSON: ${e.message}")
                onResult(false, "Failed to parse backup JSON: ${e.message}")
            }
        }
    }

    private fun guessMapping(headers: List<String>): ColumnMapping {
        var nameCol = -1
        var eventCol = -1
        var descCol = -1
        var amountCol = -1
        var dirCol = -1
        var giftTypeCol = -1

        headers.forEachIndexed { index, header ->
            val lower = header.lowercase()
            if (lower.contains("name") || lower.contains("person") || lower.contains("friend") || lower.contains("who") || lower.contains("invitee")) {
                if (nameCol == -1) nameCol = index
            } else if (lower.contains("event") || lower.contains("occasion") || lower.contains("function") || lower.contains("party") || lower.contains("opening")) {
                if (eventCol == -1) eventCol = index
            } else if (lower.contains("description") || lower.contains("desc") || lower.contains("gift") || lower.contains("details") || lower.contains("present") || lower.contains("material")) {
                if (descCol == -1) descCol = index
            } else if (lower.contains("amount") || lower.contains("value") || lower.contains("cash") || lower.contains("rs") || lower.contains("cost") || lower.contains("sum")) {
                if (amountCol == -1) amountCol = index
            } else if (lower.contains("type") || lower.contains("direction") || lower.contains("flow") || lower.contains("receive") || lower.contains("give") || lower.contains("status")) {
                if (dirCol == -1) dirCol = index
            } else if (lower.contains("material") || lower.contains("category") || lower.contains("gift_type")) {
                if (giftTypeCol == -1) giftTypeCol = index
            }
        }

        // Default guesses
        if (nameCol == -1 && headers.isNotEmpty()) nameCol = 0
        if (amountCol == -1 && headers.size > 1) amountCol = headers.size - 1

        return ColumnMapping(
            personNameCol = nameCol,
            eventTypeCol = eventCol,
            giftTypeCol = giftTypeCol,
            giftDescCol = descCol,
            amountCol = amountCol,
            directionCol = dirCol
        )
    }

    // CSV Import Logic
    fun resetImportState() {
        _importUiState.value = ImportUiState.Idle
        _importFileName.value = null
    }

    fun selectExcelFile(inputStream: java.io.InputStream) {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Loading
            try {
                val workbook = com.example.util.ExcelParser.parseXlsx(inputStream)
                if (workbook.sheets.isEmpty() || workbook.sheetData.isEmpty()) {
                    _importUiState.value = ImportUiState.Error("No sheets found in this Excel file.")
                    return@launch
                }

                val allExcelSheetsData = workbook.sheetData
                val sheetsList = workbook.sheets.map { it.name }
                val defaultSheet = sheetsList.first()
                val sheetRows = allExcelSheetsData[defaultSheet] ?: emptyList()

                if (sheetRows.isEmpty()) {
                    _importUiState.value = ImportUiState.Error("The sheet '$defaultSheet' contains no rows.")
                    return@launch
                }

                val headers = sheetRows.first()
                val dataRows = sheetRows.drop(1)

                val mapping = guessMapping(headers)

                val state = ImportUiState.Parsed(
                    headers = headers,
                    rows = dataRows,
                    mapping = mapping,
                    previewRecords = emptyList(),
                    isExcel = true,
                    selectedSheetName = defaultSheet,
                    sheetsList = sheetsList,
                    allExcelSheetsData = allExcelSheetsData
                )

                _importUiState.value = state
                updateMapping(mapping)

            } catch (e: Exception) {
                _importUiState.value = ImportUiState.Error("Failed to parse Excel file: ${e.message}")
            }
        }
    }

    fun loadSampleExcelTemplate() {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Loading
            try {
                val allExcelSheetsData = sampleExcelSheets
                val sheetsList = allExcelSheetsData.keys.toList()
                val defaultSheet = sheetsList.first()
                val sheetRows = allExcelSheetsData[defaultSheet] ?: emptyList()

                val headers = sheetRows.first()
                val dataRows = sheetRows.drop(1)

                val mapping = guessMapping(headers)

                val state = ImportUiState.Parsed(
                    headers = headers,
                    rows = dataRows,
                    mapping = mapping,
                    previewRecords = emptyList(),
                    isExcel = true,
                    selectedSheetName = defaultSheet,
                    sheetsList = sheetsList,
                    allExcelSheetsData = allExcelSheetsData
                )

                _importUiState.value = state
                updateMapping(mapping)
            } catch (e: Exception) {
                _importUiState.value = ImportUiState.Error("Failed to load sample sheet: ${e.message}")
            }
        }
    }

    fun selectSheet(sheetName: String) {
        val currentState = _importUiState.value
        if (currentState is ImportUiState.Parsed && currentState.isExcel) {
            val sheetRows = currentState.allExcelSheetsData[sheetName] ?: emptyList()
            if (sheetRows.isEmpty()) {
                _importUiState.value = currentState.copy(
                    headers = emptyList(),
                    rows = emptyList(),
                    selectedSheetName = sheetName,
                    previewRecords = emptyList()
                )
                return
            }

            val headers = sheetRows.first()
            val dataRows = sheetRows.drop(1)
            val mapping = guessMapping(headers)

            val newState = currentState.copy(
                headers = headers,
                rows = dataRows,
                mapping = mapping,
                selectedSheetName = sheetName,
                previewRecords = emptyList()
            )

            _importUiState.value = newState
            updateMapping(mapping)
        }
    }

    fun selectCsvFile(content: String) {
        viewModelScope.launch {
            _importUiState.value = ImportUiState.Loading
            try {
                val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (lines.isEmpty()) {
                    _importUiState.value = ImportUiState.Error("CSV text is empty.")
                    return@launch
                }

                val headers = parseCsvLine(lines[0])
                val rows = lines.drop(1).map { parseCsvLine(it) }

                val mapping = guessMapping(headers)

                val state = ImportUiState.Parsed(
                    headers = headers,
                    rows = rows,
                    mapping = mapping,
                    previewRecords = emptyList()
                )
                
                // Set and update standard preview
                _importUiState.value = state
                updateMapping(mapping)
                
            } catch (e: Exception) {
                _importUiState.value = ImportUiState.Error("Failed to parse CSV: ${e.message}")
            }
        }
    }

    fun updateMapping(newMapping: ColumnMapping) {
        val currentState = _importUiState.value
        if (currentState is ImportUiState.Parsed) {
            val preview = generatePreviewRecords(currentState.rows, newMapping)
            _importUiState.value = currentState.copy(
                mapping = newMapping,
                previewRecords = preview
            )
        }
    }

    fun confirmImport() {
        val currentState = _importUiState.value
        if (currentState is ImportUiState.Parsed) {
            viewModelScope.launch {
                _importUiState.value = ImportUiState.Loading
                try {
                    val finalRecords = generatePreviewRecords(currentState.rows, currentState.mapping)
                    if (finalRecords.isEmpty()) {
                        _importUiState.value = ImportUiState.Error("No valid records found in the source CSV after mapping.")
                        return@launch
                    }
                    repository.insertRecords(finalRecords)
                    _importUiState.value = ImportUiState.Success(finalRecords.size)
                } catch (e: Exception) {
                    _importUiState.value = ImportUiState.Error("Error inserting records: ${e.message}")
                }
            }
        }
    }

    private fun generatePreviewRecords(rows: List<List<String>>, mapping: ColumnMapping): List<GiftRecord> {
        val previewList = mutableListOf<GiftRecord>()
        // Return up to raw items
        rows.forEach { row ->
            if (row.isEmpty()) return@forEach
            
            // Map Name (required, skip if empty/blank column mapped)
            val name = if (mapping.personNameCol in row.indices) {
                row[mapping.personNameCol].trim()
            } else ""
            if (name.isBlank()) return@forEach

            // Map Event Type
            val event = if (mapping.eventTypeCol in row.indices) {
                row[mapping.eventTypeCol].trim().ifBlank { mapping.defaultEventType }
            } else mapping.defaultEventType

            // Map Gift Description
            val desc = if (mapping.giftDescCol in row.indices) {
                row[mapping.giftDescCol].trim().ifBlank { mapping.defaultGiftDescription }
            } else mapping.defaultGiftDescription

            // Map Amount
            val rawAmount = if (mapping.amountCol in row.indices) {
                val amtStr = row[mapping.amountCol].trim().replace("[^\\d.]".toRegex(), "")
                amtStr.toDoubleOrNull() ?: mapping.defaultAmount
            } else mapping.defaultAmount

            // Map Direction (Received = true, Given = false)
            val isReceived = if (mapping.directionCol in row.indices) {
                val dirVal = row[mapping.directionCol].lowercase()
                when {
                    dirVal.contains("rev") || dirVal.contains("rec") || dirVal.contains("+") || dirVal.contains("in") || dirVal.contains("yes") || dirVal.contains("true") -> true
                    dirVal.contains("giv") || dirVal.contains("sent") || dirVal.contains("-") || dirVal.contains("out") || dirVal.contains("no") || dirVal.contains("false") -> false
                    else -> mapping.defaultIsReceived
                }
            } else {
                mapping.defaultIsReceived
            }

            // Map Gift Type (Cash or Gift)
            val giftType = if (mapping.giftTypeCol in row.indices) {
                val typeVal = row[mapping.giftTypeCol].lowercase()
                if (typeVal.contains("cash") || typeVal.contains("money") || typeVal.contains("amount")) "Cash" else "Gift"
            } else {
                mapping.defaultGiftType
            }

            // Map custom dynamic mappings e.g. "Place"
            val customKeysAndValues = mutableMapOf<String, String>()
            mapping.customMappings.forEach { (fieldName, colIdx) ->
                if (colIdx in row.indices) {
                    val cellVal = row[colIdx].trim()
                    if (cellVal.isNotEmpty()) {
                        customKeysAndValues[fieldName] = cellVal
                    }
                }
            }
            val customSerialized = GiftRecord.formatCustomFields(customKeysAndValues)

            previewList.add(
                GiftRecord(
                    personName = name,
                    eventType = event,
                    giftType = giftType,
                    giftDescription = desc,
                    amount = rawAmount,
                    isReceived = isReceived,
                    date = System.currentTimeMillis(), // default to now
                    customFields = customSerialized
                )
            )
        }
        return previewList
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString().trim().removeSurrounding("\""))
                current = StringBuilder()
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString().trim().removeSurrounding("\""))
        return result
    }
}

class MoiViewModelFactory(private val repository: GiftRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MoiViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MoiViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
