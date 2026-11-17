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

package com.example.ink_omron_viewer.domain.log

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File

/**
 * Exportador de logs por email
 * Utiliza un Intent para abrir el cliente de email del usuario
 */
class EmailExporter(private val context: Context) {

    companion object {
        private const val TAG = "EmailExporter"
        private const val FILE_PROVIDER_AUTHORITY = "com.example.ink_omron_viewer.fileprovider"
    }

    /**
     * Envía el archivo CSV por email usando un Intent
     * @param file Archivo CSV a enviar
     * @param config Configuración del email (destinatario, asunto, cuerpo)
     * @return true si se pudo abrir el cliente de email, false en caso contrario
     */
    fun sendByEmail(file: File, config: EmailConfig): Boolean {
        return try {
            // Obtener URI del archivo usando FileProvider
            val uri: Uri = FileProvider.getUriForFile(
                context,
                FILE_PROVIDER_AUTHORITY,
                file
            )

            // Crear Intent para enviar email
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(config.recipientEmail))
                putExtra(Intent.EXTRA_SUBJECT, config.subject)
                putExtra(Intent.EXTRA_TEXT, config.body)
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            // Verificar si hay alguna app que pueda manejar el Intent
            val packageManager = context.packageManager
            if (emailIntent.resolveActivity(packageManager) != null) {
                // Crear chooser para que el usuario seleccione la app de email
                val chooserIntent = Intent.createChooser(emailIntent, "Enviar log por email")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooserIntent)

                Log.i(TAG, "Intent de email abierto exitosamente")
                true
            } else {
                Log.w(TAG, "No hay aplicaciones de email disponibles")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando email", e)
            false
        }
    }

    /**
     * Envía múltiples archivos por email
     */
    fun sendMultipleByEmail(files: List<File>, config: EmailConfig): Boolean {
        return try {
            val uris = ArrayList<Uri>()

            files.forEach { file ->
                val uri = FileProvider.getUriForFile(
                    context,
                    FILE_PROVIDER_AUTHORITY,
                    file
                )
                uris.add(uri)
            }

            val emailIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(config.recipientEmail))
                putExtra(Intent.EXTRA_SUBJECT, config.subject)
                putExtra(Intent.EXTRA_TEXT, config.body)
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            val packageManager = context.packageManager
            if (emailIntent.resolveActivity(packageManager) != null) {
                val chooserIntent = Intent.createChooser(emailIntent, "Enviar logs por email")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooserIntent)

                Log.i(TAG, "Intent de email múltiple abierto exitosamente")
                true
            } else {
                Log.w(TAG, "No hay aplicaciones de email disponibles")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error enviando múltiples archivos por email", e)
            false
        }
    }

    /**
     * Verifica si hay aplicaciones de email disponibles
     */
    fun isEmailAvailable(): Boolean {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
        }
        val packageManager = context.packageManager
        return intent.resolveActivity(packageManager) != null
    }
}