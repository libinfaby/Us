package com.pingucodu.us.ui.screens.login

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.ui.auth.AuthViewModel
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.DescriptionGrey
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.PinkTint
import com.pingucodu.us.ui.theme.PlaceholderGrey
import com.pingucodu.us.ui.theme.Teal
import com.pingucodu.us.ui.theme.YellowSoft
import com.pingucodu.us.ui.theme.hardShadow
import androidx.compose.ui.draw.rotate

private val FieldShape = RoundedCornerShape(18.dp)
private val ButtonShape = RoundedCornerShape(18.dp)

@Composable
fun LoginScreen(modifier: Modifier = Modifier, viewModel: AuthViewModel = hiltViewModel()) {
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }
    val uiState by viewModel.loginUiState.collectAsState()
    val errorMessage = validationError ?: uiState.errorMessage

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PinkTint)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LogoSwatch(Pink, RoundedCornerShape(6.dp))
            LogoSwatch(Teal, CircleShape)
            LogoSwatch(YellowSoft, RoundedCornerShape(4.dp), rotationDegrees = 45f)
        }
        Spacer(Modifier.height(16.dp))
        Text("an app for\npingu\n& codu", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            "a little app for the two of us, and everything that comes with it.",
            style = MaterialTheme.typography.bodyMedium,
            color = DescriptionGrey,
        )
        Spacer(Modifier.height(24.dp))

        Text("USERNAME", style = PinguCoduType.monoLabel)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it; validationError = null },
            placeholder = { Text("pingu or codu", color = PlaceholderGrey) },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.titleMedium.fontWeight),
            modifier = Modifier
                .fillMaxWidth()
                .hardShadow(FieldShape)
                .border(BorderWidth, Ink, FieldShape),
            shape = FieldShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White,
            ),
        )
        Spacer(Modifier.height(14.dp))

        Text("PIN", style = PinguCoduType.monoLabel)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = pin,
            onValueChange = { pin = it; validationError = null },
            placeholder = { Text("••••••", color = PlaceholderGrey) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = MaterialTheme.typography.titleMedium.fontWeight),
            modifier = Modifier
                .fillMaxWidth()
                .hardShadow(FieldShape)
                .border(BorderWidth, Ink, FieldShape),
            shape = FieldShape,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = Color.Transparent,
                unfocusedContainerColor = Color.White,
                focusedContainerColor = Color.White,
            ),
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(14.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .hardShadow(RoundedCornerShape(12.dp), offsetX = 3.dp, offsetY = 3.dp)
                    .background(Coral, RoundedCornerShape(12.dp))
                    .border(BorderWidth, Ink, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(errorMessage, color = Ink, style = MaterialTheme.typography.bodySmall.copy(fontWeight = MaterialTheme.typography.titleMedium.fontWeight))
            }
        }
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                validationError = when {
                    username.isBlank() && pin.isBlank() -> "enter a username and pin"
                    username.isBlank() -> "enter a username"
                    pin.isBlank() -> "enter a pin"
                    else -> null
                }
                if (validationError == null) viewModel.login(username, pin)
            },
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .hardShadow(ButtonShape)
                .border(BorderWidth, Ink, ButtonShape),
            shape = ButtonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Pink,
                contentColor = Ink,
                disabledContainerColor = Pink,
                disabledContentColor = Ink,
            ),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Ink, strokeWidth = 2.dp)
            } else {
                Text("let me in", style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp))
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("quick:", style = PinguCoduType.mono)
            QuickChip("pingu") { username = "pingu" }
            QuickChip("codu") { username = "codu" }
        }
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = CircleShape,
        border = BorderStroke(1.5.dp, Ink),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = Ink),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge.copy(fontWeight = MaterialTheme.typography.titleMedium.fontWeight))
    }
}

@Composable
private fun LogoSwatch(color: Color, shape: Shape, rotationDegrees: Float = 0f) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .rotate(rotationDegrees)
            .background(color, shape)
            .border(BorderWidth, Ink, shape)
    )
}
