package com.futo.platformplayer.casting

import com.futo.platformplayer.logging.Logger
import java.io.ByteArrayOutputStream

enum class TLV8Tag(val value: UByte) {
    METHOD(0u),
    IDENTIFIER(1u),
    SALT(2u),
    PUBLIC_KEY(3u),
    PROOF(4u),
    ENCRYPTED_DATA(5u),
    STATE(6u),
    ERROR(7u),
    RETRY_DELAY(8u),
    CERTIFICATE(9u),
    SIGNATURE(0x0Au),
    PERMISSIONS(0x0Bu),
    FRAGMENT_DATA(0x0Cu),
    FRAGMENT_LAST(0x0Du),
    FLAGS(0x13u),
    SEPARATOR(0xFFu)
}

data class TLV8Item(val tag: TLV8Tag, val value: UByteArray) {
    override fun toString(): String {
        val tagHex = "%02X".format(tag.value.toInt())
        val dataHex = value.joinToString(" ") { "%02X".format(it.toInt()) }
        return "${tag.name}(0x$tagHex): $dataHex"
    }

    companion object {
        private const val TAG = "AirPlayTLV8"
        private const val FRAGMENT_THRESHOLD = 0xFF

        fun decodeAndReassembleWithLogging(data: UByteArray): Map<TLV8Tag, ByteArray> {
            val items = decode(data)
            Logger.i(TAG, "Raw TLV8 items:\n" + items.joinToString("\n") { it.toString() })

            val fields = items
                .groupBy { it.tag }
                .mapValues { (_, chunk) ->
                    chunk.fold(UByteArray(0)) { acc, item -> acc + item.value }.toByteArray()
                }

            Logger.i(TAG, "Reassembled TLV8 fields:\n" +
                    fields.entries.joinToString("\n") { (tag, bytes) ->
                        "%-12s: %s".format(tag.name,
                            bytes.joinToString(" ") { "%02X".format(it) })
                    }
            )

            return fields
        }

        fun decodeAsString(data: UByteArray): String {
            return decode(data).joinToString("\n") { it.toString() }
        }

        fun itemsToString(items: List<TLV8Item>): String = items.joinToString(separator = "\n") { it.toString() }

        fun encodeWithLogging(items: List<TLV8Item>, useFragmentData: Boolean = false): ByteArray {
            Logger.i(TAG, "Assembled TLV8 items:\n" + itemsToString(items))

            val fragments = if (useFragmentData) fragmentStandard(items) else fragmentRepeat(items)
            Logger.i(TAG, "Split TLV8 items:\n" + itemsToString(fragments))

            val out = ByteArrayOutputStream()
            fragments.forEach { frag ->
                val data = frag.value.asByteArray()
                out.write(frag.tag.value.toInt())
                out.write(data.size)
                out.write(data)
            }
            val encoded = out.toByteArray()
            val hexStream = encoded.joinToString(" ") { "%02X".format(it) }
            Logger.i(TAG, "Final TLV8 byte stream (${encoded.size} bytes):\n$hexStream")

            return encoded
        }

        private fun fragmentStandard(items: List<TLV8Item>): List<TLV8Item> {
            val frags = mutableListOf<TLV8Item>()
            items.forEach { item ->
                val bytes = item.value.asByteArray()
                if (bytes.size <= FRAGMENT_THRESHOLD) {
                    frags += item
                } else {
                    var offset = 0
                    // first fragment with original tag
                    frags += TLV8Item(item.tag, bytes.copyOfRange(0, FRAGMENT_THRESHOLD).toUByteArray())
                    offset += FRAGMENT_THRESHOLD

                    // middle fragments
                    while (bytes.size - offset > FRAGMENT_THRESHOLD) {
                        frags += TLV8Item(
                            TLV8Tag.FRAGMENT_DATA,
                            bytes.copyOfRange(offset, offset + FRAGMENT_THRESHOLD).toUByteArray()
                        )
                        offset += FRAGMENT_THRESHOLD
                    }

                    // last fragment
                    val rem = bytes.size - offset
                    frags += TLV8Item(
                        TLV8Tag.FRAGMENT_LAST,
                        bytes.copyOfRange(offset, offset + rem).toUByteArray()
                    )
                }
            }
            return frags
        }

        private fun fragmentRepeat(items: List<TLV8Item>): List<TLV8Item> {
            val frags = mutableListOf<TLV8Item>()
            items.forEach { item ->
                val bytes = item.value.asByteArray()
                var offset = 0
                while (offset < bytes.size) {
                    val chunk = minOf(FRAGMENT_THRESHOLD, bytes.size - offset)
                    frags += TLV8Item(
                        item.tag,
                        bytes.copyOfRange(offset, offset + chunk).toUByteArray()
                    )
                    offset += chunk
                }
            }
            return frags
        }

        fun decode(data: UByteArray): List<TLV8Item> {
            val items = mutableListOf<TLV8Item>()
            var i = 0

            while (i < data.size) {
                val tagByte = data[i]
                val tag = TLV8Tag.values().find { it.value == tagByte }
                    ?: throw IllegalArgumentException("Unknown tag 0x${tagByte.toString(16)} at offset $i")
                if (i + 1 >= data.size) {
                    throw IllegalArgumentException("Truncated TLV: no length byte for tag $tag at offset $i")
                }

                val length = data[i + 1].toInt() and 0xFF
                i += 2
                if (i + length > data.size) {
                    throw IllegalArgumentException("Truncated TLV: declared length $length exceeds available bytes (${data.size - i})")
                }

                var value = data.copyOfRange(i, i + length)
                i += length

                if (length == FRAGMENT_THRESHOLD && i < data.size) {
                    val nextTag = data[i]
                    if (nextTag == TLV8Tag.FRAGMENT_DATA.value ||
                        nextTag == TLV8Tag.FRAGMENT_LAST.value
                    ) {
                        while (true) {
                            if (i + 2 > data.size) {
                                throw IllegalArgumentException("Truncated fragment header at offset $i")
                            }
                            val fragTagByte = data[i]
                            val fragTag = TLV8Tag.values().find { it.value == fragTagByte }
                                ?: throw IllegalArgumentException("Unknown fragment tag 0x${fragTagByte.toString(16)} at offset $i")
                            val fragLen = data[i + 1].toInt() and 0xFF
                            i += 2
                            if (i + fragLen > data.size) {
                                throw IllegalArgumentException("Truncated fragment: declared length $fragLen exceeds available bytes (${data.size - i})")
                            }
                            val fragData = data.copyOfRange(i, i + fragLen)
                            value += fragData
                            i += fragLen

                            if (fragTag == TLV8Tag.FRAGMENT_LAST) break
                            if (fragTag != TLV8Tag.FRAGMENT_DATA) {
                                throw IllegalArgumentException("Unexpected tag $fragTag in fragment sequence")
                            }
                        }
                    }
                }

                items += TLV8Item(tag, value)
            }

            return items
        }
    }
}
