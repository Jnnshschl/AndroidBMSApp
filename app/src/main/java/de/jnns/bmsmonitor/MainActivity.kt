package de.jnns.bmsmonitor

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.android.synthetic.main.activity_main.*

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

        Intent(this, BmsService::class.java).also { intent ->
            startService(intent)
        }
    }

    override fun onSupportNavigateUp(): Boolean = findNavController(R.id.nav_host_fragment).navigateUp()
}