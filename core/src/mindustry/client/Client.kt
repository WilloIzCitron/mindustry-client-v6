package mindustry.client

import arc.*
import arc.graphics.*
import arc.math.*
import arc.util.*
import mindustry.Vars.*
import mindustry.client.ClientVars.*
import mindustry.client.Main.setPluginNetworking
import mindustry.client.Spectate.spectate
import mindustry.client.antigrief.*
import mindustry.client.communication.*
import mindustry.client.navigation.*
import mindustry.client.ui.*
import mindustry.client.utils.*
import mindustry.content.*
import mindustry.core.*
import mindustry.entities.*
import mindustry.entities.units.*
import mindustry.game.EventType.*
import mindustry.gen.*
import mindustry.input.*
import mindustry.net.*
import mindustry.world.blocks.power.*
import kotlin.math.*
import kotlin.random.*


object Client {

    fun initialize() {
        registerCommands()

        Events.on(ServerJoinEvent::class.java) { // Run when the player joins a server
            setPluginNetworking(false)
        }

        Events.on(WorldLoadEvent::class.java) {
            lastJoinTime = Time.millis()
            PowerInfo.initialize()
            Navigation.stopFollowing()
            Navigation.obstacles.clear()
            configs.clear()
            ui.unitPicker.type = null
            control.input.lastVirusWarning = null
            dispatchingBuildPlans = false
            hidingBlocks = false
            hidingUnits = false
            showingTurrets = false
            if (state.rules.pvp) ui.announce("[scarlet]Don't use a client in pvp, it's uncool!", 5f)
        }

        Events.on(ClientLoadEvent::class.java) {
            val changeHash = Core.files.internal("changelog").readString().hashCode() // Display changelog if the file contents have changed & on first run. (this is really scuffed lol)
            if (Core.settings.getInt("changeHash") != changeHash) ChangelogDialog.show()
            Core.settings.put("changeHash", changeHash)

            if (Core.settings.getBool("debug")) Log.level = Log.LogLevel.debug // Set log level to debug if the setting is checked
            if (Core.settings.getBool("discordrpc")) platform.startDiscord()
            if (Core.settings.getBool("mobileui")) mobile = !mobile

            Autocomplete.autocompleters.add(BlockEmotes())
            Autocomplete.autocompleters.add(PlayerCompletion())
            Autocomplete.autocompleters.add(CommandCompletion())

            Autocomplete.initialize()

            Navigation.navigator.init()
        }

        Events.on(PlayerJoin::class.java) { e ->
            if (e.player == null) return@on

            if (Core.settings.getBool("clientjoinleave") && (ui.chatfrag.messages.isEmpty || !ui.chatfrag.messages.first().message.equals("[accent]${e.player.name}[accent] has connected.")) && Time.timeSinceMillis(lastJoinTime) > 10000)
                player.sendMessage(Core.bundle.format("client.connected", e.player.name))
        }

        Events.on(PlayerLeave::class.java) { e ->
            if (e.player == null) return@on

            if (Core.settings.getBool("clientjoinleave") && (ui.chatfrag.messages.isEmpty || !ui.chatfrag.messages.first().message.equals("[accent]${e.player.name}[accent] has disconnected.")))
                player.sendMessage(Core.bundle.format("client.disconnected", e.player.name))
        }
    }

    fun update() {
        Navigation.update()
        PowerInfo.update()
        Spectate.update()
        if (!configs.isEmpty) {
            try {
                if (configRateLimit.allow(Administration.Config.interactRateWindow.num() * 1000L, Administration.Config.interactRateLimit.num())) {
                    val req = configs.removeLast()
                    val tile = world.tile(req.x, req.y)
                    if (tile != null) {
//                            Object initial = tile.build.config();
                        req.run()
                        //                            Timer.schedule(() -> {
//                                 if(tile.build != null && tile.build.config() == initial) configs.addLast(req); TODO: This can also cause loops
//                                 if(tile.build != null && req.value != tile.build.config()) configs.addLast(req); TODO: This infinite loops if u config something twice, find a better way to do this
//                            }, net.client() ? netClient.getPing()/1000f+.05f : .025f);
                    }
                }
            } catch (e: Exception) {
                Log.err(e)
            }
        }
    }

    private fun registerCommands() {
        register("help [page]", Core.bundle.get("client.command.help.description")) { args, player ->
            if (args.isNotEmpty() && !Strings.canParseInt(args[0])) {
                player.sendMessage("[scarlet]'page' must be a number.")
                return@register
            }
            val commandsPerPage = 6
            var page = if (args.isNotEmpty()) Strings.parseInt(args[0]) else 1
            val pages = Mathf.ceil(clientCommandHandler.commandList.size.toFloat() / commandsPerPage)
            page--
            if (page >= pages || page < 0) {
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] $pages[scarlet].")
                return@register
            }
            val result = StringBuilder()
            result.append(Strings.format("[orange]-- Client Commands Page[lightgray] @[gray]/[lightgray]@[orange] --\n\n", page + 1, pages))
            for (i in commandsPerPage * page until (commandsPerPage * (page + 1)).coerceAtMost(clientCommandHandler.commandList.size)) {
                val command = clientCommandHandler.commandList[i]
                result.append("[orange] !").append(command.text).append("[white] ").append(command.paramText)
                    .append("[lightgray] - ").append(command.description).append("\n")
            }
            player.sendMessage(result.toString())
        }

