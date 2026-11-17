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

package com.example.ink_omron_viewer.ui.configuration

import android.app.Dialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.ink_omron_viewer.R
import com.example.ink_omron_viewer.databinding.DialogUsbDeviceSelectionBinding
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialProber

/**
 * Diálogo para seleccionar un dispositivo USB Serial de la lista de dispositivos conectados.
 * Maneja automáticamente la solicitud de permisos USB cuando es necesario.
 */
class UsbDeviceSelectionDialog : DialogFragment() {

    private var _binding: DialogUsbDeviceSelectionBinding? = null
    private val binding get() = _binding!!

    private var devices: List<UsbSerialDriver> = emptyList()
    private var onDeviceSelected: ((UsbSerialDriver) -> Unit)? = null
    private lateinit var usbManager: UsbManager

    private val ACTION_USB_PERMISSION = "com.example.ink_omron_viewer.USB_PERMISSION"

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ACTION_USB_PERMISSION == intent.action) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        device?.let { usbDevice ->
                            Log.i(TAG, "USB permission granted for device: ${usbDevice.deviceName}")
                            // Encontrar el driver correspondiente
                            val driver = devices.find { it.device.deviceId == usbDevice.deviceId }
                            driver?.let {
                                onDeviceSelected?.invoke(it)
                                dismiss()
                            }
                        }
                    } else {
                        Log.w(TAG, "USB permission denied for device: ${device?.deviceName}")
                    }
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogUsbDeviceSelectionBinding.inflate(layoutInflater)

        // Obtener dispositivos USB Serial disponibles
        usbManager = requireContext().getSystemService(Context.USB_SERVICE) as UsbManager
        devices = UsbSerialProber.getDefaultProber().findAllDrivers(usbManager)

        if (devices.isEmpty()) {
            // No hay dispositivos conectados
            return AlertDialog.Builder(requireContext())
                .setTitle(R.string.usb_select_device_title)
                .setMessage(R.string.usb_no_devices_found)
                .setPositiveButton(R.string.ok) { _, _ -> dismiss() }
                .create()
        }

        // Configurar adaptador para la lista con información más detallada
        val deviceNames = devices.map { driver ->
            val device = driver.device
            val manufacturer = device.manufacturerName ?: "Unknown"
            val product = device.productName ?: "USB Device"
            val vid = String.format("VID:0x%04X", device.vendorId)
            val pid = String.format("PID:0x%04X", device.productId)
            val hasPermission = if (usbManager.hasPermission(device)) "✓" else "⚠"
            "$hasPermission $manufacturer $product\n$vid $pid"
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            deviceNames
        )

        binding.listViewDevices.adapter = adapter
        binding.listViewDevices.setOnItemClickListener { _, _, position, _ ->
            handleDeviceSelection(devices[position])
        }

        // Registrar el receiver para permisos USB
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(usbPermissionReceiver, filter)
        }

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.usb_select_device_title)
            .setView(binding.root)
            .setNegativeButton(R.string.cancel) { _, _ -> dismiss() }
            .create()
    }

    /**
     * Maneja la selección de un dispositivo, solicitando permisos si es necesario.
     */
    private fun handleDeviceSelection(driver: UsbSerialDriver) {
        val device = driver.device

        if (usbManager.hasPermission(device)) {
            // Ya tenemos permiso, proceder con la conexión
            Log.i(TAG, "Permission already granted for device: ${device.deviceName}")
            onDeviceSelected?.invoke(driver)
            dismiss()
        } else {
            // Solicitar permiso
            Log.i(TAG, "Requesting USB permission for device: ${device.deviceName}")
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
            val permissionIntent = PendingIntent.getBroadcast(
                requireContext(),
                0,
                Intent(ACTION_USB_PERMISSION),
                flags
            )
            usbManager.requestPermission(device, permissionIntent)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            requireContext().unregisterReceiver(usbPermissionReceiver)
        } catch (e: Exception) {
            // Puede que ya esté unregistered
            Log.w(TAG, "Error unregistering USB permission receiver", e)
        }
        _binding = null
    }

    companion object {
        private const val TAG = "UsbDeviceSelectionDialog"

        fun newInstance(onDeviceSelected: (UsbSerialDriver) -> Unit): UsbDeviceSelectionDialog {
            return UsbDeviceSelectionDialog().apply {
                this.onDeviceSelected = onDeviceSelected
            }
        }
    }
}
