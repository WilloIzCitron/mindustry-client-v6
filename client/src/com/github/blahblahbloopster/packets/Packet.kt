package com.github.blahblahbloopster.packets

import arc.util.Time
import com.github.blahblahbloopster.Main
import com.github.blahblahbloopster.crypto.MessageCrypto
import java.nio.ByteBuffer
import java.time.Instant
import kotlin.random.Random
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

interface Packet<T> {

    companion object {

        private val registeredTypes: List<RegisteredPacketType<*>> = listOf(
            RegisteredPacketType(SignaturePacket::class, 1, ::SignaturePacket),
            RegisteredPacketType(EncryptedMessagePacket::class, 2, ::EncryptedMessagePacket)
        )

        data class RegisteredPacketType<T : Packet<*>>(val type: KClass<T>, val id: Int, val constructor: (ByteArray, PacketHeader) -> T)

        private fun packetId(type: KClass<*>): Int? {
            return registeredTypes.find { it.type == type }?.id
        }

        fun send(packet: Packet<*>) {
            val type = packetId(packet::class) ?: return
            val serialized = packet.serialize()
            val chunked = serialized.toList().chunked(Main.communicationSystem.maxLength - PacketHeader.HEADER_SIZE)
            val id = Random.nextInt()

            for ((i, chunk) in chunked.withIndex()) {
                Time.run(0.5f * i) {
                    val toSend = ByteBuffer.allocate(chunk.size + PacketHeader.HEADER_SIZE)
                    PacketHeader(Instant.now().plusMillis(10_000 + (500L * i)), i, chunked.size, type, id).write(toSend)
                    toSend.put(chunk.toByteArray())
                    Main.communicationSystem.send(toSend.array())
                }
            }
        }

        private data class IncomingConnection(val senderId: Int, val id: Int, val totalPackets: Int, val type: Int, val array: MutableList<ByteArray?> = MutableList(totalPackets) { null }) {
            val done get() = !array.contains(null)
        }

        private val incoming = mutableSetOf<IncomingConnection>()

        fun onReceive(input: ByteArray, senderId: Int) {
            if (input.size < PacketHeader.HEADER_SIZE) return
            val buf = ByteBuffer.wrap(input)
            val header = PacketHeader(buf)
            val content = input.copyOfRange(PacketHeader.HEADER_SIZE, input.size - 1)
            if (Instant.now().isAfter(header.expirationTime)) return

            if (header.sequenceNumber == 0 && header.sequenceCount > 1) {
                if (incoming.size < 100) {
                    incoming.add(
                        IncomingConnection(
                            senderId,
                            senderId,
                            header.sequenceCount,
                            header.type
                        ).apply { array[0] = content })
                }
            } else if (header.sequenceNumber == 0 && header.sequenceCount == 1) {
                handle(content, header, senderId)
            } else {
                val found = incoming.find { it.id == senderId } ?: return
                if (found.totalPackets >= header.sequenceNumber) return
                found.array[header.sequenceNumber] = content

                if (found.done) {
                    val reduced = found.array.reduce { acc, bytes -> acc!!.plus(bytes ?: return@reduce acc) }!!
                    handle(reduced, header, senderId)
                    incoming.remove(found)
                }
            }
        }

        private fun handle(fullContents: ByteArray, header: PacketHeader, senderId: Int) {
            val packetType = registeredTypes.find { it.id == header.type } ?: return
            val packet = packetType.constructor(fullContents, header)

            when (packet) {
                is SignaturePacket -> {
                    Main.messageCrypto.received = MessageCrypto.ReceivedTriple(senderId, Instant.now().epochSecond, packet)
                    Main.messageCrypto.check(Main.messageCrypto.player, Main.messageCrypto.received)
                }
                is EncryptedMessagePacket -> {

                }
            }
        }
    }

    fun serialize(): ByteArray

    class PacketHeader {
        val expirationTime: Instant
        val sequenceNumber: Int
        val sequenceCount: Int
        val type: Int
        val id: Int

        constructor(buf: ByteBuffer) {
            expirationTime = Instant.ofEpochSecond(buf.long)
            sequenceNumber = buf.int
            sequenceCount = buf.int
            type = buf.int
            id = buf.int
        }

        constructor(expirationTime: Instant, sequenceNumber: Int, sequenceCount: Int, type: Int, id: Int) {
            this.expirationTime = expirationTime
            this.sequenceNumber = sequenceNumber
            this.sequenceCount = sequenceCount
            this.type = type
            this.id = id
        }

        constructor(expirationTime: Instant, sequenceNumber: Int, sequenceCount: Int, type: Int) : this(expirationTime, sequenceNumber, sequenceCount, type, Random.nextInt())

        fun write(buf: ByteBuffer) {
            buf.putLong(expirationTime.epochSecond)
            buf.putInt(sequenceNumber)
            buf.putInt(sequenceCount)
            buf.putInt(type)
            buf.putInt(id)
        }

        companion object {
            const val HEADER_SIZE = Long.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES + Int.SIZE_BYTES
        }
    }
}
