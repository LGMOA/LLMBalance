package com.llmbalance.app

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.llmbalance.app.data.KeyStore
import com.llmbalance.app.data.Providers
import com.llmbalance.app.data.SettingsStore
import com.llmbalance.app.databinding.ActivityMainBinding
import com.llmbalance.app.ntfy.NtfyService
import com.llmbalance.app.ui.BalanceAdapter
import com.llmbalance.app.ui.BaseActivity
import com.llmbalance.app.ui.MainViewModel
import com.llmbalance.app.ui.settings.SettingsActivity

class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: BalanceAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val keyStore = KeyStore(this)
        viewModel = ViewModelProvider(
            this,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    return MainViewModel(keyStore) as T
                }
            }
        )[MainViewModel::class.java]

        setupRecyclerView()
        observeData()
        setupClickListeners()
        startNtfyService()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startNtfyService() {
        val settingsStore = SettingsStore(this)
        if (settingsStore.enabled && settingsStore.isConfigured()) {
            NtfyService.start(this)
        }
    }

    private fun setupRecyclerView() {
        adapter = BalanceAdapter(
            onDelete = { id -> showDeleteConfirmDialog(id) }
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
    }

    private fun observeData() {
        viewModel.keys.observe(this) { keys ->
            adapter.submitList(keys)
            binding.tvEmpty.visibility = if (keys.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerView.visibility = if (keys.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.error.observe(this) { error ->
            error?.let {
                Toast.makeText(this, it, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    private fun setupClickListeners() {
        binding.fabAdd.setOnClickListener {
            showAddKeyDialog()
        }

        binding.btnQueryAll.setOnClickListener {
            viewModel.queryAll()
        }
    }

    private fun showAddKeyDialog() {
        val providers = Providers.ALL
        val providerNames = providers.map { "${it.name} - ${it.description}" }.toTypedArray()
        var selectedProviderIndex = 0

        val dialogView = layoutInflater.inflate(android.R.layout.simple_list_item_1, null)
        // We'll use a simpler approach with AlertDialog

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.add_key)

        // Inflate custom dialog
        val inputView = layoutInflater.inflate(
            resources.getIdentifier("dialog_add_key", "layout", packageName),
            null
        )

        if (inputView != null) {
            val etApiKey = inputView.findViewById<android.widget.EditText>(
                resources.getIdentifier("etApiKey", "id", packageName)
            )
            val etLabel = inputView.findViewById<android.widget.EditText>(
                resources.getIdentifier("etLabel", "id", packageName)
            )
            val spinnerProvider = inputView.findViewById<android.widget.Spinner>(
                resources.getIdentifier("spinnerProvider", "id", packageName)
            )

            if (etApiKey != null && etLabel != null && spinnerProvider != null) {
                val adapter = android.widget.ArrayAdapter(
                    this,
                    android.R.layout.simple_spinner_item,
                    providerNames
                )
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                spinnerProvider.adapter = adapter

                builder.setView(inputView)
            }
        }

        builder.setPositiveButton(R.string.save) { dialog, _ ->
            val etApiKey = inputView?.findViewById<android.widget.EditText>(
                resources.getIdentifier("etApiKey", "id", packageName)
            )
            val etLabel = inputView?.findViewById<android.widget.EditText>(
                resources.getIdentifier("etLabel", "id", packageName)
            )
            val spinnerProvider = inputView?.findViewById<android.widget.Spinner>(
                resources.getIdentifier("spinnerProvider", "id", packageName)
            )

            val apiKey = etApiKey?.text?.toString()?.trim() ?: ""
            val label = etLabel?.text?.toString()?.trim() ?: ""
            val providerIndex = spinnerProvider?.selectedItemPosition ?: 0
            val provider = Providers.ALL.getOrNull(providerIndex) ?: Providers.DEEPSEEK

            if (apiKey.isNotEmpty()) {
                viewModel.addKey(provider.id, apiKey, label)
                Toast.makeText(this, R.string.api_key_saved, Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, R.string.enter_api_key, Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.cancel()
        }

        builder.show()
    }

    private fun showDeleteConfirmDialog(id: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.confirm_delete_title)
            .setMessage(R.string.confirm_delete_message)
            .setPositiveButton(R.string.delete) { dialog, _ ->
                viewModel.deleteKey(id)
                Toast.makeText(this, R.string.deleted, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.cancel()
            }
            .show()
    }
}
