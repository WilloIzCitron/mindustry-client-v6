package com.github.blahblahbloopster.crypto

import arc.Core
import arc.files.Fi
import arc.util.serialization.Base64Coder
import com.github.blahblahbloopster.Initializable

object KeyFolder : Initializable, Iterable<KeyHolder>, MutableSet<KeyHolder> {
    private lateinit var keyFile: Fi
    private var keyList = mutableSetOf<KeyHolder>()
    override val size get() = keyList.size

    override fun initializeAlways() {
        keyFile = Core.settings.dataDirectory.child("publicKeys")
        if (!keyFile.exists()) {
            keyFile.writeBytes(byteArrayOf())
        }
        keyFile.readString().lines().forEach {
            val name = it.split(": ")[0]
            val base64 = it.split(": ")[1]
            keyList.add(KeyHolder(PublicKeyPair(Base64Coder.decode(base64)), name, false))
        }
    }

    override fun iterator() = keyList.iterator()

    override fun add(element: KeyHolder): Boolean {
        keyFile.writeString(element.name + ": " + Base64Coder.encode(element.keys.serialize()).concatToString() + "\n", true)
        return keyList.add(element)
    }

    override fun addAll(elements: Collection<KeyHolder>): Boolean {
        for (element in elements) {
            add(element)
        }
        return true
    }

    override fun clear() {
        keyFile.writeString("")
    }

    override fun contains(element: KeyHolder): Boolean {
        return keyList.contains(element)
    }

    override fun containsAll(elements: Collection<KeyHolder>): Boolean {
        return keyList.containsAll(elements)
    }

    override fun isEmpty(): Boolean {
        return keyList.isEmpty()
    }

    override fun remove(element: KeyHolder): Boolean {
        keyFile.writeString(keyFile.readString().lines().filter { it.split(": ").first() != element.name }.joinToString("\n"))
        return keyList.remove(element)
    }

    override fun removeAll(elements: Collection<KeyHolder>): Boolean {
        val names = elements.map { it.name }
        keyFile.writeString(keyFile.readString().lines().filter { !names.contains(it.split(": ").first()) }.joinToString("\n"))
        return keyList.removeAll(elements)
    }

    override fun retainAll(elements: Collection<KeyHolder>): Boolean {
        // todo: slooooooow
        val out = keyList.retainAll(elements)
        clear()
        addAll(keyList)
        return out
    }
}
