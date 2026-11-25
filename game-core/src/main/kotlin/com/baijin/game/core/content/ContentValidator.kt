package com.baijin.game.core.content

import com.baijin.game.core.EquipmentSlot
import com.baijin.game.core.Attribute
import com.baijin.game.core.WorldPosition
import kotlin.math.abs

internal class ContentValidator {
    private val issues = mutableListOf<ContentIssue>()

    fun validate(content: GameContent): List<ContentIssue> {
        check(content.contentVersion > 0, "$.contentVersion", "内容版本须为正整数")
        val scenes = table("scenes", content.scenes)
        val locations = table("locations", content.locations)
        val npcs = table("npcs", content.npcs)
        val martialArts = table("martialArts", content.martialArts)
        val items = table("items", content.items)
        table("quests", content.quests)

        reference("$.startLocationId", content.startLocationId, locations.keys)
        validateWorld(content, scenes)
        val npcLocations = mutableSetOf<String>()
        content.npcs.forEachIndexed { index, npc ->
            val path = "$.npcs[$index]"
            reference("$path.locationId", npc.locationId, locations.keys)
            check(npcLocations.add(npc.locationId), "$path.locationId", "同一交互地点只能配置一位 NPC")
            check(npc.morality in -100..100, "$path.morality", "善恶值须在 -100 至 100 之间")
            check(npc.initialAffinity in -100..100, "$path.initialAffinity", "初始好感度须在 -100 至 100 之间")
            check(npc.luck in 1..100, "$path.luck", "幸运须在 1 至 100 之间")
            check(npc.services.distinct().size == npc.services.size, "$path.services", "业务入口不能重复")
            for (band in AffinityBand.entries) {
                val topics = npc.dialogue[band].orEmpty()
                val group = "$path.dialogue.$band"
                check(topics.size == 4, group, "每档好感须配置四个话题")
                topics.forEachIndexed { topicIndex, topic ->
                    val field = "$group[$topicIndex]"
                    check(topic.title.isNotBlank(), "$field.title", "话题名称不能为空")
                    check(topic.text.isNotBlank(), "$field.text", "对白不能为空")
                    val maxTier = content.appearance.descriptions.size
                    val min = topic.minAppearanceTier
                    val max = topic.maxAppearanceTier
                    check(min == null || min in 1..maxTier, "$field.minAppearanceTier", "相貌档位超出范围")
                    check(max == null || max in (min ?: 1)..maxTier, "$field.maxAppearanceTier", "相貌档位超出范围")
                    check(topic.service == null || topic.service in npc.services, "$field.service", "须引用该人物的业务入口")
                }
            }
        }
        content.martialArts.forEachIndexed { index, art ->
            check(art.maxLevel > 0, "$.martialArts[$index].maxLevel", "最高等级须为正整数")
            check(art.openingAttackLines.isEmpty() ||
                (art.openingAttackLines.size == 3 && art.openingAttackLines.all { it.isNotBlank() }),
                "$.martialArts[$index].openingAttackLines", "普攻文案须为空或包含三条非空动作")
            art.openingLevels.forEachIndexed { levelIndex, level ->
                val path = "$.martialArts[$index].openingLevels[$levelIndex]"
                check(level.potentialCost > 0, "$path.potentialCost", "请教费用须为正整数")
                check(level.powerBonus >= 0, "$path.powerBonus", "威力加成须为非负整数")
                check(level.hitBonus in -100..100, "$path.hitBonus", "命中修正须在 -100 至 100 个百分点之间")
                check(level.criticalBonusBasisPoints in -10000..10000, "$path.criticalBonusBasisPoints", "暴击修正超出范围")
            }
        }
        content.openingTraining?.let { validateOpeningTraining(it, martialArts) }
        val staff = content.openingTraining?.staff
        staff?.let {
            val path = "$.openingTraining.staff"
            val quest = content.quests.firstOrNull { quest -> quest.id == it.questId }
            check(quest != null && quest.objectives.isNotEmpty() && quest.repeatBelowExperience == null,
                "$path.questId", "须引用有目标的一次性接引任务")
        }
        content.items.forEachIndexed { index, item ->
            val path = "$.items[$index]"
            item.description?.let { check(it.isNotBlank(), "$path.description", "物品说明不能为空") }
            item.healthRecovery?.let {
                check(it > 0 && item.equipment == null && item.id != content.supplies.foodItemId,
                    "$path.healthRecovery", "疗伤物品须有正数恢复量，且不能兼作装备或口粮")
            }
            item.equipment?.let { equipment ->
                val field = "$path.equipment"
                val weapon = equipment.slot == EquipmentSlot.WEAPON
                check(weapon == (equipment.weaponType != null), "$field.weaponType", "武器位须配置武器类型")
                check(item.id != content.supplies.foodItemId, field, "口粮不能作为装备")
                val stats = equipment.stats
                check(stats.power in 0..10000 && stats.protection in 0..10000 &&
                    listOf(stats.hit, stats.parry, stats.evasion).all { it in -100..100 },
                    "$field.stats", "装备属性超出范围")
                check(if (weapon) stats.protection == 0 && stats.evasion == 0
                    else stats.power == 0 && stats.hit == 0 && stats.parry == 0,
                    "$field.stats", "武器与护具须使用各自的属性")
                check(equipment.requiredAttributes.values.all { it > 0 },
                    "$field.requiredAttributes", "四维门槛须为正数")
                equipment.requiredArt?.let {
                    reference("$field.requiredArt.martialArtId", it.martialArtId, martialArts.keys)
                    val art = martialArts[it.martialArtId]
                    check(it.level > 0 && (art == null || it.level <= art.maxLevel),
                        "$field.requiredArt.level", "武学门槛须在武学等级范围内")
                }
            }
        }
        content.openingSparring?.let {
            validateOpeningSparring(it, npcs, items.keys, content.quests.associateBy(QuestDefinition::id))
        }
        validateArmourShop(content, npcs, items)
        content.quests.forEachIndexed { index, quest ->
            val path = "$.quests[$index]"
            reference("$path.giverNpcId", quest.giverNpcId, npcs.keys)
            reference("$path.turnInNpcId", quest.turnInNpcId, npcs.keys)
            val issuesBeforeReward = issues.size
            validateReward("$path.reward", quest.reward, items.keys, martialArts)
            if (quest.id == staff?.questId && issues.size == issuesBeforeReward) {
                val grants = quest.reward.martialArts.associate { it.martialArtId to it.level }
                check(grants.keys == setOf(staff.basicArtId, staff.appliedArtId) && grants.values.all { it == 1 },
                    "$path.reward.martialArts", "接引须授予两项开局棍法各一级")
            } else if (quest.id != staff?.questId && quest.objectives.isNotEmpty()) {
                check(quest.reward.martialArts.isEmpty(), "$path.reward.martialArts", "开局授艺由接引任务结算")
            }
            quest.repeatBelowExperience?.let {
                check(it > 0 && quest.objectives.isNotEmpty(), "$path.repeatBelowExperience", "重复差事须有目标及正数经验门槛")
            }
            if (quest.assignOnDialogue || quest.settleOnObjective) {
                check(quest.objectives.isNotEmpty(), "$path.objectives", "对话派发或当场结算的委托须有目标")
            }
            val objectiveIds = mutableSetOf<String>()
            quest.objectives.forEachIndexed { objectiveIndex, objective ->
                val field = "$path.objectives[$objectiveIndex]"
                check(ID_PATTERN.matches(objective.id) && objectiveIds.add(objective.id), "$field.id", "目标 ID 无效或重复")
                check(objective.name.isNotBlank(), "$field.name", "目标名称不能为空")
                check(objective.actionLabel.isNotBlank(), "$field.actionLabel", "动作名称不能为空")
                check(objective.completionText.isNotBlank(), "$field.completionText", "完成提示不能为空")
                if (quest.assignOnDialogue) {
                    check(!objective.assignmentText.isNullOrBlank(), "$field.assignmentText", "对话派发须有交代差事的对白")
                }
                check((objective.locationId == null) != (objective.npcId == null), field, "须指定一个地点或 NPC")
                objective.locationId?.let { reference("$field.locationId", it, locations.keys) }
                objective.npcId?.let { reference("$field.npcId", it, npcs.keys) }
            }
        }
        validateAppearance(content.appearance)
        validateCreation(content.creation)
        validateTime(content.time)
        val supplies = content.supplies
        reference("$.supplies.sellerNpcId", supplies.sellerNpcId, npcs.keys)
        reference("$.supplies.foodItemId", supplies.foodItemId, items.keys)
        reference("$.supplies.wellLocationId", supplies.wellLocationId, locations.keys)
        locations[supplies.wellLocationId]?.let {
            check(it.kind == LandmarkKind.WELL, "$.supplies.wellLocationId", "饮水地点须为水井")
        }
        check(supplies.foodPrice > 0, "$.supplies.foodPrice", "售价须为正整数")
        check(supplies.hungerReduction > 0, "$.supplies.hungerReduction", "饥饿补充量须为正整数")
        check(supplies.thirstReduction > 0, "$.supplies.thirstReduction", "解渴量须为正整数")
        return issues.toList()
    }

