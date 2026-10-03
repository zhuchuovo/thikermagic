# Arcana

纯数据包，把 ThikerMagic 的属性接到匠魂材料上。三个材料、四个词条全部是 JSON，
ID 稳定，可以被别的整合包引用、改配方、或挂到自己的工具上。另外带一条**示例绑定**：
把两个还没有材料的 trait 挂到原版 `tconstruct:iron` 的头 / 柄 / 绑定上，
让铁制工具武器直接带 FE（见下面「示例」一节）。

## 安装

把整个 `arcana/` 目录丢进 `<存档>/datapacks/`，或者打包成 zip 放进去。

> **打包必须用正斜杠路径。** Windows 上 PowerShell 的 `Compress-Archive` 会把 zip 条目名写成
> `data\arcana\...`，而 Minecraft 的 zip 文件系统不认反斜杠分隔符 —— 这种包**一个文件都读不到**，
> 表现是所有词条静默失效（穿上装备「提升最大法力不生效」、manawell 不回蓝），日志里也不报错。
> 用 `tar -a -c -f arcana.zip pack.mcmeta README.md data`、7-Zip 或 `jar` 打包，
> 打完用 `tar -tf arcana.zip` 确认条目是 `data/arcana/...` 而不是 `data\arcana\...`。

依赖：Tinkers' Construct 3.12.6+、ThikerMagic。Ars Nouveau 只有材料映射配方需要
（用 `neoforge:mod_loaded` 守卫），没有魔艺时材料和词条照常注册，只是拿不到原料。

## 属性

数据包用 `tconstruct:attribute` 模块引用这些属性，属性本身由 ThikerMagic 注册在玩家身上。

| 属性 | 默认 | 含义 |
| --- | --- | --- |
| `thikermagic:mana_regen` | 0 | **每 tick 固定点数**，0.25 = 每 tick 多回 0.25 点（每秒 5 点） |
| `thikermagic:max_mana` | 0 | 最大法力倍率 |
| `thikermagic:spell_cooldown_reduction` | 0 | 冷却缩减比例，0.4 = 只剩 60% 时长 |
| `thikermagic:flat_max_mana` | 0 | **固定**最大法力点数，30 = 直接 +30 点 |
| `thikermagic:mana_on_equip` | 0 | 穿戴时**一次性**回复的法力点数，20 = 回复 20 点（目前没有词条在用） |

`mana_regen` 是**每 tick 的固定点数**（不是倍率）：Ars Nouveau 自己的回蓝值单位是「每秒」，
集成层把属性值 ×20 之后加在上面，所以 0.25 就是每 tick 多回 0.25 点、每秒多回 5 点。
加成对玩家的书 tier / 符文数不敏感，等级越高只会越快地往上加，不会因为底子变大而显得变慢；
工具 tooltip 也直接显示「+0.25 法力回复」这种点数值。

`max_mana` 是持续的倍率，`flat_max_mana` 是持续的点数，`mana_regen` 是持续的点数，`mana_on_equip`
不一样 —— 它是装备变化时结算**一次**的点数，不是持续值，而且只有属性值变大时才结算。五个属性都
只注册在玩家身上；`flat_max_mana` 与 `mana_on_equip` 目前没有词条在用，保留给别的整合包引用。

## 四个词条

| 词条 | 目标属性 | 每级 | 操作 |
| --- | --- | --- | --- |
| `arcana:manaflow` | `thikermagic:mana_regen` | +0.25 点/tick | add_value |
| `arcana:deepwell` | `thikermagic:max_mana` | +10% | add_value |
| `arcana:swiftcast` | `thikermagic:spell_cooldown_reduction` | +8% | add_value |
| `arcana:manawell` | `thikermagic:mana_regen` | +0.5 点/tick | add_value |

六个装备槽全部生效（`mainhand/offhand/head/chest/legs/feet`）。`manaflow` 和 `manawell` 加的是固定
点数，走 `boost` 风格；`deepwell` 和 `swiftcast` 是比例，走 `percent` 风格。

### 在词条册里查到它们

`data/tconstruct/tinkering/tags/modifiers/upgrades/general.json` 把这四条 trait 写进了匠魂百科的
「词条 → 升级 → 通用」组（TiC 自己的材料 trait 也在那儿，比如铁的 `tconstruct:magnetic`），
所以在册子里能查到「深井 / 疾咏 / 魔力之井 / 法力涌流」，页面由 TiC 现场生成，不需要手写页面文件。
这个 tag **只管册子显示**：`ModifierTagProvider` 之外没有任何 Java 代码读 `upgrades/*`，不参与配方、
槽位或词条本身的行为判定（词条能做在什么工具上由配方和模块决定）。

改完这个 tag 后要 `/reload`（词条注册表重新同步）+ 重开书；只按 `F3+T` 或只 `/reload` 其中之一，
册子里可能还是旧列表。

`arcana:manaflow`（每级每 tick +0.25 点法力）现在挂在 `tconstruct:iron` 的头 / 柄 / 绑定上当示例，
别的整合包想换材料，改那份 trait 文件就行（见下面「示例」一节）。

### manawell 是持续回蓝，不是一次性 payout

它挂在 `thikermagic:mana_regen` 上，装备或手持期间**每 tick 都回**：0.5/级 = 每秒 10 点，
二级就是每秒 20 点（Ars 自己的基础回蓝是每秒 5 点，对照着看量级）。

一次性 payout 的机制还留在 `ArsNouveauIntegration` 里（`thikermagic:mana_on_equip` 属性），
目前没有词条用它，留着给别的整合包引用。它的触发条件收得很紧：**只有属性值变大才付款**，
也就是「把这件装备穿上去」才算数 —— `LivingEquipmentChangeEvent` 对每个槽位都会触发，
触发条件放宽的话，切换手持物品也会把身上那件装备的 mana_on_equip 反复结账，
表现就是「没穿脱装备也在猛涨法力」。同一个玩家 20 tick 内也仍然只结算一次。

