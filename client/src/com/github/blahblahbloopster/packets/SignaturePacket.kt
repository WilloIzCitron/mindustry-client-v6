package com.github.blahblahbloopster.packets

import com.github.blahblahbloopster.crypto.Crypto
import com.github.blahblahbloopster.crypto.bytes
import java.nio.ByteBuffer
import java.time.Instant

class SignaturePacket : Packet<SignaturePacket> {
    val time: Instant
    val senderId: Int
    val signature: ByteArray

    constructor(signature: ByteArray, time: Instant, senderId: Int) {
        this.signature = signature
        this.time = time
        this.senderId = senderId
    }

    constructor(input: ByteArray, header: Packet.PacketHeader) {
        val buf = ByteBuffer.wrap(input)
        time = Instant.ofEpochSecond(buf.long)
        senderId = buf.int
        signature = buf.bytes(Crypto.signatureSize)
    }

    override fun serialize(): ByteArray {
        val buf = ByteBuffer.allocate(Long.SIZE_BYTES + Int.SIZE_BYTES + signature.size)
        buf.putLong(time.epochSecond)
        buf.putInt(senderId)
        buf.put(signature)
        return buf.array()
    }
}
