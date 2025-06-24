package com.futo.platformplayer.casting

import com.futo.platformplayer.stripLeadingZero
import org.bouncycastle.crypto.digests.SHA512Digest
import java.math.BigInteger
import java.security.SecureRandom

class SRPClient(private val N: BigInteger, private val g: BigInteger, private val username: String, private val password: String) {
    private val digest = SHA512Digest()
    private val hashLen = digest.digestSize
    private val PAD_L: Int = (N.bitLength() + 7) / 8

    private var a: BigInteger? = null
    private var A: BigInteger? = null
    private var S: BigInteger? = null
    private var sessionKey: ByteArray? = null
    private var M: ByteArray? = null
    private var HAMK: ByteArray? = null
    private var authenticated: Boolean = false

    private val random = SecureRandom()

    fun isAuthenticated(): Boolean = authenticated
    fun getSessionKey(): ByteArray? = sessionKey

    fun srp_user_start_authentication(aOverride: BigInteger? = null): BigInteger {
        a = aOverride ?: BigInteger(256, random)
        A = g.modPow(a, N)

        if (A!!.mod(N).signum() == 0) {
            throw IllegalStateException("Invalid client parameter: A mod N = 0")
        }

        return A!!
    }

    fun getS(): ByteArray? = S?.toByteArray()?.stripLeadingZero()
    fun getA(): ByteArray? = A?.toByteArray()?.stripLeadingZero()

    fun srp_user_process_challenge(saltBytes: ByteArray, BBytes: ByteArray): ByteArray {
        return srp_user_process_challenge_internal(saltBytes, BBytes).third
    }

    fun srp_user_process_challenge_internal(saltBytes: ByteArray, BBytes: ByteArray): Triple<BigInteger, BigInteger, ByteArray> {
        if (A == null || a == null) {
            throw IllegalStateException("Must call srp_user_start_authentication() first.")
        }

        val B = BigInteger(1, BBytes)
        val u = H_nn(A!!, B)
        if (u.signum() == 0) {
            throw IllegalStateException("Invalid server parameter: u = 0")
        }

        val x = calculate_x(BigInteger(1, saltBytes))
        val k = H_nn(N, g)
        val v = g.modPow(x, N)
        if (B.mod(N).signum() == 0) {
            throw IllegalStateException("Invalid server parameter: B mod N = 0")
        }

        val kv = k.multiply(v).mod(N)
        val base = B.subtract(kv).mod(N)
        val exponent = a!!.add(u.multiply(x))
        S = base.modPow(exponent, N)

        sessionKey = hashBigInteger(S!!)
        M = calculate_M(saltBytes, A!!, B, sessionKey!!)
        return Triple(u, v, M!!.clone())
    }

    fun srp_user_verify_session(serverHAMK: ByteArray): Boolean {
        if (M == null || sessionKey == null || A == null) {
            throw IllegalStateException("Must call srp_user_process_challenge() first.")
        }

        val hamk = calculate_H_AMK(A!!, M!!, sessionKey!!)
        HAMK = hamk

        authenticated = HAMK!!.contentEquals(serverHAMK)
        return authenticated
    }

    private fun H_padded(vararg inputs: BigInteger): BigInteger {
        val allBytes = inputs.fold(ByteArray(0)) { acc, big -> acc + padTo(big, PAD_L) }
        val d = SHA512Digest()
        d.update(allBytes, 0, allBytes.size)
        val out = ByteArray(hashLen)
        d.doFinal(out, 0)
        return BigInteger(1, out)
    }

    private fun H_nn(bn1: BigInteger, bn2: BigInteger): BigInteger {
        return H_padded(bn1, bn2)
    }

    private fun H_ns(n: BigInteger, saltBytes: ByteArray): BigInteger {
        val nMinimal = n.toByteArray().stripLeadingZero()
        val concatenated = nMinimal + saltBytes
        val digest = SHA512Digest()
        digest.update(concatenated, 0, concatenated.size)
        val out = ByteArray(hashLen)
        digest.doFinal(out, 0)
        return BigInteger(1, out)
    }

    private fun calculate_x(salt: BigInteger): BigInteger {
        val userColonPass = username.toByteArray(Charsets.US_ASCII) + byteArrayOf(0x3A /* : */) + password.toByteArray(Charsets.US_ASCII)
        val ucpHash = hash(userColonPass)
        return H_ns(salt, ucpHash)
    }

    private fun hashBigInteger(value: BigInteger): ByteArray {
        val raw = value.toByteArray().stripLeadingZero()
        return hash(raw)
    }

    private fun hash(data: ByteArray): ByteArray {
        val d = SHA512Digest()
        d.update(data, 0, data.size)
        val out = ByteArray(hashLen)
        d.doFinal(out, 0)
        return out
    }

    private fun calculate_M(saltBytes: ByteArray, Aint: BigInteger, Bint: BigInteger, K: ByteArray): ByteArray {
        val H_N = hashBigInteger(N)
        val H_g = hashBigInteger(g)
        val H_xor = ByteArray(hashLen) { i -> (H_N[i].toInt() xor H_g[i].toInt()).toByte() }
        val H_I = hash(username.toByteArray(Charsets.UTF_8))
        val Abytes = Aint.toByteArray().stripLeadingZero()
        val Bbytes = Bint.toByteArray().stripLeadingZero()
        val mDigest = SHA512Digest()
        mDigest.update(H_xor, 0, hashLen)
        mDigest.update(H_I, 0, hashLen)
        mDigest.update(saltBytes, 0, saltBytes.size)
        mDigest.update(Abytes, 0, Abytes.size)
        mDigest.update(Bbytes, 0, Bbytes.size)
        mDigest.update(K, 0, hashLen)
        val mOut = ByteArray(hashLen)
        mDigest.doFinal(mOut, 0)
        return mOut
    }

    private fun calculate_H_AMK(Aint: BigInteger, M: ByteArray, K: ByteArray): ByteArray {
        val Abytes = Aint.toByteArray().stripLeadingZero()
        val hamkDigest = SHA512Digest()
        hamkDigest.update(Abytes, 0, Abytes.size)
        hamkDigest.update(M, 0, hashLen)
        hamkDigest.update(K, 0, hashLen)
        val out = ByteArray(hashLen)
        hamkDigest.doFinal(out, 0)
        return out
    }

    private fun padTo(value: BigInteger, length: Int): ByteArray {
        val minimal = value.toByteArray().stripLeadingZero()
        return if (minimal.size == length) {
            minimal
        } else if (minimal.size < length) {
            ByteArray(length - minimal.size) { 0 } + minimal
        } else {
            minimal.copyOfRange(minimal.size - length, minimal.size)
        }
    }
}