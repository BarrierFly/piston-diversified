# Piston Diversified / 活塞多样化

A Fabric mod that adds piston variants. **Mod v2** completes the plan: all 33 pistons ship, in two batches (v1 = 19, v2 = 14). Current release: **0.2.1** (1.19.4 / 1.21.10 / 1.21.11 / 26.2).

一个添加活塞变种的 Fabric 模组。**mod v2** 完成规划：33 种活塞全部实装，分两批（v1 = 19 种，v2 = 14 种）。当前版本：**0.2.1**（1.19.4 / 1.21.10 / 1.21.11 / 26.2）。

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

## Piston list / 活塞列表 (v2)

| Piston | 活塞 | Notes / 说明 |
| --- | --- | --- |
| Potato Piston | 马铃薯活塞 | Floatater-style structure selection (3 connection rules, `potato_push_limit` gamerule, default 32); the pushed structure keeps gliding through the air after the piston is gone (递归飞行). Destroy-on-push blocks and glued replaceables (grass, snow) travel with the structure; waterlogged blocks travel waterless and leave water behind; block entities are never carried — a chest in front stops the push, and so does the world border (推不动就不动). 结构选取采用三条连接规则，飞行结构递归推进；POP 类方块与黏到的可替换方块（草、雪片）随结构一起被完整带走，含水方块脱水飞行、原地留水；方块实体不会被带走——正前方的箱子会挡停推动，推到世界边界同样停住而不是丢方块。 |
| Pickaxe Piston | 镐活塞 | Mines the front block with its carried pickaxe instead of pushing it (silky touch / fortune apply, no durability); the tool rides on the block and the item. Blocks vanilla cannot push (obsidian and friends) are mined too when the pickaxe can harvest them. 用携带的镐瞬间挖掘前方方块（黑曜石等原版推不动的方块在下界合金镐下也可挖），镐随方块与物品无损往返。 |
| Recursive Piston / Sticky | 递推活塞/黏塞 | Telescoping arm: while powered the head pushes out one cell at a time (head + rods against the 12 push budget). 通电时逐格伸出，头与杆计入推动上限。 |
| Turn Push Piston / Sticky | 拐推活塞/黏塞 | Pushes the front structure sideways (placement-time bend direction) and ends in a bent head; the arrow on the retracted plate shows which way it bends (the bent head keeps the plain piston top — an arrow on that face would have to point out of itself). The sticky version pulls the block glued to the bent plate back to the head cell. 推出方向为放置时选定的拐弯方向，活塞盖上的箭头指示拐弯方向（仅收回状态可见，推出后弯头盖沿用普通活塞盖贴图）；黏性版沿拐弯方向把方块拉回头所在格。 |
| Wall Merge Piston / Sticky | 墙并活塞/黏塞 | Wall-post rods connect side-by-side pistons; the group holds out while any base is powered and retracts together (sticky groups share the summed pull budget). 杆部连成墙，任一底座有信号则整组不收回。 |
| 0-Tick Piston / Sticky | 0t计划刻活塞/黏塞 | Block events replaced by 0gt scheduled ticks; the extend/retract decision is re-derived from live signals when the tick runs. 以0gt计划刻代替方块事件。 |
| Gravity Piston | 重力活塞 | Up = normal push; sideways = the head becomes a falling block; down = telescopes through air until it meets a block; a headless base cannot retract. Unlike every other head, the gravity head can be pushed and pulled by other pistons — a detached head falls like sand. 侧推头变重力方块，下推逐格伸出，无头底座不可收回；与其他活塞头不同，重力头可被其他活塞推拉，脱离的头像沙子一样下落。 |
| Strong Piston I / II / III | 强力活塞一/二/三档 | Counts unpushable blocks (obsidian, bedrock, …) as 6 / 3 / 2 pushable blocks against the 12 budget, so they really move (1.21.4+; the blocks still exist on 1.19.4). 不可推动方块按 6/3/2 折算计入推动上限并真实移动。 |

## Crafting / 合成

All recipes are shapeless unless a 3-row grid is shown; in grids `T`/`P` = any planks, `#` = cobblestone, `R` = redstone. Recipes live in `data/piston_diversified/recipes` (`recipe` in 1.21+).

