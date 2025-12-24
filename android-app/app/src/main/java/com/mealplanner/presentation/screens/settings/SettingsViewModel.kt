package com.mealplanner.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealplanner.domain.model.UserPreferences
import com.mealplanner.domain.usecase.LoadSampleDataUseCase
import com.mealplanner.domain.usecase.ManagePreferencesUseCase
import com.mealplanner.domain.usecase.PantryStaplesResult
import com.mealplanner.domain.usecase.SampleDataResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesUseCase: ManagePreferencesUseCase,
    private val loadSampleDataUseCase: LoadSampleDataUseCase
) : ViewModel() {

    private val _preferences = MutableStateFlow(UserPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private val _sampleDataState = MutableStateFlow<SampleDataState>(SampleDataState.Idle)
    val sampleDataState: StateFlow<SampleDataState> = _sampleDataState.asStateFlow()

    private val _pantryStaplesState = MutableStateFlow<PantryStaplesState>(PantryStaplesState.Idle)
    val pantryStaplesState: StateFlow<PantryStaplesState> = _pantryStaplesState.asStateFlow()

    init {
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesUseCase.observePreferences().collect { prefs ->
                _preferences.value = prefs
            }
        }
    }

    fun updateApiKey(apiKey: String) {
        viewModelScope.launch {
            preferencesUseCase.updateApiKey(apiKey.takeIf { it.isNotBlank() })
        }
    }

    fun updateTargetServings(servings: Int) {
        viewModelScope.launch {
            preferencesUseCase.updateTargetServings(servings)
        }
    }

    fun loadSampleData() {
        viewModelScope.launch {
            _sampleDataState.value = SampleDataState.Loading

            loadSampleDataUseCase.loadSampleData()
                .onSuccess { result ->
                    _sampleDataState.value = SampleDataState.Success(result)
                }
                .onFailure { error ->
                    _sampleDataState.value = SampleDataState.Error(error.message ?: "Failed to load sample data")
                }
        }
    }

    fun dismissSampleDataState() {
        _sampleDataState.value = SampleDataState.Idle
    }

    fun loadPantryStaples() {
        viewModelScope.launch {
            _pantryStaplesState.value = PantryStaplesState.Loading

            loadSampleDataUseCase.loadPantryStaples()
                .onSuccess { result ->
                    _pantryStaplesState.value = PantryStaplesState.Success(result)
                }
                .onFailure { error ->
                    _pantryStaplesState.value = PantryStaplesState.Error(error.message ?: "Failed to load pantry staples")
                }
        }
    }

    fun dismissPantryStaplesState() {
        _pantryStaplesState.value = PantryStaplesState.Idle
    }
}

sealed class SampleDataState {
    data object Idle : SampleDataState()
    data object Loading : SampleDataState()
    data class Success(val result: SampleDataResult) : SampleDataState()
    data class Error(val message: String) : SampleDataState()
}

sealed class PantryStaplesState {
    data object Idle : PantryStaplesState()
    data object Loading : PantryStaplesState()
    data class Success(val result: PantryStaplesResult) : PantryStaplesState()
    data class Error(val message: String) : PantryStaplesState()
}
