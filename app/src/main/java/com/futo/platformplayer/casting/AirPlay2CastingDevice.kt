package com.futo.platformplayer.casting

import com.dd.plist.NSDictionary
import com.dd.plist.NSNumber
import com.dd.plist.NSString
import com.futo.platformplayer.logging.Logger
import com.futo.platformplayer.models.CastingDeviceInfo
import com.futo.platformplayer.stripLeadingZero
import com.futo.platformplayer.toHexString
import com.futo.platformplayer.toInetAddress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.*
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import java.util.*
import okhttp3.JavaNetCookieJar
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import java.net.CookieManager
import java.net.CookiePolicy

@OptIn(ExperimentalStdlibApi::class)
class AirPlay2CastingDevice : CastingDevice {
    override val protocol: CastProtocolType get() = CastProtocolType.AIRPLAY2
    override val isReady: Boolean get() = name != null && addresses?.isNotEmpty() == true && port != 0
    override var usedRemoteAddress: InetAddress? = null
    override var localAddress: InetAddress? = null
    override val canSetVolume: Boolean get() = true
    override val canSetSpeed: Boolean get() = true

    var addresses: Array<InetAddress>? = null
    var port: Int = 0

    private val _pairingDataHandler: IPairingDataHandler
    private var _scopeIO: CoroutineScope? = null
    private var _started: Boolean = false
    @Volatile private var _paired: Boolean = false
    private var _state: AirPlaySenderState = AirPlaySenderState.NOT_CONNECTED
    private var _srpClient: SRPClient? = null
    private var _pin: String? = null
    private var _sessionKey: ByteArray? = null
    private var _devicePrivateKey: ByteArray? = null
    private var _devicePublicKey: ByteArray? = null
    private var _verifierPrivateKey: ByteArray? = null
    private var _verifierPublicKey: ByteArray? = null
    private var _accessoryLtpk: ByteArray? = null
    private var _accessoryCurvePublic: ByteArray? = null
    private var _accessorySharedKey: ByteArray? = null
    private var _isEncrypted: Boolean = false
    private var _outgoingKey: ByteArray? = null
    private var _incomingKey: ByteArray? = null
    private var _outCount: Int = 0
    private var _inCount: Int = 0
    private var _cseq = 0
    private val _httpClient: OkHttpClient = OkHttpClient.Builder().cookieJar(JavaNetCookieJar(CookieManager().apply { setCookiePolicy(CookiePolicy.ACCEPT_ALL) })).build()

    companion object {
        private const val TAG = "AirPlay2CastingDevice"
        private const val DEVICE_ID = "C9635ED0964902E0"
        private const val CONTENT_TYPE = "application/octet-stream"
        private const val TAG_LENGTH = 16
        private const val MAX_BLOCK_LENGTH = 0x400
        val N = BigInteger(1, ("FFFFFFFF FFFFFFFF C90FDAA2 2168C234 C4C6628B 80DC1CD1 29024E08 8A67CC74" +
                "020BBEA6 3B139B22 514A0879 8E3404DD EF9519B3 CD3A431B 302B0A6D F25F1437" +
                "4FE1356D 6D51C245 E485B576 625E7EC6 F44C42E9 A637ED6B 0BFF5CB6 F406B7ED" +
                "EE386BFB 5A899FA5 AE9F2411 7C4B1FE6 49286651 ECE45B3D C2007CB8 A163BF05" +
                "98DA4836 1C55D39A 69163FA8 FD24CF5F 83655D23 DCA3AD96 1C62F356 208552BB" +
                "9ED52907 7096966D 670C354E 4ABC9804 F1746C08 CA18217C 32905E46 2E36CE3B" +
                "E39E772C 180E8603 9B2783A2 EC07A28F B5C55DF0 6F4C52C9 DE2BCBF6 95581718" +
                "3995497C EA956AE5 15D22618 98FA0510 15728E5A 8AAAC42D AD33170D 04507A33" +
                "A85521AB DF1CBA64 ECFB8504 58DBEF0A 8AEA7157 5D060C7D B3970F85 A6E1E4C7" +
                "ABF5AE8C DB0933D7 1E8C94E0 4A25619D CEE3D226 1AD2EE6B F12FFA06 D98A0864" +
                "D8760273 3EC86A64 521F2B18 177B200C BBE11757 7A615D6C 770988C0 BAD946E2" +
                "08E24FA0 74E5AB31 43DB5BFC E0FD108E 4B82D120 A93AD2CA FFFFFFFF FFFFFFFF").replace(" ", "").hexToByteArray())
        val g = BigInteger(1, "05".hexToByteArray())
    }

