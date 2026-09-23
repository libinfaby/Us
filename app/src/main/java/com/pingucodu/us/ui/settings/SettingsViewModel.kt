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

    val maskNamesEnabled: StateFlow<Boolean> = repository.maskNamesEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )

    fun setMaskNamesEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setMaskNamesEnabled(enabled) }
    }

    val maskLabelPingu: StateFlow<String> = repository.maskLabelPingu.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "A",
    )

    fun setMaskLabelPingu(label: String) {
        viewModelScope.launch { repository.setMaskLabelPingu(label) }
    }

    val maskLabelCodu: StateFlow<String> = repository.maskLabelCodu.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        "B",
    )

    fun setMaskLabelCodu(label: String) {
        viewModelScope.launch { repository.setMaskLabelCodu(label) }
    }
}
