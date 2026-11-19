package com.wigters.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/** Activity invisível que só repassa o toque do widget para o app/tela de destino. */
class LaunchActivity : Activity() {
    companion object {
        const val EXTRA_FALLBACK = "com.wigters.app.FALLBACK"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val target = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        @Suppress("DEPRECATION")
        val fallback = intent.getParcelableExtra<Intent>(EXTRA_FALLBACK)
        val ok = target != null &&
            runCatching { startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
        if (!ok && fallback != null) {
            runCatching { startActivity(fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
