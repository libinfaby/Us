package com.pingucodu.us.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pingucodu.us.data.settings.AppSettingsRepository
import com.pingucodu.us.ui.theme.FontScaleLevel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: AppSettingsRepository,
) : ViewModel() {
    val fontScaleLevel: StateFlow<FontScaleLevel> = repository.fontScaleLevel.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        FontScaleLevel.Default,
    )

    fun setFontScaleLevel(level: FontScaleLevel) {
        viewModelScope.launch { repository.setFontScaleLevel(level) }
    }
}
