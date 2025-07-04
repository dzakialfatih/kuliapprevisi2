package com.example.kuliapp.adapters

import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.kuliapp.databinding.ItemWorkerBinding
import com.example.kuliapp.models.Worker

class WorkerAdapter(
    private var workers: MutableList<Worker>,
    private val onHireClick: (Worker) -> Unit
) : RecyclerView.Adapter<WorkerAdapter.WorkerViewHolder>() {

    companion object {
        private const val TAG = "WorkerAdapter"
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkerViewHolder {
        val binding = ItemWorkerBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WorkerViewHolder(binding)
    }

    override fun onBindViewHolder(holder: WorkerViewHolder, position: Int) {
        val worker = workers[position]
        holder.bind(worker)
    }

    override fun getItemCount(): Int = workers.size

    fun updateData(newWorkers: List<Worker>) {
        workers.clear()
        workers.addAll(newWorkers)
        notifyDataSetChanged()
    }

    inner class WorkerViewHolder(private val binding: ItemWorkerBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(worker: Worker) {
            binding.apply {
                // Set basic info
                tvWorkerName.text = worker.name
                tvLocation.text = worker.location
                tvExperience.text = worker.experience

                // ===== RATING DAN RATING COUNT - SAMA SEPERTI WORKERDASHBOARD =====
                // Konversi rating ke float untuk RatingBar
                val ratingFloat = worker.rating.toFloat()
                ratingBar.rating = ratingFloat

                // Nilai rating (misal: 4.2)
                tvRatingScore.text = String.format("%.1f", ratingFloat)

                // Jumlah ulasan (misal: (10 ulasan))
                val reviewCount = worker.ratingCount
                tvRatingCount.text = "($reviewCount ulasan)"

                // ===== SET HARGA =====
                tvPrice.text = worker.getFormattedPrice()

                // Debug log untuk memastikan semua data
                Log.d(TAG, "=== Binding worker data ===")
                Log.d(TAG, "Name: ${worker.name}")
                Log.d(TAG, "Rating (Double): ${worker.rating}")
                Log.d(TAG, "Rating (Float): $ratingFloat")
                Log.d(TAG, "Rating Count: ${worker.ratingCount}")
                Log.d(TAG, "Rating Display: ${String.format("%.1f", ratingFloat)}")
                Log.d(TAG, "Review Text: ($reviewCount ulasan)")
                Log.d(TAG, "Price: ${worker.price}")
                Log.d(TAG, "Formatted Price: ${worker.getFormattedPrice()}")
                Log.d(TAG, "Location: ${worker.location}")
                Log.d(TAG, "Experience: ${worker.experience}")
                Log.d(TAG, "=============================")

                // Load worker image if available
                // In a real app, you'd use Glide/Picasso to load images
                // if (worker.photo.isNotEmpty()) {
                //     Glide.with(itemView.context).load(worker.photo).into(ivWorkerPhoto)
                // }

                btnHire.setOnClickListener {
                    onHireClick(worker)
                }
            }
        }
    }
}