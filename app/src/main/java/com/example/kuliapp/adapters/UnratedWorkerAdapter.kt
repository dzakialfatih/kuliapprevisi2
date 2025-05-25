package com.example.kuliapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.kuliapp.databinding.ItemWorkerUnratedBinding
import com.example.kuliapp.models.Worker

class UnratedWorkerAdapter(
    private var workers: MutableList<Worker>,
    private val onItemClick: (Worker) -> Unit
) : RecyclerView.Adapter<UnratedWorkerAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemWorkerUnratedBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val worker = workers[position]
        holder.bind(worker)
    }

    override fun getItemCount(): Int = workers.size

    fun updateData(newWorkers: List<Worker>) {
        workers.clear()
        workers.addAll(newWorkers)
        notifyDataSetChanged()
    }

    fun getItems(): List<Worker> = workers.toList()

    inner class ViewHolder(private val binding: ItemWorkerUnratedBinding) :
        RecyclerView.ViewHolder(binding.root) {

        init {
            binding.btnRate.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(workers[position])
                }
            }
        }

        fun bind(worker: Worker) {
            binding.tvWorkerName.text = worker.name
            binding.tvJobDescription.text = worker.jobDescription
            binding.tvJobDate.text = worker.jobDate

            // In a real app, load worker photo using Glide or Picasso
            // if (worker.photo.isNotEmpty()) {
            //     Glide.with(binding.root.context)
            //         .load(worker.photo)
            //         .placeholder(R.drawable.ic_person)
            //         .into(binding.ivWorkerPhoto)
            // }
        }
    }
}