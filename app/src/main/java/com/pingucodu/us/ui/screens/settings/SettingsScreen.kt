package com.pingucodu.us.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.pingucodu.us.ui.auth.AuthViewModel
import com.pingucodu.us.ui.settings.SettingsViewModel
import com.pingucodu.us.ui.theme.BorderWidth
import com.pingucodu.us.ui.theme.Coral
import com.pingucodu.us.ui.theme.DashedDivider
import com.pingucodu.us.ui.theme.DescriptionGrey
import com.pingucodu.us.ui.theme.FontScaleLevel
import com.pingucodu.us.ui.theme.Ink
import com.pingucodu.us.ui.theme.Pink
import com.pingucodu.us.ui.theme.PinguCoduType
import com.pingucodu.us.ui.theme.PlaceholderGrey

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val fontScaleLevel by viewModel.fontScaleLevel.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Row(modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "back")
            }
        }
        Text("settings", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(24.dp))

        Text("TEXT SIZE", style = PinguCoduType.monoLabel)
        Spacer(Modifier.height(4.dp))
        Text(
            "changes the font size across the entire app.",
            style = MaterialTheme.typography.bodySmall,
            color = DescriptionGrey,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FontScaleLevel.entries.forEach { level ->
                FontSizeOption(
                    level = level,
                    selected = level == fontScaleLevel,
                    onClick = { viewModel.setFontScaleLevel(level) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        DashedDivider()
        Spacer(Modifier.height(20.dp))

        ChangePinSection(viewModel = authViewModel)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun ChangePinSection(viewModel: AuthViewModel) {
    var currentPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val uiState by viewModel.changePinUiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.resetChangePinState() }
    LaunchedEffect(uiState.success) {
        if (uiState.success) {
            currentPin = ""
            newPin = ""
            confirmPin = ""
        }
    }

    val mismatch = confirmPin.isNotEmpty() && newPin != confirmPin

    Text("CHANGE PIN", style = PinguCoduType.monoLabel)
    Spacer(Modifier.height(12.dp))

    Text("current pin", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = currentPin,
        onValueChange = { currentPin = it },
        placeholder = { Text("••••••", color = PlaceholderGrey) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
    )
    Spacer(Modifier.height(16.dp))

    Text("new pin", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = newPin,
        onValueChange = { newPin = it },
        placeholder = { Text("••••••", color = PlaceholderGrey) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
    )
    Spacer(Modifier.height(16.dp))

    Text("confirm new pin", style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = confirmPin,
        onValueChange = { confirmPin = it },
        placeholder = { Text("••••••", color = PlaceholderGrey) },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Ink, focusedBorderColor = Ink),
    )
    Spacer(Modifier.height(10.dp))

    val errorText = uiState.errorMessage ?: if (mismatch) "new pin and confirmation don't match" else null
    if (errorText != null) {
        Text(errorText, color = Coral, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
    }
    if (uiState.success) {
        Text("pin updated", color = Ink, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
    }

    Button(
        onClick = { viewModel.changePin(currentPin, newPin) },
        enabled = !uiState.isLoading &&
            currentPin.isNotBlank() &&
            newPin.length >= 4 &&
            newPin == confirmPin,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .border(BorderWidth, Ink, RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Pink, contentColor = Ink),
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Ink, strokeWidth = 2.dp)
        } else {
            Text("update pin", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FontSizeOption(
    level: FontScaleLevel,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .height(76.dp)
            .background(if (selected) Pink else Color.White, RoundedCornerShape(14.dp))
            .border(BorderWidth, Ink, RoundedCornerShape(14.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Aa", style = MaterialTheme.typography.titleLarge.copy(fontSize = (14 + level.ordinal * 3).sp))
        Spacer(Modifier.height(4.dp))
        Text(level.label, style = PinguCoduType.monoLabel, textAlign = TextAlign.Center)
    }
}
