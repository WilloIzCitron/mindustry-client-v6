package com.github.blahblahbloopster.crypto

import arc.Core
import arc.Events
import arc.graphics.Color
import arc.util.Log
import arc.util.Time
import arc.util.serialization.Base64Coder
import com.github.blahblahbloopster.packets.EncryptedMessagePacket
import com.github.blahblahbloopster.packets.Packet
import com.github.blahblahbloopster.packets.SignaturePacket
import mindustry.Vars
import mindustry.game.EventType
import java.nio.ByteBuffer
import java.time.Instant
import java.util.zip.DeflaterInputStream
import java.util.zip.InflaterInputStream
import kotlin.math.abs

/** Provides the interface between [Crypto] and a [CommunicationSystem] */
class MessageCrypto {
    lateinit var communicationSystem: CommunicationSystem
    var keyQuad: KeyQuad? = null

    var player = PlayerTriple(0, 0, "")  // Maps player ID to last sent message
    var received = ReceivedTriple(0, 0, null) // Maps player ID to last sent message

    companion object {
        private const val ENCRYPTION_VALIDITY = 0b10101010.toByte()

        fun base64public(input: String): PublicKeyPair? {
            return try {
                PublicKeyPair(Base64Coder.decode(input))
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

    data class PlayerTriple(val id: Int, val time: Long, val message: String)

    data class ReceivedTriple(val id: Int, val time: Long, val signaturePacket: SignaturePacket?)

    fun init(communicationSystem: CommunicationSystem) {
        this.communicationSystem = communicationSystem

        if (Core.settings.dataDirectory.child("key.txt").exists()) {
            try {
                keyQuad = KeyQuad(Base64Coder.decode(Core.settings.dataDirectory.child("key.txt").readString()))
                Log.info("Loaded keypair")
            } catch (ignored: Exception) {}

            Events.on(EventType.SendChatMessageEvent::class.java) { event ->
                sign(event.message, keyQuad ?: return@on)
                Time.run(0.05f) {
                    val message = Vars.ui.chatfrag.messages.find { it.message.contains(player.message) } ?: return@run
                    message.backgroundColor = Color.green.cpy().mul(0.4f)
                }
            }
        }

        Events.on(EventType.PlayerChatEventClient::class.java) { event ->
            player = PlayerTriple((event.player ?: return@on).id, Instant.now().epochSecond, event.message)
            check(player, received)
        }
    }

    fun base64public(): String? {
        return Base64Coder.encode(PublicKeyPair(keyQuad ?: return null).serialize()).toString()
    }

    /** Checks the validity of a message given two triples, see above. */
    fun check(player: PlayerTriple, received: ReceivedTriple) {
        if (player.id == 0 || player.time == 0L || player.message == "") return
        if (received.id == 0 || received.time == 0L || received.signaturePacket == null) return
        val time = Instant.now().epochSecond
        if (abs(player.time - time) > 3 || abs(received.time - time) > 3) {
            return
        }

        if (player.id != received.id) {
            return
        }

        for (key in KeyFolder) {
            val match = verify(player.message, player.id, received.signaturePacket.signature, key.keys)
            if (match) {
                val message = Vars.ui.chatfrag.messages.find { it.message.contains(player.message) } ?: break
                message.backgroundColor = Color.green.cpy().mul(if (key.official) 0.75f else 0.4f)
                message.verifiedSender = key.name
                message.format()
                break
            }
        }
    }

    /**
     *  Converts an input message, sender ID, and current time (unix time) to a [ByteArray] for sending.
     *  The message isn't used on its own because it would be vulnerable to replay attacks.
     */
    private fun stringToSendable(input: String, sender: Int, time: Long): ByteArray {
        val output = input.toByteArray().toMutableList()
        output.addAll(ByteBuffer.allocate(4).putInt(sender).array().toList())  // Add sender ID
        output.addAll(ByteBuffer.allocate(8).putLong(time).array().toList())  // Add current time
        return output.toByteArray()
    }

    /** Signs an outgoing message.  Includes the sender ID and current time to prevent impersonation and replay attacks. */
    fun sign(message: String, key: KeyQuad) {
        val time = Instant.now()
        val signature = Crypto.sign(stringToSendable(message, communicationSystem.id, time.epochSecond), key.edPrivateKey)
        Packet.send(SignaturePacket(signature, time, communicationSystem.id))
    }

    fun plaintextToEncryptable(plaintext: ByteArray, time: Long, id: Int): ByteArray {
        val buf = ByteBuffer.allocate(plaintext.size + Long.SIZE_BYTES + Int.SIZE_BYTES + Byte.SIZE_BYTES)
        buf.putLong(time)
        buf.putInt(id)
        buf.put(plaintext)
        buf.put(ENCRYPTION_VALIDITY)
        return buf.array()
    }

    data class EncryptionMetadata(val time: Long, val id: Int, val plaintext: ByteArray)

    fun decryptedToPlaintext(inp: ByteArray): EncryptionMetadata {
        val buf = ByteBuffer.wrap(inp)
        val time = buf.long
        val id = buf.int
        val plaintext = buf.bytes(buf.remaining() - 1)
        return EncryptionMetadata(time, id, plaintext)
    }

    fun encrypt(message: String, destination: KeyHolder) {
        val time = Instant.now().epochSecond
        val id = communicationSystem.id
        val compressor = DeflaterInputStream(message.toByteArray().inputStream())
        val encoded = compressor.readAllBytes()
        val plaintext = plaintextToEncryptable(encoded, time, id)
        val ciphertext = destination.crypto?.encrypt(plaintext) ?: return
        Packet.send(EncryptedMessagePacket(Instant.ofEpochSecond(time), ciphertext))
    }

    fun decrypt(input: EncryptedMessagePacket, header: Packet.PacketHeader, sender: Int) {
        val crypto = KeyFolder.find { it.crypto?.decrypt(input.ciphertext)?.last() == ENCRYPTION_VALIDITY } ?: return
        val decrypted = decryptedToPlaintext(crypto.crypto?.decrypt(input.ciphertext) ?: return)
        if (abs(Instant.now().epochSecond - decrypted.time) > 3) {
            return  // Expired data
        }
        if (decrypted.id != sender) {
            return  // Sender doesn't match
        }
        println(InflaterInputStream(decrypted.plaintext.inputStream()).readAllBytes().decodeToString())
    }

    /** Verifies an incoming message. */
    fun verify(message: String, sender: Int, signature: ByteArray, key: PublicKeyPair): Boolean {
        if (signature.size != Crypto.signatureSize + 12) {
            return false
        }
        val buffer = ByteBuffer.wrap(signature)
        val time = buffer.long
        val senderId = buffer.int
        val bytes = buffer.array()
        val signatureBytes = bytes.takeLast(Crypto.signatureSize).toByteArray()

        val original = stringToSendable(message, sender, time)

        return try {
            val valid = Crypto.verify(original, signatureBytes, key.edPublicKey)
            abs(Instant.now().epochSecond - time) < 3 &&
                    sender == senderId &&
                    valid
        } catch (ignored: Exception) {
            false
        }
    }
}
