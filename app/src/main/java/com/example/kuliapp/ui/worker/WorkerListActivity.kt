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
import com.example.kuliapp.models.RatingStats
import com.example.kuliapp.models.Worker
import com.example.kuliapp.utils.PreferenceManager
import com.example.kuliapp.utils.WhatsappUtils
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class WorkerListActivity : AppCompatActivity() {

    // Inisialisasi View Binding
    private lateinit var binding: ActivityWorkerListBinding

    // Inisialisasi Adapter untuk RecyclerView
    private lateinit var workerAdapter: WorkerAdapter

    // Inisialisasi Firebase Components
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var preferenceManager: PreferenceManager

    // List untuk menyimpan data worker
    private var allWorkers = mutableListOf<Worker>()
    private var filteredWorkers = mutableListOf<Worker>()

    // Variable untuk filter dan pencarian
    private var filterByRating = true
    private var currentLocationQuery = ""

    // Flag untuk menggunakan dummy data atau Firebase
    private var useDummyData = false // Set ke false jika ingin menggunakan Firebase

    companion object {
        private const val TAG = "WorkerListActivity"
        private const val COLLECTION_WORKERS = "workers"
        private const val COLLECTION_JOBS = "jobs"
        private const val COLLECTION_RATINGS = "ratings"
    }

    // Fungsi utama onCreate - lifecycle activity
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkerListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi Firebase
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        preferenceManager = PreferenceManager(this)

        // Setup komponen UI
        setupUI()

        // Setup event listeners
        setupListeners()

        // Setup swipe to refresh
        setupSwipeRefresh()

        // Load data berdasarkan flag
        if (useDummyData) {
            loadDummyWorkers()
        } else {
            loadWorkersFromFirebase()
        }
    }

    // Fungsi untuk setup UI components
    private fun setupUI() {
        // Setup tombol back di toolbar
        binding.btnBack.setOnClickListener {
            onBackPressed()
        }

        // Setup RecyclerView untuk menampilkan list worker
        binding.rvWorkers.layoutManager = LinearLayoutManager(this)
        workerAdapter = WorkerAdapter(mutableListOf()) { worker ->
            // Handle tombol hire worker - tampilkan dialog konfirmasi booking
            showBookingConfirmationDialog(worker)
        }
        binding.rvWorkers.adapter = workerAdapter
    }

    // Fungsi untuk setup swipe to refresh
    private fun setupSwipeRefresh() {
        // Setup SwipeRefreshLayout untuk refresh data
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }

        // Kustomisasi warna refresh indicator
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.accent,
            R.color.primary_dark
        )
    }

    // Fungsi untuk refresh data worker
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
            // Refresh data dari Firebase
            loadWorkersFromFirebase()
        }
    }

    // Fungsi untuk setup event listeners
    private fun setupListeners() {
        // Search box untuk filter lokasi
        binding.etSearchLocation.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                currentLocationQuery = s.toString()
                applyFilters()
            }
        })

        // Tombol filter berdasarkan rating
        binding.btnFilterRating.setOnClickListener {
            filterByRating = true
            updateFilterButtonStates()
            applyFilters()
        }

        // Tombol filter berdasarkan jarak
        binding.btnFilterDistance.setOnClickListener {
            filterByRating = false
            updateFilterButtonStates()
            applyFilters()
        }
    }

    // Fungsi untuk menampilkan dialog konfirmasi booking
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

        // Set detail worker di dialog - SAMA SEPERTI WORKERDASHBOARD
        dialogBinding.tvWorkerName.text = worker.name
        dialogBinding.tvWorkerLocation.text = worker.location
        dialogBinding.tvWorkerRating.text = String.format("%.1f", worker.rating.toFloat())
        dialogBinding.tvPrice.text = worker.getFormattedPrice()
        dialogBinding.tvExperience.text = worker.experience

        // Load foto worker jika tersedia
        if (worker.photo.isNotEmpty()) {
            // Load with image loading library
            // Glide.with(this).load(worker.photo).into(dialogBinding.ivWorkerPhoto)
        }

        // Setup tombol cancel di dialog
        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        // Setup tombol submit booking di dialog
        dialogBinding.btnSubmit.setOnClickListener {
            val jobDescription = dialogBinding.etJobDescription.text.toString().trim()

            if (jobDescription.isEmpty()) {
                Toast.makeText(this, "Mohon isi deskripsi pekerjaan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            dialog.dismiss()

            // Buat booking dan buka WhatsApp
            createBooking(worker, jobDescription) { success ->
                if (success) {
                    // Buka WhatsApp setelah booking berhasil
                    openWhatsAppWithWorker(worker, jobDescription)
                } else {
                    Toast.makeText(this, "Gagal membuat pesanan", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    // Fungsi untuk membuat booking/pesanan baru
    private fun createBooking(worker: Worker, jobDescription: String, callback: (Boolean) -> Unit) {
        val currentUserId = auth.currentUser?.uid
        val currentUserName = preferenceManager.getString("user_name") ?: "Pengguna"

        if (currentUserId == null) {
            Toast.makeText(this, "Error: User not authenticated", Toast.LENGTH_SHORT).show()
            callback(false)
            return
        }

        if (useDummyData) {
            // Mode dummy data - simulasi booking berhasil
            Toast.makeText(this, "Pesanan berhasil dibuat (dummy mode)", Toast.LENGTH_SHORT).show()
            callback(true)
            return
        }

        // Buat object job dengan data booking
        val jobId = firestore.collection(COLLECTION_JOBS).document().id
        val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))

        val job = Job(
            customerId = currentUserId,
            customerName = currentUserName,
            workerId = worker.workerId,
            workerName = worker.name,
            workerPhoto = worker.photo,
            jobDate = dateFormat.format(Date()),
            description = jobDescription,
            status = "completed", // Status job setelah dibuat
            rating = 0.0,
            price = worker.price,
            location = worker.location,
            _createdAt = Timestamp.now(),
            _updatedAt = Timestamp.now()
        )

        // Log untuk debugging
        Log.d(TAG, "Creating job with data:")
        Log.d(TAG, "Job ID: ${job.workerId}")
        Log.d(TAG, "Customer ID: ${job.customerId}")
        Log.d(TAG, "Worker ID: ${job.workerId}")
        Log.d(TAG, "Status: ${job.status}")
        Log.d(TAG, "Rating: ${job.rating}")
        Log.d(TAG, "Description: ${job.description}")

        // Tampilkan loading indicator
        Toast.makeText(this, "Membuat pesanan...", Toast.LENGTH_SHORT).show()

        // Simpan job ke Firebase
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

    // Fungsi untuk membuka WhatsApp dengan worker
    private fun openWhatsAppWithWorker(worker: Worker, jobDescription: String) {
        // Format nomor telepon untuk WhatsApp
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

    // Fungsi untuk update tampilan tombol filter
    private fun updateFilterButtonStates() {
        // Update appearance tombol berdasarkan filter yang dipilih
        if (filterByRating) {
            binding.btnFilterRating.isSelected = true
            binding.btnFilterDistance.isSelected = false
        } else {
            binding.btnFilterRating.isSelected = false
            binding.btnFilterDistance.isSelected = true
        }
    }

    // Fungsi untuk load dummy data worker
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

    // Fungsi untuk mendapatkan dummy data worker
    private fun getDummyWorkers(): List<Worker> {
        return listOf(
            Worker(
                workerId = "1",
                name = "Rudi Hartono",
                photo = "",
                rating = 4.8,
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
                rating = 4.5,
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
                rating = 4.9,
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
                rating = 4.7,
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
                rating = 4.6,
                ratingCount = 21,
                location = "Bekasi Timur",
                experience = "Pembuatan furniture dan renovasi ringan",
                phone = "6281234567894",
                price = 160000,
                isAvailable = true,
            )
        )
    }

    // Fungsi untuk load data worker dari Firebase
    private fun loadWorkersFromFirebase() {
        showLoading(true)

        firestore.collection(COLLECTION_WORKERS)
            .whereEqualTo("isAvailable", true)
            .get()
            .addOnSuccessListener { documents ->
                allWorkers.clear()

                for (document in documents) {
                    val worker = document.toObject(Worker::class.java)

                    // Log debug untuk semua data worker
                    Log.d(TAG, "Worker Data: ${document.data}")
                    Log.d(TAG, "Worker Name: ${worker.name}")
                    Log.d(TAG, "Worker Price (Long): ${worker.price}")
                    Log.d(TAG, "Worker Price (Formatted): ${worker.getFormattedPrice()}")

                    if (worker.name.isNotEmpty()) {
                        allWorkers.add(worker)
                    }
                }

                Log.d(TAG, "Loaded ${allWorkers.size} workers from Firebase")

                // Load rating data untuk semua worker - SAMA SEPERTI WORKERDASHBOARD
                loadRatingDataForAllWorkers()
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e(TAG, "Error loading workers from Firebase", exception)

                // Fallback ke dummy data jika Firebase gagal
                Log.d(TAG, "Falling back to dummy data")
                loadDummyWorkers()
            }
    }

    // ===== METODE RATING - SAMA SEPERTI WORKERDASHBOARD =====
    // Fungsi untuk load rating data untuk semua worker
    private fun loadRatingDataForAllWorkers() {
        if (allWorkers.isEmpty()) {
            showLoading(false)
            applyFilters()
            return
        }

        // Buat list untuk menyimpan hasil rating yang sudah diproses
        val processedWorkers = mutableListOf<Worker>()
        var remainingWorkers = allWorkers.size

        // Proses setiap worker untuk mendapatkan rating
        allWorkers.forEach { worker ->
            loadRatingDataForWorker(worker) { updatedWorker ->
                processedWorkers.add(updatedWorker)
                remainingWorkers--

                // Jika semua worker sudah diproses
                if (remainingWorkers == 0) {
                    // Update allWorkers dengan data rating yang baru
                    allWorkers.clear()
                    allWorkers.addAll(processedWorkers)

                    showLoading(false)
                    applyFilters()
                    binding.swipeRefreshLayout.isRefreshing = false
                }
            }
        }
    }

    // Fungsi untuk load rating data untuk satu worker - SAMA SEPERTI WORKERDASHBOARD
    private fun loadRatingDataForWorker(worker: Worker, callback: (Worker) -> Unit) {
        Log.d("RatingDebug", "Mengambil rating untuk workerId = ${worker.workerId}")

        firestore.collection(COLLECTION_RATINGS)
            .whereEqualTo("workerId", worker.workerId)
            .get()
            .addOnSuccessListener { documents ->
                Log.d("RatingDebug", "Jumlah dokumen ditemukan: ${documents.size()}")

                var totalRating = 0.0
                var count = 0

                for (document in documents) {
                    val rating = document.getDouble("rating")
                    Log.d("RatingDebug", "Rating ditemukan: $rating")
                    if (rating != null) {
                        totalRating += rating
                        count++
                    }
                }

                Log.d("RatingDebug", "Total rating: $totalRating dari $count ulasan")

                val updatedWorker = if (count > 0) {
                    val average = totalRating / count
                    Log.d("RatingDebug", "Average rating untuk ${worker.name}: $average")
                    worker.copy(
                        rating = average,
                        ratingCount = count
                    )
                } else {
                    // Jika tidak ada rating, gunakan nilai default
                    worker.copy(
                        rating = 0.0,
                        ratingCount = 0
                    )
                }

                callback(updatedWorker)
            }
            .addOnFailureListener { exception ->
                Log.e("RatingDebug", "Error loading rating for ${worker.name}", exception)
                // Jika gagal, tetap kembalikan worker asli
                callback(worker)
            }
    }

    // Fungsi untuk menerapkan filter dan sorting pada data worker
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
            // Sorting berdasarkan rating tertinggi - SAMA SEPERTI WORKERDASHBOARD
            locationFiltered.sortedWith(
                compareByDescending<Worker> { it.rating }
                    .thenByDescending { it.ratingCount }
            ).toMutableList()
        } else {
            // Sorting berdasarkan jarak (sementara berdasarkan update terbaru)
            // Nanti bisa diubah berdasarkan jarak GPS
            locationFiltered.sortedByDescending { it.updatedAt }.toMutableList()
        }

        updateWorkersList()
    }

    // Fungsi untuk update tampilan list worker
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

    // Fungsi untuk menampilkan empty state
    private fun showEmptyState(message: String) {
        binding.tvNoWorkers.text = message
        binding.tvNoWorkers.visibility = View.VISIBLE
        binding.rvWorkers.visibility = View.GONE
    }

    // Fungsi untuk menyembunyikan empty state
    private fun hideEmptyState() {
        binding.tvNoWorkers.visibility = View.GONE
        binding.rvWorkers.visibility = View.VISIBLE
    }

    // Fungsi untuk menampilkan/menyembunyikan loading indicator
    private fun showLoading(show: Boolean) {
        // Implement loading indicator
        // binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE

        if (show) {
            binding.tvNoWorkers.text = "Memuat data pekerja..."
            binding.tvNoWorkers.visibility = View.VISIBLE
            binding.rvWorkers.visibility = View.GONE
        }
    }

    // Fungsi untuk membuat template pesan booking WhatsApp
    private fun getBookingMessage(worker: Worker, jobDescription: String): String {
        return """
            Halo ${worker.name}, 
            
            Saya ingin memesan jasa Anda melalui aplikasi JasaKuli.
            
            Detail Pekerjaan:
            - Deskripsi: $jobDescription
            - Lokasi: ${worker.location}
            - Tarif: ${worker.getFormattedPrice()}/hari
            - Rating: ${String.format("%.1f", worker.rating.toFloat())} (${worker.ratingCount} ulasan)
            
            Kapan Anda bisa memulai pekerjaan ini?
            
            Terima kasih.
        """.trimIndent()
    }

    // Lifecycle method onResume - dipanggil saat activity kembali aktif
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