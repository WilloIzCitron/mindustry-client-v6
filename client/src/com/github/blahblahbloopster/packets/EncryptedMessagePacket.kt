package com.github.blahblahbloopster.packets

import com.github.blahblahbloopster.crypto.remainingBytes
import java.nio.ByteBuffer
import java.time.Instant

class EncryptedMessagePacket : Packet<EncryptedMessagePacket> {
    val timestamp: Instant
    val ciphertext: ByteArray

    constructor(timestamp: Instant, ciphertext: ByteArray) {
        this.timestamp = timestamp
        this.ciphertext = ciphertext
    }

    constructor(input: ByteArray, header: Packet.PacketHeader) {
        val buf = ByteBuffer.wrap(input)
        timestamp = Instant.ofEpochSecond(buf.long)
        ciphertext = buf.remainingBytes()
    }

    override fun serialize(): ByteArray {
        val buf = ByteBuffer.allocate(ciphertext.size + Long.SIZE_BYTES)
        buf.putLong(timestamp.epochSecond)
        buf.put(ciphertext)
        return buf.array()
    }
}