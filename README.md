# Thiker Magic 匠艺魔法

把 Ars Nouveau（魔艺）的法力与施法接进 Tinkers' Construct（匠魂）：用魔艺的材料做出的工具或装备，
天生就带对应词条 —— 拿着或穿着，法力上限、法力回复、施法冷却都会跟着动。

所有词条都是 `data/thikermagic/tinkering/modifiers/` 下的纯 JSON，别的整合包可以直接引用同一批 ID、
用自己的配方改写，或者挂到自己的工具上。

## 属性

模组注册了五个属性并挂到玩家身上；数据包用 `tconstruct:attribute` 模块引用它们，所以任何词条
（本模组的或别的包的）都能喂这些属性。

| 属性 | 默认 | 含义 |
| --- | --- | --- |
| `thikermagic:mana_regen` | `0` | 魔艺法力回复的百分比加成，`0.25` = +25% |
| `thikermagic:max_mana` | `0` | 魔艺法力上限的百分比加成，`0.25` = +25% |
| `thikermagic:flat_max_mana` | `0` | 固定点数上限，在百分比之前结算，两者合起来是 `(基础 + 固定) × (1 + 百分比)`，`30` = +30 点 |
| `thikermagic:mana_on_equip` | `0` | 装备时**一次性**结算的法力点数，不是持续加成，`20` = 回 20 点 |
| `thikermagic:spell_cooldown_reduction` | `0` | 施法冷却被削掉的比例，`0.4` = 只剩 60% 时长 |

## 词条

| ID | 类型 | 等级 | 每级效果 |
| --- | --- | --- | --- |
| `thikermagic:mana_siphon` | 升级 | 3 | 法力回复 +15% |
| `thikermagic:spell_haste` | 升级 | 3 | 施法冷却缩减 +8% |
| `thikermagic:arcane_attunement` | 升级 | 3 | 法力上限 +10% |
| `thikermagic:mana_reservoir` | 能力 | 1 | 法力上限 +40%，法力回复 +25% |
| `thikermagic:arcane_surge` | 能力 | 1 | 冷却缩减 +30%，法力回复 +30% |

这五个都收在词条标签 `thikermagic:arcane` 里。

## Forge Energy（FE）词条

`thikermagic:charged_core`（升级，1–3 级，标签 `thikermagic:energy`）不是魔艺词条，是管 FE 能量的那个。
存储部分匠魂本体就提供了：`ToolEnergyCapability` 把电量存在工具的持久数据 `tconstruct:energy` 里，
容量是工具属性 `tconstruct:max_energy`，`ToolCapabilityProvider` 会把所有容量大于 0 的工具注册成
`IEnergyStorage`。所以这个词条只负责提供数值：

- 容量：`tconstruct:stat_boost` 作用于 `tconstruct:max_energy`，**每级 10000**
- `tconstruct:trait` 指向 `tconstruct:energy_handler`：工具数据变化时把电量夹到容量范围内，
  并在 tooltip 上显示 `Energy: 当前 / 上限 FE`
- `thikermagic:charged_attack`：`com.yg.thikermagic.energy` 里的自定义模块

按 `充能比例 = 电量 / 容量` 算，模块把近战伤害乘上 `1 + 0.2 × 比例`；`EnergyAttack` 则把
`0.5 × 比例` 作为临时属性修饰符挂到玩家的 `minecraft:generic.attack_speed` 上，每 tick 重新算一次，
取双手里那件充能更高的工具。每次成功命中扣 100 FE（`addEnergy` 会在 0 处夹断，所以没电的工具照样能用，
只是没有加成）。

攻速特意没有走匠魂的 `tconstruct:attribute` 模块：那种物品属性修饰符是在装备时构建一次的，而钩子文档
明确要求装卸前后列表不能变，可电量每一击都在变。

模块的三个数值都是数据包字段（`damage_bonus`、`attack_speed_bonus`、`energy_per_attack`），
整合包可以调它们，也可以把模块挂到自己的词条上：

```json
{
  "type": "thikermagic:charged_attack",
  "damage_bonus": 0.2,
  "attack_speed_bonus": 0.5,
  "energy_per_attack": 100
}
```

反方向的例子在 arcana 数据包（`datapacks/arcana/`）里：它给 `tconstruct:iron`（铁）的头／柄／绑定
挂上这个词条和 `arcana:manaflow`，于是所有用铁做头部/绑定的工具、武器都自带 FE 词条 ——
写法是往包里加 `data/tconstruct/tinkering/materials/traits/iron.json`。因为匠魂给每个能做工具部件的
材料都写了词条（铁的是 `default` 里的 `tconstruct:magnetic`），文件里要把那条原词条一起列出来：
`perStat` 的键是**替换**不是追加。想换成别的材料，照抄这个文件就行；类别键、合并规则、怎么只影响单个
部位、以及为什么 `materials traits` 命令必须带部位类型，都在那个包的 README 里。

### 给工具充能

工具本身就是标准 `IEnergyStorage`，所以**任何能给物品充能的装置都能直接充** —— 科技模组的充电器、
电池、玩家充电台都可以，完全不需要本模组参与。

没有科技模组的包，`EnergyCharging` 提供了原版手段：手持工具、另一只手持充能物品，**潜行 + 右键**。
默认数值如下，都能在 `config/thikermagic-common.toml` 的 `[energy_charging]` 里改：

| 物品 | 能量 |
| --- | --- |
| `minecraft:redstone` 红石粉 | 1000 FE |
| `minecraft:redstone_block` 红石块 | 9000 FE |
| `minecraft:glowstone_dust` 萤石粉 | 2000 FE |
| `minecraft:amethyst_shard` 紫水晶碎片 | 4000 FE |

