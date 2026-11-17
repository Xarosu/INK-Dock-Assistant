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

import android.annotation.SuppressLint
import android.content.res.Resources
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ink_omron_viewer.R
import com.example.ink_omron_viewer.domain.log.LogEntry

class LogEntriesAdapter : ListAdapter<LogEntry, LogEntriesAdapter.LogEntryViewHolder>(LogEntryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LogEntryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_log_entry, parent, false)
        return LogEntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: LogEntryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class LogEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val textViewTimestamp: TextView = itemView.findViewById(R.id.textViewTimestamp)
        private val textViewDescription: TextView = itemView.findViewById(R.id.textViewDescription)
        private val textViewDetails: TextView = itemView.findViewById(R.id.textViewDetails)

        // Lazy initialization: El mapa se construye automáticamente desde los recursos
        // Solo se calcula una vez y se reutiliza para todas las entradas del log
        private val codeToIndexMap: Map<Int, Int> by lazy {
            val codes = itemView.resources.getIntArray(R.array.event_codes)
            codes.mapIndexed { index, code -> code to index }.toMap()
        }

        @SuppressLint("SetTextI18n")
        fun bind(entry: LogEntry) {
            textViewTimestamp.text = entry.getFormattedTimestamp()
            textViewDescription.text = getDescriptionForEventCode(itemView.resources, entry.eventCode)
            textViewDetails.text = "Código: ${entry.eventCode}"
        }

        private fun getDescriptionForEventCode(resources: Resources, code: Int): String {
            val descriptions = resources.getStringArray(R.array.event_descriptions)
            val index = codeToIndexMap[code] ?: -1
            return descriptions.getOrNull(index) ?: "Evento desconocido (Código: $code)"
        }
    }

    class LogEntryDiffCallback : DiffUtil.ItemCallback<LogEntry>() {
        override fun areItemsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            // Suponiendo que la combinación de timestamp y código es única
            return oldItem.timestamp == newItem.timestamp && oldItem.eventCode == newItem.eventCode
        }

        override fun areContentsTheSame(oldItem: LogEntry, newItem: LogEntry): Boolean {
            return oldItem == newItem
        }
    }
}
