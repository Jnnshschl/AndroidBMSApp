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
import androidx.preference.PreferenceManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.DefaultValueFormatter
import de.jnns.bmsmonitor.data.BatteryData
import de.jnns.bmsmonitor.databinding.FragmentStatsBinding
import io.realm.Realm
import io.realm.kotlin.where


@ExperimentalUnsignedTypes
class StatsFragment : Fragment() {
    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!

    private var selectedBatteryIndex = -1
    private var selectedTimeDuration = 60

    private var needToAdjustYMinMax = true

    private var timeDurationNames = listOf("1m", "10s", "10m")
    private var timeDurations = listOf(60, 10, 600)

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
        configureLineChart(binding.linechartPower, 0)
        configureLineChart(binding.linechartCapacity, 1)

        binding.linechartCellVoltage.axisLeft.axisMinimum = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("minCellVoltage", "2500")!!.toFloat() / 1000.0f
        binding.linechartCellVoltage.axisLeft.axisMaximum = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("maxCellVoltage", "4200")!!.toFloat() / 1000.0f

        binding.linechartPower.axisLeft.axisMinimum = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("minPower", "-500")!!.toFloat()
        binding.linechartPower.axisLeft.axisMaximum = PreferenceManager.getDefaultSharedPreferences(requireContext()).getString("maxPower", "1000")!!.toFloat()
        binding.linechartPower.axisLeft.granularity = (binding.linechartPower.axisLeft.axisMaximum - binding.linechartPower.axisLeft.axisMinimum) / 6.0f

        binding.linechartCapacity.axisLeft.axisMinimum = 0.0f
        binding.linechartCapacity.axisLeft.axisMaximum = 100.0f
        binding.linechartCapacity.axisLeft.granularity = (binding.linechartCapacity.axisLeft.axisMaximum - binding.linechartCapacity.axisLeft.axisMinimum) / 6.0f

        val realm = Realm.getDefaultInstance()
        val batteries = realm.where<BatteryData>().distinct("bleAddress").findAll()