    constructor(name: String, addresses: Array<InetAddress>, port: Int, pairingDataHandler: IPairingDataHandler) {
        this.name = name
        this.addresses = addresses
        this.port = port
        _pairingDataHandler = pairingDataHandler
    }

    constructor(deviceInfo: CastingDeviceInfo, pairingDataHandler: IPairingDataHandler) {
        this.name = deviceInfo.name
        this.addresses = deviceInfo.addresses.mapNotNull { it.toInetAddress() }.toTypedArray()
        this.port = deviceInfo.port
        _pairingDataHandler = pairingDataHandler
    }

    override fun getAddresses(): List<InetAddress> = addresses?.toList() ?: emptyList()

    override fun providePairingPin(pin: String?) {
        Logger.i(TAG, "Pairing PIN provided $pin")
        _pin = pin
        _scopeIO?.launch(Dispatchers.IO) {
            performPair(pin)
        }
    }

    override fun start() {
        if (_started) return
        val adrs = addresses ?: return

        _started = true
        _paired = false
        _scopeIO = CoroutineScope(Dispatchers.IO)

        Logger.i(TAG, "Starting AirPlay2 device...")

        _scopeIO?.launch(Dispatchers.IO) {
            usedRemoteAddress = adrs.firstOrNull { addr ->
                try {
                    val socket = java.net.Socket(addr, port)
                    localAddress = socket.localAddress
                    socket.close()
                    true
                } catch (e: Exception) {
                    Logger.w(TAG, "Failed connecting to $addr:$port", e)
                    false
                }
            }

            if (usedRemoteAddress == null) {
                Logger.w(TAG, "Could not connect to any address.")
                return@launch
            }

            Logger.i(TAG, "Connected to ${usedRemoteAddress}:${port}")
            if (!_paired) {
                performPairSetup()
            }
        }
    }

    override fun stop() {
        Logger.i(TAG, "Stopping AirPlay2 device...")
        connectionState = CastConnectionState.DISCONNECTED
        _paired = false
        _started = false
        _scopeIO?.cancel()
        _scopeIO = null
        Logger.i(TAG, "AirPlay2 device stopped.")
    }

    override fun loadVideo(
        streamType: String,
        contentType: String,
        contentId: String,
        resumePosition: Double,
        duration: Double,
        speed: Double?
    ) {
        Logger.i(TAG, "loadVideo: contentId=$contentId, resumePosition=$resumePosition")
        if (!isReady || !_paired) return

        //TODO
    }

    override fun loadContent(contentType: String, content: String, resumePosition: Double, duration: Double, speed: Double?) {
        //TODO
    }

    override fun seekVideo(timeSeconds: Double) {
        Logger.i(TAG, "seekVideo: $timeSeconds")
        if (!isReady || !_paired) return

        //TODO
    }

    override fun resumeVideo() {
        Logger.i(TAG, "resumeVideo")
        if (!isReady || !_paired) return
        //TODO
        isPlaying = true
    }

    override fun pauseVideo() {
        Logger.i(TAG, "pauseVideo")
        if (!isReady || !_paired) return
        //TODO
        isPlaying = false
    }

    override fun stopVideo() {
        Logger.i(TAG, "stopVideo")
        if (!isReady || !_paired) return

        //TODO
    }

    override fun stopCasting() {
        stopVideo()
        stop()
    }

    override fun changeVolume(volume: Double) {
        Logger.i(TAG, "changeVolume: $volume")
        if (!isReady || !_paired) return

        //TODO
    }

    override fun changeSpeed(speed: Double) {
        Logger.i(TAG, "changeSpeed: $speed")
        if (!isReady || !_paired) return
        //TODO
    }

    override fun getDeviceInfo(): CastingDeviceInfo {
        return CastingDeviceInfo(
            name!!,
            CastProtocolType.AIRPLAY2,
            addresses!!.mapNotNull { it.hostAddress }.toTypedArray(),
            port
        )
    }

    private fun getUrl(endpoint: String): String {
        return "http://${usedRemoteAddress?.hostAddress}:$port$endpoint"
    }

