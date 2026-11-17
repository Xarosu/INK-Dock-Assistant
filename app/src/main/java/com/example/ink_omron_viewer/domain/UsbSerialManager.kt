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

package com.example.ink_omron_viewer.domain

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import androidx.core.content.ContextCompat
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import java.io.IOException

class UsbSerialManager(private val context: Context) {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private var serialPort: UsbSerialPort? = null
    private var connection: UsbDeviceConnection? = null

    fun findDrivers(): List<UsbSerialPort> {
        return UsbSerialProber.getDefaultProber().findAllDrivers(usbManager).map { it.ports }.flatten()
    }

    fun connect(port: UsbSerialPort, onResult: (Boolean) -> Unit) {
        if (!usbManager.hasPermission(port.driver.device)) {
            val permissionIntent = PendingIntent.getBroadcast(context, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    context.unregisterReceiver(this)
                    if (intent.action == ACTION_USB_PERMISSION) {
                        if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                            openConnection(port, onResult)
                        } else {
                            onResult(false)
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            usbManager.requestPermission(port.driver.device, permissionIntent)
        } else {
            openConnection(port, onResult)
        }
    }

    private fun openConnection(port: UsbSerialPort, onResult: (Boolean) -> Unit) {
        connection = usbManager.openDevice(port.driver.device)
        if (connection == null) {
            onResult(false)
            return
        }

        serialPort = port
        try {
            serialPort?.open(connection)
            serialPort?.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            onResult(true)
        } catch (e: IOException) {
            e.printStackTrace()
            close()
            onResult(false)
        }
    }

    fun close() {
        try {
            serialPort?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        serialPort = null
        connection = null
    }

    @Throws(IOException::class)
    fun write(data: ByteArray) {
        serialPort?.write(data, 2000)
    }

    @Throws(IOException::class)
    fun read(buffer: ByteArray): Int {
        return serialPort?.read(buffer, 2000) ?: 0
    }

    companion object {
        private const val ACTION_USB_PERMISSION = "com.example.ink_omron_viewer.USB_PERMISSION"
    }
}
