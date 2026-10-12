package com.blindtechnexus.freeeloquencetts

/** Expand only the engine PCM; no device-wide balance setting is touched. */
object TtsStereo {
    fun convert(buffer: ByteArray, n: Int, balance: Int): ByteArray {
        val out=ByteArray(n*2)
        val left=if(balance>0) 1f-balance/100f else 1f
        val right=if(balance<0) 1f+balance/100f else 1f
        for(i in 0 until n/2) {
            val sample=((buffer[i*2].toInt() and 255) or (buffer[i*2+1].toInt() shl 8)).toShort().toInt()
            for(c in 0..1) {
                val v=(sample*if(c==0) left else right).toInt()
                out[i*4+c*2]=v.toByte(); out[i*4+c*2+1]=(v shr 8).toByte()
            }
        }
        return out
    }
}
