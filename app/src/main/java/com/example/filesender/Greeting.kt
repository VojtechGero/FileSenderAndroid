package com.example.filesender

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun Greeting(modifier: Modifier = Modifier) {
    var localName by remember { mutableStateOf("android") }
    val nameState = rememberTextFieldState(initialText = localName)

    Column(modifier = modifier) {
        Row {
            TextField(
                state = nameState,
                label = { Text("Name") }
            )

            FilledButtonExample {
                localName = nameState.text.toString()
            }
        }

        Text(text = "Hello $localName!")
    }
}



@Composable
fun FilledButtonExample(onClick: () -> Unit) {
    Button(onClick = { onClick() }) {
        Text("Update")
    }
}