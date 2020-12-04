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
import androidx.preference.PreferenceManager
import com.github.anastr.speedviewlib.components.Section
import com.google.gson.Gson
import de.jnns.bmsmonitor.data.BatteryData
import kotlinx.android.synthetic.main.fragment_battery.*
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.pow
import kotlin.math.roundToInt


@ExperimentalUnsignedTypes
class BatteryFragment : Fragment() {
    // no need to refresh data in the background
    private var isInForeground = false

    // used to create the cell bars
    private lateinit var cellBars: ArrayList<ProgressBar>
    private var cellBarsInitialized = false

    // time calculation smoothing
    // default 5 min
    private var smoothCount = 60 * 5
    private var smoothIndex = 0
    private var wattValues = FloatArray(smoothCount)

    private var minCellVoltage = 4200
    private var maxCellVoltage = 3000

    private val mMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            try {
                val msg: String = intent.getStringExtra("batteryData")

                if (labelStatus != null) {
                    labelStatus.text = String.format(resources.getString(R.string.connectedToBms), intent.getStringExtra("deviceName"))
                }

                if (msg.isNotEmpty()) {
                    updateUi(Gson().fromJson(msg, BatteryData::class.java))
                }
            } catch (ex: Exception) {
                // ignored
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mMessageReceiver, IntentFilter("bmsDataIntent"))
        return inflater.inflate(R.layout.fragment_battery, container, false)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(requireContext()).unregisterReceiver(mMessageReceiver)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        speedViewPower.clearSections()
        speedViewPower.addSections(
            Section(0.00000000f, 0.11111111f, ContextCompat.getColor(requireContext(), R.color.batteryChargeHigh), 72.0f),
            Section(0.11111111f, 0.22222222f, ContextCompat.getColor(requireContext(), R.color.batteryChargeMedium), 72.0f),
            Section(0.22222222f, 0.33333333f, ContextCompat.getColor(requireContext(), R.color.batteryChargeLow), 72.0f),
            Section(0.33333333f, 0.44444444f, ContextCompat.getColor(requireContext(), R.color.batteryDischargeLow), 72.0f),
            Section(0.44444444f, 0.66666666f, ContextCompat.getColor(requireContext(), R.color.batteryDischargeMedium), 72.0f),
            Section(0.66666666f, 0.88888888f, ContextCompat.getColor(requireContext(), R.color.batteryDischargeHigh), 72.0f),
            Section(0.88888888f, 1.00000000f, ContextCompat.getColor(requireContext(), R.color.batteryDischargeHighest), 72.0f)
        )

        speedViewPower.speedTextTypeface = resources.getFont(R.font.aldrich)
        speedViewPower.textTypeface = resources.getFont(R.font.aldrich)

