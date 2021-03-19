package com.github.blahblahbloopster.antigrief

import arc.Events
import arc.util.Time
import com.github.blahblahbloopster.Initializable
import mindustry.Vars
import mindustry.client.Client.vars
import mindustry.client.antigrief.TileLog
import mindustry.game.EventType

object TileLogging : Initializable {

    private lateinit var logs: Array<Array<TileLog>>

    override fun initializeGameLoad() {
        Events.on(EventType.WorldLoadEvent::class.java) {
            if (Time.timeSinceMillis(vars.lastSyncTime) > 5000) {
                logs = Array(Vars.world.width()) { x -> Array(Vars.world.height()) { y -> TileLog(Vars.world.tile(x, y)) } }
            }
        }
    }

    fun getLog(x: Int, y: Int): TileLog? = logs.getOrNull(x)?.getOrNull(y)
}
