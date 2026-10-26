package com.samsung.android.health.sdk.sample.healthdiary.viewmodel

import android.app.Activity
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samsung.android.health.sdk.sample.healthdiary.utils.AppConstants
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.IdFilter
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UpdateFoodViewModel(private val healthDataStore: HealthDataStore) :
    ViewModel() {

    private val _nutritionUpdateResponse = MutableLiveData<Boolean>()
    private val _nutritionDeleteResponse = MutableLiveData<Boolean>()
    private val _exceptionResponse = MutableStateFlow(Throwable("Default"))
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        viewModelScope.launch {
            _exceptionResponse.emit(exception)
        }
    }
    val exceptionResponse: StateFlow<Throwable> = _exceptionResponse
    val nutritionUpdateResponse: LiveData<Boolean> = _nutritionUpdateResponse
    val nutritionDeleteResponse: LiveData<Boolean> = _nutritionDeleteResponse

    fun updateNutritionData(uid: String, nutritionData: HealthDataPoint) {
        val updateRequest = DataTypes.NUTRITION.updateDataRequestBuilder
            .addDataWithUid(uid, nutritionData)
            .build()

        /**  Make SDK call to update nutrition data */
        viewModelScope.launch(AppConstants.SCOPE_IO_DISPATCHERS + exceptionHandler) {
            healthDataStore.updateData(updateRequest)
            _nutritionUpdateResponse.postValue(true)
        }
    }

    fun deleteNutritionData(uid: String) {
        val idFilter = IdFilter.builder()
            .addDataUid(uid)
            .build()

        val deleteRequest = DataTypes.NUTRITION.deleteDataRequestBuilder
            .setIdFilter(idFilter)
            .build()

        /**  Make SDK call to update nutrition data */
        viewModelScope.launch(AppConstants.SCOPE_IO_DISPATCHERS + exceptionHandler) {
            healthDataStore.deleteData(deleteRequest)
            _nutritionDeleteResponse.postValue(true)
        }
    }

    fun setDefaultValueToExceptionResponse()
    {
        _exceptionResponse.value = Throwable("Default")
    }
}
