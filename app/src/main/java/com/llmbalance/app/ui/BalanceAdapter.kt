package com.llmbalance.app.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.llmbalance.app.databinding.ItemProviderBinding
import com.llmbalance.app.data.Providers

class BalanceAdapter(
    private val onDelete: (String) -> Unit
) : ListAdapter<KeyWithBalance, BalanceAdapter.ViewHolder>(DiffCallback) {

    class ViewHolder(private val binding: ItemProviderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(
            item: KeyWithBalance,
            onDelete: (String) -> Unit
        ) {
            val provider = Providers.getById(item.savedKey.providerId)
            binding.tvProviderName.text = provider?.name ?: item.savedKey.providerId
            binding.tvLabel.text = item.savedKey.label.ifEmpty { item.savedKey.apiKey.take(8) + "..." }

            if (item.isQuerying) {
                binding.tvBalance.text = "查询中..."
                binding.tvStatus.text = ""
            } else if (item.balanceResult != null) {
                val result = item.balanceResult
                if (result.success && result.balanceResult != null) {
                    val b = result.balanceResult
                    binding.tvBalance.text = "${b.totalBalance} ${b.currency}".trim()
                    binding.tvStatus.text = if (b.isAvailable) "✅ 可用" else "⚠️ 异常"
                } else {
                    binding.tvBalance.text = "查询失败"
                    binding.tvStatus.text = "❌ ${result.errorMessage?.take(40) ?: ""}"
                }
            } else {
                binding.tvBalance.text = "等待查询..."
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
                    binding.tvDetail.visibility = android.view.View.VISIBLE
                } else {
                    binding.tvDetail.visibility = android.view.View.GONE
                }
            } else {
                binding.tvDetail.visibility = android.view.View.GONE
            }

            // Hide individual query button (auto-queries every 5s)
            binding.btnQuery.visibility = android.view.View.GONE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemProviderBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onDelete)
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