    private fun validateArmourShop(
        content: GameContent,
        npcs: Map<String, NpcDefinition>,
        items: Map<String, ItemDefinition>,
    ) {
        val shop = content.openingArmourShop
        content.npcs.forEachIndexed { index, npc ->
            check((NpcService.ARMOUR in npc.services) == (shop?.npcId == npc.id),
                "$.npcs[$index].services", "护具入口须与备战货单对应")
        }
        if (shop == null) return
        val path = "$.openingArmourShop"
        reference("$path.npcId", shop.npcId, npcs.keys)
        check(content.openingTraining?.staff != null, path, "备战货单须配置师门接引")
        check(shop.stock.isNotEmpty(), "$path.stock", "备战货单须配置商品")
        val listed = mutableSetOf<String>()
        shop.stock.forEachIndexed { index, entry ->
            val row = "$path.stock[$index]"
            reference("$row.itemId", entry.itemId, items.keys)
            items[entry.itemId]?.let { item ->
                check(item.equipment?.slot?.let { it != EquipmentSlot.WEAPON } == true,
                    "$row.itemId", "备战护具货单须引用护具")
            }
            check(listed.add(entry.itemId), "$row.itemId", "同一物品不能重复上架")
            check(entry.price > 0, "$row.price", "售价须为正整数")
        }
    }

