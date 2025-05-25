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

                // Set rating
                ratingBar.rating = worker.rating.toFloat()
                tvRatingScore.text = worker.getFormattedRating()  // INI YANG HILANG!
                tvRatingCount.text = "(${worker.ratingCount} ulasan)"

                // SET HARGA - INI YANG PALING PENTING DAN HILANG!
                tvPrice.text = worker.getFormattedPrice()

                // Debug log untuk memastikan data price
                Log.d(TAG, "Binding worker: ${worker.name}")
                Log.d(TAG, "Price value: ${worker.price}")
                Log.d(TAG, "Formatted price: ${worker.getFormattedPrice()}")

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