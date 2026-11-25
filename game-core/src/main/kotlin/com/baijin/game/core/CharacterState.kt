package com.baijin.game.core

import kotlinx.serialization.Serializable

@Serializable
enum class Gender { MALE, FEMALE }

enum class Attribute { STRENGTH, AGILITY, INSIGHT, CONSTITUTION }

@Serializable
data class Attributes(val strength: Int, val agility: Int, val insight: Int, val constitution: Int) {
    operator fun get(attribute: Attribute): Int = when (attribute) {
        Attribute.STRENGTH -> strength
        Attribute.AGILITY -> agility
        Attribute.INSIGHT -> insight
        Attribute.CONSTITUTION -> constitution
    }

    fun withValue(attribute: Attribute, value: Int): Attributes = when (attribute) {
        Attribute.STRENGTH -> copy(strength = value)
        Attribute.AGILITY -> copy(agility = value)
        Attribute.INSIGHT -> copy(insight = value)
        Attribute.CONSTITUTION -> copy(constitution = value)
    }
}

@Serializable
data class CharacterState(
    val gender: Gender,
    val attributes: Attributes,
    val luck: Int,
    val appearanceTier: Int,
    val health: Int,
    val maxHealth: Int,
    val effectiveMaxHealth: Int,
    val innerPower: Int,
    val maxInnerPower: Int,
)