    private fun performPairSetup() {
        /*Logger.i(TAG, "Starting pair-setup...")
        _state = AirPlaySenderState.WAITING_ON_PAIR_PIN_START
        val pinResult = postHttp("/pair-pin-start", ByteArray(0), null)
        if (pinResult == true) {
            Logger.i(TAG, "Waiting for PIN...")
            onPairingPinRequired.emit()
        } else {
            Logger.w(TAG, "Failed to show PIN, attempting pair without PIN")
            _scopeIO?.launch(Dispatchers.IO) { performPair(null) }
        }*/

        _scopeIO?.launch(Dispatchers.IO) { performPair(null) }
    }

    private fun performPair(pin: String?) {
        Logger.i(TAG, "Performing pair with PIN $pin")

        _state = AirPlaySenderState.WAITING_ON_PAIR_SETUP1
        val username = "Pair-Setup"
        val password = pin ?: "3939"
        _srpClient = SRPClient(N, g, username, password)

        val stateItem = TLV8Item(TLV8Tag.STATE, ubyteArrayOf(PairingState.M1.value))
        val methodItem = TLV8Item(TLV8Tag.METHOD, ubyteArrayOf(PairingMethod.PAIR_SETUP.value))
        val tlvItems = listOf(stateItem, methodItem)
        val encodedTlv = TLV8Item.encodeWithLogging(tlvItems)

        val headers = mapOf(
            "Content-Type" to CONTENT_TYPE,
            "Content-Length" to encodedTlv.size.toString()
        )
        val response = postHttpWithResponse("/pair-setup", encodedTlv, headers)
        if (response?.isSuccessful == true) {
            response.body?.bytes()?.let { continuePairSetup(it) }
        } else {
            pairingDidFail("Failed to initiate pair-setup")
        }
    }

    private fun continuePairSetup(responseData: ByteArray) {
        if (responseData.isEmpty()) {
            pairingDidFail("Server response data is empty")
            return
        }

        Logger.i(TAG, "Response: " + TLV8Item.decodeAsString(responseData.asUByteArray()))

        val fields  = TLV8Item.decodeAndReassembleWithLogging(responseData.asUByteArray())
        val errorBytes = fields[TLV8Tag.ERROR]
        if (errorBytes?.isNotEmpty() == true) {
            val errorCode = errorBytes[0].toUByte().toInt()
            if (errorCode == 0x03) {
                val backoffBytes = fields[TLV8Tag.RETRY_DELAY]
                val backoffSeconds = ByteBuffer.wrap(backoffBytes).order(ByteOrder.LITTLE_ENDIAN).short
                pairingDidFail("Pairing backoff requested, should retry in ${backoffSeconds}s")
            } else {
                pairingDidFail("Pairing failed with error code $errorCode")
            }
            return
        }
        val stateBytes = fields[TLV8Tag.STATE]
        if (stateBytes == null || stateBytes.isEmpty()) {
            pairingDidFail("State item is missing")
            return
        }
        val remoteState = stateBytes[0].toUByte()
        Logger.i(TAG, "Transitioned to state ${remoteState}")

        when {
            // ───── SETUP PHASE ─────
            _state == AirPlaySenderState.WAITING_ON_PAIR_SETUP1 && remoteState == PairingState.M2.value -> pairSetupM2M3(fields)
            _state == AirPlaySenderState.WAITING_ON_PAIR_SETUP2 && remoteState == PairingState.M4.value -> pairSetupM4M5(fields)
            _state == AirPlaySenderState.WAITING_ON_PAIR_SETUP3 && remoteState == PairingState.M6.value -> pairVerifyM1(fields)

            // ───── VERIFY PHASE ─────
            _state == AirPlaySenderState.WAITING_ON_PAIR_VERIFY1 && remoteState == PairingState.M2.value -> pairVerifyM2(fields)
            _state == AirPlaySenderState.WAITING_ON_PAIR_VERIFY2 && remoteState == PairingState.M4.value -> {
                _isEncrypted = true
                setCiphers()
                _state = AirPlaySenderState.READY_TO_PLAY
                _paired = true
                pairingDidFinish()
            }

            else -> pairingDidFail("Unexpected STATE=$remoteState when in $_state")
        }
    }


