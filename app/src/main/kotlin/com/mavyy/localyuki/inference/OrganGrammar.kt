package com.mavyy.localyuki.inference
import com.mavyy.localyuki.foundation.admission.ModelRole
import com.mavyy.localyuki.foundation.embodiment.CapabilityId
/** App-supplied syntax; compact JSON reserves measured tokens for meaning rather than indentation.
 * Models cannot select a grammar or expand its authority. Whitespace inside strings is preserved. */
internal object OrganGrammar {
 private val common="""
ws ::= ""
string ::= "\"" character character* "\""
character ::= [^"\\\x00-\x1F] | "\\" (["\\/bfnrt] | "u" [0-9a-fA-F]{4})
strings ::= "[" ws (string (ws "," ws string){0,7})? ws "]"
meaning ::= "[" ws string (ws "," ws string){0,5} ws "]"
number ::= "100" | [1-9] [0-9]? | "0"
source ::= "\"input\"" | "\"memory" [0-9] [0-9]? "\""
sources ::= "[" ws source (ws "," ws source){0,7} ws "]"
affect ::= "\"neutral\"" | "\"warmth\"" | "\"concern\"" | "\"curiosity\"" | "\"frustration\""
""".trimIndent()
 fun forRole(role:ModelRole,sources:Set<String> = setOf("input"),capabilities:Set<CapabilityId> = emptySet()):String {
  require(sources.isNotEmpty() && sources.size<=29 && sources.all { it=="input" || it.matches(Regex("(memory|observation)[0-9]{1,2}")) })
  val rules=when(role) {
   ModelRole.SYSTEM_ONE -> """
root ::= "{" ws "\"route\":" ws ("\"RESPOND\"" | "\"REASON\"") ws ",\"confidence\":" ws number ws ",\"salience\":" ws number ws ",\"intent\":" ws string ws ",\"affect\":" ws affect ws ",\"meaning\":" ws meaning ws ",\"uncertainty\":" ws strings ws ",\"sources\":" ws sources ws ",\"updates\":" ws updates ws "}" ws
updates ::= "[" ws (update (ws "," ws update)?)? ws "]"
update ::= "{" ws "\"kind\":" ws kind ws ",\"topic\":" ws string ws ",\"content\":" ws string ws ",\"sources\":" ws sources ws "}"
kind ::= "\"CONCERN\"" | "\"INTENTION\"" | "\"TOPIC\"" | "\"CURIOSITY\"" | "\"PREFERENCE\"" | "\"OPINION\"" | "\"REFLECTION\"" | "\"SELF_STATE\"" | "\"RELATIONSHIP\""
"""
   ModelRole.SYSTEM_TWO -> """
root ::= "{" ws "\"points\":" ws meaning ws ",\"uncertainty\":" ws strings ws ",\"sources\":" ws sources ws ",\"actions\":" ws actions ws "}" ws
actions ::= "[" ws (action (ws "," ws action)?)? ws "]"
action ::= "{" ws "\"capability\":" ws string ws ",\"payload\":" ws string ws ",\"targetPackage\":" ws (string | "null") ws ",\"notBefore\":" ws (string | "null") ws "}"
"""
   ModelRole.LANGUAGE_EXPRESSION -> """
root ::= "{" ws "\"text\":" ws string ws ",\"pointIds\":" ws "[" ws number (ws "," ws number)* ws "]" ws "}" ws
"""
   ModelRole.AFFECT -> """
root ::= "{" ws "\"affect\":" ws affect ws ",\"confidence\":" ws number ws "}" ws
"""
   else -> return ""
  }
  val bounded=common.lineSequence().joinToString("\n") { if(it.startsWith("source ::=")) "source ::= "+sources.sorted().joinToString(" | ") { s->"\"\\\"$s\\\"\"" } else it }
  val actions=if(role==ModelRole.SYSTEM_TWO) {
    if(capabilities.isEmpty())rules.lineSequence().joinToString("\n") { if(it.startsWith("actions ::=")) "actions ::= \"[\" ws \"]\"" else it }
    else rules.replace("\"\\\"capability\\\":\" ws string","\"\\\"capability\\\":\" ws capability")+"\ncapability ::= "+capabilities.sortedBy { it.name }.joinToString(" | ") { "\"\\\"${it.name}\\\"\"" }
  } else rules
  return actions.trimIndent()+"\n"+bounded+"\n"
 }
}
