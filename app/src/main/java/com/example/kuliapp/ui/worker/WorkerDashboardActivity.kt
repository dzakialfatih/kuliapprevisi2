package com.example.kuliapp.ui.worker

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.kuliapp.R
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
import com.google.firebase.storage.FirebaseStorage
import java.util.*

class WorkerDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWorkerDashboardBinding
    private lateinit var preferenceManager: PreferenceManager
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private lateinit var storage: FirebaseStorage

    private var isAvailable: Boolean = false
    private var currentRating: Float = 0f
    private var totalReviews: Int = 0
    private var currentWorker: Worker? = null
    private var selectedImageUri: Uri? = null

    companion object {
        private const val TAG = "WorkerDashboard"
        private const val COLLECTION_WORKERS = "workers"
        private const val COLLECTION_RATINGS = "ratings"
    }

    // Untuk menangani hasil dari pemilihan gambar
    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            binding.profileImage.setImageURI(it)
            uploadProfileImage(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWorkerDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        storage = FirebaseStorage.getInstance()
        preferenceManager = PreferenceManager(this)

        // Setup UI
        setupSwipeRefresh()
        setupListeners()
        loadWorkerData()
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

        // 🔁 Panggil dengan callback
        loadWorkerData(onComplete = onLoadComplete)
    }


    private fun loadWorkerData(onComplete: (() -> Unit)? = null) {
        val userId = auth.currentUser?.uid
        if (userId == null) {
            redirectToLogin()
            onComplete?.invoke()
            return
        }

        showLoading(true)

        firestore.collection(COLLECTION_WORKERS)
            .document(userId)
            .get()
            .addOnSuccessListener { document ->
                showLoading(false)
                if (document.exists()) {
                    try {
                        currentWorker = document.toObject(Worker::class.java)
                        currentWorker?.let { worker ->
                            updateUI(worker)
                            loadRatingData(userId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error deserializing worker data", e)
                        try {
                            currentWorker = createWorkerFromDocument(document.data ?: emptyMap())
                            currentWorker?.let { worker ->
                                updateUI(worker)
                                loadRatingData(userId)
                            }
                        } catch (manualException: Exception) {
                            Log.e(TAG, "Manual mapping also failed", manualException)
                            Toast.makeText(this, "Data profil rusak, membuat ulang...", Toast.LENGTH_SHORT).show()
                            createDefaultWorkerProfile(userId)
                        }
                    }
                } else {
                    createDefaultWorkerProfile(userId)
                }

                // ✅ Callback setelah berhasil/sudah selesai
                onComplete?.invoke()
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e(TAG, "Error loading worker data", exception)
                Toast.makeText(this, "Gagal memuat data profil", Toast.LENGTH_SHORT).show()

                // ✅ Callback meskipun gagal
                onComplete?.invoke()
            }
    }


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
            rating = ((data["rating"] as? Number)?.toDouble() ?: 0.0f) as Float,
            ratingCount = (data["ratingCount"] as? Number)?.toInt() ?: 0,
            isAvailable = data["isAvailable"] as? Boolean ?: true,
//            createdAt = convertToTimestamp(data["createdAt"]),
//            updatedAt = convertToTimestamp(data["updatedAt"]),
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

    private fun createDefaultWorkerProfile(userId: String) {
        val userEmail = auth.currentUser?.email ?: ""
        val userName = userEmail.substringBefore("@").replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }

        val defaultWorker = Worker(
            workerId = userId,
            name = userName,
            email = userEmail,
            phone = "",
            location = "",
            experience = "",
            price = 0,
            photo = "",
            rating = 0.0F,
            ratingCount = 0,
            isAvailable = true,
//            createdAt = Timestamp.now(), // Gunakan Timestamp.now()
//            updatedAt = Timestamp.now(), // Gunakan Timestamp.now()
            jobDate = "",
            jobDescription = "",
            phoneNumber = ""
        )

        firestore.collection(COLLECTION_WORKERS)
            .document(userId)
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

    private fun updateUI(worker: Worker) {
        binding.textName.text = worker.name
        binding.textDomisili.text = worker.location
        binding.textPhone.text = worker.phone
        binding.textHarga.text = "Rp ${String.format("%,d", worker.price)}/hari"
        binding.textDescription.text = worker.experience

        isAvailable = worker.isAvailable
        updateAvailabilityStatus()

        // Load profile image if exists
        if (worker.photo.isNotEmpty()) {
            // Menggunakan library seperti Glide atau Picasso untuk load image
            // Glide.with(this).load(worker.photo).into(binding.profileImage)
        }
    }

    private fun loadRatingData(workerId: String) {
        firestore.collection(COLLECTION_RATINGS)
            .whereEqualTo("workerId", workerId)
            .get()
            .addOnSuccessListener { documents ->
                var totalRating = 0.0
                var count = 0

                for (document in documents) {
                    val rating = document.getDouble("rating") ?: 0.0
                    totalRating += rating
                    count++
                }

                currentRating = if (count > 0) (totalRating / count).toFloat() else 0f
                totalReviews = count
                updateRatingDisplay()

                // Update rating di document worker
                updateWorkerRating(workerId, currentRating.toDouble(), count)
            }
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error loading rating data", exception)
            }
    }

    private fun updateWorkerRating(workerId: String, rating: Double, count: Int) {
        firestore.collection(COLLECTION_WORKERS)
            .document(workerId)
            .update(
                mapOf(
                    "rating" to rating,
                    "ratingCount" to count,
                    "updatedAt" to Timestamp.now() // Gunakan Timestamp.now()
                )
            )
    }

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

    private fun updateAvailabilityInFirebase(isAvailable: Boolean) {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection(COLLECTION_WORKERS)
            .document(userId)
            .update(
                mapOf(
                    "isAvailable" to isAvailable,
                    "updatedAt" to Timestamp.now() // Gunakan Timestamp.now()
                )
            )
            .addOnFailureListener { exception ->
                Log.e(TAG, "Error updating availability", exception)
                Toast.makeText(this, "Gagal mengubah status ketersediaan", Toast.LENGTH_SHORT).show()
            }
    }

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

    private fun updateRatingDisplay() {
        binding.ratingBar.rating = currentRating
        binding.textRatingValue.text = String.format("%.1f", currentRating)
        binding.textReviewCount.text = "($totalReviews ulasan)"
    }

    private fun showEditProfileDialog(worker: Worker) {
        val dialog = EditProfileDialogFragment(
            worker.name,
            worker.location,
            worker.phone,
            "Rp ${String.format("%,d", worker.price)}",
            worker.experience
        ) { name, location, phone, priceText, experience ->
            // Parse price from text
            val price = priceText.replace("Rp ", "").replace(".", "").replace(",", "").toLongOrNull() ?: worker.price

            // Update worker object
            val updatedWorker = worker.copy(
                name = name,
                location = location,
                phone = phone,
                price = price,
                experience = experience,
                _updatedAt = Timestamp.now() // Gunakan Timestamp.now()
            )

            // Update to Firebase
            updateWorkerProfile(updatedWorker)
        }
        dialog.show(supportFragmentManager, "EditProfileDialog")
    }

    private fun updateWorkerProfile(worker: Worker) {
        val userId = auth.currentUser?.uid ?: return

        showLoading(true)
        firestore.collection(COLLECTION_WORKERS)
            .document(userId)
            .set(worker)
            .addOnSuccessListener {
                showLoading(false)
                currentWorker = worker
                updateUI(worker)
                Snackbar.make(binding.root, "Profil berhasil diperbarui", Snackbar.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e(TAG, "Error updating worker profile", exception)
                Toast.makeText(this, "Gagal memperbarui profil", Toast.LENGTH_SHORT).show()
            }
    }

    private fun uploadProfileImage(imageUri: Uri) {
        val userId = auth.currentUser?.uid ?: return
        val imageRef = storage.reference.child("profile_images/${userId}.jpg")

        showLoading(true)
        imageRef.putFile(imageUri)
            .addOnSuccessListener { taskSnapshot ->
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    // Update photo URL in Firestore
                    firestore.collection(COLLECTION_WORKERS)
                        .document(userId)
                        .update(
                            mapOf(
                                "photo" to downloadUri.toString(),
                                "updatedAt" to Timestamp.now() // Gunakan Timestamp.now()
                            )
                        )
                        .addOnSuccessListener {
                            showLoading(false)
                            currentWorker = currentWorker?.copy(photo = downloadUri.toString())
                            Snackbar.make(binding.root, "Foto profil berhasil diubah", Snackbar.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener { exception ->
                            showLoading(false)
                            Log.e(TAG, "Error updating photo URL", exception)
                            Toast.makeText(this, "Gagal menyimpan URL foto", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { exception ->
                showLoading(false)
                Log.e(TAG, "Error uploading image", exception)
                Toast.makeText(this, "Gagal mengupload foto", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Keluar")
            .setMessage("Apakah Anda yakin ingin keluar dari aplikasi?")
            .setNegativeButton("Batal") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Keluar") { _, _ ->
                // Clear preferences and logout
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

    private fun redirectToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showLoading(show: Boolean) {
        // Implement loading indicator
        // binding.progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }
}