        register("unit <unit-type>", Core.bundle.get("client.command.unit.description")) { args, _ ->
            ui.unitPicker.findUnit(content.units().copy().sort { b -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], b.localizedName) }.first())
        }

        register("count <unit-type>", Core.bundle.get("client.command.count.description")) { args, player ->
            val unit = content.units().copy().sort { b -> BiasedLevenshtein.biasedLevenshteinInsensitive(args[0], b.localizedName) }.first()
            // TODO: Make this check each unit to see if it is a player/formation unit, display that info
            player.sendMessage(Strings.format("[accent]@: @/@", unit.localizedName, player.team().data().countType(unit), Units.getCap(player.team())))
        }

        register("go [x] [y]", Core.bundle.get("client.command.go.description")) { args, player ->
            try {
                if (args.size == 2) lastSentPos.set(args[0].toFloat(), args[1].toFloat())
                Navigation.navigateTo(lastSentPos.cpy().scl(tilesize.toFloat()))
            } catch (e: Exception) {
                player.sendMessage(Core.bundle.format("client.command.coordsinvalid", clientCommandHandler.prefix + "go"))
            }
        }

        register("lookat [x] [y]", Core.bundle.get("client.command.lookat.description")) { args, player ->
            try {
                DesktopInput.panning = true
                if (args.size == 2) lastSentPos.set(args[0].toFloat(), args[1].toFloat())
                spectate(lastSentPos.cpy().scl(tilesize.toFloat()))
            } catch (e: Exception) {
                player.sendMessage(Core.bundle.format("client.command.coordsinvalid", clientCommandHandler.prefix + "lookat"))
            }
        }

        register("tp [x] [y]", Core.bundle.get("client.command.tp.description")) { args, player ->
            try {
                if (args.size == 2) lastSentPos.set(args[0].toFloat(), args[1].toFloat())
                NetClient.setPosition(lastSentPos.cpy().scl(tilesize.toFloat()).x, lastSentPos.cpy().scl(tilesize.toFloat()).y)
            } catch (e: Exception) {
                player.sendMessage(Core.bundle.format("client.command.coordsinvalid", clientCommandHandler.prefix + "tp"))
            }
        }

        register("here [message...]", Core.bundle.get("client.command.here.description")) { args, player ->
            Call.sendChatMessage(Strings.format("@(@, @)", if (args.isEmpty()) "" else args[0] + " ", player.tileX(), player.tileY()))
        }

        register("cursor [message...]", Core.bundle.get("client.command.cursor.description")) { args, _ ->
            Call.sendChatMessage(Strings.format("@(@, @)", if (args.isEmpty()) "" else args[0] + " ", control.input.rawTileX(), control.input.rawTileY()))
        }

        register("builder [options...]", Core.bundle.get("client.command.builder.description")) { args, _: Player ->
            Navigation.follow(BuildPath(if (args.isEmpty()) "" else args[0]))
        } // TODO: This is so scuffed lol

        register("miner [options...]", Core.bundle.get("client.command.miner.description")) { args, _: Player ->
            Navigation.follow(MinePath(if (args.isEmpty()) "" else args[0]))
        } // TODO: This is so scuffed lol

        register(" [message...]", Core.bundle.get("client.command.!.description")) { args, _ ->
            Call.sendChatMessage("!" + if (args.size == 1) args[0] else "")
        }

        register("shrug [message...]", Core.bundle.get("client.command.shrug.description")) { args, _ ->
            Call.sendChatMessage("¯\\_(ツ)_/¯ " + if (args.size == 1) args[0] else "")
        }

        register("login [name] [pw]", Core.bundle.get("client.command.login.description")) { args, _ ->
            if (args.size == 2) Core.settings.put("cnpw", args[0] + " " + args[1])
            else Call.sendChatMessage("/login " + Core.settings.getString("cnpw", ""))
        }

        register("marker <name> [x] [y]", Core.bundle.get("client.command.marker.description")) { args, player ->
            val x = if (args.size == 3) args[1].toIntOrNull() ?: player.tileX() else player.tileX()
            val y = if (args.size == 3) args[2].toIntOrNull() ?: player.tileY() else player.tileY()
            val color = Color.HSVtoRGB(Random.nextFloat() * 360, 75f, 75f)
            Markers.add(Markers.Marker(x, y, args[0], color))
            player.sendMessage(Core.bundle.format("client.command.marker.added", x, y))
        }

        register("js <code...>", Core.bundle.get("client.command.js.description")) { args, player: Player ->
            player.sendMessage("[accent]" + mods.scripts.runConsole(args[0]))
        }

        register("/js <code...>", Core.bundle.get("client.command.serverjs.description")) { args, player ->
            player.sendMessage("[accent]" + mods.scripts.runConsole(args[0]))
            Call.sendChatMessage("/js " + args[0])
        }

        register("cc [setting]", Core.bundle.get("client.command.cc.description")) { args, player ->
            if (args.size != 1 || !args[0].matches("(?i)^[ari].*".toRegex())) {
                player.sendMessage(Core.bundle.get("client.command.cc.invalid"))
                return@register
            }
            for (tile in world.tiles) {
                if (tile?.build == null || tile.build.team != player.team() || tile.block() != Blocks.commandCenter) continue
                Call.tileConfig(player, tile.build, when (args[0].toLowerCase()[0]) {
                    'a' -> UnitCommand.attack
                    'r' -> UnitCommand.rally
                    else -> UnitCommand.idle
                })
                player.sendMessage(Core.bundle.format("client.command.cc.success", args[0]))
                return@register
            }
            player.sendMessage(Core.bundle.get("client.command.cc.notfound"))
        }

        register("poli", "Spelling is hard. This will make sure you never forget how to spell the plural of poly, you're welcome.") { _, _ ->
            Call.sendChatMessage("Unlike a roly-poly whose plural is roly-polies, the plural form of poly is polys. Please remember this, thanks! :)")
        }

        register("silicone", "Spelling is hard. This will make sure you never forget how to spell silicon, you're welcome.") { _, _ ->
            Call.sendChatMessage("\"In short, silicon is a naturally occurring chemical element, whereas silicone is a synthetic substance.\" They are not the same, please get it right!")
        }

        register("togglesign", Core.bundle.get("client.command.togglesign.description")) { _, player ->
            signMessages = !signMessages
            player.sendMessage(Core.bundle.format("client.command.togglesign.success", Core.bundle.get(if (signMessages) "on" else "off")))
        }

        register("networking", Core.bundle.get("client.command.networking.description")) { _, player ->
            if (Main.communicationSystem.activeCommunicationSystem == PluginCommunicationSystem) {
                player.sendMessage("[accent]Using plugin communication system provided by the server.")
                return@register
            }
            val build = MessageBlockCommunicationSystem.findProcessor() ?: MessageBlockCommunicationSystem.findMessage()
            if (build == null) player.sendMessage("[scarlet]No valid processor or message block found; communication system inactive.")
            else player.sendMessage("[accent]${build.block.localizedName} at (${build.tileX()}, ${build.tileY()}) in use for communication.")
        }

        register("e <destination> <message...>", Core.bundle.get("client.command.e.description")) { args, _ ->
            for (key in Main.messageCrypto.keys) {
                if (key.name.equals(args[0], true)) {
                    Main.messageCrypto.encrypt(args[1], key)
                    return@register
                }
            }
            Toast(3f).add("@client.invalidkey")
        }

        register("fixpower [c]", Core.bundle.get("client.command.fixpower.description")) { args, player ->
            val confirmed = args.any() && args[0] == "c" // Don't configure by default
            var n = 0
            val grids = mutableMapOf<Int, MutableSet<Int>>()
            for (grid in PowerGraph.activeGraphs.filter { g -> g.team == player.team() }) {
                for (nodeBuild in grid.all) {
                    val nodeBlock = nodeBuild.block as? PowerNode ?: continue
                    var links = nodeBuild.power.links.size
                    nodeBlock.getPotentialLinks(nodeBuild.tile, player.team()) { link ->
                        if (PowerDiode.connected.any { it.first == min(grid.id, link.power.graph.id) && it.second == max(grid.id, link.power.graph.id) }) return@getPotentialLinks // Don't connect across diodes
                        if (++links > nodeBlock.maxNodes) return@getPotentialLinks // Respect max links
                        val t = grids.getOrPut(grid.id) { mutableSetOf(grid.id) }
                        val l = grids.getOrDefault(link.power.graph.id, mutableSetOf())
                        if (l.add(grid.id) && t.add(link.power.graph.id)) {
                            l.addAll(t)
                            grids[link.power.graph.id] = l
                            if (confirmed) configs.add(ConfigRequest(nodeBuild.tileX(), nodeBuild.tileY(), link.pos()))
                            n++
                        }
                    }
                }
            }
            if (confirmed) {
                player.sendMessage(Core.bundle.format("client.command.fixpower.success", n))
            } else {
                player.sendMessage(Core.bundle.format("client.command.fixpower.confirm", n, PowerGraph.activeGraphs.size))
            }
        }
    }

    /** Registers a command.
     *
     * @param format The format of the command, basically name and parameters together. Example:
     *      "help [page]"
     * @param description The description of the command.
     * @param runner A lambda to run when the command is invoked.
     */
    fun register(format: String, description: String = "", runner: (args: Array<String>, player: Player) -> Unit) {
        val args = if (format.contains(' ')) format.substringAfter(' ') else ""
        clientCommandHandler.register(format.substringBefore(' '), args, description, runner)
    }
}
