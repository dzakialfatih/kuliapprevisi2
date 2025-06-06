package com.example.kuliapp.ui.worker

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.kuliapp.R
import com.example.kuliapp.adapters.WorkerAdapter
import com.example.kuliapp.databinding.ActivityWorkerListBinding
import com.example.kuliapp.databinding.DialogBookingConfirmationBinding
import com.example.kuliapp.models.Job
import com.example.kuliapp.models.Worker
import com.example.kuliapp.ui.customer.CustomerDashboardActivity
import com.example.kuliapp.ui.customer.CustomerDashboardActivity.Companion
import com.example.kuliapp.utils.PreferenceManager
import com.example.kuliapp.utils.WhatsappUtils
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.*

class WorkerListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkerListBinding
    private lateinit var workerAdapter: WorkerAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var preferenceManager: PreferenceManager
    private var allWorkers = mutableListOf<Worker>()
    private var filteredWorkers = mutableListOf<Worker>()
    private var filterByRating = true
    private var currentLocationQuery = ""

    // Flag untuk menggunakan dummy data atau Firebase
    private var useDummyData = false // Set ke false jika ingin menggunakan Firebase

    companion object {
        private const val TAG = "WorkerListActivity"
        private const val COLLECTION_WORKERS = "workers"
        private const val COLLECTION_JOBS = "jobs"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkerListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        preferenceManager = PreferenceManager(this)

        setupUI()
        setupListeners()
        setupSwipeRefresh()

        // Load data berdasarkan flag
        if (useDummyData) {
            loadDummyWorkers()
        } else {
            loadWorkersFromFirebase()
        }
    }

    private fun setupUI() {
        // Setup toolbar
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        // Setup RecyclerView
        binding.rvWorkers.layoutManager = LinearLayoutManager(this)
        workerAdapter = WorkerAdapter(mutableListOf()) { worker ->
            // Handle hire worker - show booking confirmation dialog
            showBookingConfirmationDialog(worker)
        }
        binding.rvWorkers.adapter = workerAdapter
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
        Log.d(TAG, "Refreshing worker data...")

        if (useDummyData) {
            // Simulasi delay untuk dummy data
            binding.root.postDelayed({
                allWorkers.clear()
                allWorkers.addAll(getDummyWorkers())
                applyFilters()
                binding.swipeRefreshLayout.isRefreshing = false
            }, 1000)
        } else {
            // Refresh dari Firebase
            firestore.collection(COLLECTION_WORKERS)
                .whereEqualTo("isAvailable", true)
                .get()
                .addOnCompleteListener { task ->
                    // Stop refresh animation dalam semua kasus
                    binding.swipeRefreshLayout.isRefreshing = false

                    if (task.isSuccessful) {
                        allWorkers.clear()
                        task.result?.documents?.forEach { document ->
                            val worker = document.toObject(Worker::class.java)
                            if (worker != null && worker.name.isNotEmpty()) {
                                allWorkers.add(worker)
                            }
                        }
                        applyFilters()
                    } else {
                        Toast.makeText(this, "Gagal memuat data", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun setupListeners() {
        // Search location filter
        binding.etSearchLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentLocationQuery = s.toString()
                applyFilters()
            }
        })

        // Filter buttons
        binding.btnFilterRating.setOnClickListener {
            filterByRating = true
            updateFilterButtonStates()
            applyFilters()
        }

        binding.btnFilterDistance.setOnClickListener {
            filterByRating = false
            updateFilterButtonStates()
            applyFilters()
        }
    }

    private fun showBookingConfirmationDialog(worker: Worker) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val dialogBinding = DialogBookingConfirmationBinding.inflate(layoutInflater)

        dialog.setContentView(dialogBinding.root)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.WHITE))
        dialog.window?.setLayout(
            resources.displayMetrics.widthPixels * 90 / 100,
            android.view.WindowManager.LayoutParams.WRAP_CONTENT
        )

        // Set worker details in dialog
        dialogBinding.tvWorkerName.text = worker.name
        dialogBinding.tvWorkerLocation.text = worker.location
        dialogBinding.tvWorkerRating.text = worker.getFormattedRating()
        dialogBinding.tvPrice.text = worker.getFormattedPrice()
        dialogBinding.tvExperience.text = worker.experience

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
            val jobDescription = dialogBinding.etJobDescription.text.toString().trim()

            if (jobDescription.isEmpty()) {
                Toast.makeText(this, "Mohon isi deskripsi pekerjaan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()

            // Create booking and then open WhatsApp
            createBooking(worker, jobDescription) { success ->
                if (success) {
                    // Open WhatsApp after successful booking
                    openWhatsAppWithWorker(worker, jobDescription)
                } else {
                    Toast.makeText(this, "Gagal membuat pesanan", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    // Di WorkerListActivity.kt - Perbaikan fungsi createBooking

    private fun createBooking(worker: Worker, jobDescription: String, callback: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid
        val currentUserName = preferenceManager.getString("user_name") ?: "Pengguna"

        if (currentUserId == null) {
            Toast.makeText(this, "Error: User not authenticated", Toast.LENGTH_SHORT).show()
            callback(false)
            return
        }

        if (useDummyData) {
            // UNTUK DUMMY DATA
            Toast.makeText(this, "Pesanan berhasil dibuat (dummy mode)", Toast.LENGTH_SHORT).show()
            callback(true)
            return
        }

        // Create job object dengan status yang benar
        val jobId = firestore.collection(COLLECTION_JOBS).document().id
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))

        val job = Job(
            customerId = currentUserId,
            customerName = currentUserName,
            workerId = worker.workerId,
            workerName = worker.name,
            workerPhoto = worker.photo,
            date = dateFormat.format(Date()),
            description = jobDescription,
            status = "completed", // UBAH DARI "completed" KE "pending"
            rating = 0.0f,
            price = worker.price,
            location = worker.location,
            _createdAt = Timestamp.now(),
            _updatedAt = Timestamp.now()
        )

        // LOG UNTUK DEBUG
        Log.d(TAG, "Creating job with data:")
        Log.d(TAG, "Job ID: ${job.workerId}")
        Log.d(TAG, "Customer ID: ${job.customerId}")
        Log.d(TAG, "Worker ID: ${job.workerId}")
        Log.d(TAG, "Status: ${job.status}")
        Log.d(TAG, "Rating: ${job.rating}")
        Log.d(TAG, "Description: ${job.description}")

        // Show loading
        Toast.makeText(this, "Membuat pesanan...", Toast.LENGTH_SHORT).show()

        // Save job to Firebase
        firestore.collection(COLLECTION_JOBS)
            .document(jobId)
            .set(job)
            .addOnSuccessListener {
                Log.d(TAG, "Job created successfully: $jobId")
                Log.d(TAG, "Job saved to Firebase with status: ${job.status} and rating: ${job.rating}")
                Toast.makeText(this, "Pesanan berhasil dibuat", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                callback(true)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error creating job", exception)
                Toast.makeText(this, "Gagal membuat pesanan", Toast.LENGTH_SHORT).show()
                callback(false)
            }
    }

    // TAMBAHKAN FUNGSI UNTUK SIMULASI UPDATE STATUS JOB (UNTUK TESTING)
    private fun simulateJobCompletion(jobId: String) {
        // Fungsi ini bisa dipanggil untuk testing
        // Dalam aplikasi real, ini akan dilakukan oleh worker atau sistem
        firestore.collection(COLLECTION_JOBS)
            .document(jobId)
            .update(
                mapOf(
                    "status" to "completed",
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnSuccessListener {
                Log.d(TAG, "Job status updated to completed: $jobId")
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error updating job status", exception)
            }
    }

    private fun openWhatsAppWithWorker(worker: Worker, jobDescription: String) {
        val phoneNumber = if (worker.phone.startsWith("+62")) {
            worker.phone
        } else if (worker.phone.startsWith("08")) {
            "+62${worker.phone.substring(1)}"
        } else if (worker.phone.startsWith("62")) {
            "+${worker.phone}"
        } else {
            "+62${worker.phone}"
        }

        val message = getBookingMessage(worker, jobDescription)
        WhatsappUtils.openWhatsAppChat(this, phoneNumber, message)

        setResult(RESULT_OK)
    }

    private fun updateFilterButtonStates() {
        // Update button appearance based on selection
        if (filterByRating) {
            binding.btnFilterRating.isSelected = true
            binding.btnFilterDistance.isSelected = false
        } else {
            binding.btnFilterRating.isSelected = false
            binding.btnFilterDistance.isSelected = true
        }
    }

    // Fungsi untuk load dummy data
    private fun loadDummyWorkers() {
        showLoading(true)

        // Simulasi delay loading
        binding.root.postDelayed({
            allWorkers.clear()
            allWorkers.addAll(getDummyWorkers())

            Log.d(TAG, "Loaded ${allWorkers.size} dummy workers")
            showLoading(false)
            applyFilters()
        }, 1000) // Delay 1 detik untuk simulasi loading
    }

    // Fungsi dummy data
    private fun getDummyWorkers(): List<Worker> {
        return listOf(
            Worker(
                workerId = "1",
                name = "Rudi Hartono",
                photo = "",
                rating = 4.8f,
                ratingCount = 24,
                location = "Bekasi Utara",
                experience = "Pengalaman 5 tahun sebagai tukang bangunan",
                phone = "6281234567890",
                price = 150000,
                isAvailable = true,
            ),
            Worker(
                workerId = "2",
                name = "Joko Widodo",
                photo = "",
                rating = 4.5f,
                ratingCount = 18,
                location = "Bekasi Selatan",
                experience = "Spesialis keramik dan granit",
                phone = "6281234567891",
                price = 175000,
                isAvailable = true,
            ),
            Worker(
                workerId = "3",
                name = "Ahmad Wijaya",
                photo = "",
                rating = 4.9f,
                ratingCount = 32,
                location = "Jakarta Timur",
                experience = "Ahli pengecatan dan renovasi",
                phone = "6281234567892",
                price = 200000,
                isAvailable = true,
            ),
            Worker(
                workerId = "4",
                name = "Dedi Cahyono",
                photo = "",
                rating = 4.7f,
                ratingCount = 15,
                location = "Bekasi Barat",
                experience = "Instalasi listrik dan perbaikan",
                phone = "6281234567893",
                price = 185000,
                isAvailable = true,
            ),
            Worker(
                workerId = "5",
                name = "Eko Prasetyo",
                photo = "",
                rating = 4.6f,
                ratingCount = 21,
                location = "Bekasi Timur",
                experience = "Pembuatan furniture dan renovasi ringan",
                phone = "6281234567894",
                price = 160000,
                isAvailable = true,
            )
        )
    }

    private fun loadWorkersFromFirebase() {
        showLoading(true)

        firestore.collection(COLLECTION_WORKERS)
            .whereEqualTo("isAvailable", true)
            .get()
            .addOnSuccessListener { documents ->
                showLoading(false)
                allWorkers.clear()

                for (document in documents) {
                    val worker = document.toObject(Worker::class.java)

                    // DEBUG: Log semua data worker
                    Log.d(TAG, "Worker Data: ${document.data}")
                    Log.d(TAG, "Worker Name: ${worker.name}")
                    Log.d(TAG, "Worker Price (Long): ${worker.price}")
                    Log.d(TAG, "Worker Price (Formatted): ${worker.getFormattedPrice()}")

                    if (worker.name.isNotEmpty()) {
                        allWorkers.add(worker)
                    }
                }

                Log.d(TAG, "Loaded ${allWorkers.size} workers from Firebase")
                applyFilters()
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e(TAG, "Error loading workers from Firebase", exception)

                // Fallback ke dummy data jika Firebase gagal
                Log.d(TAG, "Falling back to dummy data")
                loadDummyWorkers()
            }
    }

    private fun applyFilters() {
        // Filter berdasarkan lokasi terlebih dahulu
        val locationFiltered = if (currentLocationQuery.isEmpty()) {
            allWorkers
        } else {
            allWorkers.filter { worker ->
                worker.location.contains(currentLocationQuery, ignoreCase = true)
            }
        }

        // Lalu urutkan berdasarkan pilihan filter
        filteredWorkers = if (filterByRating) {
            locationFiltered.sortedWith(
                compareByDescending<Worker> { it.rating }
                    .thenByDescending { it.ratingCount }
            ).toMutableList()
        } else {
            // Untuk sementara, kita urutkan berdasarkan update terbaru
            // Nanti bisa diubah berdasarkan jarak GPS
            locationFiltered.sortedByDescending { it.updatedAt }.toMutableList()
        }

        updateWorkersList()
    }

    private fun updateWorkersList() {
        if (filteredWorkers.isEmpty()) {
            if (allWorkers.isEmpty()) {
                showEmptyState("Belum ada pekerja yang terdaftar")
            } else {
                showEmptyState("Tidak ada pekerja di lokasi \"$currentLocationQuery\"")
            }
        } else {
            hideEmptyState()
            workerAdapter.updateData(filteredWorkers)
        }
    }

    private fun showEmptyState(message: String) {
        binding.tvNoWorkers.text = message
        binding.tvNoWorkers.visibility = View.VISIBLE
        binding.rvWorkers.visibility = View.GONE
    }

    private fun hideEmptyState() {
        binding.tvNoWorkers.visibility = View.GONE
        binding.rvWorkers.visibility = View.VISIBLE
    }

    private fun showLoading(show: Boolean) {
        // Implement loading indicator
        // binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE

        if (show) {
            binding.tvNoWorkers.text = "Memuat data pekerja..."
            binding.tvNoWorkers.visibility = View.VISIBLE
            binding.rvWorkers.visibility = View.GONE
        }
    }

    private fun getBookingMessage(worker: Worker, jobDescription: String): String {
        return """
            Halo ${worker.name}, 
            
            Saya ingin memesan jasa Anda melalui aplikasi JasaKuli.
            
            Detail Pekerjaan:
            - Deskripsi: $jobDescription
            - Lokasi: ${worker.location}
            - Tarif: ${worker.getFormattedPrice()}/hari
            - Rating: ${worker.getFormattedRating()} (${worker.ratingCount} ulasan)
            
            Kapan Anda bisa memulai pekerjaan ini?
            
            Terima kasih.
        """.trimIndent()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data ketika kembali ke activity ini
        // untuk memastikan status availability up-to-date
        if (useDummyData) {
            loadDummyWorkers()
        } else {
            loadWorkersFromFirebase()
        }
    }
}