        minCellVoltage = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("minCellVoltage", "1000")!!.toInt()
        maxCellVoltage = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("maxCellVoltage", "1000")!!.toInt()
    }

    override fun onResume() {
        super.onResume()

        isInForeground = true
        cellBarsInitialized = false

        speedViewPower.speedTo(0.0f, 0)
        labelStatus.text = getString(R.string.waitForBms)
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
            if (!cellBarsInitialized) {
                initCellBars(batteryData.cellCount)
            }

            // Power Gauge
            speedViewPower.speedTo(batteryData.power * -1.0f, 1000)

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

            labelTime.text = String.format("%02d:%02d", remainingHours.roundToInt(), remainingMinutes.roundToInt())

            // Cell Bars
            for (i in 0 until batteryData.cellCount) {
                cellBars[i].progress = maxCellVoltage - (batteryData.cellVoltages[i] * 1000.0f).roundToInt()

                val progress = ((batteryData.cellVoltages[i] - (minCellVoltage / 1000.0f)) / ((maxCellVoltage / 1000.0f) - (minCellVoltage / 1000.0f))) * 100.0f
                uiColorCapacityProgressBar(progress, cellBars[i])
            }

            // Row 1
            labelVoltage.text = roundTo(batteryData.voltage, 1).toString()

            val totalPercentage = ((batteryData.percentage) * 1000.0f).roundToInt() / 10.0f

            labelPercentage.text = totalPercentage.toString()
            uiColorCapacityTextView(totalPercentage, labelPercentage)

            uiBatteryCapacityBar(totalPercentage)

            // Row 2
            labelCurrent.text = String.format(Locale.US, "%.2f", roundTo(batteryData.current, 2))
            uiColorEnergyTextView(batteryData.current, labelCurrent)

            labelPower.text = batteryData.watthours.roundToInt().toString()
            uiColorCapacityTextView(totalPercentage, labelPower)

            // Row 3
            labelTemperature.text = roundTo(batteryData.avgTemperature, 1).toString()
            uiColorTemperatureTextView(batteryData.avgTemperature, labelTemperature)

            labelTemperatureMax.text = roundTo(batteryData.maxTemperature, 1).toString()
            uiColorTemperatureTextView(batteryData.maxTemperature, labelTemperatureMax)
        }
    }

    private fun initCellBars(cellCount: Int) {
        cellBarsInitialized = true
        cellBars = ArrayList(cellCount)

        for (i in 0 until cellCount) {
            val progressBar = ProgressBar(requireContext(), null, android.R.attr.progressBarStyleHorizontal)

            progressBar.scaleY = 2.0f
            progressBar.scaleX = 1.8f
            progressBar.rotation = 270.0f
            progressBar.min = 0
            progressBar.max = maxCellVoltage - minCellVoltage
            progressBar.layoutParams = ViewGroup.LayoutParams(layoutCells.width / cellCount, 20)

            cellBars.add(progressBar)
            layoutCells.addView(cellBars[i])
        }
    }

    private fun uiColorEnergyTextView(value: Float, uiElement: TextView) {
        when {
            value > 0.0f -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.primaryLightGreen))
            }
            value == 0.0f -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
            }
            else -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.primaryLightRed))
            }
        }
    }

    private fun uiColorCapacityTextView(value: Float, uiElement: TextView) {
        // 80% skipped here because battery only gets charged to ~80 percent
        when {
            value < 20 -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.percentUnder20))
            }
            value < 40 -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.percentUnder40))
            }
            value < 70 -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.percentUnder70))
            }
            else -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
            }
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

    private fun uiColorTemperatureTextView(value: Float, uiElement: TextView) {
        when {
            value < 0.0f -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.temperatureColdest))
            }
            value < 6.0f -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.temperatureColder))
            }
            value < 10.0f -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.temperatureCold))
            }
            value > 16.0f -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.temperatureHot))
            }
            value > 24.0f -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.temperatureHotter))
            }
            value > 36.0f -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.temperatureHottest))
            }
            else -> {
                uiElement.setTextColor(ContextCompat.getColor(requireContext(), R.color.temperatureNeutral))
            }
        }
    }

    private fun uiBatteryCapacityBar(value: Float) {
        when {
            value == 0.0f -> {
                progressBarBattery1.progress = 0
                progressBarBattery2.progress = 0
                progressBarBattery3.progress = 0
                progressBarBattery4.progress = 0
                progressBarBattery5.progress = 0
            }
            value < 20.0f -> {
                val newValue = value * 5.0f
                uiColorCapacityProgressBar(newValue, progressBarBattery1)
                progressBarBattery1.progress = newValue.roundToInt()
                progressBarBattery2.progress = 0
                progressBarBattery3.progress = 0
                progressBarBattery4.progress = 0
                progressBarBattery5.progress = 0
            }
            value < 40.0f -> {
                progressBarBattery1.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery1.progress = 100
                val newValue = (value - 20.0f) * 5.0f
                uiColorCapacityProgressBar(newValue, progressBarBattery2)
                progressBarBattery2.progress = newValue.roundToInt()
                progressBarBattery3.progress = 0
                progressBarBattery4.progress = 0
                progressBarBattery5.progress = 0
            }
            value < 60.0f -> {
                progressBarBattery1.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery1.progress = 100
                progressBarBattery2.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery2.progress = 100
                val newValue = (value - 40.0f) * 5.0f
                uiColorCapacityProgressBar(newValue, progressBarBattery3)
                progressBarBattery3.progress = newValue.roundToInt()
                progressBarBattery4.progress = 0
                progressBarBattery5.progress = 0
            }
            value < 80.0f -> {
                progressBarBattery1.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery1.progress = 100
                progressBarBattery2.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery2.progress = 100
                progressBarBattery3.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery3.progress = 100
                val newValue = (value - 60.0f) * 5.0f
                uiColorCapacityProgressBar(newValue, progressBarBattery4)
                progressBarBattery4.progress = newValue.roundToInt()
                progressBarBattery5.progress = 0
            }
            value < 100.0f -> {
                progressBarBattery1.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery1.progress = 100
                progressBarBattery2.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery2.progress = 100
                progressBarBattery3.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery3.progress = 100
                progressBarBattery4.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery4.progress = 100
                val newValue = (value - 80.0f) * 5.0f
                uiColorCapacityProgressBar(newValue, progressBarBattery5)
                progressBarBattery5.progress = newValue.roundToInt()
            }
            else -> {
                progressBarBattery1.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery1.progress = 100
                progressBarBattery2.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery2.progress = 100
                progressBarBattery3.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery3.progress = 100
                progressBarBattery4.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery4.progress = 100
                progressBarBattery5.progressTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.percentUnder100))
                progressBarBattery5.progress = 100
            }
        }
    }

    private fun roundTo(value: Float, decimals: Int = 0): Float {
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