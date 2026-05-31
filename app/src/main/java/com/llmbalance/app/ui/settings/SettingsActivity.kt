package com.llmbalance.app.ui.settings

import android.os.Bundle
import android.widget.Toast
import com.llmbalance.app.R
import com.llmbalance.app.data.SettingsStore
import com.llmbalance.app.ntfy.NtfyService
import com.llmbalance.app.ntfy.NotificationHelper
import com.llmbalance.app.ui.BaseActivity
import com.llmbalance.app.util.LocaleHelper
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.switchmaterial.SwitchMaterial

class SettingsActivity : BaseActivity() {

    private lateinit var settingsStore: SettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        settingsStore = SettingsStore(this)

        val etServerUrl = findViewById<TextInputEditText>(R.id.etServerUrl)
        val etTopic = findViewById<TextInputEditText>(R.id.etTopic)
        val switchEnabled = findViewById<SwitchMaterial>(R.id.switchEnabled)
        val btnSave = findViewById<MaterialButton>(R.id.btnSave)
        val btnTest = findViewById<MaterialButton>(R.id.btnTest)
        val btnLangZh = findViewById<MaterialButton>(R.id.btnLangZh)
        val btnLangEn = findViewById<MaterialButton>(R.id.btnLangEn)

        // Load current settings
        etServerUrl.setText(settingsStore.serverUrl)
        etTopic.setText(settingsStore.topic)
        switchEnabled.isChecked = settingsStore.enabled
        updateLanguageButtons(settingsStore.language)

        // Language selection handlers
        btnLangZh.setOnClickListener { selectLanguage(LocaleHelper.LANGUAGE_CHINESE) }
        btnLangEn.setOnClickListener { selectLanguage(LocaleHelper.LANGUAGE_ENGLISH) }

        btnSave.setOnClickListener {
            val serverUrl = etServerUrl.text.toString().trim()
            val topic = etTopic.text.toString().trim()

            if (serverUrl.isBlank() || topic.isBlank()) {
                Toast.makeText(this, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val needsRestart = serverUrl != settingsStore.serverUrl ||
                    topic != settingsStore.topic ||
                    switchEnabled.isChecked != settingsStore.enabled

            settingsStore.serverUrl = serverUrl
            settingsStore.topic = topic
            settingsStore.enabled = switchEnabled.isChecked

            Toast.makeText(this, getString(R.string.settings_saved), Toast.LENGTH_SHORT).show()

            if (needsRestart) {
                if (switchEnabled.isChecked) {
                    NtfyService.stop(this)
                    // Slight delay before restart to let old connection clean up
                    window.decorView.postDelayed({
                        if (!isFinishing && !isDestroyed) {
                            NtfyService.start(this)
                        }
                    }, 500)
                } else {
                    NtfyService.stop(this)
                }
            }
        }

        btnTest.setOnClickListener {
            NotificationHelper.showTestNotification(this)
        }

        // Toolbar back button
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            finish()
        }
    }

    private fun selectLanguage(languageCode: String) {
        if (languageCode == settingsStore.language) return

        settingsStore.language = languageCode
        updateLanguageButtons(languageCode)

        Toast.makeText(this, getString(R.string.language_restart_hint), Toast.LENGTH_SHORT).show()

        // Recreate activity to apply new locale
        recreate()
    }

    private fun updateLanguageButtons(selectedLanguage: String) {
        val btnLangZh = findViewById<MaterialButton>(R.id.btnLangZh)
        val btnLangEn = findViewById<MaterialButton>(R.id.btnLangEn)

        val isZh = selectedLanguage == LocaleHelper.LANGUAGE_CHINESE
        btnLangZh.isEnabled = !isZh
        btnLangEn.isEnabled = isZh
    }
}
