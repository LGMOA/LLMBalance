package com.llmbalance.app.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.llmbalance.app.data.SettingsStore
import com.llmbalance.app.util.LocaleHelper

/**
 * Base activity that applies the saved locale on attach.
 * All activities should extend this to support language switching.
 */
open class BaseActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val settingsStore = SettingsStore(newBase)
        val wrappedContext = LocaleHelper.wrapContext(newBase, settingsStore.language)
        super.attachBaseContext(wrappedContext)
    }
}
