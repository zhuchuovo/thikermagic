# Thiker Magic

Turns Ars Nouveau's mana and casting into Tinkers' Construct materials. Build a tool or a
piece of armour out of Ars Nouveau stock and it arrives with the matching trait: carry it
or wear it and your mana ceiling, your mana regen and your cast cooldown all move with it.

Every modifier ships as plain JSON under `data/thikermagic/tinkering/modifiers/`, so other
packs can reference the same ids, re-point them with their own recipes, or hang them off
their own tools.

## Attributes

Five attributes are registered and attached to players. Datapacks reference them with the
`tconstruct:attribute` module, so any modifier — from this mod or any other — can feed them.

| Attribute | Default | Meaning |
| --- | --- | --- |
| `thikermagic:mana_regen` | `0` | Percentage bonus to Ars Nouveau mana regeneration. `0.25` = +25% |
| `thikermagic:max_mana` | `0` | Percentage bonus to Ars Nouveau max mana. `0.25` = +25% |
| `thikermagic:flat_max_mana` | `0` | Flat bonus in mana points, applied before the percentage, so the two compose as `(base + flat) * (1 + percent)`. `30` = +30 mana |
| `thikermagic:mana_on_equip` | `0` | One-off payout of mana points when gear is equipped, not a continuous bonus. `20` = +20 mana |
| `thikermagic:spell_cooldown_reduction` | `0` | Fraction of the cast cooldown removed. `0.4` = 60% as long |

## Modifiers

| Id | Type | Levels | Effect per level |
| --- | --- | --- | --- |
| `thikermagic:mana_siphon` | Upgrade | 3 | +15% mana regeneration |
| `thikermagic:spell_haste` | Upgrade | 3 | +8% cast cooldown reduction |
| `thikermagic:arcane_attunement` | Upgrade | 3 | +10% max mana |
| `thikermagic:mana_reservoir` | Ability | 1 | +40% max mana, +25% mana regeneration |
| `thikermagic:arcane_surge` | Ability | 1 | +30% cooldown reduction, +30% mana regeneration |

All five are collected in the modifier tag `thikermagic:arcane`.

## Forge Energy traits

`thikermagic:charged_core` (upgrade, levels 1-3, tag `thikermagic:energy`) is not an Ars Nouveau
trait — it is the Forge Energy one. Tinkers' Construct already ships the storage side of this:
`ToolEnergyCapability` keeps the energy in the tool's persistent data under `tconstruct:energy`, the
capacity is the tool stat `tconstruct:max_energy`, and `ToolCapabilityProvider` exposes every tool
with a non-zero capacity as an `IEnergyStorage`. The trait only supplies the numbers:

- capacity: `tconstruct:stat_boost` on `tconstruct:max_energy`, **10000 per level**
- `tconstruct:trait` pointing at `tconstruct:energy_handler`, which clamps the stored energy to the
  capacity on tool change and prints `Energy: current / max FE` on the tooltip
- `thikermagic:charged_attack`, the module in `com.yg.thikermagic.energy`

At a charge of `energy / capacity` the module multiplies melee damage by `1 + 0.2 * charge`, and
`EnergyAttack` keeps `0.5 * charge` on the player's `minecraft:generic.attack_speed` as a transient
attribute modifier, refreshed once per tick from whichever hand holds the better charged tool. Every
landed melee hit drains 100 FE (`addEnergy` clamps at zero, so an empty tool is still usable, just
unbuffed).

Attack speed deliberately does not go through Tinkers' `tconstruct:attribute` module: those item
attribute modifiers are built when the tool is equipped and the hook documents that the list must
not change between equipping and unequipping, while energy changes every attack.

The three module fields are datapack fields (`damage_bonus`, `attack_speed_bonus`,
`energy_per_attack`), so a pack can retune them or hang the module on a trait of its own:

```json
{
  "type": "thikermagic:charged_attack",
  "damage_bonus": 0.2,
  "attack_speed_bonus": 0.5,
  "energy_per_attack": 100
}
```

The arcana datapack (`datapacks/arcana/`) carries a worked example of the other direction: it hangs
this trait, plus `arcana:manaflow`, on `tconstruct:iron` so every iron-headed tool or weapon comes
with the FE trait, by adding `data/tconstruct/tinkering/materials/traits/iron.json` to the pack.
Because Tinkers' ships a trait on every material that can make a tool part (iron's is
`tconstruct:magnetic`, from its `default` list), the file re-lists that trait alongside ours —
`perStat` keys replace, they do not append. Copy that file to make a different material hand out a
charged tool; the pack README covers the category keys, the merge rules, how to narrow the binding
to a single part, and why `materials traits` needs a stat type to show any of it.

### Filling a tool

The tool is a plain `IEnergyStorage`, so **anything that charges items works out of the box** —
a tech mod's charger, battery, or player charger all fill it, and nothing here has to be involved.

For packs with no tech mod, `EnergyCharging` adds a vanilla sink: hold the tool and a charging item
in the other hand and **sneak + right click**. Default values, all editable in
`config/thikermagic-common.toml` under `[energy_charging]`:

| Item | Energy |
| --- | --- |
| `minecraft:redstone` | 1000 FE |
| `minecraft:redstone_block` | 9000 FE |
| `minecraft:glowstone_dust` | 2000 FE |
| `minecraft:amethyst_shard` | 4000 FE |

`items` takes `"<item id>=<fe>"` lines, so a pack can point it at its own batteries. The item is
only spent for what actually fits (a nearly full tool does not burn a whole block), a full tool
reports itself instead of eating the item, and `requiresSneak = false` makes a plain right click
charge — it defaults to `true` so the tool's own right click behaviour is never stolen.

### Combat path, measured

Dev server, a zombie swinging a full-iron sword (three iron parts → `charged_core` 6, capacity
60000) at a cow, damage applied with `/damage <cow> 4 minecraft:mob_attack by <zombie>`:

| Tool energy | Damage | Cow | Energy after |
| --- | --- | --- | --- |
| `{}` | 4.0 | 10.0 → 6.0 | `{}` |
| `60000` | 4.8 = 4 × 1.2 | 6.0 → 5.2 | `59900` |

That is `MONSTER_MELEE_DAMAGE` (+20% at full charge) and `MONSTER_MELEE_HIT` (100 FE per hit) end to
end. The player path runs the same two methods through `MELEE_DAMAGE` / `afterMeleeHit`, so it needs
no separate numbers; the attack speed half is a player attribute and still wants a real client to
watch.

## How the hook works

Ars Nouveau exposes three public events that this mod listens to:

- `ManaRegenCalcEvent` — fires once per regen tick in `ManaUtil#getManaRegen`. The attribute
  scales the already-computed value.
- `MaxManaCalcEvent` — fires after gear, glyph and book-tier math in `ManaUtil#calcMaxMana`.
- `SpellCastEvent` — fires before a cast resolves and is cancellable.

Two NeoForge hooks round this out: `LivingEquipmentChangeEvent` notices a gear swap, and the end
of the server tick pays the `thikermagic:mana_on_equip` payout it queued (one payout per player
per 20 ticks, so swapping back and forth cannot farm mana).

### Keeping the mana bar's ratio

Ars Nouveau recomputes max mana **every tick** and clamps current mana to the new cap on every
write. Because every max-mana bonus this mod feeds in lives on a piece of Tinkers' gear, simply
putting a tool away or taking off a piece of armor moves the cap: the bar loses its fill ratio
when the cap grows, and the mana above the new cap is destroyed when it shrinks.

`[max_mana] keepManaRatio` (default `true`, `config/thikermagic-common.toml`) scales current mana
by the same factor the cap moved by, so a full bar stays full through a gear swap. Set it to
`false` to let Ars Nouveau clamp current mana to the new cap instead.

The rescale runs at the end of the server tick. Ars Nouveau refreshes the cap during
`PlayerTickEvent.Pre` of the tick *after* the gear change and only clamps on the regen tick after
that, so the new cap is already in place by the time the ratio is restored.

Ars Nouveau has **no native cast cooldown**, so this mod supplies one: after a successful cast
the casting tool goes on cooldown for `castCooldownTicks` (default `10`, configurable in
`config/thikermagic-common.toml`), and `thikermagic:spell_cooldown_reduction` shortens it.
Set `cooldownTicks = 0` to leave Ars Nouveau casting completely untouched — the other two
attributes keep working either way.

Ars Nouveau is an **optional** dependency. Without it the attributes still register and
modifiers still apply, they simply have nothing to act on.

## Writing your own modifier

`data/<your_pack>/tinkering/modifiers/arcane_focus.json`:

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

## Merged packs

The standalone `Ticglossary` mod was folded into this one, so a single jar now carries both. Its two
modifiers live under this namespace and nothing else about them changed:

| Id | Type | Source | Effect |
| --- | --- | --- | --- |
| `thikermagic:lifesteal` | Upgrade, max 5 | `tconstruct:lifesteal` module | Attacks restore 5% of dealt damage per level and use 1 durability |
| `thikermagic:creative_flight` | Ability | `neoforge:creative_flight` attribute | Chestplates grant creative-style flight |

Their recipes, salvage recipes, translations, modifier icons and the entries that put them in
Tinkers' book tags (`tconstruct:modifiers/upgrades/general`,
`tconstruct:modifiers/abilities/general`) all came along. The icons also moved to the path Tinkers'
actually reads, `assets/<namespace>/tinkering/modifier_icons.json` — the old mod shipped them as
`tinkering/modifiers.json`, which `ModifierIconManager` never loads, so they had never shown up.

`../Ticglossary1.21.1` is kept as the pre-merge snapshot. Do not ship it next to this mod: its
`ticglossary:*` ids are duplicates of the modifiers above.

## Building

```powershell
$env:JAVA_HOME='D:\code\mcmod\.toolchains\jdk-21\jdk-21.0.11+10'
$env:GRADLE_USER_HOME='D:\code\mcmod\.gradle-home'
.\gradlew.bat build
```

The output jar lands in `build/libs/`. Tinkers' Construct, Mantle and Ars Nouveau are consumed
from sibling checkouts / the project folder as compile-only dependencies, so they are never
bundled into the jar.