### 上限变化时，法力条的比例会被保住

`arcana:deepwell` 这类词条改的是**上限**。Ars Nouveau 每 tick 重算上限，并且在每次写法力时
把当前值 clamp 到上限，所以只要穿脱或持放带这类词条的装备：

- 上限变大（穿上）→ 当前法力不跟着涨 → 条看起来只剩一截；
- 上限变小（脱下）→ 超出新上限的那部分法力被直接抹掉。

`ArsNouveauIntegration` 在服务器 tick 末尾发现上限变了，就按同一比例缩放当前法力，
满条穿脱之后仍然是满条。开关在 `config/thikermagic-common.toml` 的 `[max_mana] keepManaRatio`
（默认 `true`），关掉就回到 Ars 原本的 clamp 行为。

时序上来得及：Ars 在装备变化**后一 tick** 的 `PlayerTickEvent.Pre` 刷新上限，再下一 tick 的回复
才做 clamp，而比例缩放在服务器 tick 末执行。

### arcana:manawell 的获取

`manawell` 同时是 `arcana:starstone` 的材料 trait，所以用星辉石做的工具自带二级。
想给别的工具加上去，用工具工坊逐级打，三级配方都在
`data/arcana/recipe/tools/modifiers/upgrades/`：

| 等级 | 配方 | 消耗 |
| --- | --- | --- |
| I | `manawell_1.json` | 源质宝石 x3 + 1 个升级槽 |
| II | `manawell_2.json` | 源质宝石 x4 + 魔花纤维 x2 |
| III | `manawell_3.json` | 源质宝石 x6 + 防护精华 x1 |

工具范围和 ThikerMagic 自带词条一致：`tconstruct:modifiable/armor` 和 `tconstruct:modifiable/held`。

## 三个材料

每个材料自带一个词条作为 trait，默认 2 级。用这个材料做出来的工具自动生效，不需要额外配方。

| 材料 | tier | trait | 头部件 | 原料 |
| --- | --- | --- | --- | --- |
| `arcana:crystalsong` | 3 | `arcana:deepwell` x2 | 耐久 700 / 攻击 2.0 / 挖掘 6.0 / diamond | `ars_nouveau:source_gem` |
| `arcana:quicksteel` | 3 | `arcana:swiftcast` x2 | 耐久 380 / 攻击 1.75 / 挖掘 7.5 / diamond | `ars_nouveau:manipulation_essence` |
| `arcana:starstone` | 3 | `arcana:manawell` x2 | 耐久 420 / 攻击 1.75 / 挖掘 5.5 / diamond | `ars_nouveau:starbuncle_shards` |

三个材料的原料全部来自 Ars Nouveau（星宝石兽信物 = 对星宝石兽用金粒换来），所以材料映射配方
都带 `neoforge:mod_loaded` 守卫 —— 没装魔艺时三个材料照常注册，只是拿不到原料。

`arcana:manawoven`（法力织物）已经移除：定义、贴图、翻译和颜色键都不再随包发布。它的 trait 本来就是
`arcana:deepwell`，和晶歌石重合，留着只是多一份一样的材料。`arcana:manaflow` 词条本身保留
（见上），别的包想用仍然可以拿它去挂自己的材料。

部件怎么做出来的：部件构建器里放**图案 + 上面那件原料**就行。配方用的是 Tinkers 自带的
`tconstruct:part_builder`（按图案匹配，材料从输入物品解析，没有写死材料），所以材料是不是
"能合成"只取决于三件事 —— 材料 `craftable: true`、材料在该部位类型的 stats 存在、
手上的原料数量够 `cost`。三条这个包都满足。

玩家路径：拿原料 -> 部件构建器（图案 + 原料）-> 工具工坊组装 -> 工具自带属性词条。
原料到材料的映射由 `data/arcana/recipe/tools/materials/*.json` 提供（`tconstruct:material` 配方）。

**"做不出来"怎么排查**，按顺序看三件事：

1. 手里要有**图案**（Stencil Table 用木板做），部件构建器里图案和原料缺一不可。
2. 原料数量要够。`tconstruct:material` 配方里 `needed: 1, value: 1`，也就是一个原料算一点材料值；
   镐头 `cost: 2` 就要 2 个原料，护腿板 `cost: 5` 就要 5 个。
3. 游戏内自查材料有没有正确注册部位属性：

```
/tconstruct materials stats tconstruct:head arcana:crystalsong
/tconstruct materials traits arcana:crystalsong
```

第一条返回耐久/挖掘等数值、第二条返回 `arcana:deepwell`，就说明材料数据完全正常，
做不出来只可能是图案或原料的问题。

## 示例：给原版材料挂「工具武器类」词条

### 先查：哪些词条没有材料

`thikermagic` / `arcana` 里的词条分两类：

| 来源 | 词条 | 有没有材料挂 |
| --- | --- | --- |
| 工具工坊配方（`thikermagic` 自带） | `mana_siphon` / `spell_haste` / `arcane_attunement` / `mana_reservoir` / `arcane_surge` / `lifesteal` / `creative_flight` | 没有，本来就靠配方拿 |
| 材料 trait（`arcana` 的设计） | `deepwell` → crystalsong、`swiftcast` → quicksteel、`manawell` → starstone | 三个都有 |
| 同上，但一直没人用 | `manaflow` | **没有** |
| 模组新加的 FE 词条 | `charged_core` | **没有**（它自己有配方，不挂材料也能拿到） |

所以真正"没有绑定材料"的 trait 是 `arcana:manaflow` 和 `thikermagic:charged_core`，这一版把两个都挂上了。

### 哪些材料能做工具武器，却没有词条

TiC 1.21.1 的 104 个材料里，traits 文件是空 `{}` 的只有两个：