    private fun pairSetupM2M3(fields: Map<TLV8Tag, ByteArray>) {
        _state = AirPlaySenderState.WAITING_ON_PAIR_SETUP2

        val saltBytes = fields[TLV8Tag.SALT]
        val BBytes = fields[TLV8Tag.PUBLIC_KEY]
        if (saltBytes == null || BBytes == null) {
            pairingDidFail("Salt or public key is missing")
            return
        }

        try {
            val client = _srpClient ?: throw IllegalStateException("SRPClient not initialized")
            val ABytes = client.srp_user_start_authentication()

            val M1Bytes = client.srp_user_process_challenge(saltBytes, BBytes)

            val stateItem = TLV8Item(TLV8Tag.STATE, ubyteArrayOf(PairingState.M3.value))
            val aBytes = ABytes.toByteArray().stripLeadingZero().asUByteArray()
            val pkItem = TLV8Item(TLV8Tag.PUBLIC_KEY, aBytes)
            val m1Bytes = M1Bytes.asUByteArray()
            val proofItem = TLV8Item(TLV8Tag.PROOF, m1Bytes)
            val tlvItems = listOf(stateItem, pkItem, proofItem)
            val encodedTlv = TLV8Item.encodeWithLogging(tlvItems)

            val headers = mapOf(
                "Content-Type"   to CONTENT_TYPE,
                "Content-Length" to encodedTlv.size.toString()
            )
            val response = postHttpWithResponse("/pair-setup", encodedTlv, headers)
            if (response == null) {
                pairingDidFail("M2→M3: no HTTP response (connection error)")
                return
            }

            val code = response.code
            val bodyBytes = response.body?.bytes()
            if (response.isSuccessful && bodyBytes != null) {
                continuePairSetup(bodyBytes)
            } else {
                pairingDidFail("M2→M3 failed: HTTP $code, body=${bodyBytes?.toHexString()}")
            }
        } catch (e: Exception) {
            pairingDidFail("SRP calculation failed.", e)
        }
    }

    private fun pairSetupM4M5(fields: Map<TLV8Tag, ByteArray>) {
        _state = AirPlaySenderState.WAITING_ON_PAIR_SETUP3

        val proofBytes = fields[TLV8Tag.PROOF]
        if (proofBytes == null) {
            pairingDidFail("Proof is missing")
            return
        }

        try {
            val client = _srpClient ?: throw IllegalStateException("SRPClient not initialized")
            val verified = client.srp_user_verify_session(proofBytes)
            if (!verified) {
                pairingDidFail("Server authentication failed")
                return
            }

            val K = client.getSessionKey() ?: throw IllegalStateException("Session key not computed")
            _sessionKey = K

            val seed = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val edPriv = Ed25519PrivateKeyParameters(seed, 0)
            val edPub = edPriv.generatePublicKey()

            _devicePrivateKey = seed
            _devicePublicKey = edPub.encoded

            val deviceX = hkdfExtractExpand(
                K,
                "Pair-Setup-Controller-Sign-Salt".toByteArray(Charsets.US_ASCII),
                "Pair-Setup-Controller-Sign-Info".toByteArray(Charsets.US_ASCII),
                32
            )

            val deviceIDBytes = DEVICE_ID.toByteArray(Charsets.US_ASCII)
            val deviceInfo = concat(deviceX, deviceIDBytes, edPub.encoded)

            val signer = Ed25519Signer()
            signer.init(true, edPriv)
            signer.update(deviceInfo, 0, deviceInfo.size)
            val signature = signer.generateSignature()

            val identifierItem = TLV8Item(TLV8Tag.IDENTIFIER, deviceIDBytes.asUByteArray())
            val publicKeyItem = TLV8Item(TLV8Tag.PUBLIC_KEY, edPub.encoded.asUByteArray())
            val sigItem = TLV8Item(TLV8Tag.SIGNATURE, signature.asUByteArray())
            val tlvItems = listOf(identifierItem, publicKeyItem, sigItem)
            val encodedTlv = TLV8Item.encodeWithLogging(tlvItems)

            val sessionKey2 = hkdfExtractExpand(
                K,
                "Pair-Setup-Encrypt-Salt".toByteArray(Charsets.US_ASCII),
                "Pair-Setup-Encrypt-Info".toByteArray(Charsets.US_ASCII),
                32
            )

            val bcNonce = ByteArray(4) { 0x00 } + "PS-Msg05".toByteArray(Charsets.UTF_8)
            val (ciphertext, mac) = chacha20Poly1305Encrypt(
                sessionKey2,
                bcNonce,
                ByteArray(0),
                encodedTlv
            )
            val encryptedData = ciphertext + mac

            val stateItem = TLV8Item(TLV8Tag.STATE, ubyteArrayOf(PairingState.M5.value))
            val encryptedDataItem = TLV8Item(TLV8Tag.ENCRYPTED_DATA, encryptedData.asUByteArray())
            val responseItems = listOf(stateItem, encryptedDataItem)
            val responseTlv = TLV8Item.encodeWithLogging(responseItems)

            val headers = mapOf(
                "Content-Type" to CONTENT_TYPE,
                "Content-Length" to responseTlv.size.toString()
            )
            val response = postHttpWithResponse("/pair-setup", responseTlv, headers)
            if (response?.isSuccessful == true) {
                response.body?.bytes()?.let { continuePairSetup(it) }
            } else {
                pairingDidFail("Failed to process M4→M5")
            }
        } catch (e: Exception) {
            pairingDidFail("Error in M4→M5.", e)
        }
    }

