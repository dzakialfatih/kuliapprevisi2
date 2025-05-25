package com.example.kuliapp.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.kuliapp.databinding.ItemActiveOrderBinding
import com.example.kuliapp.models.Order

class ActiveOrdersAdapter(
    private val onContactClick: (String) -> Unit,
    private val onAcceptClick: (Order) -> Unit,
    private val onRejectClick: (Order) -> Unit
) : ListAdapter<Order, ActiveOrdersAdapter.OrderViewHolder>(OrderDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemActiveOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class OrderViewHolder(private val binding: ItemActiveOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: Order) {
            binding.tvCustomerName.text = order.customerName
            binding.tvAddress.text = order.address
            binding.tvJobType.text = order.jobType
            binding.tvDate.text = order.date

            // Tergantung status, tampilkan atau sembunyikan tombol
            if (order.status == "ACCEPTED") {
                binding.btnAccept.visibility = View.GONE
                binding.btnReject.visibility = View.GONE
            } else {
                binding.btnAccept.visibility = View.VISIBLE
                binding.btnReject.visibility = View.VISIBLE
            }

            binding.btnContact.setOnClickListener {
                onContactClick(order.customerPhone)
            }

            binding.btnAccept.setOnClickListener {
                onAcceptClick(order)
            }

            binding.btnReject.setOnClickListener {
                onRejectClick(order)
            }
        }
    }

    class OrderDiffCallback : DiffUtil.ItemCallback<Order>() {
        override fun areItemsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Order, newItem: Order): Boolean {
            return oldItem == newItem
        }
    }
}