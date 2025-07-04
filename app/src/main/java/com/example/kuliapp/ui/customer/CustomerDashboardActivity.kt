package com.example.kuliapp.ui.customer

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.RatingBar
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
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

    // === DEKLARASI VARIABEL ===
    private lateinit var binding: ActivityCustomerDashboardBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var unratedWorkerAdapter: UnratedWorkerAdapter
    private lateinit var recentJobAdapter: RecentJobAdapter
    private val workerListeners = mutableListOf<ListenerRegistration>()
    private val handler = Handler(Looper.getMainLooper())

    // Flag untuk menggunakan dummy data atau tidak
    private val useDummyData = false // Set ke false untuk menggunakan Firebase
    private var recentJobsListener: ListenerRegistration? = null

    // === KONSTANTA ===
    companion object {
        private const val TAG = "CustomerDashboard"
        private const val COLLECTION_WORKERS = "workers"
        private const val COLLECTION_RATINGS = "ratings"
        private const val COLLECTION_JOBS = "jobs"
    }

    // === LIFECYCLE METHODS ===

    // Inisialisasi Activity
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

    // Cleanup listeners saat activity dihancurkan
    override fun onDestroy() {
        super.onDestroy()
        cleanupWorkerListeners()
        recentJobsListener?.remove()
    }

    // === UI SETUP METHODS ===

    // Setup tampilan UI utama
    private fun setupUI() {
        // Set user's name in welcome message
        val userName = preferenceManager.getString("user_name")
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

    // Setup event listeners untuk tombol-tombol
    private fun setupListeners() {
        // Tombol Find Worker - navigasi ke worker list
        binding.btnFindWorker.setOnClickListener {
            startActivity(Intent(this, WorkerListActivity::class.java))
        }

        // Tombol Profile - navigasi ke profile
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

    // Setup swipe refresh functionality
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

    // === LOADING MANAGEMENT ===

    // Menampilkan/menyembunyikan loading overlay
    private fun showLoading(isLoading: Boolean) {
        findViewById<FrameLayout>(R.id.loadingOverlay).visibility =
            if (isLoading) View.VISIBLE else View.GONE
    }

    // === DATA REFRESH METHODS ===

    // Refresh semua data dashboard
    private fun refreshData() {
        // Refresh daftar worker yang sudah dirating
        loadRecentJobs()

        // Refresh daftar worker yang belum dirating
        loadUnratedWorkersFromFirebase()

        // Refresh listener untuk perubahan real-time
        setupRecentJobsListener()

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

    // === REAL-TIME LISTENERS ===

    // Setup listener untuk perubahan job real-time
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

    // === LOAD UNRATED WORKERS METHODS ===

    // Load unrated workers - method utama dengan callback
    private fun loadUnratedWorkers(onComplete: (() -> Unit)? = null) {
        if (useDummyData) {
            loadUnratedWorkersFromDummy(onComplete)
        } else {
            loadUnratedWorkersFromFirebase(onComplete)
        }
    }

    // Load unrated workers dari dummy data
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

    // Load unrated workers dari Firebase
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
                Log.d(TAG, "Setting up listener for job: ${job.workerId}, workerName: ${job.workerName}")

                if (job.workerName.isBlank()) {
                    Log.w(TAG, "Invalid workerName for job: ${job.workerId}")
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
                                        jobDate = job.jobDate,
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

    // Load unrated workers dari Firebase - versi efisien dengan batch query
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
                                        jobDate = j.jobDate,
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

    // Load unrated workers dengan pencarian nama yang flexible
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
                                        jobDate = j.jobDate,
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

    // === LOAD RECENT JOBS METHODS ===

    // Load recent jobs - method utama dengan callback
    private fun loadRecentJobs(onComplete: (() -> Unit)? = null) {
        if (useDummyData) {
            loadRecentJobsFromDummy(onComplete)
        } else {
            loadRecentJobsFromFirebase(onComplete)
        }
    }

    // Load recent jobs dari dummy data
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

    // Load recent jobs dari Firebase
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
                    val job = document.toObject(Job::class.java).copy(workerId = document.id)
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

    // === LISTENER MANAGEMENT ===

    // Cleanup semua worker listeners
    private fun cleanupWorkerListeners() {
        Log.d(TAG, "Cleaning up ${workerListeners.size} worker listeners")
        workerListeners.forEach { it.remove() }
        workerListeners.clear()
    }



    private fun setupRecentJobsListener() {
        val userId = auth.currentUser?.uid ?: return

        // Cleanup listener sebelumnya jika ada
        recentJobsListener?.remove()

        recentJobsListener = firestore.collection(COLLECTION_JOBS)
            .whereEqualTo("customerId", userId)
            .whereEqualTo("status", "completed")
            .orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to recent jobs", error)
                    return@addSnapshotListener
                }

                snapshot?.let { querySnapshot ->
                    val jobs = mutableListOf<Job>()
                    for (document in querySnapshot.documents) {
                        try {
                            val job = document.toObject(Job::class.java)
                            job?.let { jobs.add(it) }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing job document", e)
                        }
                    }

                    // Update UI dengan data terbaru
                    updateRecentJobsList(jobs)

                    // Log untuk debugging
                    Log.d(TAG, "Recent jobs updated: ${jobs.size} jobs found")
                }
            }
    }
    // Dummy data functions
    // Di CustomerDashboardActivity.kt - Enhanced Dummy Data

    private fun getDummyUnratedWorkers(): List<Worker> {
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
        return listOf(
            Worker(
                workerId = "worker_1",
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
                workerId = "worker_2",
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
                workerId = "worker_3",
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
                workerId = "worker_4",
                workerName = "Ahmad Wijaya",
                workerPhoto = "",
                jobDate = dateFormat.format(Date(System.currentTimeMillis() - 259200000)), // 3 days ago
                description = "Pengecatan tembok rumah",
                status = "completed",
                rating = 4.5,
                price = 200000,
                location = "Jakarta Timur"
            ),
            Job(
                workerId = "worker_5",
                workerName = "Dedi Cahyono",
                workerPhoto = "",
                jobDate = dateFormat.format(Date(System.currentTimeMillis() - 345600000)), // 4 days ago
                description = "Perbaikan instalasi listrik",
                status = "completed",
                rating = 5.0,
                price = 250000,
                location = "Bekasi Barat"
            ),
            Job(
                workerId = "worker_6",
                workerName = "Eko Prasetyo",
                workerPhoto = "",
                jobDate = dateFormat.format(Date(System.currentTimeMillis() - 432000000)), // 5 days ago
                description = "Pembuatan rak buku custom",
                status = "completed",
                rating = 4.0,
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
            customerId = currentUserId,
            customerName = "Test Customer",
            workerId = "worker_1",
            workerName = "Budi Santoso",
            workerPhoto = "",
            jobDate = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID")).format(Date()),
            description = "Test job yang sudah selesai",
            status = "completed",
            rating = 0.0, // Belum di-rating
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
        runOnUiThread {
            recentJobAdapter.updateData(jobs)

            // Show/hide empty state
            if (jobs.isEmpty()) {
                showEmptyRecentJobs()
            } else {
                binding.rvRecentJobs.visibility = View.VISIBLE
                binding.layoutNoRecentJobs.visibility = View.GONE
            }
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
                submitRating(worker, rating, dialog)
            }
        }

        dialog.show()
    }

    private fun submitRating(worker: Worker, rating: Float, dialog: Dialog) {
        val currentUserId = auth.currentUser?.uid
        val currentUserName = preferenceManager.getString("user_name") ?: "Pengguna"

        if (currentUserId == null) {
            Toast.makeText(this, "Error: User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d(TAG, "Submitting rating for worker: ${worker.name}, rating: $rating")

        // Create rating object
        val ratingId = firestore.collection(COLLECTION_RATINGS).document().id
        val ratingObj = Rating(
            workerId = worker.workerId,
            customerId = currentUserId,
            customerName = currentUserName,
            rating = rating.toDouble(),
            date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()),
            _createdAt = Timestamp.now()
        )

        // Show loading
        dialog.setCancelable(false)

        // Submit rating to Firebase dengan urutan yang benar
        firestore.collection(COLLECTION_RATINGS)
            .document(ratingId)
            .set(ratingObj)
            .addOnSuccessListener {
                Log.d(TAG, "Rating saved successfully")

                // PERBAIKAN: Update job rating terlebih dahulu
                updateJobRating(worker, rating) { jobUpdateSuccess ->
                    if (jobUpdateSuccess) {
                        Log.d(TAG, "Job rating updated successfully")

                        // Update worker's overall rating
                        updateWorkerRating(worker.workerId) {
                            Log.d(TAG, "Worker rating updated successfully")

                            dialog.dismiss()
                            Toast.makeText(
                                this,
                                getString(R.string.rating_submitted_successfully),
                                Toast.LENGTH_SHORT
                            ).show()

                            // TAMBAHAN: Force refresh jika listener tidak trigger
                            handler.postDelayed({
                                Log.d(TAG, "Force refreshing recent jobs after rating")
                                loadRecentJobsFromFirebase()
                            }, 2000) // Delay 2 detik untuk memastikan Firestore sync
                        }
                    } else {
                        dialog.dismiss()
                        Log.e(TAG, "Failed to update job rating")
                        Toast.makeText(this, "Gagal menyimpan rating ke job", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .addOnFailureListener { exception ->
                dialog.dismiss()
                Log.e(TAG, "Error submitting rating", exception)
                Toast.makeText(this, "Gagal mengirim rating", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateWorkerRatingCount(workerId: String, callback: (Boolean) -> Unit) {
        // 1. Ambil semua rating untuk worker ini
        firestore.collection(COLLECTION_RATINGS)
            .whereEqualTo("workerId", workerId)
            .get()
            .addOnSuccessListener { documents ->
                var totalRating = 0f
                var count = 0

                // 2. Hitung total rating dan jumlah rating

                for (document in documents) {
                    val rating = (document.getDouble("rating") ?: 0.0).toFloat()
                    totalRating += rating
                    count++
                }


                // 3. Hitung rata-rata rating
                val averageRating = if (count > 0) totalRating / count else 0.0

                // 4. Update data worker dengan rating baru
                firestore.collection(COLLECTION_WORKERS)
                    .document(workerId)
                    .update(
                        mapOf(
                            "rating" to averageRating,
                            "ratingCount" to count,
                            "updatedAt" to Timestamp.now()
                        )
                    )
                    .addOnSuccessListener {
                        Log.d(TAG, "Worker rating updated successfully. New rating: $averageRating, Count: $count")
                        callback(true)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error updating worker rating", exception)
                        callback(false)
                    }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error fetching ratings for worker", exception)
                callback(false)
            }
    }

    private fun updateJobRating(worker: Worker, rating: Float, callback: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return

        // Update job yang terkait dengan worker ini
        firestore.collection(COLLECTION_JOBS)
            .whereEqualTo("customerId", userId)
            .whereEqualTo("workerId", worker.workerId)
            .whereEqualTo("status", "completed")
            .get()
            .addOnSuccessListener { documents ->
                val batch = firestore.batch()

                for (document in documents) {
                    val jobRef = firestore.collection(COLLECTION_JOBS).document(document.id)
                    batch.update(jobRef, mapOf(
                        "rating" to rating,
                        "updatedAt" to Timestamp.now()
                    ))
                }

                batch.commit()
                    .addOnSuccessListener {
                        callback(true)
                    }
                    .addOnFailureListener { exception ->
                        Log.e(TAG, "Error updating job ratings", exception)
                        callback(false)
                    }
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error fetching jobs for rating update", exception)
                callback(false)
            }
    }

    private fun debugWorkerRatings(workerId: String) {
        firestore.collection(COLLECTION_RATINGS)
            .whereEqualTo("workerId", workerId)
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "=== DEBUG WORKER RATINGS ===")
                Log.d(TAG, "Worker ID: $workerId")
                Log.d(TAG, "Total ratings found: ${documents.size()}")

                var totalRating = 0.0
                for ((index, document) in documents.withIndex()) {
                    val rating = (document.getDouble("rating") ?: 0.0).toFloat()
                    val customerName = document.getString("customerName") ?: "Unknown"
                    val review = document.getString("review") ?: ""

                    Log.d(TAG, "Rating ${index + 1}: $rating by $customerName - $review")
                    totalRating += rating
                }

                val averageRating = if (documents.size() > 0) totalRating / documents.size() else 0.0
                Log.d(TAG, "Average rating: $averageRating")
                Log.d(TAG, "=== END DEBUG ===")
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
        // Hanya untuk testing - hapus di production
        val testRating = Rating(
            workerId = "test_worker_id", // Ganti dengan worker ID yang valid
            customerId = auth.currentUser?.uid ?: "",
            customerName = "Test Customer",
            rating = 4.5,
            date = SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date()),
            _createdAt = Timestamp.now()
        )

        firestore.collection(COLLECTION_RATINGS)
            .document(testRating.workerId)
            .set(testRating)
            .addOnSuccessListener {
                updateWorkerRatingCount(testRating.workerId) { success ->
                    if (success) {
                        Toast.makeText(this, "Test rating berhasil dibuat", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Test rating dibuat tapi gagal update worker", Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    private fun updateWorkerRating(workerId: String, callback: () -> Unit) {
        Log.d(TAG, "Updating worker rating for: $workerId")

        // Hitung ulang rating berdasarkan semua rating yang ada
        firestore.collection(COLLECTION_RATINGS)
            .whereEqualTo("workerId", workerId)
            .get()
            .addOnSuccessListener { documents ->
                var totalRating = 0f
                var count = 0
                val ratings = mutableListOf<Float>()

                for (document in documents) {
                    val rating = (document.getDouble("rating") ?: 0.0).toFloat()
                    if (rating > 0f) { // Hanya hitung rating yang valid
                        totalRating += rating
                        count++
                        ratings.add(rating)
                    }
                }

                val averageRating = if (count > 0) {
                    String.format(Locale.US, "%.2f", totalRating / count).toFloat()
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
        showLoading(true)

        firestore.collection(COLLECTION_WORKERS)
            .get()
            .addOnSuccessListener { workers ->
                val batch = firestore.batch()
                var processedCount = 0
                val totalWorkers = workers.size()

                if (totalWorkers == 0) {
                    showLoading(false)
                    return@addOnSuccessListener
                }

                for (workerDoc in workers) {
                    val workerId = workerDoc.id

                    // Ambil semua rating untuk worker ini
                    firestore.collection(COLLECTION_RATINGS)
                        .whereEqualTo("workerId", workerId)
                        .get()
                        .addOnSuccessListener { ratings ->
                            var totalRating = 0f
                            var count = 0

                            for (ratingDoc in ratings) {
                                val rating = (ratingDoc.getDouble("rating") ?: 0.0).toFloat()
                                totalRating += rating
                                count++
                            }

                            val averageRating = if (count > 0) totalRating / count else 0.0

                            // Update worker rating
                            val workerRef = firestore.collection(COLLECTION_WORKERS).document(workerId)
                            batch.update(workerRef, mapOf(
                                "rating" to averageRating,
                                "ratingCount" to count,
                                "updatedAt" to Timestamp.now()
                            ))

                            processedCount++

                            // Commit batch ketika semua worker sudah diproses
                            if (processedCount == totalWorkers) {
                                batch.commit()
                                    .addOnSuccessListener {
                                        showLoading(false)
                                        Toast.makeText(this, "Rating semua pekerja berhasil diperbarui", Toast.LENGTH_SHORT).show()
                                        refreshData()
                                    }
                                    .addOnFailureListener { exception ->
                                        showLoading(false)
                                        Log.e(TAG, "Error committing batch update", exception)
                                        Toast.makeText(this, "Gagal memperbarui rating", Toast.LENGTH_SHORT).show()
                                    }
                            }
                        }
                }
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e(TAG, "Error fetching workers for recalculation", exception)
                Toast.makeText(this, "Gagal mengambil data pekerja", Toast.LENGTH_SHORT).show()
            }
    }

    private fun debugRecentJobs() {
        val currentUserId = auth.currentUser?.uid ?: return

        firestore.collection(COLLECTION_JOBS)
            .whereEqualTo("customerId", currentUserId)
            .whereEqualTo("status", "completed")
            .get()
            .addOnSuccessListener { documents ->
                Log.d(TAG, "=== DEBUG RECENT JOBS ===")
                documents.forEach { doc ->
                    val job = doc.toObject(Job::class.java)
                    Log.d(TAG, "Job ID: ${doc.id}")
                    Log.d(TAG, "Worker Name: ${job.workerName}")
                    Log.d(TAG, "Description: ${job.description}")
                    Log.d(TAG, "Rating: ${job.rating}")
                    Log.d(TAG, "Updated At: ${job.updatedAt}")
                    Log.d(TAG, "---")
                }
            }
    }

    private fun forceRefreshRecentJobs() {
        Log.d(TAG, "Force refreshing recent jobs...")

        // Remove existing listener
        recentJobsListener?.remove()

        // Setup new listener
        setupRecentJobsListener()

        // Also load once manually
        loadRecentJobsFromFirebase()
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
        Log.d(TAG, "onResume: Refreshing data")
        // Setup listener lagi jika hilang
        setupRecentJobsListener()
        // Refresh data when returning to this activity
        loadUnratedWorkers()
        loadRecentJobs()
    }

}