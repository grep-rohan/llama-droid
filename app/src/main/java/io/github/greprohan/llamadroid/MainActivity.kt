package io.github.greprohan.llamadroid

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Minimal placeholder Activity for T3 — establishes the Android Views
 * entry point without any native launch, model import, or inference logic.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)

        // Placeholder text to confirm the Activity renders.
        findViewById<TextView>(android.R.id.text1)?.text =
            "llama.droid — T3 minimal APK (no native runtime yet)"
    }
}
