package org.skepsun.kototoro.video.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.skepsun.kototoro.R
import org.skepsun.kototoro.core.network.ContentHttpClient
import org.skepsun.kototoro.video.data.VideoLocalCacheProxy
import org.skepsun.kototoro.video.dlna.DlnaController
import org.skepsun.kototoro.video.dlna.DlnaDevice
import org.skepsun.kototoro.video.dlna.SsdpDiscovery
import javax.inject.Inject

@AndroidEntryPoint
class DlnaDeviceSheet : BottomSheetDialogFragment() {

    @Inject
    @ContentHttpClient
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var videoLocalCacheProxy: VideoLocalCacheProxy

    private val devices = mutableListOf<DlnaDevice>()
    private var adapter: DeviceAdapter? = null

    /**
     * Callback invoked when casting starts successfully.
     * The activity should use this to pause local playback.
     */
    var onCastStarted: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.sheet_dlna_devices, container, false)

        val title = view.findViewById<TextView>(R.id.text_title)
        val progress = view.findViewById<ProgressBar>(R.id.progress_bar)
        val recycler = view.findViewById<RecyclerView>(R.id.recycler_devices)
        val emptyText = view.findViewById<TextView>(R.id.text_empty)

        title.text = getString(R.string.cast_to_device)
        recycler.layoutManager = LinearLayoutManager(requireContext())
        adapter = DeviceAdapter(devices) { device -> onDeviceSelected(device) }
        recycler.adapter = adapter

        progress.visibility = View.VISIBLE
        emptyText.visibility = View.GONE

        lifecycleScope.launch {
            val found = SsdpDiscovery.discover(requireContext(), okHttpClient)
            progress.visibility = View.GONE
            devices.clear()
            devices.addAll(found)
            adapter?.notifyDataSetChanged()
            emptyText.visibility = if (found.isEmpty()) View.VISIBLE else View.GONE
        }

        return view
    }

    private fun onDeviceSelected(device: DlnaDevice) {
        val url = arguments?.getString(ARG_URL) ?: return
        @Suppress("UNCHECKED_CAST")
        val headers = (arguments?.getSerializable(ARG_HEADERS) as? HashMap<String, String>).orEmpty()
        val positionMs = arguments?.getLong(ARG_POSITION, 0L) ?: 0L

        lifecycleScope.launch {
            val lanUrl = videoLocalCacheProxy.getLanProxyUrl(url, headers)
            if (lanUrl == null) {
                Toast.makeText(context, R.string.cast_no_wifi, Toast.LENGTH_SHORT).show()
                return@launch
            }

            val setOk = DlnaController.setAVTransportURI(okHttpClient, device, lanUrl)
            if (setOk) {
                DlnaController.play(okHttpClient, device)
                // Seek to the current playback position if > 5 seconds
                if (positionMs > 5000) {
                    DlnaController.seek(okHttpClient, device, positionMs)
                }
                Toast.makeText(context, getString(R.string.casting_to, device.name), Toast.LENGTH_SHORT).show()
                // Notify the activity to pause local playback
                onCastStarted?.invoke()
            } else {
                Toast.makeText(context, R.string.cast_failed, Toast.LENGTH_SHORT).show()
            }
            dismiss()
        }
    }

    private class DeviceAdapter(
        private val items: List<DlnaDevice>,
        private val onClick: (DlnaDevice) -> Unit,
    ) : RecyclerView.Adapter<DeviceAdapter.VH>() {

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(android.R.id.text1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val view = LayoutInflater.from(parent.context)
                .inflate(android.R.layout.simple_list_item_1, parent, false)
            return VH(view)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val device = items[position]
            holder.name.text = device.name
            holder.itemView.setOnClickListener { onClick(device) }
        }

        override fun getItemCount(): Int = items.size
    }

    companion object {
        private const val ARG_URL = "url"
        private const val ARG_HEADERS = "headers"
        private const val ARG_POSITION = "position"

        fun newInstance(url: String, headers: Map<String, String>, positionMs: Long = 0L): DlnaDeviceSheet {
            return DlnaDeviceSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_URL, url)
                    putSerializable(ARG_HEADERS, HashMap(headers))
                    putLong(ARG_POSITION, positionMs)
                }
            }
        }
    }
}
