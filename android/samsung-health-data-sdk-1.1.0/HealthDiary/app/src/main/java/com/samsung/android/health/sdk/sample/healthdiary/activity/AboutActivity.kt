/*
 * Copyright (C) 2024 Samsung Electronics Co., Ltd. All rights reserved
 */
package com.samsung.android.health.sdk.sample.healthdiary.activity

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import com.samsung.android.health.sdk.sample.healthdiary.R
import com.samsung.android.health.sdk.sample.healthdiary.databinding.AboutHealthDiaryBinding

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val versionName = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0)).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            } ?: "Unknown"
        } catch (_: PackageManager.NameNotFoundException) {
            "Unknown"
        }

        DataBindingUtil
            .setContentView<AboutHealthDiaryBinding>(this, R.layout.about_health_diary)
            .apply {
                appVersionValue.text = versionName
            }
    }
}

