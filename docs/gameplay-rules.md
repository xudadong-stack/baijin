# 玩法模块索引

在下表查找各玩法的细则、数值试算和待定规则。开局、中期和终局按角色在游戏内的进度划分，数值根据实玩调整。[玩法范围](gameplay.md) · [验收规则](acceptance.md)

玩法设计借鉴文曲星掌机版的修炼、生活状态和角色选择。地图、人物、门派、任务、事件、文案和数值采用白金项目的原创设计。[掌机版玩家攻略](https://zhidao.baidu.com/question/2451865.html?qbl=relate_question_5) · [原版开发参与者访谈](https://www.jiemian.com/article/1144843.html)

## 玩法模块

| 模块 | 规则和数值 | 验收 |
| --- | --- | --- |
| [角色与补给](modules/character.md) | 四维、[相貌](modules/character.md#相貌)、游戏时间、饥渴、饮食与自然恢复 | [验收](modules/character.md#验收场景) |
| [任务与经济](modules/quests/README.md) | 委托、发奖、差事、任务设计与报酬 | [验收](modules/quests/README.md#验收场景) |
| [武学成长](modules/martial-arts.md) | [八项基本功](modules/martial-arts.md#八项基本功)、[两条门派路线](modules/martial-arts.md#两条门派路线)、请教、自练、运用与进阶招式 | [验收](modules/martial-arts.md#验收场景) |
| [战斗](modules/combat.md) | [文字战斗页面](modules/combat.md#战斗页面)、回合、命中闪避、招架伤害、伤势和战败 | [验收](modules/combat.md#验收场景) |
| [物品与装备](modules/items.md) | 背包、[61 种装备的属性与取得](modules/equipment.md)、[商贩与货单](modules/items.md#交易)、普通掉落与任务必掉 | [验收](modules/items.md#验收场景) |
| [探索与剧情](modules/world.md) | [NPC 人物索引](modules/npcs.md)、区域开放、换物、秘笈、六派信物和结局 | [验收](modules/world.md#验收场景) |

## 角色成长与吃喝

[角色成长与吃喝](modules/character.md#角色成长与吃喝)

## 委托与经济

[委托与经济](modules/quests/README.md#委托与经济)

### 任务必需掉落

[任务必需掉落](modules/items.md#任务必需掉落)

### 任务并行与交付

[任务并行与交付](modules/quests/README.md#任务并行与交付)

### 任务派发与结算

[任务派发与结算](modules/quests/design.md)

## 武学与修炼

[武学与修炼](modules/martial-arts.md#武学与修炼)

## 气血、内力、受伤与疗伤

[气血、内力、受伤与疗伤](modules/combat.md#气血内力受伤与疗伤)

## 战斗与装备

[战斗与装备](modules/combat.md#战斗规则)

### 普通掉落

[普通掉落](modules/items.md#普通掉落)

### 武器、护具与背包

[武器、护具与背包](modules/items.md#武器护具与背包)

### 战败处置

[战败处置](modules/combat.md#战败处置)

## 多区域探索与支线

[多区域探索与支线](modules/world.md#多区域探索与支线)

### 物品交换

[物品交换](modules/world.md#物品交换)

### 隐藏秘笈

[隐藏秘笈](modules/world.md#隐藏秘笈)

### 六派信物

[六派信物](modules/world.md#六派信物)

## 声望、通缉与结局

[声望、通缉与结局](modules/world.md#声望通缉与结局)

## 配置与完整验收

- 数值配置与平衡试算见对应章节。[武器与护具](modules/items.md#武器与护具) · [普通掉落权重](modules/items.md#普通掉落权重) · [任务奖励](modules/quests/rewards.md#任务奖励)
- 任务的对话入口、目标与交付流程见 [任务设计](modules/quests/design.md)。
- 所有初始样例值、武学门槛、时间间隔、物品价格、掉落权重、声望变化和任务奖励由内容配置提供，按实际玩法检验合法范围与引用。掉落表可包含空结果。
- 完整验收以同一版本覆盖开局、门派成长、探索、支线和终局。从建角到主线通关沿用同一份存档，并分别验证两条门派路线、各项任务交付和读档续玩。
- 任务派发与结算、购买、食用、学习、战斗回合和领取战利品均以完整状态提交。文档中的验收场景可分别用于规则测试和真机验收。[模块边界与架构](architecture.md) · [验收规则](acceptance.md)