        // Battery selection
        val spinnerBatteryAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item)

        for (battery in batteries) {
            spinnerBatteryAdapter.add(battery.bleName)
        }

        binding.spinnerBattery.adapter = spinnerBatteryAdapter
        binding.spinnerBattery.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedBatteryIndex = position
                updateUi()

                needToAdjustYMinMax = true
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                binding.linechartVoltage.invalidate()
            }
        }

        // Time selection
        val spinnerTimeAdapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_spinner_dropdown_item)

        for (time in timeDurationNames) {
            spinnerTimeAdapter.add(time)
        }

        binding.spinnerTime.adapter = spinnerTimeAdapter
        binding.spinnerTime.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedTimeDuration = timeDurations[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                selectedTimeDuration = 60
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

    private fun setChartYMinMax(batteryData: BatteryData) {
        binding.linechartVoltage.axisLeft.axisMinimum = 2.5f * batteryData.cellCount.toFloat()
        binding.linechartVoltage.axisLeft.axisMaximum = 4.2f * batteryData.cellCount.toFloat()
    }

    private fun updateUi() {
        if (selectedBatteryIndex == -1) {
            return
        }

        val realm = Realm.getDefaultInstance()
        val batteries = realm.where<BatteryData>().distinct("bleAddress").findAll()

        val batteryDataSet = realm.where<BatteryData>()
            .equalTo("bleAddress", batteries[selectedBatteryIndex]!!.bleAddress)
            .greaterThanOrEqualTo("timestamp", System.currentTimeMillis() - (selectedTimeDuration * 1000))
            .findAll()

        if (batteryDataSet.count() > 0) {
            if (needToAdjustYMinMax) {
                setChartYMinMax(batteryDataSet[0]!!)
            }

            val minTime = batteryDataSet.min("timestamp") as Long

            // Voltage
            val batteryVoltages = buildDataset(batteryDataSet.map { Pair(it.voltage, it.timestamp) }, minTime)
            updateChart(binding.linechartVoltage, requireActivity().getColor(R.color.primaryLightGreen), listOf(LineDataSet(batteryVoltages, "Voltage")))

            // Cell Voltages
            val batteryCellVoltages = buildDatasets(batteryDataSet.map { Pair(it.cellVoltages, it.timestamp) }, minTime)

            val cellVoltagesSets = ArrayList<LineDataSet>()

            for (c in 0 until batteryDataSet[0]!!.cellCount) {
                val cellSet = LineDataSet(batteryCellVoltages[c], "Cell $c Voltage")
                cellVoltagesSets.add(cellSet)
            }

            updateChart(binding.linechartCellVoltage, requireActivity().getColor(R.color.primaryLightYellow), cellVoltagesSets)

            // Power
            val batteryPower = buildDataset(batteryDataSet.map { Pair(it.power, it.timestamp) }, minTime)
            updateChart(binding.linechartPower, requireActivity().getColor(R.color.primaryLightRed), listOf(LineDataSet(batteryPower, "Power Usage")))

            // Capacity
            val batteryCapacity = buildDataset(batteryDataSet.map { Pair((it.currentCapacity / it.totalCapacity) * 100.0f, it.timestamp) }, minTime)
            updateChart(binding.linechartCapacity, requireActivity().getColor(R.color.primaryLightGreen), listOf(LineDataSet(batteryCapacity, "Capacity")))
        } else {
            binding.linechartVoltage.clear()
            binding.linechartCellVoltage.clear()
            binding.linechartPower.clear()
            binding.linechartCapacity.clear()
        }
    }

    private fun buildDataset(inputData: List<Pair<Float, Long>>, minTime: Long): List<Entry> {
        val entries = ArrayList<Entry>()

        for (data in inputData) {
            val timestamp = (data.second - minTime).toFloat() / 1000.0f
            entries.add(Entry(timestamp, data.first))
        }

        return entries
    }

    private fun buildDatasets(inputData: List<Pair<List<Float>, Long>>, minTime: Long): List<List<Entry>> {
        val entries = ArrayList<List<Entry>>()
        val dataSetCount = inputData[0].first.count()

        val dataSets = ArrayList<ArrayList<Pair<Float, Long>>>(dataSetCount)

        for ((c, data) in inputData.withIndex()) {
            for ((i, f) in data.first.withIndex()) {
                if (c == 0) {
                    dataSets.add(ArrayList())
                }

                dataSets[i].add(Pair(f, data.second))
            }
        }

        for (dataSet in dataSets) {
            entries.add(buildDataset(dataSet, minTime))
        }

        return entries
    }

    private fun updateChart(lineChart: LineChart, color: Int, dataSets: List<LineDataSet>) {
        for (dataSet in dataSets) {
            dataSet.setColors(color)
            dataSet.setDrawCircles(false)
            dataSet.setDrawCircleHole(false)
            dataSet.setDrawValues(false)
            // dataSet.mode = LineDataSet.Mode.CUBIC_BEZIER
        }

        val lineData = LineData(dataSets)
        lineChart.data = lineData
        lineChart.invalidate()
    }

    private fun configureLineChart(lineChart: LineChart, decimals: Int = 0) {
        lineChart.setNoDataTextColor(requireActivity().getColor(R.color.white))
        lineChart.setNoDataText("No Data...")

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
        lineChart.axisLeft.textColor = requireActivity().getColor(R.color.white)
        lineChart.axisLeft.valueFormatter = DefaultValueFormatter(decimals)

        lineChart.xAxis.setDrawGridLines(false)
        // lineChart.xAxis.setDrawLabels(false)
        // lineChart.xAxis.setDrawAxisLine(false)
        lineChart.xAxis.position = XAxis.XAxisPosition.BOTTOM
        lineChart.xAxis.textColor = requireActivity().getColor(R.color.white)
        lineChart.xAxis.valueFormatter = DefaultValueFormatter(0)

        lineChart.axisRight.isEnabled = false
        lineChart.xAxis.granularity = 7.0f
    }
}