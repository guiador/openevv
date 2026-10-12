package com.blindtechnexus.freeeloquencetts

/** Read-only preset metadata collected on the engine's own thread. */
object VoiceDefaults {
    private val values=java.util.concurrent.ConcurrentHashMap<Pair<Int,Int>,Map<Int,Int>>()
    fun remember(language:Int, voice:Int, shape:Map<Int,Int>) { if(shape.isNotEmpty()) values[language to voice]=shape }
    fun get(s:Settings,param:Int):Int?=values[s.language to s.voice]?.get(param)
}