除给出 3 行排布的为有序合成外，其余均为无序合成；排布中 `T`/`P` = 任意木板，`#` = 圆石，`R` = 红石粉。配方位于 `data/piston_diversified/recipes`（1.21+ 为 `recipe`）。

| Piston | 活塞 | Crafting / 合成 |
| --- | --- | --- |
| Honey Piston | 蜂蜜活塞 | Piston + Honey Bottle / 活塞 + 蜂蜜瓶 |
| Projectile Piston | 抛射活塞 | Piston + Bow / 活塞 + 弓 |
| Chain Piston / Sticky | 连锁型活塞/黏塞 | Cyan dye on Piston / Sticky / Loop (see Conversions) / 对活塞、黏塞或循环塞使用青色染料（见转换） |
| Loop Piston | 循环型活塞 | Purple dye on Piston / Chain (see Conversions) / 对活塞或连锁塞使用紫色染料（见转换） |
| Wind Charge Piston | 风弹活塞 | Piston + Wind Charge / 活塞 + 风弹（1.20.5+ only, no recipe in 1.19.4 / 仅 1.20.5+，1.19.4 无配方） |
| Silent Piston | 静音活塞 | Piston + any Wool / 活塞 + 任意羊毛 |
| Recoil Piston | 后坐活塞 | Grid `TTT` / `PXP` / `#R#`, `X` = iron ingot / 排布中 `X` = 铁锭 |
| Piston End Rod | 活塞端杆 | Piston + End Rod / 活塞 + 末地烛 |
| Skull Piston | 头颅活塞 | Piston + the 6 mob heads (skeleton, wither skeleton, zombie, player, creeper, dragon) / 活塞 + 六种生物头颅 |
| Directional QC Piston / Sticky | 随朝向QC活塞/黏塞 | Grid `TRT` / `#X#` / `#P#`, `X` = iron ingot; sticky: QC Piston + Slime Ball / 排布中 `X` = 铁锭；黏性版 = QC活塞 + 黏液球 |
| Observer Piston / Sticky | 侦测器活塞/黏塞 | Piston + Observer / 活塞 + 侦测器; sticky: Observer Piston + Slime Ball / 黏性版 = 侦测器活塞 + 黏液球 |
| Piston Redstone End Rod | 活塞红石端杆 | Piston + End Rod + Redstone / 活塞 + 末地烛 + 红石粉 |
| Long Push Piston | 长推活塞 | Piston + Block of Redstone / 活塞 + 红石块 |
| Weak Piston | 虚弱活塞 | Grid `TTT` / `#N#` / `#R#`, `N` = iron nugget / 排布中 `N` = 铁粒 |
| Fast Piston / Sticky | 快速活塞/黏塞 | Grid `TTT` / `#I#` / `#R#`, `I` = ice; sticky: Fast Piston + Slime Ball / 排布中 `I` = 冰；黏性版 = 快速活塞 + 黏液球 |
| Potato Piston | 马铃薯活塞 | Piston + Potato / Poisonous Potato / 活塞 + 马铃薯或毒马铃薯 |
| Pickaxe Piston | 镐活塞 | Piston + any Pickaxe (the pickaxe is kept) / 活塞 + 任意镐（镐被保留） |
| Recursive Piston / Sticky | 递推活塞/黏塞 | Piston + Piston / 活塞 + 活塞; sticky: Grid `SS` / `SS`, `S` = Sticky Piston / 黏性版排布 `S` = 黏塞 |
| Turn Push Piston / Sticky | 拐推活塞/黏塞 | Grid `#TT` / `#X#` / `#R#` (or the mirrored form with the planks on the right); sticky: + Slime Ball / 排布见左，木板也可放右侧；黏性版 = 拐推活塞 + 黏液球 |
| Wall Merge Piston / Sticky | 墙并活塞/黏塞 | Piston + any Wall / 活塞 + 任意墙; sticky: + Slime Ball / 黏性版 = 墙并活塞 + 黏液球 |
| 0-Tick Piston / Sticky | 0t计划刻活塞/黏塞 | Piston + String / 活塞 + 线; sticky: + Slime Ball / 黏性版 = 0t活塞 + 黏液球 |
| Gravity Piston | 重力活塞 | Grid `TTT` / `#G#` / `#R#`, `G` = Gravel / 排布中 `G` = 砂砾 |
| Strong Piston I / II / III | 强力活塞一/二/三档 | Vanilla piston grid with 1 / 2 / 3 of the top planks replaced by Creaking Hearts (`H`) / 原版活塞配方上排改 1/2/3 个嘎枝之心 `H`（1.21.4+） |

