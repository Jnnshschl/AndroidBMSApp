package de.jnns.bmsmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import com.github.anastr.speedviewlib.components.Section
import com.google.gson.Gson
import de.jnns.bmsmonitor.data.BikeData
import de.jnns.bmsmonitor.databinding.FragmentBikeBinding

@ExperimentalUnsignedTypes
class BikeFragment : Fragment() {
    private var _binding: FragmentBikeBinding? = null
    private val binding get() = _binding!!

    private val mMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val msg: String = intent.getStringExtra("bikeData")!!

                binding.labelStatus.text = String.format(resources.getString(R.string.connectedToBike), intent.getStringExtra("deviceName"))

                if (msg.isNotEmpty()) {
                    updateUi(Gson().fromJson(msg, BikeData::class.java))
                }
            } catch (ex: Exception) {
                // ignored
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mMessageReceiver, IntentFilter("bikeDataIntent"))
        _binding = FragmentBikeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as MainActivity).binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.page_battery -> {
                    requireActivity().title = getString(R.string.app_name)
                    requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.action_bikeFragment_to_batteryFragment)
                    true
                }
                R.id.page_settings -> {
                    requireActivity().title = getString(R.string.appNameSettings)
                    requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.action_bikeFragment_to_settingsFragment)
                    true
                }
                R.id.page_stats -> {
                    requireActivity().title = getString(R.string.appNameStats)
                    requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.action_bikeFragment_to_statsFragment)
                    true
                }
                else -> false
            }
        }

        binding.speedViewSpeed.clearSections()
        binding.speedViewSpeed.addSections(
            Section(0.0f, 0.2f, ContextCompat.getColor(requireContext(), R.color.white), 72.0f),
            Section(0.2f, 0.4f, ContextCompat.getColor(requireContext(), R.color.batteryDischargeLow), 72.0f),
            Section(0.4f, 0.6f, ContextCompat.getColor(requireContext(), R.color.batteryDischargeMedium), 72.0f),
            Section(0.6f, 0.8f, ContextCompat.getColor(requireContext(), R.color.batteryDischargeHigh), 72.0f),
            Section(0.8f, 1.0f, ContextCompat.getColor(requireContext(), R.color.batteryDischargeHighest), 72.0f)
        )

        binding.speedViewSpeed.maxSpeed = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("maxSpeed", "35")!!.toFloat()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }

    private fun updateUi(bikeData: BikeData) {
        requireActivity().runOnUiThread {
            binding.speedViewSpeed.speedTo(bikeData.speed.toFloat())

            binding.labelSpeed.text = bikeData.speed.toString()
            binding.labelAssistLevel.text = bikeData.assistLevel.toString()
        }
    }
}