package com.mavyy.localyuki.inference

import com.mavyy.localyuki.foundation.admission.ModelRole
import com.mavyy.localyuki.foundation.embodiment.CapabilityId
import org.json.*

internal data class OrganPrompt(val user:String,val grammar:String,val shortened:Boolean)
/** Whole JSON candidates; native tokenization chooses a fitting one without cutting owner input. */
internal object PromptPreparation {
    fun candidates(role:ModelRole,user:String,output:Int):List<OrganPrompt> {
        val original=try { JSONObject(user) }catch(_:JSONException){null}
        val contextual=original?.has("ownerInput")==true && role in setOf(ModelRole.SYSTEM_ONE,ModelRole.SYSTEM_TWO)
        val variants=mutableListOf<Pair<String,Boolean>>(user to false)
        if(contextual) {
            for(keep in listOf(2,1,0)) {
                val j=JSONObject(original.toString())
                for(key in listOf("memories","observations","recentConversation","ongoingContext")) {
                    val a=j.optJSONArray(key)?:continue
                    if(keep==0)j.remove(key) else if(a.length()>keep) {
                        val start=if(key=="recentConversation")a.length()-keep else 0
                        j.put(key,JSONArray((start until start+keep).map { a.get(it) }))
                    }
                }
                if(keep==0)j.optJSONObject("identity")?.remove("personality")
                if(j.toString()!=original.toString()) {
                    val u=j.optJSONArray("uncertainty")?:JSONArray()
                    u.put("Context was limited; omitted information is unknown.");j.put("uncertainty",u)
                    variants+=j.toString() to true
                }
            }
        }
        return variants.distinctBy { it.first }.filter { it.first.toByteArray().size<=16384 }.map { (text,shortened)->
            val j=try { JSONObject(text) }catch(_:JSONException){null}
            val refs=mutableSetOf("input")
            for(key in listOf("memories","observations"))j?.optJSONArray(key)?.let { a->
                for(i in 0 until a.length()) {
                    val id=a.getJSONObject(i).getString("source");require(id.matches(Regex("(memory|observation)[0-9]{1,2}")));refs+=id
                }
            }
            val caps=mutableSetOf<CapabilityId>()
            if(!shortened)j?.optJSONArray("availableCapabilities")?.let { a->for(i in 0 until a.length())caps+=CapabilityId.valueOf(a.getString(i)) }
            val grammar=if(role==ModelRole.LANGUAGE_EXPRESSION && j?.has("points")==true)OrganGrammar.forExpression(j.getJSONArray("points").length())
                else OrganGrammar.forRole(role,refs,caps,contextual && role==ModelRole.SYSTEM_ONE && output<=128)
            OrganPrompt(text,grammar,shortened)
        }.also { require(it.isNotEmpty()) { "Owner input and mandatory context exceed the safe request size" } }
    }
}
