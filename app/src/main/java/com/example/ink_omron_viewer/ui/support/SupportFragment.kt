/*
 * INK OMRON Viewer - Monitorización y control de muelles de carga.
 * Copyright (C) 2026 X. Rodriguez <xarosu@gmail.com>
 *
 * Este programa es software libre: puedes redistribuirlo y/o modificarlo
 * bajo los términos de la Licencia Pública General GNU publicada por la
 * Free Software Foundation, ya sea la versión 3 de la Licencia, o
 * cualquier versión posterior.
 *
 * Este programa se distribuye con la esperanza de que sea útil, pero
 * SIN GARANTÍA ALGUNA; ni siquiera la garantía implícita de
 * MERCANTIBILIDAD o APTITUD PARA UN PROPÓSITO DETERMINADO.
 * Consulte la Licencia Pública General GNU para más detalles.
 *
 * Debería haber recibido una copia de la Licencia Pública General GNU
 * junto con este programa. Si no es así, consulte <https://www.gnu.org/licenses/>.
 *
 */

package com.example.ink_omron_viewer.ui.support

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.example.ink_omron_viewer.R
import com.example.ink_omron_viewer.databinding.FragmentSupportBinding
import com.example.ink_omron_viewer.util.LanguageManager

class SupportFragment : Fragment() {

    private var _binding: FragmentSupportBinding? = null
    private val binding get() = _binding!!
    private val supportViewModel: SupportViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSupportBinding.inflate(inflater, container, false)
        val root: View = binding.root

        setupContactCard()
        setupSupportCard()
        setupLanguageSelector()

        return root
    }

    private fun setupContactCard() {
        // Email
        supportViewModel.contactEmail.observe(viewLifecycleOwner) { emailAddress ->
            binding.textViewContactEmail.text = getString(R.string.support_email_label, emailAddress)
            binding.textViewContactEmail.setOnClickListener {
                openUrl("mailto:$emailAddress", R.string.support_no_email_app)
            }
        }

        // Phone
        supportViewModel.contactPhone.observe(viewLifecycleOwner) { phoneNumber ->
            binding.textViewContactPhone.isVisible = phoneNumber.isNotBlank()
            binding.textViewContactPhone.text = getString(R.string.support_phone_label, phoneNumber)
            binding.textViewContactPhone.setOnClickListener {
                openUrl("tel:$phoneNumber", R.string.support_no_phone_app)
            }
        }
    }

    private fun setupSupportCard() {
        // Email
        supportViewModel.supportEmail.observe(viewLifecycleOwner) { emailAddress ->
            binding.textViewSupportEmail.text = getString(R.string.support_email_label, emailAddress)
            binding.textViewSupportEmail.setOnClickListener {
                openUrl("mailto:$emailAddress", R.string.support_no_email_app)
            }
        }

        // Phone
        supportViewModel.supportPhone.observe(viewLifecycleOwner) { phoneNumber ->
            binding.textViewSupportPhone.isVisible = phoneNumber.isNotBlank()
            binding.textViewSupportPhone.text = getString(R.string.support_phone_label, phoneNumber)
            binding.textViewSupportPhone.setOnClickListener {
                openUrl("tel:$phoneNumber", R.string.support_no_phone_app)
            }
        }

        // WhatsApp
        supportViewModel.supportWhatsapp.observe(viewLifecycleOwner) { whatsappNumber ->
            supportViewModel.supportWhatsappDisplay.observe(viewLifecycleOwner) { whatsappDisplay ->
                binding.textViewSupportWhatsapp.isVisible = whatsappNumber.isNotBlank()
                binding.textViewSupportWhatsapp.text = getString(R.string.support_whatsapp_label, whatsappDisplay)
                binding.textViewSupportWhatsapp.setOnClickListener {
                    val url = "https://api.whatsapp.com/send?phone=$whatsappNumber"
                    openUrl(url, R.string.support_no_whatsapp_app)
                }
            }
        }
    }

    private fun setupLanguageSelector() {
        val currentLanguage = LanguageManager.getLanguage(requireContext())

        // Establecer el radio button seleccionado según el idioma actual
        when (currentLanguage) {
            LanguageManager.LANGUAGE_SYSTEM -> binding.radioButtonSystem.isChecked = true
            LanguageManager.LANGUAGE_SPANISH -> binding.radioButtonSpanish.isChecked = true
            LanguageManager.LANGUAGE_ENGLISH -> binding.radioButtonEnglish.isChecked = true
            LanguageManager.LANGUAGE_FRENCH -> binding.radioButtonFrench.isChecked = true
        }

        // Listener para cambios de idioma
        binding.radioGroupLanguage.setOnCheckedChangeListener { _, checkedId ->
            val newLanguage = when (checkedId) {
                R.id.radioButtonSystem -> LanguageManager.LANGUAGE_SYSTEM
                R.id.radioButtonSpanish -> LanguageManager.LANGUAGE_SPANISH
                R.id.radioButtonEnglish -> LanguageManager.LANGUAGE_ENGLISH
                R.id.radioButtonFrench -> LanguageManager.LANGUAGE_FRENCH
                else -> LanguageManager.LANGUAGE_SYSTEM
            }

            // Si el idioma cambió, guardar y mostrar diálogo de reinicio
            if (newLanguage != currentLanguage) {
                LanguageManager.setLanguage(requireContext(), newLanguage)
                showRestartDialog()
            }
        }
    }

    private fun showRestartDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.language_selector_title)
            .setMessage(R.string.language_restart_required)
            .setPositiveButton(R.string.ok) { _, _ ->
                // Reiniciar la actividad para aplicar el nuevo idioma
                requireActivity().recreate()
            }
            .setCancelable(false)
            .show()
    }

    private fun openUrl(url: String, errorMsgResId: Int) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            if (url.startsWith("mailto:")) {
                intent.action = Intent.ACTION_SENDTO
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(requireContext(), errorMsgResId, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
