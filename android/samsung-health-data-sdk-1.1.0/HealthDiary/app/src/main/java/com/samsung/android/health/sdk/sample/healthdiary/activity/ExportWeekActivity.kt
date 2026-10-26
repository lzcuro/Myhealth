package com.samsung.android.health.sdk.sample.healthdiary.activity

import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.samsung.android.sdk.health.data.HealthDataService
import com.samsung.android.sdk.health.data.data.HealthDataPoint
import com.samsung.android.sdk.health.data.data.entries.SleepSession
import com.samsung.android.sdk.health.data.permission.AccessType
import com.samsung.android.sdk.health.data.permission.Permission
import com.samsung.android.sdk.health.data.request.DataType
import com.samsung.android.sdk.health.data.request.DataType.NutritionType
import com.samsung.android.sdk.health.data.request.DataTypes
import com.samsung.android.sdk.health.data.request.LocalTimeFilter
import com.samsung.android.sdk.health.data.request.Ordering
import java.io.File
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ExportWeekActivity : AppCompatActivity() {

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        statusView = TextView(this).apply {
            text = "최근 1주일 수면·식단을 읽는 중"
            textSize = 18f
            setPadding(48, 48, 48, 48)
        }
        setContentView(statusView)
        lifecycleScope.launch(Dispatchers.IO) {
            val message = runCatching { exportWeek() }.getOrElse { error ->
                Log.e(TAG, "export failed", error)
                "FAIL ${error.javaClass.simpleName}: ${error.message}"
            }
            writeText("status.txt", message)
            withContext(Dispatchers.Main) {
                statusView.text = message
            }
        }
    }

    private suspend fun exportWeek(): String {
        val store = HealthDataService.getStore(this)
        val permissions = setOf(
            Permission.of(DataTypes.SLEEP, AccessType.READ),
            Permission.of(DataTypes.NUTRITION, AccessType.READ)
        )
        val granted = store.getGrantedPermissions(permissions)
        val allowed = if (granted.containsAll(permissions)) {
            granted
        } else {
            store.requestPermissions(permissions, this)
        }
        if (!allowed.containsAll(permissions)) {
            return "NO_PERMISSION"
        }

        val end = LocalDateTime.now()
        val start = end.minusDays(7)
        val filter = LocalTimeFilter.of(start, end)
        val sleep = store.readData(
            DataTypes.SLEEP.readDataRequestBuilder
                .setLocalTimeFilter(filter)
                .setOrdering(Ordering.ASC)
                .build()
        ).dataList
        val nutrition = store.readData(
            DataTypes.NUTRITION.readDataRequestBuilder
                .setLocalTimeFilter(filter)
                .setOrdering(Ordering.ASC)
                .build()
        ).dataList

        val root = JSONObject()
            .put("start", start.toString())
            .put("end", end.toString())
            .put("sleep", JSONArray().apply { sleep.forEach { put(sleepPoint(it)) } })
            .put("nutrition", JSONArray().apply { nutrition.forEach { put(nutritionPoint(it)) } })
        writeText("samsung-health-week.json", root.toString(2))
        return "OK sleep=${sleep.size} nutrition=${nutrition.size}"
    }

    private fun sleepPoint(point: HealthDataPoint): JSONObject {
        val sessions = point.getValue(DataType.SleepType.SESSIONS) as? List<SleepSession> ?: emptyList()
        return basePoint(point)
            .put("duration", point.getValue(DataType.SleepType.DURATION)?.toString())
            .put("sleepScore", point.getValue(DataType.SleepType.SLEEP_SCORE))
            .put("sessions", JSONArray().apply { sessions.forEach { put(session(it)) } })
    }

    private fun session(session: SleepSession): JSONObject {
        return JSONObject()
            .put("startTime", session.startTime.toString())
            .put("endTime", session.endTime.toString())
            .put("duration", session.duration.toString())
            .put("stages", JSONArray().apply {
                session.stages.orEmpty().forEach { stage ->
                    put(
                        JSONObject()
                            .put("startTime", stage.startTime.toString())
                            .put("endTime", stage.endTime.toString())
                            .put("stage", stage.stage.name)
                    )
                }
            })
    }

    private fun nutritionPoint(point: HealthDataPoint): JSONObject {
        return basePoint(point)
            .put("mealType", point.getValue(NutritionType.MEAL_TYPE)?.name)
            .put("title", point.getValue(NutritionType.TITLE))
            .put("calories", point.getValue(NutritionType.CALORIES))
            .put("protein", point.getValue(NutritionType.PROTEIN))
            .put("totalFat", point.getValue(NutritionType.TOTAL_FAT))
            .put("saturatedFat", point.getValue(NutritionType.SATURATED_FAT))
            .put("polyunsaturatedFat", point.getValue(NutritionType.POLYSATURATED_FAT))
            .put("monounsaturatedFat", point.getValue(NutritionType.MONOSATURATED_FAT))
            .put("transFat", point.getValue(NutritionType.TRANS_FAT))
            .put("carbohydrate", point.getValue(NutritionType.CARBOHYDRATE))
            .put("dietaryFiber", point.getValue(NutritionType.DIETARY_FIBER))
            .put("sugar", point.getValue(NutritionType.SUGAR))
            .put("cholesterol", point.getValue(NutritionType.CHOLESTEROL))
            .put("sodium", point.getValue(NutritionType.SODIUM))
            .put("potassium", point.getValue(NutritionType.POTASSIUM))
            .put("vitaminA", point.getValue(NutritionType.VITAMIN_A))
            .put("vitaminC", point.getValue(NutritionType.VITAMIN_C))
            .put("calcium", point.getValue(NutritionType.CALCIUM))
            .put("iron", point.getValue(NutritionType.IRON))
    }

    private fun basePoint(point: HealthDataPoint): JSONObject {
        return JSONObject()
            .put("uid", point.uid)
            .put("startTime", point.startTime?.toString())
            .put("endTime", point.endTime?.toString())
            .put("updateTime", point.updateTime?.toString())
            .put("zoneOffset", point.zoneOffset?.toString())
            .put("appId", point.dataSource?.appId)
            .put("deviceId", point.dataSource?.deviceId)
    }

    private fun writeText(name: String, text: String) {
        File(filesDir, name).writeText(text)
        Log.i(TAG, "$name bytes=${text.length}")
    }

    companion object {
        private const val TAG = "ExportWeek"
    }
}
