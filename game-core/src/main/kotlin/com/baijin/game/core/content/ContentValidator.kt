package com.baijin.game.core.content

internal class ContentValidator {
    private val issues = mutableListOf<ContentIssue>()

    fun validate(content: GameContent): List<ContentIssue> {
        check(content.contentVersion > 0, "$.contentVersion", "内容版本须为正整数")
        val locations = table("locations", content.locations)
        val npcs = table("npcs", content.npcs)
        val martialArts = table("martialArts", content.martialArts)
        val items = table("items", content.items)
        table("quests", content.quests)

        reference("$.startLocationId", content.startLocationId, locations.keys)
        content.npcs.forEachIndexed { index, npc ->
            val path = "$.npcs[$index]"
            reference("$path.locationId", npc.locationId, locations.keys)
            check(npc.morality in -100..100, "$path.morality", "善恶值须在 -100 至 100 之间")
            check(npc.initialAffinity in -100..100, "$path.initialAffinity", "初始好感度须在 -100 至 100 之间")
            check(npc.luck in 1..100, "$path.luck", "幸运须在 1 至 100 之间")
        }
        content.martialArts.forEachIndexed { index, art ->
            check(art.maxLevel > 0, "$.martialArts[$index].maxLevel", "最高等级须为正整数")
        }
        content.quests.forEachIndexed { index, quest ->
            val path = "$.quests[$index]"
            reference("$path.giverNpcId", quest.giverNpcId, npcs.keys)
            reference("$path.turnInNpcId", quest.turnInNpcId, npcs.keys)
            validateReward("$path.reward", quest.reward, items.keys, martialArts)
        }
        validateAppearance(content.appearance)
        return issues.toList()
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