`items` 接受 `"<物品 ID>=<fe>"` 的写法，整合包可以指向自己的电池物品。物品只按**实际装得下的量**
扣（快满电时不会白烧一整块），满电只提示不吞物品；`requiresSneak = false` 可以让普通右键也充能，
默认 `true` 是为了不抢工具自己的右键功能。

### 战斗链路实测

开发服务端，让僵尸拿着**全铁剑**（三个部件全铁 → `charged_core` 6 级、容量 60000）打牛，
伤害用 `/damage <牛> 4 minecraft:mob_attack by <僵尸>` 施加：

| 工具电量 | 实际伤害 | 牛的血量 | 打完之后电量 |
| --- | --- | --- | --- |
| `{}`（空） | 4.0 | 10.0 → 6.0 | `{}` |
| `60000`（满） | 4.8 = 4 × 1.2 | 6.0 → 5.2 | `59900` |

这就是 `MONSTER_MELEE_DAMAGE`（满电 +20% 伤害）和 `MONSTER_MELEE_HIT`（每击 100 FE）端到端跑通的结果。
玩家那条路走的是同一对方法（`MELEE_DAMAGE` / `afterMeleeHit`），所以数字一样；攻速那一半是玩家属性，
仍然需要真客户端才能观察。

## 钩子怎么工作

模组监听了 Ars Nouveau 的三个公开事件：

- `ManaRegenCalcEvent` —— 在 `ManaUtil#getManaRegen` 里每个回复 tick 触发一次，属性按比例缩放算好的值
- `MaxManaCalcEvent` —— 在 `ManaUtil#calcMaxMana` 里、装备/符文/书等级都算完之后触发
- `SpellCastEvent` —— 施法结算之前触发，可取消

另外两个 NeoForge 钩子补上剩余部分：`LivingEquipmentChangeEvent` 捕捉换装，服务器 tick 末尾结算它排队
的 `thikermagic:mana_on_equip` 付款（每个玩家 20 tick 内只结算一次，反复换装刷不出法力）。

### 保住法力条的比例

Ars Nouveau **每 tick** 重算法力上限，并在每次写法力时把当前值夹到新上限。因为本模组喂进去的上限加成
都长在匠魂装备上，收起工具或脱下一件护甲就会挪动上限：上限变大时法力条丢掉填充比例，上限变小时
超出的法力被直接抹掉。

`[max_mana] keepManaRatio`（默认 `true`，在 `config/thikermagic-common.toml`）会按上限变化的同一比例
缩放当前法力，所以换装前后满条仍然是满条。设成 `false` 就交回给 Ars Nouveau 自己夹断。

缩放在服务器 tick 末尾执行。Ars Nouveau 在换装**后一 tick** 的 `PlayerTickEvent.Pre` 刷新上限，
再下一 tick 的回复才夹断，所以比例恢复时新上限已经就位。

Ars Nouveau **本身没有施法冷却**，冷却由本模组提供：成功施法后施法工具进入 `castCooldownTicks`
（默认 `10`，可在 `config/thikermagic-common.toml` 配置）的冷却，`thikermagic:spell_cooldown_reduction`
会缩短它。把 `cooldownTicks = 0` 可以完全不动魔艺的施法 —— 另外两个属性照常工作。

Ars Nouveau 是**可选**前置。没装时属性照常注册、词条照常生效，只是没有作用对象。

## 写自己的词条

`data/<你的整合包>/tinkering/modifiers/arcane_focus.json`：

```json
{
  "level_display": "tconstruct:default",
  "modules": [
    {
      "type": "tconstruct:attribute",
      "attribute": "thikermagic:mana_regen",
      "each_level": 0.2,
      "operation": "add_value",
      "slots": ["mainhand", "offhand", "head", "chest", "legs", "feet"],
      "tooltip_style": "percent"
    }
  ],
  "tooltip_display": "always"
}
```

## 并入的模组

独立的 `Ticglossary`（匠魂词库）已经并进本模组，现在一个 jar 同时带两边的词条。它那两个词条落在
`thikermagic` 命名空间下，其余一切不变：

| ID | 类型 | 用什么实现 | 效果 |
| --- | --- | --- | --- |
| `thikermagic:lifesteal` | 升级，最高 5 级 | 匠魂自带的 `tconstruct:lifesteal` 模块 | 每级回复造成伤害的 5%，每次触发多磨 1 点耐久 |
| `thikermagic:creative_flight` | 能力 | NeoForge 自带的 `neoforge:creative_flight` 属性 | 穿胸甲时像创造模式那样飞 |

它们的配方、打捞配方、翻译、词条图标，以及让它们出现在匠魂手册里的标签条目
（`tconstruct:modifiers/upgrades/general`、`tconstruct:modifiers/abilities/general`）都一并搬过来了。
图标还挪到了匠魂真正会读的路径 `assets/<命名空间>/tinkering/modifier_icons.json` —— 旧模组把它写成
`tinkering/modifiers.json`，而 `ModifierIconManager` 从不加载那个文件名，所以图标一直没显示过。

`../Ticglossary1.21.1` 保留为合并前的快照。别和本模组同时装：它的 `ticglossary:*` 和上面的词条是重复的。

## 构建

```powershell
$env:JAVA_HOME='D:\code\mcmod\.toolchains\jdk-21\jdk-21.0.11+10'
$env:GRADLE_USER_HOME='D:\code\mcmod\.gradle-home'
.\gradlew.bat build
```

产物 jar 在 `build/libs/`。Tinkers' Construct、Mantle、Ars Nouveau 都是从同级 checkout／项目目录里
以 compile-only 方式引入的，所以永远不会被打进 jar。
