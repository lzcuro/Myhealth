package com.samsung.android.health.sdk.sample.healthdiary.activity

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.samsung.android.health.sdk.sample.healthdiary.ChooseFoodActivityBinding
import com.samsung.android.health.sdk.sample.healthdiary.R
import com.samsung.android.health.sdk.sample.healthdiary.adapters.ChooseFoodAdapter
import com.samsung.android.health.sdk.sample.healthdiary.entries.FoodInfoTable
import com.samsung.android.health.sdk.sample.healthdiary.utils.getMealType
import com.samsung.android.health.sdk.sample.healthdiary.utils.getTime
import com.samsung.android.health.sdk.sample.healthdiary.utils.resolveException
import com.samsung.android.health.sdk.sample.healthdiary.utils.showToast
import com.samsung.android.health.sdk.sample.healthdiary.viewmodel.ChooseFoodViewModel
import com.samsung.android.health.sdk.sample.healthdiary.viewmodel.HealthViewModelFactory
import com.samsung.android.sdk.health.data.request.DataType.NutritionType.MealType
import kotlinx.coroutines.launch

class ChooseFoodActivity : AppCompatActivity() {

    private lateinit var binding: ChooseFoodActivityBinding
    private lateinit var chooseFoodViewModel: ChooseFoodViewModel
    private lateinit var chooseFoodAdapter: ChooseFoodAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chooseFoodViewModel = ViewModelProvider(
            this, HealthViewModelFactory(this)
        )[ChooseFoodViewModel::class.java]

        chooseFoodAdapter = ChooseFoodAdapter(chooseFoodViewModel)

        binding = DataBindingUtil
            .setContentView<ChooseFoodActivityBinding>(this, R.layout.choose_food)
            .apply {
                viewModel = chooseFoodViewModel
                chooseFoodList.layoutManager = LinearLayoutManager(this@ChooseFoodActivity)
                chooseFoodList.adapter = chooseFoodAdapter
            }

        setup(getMealType(intent), getTime(intent))
        setChooseFoodObserver()
    }

    override fun onStop() {
        super.onStop()
        chooseFoodViewModel.setDefaultValueToExceptionResponse()
    }

    private fun setup(mealType: MealType, time: String) {
        chooseFoodAdapter.updateList(mealType, FoodInfoTable.keys(), time)
    }

    private fun setChooseFoodObserver() {
        /** Show insert success response */
        chooseFoodViewModel.nutritionInsertResponse.observe(this) {
            showToast(this, "Data inserted")
            finish()
        }

        /** Show toast on exception occurrence **/
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                chooseFoodViewModel.exceptionResponse.collect { exception ->
                    if(exception.message!! != "Default"){
                        showToast(this@ChooseFoodActivity, exception.message!!)
                        resolveException(exception, this@ChooseFoodActivity)
                    }
                }
            }
        }
    }
}