    private fun pairVerifyM1(fields: Map<TLV8Tag, ByteArray>) {
        _state = AirPlaySenderState.WAITING_ON_PAIR_VERIFY1

        val encryptedField = fields[TLV8Tag.ENCRYPTED_DATA]
        if (encryptedField == null) {
            pairingDidFail("Encrypted data missing")
            return
        }
        val encryptedTlvData = encryptedField.copyOfRange(0, encryptedField.size - TAG_LENGTH)
        val tagData = encryptedField.copyOfRange(encryptedField.size - TAG_LENGTH, encryptedField.size)

        try {
            val K = _sessionKey ?: throw IllegalStateException("No valid session key")
            val sessionKey2 = hkdfExtractExpand(
                K,
                "Pair-Setup-Encrypt-Salt".toByteArray(Charsets.UTF_8),
                "Pair-Setup-Encrypt-Info".toByteArray(Charsets.UTF_8),
                32
            )

            val nonce = ByteArray(4) { 0x00 } + "PS-Msg06".toByteArray(Charsets.UTF_8)
            val decryptedTlv = chacha20Poly1305Decrypt(
                sessionKey2,
                nonce,
                ByteArray(0),
                encryptedTlvData,
                tagData
            ) ?: throw IllegalStateException("Decryption failed")

            val accessoryItems = TLV8Item.decode(decryptedTlv.asUByteArray())
            val accessoryIdBytes = accessoryItems.find { it.tag == TLV8Tag.IDENTIFIER }?.value?.asByteArray()
            val accessoryLtpkBytes = accessoryItems.find { it.tag == TLV8Tag.PUBLIC_KEY }?.value?.asByteArray()
            val accessorySigBytes = accessoryItems.find { it.tag == TLV8Tag.SIGNATURE }?.value?.asByteArray()

            if (accessoryIdBytes == null || accessoryLtpkBytes == null || accessorySigBytes == null) {
                pairingDidFail("Accessory data incomplete")
                return
            }
            _accessoryLtpk = accessoryLtpkBytes
            val accessoryX = hkdfExtractExpand(
                K,
                "Pair-Setup-Accessory-Sign-Salt".toByteArray(Charsets.UTF_8),
                "Pair-Setup-Accessory-Sign-Info".toByteArray(Charsets.UTF_8),
                32
            )

            val accessoryInfo = concat(accessoryX, accessoryIdBytes, accessoryLtpkBytes)
            val verifier = Ed25519Signer()
            val pubParam = Ed25519PublicKeyParameters(accessoryLtpkBytes, 0)
            verifier.init(false, pubParam)
            verifier.update(accessoryInfo, 0, accessoryInfo.size)
            if (!verifier.verifySignature(accessorySigBytes)) {
                pairingDidFail("Accessory signature not verified")
                return
            }
            Logger.i(TAG, "Accessory signature is valid!")

            val curvePriv = ByteArray(32).also { SecureRandom().nextBytes(it) }
            val curvePub = X25519PrivateKeyParameters(curvePriv, 0)
                .generatePublicKey()
                .encoded
            _verifierPrivateKey = curvePriv
            _verifierPublicKey = curvePub

            val stateItem = TLV8Item(TLV8Tag.STATE, ubyteArrayOf(PairingState.M1.value))
            val pkItem = TLV8Item(TLV8Tag.PUBLIC_KEY, curvePub.asUByteArray())
            val responseItems = listOf(stateItem, pkItem)
            val encodedTlv = TLV8Item.encodeWithLogging(responseItems)

            val headers = mapOf(
                "Content-Type" to CONTENT_TYPE,
                "Content-Length" to encodedTlv.size.toString()
            )
            val response = postHttpWithResponse("/pair-verify", encodedTlv, headers)
            if (response?.isSuccessful == true) {
                response.body?.bytes()?.let { continuePairSetup(it) }
            } else {
                pairingDidFail("Failed to process pair-verify M1")
            }
        } catch (e: Exception) {
            pairingDidFail("Pair-verify M1 failed: ${e.message}")
        }
    }


