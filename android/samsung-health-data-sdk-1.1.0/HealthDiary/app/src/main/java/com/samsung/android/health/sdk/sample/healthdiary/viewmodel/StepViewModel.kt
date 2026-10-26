/*
 * Copyright (C) 2024 Samsung Electronics Co., Ltd. All rights reserved
 */
package com.samsung.android.health.sdk.sample.healthdiary.viewmodel

import android.app.Activity
import androidx.databinding.ObservableField
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.samsung.android.health.sdk.sample.healthdiary.utils.AppConstants
import com.samsung.android.health.sdk.sample.healthdiary.utils.dateFormat
import com.samsung.android.sdk.health.data.HealthDataStore
import com.samsung.android.sdk.health.data.data.AggregatedData
import com.samsung.android.sdk.health.data.error.ResolvablePlatformException
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.LocalTimeGroup
import com.samsung.android.sdk.health.data.request.LocalTimeGroupUnit
import com.samsung.android.sdk.health.data.request.Ordering
import com.samsung.android.sdk.health.data.response.DataResponse
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDateTime
import kotlinx.coroutines.launch

class StepViewModel(private val healthDataStore: HealthDataStore) :
    ViewModel() {

    private val _exceptionResponse = MutableStateFlow(Throwable("Default"))
    private val _totalStepCountData = MutableLiveData<List<AggregatedData<Long>>>()
    private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
        viewModelScope.launch {
            _exceptionResponse.emit(exception)
        }
    }
    val totalStepCountData: LiveData<List<AggregatedData<Long>>> = _totalStepCountData
    val exceptionResponse: StateFlow<Throwable> = _exceptionResponse
    val totalStepCount = ObservableField<String>()
    val dayStartTimeAsText = ObservableField<String>()

    fun readStepData(dateTime: LocalDateTime) {
        dayStartTimeAsText.set(dateTime.format(dateFormat))

        val localtimeFilter = LocalTimeFilter.of(dateTime, dateTime.plusDays(1))
        val localTimeGroup = LocalTimeGroup.of(LocalTimeGroupUnit.HOURLY, 1)
        val aggregateRequest = DataType.StepsType.TOTAL.requestBuilder
            .setLocalTimeFilterWithGroup(localtimeFilter, localTimeGroup)
            .setOrdering(Ordering.ASC)
            .build()

        /**  Make SDK call to read step data */
        viewModelScope.launch(AppConstants.SCOPE_IO_DISPATCHERS + exceptionHandler) {
            val result = healthDataStore.aggregateData(aggregateRequest)
            processAggregateDataResponse(result)
        }
    }

    private fun processAggregateDataResponse(
        result: DataResponse<AggregatedData<Long>>
    ) {
        val stepCount = ArrayList<AggregatedData<Long>>()
        var totalSteps: Long = 0

        result.dataList.forEach { stepData ->
            val hourlySteps = stepData.value as Long
            totalSteps += hourlySteps
            stepCount.add(stepData)
        }
        totalStepCount.set(totalSteps.toString())
        _totalStepCountData.postValue(stepCount)
    }

    fun setDefaultValueToExceptionResponse()
    {
        _exceptionResponse.value = Throwable("Default")
    }
}