    private fun validateOpeningTraining(
        training: OpeningTrainingDefinition,
        arts: Map<String, MartialArtDefinition>,
    ) {
        val path = "$.openingTraining"
        table("openingTraining.sects", training.sects)
        check(training.sects.isNotEmpty(), "$path.sects", "须配置至少一条开局门派路线")
        val used = mutableSetOf<String>()
        fun validateArt(field: String, id: String) {
            reference(field, id, arts.keys)
            arts[id]?.let { art ->
                check(art.openingLevels.size == 2 && art.maxLevel >= 2, field, "入门武学须配置 1 和 2 级")
            }
            check(used.add(id), field, "同一武学只能用于一个开局课表位置")
        }
        validateArt("$path.basicUnarmedArtId", training.basicUnarmedArtId)
        training.sects.forEachIndexed { index, sect ->
            validateArt("$path.sects[$index].unarmedArtId", sect.unarmedArtId)
        }
        training.staff?.let { staff ->
            listOf("basicArtId" to staff.basicArtId, "appliedArtId" to staff.appliedArtId).forEach { (key, id) ->
                val field = "$path.staff.$key"
                reference(field, id, arts.keys)
                check(used.add(id), field, "同一武学只能用于一个开局课表位置")
            }
        }
    }

    private fun validateOpeningSparring(
        sparring: OpeningSparringDefinition,
        npcs: Map<String, NpcDefinition>,
        itemIds: Set<String>,
        quests: Map<String, QuestDefinition>,
    ) {
        val path = "$.openingSparring"
        val opponent = sparring.opponent
        check(ID_PATTERN.matches(sparring.id), "$path.id", "场次 ID 无效")
        reference("$path.npcId", sparring.npcId, npcs.keys)
        npcs[sparring.npcId]?.let {
            check(NpcService.SPAR in it.services, "$path.npcId", "对手须提供切磋入口")
        }
        check(sparring.damageVariance >= 0, "$path.damageVariance", "浮动幅度须为非负整数")
        check(opponent.strength > 0, "$path.opponent.strength", "膂力须为正整数")
        check(opponent.agility > 0, "$path.opponent.agility", "敏捷须为正整数")
        check(opponent.maxHealth > 0, "$path.opponent.maxHealth", "气血上限须为正整数")
        check(opponent.maxInnerPower > 0, "$path.opponent.maxInnerPower", "内力上限须为正整数")
        validateSparringDefense("$path.playerDefense", sparring.playerDefense)
        validateSparringDefense("$path.opponent.defense", opponent.defense)
        check(sparring.lootGroups.sumOf { it.weight.toLong() } > 0, "$path.lootGroups", "总权重须为正整数")
        sparring.lootGroups.forEachIndexed { index, group ->
            val groupPath = "$path.lootGroups[$index]"
            check(group.weight >= 0, "$groupPath.weight", "权重须为非负整数")
            validateLoot("$groupPath.items", group.items, itemIds)
        }
        sparring.requiredLoot.forEachIndexed { index, drop ->
            val dropPath = "$path.requiredLoot[$index]"
            val objectives = quests[drop.questId]?.objectives.orEmpty()
            check(objectives.isNotEmpty(), "$dropPath.questId", "须引用有进度的任务")
            check(drop.objectiveId == null || objectives.any { it.id == drop.objectiveId },
                "$dropPath.objectiveId", "须引用该任务的目标")
            check(drop.items.isNotEmpty(), "$dropPath.items", "必掉组须包含物品")
            validateLoot("$dropPath.items", drop.items, itemIds)
        }
    }