| 材料 | stats | 能不能做工具武器部件 |
| --- | --- | --- |
| `tconstruct:feather` | 只有 `tconstruct:fletching` | **不能**，只能做箭羽（弹药） |
| `tconstruct:phantom` | 只有 `tconstruct:repair_kit` | **不能**，`repair_kit` 的 trait 不会被任何工具部件读取 |

`bloodbone` / `chain` / `platinum` / `tungsten` / `rotten_flesh` 这五个连 traits 文件都没有，但它们是
**重定向材料**（分别指向 venombone / rose_gold / seared_stone / iron / leather），本身没有实体数据，
写了也不会生效。

**结论：TiC 里不存在"既能做头/柄/绑定/弓臂，又完全没有词条"的材料** —— 能做近战部件的（`head` /
`handle` / `binding`）和能做远程部件的（`limb` / `grip` / `bowstring`）材料，TiC 本体一律写过词条，
多数还是通过 `default` 给的。所以"工具武器类"的绑定只能用**加而不换**的写法：把 TiC 的原词条和我们的
词条一起列在同一个数组里。这份包挑了最常用、最容易验证的 `tconstruct:iron` 做示例。

### 文件放哪、为什么

```
data/tconstruct/tinkering/materials/traits/iron.json
```

**材料 id 由文件路径决定，不是文件里的字段** —— `MaterialTraitsManager` 直接把
`data/<命名空间>/tinkering/materials/traits/<路径>.json` 的路径当成材料 id，
所以给原版材料加词条时文件必须写在 `tconstruct` 命名空间下，而不是 `arcana` 下。

同一个材料 id 在多个数据包里各有一份文件时，Mantle 的 `MergingJsonDataLoader` 会按包顺序把两份都解析进
同一个 builder：`default` 只在文件提供时替换，`perStat` 的每个 stat type 各自替换（同一个键后加载的覆盖
先加载的，没写的键保持原样）。所以"补词条"是可行的，但**同一个键会顶掉原来的词条** —— 要么把原词条一起
列出来，要么换一个原文件没用过的键。

### 键写「类别」而不是具体部位

TiC 给 stat type 注册了一层 fallback，traits 里可以直接用类别名：

| 类别键 | 覆盖的 stat type |
| --- | --- |
| `tconstruct:melee_harvest` | `head` / `handle` / `binding` |
| `tconstruct:ranged` | `limb` / `grip` / `bowstring` |
| `tconstruct:armor` | `plating_*` / `maille` / `cuirass` / `shield_core` / `slime` / `skull` / `shell` / `laces` |
| `tconstruct:ammo` | `arrow_head` / `arrow_shaft` / `fletching` |

`tconstruct:ribcage` 与 `tconstruct:repair_kit` 没有类别，只能直写部位名。想只影响一个部位（比如只影响
绑定部件）就直接写 `tconstruct:binding`，它比 `melee_harvest` 更窄。

### 这份包里的绑定

`iron.json` 给 `tconstruct:melee_harvest`（头 / 柄 / 绑定）挂了三个词条 —— 第一个是 TiC 原本通过
`default` 给铁的 `tconstruct:magnetic`，**必须一起列出来**，否则会把原词条顶掉：

```json
{
  "perStat": {
    "tconstruct:melee_harvest": [
      { "level": 1, "name": "tconstruct:magnetic" },
      { "level": 2, "name": "thikermagic:charged_core" },
      { "level": 2, "name": "arcana:manaflow" }
    ]
  }
}
```

于是**任何用铁做头 / 柄 / 绑定的工具或武器**（铁剑、铁镐、铁锤……）都自带：

| 词条 | 在工具上表现 |
| --- | --- |
| `tconstruct:magnetic` | 原样保留，铁的近战词条没有变化 |
| `thikermagic:charged_core` | 工具成为 FE 容器（容量 20000），tooltip 显示 `Energy: 0 / 20,000 FE`；充能越高近战伤害与攻速越高，每次命中扣 100 FE |
| `arcana:manaflow` | 手持 / 装备该工具时每 tick +0.5 点魔艺法力（每秒 10 点） |

铁其它部位不受影响：`plating_*` 等护甲件仍走 `armor` 键的 `tconstruct:projectile_protection`，
`skull` 仍走 `tconstruct:plague` + `tconstruct:husk_disguise`，而 `limb` / `grip`（弓臂、握把）在
`ranged` 类别上没有词条，仍旧落到 `default` 的 `tconstruct:magnetic`。

### 想换材料 / 换部位

- **换材料**：把文件改名成目标材料的路径，并把数组里的 `tconstruct:<原词条>` 换成那个材料自己的词条
  （查法：`/tconstruct materials traits <材料>`，看到的就是它的 `default`）。**代价**是这份复制品从此与
  TiC 的定义脱钩 —— TiC 改词条（换等级、加词条）时你得跟着改，忘了就会把新版本的原词条顶掉。所以只有当
  你确实要改这个材料的数值时才这么写。
- **只想影响一个部位**：直接写部位键，比类别更窄。例如只让铁做**绑定**时才带 FE 词条：

```json
{
  "perStat": {
    "tconstruct:binding": [
      { "level": 1, "name": "tconstruct:magnetic" },
      { "level": 2, "name": "thikermagic:charged_core" }
    ]
  }
}
```

- **远程武器**：把键换成 `tconstruct:ranged`（`limb` / `grip` / `bowstring`），弓和弩就会带上词条。
- **只是想加、不想碰原版数值**：新建一个材料，`definition` + `stats` + `traits` 三个文件都放在自己的
  命名空间下，trait 里直接写 `"default": [...]` 即可 —— 新材料的 traits 文件是空的，没有任何原词条要
  照顾。代价是贴图和翻译属于资源包（见「翻译与材质」一节），数据包给不了外观和名字。

### 自查

```
/tconstruct materials traits tconstruct:iron tconstruct:head
```

应当返回 `Magnetic I (tconstruct:magnetic)`、`Charged Core II (thikermagic:charged_core)`、
`Manaflow II (arcana:manaflow)`；把部位换成 `tconstruct:limb` 应当只剩 `Magnetic I`。

