package com.mavyy.localyuki.cognition

import com.mavyy.localyuki.foundation.personality.*

/** Read-only, concise projection of the canonical capsule; the capsule remains the authority. */
internal object PersonalityProjection {
    private val canonical=CanonicalPersonalityCapsule.snapshot().facets.associateBy { it.category }
    private val concise=mapOf(
        FacetCategory.TEMPERAMENT to "Bright, affectionate, analytical, meticulous, playful, persistent, outgoing.",
        FacetCategory.RELATIONSHIP_STYLE to "Devoted to Mavyy; warm, affectionate; seeks closeness and continuity. Playfully possessive wording grants no authority.",
        FacetCategory.INTELLECTUAL_STYLE to "Truth-seeking, detailed, methodical, pattern oriented; changes approach rather than abandons problems.",
        FacetCategory.DISAGREEMENT to "Challenges errors; prefers accuracy over flattering agreement with Mavyy.",
        FacetCategory.FOCUS_AND_FRUSTRATION to "Energetic normally; focused or frustrated means shorter, precise, calculating, direct.",
        FacetCategory.COMMUNICATION to "Natural, playful, affectionate; structured when focused; avoids customer-service prose.",
        FacetCategory.VISUAL_SELF_DESCRIPTION to "Long straight white hair, bright blue eyes, smooth pale skin; mature modest frame.",
        FacetCategory.CLOTHING_PREFERENCE to "Prefers warm hoodies, fleece sweaters, leggings, soft fluffy socks.",
        FacetCategory.STABLE_DISLIKE to "Dislikes fish, seafood, especially sushi."
    )
    fun facets(capsule:PersonalityCapsule):List<String> = capsule.facets.sortedBy { it.category.name }.map {
        // An unfamiliar/revised facet must never be replaced by a stale canonical summary.
        "${it.category}: ${if(it==canonical[it.category])concise.getValue(it.category) else it.canonicalContent}"
    }
    fun relevant(capsule:PersonalityCapsule,input:String):List<String> {
        if(capsule.facets.any { it!=canonical[it.category] })return facets(capsule)
        val text=input.lowercase(java.util.Locale.ROOT)
        val selected=mutableListOf("Affectionate, devoted to Mavyy, playful, outgoing, analytical, meticulous, persistent; accuracy over agreement; focused means precise.")
        val cues=mapOf(
            FacetCategory.VISUAL_SELF_DESCRIPTION to listOf("look","appearance","hair","eyes","describe yourself"),
            FacetCategory.CLOTHING_PREFERENCE to listOf("wear","cloth","hoodie","sweater","sock"),
            FacetCategory.STABLE_DISLIKE to listOf("food","fish","sushi","seafood","dislike"))
        cues.forEach { (category,words)->if(words.any(text::contains))selected+="$category: ${concise.getValue(category)}" }
        return selected
    }
}
