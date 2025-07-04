package com.example.kuliapp.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.kuliapp.databinding.DialogEditProfileBinding

// Dialog Fragment untuk mengedit profil pengguna
class EditProfileDialogFragment(
    private val currentName: String,
    private val currentDomisili: String,
    private val currentPhone: String,
    private val currentHarga: String,
    private val currentDescription: String,
    private val onSave: (name: String, domisili: String, phone: String, harga: String, description: String) -> Unit
) : DialogFragment() {

    // Binding untuk layout dialog
    private var _binding: DialogEditProfileBinding? = null
    private val binding get() = _binding!!

    // Membuat tampilan dialog
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    // Setup tampilan dan listener setelah view dibuat
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Mengatur ukuran dialog
        setupDialogSize()

        // Mengisi field dengan data saat ini
        populateCurrentData()

        // Setup event listener untuk tombol-tombol
        setupButtonListeners()
    }

    // Mengatur ukuran dialog agar sesuai dengan layar
    private fun setupDialogSize() {
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // Mengisi field input dengan data profil saat ini
    private fun populateCurrentData() {
        binding.editTextName.setText(currentName)
        binding.editTextDomisili.setText(currentDomisili)
        binding.editTextPhone.setText(currentPhone)
        binding.editTextHarga.setText(currentHarga)
        binding.editTextDescription.setText(currentDescription)
    }

    // Setup listener untuk tombol Save dan Cancel
    private fun setupButtonListeners() {
        // Tombol Simpan - menyimpan perubahan profil
        binding.buttonSave.setOnClickListener {
            handleSaveButtonClick()
        }

        // Tombol Batal - menutup dialog tanpa menyimpan
        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
    }

    // Menangani klik tombol simpan
    private fun handleSaveButtonClick() {
        // Mengambil data input dari field
        val name = binding.editTextName.text.toString().trim()
        val domisili = binding.editTextDomisili.text.toString().trim()
        val phone = binding.editTextPhone.text.toString().trim()
        val harga = binding.editTextHarga.text.toString().trim()
        val description = binding.editTextDescription.text.toString().trim()

        // Validasi input - memastikan field wajib tidak kosong
        if (name.isEmpty() || domisili.isEmpty() || phone.isEmpty() || harga.isEmpty()) {
            showErrorMessage("Semua kolom harus diisi")
            return
        }

        // Menyimpan data dan menutup dialog
        onSave(name, domisili, phone, harga, description)
        dismiss()
    }

    // Menampilkan pesan error
    private fun showErrorMessage(message: String) {
        binding.textErrorMessage.visibility = View.VISIBLE
        binding.textErrorMessage.text = message
    }

    // Membersihkan binding saat view dihancurkan
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}