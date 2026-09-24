package com.mavyy.localyuki.foundation.personality

import com.mavyy.localyuki.foundation.contracts.FoundationResult
import com.mavyy.localyuki.foundation.identity.PersonalityCapsuleVersion
import com.mavyy.localyuki.foundation.identity.HonestyPolicy
import java.util.Collections

enum class FacetCategory {
    TEMPERAMENT, RELATIONSHIP_STYLE, INTELLECTUAL_STYLE, DISAGREEMENT,
    FOCUS_AND_FRUSTRATION, COMMUNICATION, VISUAL_SELF_DESCRIPTION,
    CLOTHING_PREFERENCE, STABLE_DISLIKE
}

data class PersonalityFacet(val stableId: String, val category: FacetCategory, val canonicalContent: String) {
    init { require(stableId.isNotBlank() && canonicalContent.isNotBlank()) }
}

/** Facet order is never semantic. The capsule cannot contain missing or duplicate V1 categories. */
class PersonalityCapsule(
    val id: String,
    val version: PersonalityCapsuleVersion,
    val honesty: HonestyPolicy,
    facets: Collection<PersonalityFacet>
) {
    val facets: Set<PersonalityFacet> = Collections.unmodifiableSet(facets.toSet())

    init {
        require(id.isNotBlank())
        require(facets.size == this.facets.size) { "Duplicate personality facets" }
        require(this.facets.map { it.stableId }.toSet().size == this.facets.size) { "Duplicate facet IDs" }
        require(this.facets.map { it.category }.toSet() == FacetCategory.entries.toSet()) {
            "Missing or duplicate personality facet categories"
        }
    }

    override fun equals(other: Any?): Boolean = other is PersonalityCapsule &&
        id == other.id && version == other.version && honesty == other.honesty && facets == other.facets
    override fun hashCode(): Int = 31 * (31 * (31 * id.hashCode() + version.hashCode()) + honesty.hashCode()) + facets.hashCode()
}

fun interface PersonalityCapsuleReader {
    fun read(): FoundationResult<PersonalityCapsule>
}
