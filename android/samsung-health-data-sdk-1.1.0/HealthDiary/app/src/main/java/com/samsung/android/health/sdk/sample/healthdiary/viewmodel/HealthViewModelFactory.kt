/*
 * Copyright (C) 2024 Samsung Electronics Co., Ltd. All rights reserved
 */
package com.samsung.android.health.sdk.sample.healthdiary.viewmodel

import android.app.Activity
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.samsung.android.sdk.health.data.HealthDataService

class HealthViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        HealthMainViewModel::class.java ->
            HealthMainViewModel(HealthDataService.getStore(context))

        StepViewModel::class.java ->
            StepViewModel(HealthDataService.getStore(context))

        NutritionViewModel::class.java ->
            NutritionViewModel(HealthDataService.getStore(context))

        HeartRateViewModel::class.java ->
            HeartRateViewModel(HealthDataService.getStore(context))

        SleepViewModel::class.java ->
            SleepViewModel(HealthDataService.getStore(context))

        ChooseFoodViewModel::class.java ->
            ChooseFoodViewModel(HealthDataService.getStore(context))

        UpdateFoodViewModel::class.java ->
            UpdateFoodViewModel(HealthDataService.getStore(context))

        else -> throw IllegalArgumentException("Unknown ViewModel class")
    } as T
}
