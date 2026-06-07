package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GiftRecord
import com.example.data.GiftRecordDao
import com.example.data.GiftRepository
import com.example.ui.ImportUiState
import com.example.ui.MoiViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// Standalone Mock DAO implementation for local unit testing
class TestGiftRecordDao : GiftRecordDao {
    private val records = mutableListOf<GiftRecord>()
    override fun getAllRecords(): Flow<List<GiftRecord>> = flowOf(records)
    override fun searchRecords(query: String): Flow<List<GiftRecord>> = flowOf(records)
    override suspend fun insertRecord(record: GiftRecord): Long { records.add(record); return 1L }
    override suspend fun insertRecords(recordsList: List<GiftRecord>) { records.addAll(recordsList) }
    override suspend fun updateRecord(record: GiftRecord) {}
    override suspend fun deleteRecord(record: GiftRecord) {}
    override suspend fun deleteRecordById(id: Int) {}
    override suspend fun deleteAllRecords() { records.clear() }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Moi", appName)
    }

    @Test
    fun `test loading excel sample template updates ui state`() = runTest {
        val mockDao = TestGiftRecordDao()
        val repository = GiftRepository(mockDao)
        val viewModel = MoiViewModel(repository)
        
        viewModel.loadSampleExcelTemplate()
        
        // Retrieve the updated parsed state
        val state = viewModel.importUiState.value
        assertTrue(state is ImportUiState.Parsed)
        
        val parsedState = state as ImportUiState.Parsed
        assertEquals(true, parsedState.isExcel)
        assertEquals("Wedding Moi List", parsedState.selectedSheetName)
        assertEquals(3, parsedState.sheetsList.size)
        assertTrue(parsedState.sheetsList.contains("Housewarming & Pooja"))
    }

    @Test
    fun `test sheet switching matches correct data columns`() = runTest {
        val mockDao = TestGiftRecordDao()
        val repository = GiftRepository(mockDao)
        val viewModel = MoiViewModel(repository)
        
        viewModel.loadSampleExcelTemplate()
        
        // Pre-assertion
        var parsedState = viewModel.importUiState.value as ImportUiState.Parsed
        assertEquals("Wedding Moi List", parsedState.selectedSheetName)
        assertEquals("Invitee Name", parsedState.headers[0])
        
        // Select sheet 2
        viewModel.selectSheet("Housewarming & Pooja")
        
        parsedState = viewModel.importUiState.value as ImportUiState.Parsed
        assertEquals("Housewarming & Pooja", parsedState.selectedSheetName)
        assertEquals("Person", parsedState.headers[0])
        assertEquals("Occasion", parsedState.headers[1])
    }
}