    private fun validateLoot(path: String, items: Map<String, Int>, itemIds: Set<String>) {
        items.forEach { (id, quantity) ->
            reference("$path.$id", id, itemIds)
            check(quantity > 0, "$path.$id", "数量须为正整数")
        }
    }

    private fun validateSparringDefense(path: String, defense: SparringDefenseDefinition) {
        check(defense.baseParryPercent in 0..100, "$path.baseParryPercent", "基础招架率须在 0 至 100 之间")
        check(defense.protection >= 0, "$path.protection", "防护须为非负整数")
        check(defense.evasionBonus in -100..100, "$path.evasionBonus", "闪避修正须在 -100 至 100 个百分点之间")
    }

    private fun <T : ContentEntry> table(name: String, entries: List<T>): Map<String, T> {
        val entriesById = mutableMapOf<String, T>()
        entries.forEachIndexed { index, entry ->
            val path = "$.$name[$index]"
            check(ID_PATTERN.matches(entry.id), "$path.id", "ID 需使用小写字母、数字和下划线，并以字母开头")
            check(entriesById.putIfAbsent(entry.id, entry) == null, "$path.id", "重复 ID：${entry.id}")
            check(entry.name.isNotBlank(), "$path.name", "名称不能为空")
        }
        return entriesById
    }

