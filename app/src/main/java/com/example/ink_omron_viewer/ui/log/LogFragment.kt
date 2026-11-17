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

package com.example.ink_omron_viewer.ui.log

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ink_omron_viewer.databinding.FragmentLogBinding
import com.example.ink_omron_viewer.domain.LogViewModel
import com.example.ink_omron_viewer.domain.log.EmailConfig
import com.example.ink_omron_viewer.domain.DockConfigurationViewModel
import com.example.ink_omron_viewer.domain.log.FtpConfig
import com.example.ink_omron_viewer.ui.SharedViewModel
import com.example.ink_omron_viewer.ui.CommunicationViewModel

class LogFragment : Fragment() {

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!

    private val commsViewModel: CommunicationViewModel by activityViewModels()
    private val logViewModel: LogViewModel by activityViewModels()
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val dockConfigViewModel: DockConfigurationViewModel by activityViewModels()

    private lateinit var logAdapter: LogEntriesAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)

        setupRecyclerView()
        setupObservers()
        setupListeners()

        return binding.root
    }

    private fun setupRecyclerView() {
        logAdapter = LogEntriesAdapter()
        binding.recyclerLogEntries.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = logAdapter
        }
    }

    private fun setupObservers() {
        // Observar el objeto SystemLog completo
        logViewModel.systemLog.observe(viewLifecycleOwner) { systemLog ->
            if (systemLog != null) {
                // Configuración del PLC
                binding.textPlcModel.text = "Modelo: ${systemLog.plcConfig.model}"
                binding.textPlcIp.text = "IP: ${systemLog.plcConfig.ipAddress}"
                binding.textPlcOrder.text = "Nº Pedido: ${systemLog.plcConfig.orderNumber}"
                binding.textPlcDock.text = "Nº Muelle: ${systemLog.plcConfig.dockNumber}"
                binding.cardPlcConfig.visibility = View.VISIBLE

                // Estadísticas
                val stats = logViewModel.getStatistics()
                val activeMachinesCount = systemLog.dockConfig.machines.count { it.isActive }
                binding.textTotalEntries.text = "Total de entradas: ${systemLog.logEntries.size}"
                binding.textActiveMachines.text = "Máquinas activas: $activeMachinesCount"
                binding.cardStatistics.visibility = View.VISIBLE

                // Entradas de log
                if (systemLog.logEntries.isNotEmpty()) {
                    logAdapter.submitList(systemLog.logEntries.take(50))
                    binding.cardLogPreview.visibility = View.VISIBLE
                } else {
                    binding.cardLogPreview.visibility = View.GONE
                }

                binding.buttonGenerateCsv.isEnabled = true
            } else {
                // Ocultar vistas si no hay log
                binding.cardPlcConfig.visibility = View.GONE
                binding.cardStatistics.visibility = View.GONE
                binding.cardLogPreview.visibility = View.GONE
                binding.buttonGenerateCsv.isEnabled = false
            }
        }

        // Observar archivo generado
        logViewModel.generatedFile.observe(viewLifecycleOwner) { file ->
            if (file != null) {
                binding.buttonExportEmail.isEnabled = true
                binding.buttonExportFtp.isEnabled = true
                Toast.makeText(requireContext(), "Archivo CSV generado", Toast.LENGTH_SHORT).show()
            }
        }

        // Observar estado de carga
        logViewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            binding.buttonLoadLog.isEnabled = !isLoading
            binding.buttonGenerateCsv.isEnabled = !isLoading && logViewModel.systemLog.value != null
        }

        // Observar mensajes de estado
        logViewModel.toastMessage.observe(viewLifecycleOwner) { message ->
            if (message.isNotEmpty()) {
                binding.textStatus.text = message
                binding.textStatus.visibility = View.VISIBLE
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
                logViewModel.onToastShown()
            } else {
                binding.textStatus.visibility = View.GONE
            }
        }
    }

    private fun setupListeners() {
        // Cargar log desde el PLC
        binding.buttonLoadLog.setOnClickListener {
            logViewModel.loadSystemLog(commsViewModel, sharedViewModel, dockConfigViewModel)
        }

        // Generar archivo CSV
        binding.buttonGenerateCsv.setOnClickListener {
            logViewModel.generateCsvFile()
        }

        // Mostrar configuración de email
        binding.buttonExportEmail.setOnClickListener {
            toggleEmailConfig()
        }

        // Mostrar configuración de FTP
        binding.buttonExportFtp.setOnClickListener {
            toggleFtpConfig()
        }

        // Confirmar envío por email
        binding.buttonConfirmEmail.setOnClickListener {
            val recipient = binding.editEmailRecipient.text.toString().trim()
            if (recipient.isEmpty()) {
                Toast.makeText(requireContext(), "Ingrese un correo destinatario", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val subject = binding.editEmailSubject.text.toString().trim()
            val dockNumber = logViewModel.systemLog.value?.plcConfig?.dockNumber ?: 0
            val finalSubject = if (subject.isEmpty()) {
                "Log del Sistema - Muelle $dockNumber"
            } else {
                subject
            }

            val emailConfig = EmailConfig(
                recipientEmail = recipient,
                subject = finalSubject,
                body = "Adjunto encontrará el registro de actividad del muelle de carga."
            )

            logViewModel.exportByEmail(emailConfig)
            binding.cardEmailConfig.visibility = View.GONE
        }

        // Probar conexión FTP
        binding.buttonTestFtp.setOnClickListener {
            val ftpConfig = getFtpConfig()
            if (ftpConfig != null) {
                // logViewModel.testFtpConnection(ftpConfig) // Esta función no existe, comentar o implementar
            }
        }

        // Confirmar envío por FTP
        binding.buttonConfirmFtp.setOnClickListener {
            val ftpConfig = getFtpConfig()
            if (ftpConfig != null) {
                logViewModel.exportByFtp(ftpConfig)
                binding.cardFtpConfig.visibility = View.GONE
            }
        }
    }

    private fun toggleEmailConfig() {
        val currentVisibility = binding.cardEmailConfig.visibility
        binding.cardEmailConfig.visibility = if (currentVisibility == View.VISIBLE) {
            View.GONE
        } else {
            binding.cardFtpConfig.visibility = View.GONE
            View.VISIBLE
        }
    }

    private fun toggleFtpConfig() {
        val currentVisibility = binding.cardFtpConfig.visibility
        binding.cardFtpConfig.visibility = if (currentVisibility == View.VISIBLE) {
            View.GONE
        } else {
            binding.cardEmailConfig.visibility = View.GONE
            View.VISIBLE
        }
    }

    private fun getFtpConfig(): FtpConfig? {
        val host = binding.editFtpHost.text.toString().trim()
        val portStr = binding.editFtpPort.text.toString().trim()
        val username = binding.editFtpUsername.text.toString().trim()
        val password = binding.editFtpPassword.text.toString().trim()
        val remotePath = binding.editFtpPath.text.toString().trim()

        if (host.isEmpty()) {
            Toast.makeText(requireContext(), "Ingrese el servidor FTP", Toast.LENGTH_SHORT).show()
            return null
        }

        if (username.isEmpty()) {
            Toast.makeText(requireContext(), "Ingrese el usuario FTP", Toast.LENGTH_SHORT).show()
            return null
        }

        if (password.isEmpty()) {
            Toast.makeText(requireContext(), "Ingrese la contraseña FTP", Toast.LENGTH_SHORT).show()
            return null
        }

        val port = portStr.toIntOrNull() ?: 21

        return FtpConfig(
            host = host,
            port = port,
            username = username,
            password = password,
            remotePath = remotePath.ifEmpty { "/logs" }
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}