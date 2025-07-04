package com.example.kuliapp.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.kuliapp.R
import com.example.kuliapp.databinding.ItemRecentJobBinding
import com.example.kuliapp.models.Job

class RecentJobAdapter(
    private var jobs: MutableList<Job>
) : RecyclerView.Adapter<RecentJobAdapter.RecentJobViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentJobViewHolder {
        val binding = ItemRecentJobBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return RecentJobViewHolder(binding)
    }

    override fun onBindViewHolder(holder: RecentJobViewHolder, position: Int) {
        val job = jobs[position]
        holder.bind(job)
    }

    override fun getItemCount(): Int = jobs.size

    fun updateData(newJobs: List<Job>) {
        jobs.clear()
        jobs.addAll(newJobs)
        notifyDataSetChanged()
    }

    inner class RecentJobViewHolder(private val binding: ItemRecentJobBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(job: Job) {
            binding.apply {
                tvWorkerName.text = job.workerName
                tvJobDate.text = job.jobDate
                tvJobDescription.text = job.description
                ratingBar.rating = job.rating.toFloat()

                // Set warna bintang menjadi kuning
                val yellow = ContextCompat.getColorStateList(itemView.context, R.color.star_rating)
                ratingBar.progressTintList = yellow
                ratingBar.secondaryProgressTintList = yellow

                // Tambahan untuk memastikan bintang terlihat dengan baik
                ratingBar.indeterminateTintList = yellow


                // Set status based on job status
                tvStatus.text = if (job.status == "completed") {
                    itemView.context.getString(R.string.completed)
                } else {
                    itemView.context.getString(R.string.ongoing)
                }

                // Change status background based on status
                tvStatus.setBackgroundResource(
                    if (job.status == "completed") {
                        R.drawable.bg_status_completed
                    } else {
                        R.drawable.bg_status_ongoing
                    }
                )

                // Load worker image if available
                // In a real app, you'd use Glide/Picasso to load images
                // if (job.workerPhoto.isNotEmpty()) {
                //     Glide.with(itemView.context).load(job.workerPhoto).into(ivWorkerPhoto)
                // }
            }
        }
    }
}