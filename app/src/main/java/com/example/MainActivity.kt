package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.ui.navigation.MainAppNavigation
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.FantaAstaTheme
import com.example.ui.viewmodel.AuctionViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AuctionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FantaAstaTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = DarkBackground
                ) {
                    MainAppNavigation(viewModel = viewModel)
                }
            }
        }
    }
}