**命令要带上部位类型。** 省略部位时 `tconstruct materials traits <材料>` 只打印**默认**词条：铁现在的
`default` 没被改过，所以不带部位类型时只会看到 `Magnetic I`，看不到新加的两个 —— 那不代表绑定没生效。
类别键会被 fallback 展开，`tconstruct:melee_harvest` 在 `head` / `handle` / `binding` 三个部位上都能查到。

对照检查（这两个材料不该有任何变化）：

```
/tconstruct materials traits arcana:crystalsong tconstruct:head   -> Deepwell II (arcana:deepwell)
/tconstruct materials traits tconstruct:feather tconstruct:fletching -> (没有默认词条，也没有 ammo 词条)
```

成品验证：给僵尸一把**镐头是铁、柄和绑定是木头**的镐（这样铁只出在一个部件上），读它的 NBT：

```
summon minecraft:zombie 0 100 0 {NoAI:1b,HandItems:[{id:"tconstruct:pickaxe",count:1,components:
  {"minecraft:custom_data":{tic_materials:["tconstruct:iron","tconstruct:wood","tconstruct:wood"]}}},{}]}
data get entity @e[type=zombie,limit=1] HandItems[0].components."minecraft:custom_data"
```

```
tic_stats     = {..., "tconstruct:max_energy": 20000.0f, ...}
tic_modifiers = [{level: 1, name: "tconstruct:energy_handler"},
                 {level: 1, name: "tconstruct:magnetic"},
                 {level: 2, name: "thikermagic:charged_core"},
                 {level: 2, name: "arcana:manaflow"},
                 {level: 2, name: "tconstruct:cultivated"},
                 {level: 1, name: "tconstruct:pierce"}]
```

`max_energy` 出现、`charged_core` / `manaflow` 出现、而铁的 `magnetic` 与木头的 `cultivated` 也都在，
说明材料 -> trait -> 容量这条链整条通了，且没有顶掉任何一个原词条。

**词条等级按部件累加。** 三个部件都用铁的话，`charged_core` 是 2+2+2 = **6 级**、容量 60000，
`magnetic` 也会变成 3 级 —— 这是 TiC 材料 trait 的通用行为（`arcana` 自己的材料同理），
不受配方等级上限约束。示例文件里写 2 级，只是"一个铁部件 = 20000 容量"的意思。

## 翻译与材质

颜色、翻译、词条图标和**材质贴图**四样都来自**资源包**（数据包不认这些）。本仓库提供了两份：

- **已经在模组里**：`src/main/resources/assets/arcana/` 与 `assets/tconstruct/` —— 打包进
  ThikerMagic 的 jar，由 `mod/thikermagic` 自动提供，装模组就有中文、图标和材质颜色，什么都不用配。
- **独立资源包**：`resourcepacks/arcana-resources/`（以及打好的 zip，约 320 KB），给不想装模组、
  只想引用这些 id 的整合包用。放进 `resourcepacks/` 后在游戏里手动启用。
  注意它往 `assets/tconstruct/` 里放了部件与装甲贴图 —— 这是 TiC 规定的材质贴图位置，
  addon 都得往这个命名空间写，不是写错了。

内容：

| 文件 | 作用 |
| --- | --- |
| `assets/arcana/lang/zh_cn.json` | 材料名、词条名、flavor、description、tooltip、II/III 级 |
| `assets/arcana/mantle/colors.json` | 材料名与**词条名**的颜色（工具 tooltip、材料册、词条 tooltip 的数值行） |
| `assets/arcana/tinkering/materials/*.json` | 材质渲染定义：`color` 兜底色、`fallbacks` 兜底基底、`generator` 调色板 |
| `assets/arcana/tinkering/modifier_icons.json` | 词条图标，引用原版物品贴图 |
| `assets/tconstruct/textures/item/tool/**` | 三个材质的**部件贴图**（178 个部件 × 3 材质 = 534 张） |
| `assets/tconstruct/textures/tinker_armor/**` | 三个材质的**装甲贴图**（plate / slime / travelers / 鱼钩） |

部件数不是拍脑袋定的：TiC 自己的 `tinkering/generator_part_textures.json` 列了 261 个部件，
按材料实际声明了哪些 stat 过滤之后，这三个材料各有 178 个。对照 TiC 本体，铁质贴图有 186 张
（铁的 `supported_stats` 多出 `repair_kit` / `armor_plating` / `armor_maille`），量级对得上。

独立资源包那份 zip 约 320 KB，其中绝大部分就是这些 png；不装模组也能拿到完整外观。

### 材质颜色：三层数据，改哪一层影响哪一层

TiC 渲染一个材质时依次看三样东西，缺一层就往下掉一层：

| 层 | 位置 | 游戏里读不读 |
| --- | --- | --- |
| ① 调色板 | `materials/*.json` 的 `generator.transformer.color_mapping.palette` | **不读**。只给生成器用（TiC 的 `/tconstruct generate_part_textures` 命令、datagen） |
| ② 成品贴图 | `assets/tconstruct/textures/**/<部件>_arcana_<材料>.png` | 读。找到就**直接用，不染色** |
| ③ 兜底 | `materials/*.json` 的 `color` + `fallbacks` | 读。② 不存在时，用 `fallbacks` 命中的基底贴图乘以 `color` |

几个容易踩的点：

- **`generator` 是给生成器看的，不进游戏。** `MaterialRenderInfo` 的 loadable 只解析
  `texture` / `fallbacks` / `color` / `luminosity`，`generator` 字段在客户端加载时被直接忽略。
  所以只改 `generator` 而不重新出图，游戏里的颜色**一点都不会变**。
