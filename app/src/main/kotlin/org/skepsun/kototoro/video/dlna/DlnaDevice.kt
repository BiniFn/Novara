package org.skepsun.kototoro.video.dlna

/**
 * Represents a discovered DLNA device on the local network.
 */
data class DlnaDevice(
    val name: String,
    val location: String,      // XML description URL
    val controlUrl: String,    // AVTransport control URL
    val deviceType: String,    // e.g. "urn:schemas-upnp-org:device:MediaRenderer:1"
)
