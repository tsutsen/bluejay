package com.tsutsen.platformplayer.di

import android.content.Context
import android.util.Log
import com.tsutsen.platformplayer.casting.CastConnectionState
import com.tsutsen.platformplayer.casting.CastProtocolType
import com.tsutsen.platformplayer.casting.CastingDevice
import com.tsutsen.platformplayer.core.data.repository.CastingRepository
import com.tsutsen.platformplayer.core.model.CastDevice
import com.tsutsen.platformplayer.core.model.CastState
import com.tsutsen.platformplayer.models.CastingDeviceInfo
import com.tsutsen.platformplayer.states.StateApp
import com.tsutsen.platformplayer.states.StateCasting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.fcast.sender_sdk.DeviceInfo
import org.fcast.sender_sdk.ProtocolType
import org.fcast.sender_sdk.tryIpAddrFromStr
import java.net.URL

/**
 * [CastingRepository] backed by the fcast sender SDK state
 * ([StateCasting]). Bridges the app-module SDK world to the core/data
 * interface the player observes.
 */
class CastingRepositoryImpl(private val context: Context) : CastingRepository {

    // Deliberately NOT a constructor val: touching StateCasting.instance here
    // (during Hilt graph construction in App.onCreate) used to crash because
    // FragmentedStorage's files dir is not ready that early.
    private val casting: StateCasting get() = StateCasting.instance

    private val _state = MutableStateFlow(CastState())
    private val stateFlow: StateFlow<CastState> = _state.asStateFlow()
    override val state: StateFlow<CastState>
        get() {
            ensureSubscribed()
            return stateFlow
        }

    private val _currentTimeMs = MutableStateFlow(0L)
    private val currentTimeFlow: StateFlow<Long> = _currentTimeMs.asStateFlow()
    override val currentTimeMs: StateFlow<Long>
        get() {
            ensureSubscribed()
            return currentTimeFlow
        }

    private val _durationMs = MutableStateFlow(0L)
    private val durationFlow: StateFlow<Long> = _durationMs.asStateFlow()
    override val durationMs: StateFlow<Long>
        get() {
            ensureSubscribed()
            return durationFlow
        }

    private var mediaEndedListener: (() -> Unit)? = null

    @Volatile
    private var subscribed = false

    private fun ensureSubscribed() {
        if (subscribed) return
        synchronized(this) {
            if (subscribed) return

        fun refreshDiscovered() {
            _state.update { it.copy(discoveredDevices = casting.devices.values.map { device -> toCastDevice(device) }) }
        }

        casting.onDeviceAdded.subscribe { refreshDiscovered() }
        casting.onDeviceChanged.subscribe { refreshDiscovered() }
        casting.onDeviceRemoved.subscribe { refreshDiscovered() }

        casting.onActiveDeviceConnectionStateChanged.subscribe { device, connectionState ->
            val castDevice = toCastDevice(device)
            _state.update {
                it.copy(
                    isConnecting = connectionState == CastConnectionState.CONNECTING,
                    isCasting = connectionState == CastConnectionState.CONNECTED,
                    activeDevice = if (connectionState == CastConnectionState.DISCONNECTED) null else castDevice,
                    discoveredDevices = casting.devices.values.map { d -> toCastDevice(d) },
                    rememberedDevices = casting.getRememberedCastingDevices().map { toCastDevice(it) },
                )
            }
        }

        casting.onActiveDeviceTimeChanged.subscribe { seconds ->
            _currentTimeMs.value = (seconds * 1000.0).toLong()
        }
        casting.onActiveDeviceDurationChanged.subscribe { seconds ->
            _durationMs.value = (seconds * 1000.0).toLong()
        }
        casting.onActiveDeviceMediaItemEnd.subscribe {
            mediaEndedListener?.invoke()
        }

            subscribed = true
        }
    }

    private fun toCastDevice(device: CastingDevice): CastDevice {
        val info = device.getDeviceInfo()
        return CastDevice(
            name = info.name,
            type = when (info.type) {
                CastProtocolType.CHROMECAST -> "chromecast"
                CastProtocolType.FCAST -> "fcast"
                else -> "unknown"
            },
            addresses = info.addresses.toList(),
            port = info.port,
        )
    }

    override fun connect(device: CastDevice) {
        ensureSubscribed()
        val existing = casting.devices[device.name]
        val handle = existing
            ?: CastingDeviceInfo(
                name = device.name,
                type = if (device.type == "chromecast") CastProtocolType.CHROMECAST else CastProtocolType.FCAST,
                addresses = device.addresses.toTypedArray(),
                port = device.port,
            ).let { casting.deviceFromInfo(it) }
            ?: return
        Log.i(TAG, "Connecting to cast device: ${device.name}")
        casting.connectDevice(handle)
    }

    override fun connectByUrl(url: String) {
        ensureSubscribed()
        try {
            val parsed = URL(url)
            val host = parsed.host
            val port = (if (parsed.port > 0) parsed.port else DEFAULT_FCAST_PORT)
            val ipAddr = tryIpAddrFromStr(host)
            if (ipAddr == null) {
                Log.w(TAG, "Cast URL host is not an IP address: $host")
                return
            }
            val deviceInfo = DeviceInfo(
                name = host,
                protocol = ProtocolType.F_CAST,
                addresses = listOf(ipAddr),
                port = port.toUShort(),
            )
            val castContext = casting._context ?: return
            val handle = CastingDevice(castContext.createDeviceFromInfo(deviceInfo))
            casting.addRememberedDevice(handle)
            casting.connectDevice(handle)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to connect by URL: $url", e)
        }
    }

    override fun disconnect() {
        ensureSubscribed()
        casting.activeDevice?.disconnect()
    }

    override fun pause() {
        ensureSubscribed()
        casting.pauseVideo()
    }

    override fun resume() {
        ensureSubscribed()
        casting.resumeVideo()
    }

    override fun seekTo(positionMs: Long) {
        ensureSubscribed()
        casting.videoSeekTo(positionMs / 1000.0)
    }

    override fun setSpeed(speed: Float): Boolean {
        ensureSubscribed()
        return casting.changeSpeed(speed.toDouble())
    }

    override fun setMediaEndedListener(listener: (() -> Unit)?) {
        mediaEndedListener = listener
    }

    private companion object {
        const val TAG = "CastingRepositoryImpl"
        const val DEFAULT_FCAST_PORT = 8000
    }
}
