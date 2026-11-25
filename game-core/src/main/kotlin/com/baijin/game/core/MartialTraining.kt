package com.baijin.game.core

import com.baijin.game.core.content.GameContent
import com.baijin.game.core.content.MartialArtDefinition
import com.baijin.game.core.content.OpeningMartialArtLevel
import com.baijin.game.core.content.OpeningSectDefinition
import kotlinx.serialization.Serializable

@Serializable
data class MartialTrainingState(
    val sectId: String? = null,
    val levels: Map<String, Int> = emptyMap(),
    val appliedUnarmedArtId: String? = null,
    val appliedStaffArtId: String? = null,
) {
    fun levelOf(artId: String): Int = levels[artId] ?: 0
}

data class MartialTrainingView(
    val sect: OpeningSectDefinition?,
    val sectChoices: List<OpeningSectDefinition>,
    val lessons: List<MartialLesson>,
    val appliedUnarmedArt: MartialArtDefinition?,
    val unarmedAttack: UnarmedAttack,
    val staffTraining: List<Pair<MartialArtDefinition, Int>> = emptyList(),
    val appliedStaffArt: MartialArtDefinition? = null,
)

data class MartialLesson(
    val art: MartialArtDefinition,
    val level: Int,
    val nextCost: Int?,
    val requirement: ActionRequirement?,
)

@Serializable
data class UnarmedAttack(
    val power: Int,
    val hitBonus: Int,
    val criticalBonusBasisPoints: Int,
)

internal fun unarmedAttack(
    strength: Int,
    basic: OpeningMartialArtLevel? = null,
    applied: OpeningMartialArtLevel? = null,
): UnarmedAttack {
    val power = 4L + strength + (basic?.powerBonus ?: 0) + (applied?.powerBonus ?: 0)
    return UnarmedAttack(
        power = power.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        hitBonus = applied?.hitBonus ?: 0,
        criticalBonusBasisPoints = applied?.criticalBonusBasisPoints ?: 0,
    )
}

internal class MartialTraining(content: GameContent) {
    private val arts = content.martialArts.associateBy { it.id }
    private val basic = content.openingTraining?.basicUnarmedArtId?.let(arts::getValue)
    private val sects = content.openingTraining?.sects.orEmpty()
    private val sectsById = sects.associateBy { it.id }
    private val staff = content.openingTraining?.staff
    private val staffArtIds = listOfNotNull(staff?.basicArtId, staff?.appliedArtId)

    fun validState(state: GameState): Boolean {
        val training = state.martialTraining
        val sect = training.sectId?.let { sectsById[it] ?: return false }
        val sectArtId = sect?.unarmedArtId
        val basicLevel = basic?.let { training.levelOf(it.id) } ?: 0
        val sectLevel = sectArtId?.let(training::levelOf) ?: 0
        val expectedApplied = sectArtId.takeIf { sectLevel > 0 }
        val introductionComplete = staff?.preparation(state)?.unlocked == true
        val expectedStaffArt = staff?.appliedArtId?.takeIf { introductionComplete }
        return training.levels.all { (id, level) ->
            when {
                id == basic?.id || id == sectArtId -> level in 1..arts.getValue(id).openingLevels.size
                id in staffArtIds -> level in 1..arts.getValue(id).maxLevel
                else -> false
            }
        } && sectLevel <= basicLevel && (basicLevel < 2 || sectLevel >= 1) &&
            training.appliedUnarmedArtId == expectedApplied && training.appliedStaffArtId == expectedStaffArt &&
            (!introductionComplete || (sectLevel > 0 && staffArtIds.all { training.levelOf(it) > 0 }))
    }

    fun introductionBlock(state: GameState): ActionRequirement? {
        val training = state.martialTraining
        val sect = training.sectId?.let(sectsById::getValue) ?: return ActionRequirement.SectRequired
        return listOfNotNull(basic?.id, sect.unarmedArtId).firstOrNull { training.levelOf(it) < 1 }
            ?.let { ActionRequirement.MartialLevelRequired(arts.getValue(it).name, 1) }
    }

    fun weaponAttack(state: GameState, weapon: EquipmentDefinition): UnarmedAttack {
        val training = state.martialTraining
        fun level(id: String?) = id?.let { arts.getValue(it).openingLevels.getOrNull(training.levelOf(it) - 1) }
        val base = if (weapon.weaponType == WeaponType.STAFF) {
            unarmedAttack(state.character.attributes.strength, level(staff?.basicArtId), level(training.appliedStaffArtId))
        } else unarmedAttack(state.character.attributes.strength)
        return base.copy(
            power = (base.power.toLong() + weapon.stats.power).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            hitBonus = base.hitBonus + weapon.stats.hit,
        )
    }

