package com.github.blahblahbloopster

import arc.math.geom.Vec2
import arc.struct.Queue
import arc.util.CommandHandler
import arc.util.Ratekeeper
import mindustry.client.ClientMode
import mindustry.client.antigrief.ConfigRequest

object ClientVarsImpl {

    @CustomGetterSetter("getMode", "setMode")
    internal var mode: ClientMode = ClientMode.normal
    @CustomGetterSetter("getConfigs", "")
    internal var configs: Queue<ConfigRequest> = Queue()
    @CustomGetterSetter("getShowingTurrets", "setShowingTurrets")
    internal var showingTurrets: Boolean = false
    @CustomGetterSetter("getHideUnits", "setHideUnits")
    internal var hideUnits: Boolean = false
    @CustomGetterSetter("getHidingBlocks", "setHidingBlocks")
    internal var hidingBlocks: Boolean = false
    @CustomGetterSetter("getDispatchingBuildPlans", "setDispatchingBuildPlans")
    internal var dispatchingBuildPlans: Boolean = false
    @CustomGetterSetter("getLastSyncTime", "setLastSyncTime")
    internal var lastSyncTime: Long = 0L
    @CustomGetterSetter("getFooCommands", "")
    internal val fooCommands = CommandHandler("!")
    @CustomGetterSetter("getConfigRateLimit", "")
    internal val configRateLimit = Ratekeeper()
    @CustomGetterSetter("getLastSentPos", "")
    internal var lastSentPos: Vec2 = Vec2()
    @CustomGetterSetter("getFooUser", "")
    internal const val FOO_USER = 0b10101010.toByte()
    @CustomGetterSetter("getAssisting", "")
    internal const val ASSISTING = 0b01010101.toByte()
    @CustomGetterSetter("getMessageBlockCommunicationPrefix", "")
    internal val MESSAGE_BLOCK_COMMUNICATION_PREFIX = "IN USE FOR CHAT AUTHENTICATION, do not use"
}
