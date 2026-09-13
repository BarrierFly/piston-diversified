# Piston Diversified / 活塞多样化

A Fabric mod that adds piston variants. **Mod v1** ships the first 19 pistons of the plan; the remaining 11 (potato, pickaxe, recursive ×2, corner-push ×2, wall-parallel ×2, 0t-scheduled-tick ×2, gravity) are planned for mod v2.

一个添加活塞变种的 Fabric 模组。**mod v1** 包含规划中的前 19 种活塞；其余 11 种（马铃薯、镐、递推×2、拐推×2、墙并×2、0t计划刻×2、重力）计划放在 mod v2。

## Piston list / 活塞列表 (v1)

| Piston | 活塞 | Notes / 说明 |
| --- | --- | --- |
| Honey Piston | 蜂蜜活塞 | Sticky; pulls the second cell back after a 0-tick instant retract (no block displacement). 瞬推时第2格可拉回，不丢方块。 |
| Projectile Piston | 抛射活塞 | Launches the front block as a falling block (af2022 dispenser motion); impact & scrape on collision. 抛射前方方块，撞击/刮动原创机制。 |
| Chain Piston / Sticky | 连锁型活塞/黏塞 | Counts as powered while a piston head points at it (cyan dye to convert, water bottle to revert). 活塞头指向即有信号。 |
| Loop Piston | 循环型活塞 | Self-oscillates while powered (purple dye conversion). 有信号时反复伸缩。 |
| Wind Charge Piston | 风弹活塞 | Pushes nothing; applies the wind charge trigger interaction when extended (1.20.5+; crafted with a wind charge). 推出后对前方执行风弹交互。 |
| Silent Piston | 静音活塞 | No sounds, no game events (wool crafting). 无声音、不触发幽匿感测体。 |
| Recoil Piston | 后坐活塞 | With a blocked front, the base recoils backwards instead. 前方有方块时底座后移。 |
| Piston End Rod | 活塞端杆 | End-rod arm, light 15 (retracted base + head). 端杆造型，发15级光。 |
| Skull Piston | 头颅活塞 | Head has an independent POWERED face (calm/excited observer face). 头部独立激活表情。 |
| Directional QC Piston / Sticky | 随朝向QC活塞/黏塞 | QC reads the head cell instead of the cell above. QC改为读取活塞头格。 |
| Observer Piston / Sticky | 侦测器活塞/黏塞 | Detects updates on the back face; observer timing. 从底座面检测更新。 |
| Piston Redstone End Rod | 活塞红石端杆 | End-rod arm; head emits a torch-style signal forwards; light 8. 头部输出红石火把信号。 |
| Long Push Piston | 长推活塞 | Extends on placement, never retracts. 放置直接推出，永不收回。 |
| Weak Piston | 虚弱活塞 | Cannot push; destroys destroy-on-push blocks; 2×2 rod. 推不动方块，可破坏易碎方块。 |
| Fast Piston / Sticky | 快速活塞/黏塞 | Moving pistons finish the same tick progress reaches 1 (ice crafting). 移塞提前一tick落位。 |

## Building / 构建

Requires JDK 17/21/25 (auto-provisioned via foojay). Gradle 9.7.1 wrapper included.

```bash
./gradlew build                 # active version (1.21.11) / 当前版本
./gradlew buildAll?             # not configured — build each node:
./gradlew :1.19.4:build :1.21.10:build :1.21.11:build :26.2.x:build
```

Multi-version via [Stonecutter](https://stonecutter.kikugie.dev/): 1.19.4 / 1.21.10 / 1.21.11 / 26.2, mojang mappings, primary version 1.21.11.

多版本由 Stonecutter 管理，主版本 1.21.11，使用 mojang mappings。

## Conversions / 转换

- Cyan dye: piston/sticky/loop → chain (sticky stays sticky for sticky sources). 青色染料转换。
- Purple dye: piston/chain → loop. 紫色染料转换。
- Water bottle: chain/loop → vanilla piston. 水瓶还原。

## Assets / 资产

All textures are derived from the vanilla jar by `tools/gen_assets.py` (also generates blockstates, models, recipes, loot tables, lang, icon). Re-run after editing the table.

所有贴图由 `tools/gen_assets.py` 从原版 jar 派生生成，同脚本生成模型/配方/战利品表/语言/图标。

## License / 授权

[WTFPL v2](./LICENSE) — do what the fuck you want to.
