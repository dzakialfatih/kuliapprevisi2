package com.example.kuliapp.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast

object WhatsappUtils {
    /**
     * Opens WhatsApp chat with the given phone number
     *
     * @param context The context to use for starting the activity
     * @param phoneNumber The phone number to chat with (should include country code, e.g. "62812345678")
     * @param message Optional message to pre-fill in the chat
     */
    fun openWhatsAppChat(context: Context, phoneNumber: String, message: String = "") {
        try {
            // Ensure phone number is properly formatted (remove spaces, '+' etc)
            val formattedNumber = phoneNumber.replace("\\s+".toRegex(), "")
                .replace("+", "")

            // Create the WhatsApp URI
            val uri = Uri.parse(
                "https://api.whatsapp.com/send?phone=$formattedNumber&text=${Uri.encode(message)}"
            )

            // Create and start the intent
            val intent = Intent(Intent.ACTION_VIEW, uri)

            // Check if WhatsApp is installed
            val packageManager = context.packageManager
            if (intent.resolveActivity(packageManager) != null) {
                context.startActivity(intent)
            } else {
                // WhatsApp not installed, open in browser instead
                val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                context.startActivity(browserIntent)

                // Show a toast message
                Toast.makeText(
                    context,
                    "WhatsApp tidak terinstall. Membuka di browser.",
                    Toast.LENGTH_LONG
                ).show()
            }
        } catch (e: Exception) {
            // Handle any exceptions
            Toast.makeText(
                context,
                "Gagal membuka WhatsApp: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}