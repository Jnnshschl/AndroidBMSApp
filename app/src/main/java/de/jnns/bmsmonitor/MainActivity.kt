package de.jnns.bmsmonitor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.navigation.findNavController
import kotlinx.android.synthetic.main.activity_main.*

@ExperimentalUnsignedTypes
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.page_battery -> {
                    title = getString(R.string.app_name)
                    findNavController(R.id.nav_host_fragment).navigate(R.id.action_settingsFragment_to_batteryFragment)
                    true
                }
                R.id.page_settings -> {
                    title = getString(R.string.appNameSettings)
                    findNavController(R.id.nav_host_fragment).navigate(R.id.action_batteryFragment_to_settingsFragment)
                    true
                }
                else -> false
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 4711)
        } else {
            Intent(this, BmsService::class.java).also { intent -> startService(intent) }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when (requestCode) {
            4711 -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    Intent(this, BmsService::class.java).also { intent ->
                        startService(intent)
                    }
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean = findNavController(R.id.nav_host_fragment).navigateUp()
}