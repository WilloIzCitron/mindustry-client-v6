package com.github.blahblahbloopster

import arc.Core
import arc.graphics.Color
import arc.math.geom.Vec2
import arc.util.CommandHandler
import arc.util.serialization.Base64Coder
import com.github.blahblahbloopster.crypto.Crypto
import com.github.blahblahbloopster.crypto.KeyFolder
import com.github.blahblahbloopster.ui.ChangelogDialog
import com.github.blahblahbloopster.ui.FeaturesDialog
import com.github.blahblahbloopster.ui.FindDialog
import mindustry.Vars
import mindustry.client.Client
import mindustry.client.ClientInterface
import com.github.blahblahbloopster.navigation.AssistPath
import com.github.blahblahbloopster.ui.KeyShareDialog
import mindustry.client.navigation.Navigation
import mindustry.client.utils.FloatEmbed
import mindustry.gen.Player

class ClientMapping : ClientInterface {

    override fun registerCommands(handler: CommandHandler?) {
        handler ?: return
        handler.register<Player>("e", "<destination> [message...]", "description") { args, _ ->
            val key = KeyFolder.find { it.name.equals(args[0], ignoreCase = true) }
            key ?: run {
                Vars.ui.chatfrag.addMessage("Invalid destination", "client", Color.coral.cpy().mul(0.75f))
                return@register
            }
            Main.messageCrypto.encrypt(args[1], key)
        }
    }

    override fun showFindDialog() {
        FindDialog.show()
    }

    override fun showChangelogDialog() {
        ChangelogDialog.show()
    }

    override fun showFeaturesDialog() {
        FeaturesDialog.show()
    }

    override fun setAssistPath(player: Player?) {
        Navigation.follow(AssistPath(player))
    }

    override fun floatEmbed(): Vec2 {
        return when {
            Navigation.currentlyFollowing is AssistPath && Core.settings.getBool("displayasuser") ->
                Vec2(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, Client.FOO_USER),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, Client.ASSISTING)
                )
            Core.settings.getBool("displayasuser") ->
                Vec2(
                    FloatEmbed.embedInFloat(Vars.player.unit().aimX, Client.FOO_USER),
                    FloatEmbed.embedInFloat(Vars.player.unit().aimY, Client.FOO_USER)
                )
            else -> Vec2(Vars.player.unit().aimX, Vars.player.unit().aimY)
        }
    }

    override fun generateKey() {
        val generate = {
            val quad = Crypto.generateKeyQuad()
            Core.settings.dataDirectory.child("key.txt").writeString((Base64Coder.encode(quad.serialize()).concatToString()), false)
            Main.messageCrypto.keyQuad = quad
        }

        if (Main.messageCrypto.keyQuad != null) {
            Vars.ui.showConfirm("Key Overwrite",
                "This will irreversibly overwrite your key.  Are you sure you want to do this?", generate)
        } else {
            generate()
        }
    }

    override fun shareKey() {
        KeyShareDialog().show()
    }
}
