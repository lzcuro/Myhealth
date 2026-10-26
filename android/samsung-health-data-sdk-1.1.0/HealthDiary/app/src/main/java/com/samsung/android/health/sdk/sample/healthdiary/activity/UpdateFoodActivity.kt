package com.samsung.android.health.sdk.sample.healthdiary.activity

import android.os.Build
import android.os.Bundle
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.samsung.android.health.sdk.sample.healthdiary.R
import com.samsung.android.health.sdk.sample.healthdiary.UpdateFoodActivityBinding
import com.samsung.android.health.sdk.sample.healthdiary.adapters.UpdateFoodAdapter
import com.samsung.android.health.sdk.sample.healthdiary.utils.AppConstants
import com.samsung.android.health.sdk.sample.healthdiary.entries.NutritionUpdateData
import com.samsung.android.health.sdk.sample.healthdiary.utils.getMealType
import com.samsung.android.health.sdk.sample.healthdiary.utils.getTime
import com.samsung.android.health.sdk.sample.healthdiary.utils.resolveException
import com.samsung.android.health.sdk.sample.healthdiary.utils.showToast
import com.samsung.android.health.sdk.sample.healthdiary.viewmodel.HealthViewModelFactory
import com.samsung.android.health.sdk.sample.healthdiary.viewmodel.UpdateFoodViewModel
import com.samsung.android.sdk.health.data.request.DataType.NutritionType.MealType
import kotlinx.coroutines.launch
import java.util.ArrayList

class UpdateFoodActivity : AppCompatActivity() {

    private lateinit var binding: UpdateFoodActivityBinding
    private lateinit var updateFoodViewModel: UpdateFoodViewModel
    private lateinit var updateFoodAdapter: UpdateFoodAdapter
    private lateinit var mealType: MealType

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        updateFoodViewModel = ViewModelProvider(
            this, HealthViewModelFactory(this)
        )[UpdateFoodViewModel::class.java]

        updateFoodAdapter = UpdateFoodAdapter(updateFoodViewModel)

        binding = DataBindingUtil
            .setContentView<UpdateFoodActivityBinding>(this, R.layout.update_food)
            .apply {
                viewModel = updateFoodViewModel
                updateFoodList.layoutManager = LinearLayoutManager(this@UpdateFoodActivity)
                updateFoodList.adapter = updateFoodAdapter
            }

        mealType = getMealType(intent)
        binding.textDesc.text = mealType.toString().format()
        val foodList = intent.extras?.getParcelableArrayList(
            AppConstants.BUNDLE_KEY_NUTRITION_DATA,
            NutritionUpdateData::class.java
        )

        setup(foodList, getTime(intent))
        setUpdateFoodObserver()
    }

    override fun onStop() {
        super.onStop()
        updateFoodViewModel.setDefaultValueToExceptionResponse()
    }

    private fun setup(foodList: ArrayList<NutritionUpdateData>?, time: String) {
        if (foodList != null) {
            updateFoodAdapter.updateList(mealType, foodList, time)
        }
    }

    private fun setUpdateFoodObserver() {
        /**  Show update success response */
        updateFoodViewModel.nutritionUpdateResponse.observe(this) {
            showToast(this, "Data updated")
            finish()
        }

        /**  Show delete success response */
        updateFoodViewModel.nutritionDeleteResponse.observe(this) {
            showToast(this, "Data deleted")
            finish()
        }

        /** Show toast on exception occurrence **/
        lifecycleScope.launch {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                updateFoodViewModel.exceptionResponse.collect { exception ->
                    if(exception.message!! != "Default"){
                        showToast(this@UpdateFoodActivity, exception.message!!)
                        resolveException(exception, this@UpdateFoodActivity)
                    }
                }
            }
        }
    }

    private fun String.format(): String =
        this.lowercase().replaceFirstChar { it.titlecase() }.replace("_", " ")
}
