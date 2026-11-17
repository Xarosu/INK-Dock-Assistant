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

package com.example.ink_omron_viewer.ui.usb

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.hoho.android.usbserial.driver.UsbSerialPort

class UsbDeviceAdapter(private var devices: List<UsbSerialPort>, private val onDeviceClick: (UsbSerialPort) -> Unit) :
    RecyclerView.Adapter<UsbDeviceAdapter.UsbDeviceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsbDeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
        return UsbDeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsbDeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device)
        holder.itemView.setOnClickListener { onDeviceClick(device) }
    }

    override fun getItemCount(): Int = devices.size

    @SuppressLint("NotifyDataSetChanged")
    fun updateDevices(newDevices: List<UsbSerialPort>) {
        devices = newDevices
        notifyDataSetChanged() // This is simple, but consider DiffUtil for performance
    }

    class UsbDeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val text1: TextView = itemView.findViewById(android.R.id.text1)
        private val text2: TextView = itemView.findViewById(android.R.id.text2)

        fun bind(port: UsbSerialPort) {
            val device = port.driver.device
            text1.text = device.deviceName
            text2.text = "Vendor ID: ${device.vendorId}, Product ID: ${device.productId}"
        }
    }
}
