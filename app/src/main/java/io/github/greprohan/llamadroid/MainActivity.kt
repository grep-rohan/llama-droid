package io.github.greprohan.llamadroid

import android.app.Activity
import android.os.Bundle

/**
 * Minimal placeholder Activity for T3 — establishes the Android Views
 * entry point without any native launch, model import, or inference logic.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_activity)
    }
}
