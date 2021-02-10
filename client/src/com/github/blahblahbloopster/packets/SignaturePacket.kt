package com.github.blahblahbloopster.packets

class SignaturePacket : Packet<SignaturePacket> {
    var original: String? = null

    override fun serialize(): ByteArray {
        TODO("Not yet implemented")
    }

    override fun deserialize(input: ByteArray, header: Packet.PacketHeader): SignaturePacket {
        TODO("Not yet implemented")
        }
}