    private fun pairVerifyM2(fields: Map<TLV8Tag, ByteArray>) {
        _state = AirPlaySenderState.WAITING_ON_PAIR_VERIFY2

        val accessoryCurvePubBytes = fields[TLV8Tag.PUBLIC_KEY]
        val accessoryEncryptedField = fields[TLV8Tag.ENCRYPTED_DATA]
        if (accessoryCurvePubBytes == null || accessoryEncryptedField == null) {
            pairingDidFail("Public key or encrypted data missing")
            return
        }
        _accessoryCurvePublic = accessoryCurvePubBytes

        val encryptedTlvData = accessoryEncryptedField.copyOfRange(0, accessoryEncryptedField.size - TAG_LENGTH)
        val tagData = accessoryEncryptedField.copyOfRange(accessoryEncryptedField.size - TAG_LENGTH, accessoryEncryptedField.size)

        try {
            val privParam = X25519PrivateKeyParameters(_verifierPrivateKey!!, 0)
            val pubParam = X25519PublicKeyParameters(accessoryCurvePubBytes, 0)
            val sharedSecret = ByteArray(32)
            privParam.generateSecret(pubParam, sharedSecret, 0)
            _accessorySharedKey = sharedSecret

            val sessionKey = hkdfExtractExpand(
                sharedSecret,
                "Pair-Verify-Encrypt-Salt".toByteArray(Charsets.UTF_8),
                "Pair-Verify-Encrypt-Info".toByteArray(Charsets.UTF_8),
                32
            )

            val nonce = ByteArray(4) { 0x00 } + "PV-Msg02".toByteArray(Charsets.UTF_8)
            val decryptedTlv = chacha20Poly1305Decrypt(
                sessionKey,
                nonce,
                ByteArray(0),
                encryptedTlvData,
                tagData
            ) ?: throw IllegalStateException("Decryption failed")

            val accessoryItems = TLV8Item.decode(decryptedTlv.asUByteArray())
            val accessoryIdBytes = accessoryItems.find { it.tag == TLV8Tag.IDENTIFIER }?.value?.asByteArray()
            val accessorySigBytes = accessoryItems.find { it.tag == TLV8Tag.SIGNATURE }?.value?.asByteArray()
            if (accessoryIdBytes == null || accessorySigBytes == null) {
                pairingDidFail("Accessory data incomplete")
                return
            }

            val accessoryInfo = concat(
                accessoryCurvePubBytes,
                accessoryIdBytes,
                _verifierPublicKey!!
            )
            val verifier = Ed25519Signer()
            verifier.init(false, Ed25519PublicKeyParameters(_accessoryLtpk!!, 0))
            verifier.update(accessoryInfo, 0, accessoryInfo.size)
            if (!verifier.verifySignature(accessorySigBytes)) {
                pairingDidFail("Accessory signature not verified")
                return
            }
            Logger.i(TAG, "Accessory signature is valid!")

            val deviceIDBytes = DEVICE_ID.toByteArray(Charsets.UTF_8)
            val deviceInfo = concat(
                _verifierPublicKey!!,
                deviceIDBytes,
                accessoryCurvePubBytes
            )
            val signer = Ed25519Signer()
            val edPriv = Ed25519PrivateKeyParameters(_devicePrivateKey!!, 0)
            signer.init(true, edPriv)
            signer.update(deviceInfo, 0, deviceInfo.size)
            val signature = signer.generateSignature()

            val identifierItem = TLV8Item(TLV8Tag.IDENTIFIER, deviceIDBytes.asUByteArray())
            val signatureItem = TLV8Item(TLV8Tag.SIGNATURE, signature.asUByteArray())
            val tlvItems = listOf(identifierItem, signatureItem)
            val encodedTlv = TLV8Item.encodeWithLogging(tlvItems)

            val nonce2 = ByteArray(4) { 0x00 } + "PV-Msg03".toByteArray(Charsets.UTF_8)
            val (ciphertext, mac) = chacha20Poly1305Encrypt(
                sessionKey,
                nonce2,
                ByteArray(0),
                encodedTlv
            )
            val encryptedData = ciphertext + mac

            val stateItem = TLV8Item(TLV8Tag.STATE, ubyteArrayOf(PairingState.M3.value))
            val encryptedDataItem = TLV8Item(TLV8Tag.ENCRYPTED_DATA, encryptedData.asUByteArray())
            val responseItems = listOf(stateItem, encryptedDataItem)
            val encodedResponse = TLV8Item.encodeWithLogging(responseItems)

            val headers = mapOf(
                "Content-Type" to CONTENT_TYPE,
                "Content-Length" to encodedResponse.size.toString()
            )
            val response = postHttpWithResponse("/pair-verify", encodedResponse, headers)
            if (response?.isSuccessful == true) {
                response.body?.bytes()?.let { continuePairSetup(it) }
            } else {
                pairingDidFail("Failed to process pair-verify M2")
            }
        } catch (e: Exception) {
            pairingDidFail("Pair-verify M2 failed: ${e.message}")
        }
    }

