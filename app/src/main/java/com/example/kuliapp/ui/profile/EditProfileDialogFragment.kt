package com.example.kuliapp.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.example.kuliapp.databinding.DialogEditProfileBinding

class EditProfileDialogFragment(
    private val currentName: String,
    private val currentDomisili: String,
    private val currentPhone: String,
    private val currentHarga: String,
    private val currentDescription: String,
    private val onSave: (name: String, domisili: String, phone: String, harga: String, description: String) -> Unit
) : DialogFragment() {

    private var _binding: DialogEditProfileBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogEditProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set dialog width to match parent
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        // Isi dengan data saat ini
        binding.editTextName.setText(currentName)
        binding.editTextDomisili.setText(currentDomisili)
        binding.editTextPhone.setText(currentPhone)
        binding.editTextHarga.setText(currentHarga)
        binding.editTextDescription.setText(currentDescription)

        // Setup tombol
        binding.buttonSave.setOnClickListener {
            val name = binding.editTextName.text.toString().trim()
            val domisili = binding.editTextDomisili.text.toString().trim()
            val phone = binding.editTextPhone.text.toString().trim()
            val harga = binding.editTextHarga.text.toString().trim()
            val description = binding.editTextDescription.text.toString().trim()

            // Validasi input sederhana
            if (name.isEmpty() || domisili.isEmpty() || phone.isEmpty() || harga.isEmpty()) {
                binding.textErrorMessage.visibility = View.VISIBLE
                binding.textErrorMessage.text = "Semua kolom harus diisi"
                return@setOnClickListener
            }

            // Panggil callback
            onSave(name, domisili, phone, harga, description)
            dismiss()
        }

        binding.buttonCancel.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}