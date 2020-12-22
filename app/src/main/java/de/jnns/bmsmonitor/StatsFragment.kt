package de.jnns.bmsmonitor

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.findNavController
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.DefaultValueFormatter
import de.jnns.bmsmonitor.data.BatteryData
import de.jnns.bmsmonitor.databinding.FragmentStatsBinding
import io.realm.Realm
import io.realm.Sort
import io.realm.kotlin.where


@ExperimentalUnsignedTypes
class StatsFragment : Fragment() {
    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private var selectedBatteryIndex = -1

    private val mMessageReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            updateUi()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        LocalBroadcastManager.getInstance(requireContext()).registerReceiver(mMessageReceiver, IntentFilter("bmsDataIntent"))
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        (requireActivity() as MainActivity).binding.bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.page_battery -> {
                    requireActivity().title = getString(R.string.appNameSettings)
                    requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.action_statsFragment_to_batteryFragment)
                    true
                }
                R.id.page_bike -> {
                    requireActivity().title = getString(R.string.app_name)
                    requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.action_statsFragment_to_bikeFragment)
                    true
                }
                R.id.page_settings -> {
                    requireActivity().title = getString(R.string.appNameStats)
                    requireActivity().findNavController(R.id.nav_host_fragment).navigate(R.id.action_statsFragment_to_settingsFragment)
                    true
                }
                else -> false
            }
        }

        configureLineChart(binding.linechartVoltage, 2)
        configureLineChart(binding.linechartCellVoltage, 3)
        configureLineChart(binding.linechartPower)

        val realm = Realm.getDefaultInstance()
        val batteries = realm.where<BatteryData>().distinct("bleAddress").findAll()

        val spinnerAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item)

        for (battery in batteries) {
            spinnerAdapter.add(battery.bleName)
        }

        binding.spinnerBattery.adapter = spinnerAdapter

        binding.spinnerBattery.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedBatteryIndex = position
                updateUi()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.linechartVoltage.invalidate()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        selectedBatteryIndex = -1
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        selectedBatteryIndex = -1
    }

    private fun updateUi() {
        if (selectedBatteryIndex == -1) {
            return
        }

        val realm = Realm.getDefaultInstance()
        val batteries = realm.where<BatteryData>().distinct("bleAddress").findAll()

        val dataSetVoltage = realm.where<BatteryData>()
            .equalTo("bleAddress", batteries[selectedBatteryIndex]!!.bleAddress)
            .sort("timestamp", Sort.DESCENDING)
            .limit(60)
            .findAll()
            .sort("timestamp", Sort.ASCENDING)

        if (dataSetVoltage.size > 0) {
            val batteryVoltages = ArrayList<Entry>()
            val batteryCellVoltages = ArrayList<ArrayList<Entry>>()
            val batteryPower = ArrayList<Entry>()

            val minTsVoltage = dataSetVoltage.min("timestamp") as Long

            for (data in dataSetVoltage) {
                val timestamp = (data.timestamp - minTsVoltage).toFloat()

                batteryVoltages.add(Entry(timestamp, data.voltage))
                batteryPower.add(Entry(timestamp, data.power))

                for ((c, cellVoltage) in data.cellVoltages.withIndex()) {
                    if (batteryCellVoltages.size <= c) {
                        batteryCellVoltages.add(ArrayList())
                    }

                    batteryCellVoltages[c].add(Entry(timestamp, cellVoltage))
                }
            }

            val voltageSet = LineDataSet(batteryVoltages, "Voltage")
            voltageSet.setColors(resources.getColor(R.color.primaryLightGreen))
            voltageSet.setDrawCircles(false)
            voltageSet.setDrawCircleHole(false)
            voltageSet.setDrawValues(false)
            voltageSet.mode = LineDataSet.Mode.HORIZONTAL_BEZIER

            val voltageData = LineData(voltageSet)
            binding.linechartVoltage.data = voltageData
            binding.linechartVoltage.invalidate()

            val cellVoltagesSets = ArrayList<LineDataSet>()

            for (c in 0 until dataSetVoltage[0]!!.cellCount) {
                val cellSet = LineDataSet(batteryCellVoltages[c], "Cell $c Voltage")
                cellSet.setColors(resources.getColor(R.color.primaryLightYellow))
                cellSet.setDrawCircles(false)
                cellSet.setDrawCircleHole(false)
                cellSet.setDrawValues(false)
                cellSet.mode = LineDataSet.Mode.HORIZONTAL_BEZIER

                cellVoltagesSets.add(cellSet)
            }

            val cellVoltagesData = LineData(cellVoltagesSets.toList())
            binding.linechartCellVoltage.data = cellVoltagesData
            binding.linechartCellVoltage.invalidate()

            val powerSet = LineDataSet(batteryPower, "Power Usage")
            powerSet.setColors(resources.getColor(R.color.primary))
            powerSet.setDrawCircles(false)
            powerSet.setDrawCircleHole(false)
            powerSet.setDrawValues(false)
            powerSet.mode = LineDataSet.Mode.HORIZONTAL_BEZIER

            val powerData = LineData(powerSet)
            binding.linechartPower.data = powerData
            binding.linechartPower.invalidate()
        }
    }

    private fun configureLineChart(lineChart: LineChart, decimals: Int = 1) {
        lineChart.setNoDataTextColor(resources.getColor(R.color.white))
        lineChart.setNoDataText("...")

        lineChart.setPinchZoom(false)
        lineChart.setTouchEnabled(false)
        lineChart.isClickable = false
        lineChart.isDoubleTapToZoomEnabled = false

        lineChart.setDrawBorders(false)
        lineChart.setDrawBorders(false)
        lineChart.setDrawGridBackground(false)

        lineChart.description.isEnabled = false
        lineChart.legend.isEnabled = false

        lineChart.axisLeft.setDrawGridLines(false)
        // lineChart.axisLeft.setDrawLabels(false)
        // lineChart.axisLeft.setDrawAxisLine(false)
        lineChart.axisLeft.textColor = resources.getColor(R.color.white)
        lineChart.axisLeft.valueFormatter = DefaultValueFormatter(decimals)

        lineChart.xAxis.setDrawGridLines(false)
        // lineChart.xAxis.setDrawLabels(false)
        // lineChart.xAxis.setDrawAxisLine(false)
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.xAxis.textColor = resources.getColor(R.color.white)

        lineChart.axisRight.isEnabled = false
        lineChart.xAxis.granularity = 7.0f
    }
}