    fun weaponAttackLines(type: WeaponType?) = when (type) {
        WeaponType.STAFF -> listOf("横棍进步，顺势点出", "拧身递棍，直取对手中路")
        WeaponType.SWORD -> listOf("提剑向前，剑锋一送")
        WeaponType.SABER -> listOf("进步扬刀，斜斩而下")
        WeaponType.WHIP -> listOf("扬腕送鞭，鞭梢一甩")
        null -> emptyList()
    }

    fun view(state: GameState): MartialTrainingView {
        val training = state.martialTraining
        val sect = training.sectId?.let(sectsById::getValue)
        val sectArt = sect?.unarmedArtId?.let(arts::getValue)
        val applied = training.appliedUnarmedArtId?.let(arts::getValue)
        val basicStats = basic?.openingLevels?.getOrNull(training.levelOf(basic.id) - 1)
        val appliedStats = applied?.openingLevels?.getOrNull(training.levelOf(applied.id) - 1)
        return MartialTrainingView(
            sect = sect,
            sectChoices = if (sect == null) sects else emptyList(),
            lessons = listOfNotNull(basic, sectArt).map { lesson(state, it) },
            appliedUnarmedArt = applied,
            unarmedAttack = unarmedAttack(state.character.attributes.strength, basicStats, appliedStats),
            staffTraining = staffArtIds.mapNotNull { id ->
                training.levelOf(id).takeIf { it > 0 }?.let { arts.getValue(id) to it }
            },
            appliedStaffArt = training.appliedStaffArtId?.let(arts::getValue),
        )
    }

    fun join(state: GameState, sectId: String, changed: (GameState) -> ActionResult): ActionResult {
        val training = state.martialTraining
        if (training.sectId != null) {
            return ActionResult.Blocked(ActionRequirement.AlreadyInSect(sectsById.getValue(training.sectId).name))
        }
        if (sectId !in sectsById) return ActionResult.Blocked(ActionRequirement.TrainingUnavailable)
        return changed(state.copy(martialTraining = training.copy(sectId = sectId)))
    }

    fun learn(state: GameState, artId: String, targetLevel: Int, changed: (GameState) -> ActionResult): ActionResult {
        val training = state.martialTraining
        val sectArtId = training.sectId?.let { sectsById.getValue(it).unarmedArtId }
        if (artId != basic?.id && artId != sectArtId) {
            return ActionResult.Blocked(ActionRequirement.TrainingUnavailable)
        }
        val lesson = lesson(state, arts.getValue(artId))
        if (targetLevel != lesson.level + 1) return ActionResult.Blocked(ActionRequirement.MartialLevelChanged)
        lesson.requirement?.let { return ActionResult.Blocked(it) }
        val learned = training.copy(
            levels = training.levels + (artId to targetLevel),
            appliedUnarmedArtId = if (artId == sectArtId && targetLevel == 1) artId else training.appliedUnarmedArtId,
        )
        return changed(state.copy(potential = state.potential - checkNotNull(lesson.nextCost), martialTraining = learned))
    }

    private fun lesson(state: GameState, art: MartialArtDefinition): MartialLesson {
        val training = state.martialTraining
        val level = training.levelOf(art.id)
        val next = art.openingLevels.getOrNull(level)
            ?: return MartialLesson(art, level, null, ActionRequirement.OpeningTrainingComplete)
        val discount = (state.character.attributes.insight - 4).coerceAtLeast(0) / 2
        val cost = (next.potentialCost - discount).coerceAtLeast(1)
        val basicArt = checkNotNull(basic)
        val sect = training.sectId?.let(sectsById::getValue)
        val requirement = when {
            art.id == basicArt.id && level > 0 && sect == null -> ActionRequirement.SectRequired
            art.id == basicArt.id && level > 0 && sect != null && training.levelOf(sect.unarmedArtId) == 0 ->
                ActionRequirement.MartialLevelRequired(arts.getValue(sect.unarmedArtId).name, 1)
            art.id != basicArt.id && training.levelOf(basicArt.id) < level + 1 ->
                ActionRequirement.MartialLevelRequired(basicArt.name, level + 1)
            state.potential < cost -> ActionRequirement.PotentialMissing(cost - state.potential)
            else -> null
        }
        return MartialLesson(art, level, cost, requirement)
    }
}
