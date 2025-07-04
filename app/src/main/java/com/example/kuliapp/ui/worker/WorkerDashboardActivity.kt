package com.example.kuliapp.ui.worker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.kuliapp.R
import android.view.View
import com.example.kuliapp.databinding.ActivityWorkerDashboardBinding
import com.example.kuliapp.models.Worker
import com.example.kuliapp.ui.auth.LoginActivity
import com.example.kuliapp.ui.customer.CustomerDashboardActivity
import com.example.kuliapp.ui.customer.CustomerDashboardActivity.Companion
import com.example.kuliapp.ui.profile.EditProfileDialogFragment
import com.example.kuliapp.utils.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class WorkerDashboardActivity : AppCompatActivity() {

    // ===== DEKLARASI VARIABEL =====
    private lateinit var binding: ActivityWorkerDashboardBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage

    // Variabel untuk status dan data worker
    private var isAvailable: Boolean = false
    private var currentRating: Float = 0f
    private var totalReviews: Int = 0
    private var currentWorker: Worker? = null
    private var selectedImageUri: Uri? = null

    // ===== KONSTANTA =====
    companion object {
        private const val TAG = "WorkerDashboard"
        private const val COLLECTION_WORKERS = "workers"
        private const val COLLECTION_RATINGS = "ratings"
    }

    // ===== ACTIVITY RESULT LAUNCHER =====
    // Untuk menangani hasil dari pemilihan gambar profil
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            Log.d(TAG, "Gambar dipilih: $it")

            // Validasi ukuran file (optional, maksimal 5MB)
            try {
                val inputStream = contentResolver.openInputStream(it)
                val fileSize = inputStream?.available() ?: 0
                inputStream?.close()

                if (fileSize > 5 * 1024 * 1024) { // 5MB
                    Toast.makeText(this, "Ukuran file terlalu besar (maksimal 5MB)", Toast.LENGTH_SHORT).show()
                    return@let
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking file size", e)
            }

            // Langsung tampilkan preview gambar
            selectedImageUri = it
            binding.profileImage.setImageURI(it)

            // Upload ke Firebase
            uploadProfileImage(it)
        } ?: run {
            Log.w(TAG, "Tidak ada gambar yang dipilih")
            Toast.makeText(this, "Tidak ada gambar yang dipilih", Toast.LENGTH_SHORT).show()
        }
    }

    // ===== METODE UTAMA ACTIVITY =====
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkerDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inisialisasi Firebase dan komponen
        initializeFirebase()
        initializeComponents()

        // Setup UI dan fungsi
        setupSwipeRefresh()
        setupListeners()
        loadWorkerData()
    }

    // ===== METODE INISIALISASI =====
    // Inisialisasi komponen Firebase
    private fun initializeFirebase() {
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
    }

    // Inisialisasi komponen lainnya
    private fun initializeComponents() {
        preferenceManager = PreferenceManager(this)
    }

    // ===== METODE SWIPE REFRESH =====
    // Setup SwipeRefreshLayout untuk refresh data
    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshData()
        }

        // Customize warna indikator refresh
        binding.swipeRefreshLayout.setColorSchemeResources(
            R.color.primary,
            R.color.accent,
            R.color.primary_dark
        )
    }

    // Fungsi untuk refresh data dashboard
    private fun refreshData() {
        Log.d(TAG, "Refreshing dashboard data...")

        binding.swipeRefreshLayout.isRefreshing = true

        val totalLoaders = 1
        var loadingCount = 0

        val onLoadComplete = {
            loadingCount++
            if (loadingCount >= totalLoaders) {
                binding.swipeRefreshLayout.isRefreshing = false
                Toast.makeText(this, "Data berhasil diperbarui", Toast.LENGTH_SHORT).show()
            }
        }

        // Panggil loadWorkerData dengan callback
        loadWorkerData(onComplete = onLoadComplete)
    }

    // ===== METODE LOAD DATA =====
    // Fungsi utama untuk memuat data worker dari Firestore
    private fun loadWorkerData(onComplete: (() -> Unit)? = null) {
        val workerId = auth.currentUser?.uid
        if (workerId == null) {
            redirectToLogin()
            onComplete?.invoke()
            return
        }

        showLoading(true)

        firestore.collection(COLLECTION_WORKERS)
            .document(workerId)
            .get()
            .addOnSuccessListener { document ->
                showLoading(false)
                if (document.exists()) {
                    try {
                        // Coba deserialize data worker secara otomatis
                        currentWorker = document.toObject(Worker::class.java)
                        currentWorker?.let { worker ->
                            updateUI(worker)
                            loadRatingData(worker.workerId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deserializing worker data", e)
                        try {
                            // Fallback: mapping manual jika deserialize gagal
                            currentWorker = createWorkerFromDocument(document.data ?: emptyMap())
                            currentWorker?.let { worker ->
                                updateUI(worker)
                                loadRatingData(workerId)
                            }
                        } catch (manualException: Exception) {
                            Log.e(TAG, "Manual mapping also failed", manualException)
                            Toast.makeText(this, "Data profil rusak, membuat ulang...", Toast.LENGTH_SHORT).show()
                            createDefaultWorkerProfile(workerId)
                        }
                    }
                } else {
                    // Jika dokumen tidak ada, buat profil default
                    createDefaultWorkerProfile(workerId)
                }

                // Callback setelah berhasil/sudah selesai
                onComplete?.invoke()
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e(TAG, "Error loading worker data", exception)
                Toast.makeText(this, "Gagal memuat data profil", Toast.LENGTH_SHORT).show()

                // Callback meskipun gagal
                onComplete?.invoke()
            }
    }

    // ===== HELPER METHODS UNTUK DATA WORKER =====
    // Helper function untuk membuat Worker dari Map secara manual
    private fun createWorkerFromDocument(data: Map<String, Any>): Worker {
        return Worker(
            workerId = data["id"] as? String ?: "",
            name = data["name"] as? String ?: "",
            email = data["email"] as? String ?: "",
            phone = data["phone"] as? String ?: "",
            location = data["location"] as? String ?: "",
            experience = data["experience"] as? String ?: "",
            price = (data["price"] as? Number)?.toLong() ?: 0L,
            photo = data["photo"] as? String ?: "",
            rating = (((data["rating"] as? Number)?.toDouble() ?: 0.0f) as Float).toDouble(),
            ratingCount = (data["ratingCount"] as? Number)?.toInt() ?: 0,
            isAvailable = data["isAvailable"] as? Boolean ?: true,
            jobDate = data["jobDate"] as? String ?: "",
            jobDescription = data["jobDescription"] as? String ?: "",
            phoneNumber = data["phoneNumber"] as? String ?: (data["phone"] as? String ?: "")
        )
    }

    // Helper function untuk konversi timestamp
    private fun convertToTimestamp(value: Any?): Timestamp? {
        return when (value) {
            is Timestamp -> value
            is Long -> {
                // Jika Long adalah milliseconds
                if (value > 1000000000000L) { // > year 2001 in milliseconds
                    Timestamp(Date(value))
                } else {
                    // Jika Long adalah seconds
                    Timestamp(value, 0)
                }
            }
            is Date -> Timestamp(value)
            else -> null
        }
    }

    // Fungsi untuk membuat profil worker default
    private fun createDefaultWorkerProfile(workerId: String) {
        val userEmail = auth.currentUser?.email ?: ""
        val userName = userEmail.substringBefore("@").replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }

        val defaultWorker = Worker(
            workerId = workerId,
            name = userName,
            email = userEmail,
            phone = "",
            location = "",
            experience = "",
            price = 0,
            photo = "",
            rating = 0.0,
            ratingCount = 0,
            isAvailable = true,
            jobDate = "",
            jobDescription = "",
            phoneNumber = ""
        )

        firestore.collection(COLLECTION_WORKERS)
            .document(workerId)
            .set(defaultWorker)
            .addOnSuccessListener {
                currentWorker = defaultWorker
                updateUI(defaultWorker)
                Toast.makeText(this, "Profil pekerja berhasil dibuat", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error creating worker profile", exception)
                Toast.makeText(this, "Gagal membuat profil pekerja", Toast.LENGTH_SHORT).show()
            }
    }

    // ===== METODE UPDATE UI =====
    // Fungsi untuk mengupdate tampilan UI dengan data worker
    private fun updateUI(worker: Worker) {
        binding.textName.text = worker.name
        binding.textDomisili.text = worker.location
        binding.textPhone.text = worker.phone
        binding.textHarga.text = "Rp ${String.format("%,d", worker.price)}/hari"
        binding.textDescription.text = worker.experience

        isAvailable = worker.isAvailable
        updateAvailabilityStatus()

        // ===== TAMPILKAN RATING =====
        // Konversi rating ke float untuk RatingBar
        val ratingFloat = worker.rating.toFloat()
        binding.ratingBar.rating = ratingFloat

        // Nilai rating (misal: 4.2)
        binding.textRatingValue.text = String.format("%.1f", ratingFloat)

        // Jumlah ulasan (misal: (10 ulasan))
        val reviewCount = worker.ratingCount
        binding.textReviewCount.text = "($reviewCount ulasan)"

        // ===== LOAD FOTO PROFIL =====
        // Load foto profil dari field 'photo'
        if (worker.photo.isNotEmpty()) {
            Glide.with(this)
                .load(worker.photo)
                .placeholder(R.drawable.ic_profile_placeholder) // Gambar placeholder
                .error(R.drawable.ic_profile_placeholder) // Gambar jika error
                .circleCrop() // Jika ingin foto bulat
                .into(binding.profileImage)
        } else {
            // Jika tidak ada foto, tampilkan placeholder
            binding.profileImage.setImageResource(R.drawable.ic_profile_placeholder)
        }
    }


    // ===== METODE RATING =====
    // Fungsi untuk memuat data rating dari Firestore
    private fun loadRatingData(workerId: String) {
        Log.d("RatingDebug", "Mengambil rating untuk workerId = $workerId")
        firestore.collection(COLLECTION_RATINGS)
            .whereEqualTo("workerId", workerId)
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

                if (count > 0) {
                    val average = totalRating / count
                    updateWorkerRating(workerId, average, count)
                }
            }

    }

    // Fungsi untuk mengupdate rating worker di Firestore
    private fun updateWorkerRating(workerId: String, rating: Double, count: Int) {
        val updateMap = mapOf(
            "workerId" to workerId,
            "rating" to rating,
            "ratingCount" to count
        )

        firestore.collection(COLLECTION_RATINGS)
            .document(workerId)
            .set(updateMap, SetOptions.merge()) // <--- fix utama
            .addOnSuccessListener {
                Log.d("RatingUpdate", "Berhasil update rating")
                currentRating = rating.toFloat()
                totalReviews = count
                updateRatingDisplay() // update tampilan

                firestore.collection(COLLECTION_WORKERS)
                    .document(workerId)
                    .update(
                        mapOf(
                            "workerId" to workerId,
                            "rating" to rating,
                            "ratingCount" to count
                        )
                    )
                    .addOnSuccessListener {
                        Log.d("WorkerUpdate", "Berhasil update rating ke koleksi workers")
                    }
                    .addOnFailureListener {
                        Log.e("WorkerUpdate", "Gagal update rating ke koleksi workers", it)
                    }
            }
            .addOnFailureListener {
                Log.e("RatingUpdate", "Gagal update rating", it)
            }
    }



    // Fungsi untuk mengupdate tampilan rating di UI
    private fun updateRatingDisplay() {
        binding.ratingBar.rating = currentRating
        binding.textRatingValue.text = String.format("%.1f", currentRating)
        binding.textReviewCount.text = "($totalReviews ulasan)"
    }

    // ===== METODE SETUP LISTENERS =====
    // Setup semua event listener untuk UI components
    private fun setupListeners() {
        // Tombol edit profil
        binding.buttonEditProfile.setOnClickListener {
            currentWorker?.let { worker ->
                showEditProfileDialog(worker)
            }
        }

        // Switch untuk mengubah status ketersediaan
        binding.switchAvailability.setOnCheckedChangeListener { _, isChecked ->
            isAvailable = isChecked
            updateAvailabilityInFirebase(isChecked)
            updateAvailabilityStatus()
            val statusMessage = if (isChecked) "Anda sekarang dapat menerima pesanan" else "Anda tidak menerima pesanan"
            Snackbar.make(binding.root, statusMessage, Snackbar.LENGTH_SHORT).show()
        }

        // Klik pada foto profil untuk menggantinya
        binding.profileImage.setOnClickListener {
            pickImage.launch("image/*")
        }

        // Tombol logout
        binding.buttonLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }
    }


    // ===== METODE AVAILABILITY STATUS =====
    // Fungsi untuk mengupdate status ketersediaan di Firebase
    private fun updateAvailabilityInFirebase(isAvailable: Boolean) {
        val workerId = currentWorker?.workerId ?: return
        firestore.collection(COLLECTION_WORKERS)
            .document(workerId)
            .update(
                mapOf(
                    "isAvailable" to isAvailable,
                    "updatedAt" to Timestamp.now()
                )
            )
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error updating availability", exception)
                Toast.makeText(this, "Gagal mengubah status ketersediaan", Toast.LENGTH_SHORT).show()
            }
    }

    // Fungsi untuk mengupdate tampilan status ketersediaan
    private fun updateAvailabilityStatus() {
        binding.switchAvailability.isChecked = isAvailable
        if (isAvailable) {
            binding.textAvailabilityStatus.text = "Status: Tersedia"
            binding.textAvailabilityStatus.setTextColor(ContextCompat.getColor(this, R.color.available_green))
        } else {
            binding.textAvailabilityStatus.text = "Status: Tidak Tersedia"
            binding.textAvailabilityStatus.setTextColor(ContextCompat.getColor(this, R.color.unavailable_red))
        }
    }

    // ===== METODE DIALOG EDIT PROFIL =====
    // Fungsi untuk menampilkan dialog edit profil
    private fun showEditProfileDialog(worker: Worker) {
        val dialog = EditProfileDialogFragment(
            worker.name,
            worker.location,
            worker.phone,
            "Rp ${String.format("%,d", worker.price)}",
            worker.experience
        ) { name, location, phone, priceText, experience ->
            // Parse price dari text
            val price = priceText.replace("Rp ", "").replace(".", "").replace(",", "").toLongOrNull() ?: worker.price

            // Update worker object
            val updatedWorker = worker.copy(
                name = name,
                location = location,
                phone = phone,
                price = price,
                experience = experience,
                _updatedAt = Timestamp.now()
            )

            // Update ke Firebase
            updateWorkerProfile(updatedWorker)
        }
        dialog.show(supportFragmentManager, "EditProfileDialog")
    }

    // Fungsi untuk mengupdate profil worker ke Firebase
    private fun updateWorkerProfile(worker: Worker) {
        val workerId = auth.currentUser?.uid ?: return
        val updatedWorker = worker.copy(workerId = workerId) // pastikan konsisten

        showLoading(true)
        firestore.collection(COLLECTION_WORKERS)
            .document(workerId)
            .set(updatedWorker, SetOptions.merge()) // gunakan merge
            .addOnSuccessListener {
                showLoading(false)
                currentWorker = updatedWorker
                updateUI(updatedWorker)
                Snackbar.make(binding.root, "Profil berhasil diperbarui", Snackbar.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e(TAG, "Error updating worker profile", exception)
                Toast.makeText(this, "Gagal memperbarui profil", Toast.LENGTH_SHORT).show()
            }
    }


    // ===== METODE UPLOAD GAMBAR =====
    // Fungsi untuk mengupload foto profil ke Firebase Storage
    private fun uploadProfileImage(imageUri: Uri) {
        val workerId = auth.currentUser?.uid
        if (workerId == null) {
            Toast.makeText(this, "User tidak terautentikasi", Toast.LENGTH_SHORT).show()
            return
        }

        if (currentWorker == null) {
            Toast.makeText(this, "Data worker belum dimuat", Toast.LENGTH_SHORT).show()
            return
        }

        val storageRef = storage.reference
        val imageRef = storageRef.child("worker_photos/$workerId.jpg") // Folder khusus untuk worker photos

        showLoading(true)
        Log.d(TAG, "Mulai upload foto untuk workerId: $workerId")

        try {
            val inputStream = contentResolver.openInputStream(imageUri)
            if (inputStream != null) {
                imageRef.putStream(inputStream)
                    .addOnSuccessListener { taskSnapshot ->
                        Log.d(TAG, "Upload berhasil, mendapatkan download URL...")

                        imageRef.downloadUrl
                            .addOnSuccessListener { downloadUri ->
                                val photoUrl = downloadUri.toString()
                                Log.d(TAG, "Download URL berhasil didapat: $photoUrl")

                                // Update foto URL ke Firestore
                                updatePhotoUrlInFirestore(workerId, photoUrl)
                            }
                            .addOnFailureListener { exception ->
                                showLoading(false)
                                Log.e(TAG, "Gagal mendapatkan download URL", exception)
                                Toast.makeText(this, "Gagal mendapatkan URL foto: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener { exception ->
                        showLoading(false)
                        Log.e(TAG, "Gagal upload foto", exception)
                        Toast.makeText(this, "Gagal upload foto: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                    .addOnProgressListener { taskSnapshot ->
                        // Optional: Tampilkan progress upload
                        val progress = (100.0 * taskSnapshot.bytesTransferred / taskSnapshot.totalByteCount)
                        Log.d(TAG, "Upload progress: ${progress.toInt()}%")
                    }
            } else {
                showLoading(false)
                Toast.makeText(this, "Gagal membaca file gambar", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            showLoading(false)
            Log.e(TAG, "Error saat membaca file", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }


    // Fungsi terpisah untuk update URL foto di Firestore
    private fun updatePhotoUrlInFirestore(workerId: String, photoUrl: String) {
        Log.d(TAG, "Menyimpan photo URL ke Firestore: $photoUrl")

        val updateData = hashMapOf<String, Any>(
            "photo" to photoUrl,
            "updatedAt" to Timestamp.now()
        )

        firestore.collection(COLLECTION_WORKERS)
            .document(workerId)
            .update(updateData)
            .addOnSuccessListener {
                showLoading(false)
                Log.d(TAG, "Photo URL berhasil disimpan ke field 'photo' di Firestore")

                // Update currentWorker object agar sinkron
                currentWorker = currentWorker?.copy(photo = photoUrl)

                // Update UI dengan foto baru
                loadImageIntoImageView(photoUrl)

                Toast.makeText(this, "Foto profil berhasil diperbarui", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e(TAG, "Gagal menyimpan photo URL ke Firestore", exception)
                Toast.makeText(this, "Gagal menyimpan foto: ${exception.message}", Toast.LENGTH_SHORT).show()

                // Kembalikan gambar ke kondisi semula jika gagal
                currentWorker?.let { worker ->
                    if (worker.photo.isNotEmpty()) {
                        loadImageIntoImageView(worker.photo)
                    } else {
                        binding.profileImage.setImageResource(R.drawable.ic_profile_placeholder)
                    }
                }
            }
    }

    // Fungsi untuk load image ke ImageView
    private fun loadImageIntoImageView(photoUrl: String) {
        Log.d(TAG, "Loading image from URL: $photoUrl")

        Glide.with(this)
            .load(photoUrl)
            .placeholder(R.drawable.ic_profile_placeholder)
            .error(R.drawable.ic_profile_placeholder)
            .circleCrop()
            .into(binding.profileImage)
    }

    // 5. Tambahkan fungsi untuk menghapus foto (optional)
    private fun deleteProfilePhoto() {
        val workerId = auth.currentUser?.uid ?: return

        MaterialAlertDialogBuilder(this)
            .setTitle("Hapus Foto Profil")
            .setMessage("Apakah Anda yakin ingin menghapus foto profil?")
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Hapus") { _, _ ->
                showLoading(true)

                // Hapus dari Storage
                val storageRef = storage.reference.child("worker_photos/$workerId.jpg")
                storageRef.delete()
                    .addOnSuccessListener {
                        // Hapus URL dari Firestore
                        firestore.collection(COLLECTION_WORKERS)
                            .document(workerId)
                            .update("photo", "")
                            .addOnSuccessListener {
                                showLoading(false)
                                currentWorker = currentWorker?.copy(photo = "")
                                binding.profileImage.setImageResource(R.drawable.ic_profile_placeholder)
                                Toast.makeText(this, "Foto profil berhasil dihapus", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener {
                                showLoading(false)
                                Toast.makeText(this, "Gagal menghapus data foto", Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        showLoading(false)
                        Toast.makeText(this, "Gagal menghapus foto", Toast.LENGTH_SHORT).show()
                    }
            }
            .show()
    }

    // ===== METODE DIALOG LOGOUT =====
    // Fungsi untuk menampilkan dialog konfirmasi logout
    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Keluar")
            .setMessage("Apakah Anda yakin ingin keluar dari aplikasi?")
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Keluar") { _, _ ->
                // Clear preferences dan logout
                preferenceManager.clearAllPreferences()
                auth.signOut()

                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                intent.putExtra("force_logout", true)

                startActivity(intent)
                finish()
            }
            .show()
    }

    // ===== METODE NAVIGASI =====
    // Fungsi untuk redirect ke halaman login
    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ===== METODE LOADING INDICATOR =====
    // Fungsi untuk menampilkan/menyembunyikan loading indicator
    private fun showLoading(show: Boolean) {
        binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
        binding.profileImage.isEnabled = !show // Disable saat upload
    }
}