## Building / 构建

Requires JDK 17/21/25 (auto-provisioned via foojay). Gradle wrapper included.

```bash
./gradlew build                 # active version (1.21.11) / 当前版本
./gradlew :1.19.4:build :1.21.10:build :1.21.11:build :26.2.x:build   # all nodes
```

Multi-version via [Stonecutter](https://stonecutter.kikugie.dev/): 1.19.4 / 1.21.10 / 1.21.11 / 26.2, mojang mappings, primary version 1.21.11.

多版本由 Stonecutter 管理，主版本 1.21.11，使用 mojang mappings。

Version-specific availability: the wind charge piston needs 1.20.5+, the strong pistons need the creaking heart (1.21.4+) — on older versions the blocks exist (creative tab / commands) but have no recipe.

版本差异：风弹活塞需 1.20.5+，强力活塞需嘎枝之心（1.21.4+）；旧版本方块仍注册（创造栏/指令可得）但无配方。

Gamerule / 规则：`potato_push_limit` (default 32) caps the potato piston's structure size.

## Testing / 测试

`tools/piston_testbed.py` boots a headless dev server over RCON and asserts piston behaviour (data-file parse check plus 22 cases) on all four version nodes.

```bash
python tools/piston_testbed.py --start --version 1.21.11
```

`tools/piston_testbed.py` 启动无头开发服并通过 RCON 断言活塞行为（数据文件解析检查 + 22 项用例），四个版本节点均已跑通。

## Conversions / 转换

- Cyan dye: piston/sticky/loop → chain (sticky stays sticky for sticky sources). 青色染料转换。
- Purple dye: piston/chain → loop. 紫色染料转换。
- Water bottle: chain/loop → vanilla piston. 水瓶还原。

## Assets / 资产

All textures are derived from the vanilla jar by `tools/gen_assets.py` (also generates blockstates, models, recipes, loot tables, lang, icon). Re-run after editing the table.

所有贴图由 `tools/gen_assets.py` 从原版 jar 派生生成，同脚本生成模型/配方/战利品表/语言/图标。

## Changelog / 更新日志

### 0.2.1

- Fixed / 修复：含水方块被马铃薯活塞复制（源格改为置水，脱水副本飞行）。
- Fixed：后坐活塞底座回移动画对客户端不可见（~2gt 闪现）。
- Fixed：马铃薯飞行链在下界/末地永久冻结（飞行队列现按维度推进）。
- Fixed：马铃薯结构顶到世界边界时最前排方块丢失（出界即整体停住）。
- Fixed：递推活塞伸出态基座模型杆短 4px（与碰撞箱不符）。
- Fixed：缩回的自制活塞永远推不动（现与原版缩回活塞一致可推可拉）；伸出的自制活塞按原版语义不可推；1.19.4 缩回基座不再导红。
- Fixed：1.19.4 风弹回退现会引爆 TNT 且作用半径与原版一致。
- Change：马铃薯活塞把黏到的可替换方块（草、雪片）作为真实成员完整带走，含水方块脱水飞行、原地留水，正前方的方块实体挡停推动（不会被带走）。
- Change：重力活塞头可被其他活塞推拉（其余活塞头维持不可推拉）。
- Change：自制活塞基座/头/递推杆挖掘速度与原版活塞一致（补 mineable/pickaxe 标签）。

### 0.2.0

- mod v2: potato, pickaxe, recursive (×2), turn push (×2), wall merge (×2), 0-tick (×2), gravity, strong (I–III). 马铃薯、镐、递推×2、拐推×2、墙并×2、0t 计划刻×2、重力、强力三档。

## License / 授权

[WTFPL v2](./LICENSE) — do what the fuck you want to.
