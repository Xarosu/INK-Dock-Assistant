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

package com.example.ink_omron_viewer

import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.view.Menu
import androidx.activity.viewModels
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.navigation.NavigationView
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import androidx.drawerlayout.widget.DrawerLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.ink_omron_viewer.databinding.ActivityMainBinding
import com.example.ink_omron_viewer.ui.SharedViewModel
import com.example.ink_omron_viewer.domain.ActiveMachinesViewModel
import com.example.ink_omron_viewer.util.LanguageManager

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding
    private val sharedViewModel: SharedViewModel by viewModels()
    private val activeMachinesViewModel: ActiveMachinesViewModel by viewModels()

    override fun attachBaseContext(newBase: Context) {
        // Aplicar idioma seleccionado antes de crear la actividad
        super.attachBaseContext(LanguageManager.applyLanguage(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.appBarMain.toolbar)

        val drawerLayout: DrawerLayout = binding.drawerLayout
        val navView: NavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.nav_home,
                R.id.nav_synoptic,
                R.id.nav_configuration,
                R.id.nav_support,
                R.id.nav_dock_configuration,
                R.id.nav_shelter_config,
                R.id.nav_door_config,
                R.id.nav_platform_config,
                R.id.nav_traffic_light_config
            ), drawerLayout
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Observar estado de máquinas activas para mostrar/ocultar items del menú
        activeMachinesViewModel.activeMachinesState.observe(this) { state ->
            val menu = navView.menu

            // Ocultar/mostrar items individuales según máquinas activas
            menu.findItem(R.id.nav_shelter_config)?.isVisible = state.isShelterActive
            menu.findItem(R.id.nav_door_config)?.isVisible = state.isDoorActive
            menu.findItem(R.id.nav_platform_config)?.isVisible = state.isPlatformActive
            menu.findItem(R.id.nav_traffic_light_config)?.isVisible = state.isTrafficLightActive

            // Ocultar el grupo completo si no hay máquinas activas
            menu.findItem(R.id.nav_group_active_machines)?.isVisible = state.isAnyMachineActive
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        // menuInflater.inflate(R.menu.main, menu)
        return false
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration) || super.onSupportNavigateUp()
    }
}