- **`fallbacks: ["metal"]` 是个命名约定，不是文件。** TiC 的 `textures/item/tool/**` 里
  **没有** `head_metal.png` 这类文件 —— `metal` / `rock` / `bone` 这些名字是生成器挑基底时用的
  标签（`rock` 有 `head_rock.png`，`metal` 没有）。四个材质的 `metal` 拿不到文件，
  最终会落到基础贴图 `head.png`（一张只有 63/102/178/216/255 五个灰阶的**索引图**）。
- **调色板的灰阶锚点就是这些索引值。** `palette` 用 `grey` 0/63/102/140/178/216/255 七档，
  216 档放主色，255 档放提亮色。索引图上的每个灰阶被映射成对应颜色 —— 这和"基底 × 顶点色"
  的乘法染色结果接近，但暗部和亮部更准，也与 TiC 本体材质（iron、cobalt 等）的观感一致。

### 重新生成材质贴图

`tools/generate_material_textures.ps1`：

```powershell
# 只统计，不写文件
powershell -ExecutionPolicy Bypass -File tools/generate_material_textures.ps1 -DryRun
# 实际生成，同时写入模组资源和独立资源包两处
powershell -ExecutionPolicy Bypass -File tools/generate_material_textures.ps1 -Apply
```

脚本的部件清单来自**三个来源的并集**：

1. TiC 自己的 `tinkering/generator_part_textures.json` —— 它的贴图生成器走的就是这张表，
   共 261 个部件，每个带 `stat_type`。**这是权威清单**，破损态（`head_broken`）、弓的拉弦态
   （`limb_top_1/2/3`、`bowstring_1/2/3`）、部件物品（`parts/large_plate`）都在这里，
   而它们**不会出现在签入的模型 JSON 里**。
2. `models/item/**/*.json` 的 `textures` 块引用 —— 兜底，覆盖清单之外的情况。
   （模型路径引用如 `tconstruct:item/tool/pickaxe/blocking` 不算，那是另一个模型文件。）
3. `textures/tinker_armor/` 下的中性装甲层。

**TiC 的资源要从 build 目录读，不能只读 src。** 它的 `models/item` 里有一大半是 datagen
产物，只存在于 `build/resources/main`：src 有 721 个模型 JSON，build 有 1551 个。
只读 src 会静默漏掉整族部件 —— 这正是「疾行钢材质颜色不对」的成因，见下一节。

两跳来源的处理方式不同，因为 TiC 自己就区别对待：

- **清单内的部件一律染色，不管源图是不是灰阶。** TiC 的 `GreyToColorMapping` 本来就是为彩色
  输入写的：取最大通道当灰阶索引，另外两个通道按比例缩放。弓臂、护目镜、熔炼锅手柄都是彩色
  源图，TiC 照样给它们出材质贴图。
- **只靠模型引用摸到的部件，必须是不透明像素全灰阶**才算调色板蒙版。那些是固定外观的贴图
  （法杖水晶、洗浴器的罐、打火石），TiC 根本没有它们的材质变体，染色只会把本该不变的美术改掉。

带 `stat_type` 的部件只对该材料真正声明过的 stat 生成，对应 TiC 的 `supportStatType` 过滤：
TiC 会给铁质弓臂出图，但不会给铁质弓弦出图（铁不声明 `bowstring`）。

调色板和 `supported_stats` 直接从 `assets/arcana/tinkering/materials/*.json` 读，所以贴图
和材料定义不可能跑偏。按每个材质的调色板逐像素映射，输出 `<部件>_arcana_<材料>.png`，
尺寸跟随源图（`large/` 下是 32×32，其余 16×16）。

调色板改了、或者 TiC 更新了基础部件贴图，重跑一次 `-Apply` 即可；**改完记得重新打包 zip**。

### 坑：漏一个部件，颜色就会「不对」

`MaterialRenderInfo.getSprite` 只有两条路：

```java
if (texture != null) { /* 找到 <部件>_arcana_<材料>.png → 直接用，不染色 */ }
for (String fallback : fallbacks) { /* 否则找 metal / rock… 贴上顶点色 */ }
```

`fallbacks: ["metal"]` 指向的 `head_metal.png` 在 TiC 里**不存在**，所以缺图时最终落到基础贴图
（只有 63/102/178/216/255 五个灰阶的**索引图**）再乘以 `color`。**乘法染色和调色板映射在亮部
差得最远**：同样的 255 档像素，调色板给 `#CCEFF6`，乘法只给 `#7FD8E8` —— 高光直接不见了，
整体发闷发暗。

所以「颜色不对」几乎总是**部件漏了**，而不是颜色值写错。判断办法：拿部件名去比对

```powershell
# 本材料的贴图应该和 TiC 本体同 stat 的材料一个量级
(Get-ChildItem -Recurse -File <TinkersConstruct>\build\resources\main\assets\tconstruct\textures -Filter '*_tconstruct_iron.png').Count
(Get-ChildItem -Recurse -File src\main\resources\assets\tconstruct\textures -Filter '*_arcana_quicksteel.png').Count
```

脚本 DryRun 的 `source not grey` 应该只剩 off-list 的固定外观（69 个左右）；如果清单内的部件
出现在这个列表里，说明判定被写反了。`parts considered` 应该是 331 左右（261 清单 + 模型与装甲
扫描的补充），掉到 160 上下就是只读了一个资源根。

颜色文件的键必须写**完整的 `命名空间.路径`**：材料写 `material.arcana.crystalsong`，词条写
`modifier.arcana.swiftcast`。Mantle 读的是 `assets/<命名空间>/mantle/colors.json`，键要和翻译键逐字
一致 —— 少了命名空间（比如只写 `crystalsong`）不会报错，只是永远查不到，名字保持默认白。
ThikerMagic 自带的那五个词条颜色在同名的 `assets/thikermagic/mantle/colors.json`。