    private fun setCiphers() {
        val sharedKey = _accessorySharedKey ?: return
        val prk = hkdfExtractExpand(sharedKey, "Control-Salt".encodeToByteArray(), null, 64)
        _outgoingKey = hkdfExtractExpand(prk, "Control-Write-Encryption-Key".encodeToByteArray(), null, 32)
        _incomingKey = hkdfExtractExpand(prk, "Control-Read-Encryption-Key".encodeToByteArray(), null, 32)
    }

    /*private fun postEncrypted(
        path: String,
        plaintext: ByteArray
    ): Boolean {
        val encrypted = encryptData(plaintext)
        val req = Request.Builder()
            .url(getUrl(path))
            .post(encrypted.toRequestBody(CONTENT_TYPE.toMediaType()))
            .headers(
                Headers.headersOf(
                "User-Agent" to "AirPlay/381.13",
                "X-Apple-HKP" to "3",
                "X-Apple-Client-Name" to "Grayjay"
            ) )
            .build()

        return try {
            _httpClient.newCall(req).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            Logger.w(TAG, "Encrypted POST failed to $path", e)
            false
        }
    }*/

    private fun Map<String,Any>.toNSDictionary(): NSDictionary {
        val dict = NSDictionary()
        forEach { (k,v) ->
            when (v) {
                is String -> dict[k] = NSString(v)
                is Double -> dict[k] = NSNumber(v)
                is Long -> dict[k] = NSNumber(v)
                is Int -> dict[k] = NSNumber(v)
                is Boolean -> dict[k] = if (v) NSNumber(true) else NSNumber(false)
                else -> throw IllegalArgumentException("Unsupported plist value type: ${v.javaClass}")
            }
        }
        return dict
    }

    private fun encryptData(data: ByteArray): ByteArray {
        if (!_isEncrypted || _outgoingKey == null) return data
        val result = ByteArrayOutputStream()
        var offset = 0
        while (offset < data.size) {
            val length = minOf(data.size - offset, MAX_BLOCK_LENGTH)
            val blockData = data.copyOfRange(offset, offset + length)
            val lengthData = ByteBuffer.allocate(2).putShort(length.toShort()).array()
            val nonce = ByteBuffer.allocate(12).putInt(0).putLong(_outCount.toLong()).array()
            val (ciphertext, mac) = chacha20Poly1305Encrypt(_outgoingKey!!, nonce, lengthData, blockData)
            result.write(lengthData)
            result.write(ciphertext)
            result.write(mac)
            offset += length
            _outCount++
        }
        return result.toByteArray()
    }

    private fun decryptData(data: ByteArray): ByteArray? {
        if (!_isEncrypted || _incomingKey == null || data.size < 2 + TAG_LENGTH) return null
        val length = ByteBuffer.wrap(data, 0, 2).short.toInt() and 0xFFFF
        if (data.size < 2 + length + TAG_LENGTH) return null
        val blockData = data.copyOfRange(2, 2 + length)
        val mac = data.copyOfRange(2 + length, 2 + length + TAG_LENGTH)
        val nonce = ByteBuffer.allocate(12).putInt(0).putLong(_inCount.toLong()).array()
        val plaintext = chacha20Poly1305Decrypt(_incomingKey!!, nonce, byteArrayOf(), blockData, mac)
        if (plaintext != null) _inCount++
        return plaintext
    }

    private fun pairingDidFail(message: String, e: Throwable? = null) {
        _state = AirPlaySenderState.PAIRING_FAILED
        if (e != null)
            Logger.e(TAG, "Pairing failed with message '${message}'.", e)
        else
            Logger.e(TAG, "Pairing failed with message '${message}'.")
    }