    private fun validateWorld(content: GameContent, scenes: Map<String, SceneDefinition>) {
        val occupiedTiles = mutableSetOf<WorldPosition>()
        content.locations.forEachIndexed { index, location ->
            val path = "$.locations[$index]"
            reference("$path.sceneId", location.sceneId, scenes.keys)
            val scene = scenes[location.sceneId] ?: return@forEachIndexed
            check(scene.isWalkable(location.tile), "$path.tile", "地点须位于可通行格")
            check(occupiedTiles.add(WorldPosition(scene.id, location.tile)), "$path.tile", "同一格已配置地点")
            location.objectTile?.let { tile ->
                check(
                    scene.contains(tile) && scene.contains(location.tile) &&
                        abs(tile.x - location.tile.x) + abs(tile.y - location.tile.y) <= 1,
                    "$path.objectTile",
                    "对象格须在格图内，与地点同格或正交相邻",
                )
            }
        }
        val exits = content.scenes.flatMap { scene -> scene.exits.map { it.id to (scene.id to it) } }.toMap()
        val exitIds = mutableSetOf<String>()
        content.scenes.forEachIndexed { index, scene ->
            val path = "$.scenes[$index]"
            check(scene.width > 0, "$path.tiles", "格图须有至少一行且行宽为正")
            scene.tiles.forEachIndexed { row, tiles ->
                check(tiles.length == scene.width, "$path.tiles[$row]", "格图行宽须一致")
                check(tiles.all { it == '.' || it == '#' }, "$path.tiles[$row]", "格图只接受通行格 . 与阻挡格 #")
            }
            if (scene.terrain.isNotEmpty()) {
                check(scene.terrain.size == scene.height, "$path.terrain", "地面行数须与通行格一致")
                scene.terrain.forEachIndexed { y, row ->
                    check(row.length == scene.width, "$path.terrain[$y]", "地面行宽须与通行格一致")
                    check(row.all { it in ".rwvhf" }, "$path.terrain[$y]", "未知地面类型")
                    row.forEachIndexed { x, terrain ->
                        if (terrain == 'w') check(!scene.isWalkable(com.baijin.game.core.Tile(x, y)),
                            "$path.terrain[$y][$x]", "水道不能作为通行格")
                        if (terrain == 'h' || terrain == 'v') check(scene.isWalkable(com.baijin.game.core.Tile(x, y)),
                            "$path.terrain[$y][$x]", "桥梁须可通行")
                    }
                }
            }
            scene.scenery.forEachIndexed { propIndex, prop ->
                val propPath = "$path.scenery[$propIndex]"
                check(prop.x.isFinite() && prop.x in 0f..scene.width.toFloat(), "$propPath.x", "景物横坐标须在场景内")
                check(prop.y.isFinite() && prop.y in 0f..scene.height.toFloat(), "$propPath.y", "景物落脚点须在场景内")
                check(prop.width.isFinite() && prop.width > 0, "$propPath.width", "景物宽度须为正数")
                prop.height?.let { check(it.isFinite() && it > 0, "$propPath.height", "景物高度须为正数") }
                prop.footprint.forEachIndexed { tileIndex, tile ->
                    check(scene.contains(tile) && !scene.isWalkable(tile), "$propPath.footprint[$tileIndex]", "景物占地须在阻挡格内")
                }
            }
            scene.exits.forEachIndexed { exitIndex, exit ->
                val exitPath = "$path.exits[$exitIndex]"
                check(ID_PATTERN.matches(exit.id), "$exitPath.id", "出口 ID 格式无效")
                check(exitIds.add(exit.id), "$exitPath.id", "重复出口 ID：${exit.id}")
                check(exit.name.isNotBlank(), "$exitPath.name", "名称不能为空")
                check(scene.isWalkable(exit.tile), "$exitPath.tile", "出口须位于可通行格")
                check(occupiedTiles.add(WorldPosition(scene.id, exit.tile)), "$exitPath.tile", "同一格已配置地点或出口")
                val beyond = exit.tile.step(exit.direction)
                val destinationId = exit.destinationExitId
                check(!exit.onEntry || destinationId != null, "$exitPath.onEntry", "踩格切换须有目标出入口")
                if (destinationId == null) {
                    check(!scene.contains(beyond), "$exitPath.direction", "出口须朝向格图外")
                } else {
                    check(!scene.isWalkable(beyond), "$exitPath.direction", "场景出入口须朝向门后的阻挡格或格图外")
                    val destinationPath = "$exitPath.destinationExitId"
                    reference(destinationPath, destinationId, exits.keys)
                    exits[destinationId]?.let { (otherSceneId, otherExit) ->
                        check(otherSceneId != scene.id && otherExit.destinationExitId == exit.id, destinationPath, "出入口须与另一场景的出入口互相指向")
                    }
                }
                val inside = exit.tile.step(exit.direction.opposite)
                check(
                    exit.returnTile == inside && scene.isWalkable(exit.returnTile),
                    "$exitPath.returnTile",
                    "返程格须是出口内侧相邻的可通行格",
                )
            }
        }
    }

    private fun validateReward(
        path: String,
        reward: QuestReward,
        itemIds: Set<String>,
        martialArts: Map<String, MartialArtDefinition>,
    ) {
        check(reward.coins >= 0, "$path.coins", "铜钱须为非负整数")
        check(reward.potential >= 0, "$path.potential", "潜能须为非负整数")
        check(reward.experience >= 0, "$path.experience", "实战经验须为非负整数")

        val rewardedItems = mutableSetOf<String>()
        reward.items.forEachIndexed { index, item ->
            val itemPath = "$path.items[$index]"
            reference("$itemPath.itemId", item.itemId, itemIds)
            check(rewardedItems.add(item.itemId), "$itemPath.itemId", "同一奖励的物品 ID 重复")
            check(item.quantity > 0, "$itemPath.quantity", "物品数量须为正整数")
        }

        val rewardedArts = mutableSetOf<String>()
        reward.martialArts.forEachIndexed { index, grant ->
            val artPath = "$path.martialArts[$index]"
            reference("$artPath.martialArtId", grant.martialArtId, martialArts.keys)
            check(rewardedArts.add(grant.martialArtId), "$artPath.martialArtId", "同一奖励的武学 ID 重复")
            val maxLevel = martialArts[grant.martialArtId]?.maxLevel
            check(grant.level > 0, "$artPath.level", "授艺等级须为正整数")
            if (maxLevel != null) {
                check(grant.level <= maxLevel, "$artPath.level", "授艺等级不能超过 $maxLevel")
            }
        }
    }