| 词条 / 材料 | 颜色 |
| --- | --- |
| `arcana:manaflow` / `thikermagic:mana_siphon` | `#4FD1A5` 翠绿（回蓝） |
| `thikermagic:arcane_attunement` / `arcana:crystalsong` | `#B98CFF` 淡紫（上限比例） |
| `arcana:swiftcast` / `thikermagic:spell_haste` / `arcana:quicksteel` | `#7FD8E8` 冰蓝（冷却） |
| `thikermagic:mana_reservoir` | `#2E9BFF` 亮蓝（上限 + 回蓝） |
| `thikermagic:arcane_surge` | `#FF7B4F` 橙（爆发） |
| `arcana:deepwell` | `#5B8CFF` 深蓝（上限） |
| `arcana:manawell` / `arcana:starstone` | `#FFC95C` 金（持续回蓝） |

几种翻译键的写法，缺哪个就显示哪个的原始键名：

```json
{
  "material.arcana.crystalsong": "晶歌石",
  "material.arcana.crystalsong.flavor": "石头在唱歌，唱的是坐标。",
  "material.arcana.crystalsong.encyclopedia": "材料册里的长说明。",
  "modifier.arcana.swiftcast": "疾咏",
  "modifier.arcana.swiftcast.flavor": "咒语比念头先落地。",
  "modifier.arcana.swiftcast.description": "词条详情，悬停时显示。",
  "modifier.arcana.swiftcast.tooltip": "一句话短提示。",
  "modifier.arcana.swiftcast.2": "疾咏 II",
  "modifier.arcana.swiftcast.3": "疾咏 III"
}
```

**encyclopedia 和 description 用「人话」，不是参数表。** 数值必须和词条 JSON 对得上，
但说法要像人在解释：说「穿着或握着的时候，法力上限每级多出 10%」，而不是
「装备或手持时，每级提升 10% 的法艺最大法力上限」。tooltip 也一样，统一用**每秒**而不是
每 tick —— 属性内部是每 tick 的点数（0.25/tick = 每秒 5 点），但玩家感知的是每秒。

**资源包没生效的时候，tooltip 会直接显示这些键名本身**（`material.arcana.crystalsong`、
`modifier.arcana.swiftcast.description` 等等），功能不受影响但看着很糟。确认方法：看
`logs/latest.log` 里 `Reloading ResourceManager` 那一行的列表，有没有 `mod/thikermagic`
或者 `file/arcana-resources.zip`。

## 坑：不要在数据包函数里 add 数据包词条

`tconstruct modifiers <target> add <modifier>` 这一行写在 `.mcfunction` 里会让**整个函数加载失败**：

```
Failed to load function ...
Could not parse command: TranslatableContents' arguments must be either a Component,
Number, Boolean, or a String. Was given arcana:manaflow for command.tconstruct.modifier.not_found
```

原因有两个叠在一起：

1. `ModifierArgument` 在**函数解析期**就去查词条，而函数解析发生在 `ModifierManager#apply` **之前**，
   所以同一批数据包里新加的词条还不在注册表里。
2. Tinkers 抛的 `command.tconstruct.modifier.not_found` 把词条 id 直接当成翻译参数传了进去，
   1.21 的 `TranslatableContents` 不接受这种类型，于是异常在解析期就炸了，函数整体作废。

解决办法：运行期用 rcon / 控制台执行这条命令，或者用 `tic_upgrades` 直接写 NBT ——
后者在数据包函数里是安全的。

## 游戏内自查：`/thikermagic mana`

ThikerMagic 注册了一条调试命令（短别名 `/tmana`）。在游戏里执行 `/thikermagic mana`，会同时打印
词条侧和魔艺侧的数字：

```
词条属性 max_mana +20% · flat_max_mana +0 · mana_regen +0.5/tick · 冷却 -16% · 穿戴回蓝 +40
魔艺上限 法力 60 / 120（可用 120，保留 0%）· 无词条时 100 · 词条净增 +20
魔艺回蓝 15/秒（裸装 5 + 词条 10）· 词条折算每 tick 0.5
```

判读方法：

- **深井（`max_mana`）有没有生效**：第二行的「无词条时」和当前上限不相等，就说明倍率走到了 Ars
  的 `MaxManaCalcEvent`。两边相等、且第一行是 `max_mana +0%`，说明数据包没加载 —— 常见原因是
  存档里的 zip 还是旧版本（见最后一节），换掉 `arcana.zip` 再进世界即可。
- **`mana_regen` 有没有生效**：看第一行的 `+0.5/tick` 与第三行的「词条 10/秒」，两者是 20 倍关系；
  「裸装」那一项完全不受词条影响。穿脱带词条的装备，三行数字应当立刻跟着变。
- **`manawell`**：第三行的「词条」那一项按每级 0.5/tick 计，法力条每秒往上走；数字不动就是装备
  没真正穿上（护甲槽/手持才算装备），或者身上那件还挂着旧 zip 定义。
- 不依赖本模组的对照命令：`/attribute @s thikermagic:max_mana get`、`/attribute @s thikermagic:mana_regen get`。

数字没动就先查工具：`tconstruct modifiers <工具> list`，看词条在不在 `tic_modifiers` 里。

## 验证记录

2026-09-25，ThikerMagic dev 服务端（Tinkers' Construct 3.12.8 / NeoForge 21.1.241 / Ars Nouveau 5.13.1）

| 检查项 | 基线 | 装包后 |
| --- | --- | --- |
| materials | 83 | **86** |
| material stats | 556 / 99 | **592 / 102** |
| material traits | 597 / 99 | **600 / 102** |
| dynamic modifiers | 295 | **299** |
| recipes | 4030 | **4036** |

零 `Failed to load modifier`、零配方解析错误、零材料/词条警告。

上面是四个材料（当时还含 `arcana:manawoven`）+ `manawell` 词条那一版跑出来的数字。移除
`arcana:manawoven` 之后计数会同步变小（少 1 个材料、12 条 stats、1 条 traits、1 条配方）；
这一版**没有重跑服务端回归** —— 删材料只会让计数变小，不会引入新的解析错误。

2026-10-03（晚），示例绑定从 `feather` 改挂 `tconstruct:iron`（同一环境，TiC 3.12.8 / NeoForge 21.1.241）：

