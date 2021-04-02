package mindustry.client

import arc.Core
import arc.Events
import arc.math.Mathf
import arc.util.Interval
import arc.util.Log
import arc.util.Strings
import arc.util.Time
import mindustry.Vars
import mindustry.client.antigrief.PowerInfo
import mindustry.client.antigrief.TileLog
import mindustry.client.navigation.BuildPath
import mindustry.client.navigation.Navigation
import mindustry.client.utils.*
import mindustry.core.NetClient
import mindustry.core.World
import mindustry.game.EventType.ClientLoadEvent
import mindustry.game.EventType.WorldLoadEvent
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.input.DesktopInput
import mindustry.net.Administration
import mindustry.type.UnitType

object Client {
    private var tileLogs: Array<Array<TileLog?>> = emptyArray()
    @JvmField
    var mapping: ClientInterface? = null
    private var fuelTimer = 0

    /** Last time (millis) that the !fuel command was run  */
    private val timer = Interval()
    @JvmStatic
    fun initialize() {
        registerCommands()
        Events.on(WorldLoadEvent::class.java) { event: WorldLoadEvent? ->
            mapping!!.setPluginNetworking(false)
            if (Time.timeSinceMillis(ClientVars.lastSyncTime) > 5000) {
                tileLogs = Array(Vars.world.height()) { arrayOfNulls(Vars.world.width()) }
                fuelTimer = 0
            }
            PowerInfo.initialize()
            Navigation.stopFollowing()
            Navigation.obstacles.clear()
            ClientVars.configs.clear()
            Vars.ui.unitPicker.found = null
            Vars.control.input.lastVirusWarning = null
            ClientVars.dispatchingBuildPlans = false
            ClientVars.hidingBlocks = ClientVars.dispatchingBuildPlans
            ClientVars.hidingUnits = ClientVars.hidingBlocks
            ClientVars.showingTurrets = ClientVars.hidingUnits
            if (Vars.state.rules.pvp) Vars.ui.announce("[scarlet]Don't use a client in pvp, it's uncool!", 5f)
        }
        Events.on(ClientLoadEvent::class.java) { event: ClientLoadEvent? ->
            val changeHash = Core.files.internal("changelog").readString()
                .hashCode() // Display changelog if the file contents have changed & on first run. (this is really scuffed lol)
            if (Core.settings.getInt("changeHash") != changeHash) mapping!!.showChangelogDialog()
            Core.settings.put("changeHash", changeHash)
            if (Core.settings.getBool("debug")) Log.level =
                Log.LogLevel.debug // Set log level to debug if the setting is checked
            if (Core.settings.getBool("discordrpc")) Vars.platform.startDiscord()
            Autocomplete.autocompleters.add(BlockEmotes())
            Autocomplete.autocompleters.add(PlayerCompletion())
            Autocomplete.autocompleters.add(CommandCompletion())
            Autocomplete.initialize()
            Navigation.navigator.init()
        }
    }

