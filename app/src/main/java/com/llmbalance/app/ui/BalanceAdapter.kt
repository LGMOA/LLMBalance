package com.llmbalance.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.llmbalance.app.R
import com.llmbalance.app.databinding.ItemProviderBinding
import com.llmbalance.app.data.Providers
import com.llmbalance.app.data.UsageInfo

class BalanceAdapter(
    private val onDelete: (String) -> Unit
) : ListAdapter<KeyWithBalance, BalanceAdapter.ViewHolder>(DiffCallback) {

    private val expandedItemIds = mutableSetOf<String>()

    class ViewHolder(val binding: ItemProviderBinding) :
        RecyclerView.ViewHolder(binding.root)

    private fun bindViewHolder(
        binding: ItemProviderBinding,
        item: KeyWithBalance,
        onDelete: (String) -> Unit,
        isExpanded: Boolean,
        onToggleExpand: (String) -> Unit
    ) {
        val provider = Providers.getById(item.savedKey.providerId)
        binding.tvProviderName.text = provider?.name ?: item.savedKey.providerId
        binding.tvLabel.text = item.savedKey.label.ifEmpty { item.savedKey.apiKey.take(8) + "..." }

        if (item.isQuerying) {
            binding.tvBalance.text = binding.root.context.getString(R.string.querying)
            binding.tvStatus.text = ""
        } else if (item.balanceResult != null) {
            val result = item.balanceResult
            if (result.success && result.balanceResult != null) {
                val b = result.balanceResult
                binding.tvBalance.text = "${b.totalBalance} ${b.currency}".trim()
                binding.tvStatus.text = if (b.isAvailable) "✅ ${binding.root.context.getString(R.string.query_success)}" else "⚠️ ${binding.root.context.getString(R.string.query_failed)}"
            } else {
                binding.tvBalance.text = binding.root.context.getString(R.string.query_failed)
                binding.tvStatus.text = "❌ ${result.errorMessage?.take(40) ?: ""}"
            }
        } else {
            binding.tvBalance.text = binding.root.context.getString(R.string.querying)
            binding.tvStatus.text = ""
        }

        binding.btnDelete.setOnClickListener { onDelete(item.savedKey.id) }

        // Show detail info if balance has topped_up/granted breakdown
        if (item.balanceResult?.success == true && item.balanceResult?.balanceResult != null) {
            val b = item.balanceResult.balanceResult
            val detailParts = mutableListOf<String>()
            if (b.toppedUpBalance.isNotEmpty() && b.toppedUpBalance != "0") {
                detailParts.add("已充值: ${b.toppedUpBalance}")
            }
            if (b.grantedBalance.isNotEmpty() && b.grantedBalance != "0") {
                detailParts.add("赠送: ${b.grantedBalance}")
            }
            if (detailParts.isNotEmpty()) {
                binding.tvDetail.text = detailParts.joinToString(" | ")
                binding.tvDetail.visibility = View.VISIBLE
            } else {
                binding.tvDetail.visibility = View.GONE
            }
        } else {
            binding.tvDetail.visibility = View.GONE
        }

        // Bind usage data
        bindUsageData(binding, item, isExpanded, onToggleExpand)

        // Hide individual query button (auto-queries every 5s)
        binding.btnQuery.visibility = View.GONE
    }

    private fun bindUsageData(
        binding: ItemProviderBinding,
        item: KeyWithBalance,
        isExpanded: Boolean,
        onToggleExpand: (String) -> Unit
    ) {
        val usageInfo = item.usageResult?.usageInfo
            ?: item.balanceResult?.usageInfo

        if (usageInfo == null ||
            (usageInfo.totalTokens == 0L && usageInfo.totalCalls == 0L && usageInfo.availableModels.isEmpty())) {
            binding.tvUsageSummary.visibility = View.GONE
            binding.llUsageDetail.visibility = View.GONE
            binding.btnToggleUsage.visibility = View.GONE
            return
        }

        val ctx = binding.root.context
        val hasUsage = usageInfo.totalTokens > 0 || usageInfo.totalCalls > 0

        // Build summary line
        val summaryParts = mutableListOf<String>()
        if (hasUsage) {
            summaryParts.add(ctx.getString(R.string.total_tokens, formatNumber(usageInfo.totalTokens)))
            summaryParts.add(ctx.getString(R.string.total_calls, usageInfo.totalCalls))
        }
        if (usageInfo.availableModels.isNotEmpty()) {
            summaryParts.add(ctx.getString(R.string.models_available, usageInfo.availableModels.size))
        }
        binding.tvUsageSummary.text = summaryParts.joinToString(" | ")
        binding.tvUsageSummary.visibility = View.VISIBLE

        // Toggle button
        val hasDetail = usageInfo.modelUsage.isNotEmpty() || usageInfo.availableModels.isNotEmpty()
        if (hasDetail) {
            binding.btnToggleUsage.text = if (isExpanded) {
                ctx.getString(R.string.hide_model_details)
            } else {
                ctx.getString(R.string.view_model_details)
            }
            binding.btnToggleUsage.visibility = View.VISIBLE
            binding.btnToggleUsage.setOnClickListener { onToggleExpand(item.savedKey.id) }
        } else {
            binding.btnToggleUsage.visibility = View.GONE
        }

        // Detail container
        if (isExpanded && hasDetail) {
            binding.llUsageDetail.removeAllViews()
            populateUsageDetail(binding.llUsageDetail, usageInfo)
            binding.llUsageDetail.visibility = View.VISIBLE
        } else {
            binding.llUsageDetail.visibility = View.GONE
        }
    }

    private fun populateUsageDetail(container: ViewGroup, usageInfo: UsageInfo) {
        val ctx = container.context

        // Show per-model usage if available
        if (usageInfo.modelUsage.isNotEmpty()) {
            val showModels = usageInfo.modelUsage.take(10)
            for (model in showModels) {
                val text = ctx.getString(
                    R.string.usage_model_item,
                    model.modelName,
                    formatNumber(model.totalTokens),
                    model.callCount
                )
                val tv = TextView(ctx).apply {
                    this.text = text
                    textSize = 12f
                    setTextColor(ctx.getColor(com.llmbalance.app.R.color.on_surface_variant))
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 2 }
                }
                container.addView(tv)
            }
            if (usageInfo.modelUsage.size > 10) {
                val tv = TextView(ctx).apply {
                    text = "... and ${usageInfo.modelUsage.size - 10} more"
                    textSize = 11f
                    setTextColor(ctx.getColor(com.llmbalance.app.R.color.on_surface_variant))
                }
                container.addView(tv)
            }
        }

        // Show available models if only model list (no usage data)
        if (usageInfo.modelUsage.isEmpty() && usageInfo.availableModels.isNotEmpty()) {
            val showModels = usageInfo.availableModels.take(10)
            for (modelName in showModels) {
                val tv = TextView(ctx).apply {
                    text = "• $modelName"
                    textSize = 12f
                    setTextColor(ctx.getColor(com.llmbalance.app.R.color.on_surface_variant))
                    layoutParams = ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 2 }
                }
                container.addView(tv)
            }
            if (usageInfo.availableModels.size > 10) {
                val tv = TextView(ctx).apply {
                    text = "... and ${usageInfo.availableModels.size - 10} more"
                    textSize = 11f
                    setTextColor(ctx.getColor(com.llmbalance.app.R.color.on_surface_variant))
                }
                container.addView(tv)
            }
        }
    }

    private fun formatNumber(num: Long): String {
        return when {
            num >= 1_000_000 -> "${num / 1_000_000}M"
            num >= 1_000 -> "${num / 1_000}K"
            else -> num.toString()
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProviderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        val isExpanded = expandedItemIds.contains(item.savedKey.id)
        bindViewHolder(
            binding = holder.binding,
            item = item,
            onDelete = onDelete,
            isExpanded = isExpanded,
            onToggleExpand = { id ->
                if (expandedItemIds.contains(id)) {
                    expandedItemIds.remove(id)
                } else {
                    expandedItemIds.add(id)
                }
                notifyItemChanged(position)
            }
        )
    }

    object DiffCallback : DiffUtil.ItemCallback<KeyWithBalance>() {
        override fun areItemsTheSame(oldItem: KeyWithBalance, newItem: KeyWithBalance): Boolean {
            return oldItem.savedKey.id == newItem.savedKey.id
        }

        override fun areContentsTheSame(oldItem: KeyWithBalance, newItem: KeyWithBalance): Boolean {
            return oldItem == newItem
        }
    }
}
