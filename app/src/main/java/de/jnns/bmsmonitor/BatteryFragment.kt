package de.jnns.bmsmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import androidx.preference.PreferenceManager
import com.github.anastr.speedviewlib.components.Section
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.DefaultValueFormatter
import com.google.gson.Gson
import de.jnns.bmsmonitor.data.BatteryData
import de.jnns.bmsmonitor.databinding.FragmentBatteryBinding
import java.util.*
import kotlin.math.pow
import kotlin.math.roundToInt


@ExperimentalUnsignedTypes
class BatteryFragment : Fragment() {
    private var _binding: FragmentBatteryBinding? = null
    private val binding get() = _binding!!

    // no need to refresh data in the background
    private var isInForeground = false

    // time calculation smoothing
    // default 5 min
    private var smoothCount = 60 * 5
    private var smoothIndex = 0
    private var wattValues = FloatArray(smoothCount)

    private var minCellVoltage = 0
    private var maxCellVoltage = 0

    private val mMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val msg: String = intent.getStringExtra("batteryData")!!

                binding.labelStatus.text = String.format(resources.getString(R.string.connectedToBms), intent.getStringExtra("deviceName"))

                if (msg.isNotEmpty()) {
                    updateUi(Gson().fromJson(msg, BatteryData::class.java))
                }
            } catch (ex: Exception) {
                // ignored
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mMessageReceiver, IntentFilter("bmsDataIntent"))
        _binding = FragmentBatteryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mMessageReceiver)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as MainActivity).binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.page_bike -> {
                    requireActivity().title = getString(R.string.app_name)
                    requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.action_batteryFragment_to_bikeFragment)
                    true
                }
                R.id.page_settings -> {
                    requireActivity().title = getString(R.string.appNameSettings)
                    requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.action_batteryFragment_to_settingsFragment)
                    true
                }
                R.id.page_stats -> {
                    requireActivity().title = getString(R.string.appNameStats)
                    requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.action_batteryFragment_to_statsFragment)
                    true
                }
                else -> false
            }
        }

        binding.speedViewSpeed.clearSections()
        binding.speedViewSpeed.addSections(
            Section(0.00000000f, 0.11111111f, ContextCompat.getColor(requireContext(), R.color.batteryChargeHigh), 72.0f),
            Section(0.11111111f, 0.22222222f, ContextCompat.getColor(requireContext(), R.color.batteryChargeMedium), 72.0f),
            Section(0.22222222f, 0.33333333f, ContextCompat.getColor(requireContext(), R.color.batteryChargeLow), 72.0f),
            Section(0.33333333f, 0.44444444f, ContextCompat.getColor(requireContext(), R.color.batteryDischargeLow), 72.0f),
            Section(0.44444444f, 0.66666666f, ContextCompat.getColor(requireContext(), R.color.batteryDischargeMedium), 72.0f),
            Section(0.66666666f, 0.88888888f, ContextCompat.getColor(requireContext(), R.color.batteryDischargeHigh), 72.0f),
            Section(0.88888888f, 1.00000000f, ContextCompat.getColor(requireContext(), R.color.batteryDischargeHighest), 72.0f)
        )

        binding.speedViewSpeed.minSpeed = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("minPower", "-500")!!.toFloat()
        binding.speedViewSpeed.maxSpeed = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("maxPower", "1000")!!.toFloat()

        minCellVoltage = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("minCellVoltage", "2500")!!.toInt()
        maxCellVoltage = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("maxCellVoltage", "4200")!!.toInt()

        binding.barchartCells.setNoDataTextColor(requireActivity().getColor(R.color.white))
        binding.barchartCells.setNoDataText("...")

        binding.barchartCells.setPinchZoom(false)
        binding.barchartCells.setTouchEnabled(false)
        binding.barchartCells.isClickable = false
        binding.barchartCells.isDoubleTapToZoomEnabled = false

        binding.barchartCells.setDrawBorders(false)
        binding.barchartCells.setDrawValueAboveBar(true)
        binding.barchartCells.setDrawBorders(false)
        binding.barchartCells.setDrawGridBackground(false)

        binding.barchartCells.description.isEnabled = false
        binding.barchartCells.legend.isEnabled = false

        binding.barchartCells.axisLeft.setDrawGridLines(false)
        binding.barchartCells.axisLeft.setDrawLabels(false)
        binding.barchartCells.axisLeft.setDrawAxisLine(false)

        binding.barchartCells.xAxis.setDrawGridLines(false)
        binding.barchartCells.xAxis.setDrawLabels(false)
        binding.barchartCells.xAxis.setDrawAxisLine(false)

        binding.barchartCells.axisRight.isEnabled = false
    }

    override fun onResume() {
        super.onResume()

        isInForeground = true

        binding.speedViewSpeed.speedTo(0.0f, 0)
        binding.labelStatus.text = getString(R.string.waitForBms)
    }

    override fun onPause() {
        super.onPause()
        isInForeground = false
    }

    private fun updateUi(batteryData: BatteryData) {
        if (!isInForeground) {
            return
        }

        requireActivity().runOnUiThread {
            // Power Gauge
            val powerUsage = batteryData.power * -1.0f

            binding.speedViewSpeed.speedTo(powerUsage, 1000)

            if (powerUsage < 0.0f) {
                binding.labelPower.text = "+${powerUsage.roundToInt() * -1}"
            } else {
                binding.labelPower.text = powerUsage.roundToInt().toString()
            }

            // Remaining Time
            wattValues[smoothIndex] = batteryData.power
            ++smoothIndex

            if (smoothIndex == smoothCount) {
                smoothIndex = 0
            }

            var averagePower = wattValues.filter { x -> x != 0.0f }.average().toFloat()

            if (averagePower < 0.0f) {
                averagePower *= -1
            }

            val wattSeconds: Float = when {
                batteryData.power < 0.0f -> {
                    (batteryData.watthours / averagePower) * 3600
                }
                batteryData.power > 0.0f -> {
                    ((batteryData.totalWatthours - batteryData.watthours) / averagePower) * 3600
                }
                else -> {
                    0.0f
                }
            }

            if (batteryData.power == 0.0f) {
                wattValues.fill(0.0f)
            }

            val remainingMinutes = (wattSeconds % 3600) / 60
            val remainingHours = (wattSeconds - remainingMinutes) / 3600

            binding.labelTime.text = String.format("%02d:%02d", remainingHours.roundToInt(), remainingMinutes.roundToInt())

            // Cell Bar-Diagram
            val cellBars = ArrayList<BarEntry>()

            for ((i, cell) in batteryData.cellVoltages.withIndex()) {
                cellBars.add(BarEntry(i.toFloat(), cell))
            }

            val barDataSetVoltage = BarDataSet(cellBars, "Cell Voltages")
            barDataSetVoltage.valueTextColor = requireActivity().getColor(R.color.white)
            barDataSetVoltage.valueTextSize = 12.0f
            barDataSetVoltage.valueFormatter = DefaultValueFormatter(2)
            barDataSetVoltage.setColors(requireActivity().getColor(R.color.primary))

            val barData = BarData(barDataSetVoltage)
            binding.barchartCells.data = barData
            binding.barchartCells.invalidate()

            // Row 1
            val totalPercentage = ((batteryData.percentage) * 1000.0f).roundToInt() / 10.0f

            binding.labelVoltage.text = roundTo(batteryData.voltage, 1).toString()
            binding.labelPercentage.text = totalPercentage.toString()

            uiBatteryCapacityBar(totalPercentage)

            // Row 2
            binding.labelCurrent.text = String.format(Locale.US, "%.1f", roundTo(batteryData.current, 1))
            binding.labelCapacityWh.text = batteryData.watthours.roundToInt().toString()

            // Row 3
            binding.labelTemperature.text = roundTo(batteryData.avgTemperature, 1).toString()
            binding.labelTemperatureMax.text = roundTo(batteryData.maxTemperature, 1).toString()
        }
    }

    private fun uiColorCapacityProgressBar(value: Float, uiElement: ProgressBar) {
        // 80% skipped here because battery only gets charged to ~80 percent
        when {
            value < 20 -> {
                uiElement.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder20))
            }
            value < 40 -> {
                uiElement.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder40))
            }
            value < 70 -> {
                uiElement.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder70))
            }
            else -> {
                uiElement.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
            }
        }
    }

    private fun uiBatteryCapacityBar(value: Float) {
        when {
            value == 0.0f -> {
                binding.progressBarBattery1.progress = 0
                binding.progressBarBattery2.progress = 0
                binding.progressBarBattery3.progress = 0
                binding.progressBarBattery4.progress = 0
                binding.progressBarBattery5.progress = 0
            }
            value < 20.0f -> {
                val newValue = value * 5.0f
                uiColorCapacityProgressBar(newValue, binding.progressBarBattery1)
                binding.progressBarBattery1.progress = newValue.roundToInt()
                binding.progressBarBattery2.progress = 0
                binding.progressBarBattery3.progress = 0
                binding.progressBarBattery4.progress = 0
                binding.progressBarBattery5.progress = 0
            }
            value < 40.0f -> {
                binding.progressBarBattery1.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery1.progress = 100
                val newValue = (value - 20.0f) * 5.0f
                uiColorCapacityProgressBar(newValue, binding.progressBarBattery2)
                binding.progressBarBattery2.progress = newValue.roundToInt()
                binding.progressBarBattery3.progress = 0
                binding.progressBarBattery4.progress = 0
                binding.progressBarBattery5.progress = 0
            }
            value < 60.0f -> {
                binding.progressBarBattery1.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery1.progress = 100
                binding.progressBarBattery2.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery2.progress = 100
                val newValue = (value - 40.0f) * 5.0f
                uiColorCapacityProgressBar(newValue, binding.progressBarBattery3)
                binding.progressBarBattery3.progress = newValue.roundToInt()
                binding.progressBarBattery4.progress = 0
                binding.progressBarBattery5.progress = 0
            }
            value < 80.0f -> {
                binding.progressBarBattery1.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery1.progress = 100
                binding.progressBarBattery2.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery2.progress = 100
                binding.progressBarBattery3.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery3.progress = 100
                val newValue = (value - 60.0f) * 5.0f
                uiColorCapacityProgressBar(newValue, binding.progressBarBattery4)
                binding.progressBarBattery4.progress = newValue.roundToInt()
                binding.progressBarBattery5.progress = 0
            }
            value < 100.0f -> {
                binding.progressBarBattery1.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery1.progress = 100
                binding.progressBarBattery2.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery2.progress = 100
                binding.progressBarBattery3.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery3.progress = 100
                binding.progressBarBattery4.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery4.progress = 100
                val newValue = (value - 80.0f) * 5.0f
                uiColorCapacityProgressBar(newValue, binding.progressBarBattery5)
                binding.progressBarBattery5.progress = newValue.roundToInt()
            }
            else -> {
                binding.progressBarBattery1.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery1.progress = 100
                binding.progressBarBattery2.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery2.progress = 100
                binding.progressBarBattery3.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery3.progress = 100
                binding.progressBarBattery4.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery4.progress = 100
                binding.progressBarBattery5.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                binding.progressBarBattery5.progress = 100
            }
        }
    }

    private fun roundTo(value: Float, decimals: Int): Float {
        if (value.isNaN() || value.isInfinite()) {
            return 0.0f
        }

        if (decimals == 0) {
            return value.roundToInt().toFloat()
        }

        val factor = 10.0.pow(decimals.toDouble()).toFloat()
        return (value * factor).roundToInt() / factor
    }
}