    private fun pairingDidFinish() {
        Logger.i(TAG, "Pairing succeeded. Device is ready.")
        connectionState = CastConnectionState.CONNECTED
        _state = AirPlaySenderState.READY_TO_PLAY
        _paired = true

        //TODO: Do something?
    }

    private fun postHttp(path: String, bodyBytes: ByteArray, contentType: String?): Boolean? {
        val url = getUrl(path)
        val request = Request.Builder()
            .url(url)
            .post(bodyBytes.toRequestBody(contentType?.toMediaType()))
            .header("User-Agent", "AirPlay/381.13")
            .header("X-Apple-HKP", "3")
            .header("CSeq", (_cseq++).toString())
            .apply { if (contentType != null) header("Content-Type", contentType) }
            .build()

        return try {
            _httpClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            Logger.w(TAG, "HTTP POST failed: $url", e)
            false
        }
    }

    private fun postHttpWithResponse(path: String, bodyBytes: ByteArray, headers: Map<String, String>?): Response? {
        val url = getUrl(path)
        val request = Request.Builder()
            .url(url)
            .post(bodyBytes.toRequestBody(headers?.get("Content-Type")?.toMediaType()))
            .header("User-Agent", "AirPlay/381.13")
            .header("X-Apple-HKP", "3")
            .header("X-Apple-Client-Name", "Grayjay")
            .apply {
                headers?.forEach { (k, v) -> header(k, v) }
            }
            .build()

        return try {
            _httpClient.newCall(request).execute()
        } catch (e: Exception) {
            Logger.w(TAG, "HTTP POST failed: $url", e)
            null
        }
    }

    private fun hkdfExtractExpand(ikm: ByteArray, salt: ByteArray?, info: ByteArray?, length: Int): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA512Digest())
        val params = HKDFParameters(ikm, salt, info)
        hkdf.init(params)
        val output = ByteArray(length)
        hkdf.generateBytes(output, 0, length)
        return output
    }

    private fun chacha20Poly1305Encrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, plaintext: ByteArray): Pair<ByteArray, ByteArray> {
        val aead = ChaCha20Poly1305()
        aead.init(true, AEADParameters(KeyParameter(key), 128, nonce, aad))

        val output = ByteArray(plaintext.size + 16)
        var offset = aead.processBytes(plaintext, 0, plaintext.size, output, 0)
        aead.doFinal(output, offset)

        val ciphertext = output.copyOf(plaintext.size)
        val tag = output.copyOfRange(plaintext.size, output.size)
        return Pair(ciphertext, tag)
    }

    private fun chacha20Poly1305Decrypt(key: ByteArray, nonce: ByteArray, aad: ByteArray, ciphertext: ByteArray, mac: ByteArray): ByteArray? {
        val aead = ChaCha20Poly1305()
        aead.init(false, AEADParameters(KeyParameter(key), 128, nonce, aad))

        val input = ciphertext + mac
        val output = ByteArray(ciphertext.size)
        var len = aead.processBytes(input, 0, input.size, output, 0)
        return try {
            aead.doFinal(output, len)
            output
        } catch (_: Exception) {
            null
        }
    }

    private fun concat(vararg arrays: ByteArray): ByteArray {
        val totalLength = arrays.sumOf { it.size }
        val result = ByteArray(totalLength)
        var offset = 0
        for (array in arrays) {
            System.arraycopy(array, 0, result, offset, array.size)
            offset += array.size
        }
        return result
    }
}

enum class AirPlaySenderState {
    NOT_CONNECTED,
    WAITING_ON_PAIR_PIN_START,
    WAITING_ON_PAIR_SETUP1,
    WAITING_ON_PAIR_SETUP2,
    WAITING_ON_PAIR_SETUP3,
    WAITING_ON_PAIR_VERIFY1,
    WAITING_ON_PAIR_VERIFY2,
    READY_TO_PLAY,
    CANCELLED,
    PAIRING_FAILED
}

enum class PairingState(val value: UByte) {
    M1(1u),
    M2(2u),
    M3(3u),
    M4(4u),
    M5(5u),
    M6(6u)
}

enum class PairingMethod(val value: UByte) {
    PAIR_SETUP(0u),
    PAIR_SETUP_WITH_AUTH(1u),
    PAIR_VERIFY(2u),
    ADD_PAIRING(3u),
    REMOVE_PAIRING(4u),
    LIST_PAIRINGS(5u)
}