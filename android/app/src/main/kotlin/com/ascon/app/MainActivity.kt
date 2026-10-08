package com.ascon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

/** The single activity. Navigation between features is wired here in step 4. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AsconApp() }
    }
}

@Composable
private fun AsconApp() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText("Ascon")
    }
}

@Preview
@Composable
private fun AsconAppPreview() {
    AsconApp()
}
