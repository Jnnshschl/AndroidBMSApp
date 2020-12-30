package de.jnns.bmsmonitor

import android.os.Bundle
import android.view.View
import androidx.navigation.findNavController
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import de.jnns.bmsmonitor.bluetooth.BleManager

@ExperimentalUnsignedTypes
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btPreference = findPreference<ListPreference>("macAddress") as ListPreference
        val btPreferenceBike = findPreference<ListPreference>("macAddressBike") as ListPreference

        val bleNames = BleManager.i.getBleNames()
        bleNames.add("None")
        btPreference.entries = bleNames.toTypedArray()
        btPreferenceBike.entries = bleNames.toTypedArray()

        val bleAddresses = BleManager.i.getBleAddresses()
        bleAddresses.add("0")
        btPreference.entryValues = bleAddresses.toTypedArray()
        btPreferenceBike.entryValues = bleAddresses.toTypedArray()
    }
}