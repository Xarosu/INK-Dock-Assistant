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

package com.example.ink_omron_viewer.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * LanguageManager - Gestor de idiomas de la aplicación
 *
 * Funcionalidades:
 * - Detección automática del idioma del dispositivo
 * - Cambio manual de idioma
 * - Persistencia de la selección en SharedPreferences
 * - Aplicación del idioma al Context de la app
 *
 * Idiomas soportados:
 * - Español (es) - por defecto
 * - Inglés (en)
 * - Francés (fr)
 */
object LanguageManager {

    private const val PREFS_NAME = "language_settings"
    private const val KEY_LANGUAGE = "selected_language"

    // Idiomas soportados
    const val LANGUAGE_SPANISH = "es"
    const val LANGUAGE_ENGLISH = "en"
    const val LANGUAGE_FRENCH = "fr"
    const val LANGUAGE_SYSTEM = "system" // Usar idioma del sistema

    /**
     * Obtiene el idioma actualmente seleccionado
     * @return Código de idioma (es, en, fr, system)
     */
    fun getLanguage(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, LANGUAGE_SYSTEM) ?: LANGUAGE_SYSTEM
    }

    /**
     * Guarda el idioma seleccionado
     * @param language Código de idioma (es, en, fr, system)
     */
    fun setLanguage(context: Context, language: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, language).apply()
    }

    /**
     * Aplica el idioma seleccionado al Context
     * @return Context con el idioma aplicado
     */
    fun applyLanguage(context: Context): Context {
        val language = getLanguage(context)

        // Si es "system", usar idioma del dispositivo (Android se encarga automáticamente)
        if (language == LANGUAGE_SYSTEM) {
            return context
        }

        // Aplicar idioma manual
        val locale = Locale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    /**
     * Obtiene el idioma actual del dispositivo
     * @return Código de idioma del sistema
     */
    fun getSystemLanguage(context: Context): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0].language
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale.language
        }
    }

    /**
     * Verifica si el idioma del sistema está soportado
     * @return true si es español, inglés o francés
     */
    fun isSystemLanguageSupported(context: Context): Boolean {
        val systemLang = getSystemLanguage(context)
        return systemLang in listOf(LANGUAGE_SPANISH, LANGUAGE_ENGLISH, LANGUAGE_FRENCH)
    }

    /**
     * Obtiene el nombre localizado del idioma
     * @param languageCode Código del idioma (es, en, fr)
     * @return Nombre del idioma en su propio idioma
     */
    fun getLanguageName(languageCode: String): String {
        return when (languageCode) {
            LANGUAGE_SPANISH -> "Español"
            LANGUAGE_ENGLISH -> "English"
            LANGUAGE_FRENCH -> "Français"
            LANGUAGE_SYSTEM -> "System / Sistema / Système"
            else -> languageCode
        }
    }

    /**
     * Obtiene una lista de idiomas disponibles
     * @return Lista de pares (código, nombre)
     */
    fun getAvailableLanguages(): List<Pair<String, String>> {
        return listOf(
            LANGUAGE_SYSTEM to "System / Sistema / Système",
            LANGUAGE_SPANISH to "Español",
            LANGUAGE_ENGLISH to "English",
            LANGUAGE_FRENCH to "Français"
        )
    }
}
