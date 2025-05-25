package com.example.kuliapp.ui.customer

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.example.kuliapp.R
import com.example.kuliapp.adapters.RecentJobAdapter
import com.example.kuliapp.adapters.UnratedWorkerAdapter
import com.example.kuliapp.databinding.ActivityCustomerDashboardBinding
import com.example.kuliapp.databinding.DialogRatingWorkerBinding
import com.example.kuliapp.models.Job
import com.example.kuliapp.models.Rating
import com.example.kuliapp.models.Worker
import com.example.kuliapp.ui.profile.ProfileActivity
import com.example.kuliapp.ui.worker.WorkerListActivity
import com.example.kuliapp.utils.JobStatusManager
import com.example.kuliapp.utils.PreferenceManager
import com.google.firebase.BuildConfig
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class CustomerDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCustomerDashboardBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var unratedWorkerAdapter: UnratedWorkerAdapter
    private lateinit var recentJobAdapter: RecentJobAdapter

    // Flag untuk menggunakan dummy data atau tidak
    private val useDummyData = false // Set ke false untuk menggunakan Firebase
    private var recentJobsListener: ListenerRegistration? = null

    companion object {
        private const val TAG = "CustomerDashboard"
        private const val COLLECTION_WORKERS = "workers"
        private const val COLLECTION_RATINGS = "ratings"
        private const val COLLECTION_JOBS = "jobs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomerDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        preferenceManager = PreferenceManager(this)

        setupUI()
        setupListeners()
        setupSwipeRefresh()
        loadUnratedWorkers()
        loadRecentJobs()
        setupJobListener()
        setupRecentJobsListener()
    }

    private fun setupSwipeRefresh() {
        // Setup SwipeRefreshLayout
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }

        // Customize refresh indicator colors
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.accent,
            R.color.primary_dark
        )
    }

    private fun refreshData() {
        Log.d(TAG, "Refreshing dashboard data...")

        // Show refreshing indicator
        binding.swipeRefreshLayout.isRefreshing = true

        // Counter untuk tracking loading completion
        var loadingCount = 0
        val totalLoaders = 2 // unrated workers + recent jobs

        val onLoadComplete = {
            loadingCount++
            if (loadingCount >= totalLoaders) {
                // Semua data sudah selesai loading
                binding.swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this, "Data berhasil diperbarui", Toast.LENGTH_SHORT).show()
            }
        }

        // Load data with completion callback
        loadUnratedWorkers(onLoadComplete)
        loadRecentJobs(onLoadComplete)
    }

    private fun setupJobListener() {
        val currentUserId = auth.currentUser?.uid ?: return

        firestore.collection(COLLECTION_JOBS)
            .whereEqualTo("customerId", currentUserId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed.", error)
                    return@addSnapshotListener
                }

                // Refresh data ketika ada perubahan (hanya jika tidak sedang manual refresh)
                if (!binding.swipeRefreshLayout.isRefreshing) {
                    loadUnratedWorkers()
                    loadRecentJobs()
                }
            }
    }

    private fun setupUI() {
        // Set user's name in welcome message
        val userName = auth.currentUser?.displayName
            ?: preferenceManager.getString("user_name")
            ?: auth.currentUser?.email?.substringBefore("@")?.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }
            ?: "Pengguna"

        binding.tvWelcome.text = getString(R.string.welcome_user, userName)

        // Setup RecyclerViews
        binding.rvUnratedWorkers.layoutManager = LinearLayoutManager(this)
        unratedWorkerAdapter = UnratedWorkerAdapter(mutableListOf()) { worker ->
            // Handle rating worker
            showRatingDialog(worker)
        }
        binding.rvUnratedWorkers.adapter = unratedWorkerAdapter

        binding.rvRecentJobs.layoutManager = LinearLayoutManager(this)
        recentJobAdapter = RecentJobAdapter(mutableListOf())
        binding.rvRecentJobs.adapter = recentJobAdapter
    }

    private fun setupListeners() {
        // Find worker button - navigate to worker list
        binding.btnFindWorker.setOnClickListener {
            startActivity(Intent(this, WorkerListActivity::class.java))
        }

        // Profile button - navigate to profile
        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Add manual refresh button (optional)
        // Uncomment jika Anda ingin menambahkan tombol refresh manual
        /*
        binding.btnRefresh.setOnClickListener {
            refreshData()
        }
        */
    }

    // Overloaded method with completion callback
    private fun loadUnratedWorkers(onComplete: (() -> Unit)? = null) {
        if (useDummyData) {
            loadUnratedWorkersFromDummy(onComplete)
        } else {
            loadUnratedWorkersFromFirebase(onComplete)
        }
    }

    // Overloaded method with completion callback
    private fun loadRecentJobs(onComplete: (() -> Unit)? = null) {
        if (useDummyData) {
            loadRecentJobsFromDummy(onComplete)
        } else {
            loadRecentJobsFromFirebase(onComplete)
        }
    }

    private fun loadUnratedWorkersFromDummy(onComplete: (() -> Unit)? = null) {
        showLoadingUnrated(true)

        // Simulasi delay loading
        binding.root.postDelayed({
            val dummyWorkers = getDummyUnratedWorkers()
            showLoadingUnrated(false)
            updateUnratedWorkersList(dummyWorkers)
            onComplete?.invoke()
        }, 1000) // Delay 1 detik untuk simulasi loading
    }

    private val workerListeners = mutableListOf<ListenerRegistration>()

    private fun loadUnratedWorkersFromFirebase(onComplete: (() -> Unit)? = null) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            showEmptyUnratedWorkers()
            onComplete?.invoke()
            return
        }

        Log.d(TAG, "Loading unrated workers for user: $currentUserId")
        showLoadingUnrated(true)

        // Cleanup existing listeners
        cleanupWorkerListeners()

        // Gunakan JobStatusManager untuk mendapatkan unrated completed jobs
        JobStatusManager.getUnratedCompletedJobs(firestore, currentUserId) { jobs ->
            Log.d(TAG, "Found ${jobs.size} unrated completed jobs")

            showLoadingUnrated(false)
            val unratedWorkers = mutableMapOf<String, Worker>() // Gunakan Map untuk easy update
            var completedQueries = 0
            val totalQueries = jobs.size

            if (totalQueries == 0) {
                Log.d(TAG, "No unrated jobs found")
                showEmptyUnratedWorkers()
                onComplete?.invoke()
                return@getUnratedCompletedJobs
            }

            // Untuk setiap job, setup realtime listener untuk worker-nya berdasarkan workerName
            for (job in jobs) {
                Log.d(TAG, "Setting up listener for job: ${job.id}, workerName: ${job.workerName}")

                if (job.workerName.isBlank()) {
                    Log.w(TAG, "Invalid workerName for job: ${job.id}")
                    completedQueries++
                    if (completedQueries == totalQueries) {
                        showLoadingUnrated(false)
                        updateUnratedWorkersList(unratedWorkers.values.toList())
                        onComplete?.invoke()
                    }
                    continue
                }

                // Setup realtime listener untuk setiap worker berdasarkan name field
                val listener = firestore.collection(COLLECTION_WORKERS)
                    .whereEqualTo("name", job.workerName)
                    .addSnapshotListener { querySnapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Error listening to worker with name ${job.workerName}", error)
                            return@addSnapshotListener
                        }

                        // Hapus worker lama dengan nama yang sama dari map
                        unratedWorkers.entries.removeAll { it.value.name == job.workerName }

                        querySnapshot?.documents?.forEach { workerDocument ->
                            if (workerDocument.exists()) {
                                val worker = workerDocument.toObject(Worker::class.java)
                                worker?.let {
                                    Log.d(TAG, "Worker data updated: ${it.name}")
                                    val workerWithJobInfo = it.copy(
                                        jobDate = job.date,
                                        jobDescription = job.description
                                    )

                                    // Update worker di map menggunakan documentId sebagai key
                                    unratedWorkers[workerDocument.id] = workerWithJobInfo
                                }
                            }
                        }

                        if (querySnapshot?.isEmpty == true) {
                            Log.w(TAG, "No worker found with name: ${job.workerName}")
                        }

                        // Update UI dengan data terbaru
                        updateUnratedWorkersList(unratedWorkers.values.toList())
                    }

                // Simpan listener untuk cleanup nanti
                workerListeners.add(listener)

                completedQueries++
                if (completedQueries == totalQueries) {
                    Log.d(TAG, "All listeners set up")
                    onComplete?.invoke()
                }
            }
        }
    }

    // Function untuk cleanup listeners
    private fun cleanupWorkerListeners() {
        Log.d(TAG, "Cleaning up ${workerListeners.size} worker listeners")
        workerListeners.forEach { it.remove() }
        workerListeners.clear()
    }

    // Panggil ini di onDestroy() atau ketika tidak perlu lagi
    override fun onDestroy() {
        super.onDestroy()
        cleanupWorkerListeners()
        recentJobsListener?.remove()
    }

    // Alternative: Versi yang lebih efisien menggunakan single query untuk multiple worker names
    private fun loadUnratedWorkersFromFirebaseEfficient(onComplete: (() -> Unit)? = null) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            showEmptyUnratedWorkers()
            onComplete?.invoke()
            return
        }

        Log.d(TAG, "Loading unrated workers efficiently for user: $currentUserId")
        showLoadingUnrated(true)

        cleanupWorkerListeners()

        JobStatusManager.getUnratedCompletedJobs(firestore, currentUserId) { jobs ->
            Log.d(TAG, "Found ${jobs.size} unrated completed jobs")

            if (jobs.isEmpty()) {
                showLoadingUnrated(false)
                showEmptyUnratedWorkers()
                onComplete?.invoke()
                return@getUnratedCompletedJobs
            }

            // Ambil unique worker names dari jobs
            val workerNames = jobs.map { it.workerName }.filter { it.isNotBlank() }.distinct()

            if (workerNames.isEmpty()) {
                showLoadingUnrated(false)
                showEmptyUnratedWorkers()
                onComplete?.invoke()
                return@getUnratedCompletedJobs
            }

            // Firestore whereIn memiliki limit 10 items, jadi kita perlu split jika lebih dari 10
            val chunkedWorkerNames = workerNames.chunked(10)
            val allListeners = mutableListOf<ListenerRegistration>()

            chunkedWorkerNames.forEach { chunk ->
                val listener = firestore.collection(COLLECTION_WORKERS)
                    .whereIn("name", chunk)
                    .addSnapshotListener { querySnapshot, error ->
                        if (error != null) {
                            Log.e(TAG, "Error listening to workers", error)
                            showLoadingUnrated(false)
                            return@addSnapshotListener
                        }

                        showLoadingUnrated(false)
                        val unratedWorkers = mutableListOf<Worker>()

                        querySnapshot?.documents?.forEach { workerDocument ->
                            val worker = workerDocument.toObject(Worker::class.java)
                            worker?.let { w ->
                                // Find corresponding job info berdasarkan worker name
                                val job = jobs.find { it.workerName == w.name }
                                job?.let { j ->
                                    val workerWithJobInfo = w.copy(
                                        jobDate = j.date,
                                        jobDescription = j.description
                                    )
                                    unratedWorkers.add(workerWithJobInfo)
                                }
                            }
                        }

                        Log.d(TAG, "Updated unrated workers from chunk: ${unratedWorkers.size}")

                        // Jika menggunakan multiple chunks, Anda mungkin ingin mengumpulkan semua hasil
                        // sebelum update UI. Untuk sekarang, update langsung.
                        updateUnratedWorkersList(unratedWorkers)
                    }

                allListeners.add(listener)
            }

            // Tambahkan semua listeners untuk cleanup
            workerListeners.addAll(allListeners)
            onComplete?.invoke()
        }
    }

    // Alternative: Jika nama worker bisa berubah dan ingin lebih robust
    private fun loadUnratedWorkersWithNameSearch(onComplete: (() -> Unit)? = null) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            showEmptyUnratedWorkers()
            onComplete?.invoke()
            return
        }

        Log.d(TAG, "Loading unrated workers with name search for user: $currentUserId")
        showLoadingUnrated(true)

        cleanupWorkerListeners()

        JobStatusManager.getUnratedCompletedJobs(firestore, currentUserId) { jobs ->
            Log.d(TAG, "Found ${jobs.size} unrated completed jobs")

            if (jobs.isEmpty()) {
                showLoadingUnrated(false)
                showEmptyUnratedWorkers()
                onComplete?.invoke()
                return@getUnratedCompletedJobs
            }

            // Untuk case dimana nama worker mungkin tidak exact match,
            // bisa menggunakan full collection listener dengan filter client-side
            val listener = firestore.collection(COLLECTION_WORKERS)
                .addSnapshotListener { querySnapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error listening to all workers", error)
                        showLoadingUnrated(false)
                        return@addSnapshotListener
                    }

                    showLoadingUnrated(false)
                    val unratedWorkers = mutableListOf<Worker>()
                    val jobWorkerNames = jobs.map { it.workerName.lowercase().trim() }

                    querySnapshot?.documents?.forEach { workerDocument ->
                        val worker = workerDocument.toObject(Worker::class.java)
                        worker?.let { w ->
                            // Check if this worker's name matches any job worker name
                            if (jobWorkerNames.contains(w.name.lowercase().trim())) {
                                val job = jobs.find {
                                    it.workerName.lowercase().trim() == w.name.lowercase().trim()
                                }
                                job?.let { j ->
                                    val workerWithJobInfo = w.copy(
                                        jobDate = j.date,
                                        jobDescription = j.description
                                    )
                                    unratedWorkers.add(workerWithJobInfo)
                                }
                            }
                        }
                    }

                    Log.d(TAG, "Updated unrated workers: ${unratedWorkers.size}")
                    updateUnratedWorkersList(unratedWorkers)
                }

            workerListeners.add(listener)
            onComplete?.invoke()
        }
    }

    private fun loadRecentJobsFromDummy(onComplete: (() -> Unit)? = null) {
        showLoadingRecent(true)

        // Simulasi delay loading
        binding.root.postDelayed({
            val dummyJobs = getDummyRecentJobs()
            showLoadingRecent(false)
            updateRecentJobsList(dummyJobs)
            onComplete?.invoke()
        }, 1200) // Delay 1.2 detik untuk simulasi loading
    }

    private fun loadRecentJobsFromFirebase(onComplete: (() -> Unit)? = null) {
        val currentUserId = auth.currentUser?.uid
        if (currentUserId == null) {
            showEmptyRecentJobs()
            onComplete?.invoke()
            return
        }

        showLoadingRecent(true)

        // PERBAIKAN: Query yang lebih sederhana tanpa whereNotEqualTo
        firestore.collection(COLLECTION_JOBS)
            .whereEqualTo("customerId", currentUserId)
            .whereEqualTo("status", "completed")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(20) // Ambil lebih banyak untuk filter client-side
            .get()
            .addOnSuccessListener { documents ->
                showLoadingRecent(false)
                val recentJobs = mutableListOf<Job>()

                for (document in documents) {
                    val job = document.toObject(Job::class.java).copy(id = document.id)
                    // Filter jobs yang sudah ada rating (rating > 0)
                    if (job.rating > 0f) {
                        recentJobs.add(job)
                    }
                }

                // Batasi hasil final ke 10 items
                val finalJobs = recentJobs.take(10)
                updateRecentJobsList(finalJobs)
                onComplete?.invoke()
            }
            .addOnFailureListener { exception ->
                showLoadingRecent(false)
                Log.e(TAG, "Error loading recent jobs", exception)
                showEmptyRecentJobs()
                onComplete?.invoke()
            }
    }


    private fun setupRecentJobsListener() {
        val currentUserId = auth.currentUser?.uid ?: return

        // Cleanup existing listener
        recentJobsListener?.remove()

        recentJobsListener = firestore.collection(COLLECTION_JOBS)
            .whereEqualTo("customerId", currentUserId)
            .whereEqualTo("status", "completed")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Recent jobs listener failed", error)
                    return@addSnapshotListener
                }

                val recentJobs = mutableListOf<Job>()
                snapshot?.documents?.forEach { document ->
                    val job = document.toObject(Job::class.java)?.copy(id = document.id)
                    job?.let {
                        // Filter jobs yang sudah di-rating
                        if (it.rating > 0f) {
                            recentJobs.add(it)
                        }
                    }
                }

                // Update UI dengan data terbaru
                val finalJobs = recentJobs.take(10)
                updateRecentJobsList(finalJobs)
            }
    }
    // Dummy data functions
    // Di CustomerDashboardActivity.kt - Enhanced Dummy Data

    private fun getDummyUnratedWorkers(): List<Worker> {
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
        return listOf(
            Worker(
                id = "worker_1",
                name = "Budi Santoso",
                photo = "",
                rating = 4.2, // Rating existing worker, tapi job ini belum di-rating
                ratingCount = 5,
                location = "Bekasi",
                experience = "Pemasangan keramik dan perbaikan atap",
                jobDate = dateFormat.format(Date()),
                jobDescription = "Perbaikan atap rumah yang bocor",
                price = 200000,
                phone = "6281234567890"
            ),
            Worker(
                id = "worker_2",
                name = "Agus Purnomo",
                photo = "",
                rating = 4.5,
                ratingCount = 8,
                location = "Jakarta Timur",
                experience = "Renovasi dan pengecatan",
                jobDate = dateFormat.format(Date(System.currentTimeMillis() - 86400000)), // yesterday
                jobDescription = "Pengecatan ulang ruang tamu",
                price = 175000,
                phone = "6281234567891"
            ),
            Worker(
                id = "worker_3",
                name = "Slamet Riyadi",
                photo = "",
                rating = 4.8,
                ratingCount = 12,
                location = "Bekasi Timur",
                experience = "Instalasi listrik dan perbaikan",
                jobDate = dateFormat.format(Date(System.currentTimeMillis() - 172800000)), // 2 days ago
                jobDescription = "Perbaikan instalasi listrik dapur",
                price = 220000,
                phone = "6281234567892"
            )
        )
    }

    private fun getDummyRecentJobs(): List<Job> {
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
        return listOf(
            Job(
                id = "job_1",
                workerId = "worker_4",
                workerName = "Ahmad Wijaya",
                workerPhoto = "",
                date = dateFormat.format(Date(System.currentTimeMillis() - 259200000)), // 3 days ago
                description = "Pengecatan tembok rumah",
                status = "completed",
                rating = 4.5f,
                price = 200000,
                location = "Jakarta Timur"
            ),
            Job(
                id = "job_2",
                workerId = "worker_5",
                workerName = "Dedi Cahyono",
                workerPhoto = "",
                date = dateFormat.format(Date(System.currentTimeMillis() - 345600000)), // 4 days ago
                description = "Perbaikan instalasi listrik",
                status = "completed",
                rating = 5.0f,
                price = 250000,
                location = "Bekasi Barat"
            ),
            Job(
                id = "job_3",
                workerId = "worker_6",
                workerName = "Eko Prasetyo",
                workerPhoto = "",
                date = dateFormat.format(Date(System.currentTimeMillis() - 432000000)), // 5 days ago
                description = "Pembuatan rak buku custom",
                status = "completed",
                rating = 4.0f,
                price = 180000,
                location = "Jakarta Selatan"
            )
        )
    }

    // TAMBAHKAN FUNGSI SIMULASI UNTUK TESTING FLOW
    private fun simulateJobCompletionForTesting() {
        // Fungsi ini bisa dipanggil untuk testing
        // Simulasi: worker dengan ID "worker_1" menyelesaikan pekerjaan
        val testJobId = "test_job_${System.currentTimeMillis()}"
        val currentUserId = auth.currentUser?.uid ?: return

        val completedJob = Job(
            id = testJobId,
            customerId = currentUserId,
            customerName = "Test Customer",
            workerId = "worker_1",
            workerName = "Budi Santoso",
            workerPhoto = "",
            date = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date()),
            description = "Test job yang sudah selesai",
            status = "completed",
            rating = 0.0f, // Belum di-rating
            price = 200000,
            location = "Jakarta",
            _createdAt = Timestamp.now(),
            _updatedAt = Timestamp.now()
        )

        if (!useDummyData) {
            firestore.collection(COLLECTION_JOBS)
                .document(testJobId)
                .set(completedJob)
                .addOnSuccessListener {
                    Log.d(TAG, "Test completed job created successfully")
                    Toast.makeText(this, "Test job berhasil dibuat", Toast.LENGTH_SHORT).show()
                    // Refresh data untuk melihat perubahan
                    refreshData()
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Error creating test job", exception)
                }
        }
    }

    // PANGGIL FUNGSI INI UNTUK TESTING (misalnya dari button tersembunyi)
    private fun createTestCompletedJob() {
        simulateJobCompletionForTesting()
    }

    private fun updateUnratedWorkersList(workers: List<Worker>) {
        if (workers.isEmpty()) {
            showEmptyUnratedWorkers()
        } else {
            binding.tvNoUnratedWorkers.visibility = View.GONE
            binding.rvUnratedWorkers.visibility = View.VISIBLE
            unratedWorkerAdapter.updateData(workers)
        }
    }

    private fun updateRecentJobsList(jobs: List<Job>) {
        if (jobs.isEmpty()) {
            showEmptyRecentJobs()
        } else {
            binding.tvNoRecentJobs.visibility = View.GONE
            binding.rvRecentJobs.visibility = View.VISIBLE
            recentJobAdapter.updateData(jobs)
        }
    }

    private fun showRatingDialog(worker: Worker) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogRatingWorkerBinding.inflate(layoutInflater)

        // Set star rating color
        val yellow = ContextCompat.getColorStateList(this, R.color.star_rating)
        dialogBinding.ratingBar.progressTintList = yellow
        dialogBinding.ratingBar.secondaryProgressTintList = yellow
        dialogBinding.ratingBar.indeterminateTintList = yellow

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.window?.setLayout(
            resources.displayMetrics.widthPixels * 90 / 100,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        // Set worker details in dialog
        dialogBinding.tvWorkerName.text = worker.name
        dialogBinding.tvJobDescription.text = worker.jobDescription
        dialogBinding.tvJobDate.text = worker.jobDate

        // Load worker photo if available
        if (worker.photo.isNotEmpty()) {
            // Load with image loading library
            // Glide.with(this).load(worker.photo).into(dialogBinding.ivWorkerPhoto)
        }

        // Set up dialog buttons
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSubmit.setOnClickListener {
            val rating = dialogBinding.ratingBar.rating
            val review = dialogBinding.etReview.text.toString().trim()

            if (rating <= 0) {
                Toast.makeText(this, getString(R.string.please_provide_rating), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (useDummyData) {
                // Untuk dummy data, hanya tampilkan toast dan refresh
                Toast.makeText(this, "Rating berhasil disimpan (dummy mode)", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                // Refresh data
                refreshData()
            } else {
                submitRating(worker, rating, review, dialog)
            }
        }

        dialog.show()
    }

    private fun submitRating(worker: Worker, rating: Float, review: String, dialog: Dialog) {
        val currentUserId = auth.currentUser?.uid
        val currentUserName = auth.currentUser?.displayName ?: "Pengguna"

        if (currentUserId == null) {
            Toast.makeText(this, "Error: User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Submitting rating: $rating for worker: ${worker.name} (${worker.id})")

        // Validasi rating
        if (rating < 1.0f || rating > 5.0f) {
            Toast.makeText(this, "Rating harus antara 1-5 bintang", Toast.LENGTH_SHORT).show()
            return
        }

        // Create rating object dengan ID yang unik
        val ratingId = "${currentUserId}_${worker.id}_${System.currentTimeMillis()}"
        val ratingObj = Rating(
            id = ratingId,
            workerId = worker.id,
            customerId = currentUserId,
            customerName = currentUserName,
            rating = rating,
            review = review,
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            createdAt = Timestamp.now()
        )

        // Show loading
        dialog.setCancelable(false)

        // Cek apakah user sudah pernah rating worker ini untuk job yang sama
        firestore.collection(COLLECTION_RATINGS)
            .whereEqualTo("workerId", worker.id)
            .whereEqualTo("customerId", currentUserId)
            .get()
            .addOnSuccessListener { existingRatings ->
                // Cek apakah ada rating untuk kombinasi job yang sama
                val hasExistingRating = existingRatings.documents.any { doc ->
                    val existingRating = doc.toObject(Rating::class.java)
                    // Bisa tambahkan logic lebih spesifik untuk mengecek job yang sama
                    existingRating != null
                }

                if (hasExistingRating && !useDummyData) {
                    dialog.dismiss()
                    Toast.makeText(this, "Anda sudah memberikan rating untuk pekerja ini", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                // Submit rating ke Firebase
                firestore.collection(COLLECTION_RATINGS)
                    .document(ratingId)
                    .set(ratingObj)
                    .addOnSuccessListener {
                        Log.d(TAG, "Rating saved successfully: $ratingId")

                        // Update job status dengan rating
                        updateJobRating(worker, rating) { jobUpdateSuccess ->
                            if (jobUpdateSuccess) {
                                Log.d(TAG, "Job rating updated, now updating worker rating")

                                // Update worker's overall rating
                                updateWorkerRating(worker.id) {
                                    dialog.dismiss()
                                    Toast.makeText(
                                        this,
                                        "Rating berhasil disimpan! Terima kasih atas feedback Anda.",
                                        Toast.LENGTH_SHORT
                                    ).show()

                                    Log.d(TAG, "Rating process completed successfully")
                                }
                            } else {
                                // Tetap update worker rating meskipun job update gagal
                                Log.w(TAG, "Job rating update failed, but continuing with worker rating update")
                                updateWorkerRating(worker.id) {
                                    dialog.dismiss()
                                    Toast.makeText(this, "Rating disimpan dengan beberapa masalah", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    .addOnFailureListener { exception ->
                        dialog.dismiss()
                        Log.e(TAG, "Error submitting rating", exception)
                        Toast.makeText(this, "Gagal mengirim rating: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { exception ->
                dialog.dismiss()
                Log.e(TAG, "Error checking existing ratings", exception)
                Toast.makeText(this, "Gagal mengecek rating yang ada", Toast.LENGTH_SHORT).show()
            }
    }

    private fun debugWorkerRatings(workerId: String) {
        Log.d(TAG, "=== DEBUG WORKER RATINGS ===")
        Log.d(TAG, "Worker ID: $workerId")

        // Get worker data
        firestore.collection(COLLECTION_WORKERS)
            .document(workerId)
            .get()
            .addOnSuccessListener { workerDoc ->
                val worker = workerDoc.toObject(Worker::class.java)
                Log.d(TAG, "Worker: ${worker?.name}")
                Log.d(TAG, "Current rating: ${worker?.rating}")
                Log.d(TAG, "Current rating count: ${worker?.ratingCount}")

                // Get all ratings for this worker
                firestore.collection(COLLECTION_RATINGS)
                    .whereEqualTo("workerId", workerId)
                    .orderBy("createdAt", Query.Direction.DESCENDING)
                    .get()
                    .addOnSuccessListener { ratingDocs ->
                        Log.d(TAG, "Found ${ratingDocs.size()} ratings:")

                        var totalRating = 0.0
                        var count = 0

                        ratingDocs.documents.forEachIndexed { index, doc ->
                            val rating = doc.toObject(Rating::class.java)
                            rating?.let {
                                Log.d(TAG, "${index + 1}. Rating: ${it.rating}, Customer: ${it.customerName}, Date: ${it.date}")
                                totalRating += it.rating
                                count++
                            }
                        }

                        val calculatedAverage = if (count > 0) totalRating / count else 0.0
                        Log.d(TAG, "Calculated average: $calculatedAverage (from $count ratings)")
                        Log.d(TAG, "=== END DEBUG ===")
                    }
            }
    }

    private fun addDebugFeatures() {
        // Tambahkan long click listener pada welcome text untuk akses debug
        binding.tvWelcome.setOnLongClickListener {
            if (BuildConfig.DEBUG) { // Hanya di debug build
                showDebugDialog()
            }
            true
        }
    }

    private fun showDebugDialog() {
        val options = arrayOf(
            "Recalculate All Worker Ratings",
            "Debug Specific Worker Rating",
            "Create Test Rating"
        )

        AlertDialog.Builder(this)
            .setTitle("Debug Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> recalculateAllWorkerRatings()
                    1 -> {
                        // Input dialog untuk worker ID
                        val input = EditText(this)
                        input.hint = "Enter Worker ID"
                        AlertDialog.Builder(this)
                            .setTitle("Debug Worker Rating")
                            .setView(input)
                            .setPositiveButton("Debug") { _, _ ->
                                val workerId = input.text.toString().trim()
                                if (workerId.isNotEmpty()) {
                                    debugWorkerRatings(workerId)
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    2 -> createTestRating()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun createTestRating() {
        // Fungsi untuk testing - buat rating dummy
        val testRating = Rating(
            id = "test_${System.currentTimeMillis()}",
            workerId = "worker_1", // Ganti dengan ID worker yang ada
            customerId = auth.currentUser?.uid ?: "test_customer",
            customerName = "Test Customer",
            rating = 4.5f,
            review = "Test rating untuk debugging",
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            createdAt = Timestamp.now()
        )

        firestore.collection(COLLECTION_RATINGS)
            .document(testRating.id)
            .set(testRating)
            .addOnSuccessListener {
                Log.d(TAG, "Test rating created")
                updateWorkerRating("worker_1") {
                    Toast.makeText(this, "Test rating berhasil dibuat", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to create test rating", e)
            }
    }

    private fun updateJobRating(worker: Worker, rating: Float, callback: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid ?: return

        // PERBAIKAN: Pencarian job yang lebih spesifik menggunakan workerName
        firestore.collection(COLLECTION_JOBS)
            .whereEqualTo("customerId", currentUserId)
            .whereEqualTo("workerName", worker.name) // Gunakan workerName bukan workerId
            .whereEqualTo("status", "completed")
            .whereEqualTo("description", worker.jobDescription)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // Cari job yang belum di-rating (rating = 0)
                    val unratedJob = documents.documents.find { doc ->
                        val job = doc.toObject(Job::class.java)
                        job?.rating == 0f || job?.rating == null
                    }

                    if (unratedJob != null) {
                        firestore.collection(COLLECTION_JOBS)
                            .document(unratedJob.id)
                            .update(
                                mapOf(
                                    "rating" to rating,
                                    "updatedAt" to Timestamp.now()
                                )
                            )
                            .addOnSuccessListener {
                                Log.d(TAG, "Job rating updated successfully")
                                callback(true)
                            }
                            .addOnFailureListener {
                                Log.e(TAG, "Failed to update job rating")
                                callback(false)
                            }
                    } else {
                        Log.w(TAG, "No unrated job found for worker: ${worker.name}")
                        callback(false)
                    }
                } else {
                    Log.w(TAG, "No jobs found for worker: ${worker.name}")
                    callback(false)
                }
            }
            .addOnFailureListener {
                Log.e(TAG, "Error finding job to update rating")
                callback(false)
            }
    }

    private fun updateWorkerRating(workerId: String, callback: () -> Unit) {
        Log.d(TAG, "Updating worker rating for: $workerId")

        // Hitung ulang rating berdasarkan semua rating yang ada
        firestore.collection(COLLECTION_RATINGS)
            .whereEqualTo("workerId", workerId)
            .get()
            .addOnSuccessListener { documents ->
                var totalRating = 0.0
                var count = 0
                val ratings = mutableListOf<Double>()

                for (document in documents) {
                    val rating = document.getDouble("rating") ?: 0.0
                    if (rating > 0) { // Hanya hitung rating yang valid
                        totalRating += rating
                        count++
                        ratings.add(rating)
                    }
                }

                val averageRating = if (count > 0) {
                    String.format("%.2f", totalRating / count).toDouble()
                } else {
                    0.0
                }

                Log.d(TAG, "Worker $workerId - Total ratings: $count, Average: $averageRating")
                Log.d(TAG, "Individual ratings: $ratings")

                // Update worker document dengan data yang akurat
                val updateData = mapOf(
                    "rating" to averageRating,
                    "ratingCount" to count,
                    "updatedAt" to Timestamp.now()
                )

                firestore.collection(COLLECTION_WORKERS)
                    .document(workerId)
                    .update(updateData)
                    .addOnSuccessListener {
                        Log.d(TAG, "Worker rating updated successfully: $averageRating ($count ratings)")
                        callback()
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Failed to update worker rating", exception)
                        callback()
                    }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to calculate worker rating", exception)
                callback()
            }
    }

    private fun recalculateAllWorkerRatings() {
        Log.d(TAG, "Recalculating all worker ratings...")

        firestore.collection(COLLECTION_WORKERS)
            .get()
            .addOnSuccessListener { workerDocuments ->
                var processedCount = 0
                val totalWorkers = workerDocuments.size()

                if (totalWorkers == 0) {
                    Log.d(TAG, "No workers found to recalculate")
                    return@addOnSuccessListener
                }

                for (workerDoc in workerDocuments) {
                    val workerId = workerDoc.id

                    // Hitung ulang rating untuk setiap worker
                    firestore.collection(COLLECTION_RATINGS)
                        .whereEqualTo("workerId", workerId)
                        .get()
                        .addOnSuccessListener { ratingDocuments ->
                            var totalRating = 0.0
                            var validRatingCount = 0

                            for (ratingDoc in ratingDocuments) {
                                val rating = ratingDoc.getDouble("rating") ?: 0.0
                                if (rating > 0) {
                                    totalRating += rating
                                    validRatingCount++
                                }
                            }

                            val averageRating = if (validRatingCount > 0) {
                                String.format("%.2f", totalRating / validRatingCount).toDouble()
                            } else {
                                0.0
                            }

                            // Update worker
                            workerDoc.reference.update(
                                mapOf(
                                    "rating" to averageRating,
                                    "ratingCount" to validRatingCount,
                                    "updatedAt" to Timestamp.now()
                                )
                            ).addOnCompleteListener {
                                processedCount++
                                Log.d(TAG, "Updated worker $workerId: $averageRating ($validRatingCount ratings) - Progress: $processedCount/$totalWorkers")

                                if (processedCount == totalWorkers) {
                                    Log.d(TAG, "All worker ratings recalculated successfully")
                                    Toast.makeText(this, "Semua rating worker berhasil diperbarui", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .addOnFailureListener { exception ->
                            processedCount++
                            Log.e(TAG, "Failed to recalculate rating for worker $workerId", exception)

                            if (processedCount == totalWorkers) {
                                Log.d(TAG, "Rating recalculation completed with some errors")
                            }
                        }
                }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Failed to get workers for recalculation", exception)
            }
    }

    private fun showEmptyUnratedWorkers() {
        binding.tvNoUnratedWorkers.visibility = View.VISIBLE
        binding.tvNoUnratedWorkers.visibility = View.VISIBLE
        binding.rvUnratedWorkers.visibility = View.GONE
    }

    private fun showEmptyRecentJobs() {
        binding.tvNoRecentJobs.text = "Belum ada riwayat pekerjaan"
        binding.tvNoRecentJobs.visibility = View.VISIBLE
        binding.rvRecentJobs.visibility = View.GONE
    }

    private fun showLoadingUnrated(show: Boolean) {
        // Implement loading state for unrated workers section
        if (show) {
            binding.tvNoUnratedWorkers.visibility = View.VISIBLE
            binding.tvNoUnratedWorkers.visibility = View.VISIBLE
            binding.rvUnratedWorkers.visibility = View.GONE
        }
    }

    private fun showLoadingRecent(show: Boolean) {
        // Implement loading state for recent jobs section
        if (show) {
            binding.tvNoRecentJobs.text = "Memuat riwayat pekerjaan..."
            binding.tvNoRecentJobs.visibility = View.VISIBLE
            binding.rvRecentJobs.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when returning to this activity
        loadUnratedWorkers()
        loadRecentJobs()
    }
}