package com.mealplanner.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mealplanner.domain.usecase.LoadSampleDataUseCase
import com.mealplanner.domain.usecase.PantryStaplesResult
import com.mealplanner.domain.usecase.SampleDataResult
import com.mealplanner.domain.usecase.TestModeUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val loadSampleDataUseCase: LoadSampleDataUseCase,
    private val testModeUseCase: TestModeUseCase
) : ViewModel() {

    private val _sampleDataState = MutableStateFlow<SampleDataState>(SampleDataState.Idle)
    val sampleDataState: StateFlow<SampleDataState> = _sampleDataState.asStateFlow()

    private val _pantryStaplesState = MutableStateFlow<PantryStaplesState>(PantryStaplesState.Idle)
    val pantryStaplesState: StateFlow<PantryStaplesState> = _pantryStaplesState.asStateFlow()

    private val _testModeState = MutableStateFlow<TestModeState>(TestModeState.Idle)
    val testModeState: StateFlow<TestModeState> = _testModeState.asStateFlow()

    val isTestModeEnabled: StateFlow<Boolean> = testModeUseCase.observeTestMode()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    fun enableTestMode() {
        viewModelScope.launch {
            _testModeState.value = TestModeState.Loading

            testModeUseCase.enableTestMode()
                .onSuccess {
                    _testModeState.value = TestModeState.Success("Test Mode enabled")
                }
                .onFailure { error ->
                    _testModeState.value = TestModeState.Error(error.message ?: "Failed to enable test mode")
                }
        }
    }

    fun disableTestMode() {
        viewModelScope.launch {
            _testModeState.value = TestModeState.Loading

            testModeUseCase.disableTestMode()
                .onSuccess {
                    _testModeState.value = TestModeState.Success("Test Mode disabled")
                }
                .onFailure { error ->
                    _testModeState.value = TestModeState.Error(error.message ?: "Failed to disable test mode")
                }
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            _testModeState.value = TestModeState.Loading

            testModeUseCase.clearAllData()
                .onSuccess {
                    _testModeState.value = TestModeState.Success("All data cleared")
                }
                .onFailure { error ->
                    _testModeState.value = TestModeState.Error(error.message ?: "Failed to clear data")
                }
        }
    }

    fun dismissTestModeState() {
        _testModeState.value = TestModeState.Idle
    }
}

sealed class TestModeState {
    data object Idle : TestModeState()
    data object Loading : TestModeState()
    data class Success(val message: String) : TestModeState()
    data class Error(val message: String) : TestModeState()
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
