package de.teutonstudio.zentralbank.ui.kategorien

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.sp
import de.teutonstudio.zentralbank.R
import de.teutonstudio.zentralbank.ui.ModiPad5

@Composable
fun NeueRunde(onNewRound: () -> Unit, content: @Composable () -> Unit) {
    Box(modifier = ModiPad5.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
        content()
        Column {
            Text(text = "neue Runde", fontSize = 30.sp)
            Image(painter = painterResource(id = R.drawable.dice), contentDescription = null, modifier = Modifier.clickable
            { onNewRound() })
        }
    }
}