| 检查项 | 基线 | 装包后 |
| --- | --- | --- |
| material traits（`MaterialTraitsManager` 那行） | 600 / 102 | **604 / 102** |
| dynamic modifiers | 302 | 302 |
| recipes | 4043 | 4043 |

和 feather 那版同为 +4，原因一样：`perStat` 里写的类别键本身算一个条目，再被 fallback 展开成具体部位 ——
`tconstruct:melee_harvest` 展开成 `melee_harvest` / `head` / `handle` / `binding` 四个键。（`tconstruct:ammo`
那版是 `ammo` / `arrow_head` / `arrow_shaft` / `fletching`，也是四个。）材料数不变。

rcon 实测：

```
tconstruct materials traits tconstruct:iron tconstruct:head
  -> Head traits for Iron:
     * Magnetic I (tconstruct:magnetic)        # TiC 原有，未被顶掉
     * Charged Core II (thikermagic:charged_core)
     * Manaflow II (arcana:manaflow)
tconstruct materials traits tconstruct:iron tconstruct:handle     # 同上三项
tconstruct materials traits tconstruct:iron tconstruct:binding    # 同上三项
tconstruct materials traits tconstruct:iron tconstruct:limb
  -> * Magnetic I (tconstruct:magnetic)        # 弓臂，未受影响
tconstruct materials traits tconstruct:iron
  -> * Magnetic I (tconstruct:magnetic)        # default 没被改
tconstruct materials traits tconstruct:iron tconstruct:plating_chestplate
  -> * Projectile Protection I (tconstruct:projectile_protection)   # 护甲件，未受影响
tconstruct materials traits tconstruct:feather tconstruct:fletching
  -> * No traits                               # 上一版 feather 绑定已撤回
tconstruct materials traits arcana:crystalsong tconstruct:head
  -> * Deepwell II (arcana:deepwell)           # 对照，未变
```

成品验证 —— 镐头是铁、柄与绑定是木头（`tic_materials:["tconstruct:iron","tconstruct:wood","tconstruct:wood"]`）：

```
tic_stats     = {"tconstruct:attack_speed": 1.2f, "tconstruct:attack_damage": 3.0f,
                 "tconstruct:durability": 250.0f, "tconstruct:max_energy": 20000.0f,
                 "tconstruct:mining_speed": 6.0f, "tconstruct:harvest_tier": "minecraft:iron"}
tic_modifiers = [{level: 1, name: "tconstruct:energy_handler"},
                 {level: 1, name: "tconstruct:magnetic"},
                 {level: 2, name: "thikermagic:charged_core"},
                 {level: 2, name: "arcana:manaflow"},
                 {level: 2, name: "tconstruct:cultivated"},
                 {level: 1, name: "tconstruct:pierce"}]
```

同一把镐换成三个部件全铁（`tic_materials` 全 `tconstruct:iron`）：

```
tic_stats     = {... "tconstruct:max_energy": 60000.0f ...}
tic_modifiers = [{level: 1, name: "tconstruct:energy_handler"},
                 {level: 3, name: "tconstruct:magnetic"},
                 {level: 6, name: "thikermagic:charged_core"},
                 {level: 6, name: "arcana:manaflow"},
                 {level: 1, name: "tconstruct:pierce"}]
```

即 trait 等级按部件累加：一个铁部件 = `charged_core` 2 级 = 20000 容量，三个部件 = 6 级 = 60000；
`magnetic` 也从 I 变成 III。这是 TiC 材料 trait 的通用规则，不是本包的额外行为。

2026-10-03（早），示例绑定曾挂在 `tconstruct:feather` 的 `tconstruct:ammo` 上（**已被上面那版取代**）：

| 检查项 | 基线 | 装包后 |
| --- | --- | --- |
| material traits | 600 / 102 | **604 / 102** |
| dynamic modifiers | 302 | 302 |
| recipes | 4043 | 4043 |

当时 `tconstruct materials traits tconstruct:feather tconstruct:fletching` 返回 `Charged Core II` +
`Manaflow II`，一支 flint / wood / feather 的箭读回 `max_energy: 20000.0f` 与两个词条。
问题在于**箭矢不是工具武器**：`feather` 只能做箭羽，测试范围也证明它拿不到近战/远程部件，
所以这一版把绑定挪到铁上，feather 那份文件已删除（`run/` 里的副本也一并清掉）。

2026-09-26，材质贴图重建（同一环境，DryRun 数字）：

| 检查项 | 旧 | 新 |
| --- | --- | --- |
| 部件清单（`parts considered`） | 161（只读 src 的模型扫描） | **331**（261 官方清单 + 模型/装甲扫描补充） |
| 每个材质生成的部件 | 149 | **178** |
| 贴图总数（每处） | 596（4 材质） | **534**（3 材质 × 178） |
| 与 TiC 本体对照仍缺的部件 | 45 | **0** |

映射算法用 TiC 自己的产物反查过：拿 TiC 的源图 + `iron` / `cobalt` / `slimesteel` /
`hepatizon` / `manyullyn` 的调色板重跑一遍，与 TiC 签入的 `<部件>_tconstruct_<材料>.png`
逐像素比对。8 个用例里 6 个**完全一致**（含彩色源的 `melting_pan/handle`），另两个
（`longbow/limb_top_1`、`limb_bottom_2`）各有 2 个像素不同（216↔255、168↔216）——
那是 TiC 自己的 build 产物与源素材版本不同步，与映射算法无关。

`source not grey` 的 69 条全部是清单外的固定外观：法杖水晶（`staff/earth|sky|ichor|ender`
各自普通/大型/破损共 20 个）、洗浴器的罐、打火石、护目镜底、`parts/plating_*`。
TiC 本体也没有这些部件的材质变体。

客户端那次加载（YG 自己的存档，`arcana.zip`）确认了 83 -> 86 materials、295 -> 299 dynamic
modifiers，和上表一致。

