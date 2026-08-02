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

/** Writes and mutates SharedPreferences so Peek has real, changing data to show. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("sample_prefs", MODE_PRIVATE)
        val launches = prefs.getInt("launch_count", 0) + 1
        prefs.edit()
            .putInt("launch_count", launches)
            .putString("last_user", "joel")
            .putBoolean("dark_mode", true)
            .putStringSet("tags", setOf("beta", "offline"))
            .apply()

        val label = TextView(this).apply { text = "Peek Sample\nlaunch_count = $launches\nTap to bump a counter." }
        val bump = Button(this).apply {
            text = "Bump counter"
            setOnClickListener {
                val next = prefs.getInt("counter", 0) + 1
                prefs.edit().putInt("counter", next).apply()
                label.text = "Peek Sample\nlaunch_count = $launches\ncounter = $next"
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
}
