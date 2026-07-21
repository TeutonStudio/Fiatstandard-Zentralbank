package de.teutonstudio.zentralbank

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import de.teutonstudio.zentralbank.datenbank.GameViewModel
import de.teutonstudio.zentralbank.schnittstelle.theme.CZBOracleRechnerTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CZBOracleRechnerTheme {
            val factory = GameViewModel.GameViewModelFactory(application)
            val viewModel: GameViewModel = viewModel(factory = factory)
            Navigation(viewModel)
        } }
    }
}