运行期（rcon）：

```
attribute @e[tag=probe,limit=1] thikermagic:mana_regen get
  -> Entity Zombie has no attribute Mana Regeneration
```

三个属性都解析出了显示名，说明注册成功；僵尸身上没有该属性是预期行为 ——
`ThikerMagicAttributes` 只把属性挂到 `EntityType.PLAYER`。

```
tconstruct modifiers @e[tag=probe,limit=1] add arcana:manaflow 3
  -> Applied modifier modifier.arcana.manaflow III to Zombie's item
```

三个词条都能通过 `ToolStack.addModifier` + `tryValidate` 挂到工具上，落进 `tic_upgrades`。

材料 trait 合并（工具材料 `[crystalsong, manawoven, manawoven]`，跑这段时 `arcana:manawoven`
还在；换成 `[crystalsong, starstone, starstone]` 时 `deepwell` 只有 crystalsong 给的 2 级，
`manawell` 会多出 starstone 的 2 级）：

```
tic_modifiers = [
  {level: 7, name: "arcana:manaflow"},    // 命令 3 + manawoven x2 (2 级各一次)
  {level: 5, name: "arcana:deepwell"},    // 命令 3 + crystalsong (2 级)
  {level: 3, name: "arcana:swiftcast"},   // 命令 3
  {level: 3, name: "thikermagic:mana_siphon"},
  {level: 1, name: "tconstruct:pierce"}
]
```

材料数值也按 stats 文件算对了（crystalsong 头 + manawoven 手柄：耐久 700 x 1.05 = 735，
挖掘 6.0 x 1.05 = 6.3，采掘等级 diamond）。

唯一没自动化验证的是**玩家身上的实际数值变化** —— 属性只注册到 PLAYER，无头服务端造不出玩家。
需要进游戏确认：`/attribute @s thikermagic:mana_regen get` 应该返回 0，
装上带词条的工具后变成 0.3（manaflow 2 级）之类的值。

### 坑：改完定义必须重新打包 zip

数据包改过 JSON 之后，**必须重新生成 `arcana.zip`**，否则存档里跑的还是旧定义。

2026-09-26 那次「穿上/脱下装备魔力就只剩一半」就是这么来的：`manawell` 的源文件已经改成
`mana_on_equip`（一次性回蓝），但打包好的 `arcana.zip` 里还是旧定义 `thikermagic:flat_max_mana`
（每级 +30 上限），而游戏加载的是 zip —— 于是星辉石装备每次穿脱都在抬/压上限，
条只剩一截、脱下被砍掉一截。判别方法：解压存档里的 zip 看
`data/arcana/tinkering/modifiers/manawell.json` 指向哪个属性。

同一类问题的另一半是打包方式本身：用 `Compress-Archive` 打出来的 zip 条目名带回斜杠，
Minecraft 读不到任何文件，日志里静默无错 —— 症状是**所有**词条同时失效。见上面「安装」一节。

仓库里的 `datapacks/arcana/arcana.zip`（以及同内容的 `datapacks/arcana.zip`）已经重新打过：条目名
全是正斜杠，不再把旧 zip 自身和 `META-INF` 打进去，`manawell` 也是现在的 `mana_regen` 定义。
存档里那一份要手动覆盖，游戏下次加载世界时才读得到。

### 坑：`run/` 下的副本不会跟着仓库走

开发目录里游戏实际读的是这三份，**它们都是独立拷贝，改仓库不会同步**：

| 游戏读的位置 | 内容 |
| --- | --- |
| `run/resourcepacks/arcana-resources.zip` | 独立资源包（翻译、颜色、图标、材质贴图） |
| `run/world/datapacks/arcana/` | 世界数据包目录版（含一份 `arcana.zip`） |
| `run/saves/<存档>/datapacks/arcana.zip` | 单人存档数据包 |

dev 客户端还有第三份：**模组自己的资源读 `build/resources/main/`，不是 `src/main/resources/`**。
`runClient` 会先跑 `processResources` 把它刷出来，但如果你改了 `src` 里的贴图或语言又直接
复用已有的 run 目录，游戏读到的还是旧的那份：

```powershell
$env:JAVA_HOME='D:\code\mcmod\.toolchains\jdk-21\jdk-21.0.11+10'
$env:GRADLE_USER_HOME='D:\code\mcmod\.gradle-home'
.\gradlew.bat processResources
```

改完 `datapacks/arcana/`、`resourcepacks/arcana-resources/` 或重新生成贴图之后，
要一起重打并复制过去：

```powershell
$root = "D:\code\mcmod\thikermagic"
tar -a -c -f "$root\resourcepacks\arcana-resources.zip" -C "$root\resourcepacks\arcana-resources" pack.mcmeta assets
Copy-Item "$root\resourcepacks\arcana-resources.zip" "$root\run\resourcepacks\" -Force
tar -a -c -f "$root\datapacks\arcana.zip" -C "$root\datapacks\arcana" pack.mcmeta README.md data
Copy-Item "$root\datapacks\arcana.zip" "$root\run\saves\<存档>\datapacks\" -Force
Copy-Item "$root\datapacks\arcana.zip" "$root\run\world\datapacks\arcana\arcana.zip" -Force
```

**颜色键少写命名空间就是这样漏掉的**：仓库里的
`resourcepacks/arcana-resources/assets/arcana/mantle/colors.json` 早就写成
`material.arcana.manawoven` 了，但游戏加载的 `run/resourcepacks/arcana-resources.zip` 里
还是旧的 `material.manawoven`（外加整段 `modifier` 缺失）。Mantle 查不到这个键不会报错，
只是材料名和词条名一律保持白色 —— 症状是"其它都对，就是名字没颜色"。
排查方式：`tar -xOf run/resourcepacks/arcana-resources.zip assets/arcana/mantle/colors.json`
直接看游戏在读什么，而不是看仓库里那份。

