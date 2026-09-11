package com.pingucodu.us.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.Yellow

/**
 * Login shell. No backend yet (Phase 3+) - "let me in" and the quick chips just accept
 * whatever username was typed/picked so we can verify the auth-gated nav flow end to end.
 */
@Composable
fun LoginScreen(onLoggedIn: (username: String) -> Unit, modifier: Modifier = Modifier) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Pink, RoundedCornerShape(6.dp))
            Box(Teal, CircleShape)
            Box(Yellow, RoundedCornerShape(4.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text("pingu\n& codu", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "two users. one wallet-ish. no more calculator in the whatsapp group.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(28.dp))

        Text("username", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            placeholder = { Text("pingu or codu") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Ink,
                focusedBorderColor = Ink,
            ),
        )
        Spacer(Modifier.height(16.dp))

        Text("password", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("••••••") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Ink,
                focusedBorderColor = Ink,
            ),
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = { onLoggedIn(username.ifBlank { "pingu" }) },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .border(BorderWidth, Ink, RoundedCornerShape(14.dp)),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Ink),
        ) {
            Text("let me in", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("quick: ", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onLoggedIn("pingu") }) { Text("pingu") }
            TextButton(onClick = { onLoggedIn("codu") }) { Text("codu") }
        }
    }
}

@Composable
private fun Box(color: androidx.compose.ui.graphics.Color, shape: androidx.compose.ui.graphics.Shape) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(28.dp)
            .background(color, shape)
            .border(BorderWidth, Ink, shape)
    )
}
