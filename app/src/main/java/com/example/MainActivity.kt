package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppDatabase
import com.example.data.GiftRepository
import com.example.ui.MoiApp
import com.example.ui.MoiViewModel
import com.example.ui.MoiViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private var mainViewModel: MoiViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Room Database and Repository
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = GiftRepository(database.giftRecordDao())

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val viewModel: MoiViewModel = viewModel(
                        factory = MoiViewModelFactory(repository)
                    )
                    mainViewModel = viewModel

                    // Handle intent if available
                    LaunchedEffect(intent) {
                        handleIntent(intent, viewModel)
                    }

                    MoiApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        mainViewModel?.let { viewModel ->
            handleIntent(intent, viewModel)
        }
    }

    private fun handleIntent(intent: android.content.Intent?, viewModel: MoiViewModel) {
        if (intent == null) return
        val action = intent.action

        if (android.content.Intent.ACTION_VIEW == action) {
            intent.data?.let { uri ->
                viewModel.handleIncomingUri(this, uri)
            }
        } else if (android.content.Intent.ACTION_SEND == action) {
            val uri = intent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
            if (uri != null) {
                viewModel.handleIncomingUri(this, uri)
            } else {
                val parcelable = intent.getParcelableExtra<android.os.Parcelable>(android.content.Intent.EXTRA_STREAM)
                if (parcelable is android.net.Uri) {
                    viewModel.handleIncomingUri(this, parcelable)
                }
            }
        }
    }
}
