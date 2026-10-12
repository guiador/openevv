package com.blindtechnexus.freeeloquencetts

import android.content.res.AssetManager
import org.json.JSONObject

/** Longest complete Unicode sequence wins; cached tables are immutable after load. */
object UnicodeEmoji {
    private class Node { val children=HashMap<Int,Node>(); var name:String?=null }
    private val tables=HashMap<String,Node>()
    @Synchronized private fun table(assets:AssetManager,locale:String):Node {
        tables[locale]?.let {return it}
        val json=assets.open("unicode/$locale.json").bufferedReader(Charsets.UTF_8).use {JSONObject(it.readText())}
        val root=Node()
        for(key in json.keys()) {
            var node=root; var i=0
            while(i<key.length) {
                val cp=Character.codePointAt(key,i)
                node=node.children.getOrPut(cp) {Node()}; i+=Character.charCount(cp)
            }
            node.name=json.getString(key)
        }
        tables[locale]=root
        return root
    }
    fun apply(text:String,read:Boolean,language:Int,assets:AssetManager):String {
        val locale=if(language==Eci.SPANISH_MEXICO) "es-419" else "es"
        val root=table(assets,locale)
        var out:StringBuilder?=null; var at=0
        while(at<text.length) {
            var node=root; var cursor=at; var end=at; var name:String?=null
            while(cursor<text.length) {
                val cp=Character.codePointAt(text,cursor)
                if(cursor>at && (cp==0xfe0f || cp==0xfe0e)) {
                    cursor+=Character.charCount(cp)
                    if(node.name!=null) end=cursor
                    continue
                }
                val next=node.children[cp] ?: break
                node=next; cursor+=Character.charCount(cp)
                node.name?.let {name=it;end=cursor}
            }
            if(name!=null) {
                if(out==null) out=StringBuilder(text.length+32).append(text,0,at)
                out.append(' ')
                if(read) out.append(name).append(' ')
                at=end
            } else {
                val count=Character.charCount(Character.codePointAt(text,at))
                out?.append(text,at,at+count); at+=count
            }
        }
        return out?.toString() ?: text
    }
}