    private fun validateAppearance(appearance: AppearanceDefinition) {
        val weights = appearance.weights
        check(weights.strength > 0, "$.appearance.weights.strength", "膂力权重须为正整数")
        check(weights.agility > 0, "$.appearance.weights.agility", "敏捷权重须为正整数")
        check(weights.insight > 0, "$.appearance.weights.insight", "悟性权重须为正整数")
        check(weights.constitution > 0, "$.appearance.weights.constitution", "根骨权重须为正整数")

        val thresholds = appearance.thresholds
        check(thresholds.size == 6, "$.appearance.thresholds", "须配置六个分档下限")
        thresholds.zipWithNext().forEachIndexed { index, (lower, higher) ->
            check(higher > lower, "$.appearance.thresholds[${index + 1}]", "分档下限须严格递增")
        }
        check(appearance.descriptions.size == 7, "$.appearance.descriptions", "须配置七档描述")
        appearance.descriptions.forEachIndexed { index, description ->
            val path = "$.appearance.descriptions[$index]"
            check(description.male.isNotBlank(), "$path.male", "男角描述不能为空")
            check(description.female.isNotBlank(), "$path.female", "女角描述不能为空")
        }
    }

    private fun validateCreation(creation: CharacterCreationDefinition) {
        val path = "$.creation"
        val attributeSpan = creation.maxAttribute.toLong() - creation.minAttribute
        check(creation.minAttribute > 0, "$path.minAttribute", "四维初值须为正整数")
        check(attributeSpan >= 0, "$path.maxAttribute", "四维上限不能低于初值")
        check(
            creation.attributePoints.toLong() in 0..(Attribute.entries.size * attributeSpan),
            "$path.attributePoints",
            "配点须在四维可分配范围内",
        )
        val maxLuckBonus = minOf(creation.attributePoints.toLong(), 2 * attributeSpan)
        check(
            creation.maxLuckDraw in 1..100 && creation.maxLuckDraw + maxLuckBonus <= 100,
            "$path.maxLuckDraw",
            "抽值和悟性、根骨加成后的幸运须在 1 至 100 之间",
        )
        check(creation.healthBase > 0, "$path.healthBase", "基础气血须为正整数")
        check(creation.innerPowerBase > 0, "$path.innerPowerBase", "基础内力须为正整数")
        check(
            creation.healthPerConstitution >= 0 &&
                creation.healthBase + creation.healthPerConstitution.toLong() * creation.maxAttribute <= Int.MAX_VALUE,
            "$path.healthPerConstitution",
            "气血根骨系数须为非负整数，且总上限须在整数范围内",
        )
        check(
            creation.innerPowerPerConstitution >= 0 &&
                creation.innerPowerBase + creation.innerPowerPerConstitution.toLong() * creation.maxAttribute <= Int.MAX_VALUE,
            "$path.innerPowerPerConstitution",
            "内力根骨系数须为非负整数，且总上限须在整数范围内",
        )
        check(creation.initialCoins >= 0, "$path.initialCoins", "初始铜钱须为非负整数")
    }

    private fun validateTime(time: GameTimeDefinition) {
        check(time.hungerInterval > 0, "$.time.hungerInterval", "饥饿间隔须为正整数")
        check(time.maxHunger > 0, "$.time.maxHunger", "饥饿上限须为正整数")
        check(time.thirstInterval > 0, "$.time.thirstInterval", "口渴间隔须为正整数")
        check(time.maxThirst > 0, "$.time.maxThirst", "口渴上限须为正整数")
        check(time.healthRecoveryInterval > 0, "$.time.healthRecoveryInterval", "气血恢复间隔须为正整数")
        check(time.healthRecovery >= 0, "$.time.healthRecovery", "气血恢复量须为非负整数")
        check(time.innerPowerRecoveryInterval > 0, "$.time.innerPowerRecoveryInterval", "内力恢复间隔须为正整数")
        check(time.innerPowerRecovery >= 0, "$.time.innerPowerRecovery", "内力恢复量须为非负整数")
        check(time.meditationDuration > 0, "$.time.meditationDuration", "打坐时长须为正整数")
        check(time.meditationRecovery >= 0, "$.time.meditationRecovery", "打坐恢复量须为非负整数")
    }

    private fun reference(path: String, id: String, knownIds: Set<String>) {
        check(id in knownIds, path, "找不到 ID：$id")
    }

    private fun check(valid: Boolean, path: String, message: String) {
        if (!valid) issues.add(ContentIssue(path, message))
    }

    private companion object {
        val ID_PATTERN = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")
    }
}
