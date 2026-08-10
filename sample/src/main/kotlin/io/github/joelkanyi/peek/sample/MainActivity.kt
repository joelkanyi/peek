/*
 * Copyright 2026 Joel Kanyi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.joelkanyi.peek.sample

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Seeds every store type Peek reads with realistic, fake data, then lets you mutate a value live. */
class MainActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val now = System.currentTimeMillis()
        val session = getSharedPreferences("app_settings", MODE_PRIVATE)
        val launchCount = session.getInt("app_launch_count", 0) + 1

        seedSharedPreferences(launchCount, now)
        seedMultiplatformSettings(now)
        scope.launch(Dispatchers.IO) {
            seedPreferencesDataStore()
            seedUserProfile(now)
        }

        val prefs = getSharedPreferences("user_session", MODE_PRIVATE)
        val label = TextView(this).apply {
            text = "Peek Sample\nSeeded SharedPreferences, DataStore, Proto, and Multiplatform Settings.\nlaunch #$launchCount"
        }
        val bump = Button(this).apply {
            text = "Add to cart"
            setOnClickListener {
                val next = prefs.getInt("cart_items_added", 0) + 1
                prefs.edit().putInt("cart_items_added", next).apply()
                label.text = "Peek Sample\nWatch the value change live in Peek.\ncart_items_added = $next"
            }
        }

        setContentView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(48, 96, 48, 48)
                addView(label)
                addView(bump)
            },
        )
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
