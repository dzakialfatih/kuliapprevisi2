package com.example.kuliapp.ui.worker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kuliapp.adapters.ActiveOrdersAdapter
import com.example.kuliapp.databinding.ActivityActiveOrdersBinding
import com.example.kuliapp.models.Order

class ActiveOrdersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityActiveOrdersBinding
    private lateinit var adapter: ActiveOrdersAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityActiveOrdersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadOrders()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener {
            finish()
        }

        adapter = ActiveOrdersAdapter(
            onContactClick = { phoneNumber ->
                openWhatsApp(phoneNumber)
            },
            onAcceptClick = { order ->
                acceptOrder(order)
            },
            onRejectClick = { order ->
                rejectOrder(order)
            }
        )

        binding.rvActiveOrders.layoutManager = LinearLayoutManager(this)
        binding.rvActiveOrders.adapter = adapter
    }

    private fun loadOrders() {
        // Di masa depan, data ini akan diambil dari database/backend
        val orders = getMockOrders()

        if (orders.isEmpty()) {
            binding.tvEmptyOrders.visibility = View.VISIBLE
            binding.rvActiveOrders.visibility = View.GONE
        } else {
            binding.tvEmptyOrders.visibility = View.GONE
            binding.rvActiveOrders.visibility = View.VISIBLE
            adapter.submitList(orders)
        }
    }

    private fun getMockOrders(): List<Order> {
        return listOf(
            Order(
                id = "1",
                customerName = "Budi Santoso",
                customerPhone = "081234567890",
                address = "Jl. Raya No. 123, Jakarta",
                jobType = "Renovasi Kamar Mandi",
                date = "16/05/2025",
                status = "PENDING"
            ),
            Order(
                id = "2",
                customerName = "Siti Rahayu",
                customerPhone = "087654321098",
                address = "Jl. Melati No. 45, Jakarta",
                jobType = "Pengecatan Rumah",
                date = "18/05/2025",
                status = "PENDING"
            )
        )
    }

    private fun openWhatsApp(phoneNumber: String) {
        try {
            val formattedNumber = if (phoneNumber.startsWith("0")) {
                "+62${phoneNumber.substring(1)}"
            } else {
                phoneNumber
            }

            val message = "Halo, saya pekerja dari aplikasi Jasa Kuli. Ada yang bisa saya bantu?"
            val url = "https://api.whatsapp.com/send?phone=$formattedNumber&text=${Uri.encode(message)}"
            val intent = Intent(Intent.ACTION_VIEW)
            intent.data = Uri.parse(url)
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Tidak dapat membuka WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    private fun acceptOrder(order: Order) {
        // Di masa depan, kirim data ke backend bahwa pesanan diterima
        Toast.makeText(this, "Pesanan dari ${order.customerName} diterima", Toast.LENGTH_SHORT).show()

        // Simulasi update status di UI
        val updatedOrders = getMockOrders().toMutableList()
        val index = updatedOrders.indexOfFirst { it.id == order.id }
        if (index != -1) {
            val updatedOrder = updatedOrders[index].copy(status = "ACCEPTED")
            updatedOrders[index] = updatedOrder
            adapter.submitList(updatedOrders)
        }
    }

    private fun rejectOrder(order: Order) {
        // Di masa depan, kirim data ke backend bahwa pesanan ditolak
        Toast.makeText(this, "Pesanan dari ${order.customerName} ditolak", Toast.LENGTH_SHORT).show()

        // Simulasi hapus dari UI
        val updatedOrders = getMockOrders().toMutableList()
        updatedOrders.removeIf { it.id == order.id }
        adapter.submitList(updatedOrders)

        if (updatedOrders.isEmpty()) {
            binding.tvEmptyOrders.visibility = View.VISIBLE
            binding.rvActiveOrders.visibility = View.GONE
        }
    }
}