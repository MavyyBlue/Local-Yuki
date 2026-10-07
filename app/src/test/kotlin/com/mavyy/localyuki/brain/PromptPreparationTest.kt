package com.mavyy.localyuki.brain

import com.mavyy.localyuki.inference.*
import com.mavyy.localyuki.cognition.PersonalityProjection
import com.mavyy.localyuki.foundation.admission.ModelRole
import com.mavyy.localyuki.foundation.personality.*
import org.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class) @Config(sdk=[35])
class PromptPreparationTest {
    @Test fun expressionGrammarCoversExactlySuppliedPointsAndNeverTreatsUncertaintyAsAnotherPoint() {
        val user=JSONObject().put("points",JSONArray(listOf(JSONObject().put("id",0).put("meaning","Hello, Mavyy."))))
            .put("uncertainty",JSONArray(listOf("Context omitted"))).toString()
        val grammar=PromptPreparation.candidates(ModelRole.LANGUAGE_EXPRESSION,user,64).single().grammar
        assertTrue(grammar.contains("\"[\" ws \"0\" ws \"]\""));assertFalse(grammar.contains("number (ws"))
        assertTrue(OrganGrammar.forExpression(2).contains("\"0\" ws \",\" ws \"1\""))
    }
    @Test fun largeHistoryIsReducedAsWholeJsonWithoutCuttingOwnerInputOrInventingSources() {
        val input="Hello, My Love 🥰"
        val memories=JSONArray((0 until 20).map { JSONObject().put("source","memory$it").put("content","long memory ".repeat(680)) })
        val j=JSONObject().put("ownerInput",input).put("memories",memories).put("availableCapabilities",JSONArray(listOf("DOCUMENTS")))
        val plan=PromptPreparation.candidates(ModelRole.SYSTEM_TWO,j.toString(),92)
        assertTrue(plan.all { it.user.toByteArray().size<=16384 })
        assertTrue(plan.all { JSONObject(it.user).getString("ownerInput")==input })
        val last=plan.last();assertTrue(last.shortened);assertFalse(JSONObject(last.user).has("memories"))
        assertFalse(last.grammar.contains("\\\"memory0\\\""));assertTrue(last.grammar.contains("actions ::= \"[\" ws \"]\""))
        assertTrue(JSONObject(last.user).getJSONArray("uncertainty").toString().contains("omitted information"))
    }
    @Test fun mandatoryOwnerInputAndExpressionPointsFailRatherThanBeingCut() {
        for(role in listOf(ModelRole.SYSTEM_ONE,ModelRole.LANGUAGE_EXPRESSION)) {
            val field=if(role==ModelRole.SYSTEM_ONE)"ownerInput" else "points"
            try { PromptPreparation.candidates(role,JSONObject().put(field,"🥰".repeat(6000)).toString(),92);fail("Oversized mandatory input accepted") }
            catch(_:IllegalArgumentException) {}
        }
    }
    @Test fun smallDecisionContractRetainsRoutingAffectMeaningAndEvidence() {
        val input=JSONObject().put("ownerInput","Hello").toString()
        val compact=PromptPreparation.candidates(ModelRole.SYSTEM_ONE,input,92).single().grammar
        val full=PromptPreparation.candidates(ModelRole.SYSTEM_ONE,input,256).single().grammar
        for(key in listOf("route","confidence","affect","meaning","uncertainty","sources"))assertTrue(compact.contains("\\\"$key\\\""))
        assertFalse(compact.contains("\\\"updates\\\""));assertTrue(full.contains("\\\"updates\\\""))
    }
    @Test fun canonicalProjectionSelectsRelevantFactsWithoutChangingOrHidingRevisedFacets() {
        val c=CanonicalPersonalityCapsule.snapshot();val before=c.facets.toSet()
        assertTrue(PersonalityProjection.relevant(c,"What do you like wearing?").any { it.contains("hoodies") })
        assertTrue(PersonalityProjection.relevant(c,"Do you like sushi?").any { it.contains("Dislikes fish") })
        val changed=c.facets.map { if(it.category==FacetCategory.CLOTHING_PREFERENCE)it.copy(canonicalContent="A revised preference") else it }
        val revised=PersonalityCapsule(c.id,c.version,c.honesty,changed)
        assertTrue(PersonalityProjection.relevant(revised,"Hello").any { it.contains("A revised preference") })
        assertEquals(before,c.facets);assertEquals(9,c.facets.size)
    }
}
