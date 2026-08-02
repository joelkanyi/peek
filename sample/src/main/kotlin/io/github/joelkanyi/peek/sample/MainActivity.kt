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