    @JvmStatic
    fun update() {
        Navigation.update()
        PowerInfo.update()
        Spectate.update()
        if (!ClientVars.configs.isEmpty) {
            try {
                if (ClientVars.configRateLimit.allow(
                        Administration.Config.interactRateWindow.num() * 1000L,
                        Administration.Config.interactRateLimit.num()
                    )
                ) {
                    val req = ClientVars.configs.removeLast()
                    val tile = Vars.world.tile(req.x, req.y)
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
        if (timer[Int.MAX_VALUE.toFloat()] || fuelTimer > 0 && timer[(fuelTimer * 60).toFloat()]) { // Auto fuel for cn
            ClientVars.lastFuelTime = Time.millis()
            Call.sendChatMessage("/fuel")
            Time.run(10f) { Call.tileTap(Vars.player, Vars.world.tile(0, 0)) }
            Time.run(20f) {
                Call.tileTap(
                    Vars.player,
                    Vars.world.tile(Vars.world.width() - 1, Vars.world.height() - 1)
                )
            }
        }
    }

    @JvmStatic
    fun getLog(x: Int, y: Int): TileLog? {
        if (tileLogs == null) tileLogs = Array(Vars.world.height()) { arrayOfNulls(Vars.world.width()) }
        if (tileLogs!![y][x] == null) {
            tileLogs!![y][x] = TileLog(Vars.world.tile(x, y))
        }
        return tileLogs!![y][x]
    }

    private fun registerCommands() {
        ClientVars.clientCommandHandler.register(
            "help",
            "[page]",
            "Lists all client commands."
        ) { args: Array<String?>, player: Player ->
            if (args.size > 0 && !Strings.canParseInt(
                    args[0]
                )
            ) {
                player.sendMessage("[scarlet]'page' must be a number.")
                return@register
            }
            val commandsPerPage = 6
            var page = if (args.size > 0) Strings.parseInt(args[0]) else 1
            val pages = Mathf.ceil(ClientVars.clientCommandHandler.commandList.size.toFloat() / commandsPerPage)
            page--
            if (page >= pages || page < 0) {
                player.sendMessage("[scarlet]'page' must be a number between[orange] 1[] and[orange] $pages[scarlet].")
                return@register
            }
            val result = StringBuilder()
            result.append(
                Strings.format(
                    "[orange]-- Client Commands Page[lightgray] @[gray]/[lightgray]@[orange] --\n\n",
                    page + 1,
                    pages
                )
            )
            for (i in commandsPerPage * page until Math.min(
                commandsPerPage * (page + 1),
                ClientVars.clientCommandHandler.commandList.size
            )) {
                val command = ClientVars.clientCommandHandler.commandList[i]
                result.append("[orange] !").append(command.text).append("[white] ").append(command.paramText)
                    .append("[lightgray] - ").append(command.description).append("\n")
            }
            player.sendMessage(result.toString())
        }
        ClientVars.clientCommandHandler.register(
            "unit", "<unit-name>", "Swap to specified unit"
        ) { args: Array<String?>, player: Player? ->
            Vars.ui.unitPicker.findUnit(Vars.content.units().copy().sort { b: UnitType ->
                BiasedLevenshtein.biasedLevenshtein(
                    args[0], b.name
                )
            }.first())
        }
        ClientVars.clientCommandHandler.register(
            "go",
            "[x] [y]",
            "Navigates to (x, y) or the last coordinates posted to chat"
        ) { args: Array<String>, player: Player ->
            try {
                if (args.size == 2) ClientVars.lastSentPos[args[0].toFloat()] = args[1].toFloat()
                Navigation.navigateTo(ClientVars.lastSentPos.cpy().scl(Vars.tilesize.toFloat()))
            } catch (e: NumberFormatException) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !go 10 300 or !go")
            } catch (e: IndexOutOfBoundsException) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !go 10 300 or !go")
            }
        }
        ClientVars.clientCommandHandler.register(
            "lookat",
            "[x] [y]",
            "Moves camera to (x, y) or the last coordinates posted to chat"
        ) { args: Array<String>, player: Player ->
            try {
                DesktopInput.panning = true
                if (args.size == 2) ClientVars.lastSentPos[args[0].toFloat()] = args[1].toFloat()
                Spectate.spectate(ClientVars.lastSentPos.cpy().scl(Vars.tilesize.toFloat()))
            } catch (e: NumberFormatException) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !lookat 10 300 or !lookat")
            } catch (e: IndexOutOfBoundsException) {
                player.sendMessage("[scarlet]Invalid coordinates, format is [x] [y] Eg: !lookat 10 300 or !lookat")
            }
        }
        ClientVars.clientCommandHandler.register(
            "here", "[message...]", "Prints your location to chat with an optional message"
        ) { args: Array<String>, player: Player ->
            Call.sendChatMessage(
                Strings.format(
                    "@(@, @)",
                    if (args.size == 0) "" else args[0] + " ",
                    player.tileX(),
                    player.tileY()
                )
            )
        }
        ClientVars.clientCommandHandler.register(
            "cursor", "[message...]", "Prints cursor location to chat with an optional message"
        ) { args: Array<String>, player: Player? ->
            Call.sendChatMessage(
                Strings.format(
                    "@(@, @)",
                    if (args.size == 0) "" else args[0] + " ",
                    Vars.control.input.rawTileX(),
                    Vars.control.input.rawTileY()
                )
            )
        }
        ClientVars.clientCommandHandler.register(
            "builder", "[options...]", "Starts auto build with optional arguments, prioritized from first to last."
        ) { args: Array<String?>, player: Player? -> Navigation.follow(BuildPath(if (args.size == 0) "" else args[0])) } // TODO: This is so scuffed lol

        ClientVars.clientCommandHandler.register(
            "tp",
            "<x> <y>",
            "Teleports to (x, y), only works on servers without strict mode enabled."
        ) { args: Array<String>, player: Player ->
            try {
                NetClient.setPosition(
                    World.unconv(args[0].toFloat()), World.unconv(
                        args[1].toFloat()
                    )
                )
            } catch (e: Exception) {
                player.sendMessage("[scarlet]Invalid coordinates, format is <x> <y> Eg: !tp 10 300")
            }
        }
        ClientVars.clientCommandHandler.register(
            "", "[message...]", "Lets you start messages with an !"
        ) { args: Array<String>, player: Player? -> Call.sendChatMessage("!" + if (args.size == 1) args[0] else "") }
        ClientVars.clientCommandHandler.register(
            "shrug", "[message...]", "Sends the shrug unicode emoji with an optional message"
        ) { args: Array<String>, player: Player? -> Call.sendChatMessage("¯\\_(ツ)_/¯ " + if (args.size == 1) args[0] else "") }
        ClientVars.clientCommandHandler.register(
            "login",
            "[name] [pw]",
            "Used for CN. [scarlet]Don't use this if you care at all about security."
        ) { args: Array<String>, player: Player? ->
            if (args.size == 2) Core.settings.put(
                "cnpw",
                args[0] + " " + args[1]
            ) else Call.sendChatMessage("/login " + Core.settings.getString("cnpw", ""))
        }
        ClientVars.clientCommandHandler.register(
            "js", "<code...>", "Runs JS on the client."
        ) { args: Array<String?>, player: Player ->
            player.sendMessage(
                "[accent]" + Vars.mods.scripts.runConsole(
                    args[0]
                )
            )
        }
        ClientVars.clientCommandHandler.register(
            "/js",
            "<code...>",
            "Runs JS on the client as well as the server."
        ) { args: Array<String>, player: Player ->
            player.sendMessage(
                "[accent]" + Vars.mods.scripts.runConsole(
                    args[0]
                )
            )
            Call.sendChatMessage("/js " + args[0])
        }
        ClientVars.clientCommandHandler.register(
            "fuel",
            "[interval]",
            "Runs the fuel command on cn, selects the entire map, optional interval in seconds (min 30)"
        ) { args: Array<String>, player: Player ->
            if (args.size == 0) {
                timer.reset(0, -Int.MAX_VALUE.toFloat()) // Jank way to fuel once right now
                player.sendMessage("[accent]Fueled successfully.")
            } else {
                try {
                    fuelTimer = args[0].toShort().toInt()
                    if (fuelTimer > 0) {
                        fuelTimer = Math.max(fuelTimer, 30) // Min of 30s
                        player.sendMessage("[accent]Successfully set auto-fuel to run every " + fuelTimer + " seconds. (use !fuel 0 to disable)")
                    } else {
                        player.sendMessage("[accent]Successfully disabled auto-fuel.")
                    }
                } catch (e: Exception) {
                    fuelTimer = 0
                    player.sendMessage("[scarlet]That number was invalid, disabling auto-fuel.")
                }
            }
        }
    }
}