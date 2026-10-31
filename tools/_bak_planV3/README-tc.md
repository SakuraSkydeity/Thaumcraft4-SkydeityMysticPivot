# Thaumcraft: Mystic Pivot
## 神秘时代：玄枢学

> 工程手册 + 逐批记录 · 立项 2026-10-08 · 本版整合于第 156 批（2026-10-22）
>
> **本版做过一次结构整合**：原文档 §12.1 ~ §12.155 每批一节、共 10200 行，其中大量是同一功能的
> 反复微调、构建日志与调试输出。现在改成三段式——
> **§7 系统档案**（按主题讲清「现在是怎么实现的」）、**§8 逐批索引**（一行一批，查「哪一改动过」）、
> **§9 坑集**（症状查根因）。整合前的原始逐批记录归档在 `.none/逐批详录-1-156.md`。

---

## 0. 怎么读这份文档

| 你想知道 | 去哪 |
|---|---|
| 这是个什么模组、装进去会多出什么 | §1、§7 |
| 编译、换本体版本怎么操作 | §5、§6 |
| 某个系统现在怎么实现、为什么这么写 | §7 系统档案 |
| 第 N 批改了什么 | §8 逐批索引 |
| 遇到一个怪 bug，先怀疑什么 | §9 坑集 |
| 还剩哪些没做、下一步干什么 | §10，或 `.none/ROADMAP-tc.md` |

---

## 1. 项目标识

| 项目 | 取值 |
|---|---|
| 英文名 | Thaumcraft Skydeity Mystic Pivot |
| 中文名 | 神秘时代：玄枢学 |
| modId | `skydeitymysticpivot` |
| Java 包根 | `skydeity.mysticpivot` |
| 资源命名空间 | `skydeitymysticpivot` |
| 当前版本 | `0.1.0` |
| 构建产物 | `SkydeityMysticPivot-0.1.0.jar`（约 628 KB） |
| 作者 / 许可 | skydeity / MIT |

> `neoforge.mods.toml` 的 `displayName` 刻意保持默认英文（模组列表、日志里显示
> `Thaumcraft Skydeity Mystic Pivot`）；中文名只出现在**创造栏页签**与**魔导书左侧分类**上。
> 这是用户明确要求的分离（第 154 批）。

---

## 2. 唯一依赖口径（本项目最重要的纪律）

本体各版本间变动很大，因此本项目**只以本体的公开 API 制品作为编译边界**，完全不触碰本体实现。

- 编译期唯一依赖：`*-api.jar`（`gradle.properties` 的 `parent_api`），Gradle 中声明为 `compileOnly`。
- **允许** import 的包：`dev.tc4port.thaumcraft.api.**`，即 `api.json` 中 `publicPackages` 声明的唯一公开包。
- **禁止** import 本体实现包 `dev.tc4port.thaumcraft` 下 api 以外的任何包（`registry`、`common`、
  `platform`、`nativeimpl`、`network`、`client` …）。
- **禁止** 反射、AT、Mixin 进入本体内部；需要本体行为时一律走 API 的注册入口。
- 例外只有一处：**`InfusionMatrixBlockEntityAccessor`（`@Accessor` Mixin）**，用来把注魔矩阵的
  `instability` 清零（本体 API 没有写入口，见 §7.1 与 §9 第 12 条）。为它单独引入
  `parent_runtime` 作 `compileOnly`，不打进产物。
- API 返回的 `Registration` 句柄要持强引用，失效时 `close()`（幂等）。
- 附属 jar **不得**包含本体或本体 API 的类。
- `*-sources.jar` 仅作**只读参考**，不参与编译。

---

## 3. 环境矩阵

| 项目 | 版本 |
|---|---|
| JDK | 21（`C:/Program Files/Eclipse Adoptium/java-21.0.3-delta`） |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248（`[21.1.248,)`） |
| 本体 | `4.2.3.5-1.21.1-port.0.1.0-20746`（api jar 与运行时 jar 同版本） |
| Gradle | 8.9（wrapper 自带） |
| moddev 插件 | `net.neoforged.moddev` 1.0.17 |

---

## 4. 目录结构

```
SkydeityThaumcraft_1.21/                     项目根（Gradle 工程根）
├── build.gradle / settings.gradle
├── gradle.properties                        ★ 版本与依赖路径总开关
├── gradlew.bat / gradle/wrapper/            Gradle 8.9 wrapper
├── README.md                                立项文档（给外人看的简介）
├── .none/                                   ★ 一切「不进 git 的参考料与记录」
│   ├── 1.21.1-neoforge-20744-dev-focus-refactor-snapshot/  本体 20744 快照（历史参考）
│   ├── 1.21.1-neoforge-20748-dev/tc/        本体 20748 快照（api + sources）
│   │   └── tc.jar                           ★ 完整运行时 jar（第 168 批用户补放，parent_runtime 指向它）
│   ├── README-tc.md                         本文件
│   ├── ROADMAP-tc.md                        进度与目标纲领
│   ├── 开发进度总结.md                       功能清单（面向玩家）
│   └── 逐批详录-1-156.md                    整合前的逐批原始记录（10200 行）
├── tools/                                   Python 工程链（见 §7.13）
│   ├── gen_*.py                             贴图 / 模型程序化生成（含 gen_maple_tree.py，第 167 批）
│   ├── selfcheck.py / deadcode_scan.py / orphan_scan.py / pyclean_scan.py
│   └── preview_*.py                         预览渲染（等距方块、书页、研究树）
└── src/main/
    ├── java/skydeity/mysticpivot/           84 个 .java
    │   ├── MysticPivot.java                 主类（MODID / LOGGER / 装配）
    │   ├── ModBlocks / ModItems / ModBlockEntities / ModMenus / ModRecipes / ModTab
    │   ├── ModWandParts / ModFocuses        杖柄杖端、法杖核心
    │   ├── ModAspectSources                 八卦要素的运行时来源叠加
    │   ├── Star*.java                       星象（盘、读星、仪式、工作判定、引导线）
    │   ├── Xuanshu*BlockEntity/Menu/Screen  九台机器的 BE / 菜单 / 界面
    │   ├── MapleSaplingBlock / MapleTreeGrower   枫树苗与树形（第 167 批）
    │   ├── compat/jei, compat/emi           两个配方查看器
    │   └── mixin/                           7 只（见 §7.11）
    └── resources/
        ├── META-INF/neoforge.mods.toml
        ├── skydeitymysticpivot.mixins.json
        ├── assets/skydeitymysticpivot/      466 个文件 / 116 个模型 / 25 个 blockstate
        └── data/skydeitymysticpivot/
            ├── recipe/                      54 个顶层配方 + 55 个坩埚条目
            ├── loot_table/blocks/           25 张方块掉落表（含枫树四件，第 167~168 批）
            ├── worldgen/                    configured / placed feature（枫树 + 樱花林加密，第 167~168 批）
            └── thaumcraft/
                ├── aspects/                 8 支八卦要素
                ├── focus_catalog/           4 份法核档案
                └── research/bagua.json      35 条研究
        └── data/minecraft/、data/neoforge/   原版标签追加（枫木进 logs/planks/saplings…）与 biome_modifier
```

新建源码一律放 `src/main/java/skydeity/mysticpivot/` 下，按功能开一级子包，文件名与内容同名同义。

---

## 5. 构建

### 5.1 Windows 上的正确姿势

Git Bash 里**不能**直接 `./gradlew.bat`（会以 POSIX 方式解释脚本，报找不到 Java）。
必须走 cmd：

```bash
cd /d/.codepy/trea/slash/SkydeityThaumcraft_1.21
cmd //c "gradlew.bat build --offline"
```

- 首次构建需联网拉 moddev 1.0.17 与 NeoForge 21.1.248 userdev；NeoForm 中间产物本机已缓存，
  之后一律加 `--offline`。
- 本机 `~/.gradle/gradle.properties` 已设 `org.gradle.java.home` 指向 JDK21。
- **javac 编码**：`build.gradle` 里必须显式 `options.encoding = 'UTF-8'`，中文源码与中文 lang
  否则会乱码（第 65 批踩过）。

### 5.2 产物核对

产物落在 `build/libs/SkydeityMysticPivot-0.1.0.jar`。核对时看三样：jar 体积、SHA256、
以及 jar 内关键资源与源码是否逐字节一致（`unzip -p … | sha256sum`）。

### 5.3 交付前自检

```bash
python tools/selfcheck.py      # 模型/方块状态/lang 等键/贴图尺寸/禁用字符 → 期望 ALL GREEN
python tools/deadcode_scan.py  # Java 死代码 → 期望 0
python tools/orphan_scan.py    # 孤儿资源（无人引用的贴图/模型） → 期望 0
```

---

## 6. 本体升级时的应对

1. 新的 dev 目录替换 `.none/1.21.1-neoforge-XXXXX-dev/`；
2. **只改** `gradle.properties` 的 `parent_api` / `parent_runtime` 两行（必要时同步 `neoforge.mods.toml` 的版本区间）；
3. `cmd //c "gradlew.bat clean build --offline"`，编译报错点即 API 变更点；
4. 拿新旧两个 api jar 做 **javap 全量签名 diff**，确认到底动了什么，再决定源码改不改。

历史升级路径：

| 批次 | 版本 | 结果 |
|---|---|---|
| 第 90 批 | 20740 → 20741 | 兼容性核对全过，源码零改动 |
| 第 127 批 | 20741 → 20744 | focus 体系重构，法核档案格式报错，改了 `ModFocuses` |
| 第 149 批 | 20744 → 20746 | 公开 API 只多 `WandClientStages$BeamId` 与 3 条带 `operation` 的重载，未触及，源码零改动 |
| 第 167 批 | 20746 → 20748 | 用户换 dev 快照（20746 树已删、20748 树只有 api+sources **无 runtime jar**）；javap 全量签名 diff 确认公开 API 相对 20744 全是**新增**（`VisChangePlan`/`VisChangeRequest`/`VisChangeResult`/`VisChangeSettlement` + `WandApi.changeVis` 一族、`WandVisStages`、`Thaumonomicon.back()`、Beam/Origin 的 `operation` 重载），本 mod 不碰，源码零改动；`parent_runtime` 暂借 20744 的运行时 jar 只作编译（六个 mixin 目标类已逐一核对 20744 字节码与 20748 sources 成员签名一致，见 §12.167） |
| 第 168 批 | 补 20748 runtime jar | 用户把完整运行时 jar（19.3 MB / 2246 实现类）放到 `.none/1.21.1-neoforge-20748-dev/tc.jar`；`parent_runtime` 从「借 20744」切回**真 20748**，六个 mixin 目标类的成员签名在该 jar 上重新 javap 核对无误，源码零改动 |

---

## 7. 系统档案

> 这一节按「系统」而非「批次」组织：每个系统只讲**现在是什么样**、关键实现在哪个文件、
> 以及为什么不能换个写法。想知道「哪一批变成这样的」去 §8。

### 7.1 要素体系：八卦

八支要素走**数据包注册表**（`data/…/thaumcraft/aspects/*.json`），每支由两个六基元合成：

| 卦 | aspect id | 合成 | 色 | 地气（`Fengshui`） |
|---|---|---|---|---|
| 乾 | `qian` | 风 + 光 | 鎏金 | 开阔天空（头顶无遮挡） |
| 兑 | `dui` | 水 + 金属 | 银白 | 沼泽 |
| 离 | `li` | 火 + 光 | 朱赤 | 绯红 / 诡异森林 |
| 震 | `zhen` | 风 + 气候 | 青碧 | 恶地（粘土山） |
| 巽 | `xun` | 风 + 树木 | 青苍 | 森林 |
| 坎 | `kan` | 水 + 黑暗 | 靛蓝 | 暖水海洋（海草） |
| 艮 | `gen` | 地 + 束缚 | 岩褐 | 山地 |
| 坤 | `kun` | 地 + 混沌 | 土黄 | Y < 10 |

**关键实现**

- **「八卦要素更好拿」必须走运行时 API**：数据包只能声明要素本身，没法给「原版物品」加要素含量。
  所以 `ModAspectSources` 用本体 API 在初始化时叠加 25 件原版物品的来源（第 11 批）。
- **物品含量 = `object_aspects`**，有两条硬规矩：① 键用 `aspect` 全名（`skydeitymysticpivot:qian`）；
  ② 同一物品只能有一份定义，重复会在日志里静默覆盖。
- 玄枢锭 / 玄枢碎片 / 各兽锭的 `object_aspects` 都含**全八卦各 1~2**，所以丢进坩埚能煮出任意卦气。
- 要素图标 32×32，与本体同规格；单色系（按卦色明暗分档），不画渐变。

### 7.2 法杖与法核

**四柄法杖**（`ModWandParts`，走本体 `WandPartApi`）：

| 成品 | 杖柄 id | 容量 | 耗魔系数 | 杖端 | 装配 Vis / 基元 |
|---|---|---|---|---|---|
| 宇宙寰宇支配法杖 | `infinity` | 99 999 | ×1.0（原价，第二百零九批由 ×0.1 提上来） | 晶簇杖端 | 10 × ? |
| 玄枢法杖 | `onyx` | 2 035 | ×0.6 | 玄枢寰宇鸿蒙杖端 | 25 × 40 = 1000 |
| 八卦四象法杖 | `sixiang` | 1 949 | ×0.8 | 四象麒麟杖端 | 25 × 22 = 550 |
| 朱雀蝶舞宏伟包裹法杖 | `zhuque` | 918 | ×0.8 | 朱雀蝶舞杖端 | 10 × ? |

装配 Vis = 杖端 `craftCost` × 杖柄 `craftCost`（源码 `WandAssemblyLogic` 证实）。
成品名自动拼成「杖端段 + 杖柄段 + 法杖」，稀有度按杖分档（`stampRarity`）。

**四圣兽一档**（第 161~163 批）：朱雀 / 青龙 / 白虎 / 玄武四柄是入门档，**容量一律 918、
耗损一律 ×0.8**（第 162 批先把容量拉齐、第 163 批再把耗损拉齐，此前是 950/980/940 各差一点、
0.9 与 0.85 两档）。杖柄与杖端贴图各带本兽记号（朱雀金环火舌 / 青龙竹节龙角 / 白虎冰裂虎爪 /
玄武甲片龟珠），第 162 批「杖柄和杖端不要一样」时加的。

**四枚法核**（`ModFocuses` + `data/…/focus_catalog/*.json`）：

| 核心 | focus id | base_cost（厘 vis / 基元） | 折合 | 作用 |
|---|---|---|---|---|
| 法杖核心:镇狱碎青峰 | `cosmos` | 1000 | 10 点 | 指方块 → 方块本体化为掉落物（基岩照掉） |
| 法杖核心:乾元罡气 | `guardian` | 20000 | 200 点 | 绕身守护之心，死亡时消耗一颗免死并回满 |
| 法杖核心:赤羽焚天瞬 | `teleport` | 1000 | 10 点 | 记一处坐标，一念即回 |
| 法杖核心:烛龙衔时 | `chrono` | 2500 | 25 点 | 指机器 → 16 倍速 32 秒；同一台再催翻倍至 256 封顶（第 164~166 批） |

四枚法核**配方齐全、排布成锯齿链**（第 179 批定稿）。烛龙衔时在第 166 批曾被删去配方
（当时只想让它当创造栏专属），第 179 批按「和别的法核一样」的要求补回；**第 210 批四枚一起重新定价**：
要素统一成**八卦各 32**（原来是各 64），不稳一律 6，材料各自换了主题件——

| 核心 | 中心 | 八格材料 | 卦石 |
|---|---|---|---|
| 镇狱碎青峰 | 元始珍珠 | 下界合金镐 1、玄枢锭 1、玄枢碎片 2、玄枢八卦之盐 2、八卦碎片 2 | 艮 ×1 |
| 乾元罡气 | 不死图腾 | 水银 4、**玄枢耀光** 4、**玄枢八卦之盐** 2、**玄枢布匹** 2 | 艮 ×1 |
| 赤羽焚天瞬 | **末影之眼** | 末影珍珠 2、龙息 1、玄枢碎片 2、玄枢八卦之盐 2、八卦碎片 1 | 艮 ×1 |
| 烛龙衔时 | 时钟 | 红石 2、水银 2、下界之星 1、**玄枢耀光** 2、八卦碎片 1 | **八卦石八种齐立** |

只有烛龙衔时是 `mode=all`（`trigram_gate/machines.json`）：坛脚 5×5 里乾、兑、离、震、巽、坎、艮、坤
**每种至少一块**，少一种注魔直接不启动（动作栏点名缺哪几卦）；多出来的每块压 2 点不稳、至多算 3 块。
它的物品 lore 因此多出第三行，研究页也多出一张「八石镇坛」页（`bagua.page.focus_chrono_gate`）。

研究树上四枚核心**同时挂艮卦**（第 180 批用户要求，取代第 179 批的父子链）：坐标
(5,2) → (4,3) → (3,4) → (2,5)，一条对角线，从艮卦出发的夹角 14° / 33.7° / 56.3° / 76°，
相邻间隙 19.7° / 22.6° / 19.7°，离源距离 86~99px 基本相等，看起来是一把匀开的扇子。
夹角不到 30° 也不缠 —— 第 51 条那条「≥30°」的结论偏严，真正会缠的只有源点附近
`5/sin(θ/2)` 那一段（θ=20° 时约 29px，而节点本身就遮住前 11px），详见 §9 第 57 条。

它的加速实现见 `TemporalCore`（`BlockState#getTicker` 补跑 + 倍率悬浮字，不走 Mixin）。

法核的几条**格式硬要求**（每条都崩过，见 §9）：档案必须包在 `root.focus_profiles` 数组里；
必须带 `vis_discount`；`ornament_texture` 要写完整路径含 `textures/`；饰图必须被 item 模型引用。
守护之心的持久化走 NeoForge `AttachmentType`（`guard_hearts`），可叠加、无时限、随存档。

### 7.3 材料链

```
四神兽锭（奥术工作台，六基元各 87；第二百零九批由 100 降下来）
   └─ 坩埚：锭 1 + 八卦各 2  →  四神兽碎片
   └─ 炎炉：一锭 → 八卦碎片 ×2
八卦碎片  ──坩埚：1 + 八卦各 4 ──→  玄枢碎片  ──炎炉：1 → 玄枢八卦之盐 ×2
未烬的玄枢锭（奥术工作台，正中压下界之星，六基元各 129）
   └─ 坩埚（北斗面 + 七格各一枚玄枢碎片）→  玄枢锭
```

- 布匹：四象布匹（白羊毛 + 八卦各 2）、玄枢布匹（**四象布匹** + 八卦各 4；第 215 批触媒由玄枢八卦之盐改成四象布匹），都走坩埚。
- 玄枢耀光（第 210 批）：本体「闪耀之光」（`thaumcraft:nitor`）的紫化近亲，注魔材料。
  奥术工作台「紫水晶 2 夹闪耀之光、下垫玄枢八卦之盐」，六基元各 **77**（第 214 批由 30 提上来），
  图样 `DAD / ABA / DCD` 四角补神秘锭（第 214 批，§7.16），一次出 2 枚。
  贴图 `tools/gen_xuanshu_nitor.py`（结构抄本体 16×16 火焰，只换色：紫带 + 四粒星辰色火星）。
- 阿卡西记录「森罗万象·阿卡西天机玄录」：右键全要素 +999 研究点。
- 授业卷轴「万法通玄·太虚授业天卷」：右键补全全部研究笔记。
- 玄枢祛魔书：与带附魔物品合成，附魔拓成一册附魔书、原物附魔清零但**原物退回**。
- 玄枢双生书：与一本附魔书合成，产出**两本一模一样的附魔书**（附魔、改名、其余组件整份复制），双生书本身被吃掉。
- 枫凝菼蒄（第 212 批）：枫叶里凝出来的赤橙冰晶，**打破枫叶树叶 20% 概率掉**（枫叶战利品表里
  追加的独立池，剪刀 / 精准采集拿叶子时同样会掉），64 一组。贴图 `tools/gen_maple_crystal.py`
  （16×16、五档赤橙、一根尖头晶柱）。眼下只有戒指要用它。
- 玄枢洞虚灵目（万劫玄枢·洞虚守一戒）：Curios 戒指栏，看节点要素与类型。
  **第 212 批补上注魔配方**：中心**揭示之护目镜**，外围玄枢八卦之盐 2 / 玄枢布匹 2 / 玄枢锭 2 /
  下界之星 2 / **龙蛋 1** / 枫凝菼蒄 2，八种卦象要素各 **64**，不稳 **25**（本 mod 配方里最高的一档），
  坛脚 5×5 还要**八种卦石齐立**（`trigram_gate` 里 `xuanshu_void_eye` 一条，`mode=all`）。

### 7.4 工具

| 工具 | id | 要点 |
|---|---|---|
| 八卦开山辟地镐 | `bagua_pickaxe` | Tier：耐久 52 000 / 速度 40 / 附魔亲和 22；**总伤 20**（Tier 5 + 属性 15）；注魔可升档 |
| 玄枢翎凰焚天镐 | `xuanshu_pickaxe` | 镐斧铲三用 + 隧道掘 + 时运 XVI（**成品即附魔**，不是每 tick 补写）；**总伤 66**；普通右键切「掉落模式」 |
| 玄枢玄冥镇海锄 | `xuanshu_hoe` | 9×9 耕地 + 潜行放水 + 手持催熟；**总伤 52** |

三把工具共用 `TOOL_ATTACK_SPEED = 4.0F` 的攻速加成（第 162 批）：原版基础 4.0 + 4.0 = 8.0，
攻击间隔由 5 刻砍到 **2.5 刻**（正好一半）。

两把玄枢工具的注魔升档都要求「基座下面 5×5 立乾、坎、兑各一块」，
且**注魔不保留中心工具原有的附魔与属性**——书里用红字写明「升炉之前先把该附的魔附好」。

玄枢翎凰焚天镐的**掉落模式**（第 160 批，`PickaxeDropModes` + `PickaxeDropEvents`）：
普通右键在「精准 / 时运 / 直取本体」几档之间切，挖掘时按当前档改掉落，提示只报模式名。

### 7.5 九台机器 / 工作方块

| 名称 | id | 干什么 | 要点 |
|---|---|---|---|
| 玄枢炎炉 | `xuanshu_infernal_furnace` | 免燃料熔炼 + 专属烧炼表 | 3 并行；六面侧配；星象翻倍 |
| 玄枢坩埚 | `xuanshu_crucible` | 吸要素做配方 | 3 个工作面；「吸多少做多少」 |
| 玄枢炼金炉 | `xuanshu_alchemy_furnace` | 烧物品取源质 | 3 并行；传输配置面板 |
| 玄枢复刻台 | `xuanshu_replicator` | 一进一出原样复制，数据组件全随行 | 南斗六星全供玄枢碎片才开工；六面四模式侧配；最大堆叠 1 |
| 玄枢充能器 | `xuanshu_charger` | 单方块把灵气节点养到 64/t | 青铜鼎造型；4 秒满 |
| 玄枢奥术台 | `xuanshu_arcane_table` | 附魔升级（喂，不是附） | 星盘前置：南斗五星各供一枚玄枢锭；一次运转六元各 **77** Vis（第 214 批由 129 降下来）+ **可选书槽**（放附魔书则书耗一本、书上附魔原样并入，不判能否生效） |
| 玄枢注灵香炉台 | `xuanshu_node_modifier` | 给节点硬塞要素 / 改类型 | 两槽收「任意天华 + 玄枢锭」；华槽**不判种类**（第 214 批废除「同类拒收」）；涨要素三段式（下详） |
| 九天净界玉 | `xuanshu_purifying_stone` | 腐地回青 | 单方块 |
| 玄枢台座 | `xuanshu_pedestal` | 开坛 + 卦象转化 + 吸纳罐中要素 | 多方块；必须先成坛才认卦象 |
| 玄枢源质罐 | `xuanshu_essentia_jar` | 存要素（本体罐的加强版） | 第 193 批新增、第 194 批整改；**32 种 × 每种 2048**；**第 210 批改奥术合成**（神秘锭 2 / 钻石 1 / 本体源质罐 1 / 玻璃板 5，六基元各 54），lore 压成一行；蓝紫盖 + **斜向**鎏金液面（不随要素变色）；Jade 可见要素表；`EssentiaSource` 全挂，注魔与基座抽取有丝线动画；挖起时内容随物品走（下详） |

- 九台机器的**物品形态最大堆叠 16**（星盘与卦石仍是 64），避免一次挖一背包（第 151 批）。
- 三台老机器（炎炉 / 坩埚 / 炼金炉）的**传输配置面板**同构：3×3 十字方向按钮，
  中央格渲染该方块本体作方位指示（该格即北面），底色即模式（灰关 / 蓝输入 / 橙输出 / 青输入输出），
  无文字；走 `clickMenuButton` + `ContainerData` 同步。

**玄枢注灵香炉台的三件事**（原名「玄枢注灵台」，第 163 批改名；注册 id 不变）：

1. **形制**（第 157~160 批）：底板 + 四角立柱 + 顶框四梁 + **井沿**（洞内口一圈 1px 退台，
   比梁面低 1px），中间镂空；JSON 与 Java `VoxelShape` 共 **13 个盒子且逐块一致**。
   四角柱第 158 批往里收 1 像素（0/13→1/12）；端面第 159 批起按自己占地 1:1 取图，
   不再被 16 宽顶框带压扁；井沿第 160 批加。
2. ~~**华槽同类拒收**（第 162 批）：放进去的天域之华若与**上一瓶**同类型，提示换一种且不耗材料；
   只判「不一样」，别的一概不管（类型串存在 BE 的 `LastBloomType` 里）。~~
   **第 214 批按用户口径整条废除**：任意一种天域之华都收、连续喂同类也照收；`lastBloomType`
   字段、NBT 键 `LastBloomType`、`bloomTypeKey()` 方法、撞类型早退、语言键 `same_bloom`
   五处一并删净（旧存档残留标签按未知标签忽略）。这一条现在只留历史记录。
3. **涨要素三段式**（第 163 批，`XuanshuNodeModifierMenu#nextAspect`）：
   ① 六门基础有缺 → 补缺的那门（100%）；② 六门齐但每门 < **256** → 80% 涨六门之一
   （只在未满的门里等概率）、20% 添新要素；③ 六门各满 256 → 20% 添新、六门合计 **48%**
   （每门 8%）、其余现有复合要素合计 **32%**（现有 N 门时每门 32/N%）。每次 **+12**；
   256 是<b>分段阈值</b>（第 163 批定 100 → 第 207 批 2048 → **第二百零九批按用户口径落回 256**），
   不是硬顶 —— 节点单门能涨到多少由本体 `NodeVis` 说了算（它只限制种类数 ≤ 256）。
   第三段的加权是本批改的：旧版六门满后「六门与复合要素一视同仁」，节点上几十门复合要素
   会把六门摊薄；现在六门固定拿 48 个百分点。全表写进魔导书 `bagua.page.node_modifier_lore`。

**玄枢虚空水晶塔（第 193 批形态；第 198 批改名 + 光苞；第 199 批提速 + 换 lore）**：从第 174 批的「沟灵核心 + 顶盖」两格结构整个推倒，
现在是「一台机器 + 背后一座**八乘八的塔**」。**名字第 198 批由「玄枢虚空采矿机」改成「玄枢虚空水晶塔」**
（用户口径「矿机名字改为玄枢虚空水晶塔」；注册 id `xuanshu_void_mining` 与 Java 类名都没动，只改显示名与 lore）：

- 机器本身只有一格（`xuanshu_void_mining`），空手右键开界面；**带 `FACING`**（放下去时正面朝玩家），
  塔长在**背面**；
- 塔由 `VoidAltarBlueprint` 定义（**纯代码常量**，第 192 批）：**8×8、高 13 格、255 格、9 种方块**
  （玻璃 73 / 羊毛 44 / 錾制石英 42 / 海晶灯 36 / 台阶 32 / 楼梯 12 / 玻璃板 8 / 石英砖 6 / 石英柱 2）；
  底座四角立柱、中段一圈台阶与玻璃、上段收成 4×4 的玻璃壳、顶端再收成一根细尖；
  玻璃壳里是 2×2 的海晶灯芯柱（`dy 3~8` 实心），往上收成一条细灯芯到塔尖；
- **形制就是两组常量**：`STATES[19]`（完整方块状态串）+ `CELLS[1020]`（平铺的 `dx, dy, dz, 状态序号`），
  类初始化时展开成 `List<Part>`。运行时**零文件 IO、零 NBT**，也没有任何「配置漂移」的可能；
  换建筑 = 跑 `tools/gen_void_blueprint.py` 重出这个 Java 文件再编译；
- **8 格宽没有「正中那一格」**：中轴落在正中那条**格线**上（`bounds()` 给出 `-3 … 5`，中轴 `1.0`，
  即 `machine + 1.0`）。塔心是一圈 **4×4 的淡蓝玻璃壳**（壁在 `dx, dz = −1 / 2`，即 `中轴 ± 2.0`，
  厚一格），壳里包着 **2×2 的海晶灯芯柱**（`dx, dz ∈ {0, 1}`，占 `中轴 ± 1.0`，dy 3~8 实心）。
  壳与芯之间留出**四个内角**，就是四条 1×1 的**竖向空腔**（中心在 `中轴 ± 1.5`）——
  全塔**只有这四列 13 层全是空气**，其余靠近中轴的列都被羊毛 / 石英 / 玻璃占了 3~12 层；
- **结构齐 = 自动激活**（方块实体每秒自检，`FORMED` 决定亮度与是否产出），未激活不产出也不攒进度；
- **不撒任何粒子**，光效全由 `XuanshuVoidMiningRenderer` 每帧**画**出来
  （第 195 批推倒重做成「一条线」，**第 197 批改成四根圆管**）：
  **四道深蓝圆光**，各站在塔顶那一圈淡蓝玻璃里、绕塔心四个侧面各一块的**上表面**上
  （现在是 `dy = 8` 那层：北 `(1,−1)`、南 `(0,2)`、西 `(−1,0)`、东 `(2,0)`，起点 `y = 9`），
  顶端 `36.0`；**半径** `BEAM_RADIUS = 0.09`（全宽 0.18 格，定宽）、**α = 0.9**，
  颜色 `0.11 / 0.20 / 0.78`；
- **为什么从「十字」改成圆**：第 195 批那根柱是两片**零厚度四边形十字交叉**（最省的画法），
  可零厚度面片只有正对时才是「线」，侧看是一片扁的、45° 看就是**十字** ——
  用户口径「仔细看是十字，我不希望是这样，用光线一样的圆，然后要 4 条，在四个玻璃上」。
  现在绕轴切 `BEAM_SIDES = 12` 段画**圆柱侧面**，任何角度看截面都是圆；
- **落点不写死**：`VoidMiningStructure.beamAnchors(machine)` 从结构常量里挑出
  「同一层里恰好四块、上面没压东西的淡蓝玻璃」（规则：**从低往高取第一个恰好四块的层**），
  返回每块的 `{x, y+1, z+0.5}`（格心、上表面）；凑不出四块就返回空表，
  渲染器退回「中轴一根、从 `height()` 起」的老样子 —— 所以 `axis()` / `height()` 都还活着；
- **根上添一枚光苞**（第 198 批，用户截图里红笔圈的那个尖头形状）：`drawSpindle` 沿轴切
  `LENS_RINGS = 12` 层、一圈 `LENS_SIDES = 16` 段，半径按 **`R · sin(πt)`** 走
  （`t` = 这一层占全高的比例）—— 上下各收成一个尖、正中间鼓到最粗，侧影就是一片两头尖的叶子，
  光柱从它中间穿过去。`LENS_RADIUS = 0.55` 不是随手取的：四个落点最近两根相距 **1.41** 格
  （`(1,−1)` 与 `(2,0)`），半径必须 `< 0.70` 才不互相穿插；`LENS_HEIGHT = 8`（根部往上八格，
  正好罩住「刚出塔顶」那一段）、`LENS_ALPHA = 0.55`（比光柱淡一档，读作「光散出来」）；
  两端四边形退化成线，`NO_CULL` 下看不见，省掉为尖头单写三角；
- **渲染类型必须只写颜色**：用 `RenderType.debugStructureQuads()`（`POSITION_COLOR` + `QUADS` +
  `NO_CULL` + 半透明混合 + `LEQUAL` 深度测试 + **`COLOR_WRITE` 不写深度**）。
  上一版用 `debugQuads()`（同一套状态，但它 `COLOR_DEPTH_WRITE` 会写深度），
  结果是**光柱周围一圈玻璃凭空消失**：方块实体在半透明地形**之前**渲染，
  它写进主缓冲的深度会被 `translucentTarget.copyDepthFrom` 抄进半透明缓冲，
  随后画的玻璃 / 水全被这条深度挡掉 —— 这就是用户说的「竖线周围会透视方块」（§9 坑 #93）；
- **渲染包围盒必须自己给**：默认只包方块自身前后一格，二十几格高的光柱一出盒子就被**整条剔除** ——
  这就是「抬头会闪现」的根因。覆写 `getRenderBoundingBox`，水平直接用
  `VoidMiningStructure.bounds()`（塔的 8×8 投影）、竖直到 `BEAM_TOP + 2`（§9 坑 #85）；
- 界面（196×188，AE 亮色）右侧两枚按钮：**一键搭建** / **一键收回**，走 `clickMenuButton`；
  **一键收回不再弹悬停框**（第 193 批删），一键搭建仍带材料提示；
  材料从玩家背包结算（缺料点名），收回材料进背包、塞不下掉脚下；
  产出栏 **3×4 = 12 格**（第 193 批由 3×3 加一列、整体左移一格：`OUT_X` 62 → 44、`SLOT_COUNT` 9 → 12）；
  进度条改成**先灌色、后画框**（`XuanshuGui.ring` 最后落笔），底边不再漏一条缝（§9 坑 #87）；
  **第 199 批起这条竖条不再量「这一炉还剩多少」** —— 周期缩到 1 tick 之后那个数没有意义，
  改画**运转指示**：`getSweep()` 直接取 `gameTime % SWEEP_TICKS(20)`，一秒扫一圈，
  机器在转它就一直动（数据槽 0 / 1 的语义跟着改，版式一个像素没动）；
- **判定只问「那一格不是空气」**（`satisfied`），不问方块种类 —— 石英换混凝土、玻璃换别的照样算数；
- **一键收回按每一格实际的方块掉**（`Block.getDrops`）：玻璃还玻璃、羊毛还羊毛；
  带方块实体的（箱子、漏斗、别的机器）跳过不碰并报数；
- **产物仍是 `#c:ores` 全部矿，但节奏第 199 批拉到顶**（用户口径「输出效率该最快，和工作方块一样，
  不同类型输出也变快，保证 tps 不要变高」）：`CYCLE_TICKS` 100 → **1**，与三台工作方块同拍
  （炎炉 `SMELT_INTERVAL` / 坩埚 `PROCESS_TICKS` / 炼金炉 `PROCESS_TICKS` 全是 1）；
  一批**不再是一种矿**，而是 `TYPES_PER_CYCLE = 4` 种**互不相同**的矿（随机起点顺序取，
  所以「不同类型也一起变快」），每种 `1~2` 个 → **每 tick 4~8 件**；
  产物**直接合并进栏**（`ItemHandlerHelper.insertItemStacked`：先叠同种、再找空格），
  12 格实际能压 12 × 64 件，不必每 tick 找空格；
- **「快」不许换来 tick 成本**（同一条口径的后半句「保证 tps 不要变高」）：
  ① 产物变动**不再每次发方块实体包**，改 `SYNC_INTERVAL = 10` tick 节流（`markClientDirty` / `flushSync`，
  界面里的槽位本来就由菜单每 tick 自己同步，不靠这个包）；
  ② 音效 `SOUND_INTERVAL = 40` tick 最多一声（原来每产一次响一次）；
  ③ 自动输出 `AUTO_IO_TICKS` 10 → **1**（不推就跟不上），但补了短路：**栏里一件东西都没有就直接返回**，
  空闲时一次能力查询都不做；④ 矿物清单仍是静态缓存、结构自检仍是**一秒一次**；
  ⑤ 想再快只调 `TYPES_PER_CYCLE` / `MAX_PER_TYPE` —— 一批的成本是「4 次合并」，
  与批量大小无关，**不会多出任何 tick 任务**；
- 自动化挂**机器自身四面**；「烛龙衔时」直接点机器，倍率字浮在塔顶之上（7.6 格）。
- **第 212 批两处改动**（用户口径「水晶塔输出产物的效率跟玄枢熔炉一样，要最快，指的是输出
  而不是生成矿物」+「产物栏左边隔远一点添加竖着的 3 个格子，里面画上锭的描边……」）：
  - **输出面 4 → 6**：炎炉的 `autoOutput` 走 `Direction.values()`（默认下面也是输出面），
    这台塔原来只推东西南北 —— 正下方 / 头顶的漏斗、管道收不到东西，看上去就是「产物堵在塔里」。
    现在同样**六面、每 tick、整摞**地推（`AUTO_IO_TICKS` 早在第 199 批就是 1），
    **光坛塌了停转时也照样把栏里剩的推出去**（原来 `return` 在 `autoOutput` 之前）；
  - **锭槽（3 格）**：`OUT_SLOTS = 12` 之后接 `UPGRADE_BASE = 12` 的三格，
    每格收一枚锭、**不消耗**，按难得程度给效率（**第 213 批把档位整个反过来**，
    用户口径「四神兽之锭效率最低，未尽第二，玄枢效率最高」）：
    **四神兽之锭 +1 / 未尽的玄枢锭 +2 / 玄枢锭 +3**（合计 0~9）。
    （212 批当时是反的：四神兽 +3 / 玄枢 +2 / 未烬 +1，理由是「难得程度」，但四神兽锭是最早、
    最便宜的一档，价与效正好拧着 —— 213 批按价重排。）效率只加产出批量：`typesPerCycle() = 4 + 效率`（封顶 13）、
    `maxPerType() = 2 + 效率/4`（封顶 4）—— 满级一批 13 种 × 1~4 个；
    对外那份 `IItemHandler` **只暴露 12 格产出栏**，锭槽不会被管道抽走；
    界面左侧竖排三格（`UP_X 13 / UP_Y 22 / 步进 18`），空槽铺一张 16×16 **锭描边**
    （`textures/gui/ingot_ghost.png`，`tools/gen_void_mining_gui.py` 用原版铁锭 alpha 生成），
    悬停给「收哪三种锭、各加几级」，槽下写一行「效率 +N」。

**星象仪（第 186 批新增；第 187 / 188 / 189 批三次改形制）**：`astrolabe`，
第 182~185 批那套「悬浮八卦白瓷球」单独立方。第 187 批撤掉须弥座、改「薄底盘 + 细柱」，
第 188 批把底盘压到 0.38px；**第 189 批把「鎏金底盘以下」整段砍掉**（用户截图框起来的那一块），
银白八角主台成了新底面，剩下「主台 + 托座 + 球 + 相轮刹」整体缩到 **一格之内**（原来 3.34 格高）——
一颗球坐在一只八角小台上，球离台一指，读作「浮着」。**纯装饰、无功能**：
无方块实体、无交互、亮度 10；碰撞箱两段（底座 6.28~9.72 见方 + 球 2.44~13.56 见方），不按整格算。
模型走 `tools/gen_astrolabe_obj.py`；**物品图标直接吃 OBJ**，与方块**共用同一份 `astrolabe.obj`**
（缩进一格之后几何本来就居中在格心，`astrolabe_item.obj` 已于第 189 批删除）。

**玄枢源质罐（第 193 批新增；第 194 批整改）**：`xuanshu_essentia_jar`。
外观沿用本体罐的**模型与几何**（同两个 element、同 6~14 的尺寸），但**贴图改成自家三张**：
`essentia_jar_side` / `essentia_jar_top`（**盖子换成蓝紫**）与 `essentia_jar_bottom`（原样搬来）。
三张图由 `tools/gen_jar_lid.py` 从 `tc.jar` 里读出本体原图，**只把「深棕的盖子像素」按亮度换成
蓝紫**（`LID = 0.54/0.36/0.86`，判据 `r > b + 0.02 且 max < 0.55`），玻璃那层、每个像素的明暗
一笔不动 —— 看上去就是「同一只罐子换了个蓝紫盖」。blockstate 只有一条 `""`（没有朝向）。

- **可收纳不同种要素**：`Map<AspectId, Integer> contents`，`MAX_KINDS = 32` 种、
  每种 `CAPACITY = 2048`（第 193 批 2048 → 第 195 批 1024 → **第二百零七批回到 2048**）。
  上限的取法不是拍脑袋：罐子要被写进物品组件，32 种 × 2048 是这个前提下的安全值，
  而且 32 种已覆盖四原质 / 六元质 / 绝大多数复合要素（§9 坑 #86 记了「种类」为什么也要有上限）；
- **液体是斜着渐变的鎏金、不随要素变色**：`XuanshuEssentiaJarRenderer` 画一格内的液面盒
  （`MIN/MAX = 0.25/0.75`、底 0.0625、量程 0.625 格），**逐顶点**取色：
  `t = 0.55 · 高度比例 + 0.45 · 对角线比例`（对角线 = `(x−MIN + z−MIN) / 2(MAX−MIN)`），
  再拿 `t` 去查三档色 `DEEP {0.42,0.24,0.04}` / `MID {0.94,0.70,0.17}` / `LIGHT {1.00,0.95,0.70}`
  （中点 0.55），叠 `1 + 0.07·sin(0.05t − 5.5u)` 的微光。第 193 版是「按层」取色，只能竖着渐变、
  像块分层塑料；逐顶点之后颜色沿「西北下角 → 东南上角」那根斜轴推进，同一层四个面颜色也各不相同。
  `RenderType.solid()`、亮度取 `max(200, packedLight)`；液面高度取 **最满那种 / 2048**
  （第 195 批由「总量 / 2048」改来：罐子按要素分别计数，液面该跟着最满的那一格走，
  否则 32 种各倒一点也显示满罐，看着像假的）；
- **Jade 能看见罐里有什么**（第 194 批自己写插件；**第 196 批整包删掉，改成本体自动接**）：
  20751 起（20753 沿用这一套）`EssentiaMultiStoreView` 的父接口换成 `EssentiaStorageView`，本体又给**所有**
  `BlockEntityType` 登记了 `EssentiaCapabilities.STORAGE` —— 罐子 `instanceof` 它就自动暴露，
  本体的 `EssentiaStorageJadeProvider`（注册在 `Block.class` 上）于是自己把存储段挂上来，
  画成「每种要素 数量 / 上限」的比例条，比自写版还多给了百分比。
  我们只需要补两条上限（`essentiaCapacity` 返回 2048 / `sharedEssentiaCapacity` 返回 `false`）。
  自写的 `compat/jade/` 两个类、`mods.toml` 里的 jade 可选依赖、`deps/jade-*.jar` 编译依赖、
  两条 `jade.*` 语言键**全部删除** —— 同一份信息画两遍只是重复（§12.196）。

实现要点：

- BE 同时实现 `EssentiaTransport`（收 / 放 / 抽空判定）与 `EssentiaSource`（给注魔矩阵与玄枢基座抽），
  并把 **`EssentiaMultiStoreView`** 一起挂上；三个能力都在**本模组自己的 `RegisterCapabilitiesEvent`**
  里登记（`EssentiaCapabilities.TRANSPORT` / `.SOURCE`，`BlockCapability.createSided`），本体只给自己的 BE 登记；
- **多要素容器必须覆写两条默认实现**：`availableEssentia(aspect, face)` 与 `extractableAspect(face)` ——
  原版默认只认「单一类型」，不覆写就只会给一种、其余全当零（§9 坑 #86）；
- `insert(...)` 返回**拒收数**、`extract(...)` 返回**取走数**；`extractEssentia` 是**一次付清**语义，
  只允许返回 0 或原样 `requested`；`suctionType` 给 `null`（什么都收）、`suctionAmount` 在
  `hasRoom()` 时给 32；
- **抽取动画天然正常**：注魔矩阵 `drainOneEssentia` 与 `XuanshuPedestalBlockEntity#drawEssentia`
  都走 `EssentiaApi.extract(...)`，只认 `EssentiaSource` 能力 —— 能力一登记，罐子与注魔台 / 基座之间
  那条**丝线动画自动出现**，不需要额外写渲染；
- **内容随物品走（第 194 批整条重做）**：内容是一个真正的物品组件
  `skydeitymysticpivot:jar_essentia`（`ModDataComponents`，平铺 `要素 id → 份数` 的 `CompoundTag`）；
  BE 在 `collectImplicitComponents` 里写出去、`applyImplicitComponents` 里读回来，
  战利品表用 `minecraft:copy_components`（`source: block_entity`）复制到掉落的物品上 ——
  这一条与本体防护罐完全同构，**生存挖 / 创造挖 / 别的模组拆走的是同一张表**（§9 坑 #89）；
  空罐不写组件，所以空罐之间照旧能叠（`stacksTo(16)`）；
- 创造模式要补一刀 `playerWillDestroy`：服务端在创造模式下**根本不调 `playerDestroy`**，
  不补就一颗都不掉（本体 `WardedJarBlock` 也是这么补的）；
- 潜行空手右键**倒空**（`BOTTLE_EMPTY` + 提示 `essentia_jar.cleared`）；比较器输出按装满程度分档；
- 方块：`strength(0.4F, 1.5F)`、`SoundType.GLASS`、`noOcclusion()`、**亮度 10**、
  碰撞整格但外形照本体罐（`OUTLINE` 3~13 / 0~12）、`getShadeBrightness = 1.0F`；
- 配方：玻璃围**金锭**的十字（`" G "` / `"GQG"` / `" G "`）；物品 `Rarity.RARE`、`stacksTo(16)`、三条 lore。

**玄枢传送门（第 197 批新增；第 198 批从「纯装饰」变成一台机器；第 199 批提品阶 + 挪摆放）**：`xuanshu_portal`，
外形取《星际争霸 2》神族折跃门（Warp Gate）—— 深蓝紫金属、鎏金饰条、向内弯的双角、
角间一颗青蓝能量核。

- **第 197 批的外形那套没动**：模型仍是 `minecraft:block/cube` 一句，六面各给一张贴图 ——
  **north / south 是门面**（双角 + 能量核），east / west 是侧面装甲，up 是八角封盖，down 是底板；
  四张图都是 **32×32**，由 `tools/gen_portal_tex.py` 程序化生成（改一个常量重跑就换形，不用手绘）。
  弯角不是拿小方块拼的：对每个像素算**到角中线的最短距离**定厚度、再由**跨宽坐标 u∈[-1,1]**
  查色带（外缘鎏金 → 亮钢脊 → 暗钢 → 内缘青蓝），倒角与镶边都跟着曲线走；
- **第 199 批：品阶与摆放都对齐水晶塔**（口径「传送门和水晶塔一样史诗品质颜色、堆叠 16，
  创造栏传送门和水晶塔放一起」）：`Rarity.RARE → EPIC`（紫名），`stacksTo(MACHINE_STACK)` = **16**
  （与三台工作方块、水晶塔共用同一个常量）；创造栏从「星象仪前面」挪到**水晶塔正后面** ——
  页签顺序变成 **塔 → 门 → 源质罐**，两台同一件事的产物挨着摆；
- **第 198 批：用户口径「给传送门添加和矿机一样的 ui（只添加 ui 不设计功能，先把建筑搞定）」**。
  于是它从「一只普通方块」变成了**带方块实体与界面的机器**，但**传送本身还没做**：
  - `XuanshuPortalBlock`：多了 `facing`（放置时正面朝玩家）与 `formed`（广场齐不齐），
    亮度 `7 → 12`（对齐后亮），空手右键开界面；
  - `XuanshuPortalMenu` / `XuanshuPortalScreen`：**版式一个数字都没重写**，槽位 / 背包 / 按钮坐标
    全部直接引用 `XuanshuVoidMiningMenu.OUT_X` / `INV_X` / `BTN_X` 那一套 ——
    哪天水晶塔的界面挪了位置，这台跟着挪，两边不会各走各的。12 格栏 + 竖排进度条 +
    一键搭建 / 一键收回 + 激活点，看一眼与水晶塔的界面**逐像素同构**；
  - 唯一的语义差别：**进度条量的是工程量**（已立起格数 / 604），不是产出周期 ——
    这一批要的就是「先把建筑搞定」，按完一键搭建看着条往上走；
  - 12 格栏眼下一只空箱子（能放、挖掉会散出来），`FILLED` 一秒算一次但**只走菜单的
    `ContainerData`**，不写进方块状态（1099 格的广场，每秒一次 `setBlock` + 光照重算会把客户端刷得发烫）。

**折跃门广场（第 198 批 schem 版 → 第 203 批改成纯代码的 16×16 三层方台）**：

- **形制不再来自 schem**（用户口径「基座底部我希望是连贯在一起，保证美观，然后一层层往上，
  可以不要 obj 就单纯代码，改为 16×16」）：`tools/gen_portal_blueprint.py` 从「把 schem 编译成常量」
  改成「**几个常量直接算**」—— `SIZES = (16, 12, 8)`、柱位、各段高度全写在脚本顶部，
  跑一次就重新生成 `PortalGateBlueprint.java`（`STATES[7]` + `CELLS[2416]`，
  **604 格 / 16×16 / 高 15 / 7 种方块**）。运行时依旧零文件 IO、零 NBT；
- **三层方台，一层比一层小**：y0 = **16×16 整块铺地**（四连通只有 1 块 —— 没有洞、没有孤岛，
  上一版最外那格孤立方块也随重做一起没了）、y1 = 12×12、y2 = 8×8，四周各收 2 格，
  留出 2 格宽的走道；
- **每层同一套花纹**：外圈白瓷压四个金角，往里一圈紫珀，紫珀环的四个斜角嵌海晶灯，
  四条边正中各嵌两格冰蓝玻璃 —— 灯位层层对齐，俯视是一圈套一圈；
- **门**：8 格宽，立在最上面那层 8×8 上 —— 两根方柱（柱脚紫珀、柱身两道金箍、外侧一条冰蓝玻璃缝）、
  门洞一道金槛、**4×7 蓝玻璃门面**、两级收分的门楣、顶上一条嵌海晶灯的门冠；
  正面朝局部 −Z，摆到世界时随 `facing` 旋转；
- **材料**（悬停一键搭建时逐行列出）：錾制石英 260 / 石英块 144 / 紫珀 80 / 金块 40 /
  淡蓝玻璃 36 / 蓝玻璃 28 / 海晶灯 16 —— **7 种 604 格**，比上一版（12 种 1099 格、含 3 颗信标）
  便宜一大截，而且**没有方块实体**，一键收回不会有收不回来的东西；
- **摆放三步**（`PortalGateStructure`）：① `FRONT_SHIFT = 0 − minZ`（这一版 minZ = 0，
  补正量就是 0；公式留着，换形制时自动跟随）；② `Y_SHIFT = −1` 整座往下沉一格 ——
  于是最底层铺地正好是**方块脚下那一层**，方块自己那一格永远是空的；
  ③ 按 `facing` 绕 Y 转（北 `(x,z)`、东 `(−z,x)`、南 `(−x,−z)`、西 `(z,−x)`），
  四份布局静态缓存，摆一次算一次；
- **离线自检与预览**：`tools/preview_portal_place.py` 用同一套公式复算，除原有三条断言
  （深度 0 / 前沿整排 `y ≤ −1` / `(0,0,0)` 空），**第 203 批新增第四条：最底层必须是一整块连通的方形**
  （四连通块数 = 1 且格数 = 边长²），并出**三张图** —— `portal_place_iso.png`（等距）、
  `portal_place_top.png`（俯视，四朝向并排）、`portal_place_front.png`（正视，标出每层标高）；
- **一键搭建 / 收回的流程不在这个类里**：第 198 批把 `VoidMiningStructure` 那一百多行施工流程
  抽成了 `StructureDeployer`（`Piece` 记录 + 算账 / 清场 / 落块 / 按格自己的战利品表收回 /
  缺料点名 / 带方块实体的格子跳过），两个结构共用，语言键靠 `prefix` 分开
  （`message.skydeitymysticpivot.void_mining.*` / `....portal.*`）。
  矿机那边只剩「把偏移摊成世界坐标」这一步，行为一字未变；
- **判定**只问「那一格是不是空气」，与水晶塔同一口径：石英换混凝土照样算数。

### 7.6 台座、净界阵与卦石

- **台座**：仿本体奥术基座的三段元素模型 + 四角丝带连台；右键置物、空手取物；
  立在地气对上时台面黑曜石转化为哭泣的黑曜石；吸纳上方罐中要素。
- **八枚卦位石**：贴图是「奥术石材底 + 刻符」，用于所有注魔配方的**门禁**。
  门禁是**数据驱动**的（配方 id + 台座下方 5×5×1 区域），缺卦时动作栏点名缺哪一块。
- **九天净界阵**：九块齐亮（卦位石接 `active` 状态），法杖右键开阵，阵心一枚加大尖晶。

### 7.7 星象系统

- **南北斗枢玄星盘**：3×4 十二格成阵，十三星（北斗七星 + 南斗六星，摇光为隐星）。
  供物落在星位上，翻面切换斗。
- **星象判定**（`StarReader` / `Constellation`）：
  `北斗全曜` / `南斗全曜` / `两斗合口` / `斗魁` / `南斗口合` / `杀相` / `杂曜` / `星象未成`。
- **星象仪式**（`StarRite`）：炎炉产物翻倍、坩埚与两台机器的南斗前置、复刻台南斗六星。
- **四色引导线**：炎炉赤 / 坩埚绿 / 两台机器金 / 复刻台紫（复刻台第 153 批改供龙蛋后转蓝绿）。
  渲染走**本体 TC 的 `AFTER_LEVEL` 世界后通道** + 相机朝向广告牌（加法发光），
  这样抬头看天不会消失（第 144 批根治）。
- 判定与门禁**同源**：星位摆错时头顶那条线也不亮，玩家不用猜。

### 7.8 魔导书

- 35 条研究，一条研究 = 若干页；条目字段：`display_column` / `display_row` / `parents` / `siblings` / `icon`。
- 书页正文宽度 `ResearchBookPaginator.WIDTH = 139`，全角字 9px ⇒ **一行约 15 个中文字**，超了自动折行。
- 文案体例统一为**三段式**：「一 · 功能 / 二 · 合成要求 / 三 · 来历」。
  配方页把「要素 / 不稳 / 卦石」拆成三行，选型页一条一行（第 155 批）。
- 颜色：`§9` 深蓝用于「基座下面立…卦石」这类关键条件（全书 27 处），`§c` 红用于警告。
- **格式硬要求**（每条都崩过存档）：每页必须带 `aspects`（空对象也行）与 `recipe_link_policy`；
  `COMPLETED_SINGLE` 页**恰好 1 个**配方 id。
- 研究树连线是本体画的**带正弦抖动的渐变曲线**，起点 ±5px 摆动——所以节点摆位的判断标准不是
  「几何上不相交」，而是**同源两条边的夹角 > 30°**（§9 第 19 条）。

### 7.9 界面规范

- 三台老机器与两台新台共用 `XuanshuGui` 里的常量（边距、行高、字色），不再各写一套。
- **版式只许有一份**（第 198 批）：玄枢传送门的界面**不新写任何坐标**，槽位 / 背包 / 按钮 / 进度条
  全部直接读 `XuanshuVoidMiningMenu` 的 `public static final`（`OUT_X` / `INV_X` / `BTN_X` / `BTN_Y` …）。
  「和某台机器一样的 ui」如果靠抄一遍常量实现，两批之后必然分叉。
- 字色：正文浅灰蓝，提示句蓝色，**不加投影**（第 153 批去掉）。
- 侧配面板、进度箭头、火焰图标逐像素手绘，不引贴图资源以外的东西。

### 7.10 贴图规范

- **尺寸白名单**：16×16 为主（95 张）、32×32 用于要素图标、模型与**玄枢传送门四面**（59 张）、
  16×64 与 256×256 各一张。`selfcheck.py` 会报「少见尺寸」，非 16×16 必须是有理由的。
- **像素三原则**：有限调色板 / 硬边（按像素中心点判定，不做抗锯齿）/ 分面明暗。
- **不要给每像素加随机抖动**。抖动会造出几百种近似色，16×16 放大后全是噪点——
  「像素风不够浓」十次有九次是这个原因（§9 第 18 条）。注灵台与灵目都是归零抖动后才干净的。
- 物品图标 16×16 起步；64×64 在 16 格物品栏里必糊成色块，除非确实要极清。
- **细环 / 细线怎么画**（第 165~166 批的教训，见 §9 第 38~41 条）：
  不要用 `disc(r_out, r_in)` 那种内外半径带——16×16 上 0.7 宽的带在某些角度
  一个像素中心都踩不到，环直接碎成一串虚线；要按「像素中心到曲线的距离 ≤ 0.5px」取像素
  （即把曲线栅格化），得到一圈 8 邻接的实线。
- **透明底贴图做预览**必须 `alpha_composite`（或 `paste` 带 mask）：直接 `paste` 会把
  画布底色一起顶成透明，白线饰纹的预览就整张白了（第 166 批踩到）。

### 7.11 Mixin 清单（7 只）

| Mixin | 侧 | 干什么 |
|---|---|---|
| `InfusionMatrixCraftGateMixin` | 双 | 注魔开始前的卦象门禁 |
| `InfusionMatrixAccelMixin` | 双 | 卦石 / 星象加速注魔 |
| `InfusionMatrixBlockEntityAccessor` | 双 | `@Accessor`：读写真元 `instability`（唯一实现层例外） |
| `AuraNodeEnergizedMachineMixin` | 双 | 充能器与本体「充能节点」互认 |
| `AuraNodeVisualScaleMixin` | 客户端 | 节点视觉不再随要素数值膨胀 |
| `ParentTaintAccessor` / `ParentWarpAccessor` | 双 | 读写本体的腐化与扭曲 |

规则：**桥接类（`compat/*Bridge`）不能放在 `mixin` 包里**——mixin 包会被整体重写字节码，
桥类混进去会在启动期崩（第 68 批）。

### 7.12 兼容层

- **JEI / EMI**：坩埚、台座、炎炉专属烧炼三套分类；星象前置说明**只出现在物品 lore**
  （一行淡靛蓝 `0x8FB0FF`，与「所需卦石」同色），JEI/EMI 里不再有星象信息条目。
- **Curios**：玄枢洞虚灵目进戒指栏。
- **Jade**：**不再有自家插件**。194 批写的那套在 196 批发 20751 之后整包删除 —— 本体给所有
  `BlockEntityType` 登记 `EssentiaCapabilities.STORAGE`，罐子实现 `EssentiaStorageView` 就自动
  接上本体的存储段。`compat/jade/`、`deps/jade-*.jar`、`mods.toml` 的 jade 可选依赖、
  `jade.*` 语言键都不在了，本模组的上游依赖重新回到**只剩本体**一个。
- `compat/` 包现为：`NodeStateBridge` / `NodeStructureBridge`（本体实现层的两座桥，mixin 用）
  ＋ `emi/`（3 类）＋ `jei/`（4 类）。**桥接类不能进 `mixin` 包**（§7.11 末条）。

### 7.13 工具链（`tools/`）

| 脚本 | 用途 |
|---|---|
| `gen_*.py` | 程序化生成贴图 / 模型（灵目、注灵台、法核、水晶、星盘、四圣兽杖件、烛龙钟面…）；法核饰纹各有专脚本：`gen_focus_teleport.py` / `gen_focus_guardian_orn.py` / `gen_focus_chrono.py`；**星象仪**整体模型走 `gen_astrolabe_obj.py`（第 186 批由 `gen_orb_obj.py` 改名并改形，再往前叫 `gen_tower_obj.py`） |
| ~~`apply_batch*.py`~~ | 第 179 批已删。历史批次的落盘脚本跑过就失效，重跑反而会覆盖当前资源；需要查当时怎么改的，看 git 历史或 §8 索引 |
| `selfcheck.py` | 模型引用、方块状态、lang 等键、贴图尺寸、禁用字符（U+3000 / U+2014 / U+2212） |
| `deadcode_scan.py` / `orphan_scan.py` / `pyclean_scan.py` | 死代码 / 孤儿资源 / Python 残留。`orphan_scan` 第 184 批起收编三类视野外引用（法杖杆帽的 `wandPart` 字符串、`focus_catalog` 的 `ornament_texture`、`models/_` 前缀预览临时件），误报归零 |
| `preview_block_iso.py` | 把方块 JSON 模型 + 贴图等距渲染成 PNG（带 z-buffer 与方向光），验收贴图用 |
| `preview_pages.py` / `preview_tree.py` | 把 lang 里的真实书页文案、研究树渲染成 PNG |
| `preview_obj_iso.py` | 多格 obj 拼装出图（等距 / 正交）。第 179 批起塔是单一整体 obj，只摆一项 |
| `gen_void_blueprint.py` | 第 192 批新增，**第二百零四批改成「分层字符画」**。把 `tools/schematics/structure.schem` 一次性编译成 `VoidAltarBlueprint.java`：`CHARS` 调色板 + `STATES` 状态串 + `MAP`（一层一段字符画，`== y,x,z` 起头），255 格只占两千多个字符，比原来的 `CELLS` 平铺四元组省一半还看得懂形制。换建筑就重跑它再编译，产物**不要在 Java 里手改** |
| `blueprint_map.py` | 第二百零四批新增。两个多方块共用的编码器：`encode()` 把格子表压成**逐层字符画**（一格一字符、`.` 是空气），`simplify()` 去掉取默认值的属性（`waterlogged=false` / `half=bottom` / `shape=straight` / `axis=y` …），`java_states()` / `java_map()` 出 Java 字面量。第 192 / 198 批的 `int[] CELLS`（每格 8~12 字节）到此退役 |
| `schem_structure.py` | 第 190 批新增。读 WorldEdit 的 `.schem`（gzip NBT + varint），打印格数 / 材料 / 逐层统计并出 `tools/preview/schem_structure.png`（等轴 + 逐层俯视）。**第 192 批起 Java 不再读 schem**，它退成「生成器的前端 + 离线核对」：`gen_void_blueprint.py` 复用它读文件，人工核对也用它出图 |
| `preview_void_light.py` | 第 192 批新增，第 193 / 194 批改形，第 195 批改成一条线，**第 197 批改成四根圆管、第 198 批加光苞**。出 `tools/preview/void_light.png`（左：侧视；右：俯视）：四道**深蓝圆光**（半径 0.09、α=0.9、圆截面 12 段），各自站在塔顶 `dy = 8` 那层四块露顶淡蓝玻璃的上表面（`y = 9`）上，顶端 36；**根上那枚 `sin(πt)` 纺锤形光苞**（r 0.55 / 高 8 / α 0.55）在侧视里画成两头尖的叶子，俯视里画成外面那圈更淡的圆；俯视图画该层 + 四个截面圆，一眼能看出每根是不是压在一块玻璃上、有没有出格。**落点规则与 `VoidMiningStructure#beamAnchors` 同源**，**结构数据直接正则解析 `VoidAltarBlueprint.java`**，改了 Java 常量重跑就是最新的图 |
| `gen_jar_lid.py` | 第 194 批新增。读 `tc.jar` 里的 `jar_side` / `jar_top` / `jar_bottom`，把**盖子那块深棕像素**按亮度换成蓝紫（判据 `r > b + 0.02 且 max < 0.55`），出 `textures/block/essentia_jar_side.png` / `essentia_jar_top.png` / `essentia_jar_bottom.png`（后者原样复制）。**按颜色筛、不按坐标写死**，本体换图也不会错位；只换色相，明暗与透明度一律不动 |
| `gen_essentia_jar.py` | 第 193 批新增。出 `textures/block/essentia_glow.png`（16×16，固定种子 193）：近白噪声（底 244 / 亮纹 +9 / 颗粒 ±4），给玄枢源质罐的鎏金液面当叠加层。**只在「要素色→鎏金」这条定色方案下需要**；罐体本身照旧用本体罐的图，一张都不生成 |
| `gen_book_tomes.py` | 第二百零五批新增（原 `gen_disenchant_book.py` 改名并扩成两本）。两本「玄枢书」共用原版 `enchanted_book` 底图，只把红色绑带做一次**色相旋转**（明度不动、金扣不动）：祛魔书 300°（粉紫）、双生书 **265°（蓝紫偏蓝）**。出 `textures/item/{disenchant_book,twin_book}.png` 各 16×16 + 对比预览 `tools/preview/book_tomes.png`（并排放大 + 网格）。改色只改文件顶部的 `BOOKS` 表 |
| `gen_xuanshu_nitor.py` | 第二百一十批新增。出玄枢耀光 `textures/item/xuanshu_nitor.png` + `models/item/xuanshu_nitor.json`。**结构一点不画**：读 `tools/ref/tc4_nitor.png`（本体闪耀之光的 16×16 原图，678 B，随项目受管，不读 jar），逐像素按亮度查 7 段**紫色带**重上色，保住原图明暗与 alpha；**孤立像素**（8 邻域里不透明白像素 ≤ 1，正好是顶上那四粒火星）改**星辰色**淡青白，最亮核心退一档紫白。预览写到系统临时目录，不进项目 |
| `gen_maple_crystal.py` | 第二百一十二批新增，**第二百一十四批整张换图**。出枫凝菼蒄 `textures/item/maple_crystal.png`（16×16）：按用户给的「一簇紫粉碎晶」参考图**翻译**成三根主晶（中高 / 左低 / 右中，尖在 y=1/5/4，底端齐 y=13），**五档鲜红橘**（墨赤边 / 暗赤 / 赤 / 橙 / 亮橙金）；点位图写在文件顶部，1 像素墨边、每块色 ≥2×2、**没有抖动与孤立像素**（用户「像素风再浓一点」+「颜色改为鲜红橘色」）。预览写到系统临时目录 |
| `gen_void_mining_gui.py` | 第二百一十二批新增，**第二百一十四批调淡、第二百一十五批再淡**（α150/α44 → **α110/α30**）。出水晶塔界面锭槽的底图 `textures/gui/ingot_ghost.png`（16×16）：读 `tools/ref/vanilla_iron_ingot.png` 的 **alpha** 当剪影，只描一圈轮廓 + 极淡填充 —— 空槽说明「这儿插锭」，又不会像真放了东西。描边 α235 → **150**、填充 α70 → **44**（用户「把之前锭的也改淡一点」，与新的附魔书描边同一浓度） |
| `gen_arcane_table_gui.py` | 第二百一十四批新增。出玄枢奥术台书槽的底图 `textures/gui/book_ghost.png`（16×16）：读 `disenchant_book.png` 的 **alpha** 当剪影（= 原版附魔书形状），轮廓 `#4A5478` α**110** + 填充 `#8E93A8` α**30**（第二百一十五批再淡一档），与锭那枚同浓度（用户「画一个和附魔书一样的轮廓，稍微淡一点」） |
| `check_obj_models.py` | 模拟 NeoForge 的 OBJ 解析：blockstate→模型→obj→mtl→贴图，对账 usemtl/newmtl/vt/vn/绕序 |
| `gen_portal_tex.py` | 第 197 批新增，**第二百零四批改 16×16 三面**。出三张 16×16 贴图（`xuanshu_portal_{front,side,top}.png`）+ 两张预览：front 是拱门（门楣两道横梁 + 两根门柱 + 门槛三点能量）里一把两臂紫蓝双绞漩涡；side 是竖肋 + 两道散热槽 + 电路走线 + 折跃箭头；top 是同心方环 + 俯视迷你漩涡 + 八角切角。底面不再单独出图，模型 `down` 直接指 `top`。改形只改常量，不用手绘 |
| `gen_spirit_relics.py` | 第二百批新增。出玄灵三材（玄灵兔子脚 / 神浸魔骨 / 翎月翼膜）三张 16×16 物品贴图 + 三个 `models/item/*.json` 壳子 + 三联预览。**第二百零一批**起结构改成**原版贴图调色板点位图**（`rabbit_foot` / `bone` / `phantom_membrane` 逐像素抄录，序号按亮度排，上色时对映到新色带——原版趾线 / 骨节 / 膜纹全保留），底色按「不要白」换成棕 / 藕褐 / 月夜青紫。**第二百零二批**兔脚再改（用户「兔子脚贴图再和原版区别大一点」）：**水平镜像**（原版朝右上，这只朝左上）+ 底色换**灵玉青**（与暖棕正好相反）+ 踝部一道**鎏金脚箍** + 趾尖四点**灵光青爪尖** + 掌心 2×2 **灵纹**——结构仍是原版点位图，进背包一眼不会跟原版混 |
| `gen_terrace_obj.py` | 第二百一十一批新增（顶掉第 202 批的 `gen_pillar_obj.py`），**第二百一十三批整份重写形制**。出四象石砖激活态的**鎏金楼台** `terrace.obj` + `terrace.mtl` + 256×256 图集 `sixiang_terrace.png` + 两个模型壳子 + blockstate。**整座 3×3 只出一件 obj**（坐标 −1..2 跨格）：八角须弥座**八段**收放 —— 地栿 23.9 / 圭角 23.9→23.1 / 下枋 23.1 / 下枭 23.1→21.7 / 束腰 21.7 / 上枭 21.7→23.1 / 上枋 23.1 / 台面外放成冰盘沿（23.1→23.9），四角切 `cut=3.0`；**七道挑檐一律 45° 斜面**（不再留 0.8px 水平挑檐），加顶 / 底各一片盖板 + 四片角台 `corner_shelf`，中空台心彻底封死；上接中心方台身（右旋 45° 四棱柱，8.6→9.2 枭混→8.6→8.0 收分，到 16px 与台座柱础齐边），全座 116 面。色板取台座自己的贴图（暗墨 / 暗金 / 中金 / 亮金 / 高光），下枋画四格一组竖枋回纹、束腰画壸门竖楞、台面画四角方胜 |
| `gen_portal_blueprint.py` | 第 198 批新增，第二百零二批曾改成「纯代码算」，**第二百零四批按用户「恢复旧 schem 的多方块」改回 schem + 分层字符画**。默认读 `tools/schematics/chuansongmen.schem`（可传路径覆盖），按**底面正中**归零后交给 `blueprint_map.encode()`，输出 `PortalGateBlueprint.java`：`CHARS` + `STATES[36]` + `MAP`（1099 格 / 23×25 / 高 18 / 12 种方块）。`python tools/gen_portal_blueprint.py` 直接重出 |
| `preview_portal_place.py` | 第 198 批新增，第 199 批改判据，**第二百零二批重写并加第三张图**。用**与 `PortalGateStructure` 完全相同的公式**离线复算四个朝向：`z' = z + FRONT_SHIFT`（`0 − minZ`）、`y' = y + Y_SHIFT`（−1），断言「最靠前那一排 = 方块自己那一列（深度 0）、整排 `y ≤ −1`、`(0,0,0)` 没被占」，**再加一条：最底层必须是整块连通的方形**（四连通块数 1、格数 = 边长²，对应用户「基座底部连贯在一起」）。出三张图：`portal_place_iso.png`（等距，红格 = 方块自己）、`portal_place_top.png`（四朝向并排俯视）、`portal_place_front.png`（正视，标出每层标高）。**数据直接解析 `PortalGateBlueprint.java` 的 `STATES` / `CELLS`**。改了任一边的公式都要同步另一边 |
| `check_node_modifier_shape.py` | 注灵台 JSON 模型的九盒与 Java `VoxelShape` 逐块对账 |

### 7.14 枫树（第 167 批新增）

用户原话：「我想添加一个树类似原版大型的橡树，树叶和原木和树苗重新根据原版橡木树设计，
我希望是枫树，树叶红色偏橘色。」

| 方块 | id | 类 | 要点 |
|---|---|---|---|
| 枫木原木 | `maple_log` | `RotatedPillarBlock` | AXIS 三向；参数逐项抄原版 `oak_log`（强度 2.0 / 木料音 / 岩浆点燃）；侧面暖棕竖纹树皮 + 顶面年轮 |
| 枫木木板 | `maple_planks` | `Block` | 第 168 批新增。参数抄原版 `oak_planks`；**1 原木无序合成出 4 块**（`recipe/maple_planks.json`，原料写死 `skydeitymysticpivot:maple_log` 而不用 `#minecraft:logs`——否则橡木也能合出枫木板） |
| 枫树叶 | `maple_leaves` | `LeavesBlock` | DISTANCE / PERSISTENT / WATERLOGGED 全继承：随机 tick 按距离枯萎、剪刀直采、骨粉催熟相邻树苗；红偏橘 α 镂空贴图（cutout） |
| 枫树树苗 | `maple_sapling` | `MapleSaplingBlock` | 两段式 stage + 骨粉（45% 成功率，照原版）；只能种在泥土/草上（`BushBlock`） |

**树形 = 原版大型橡树（fancy oak），树干固定最大高度（第 172 批）**：`MapleTreeGrower` 现构
`FancyTrunkPlacer(14, 0, 0)` + `FancyFoliagePlacer(2, 4, 4)` + `TwoLayersFeatureSize(0, 0, 0, 4)`
+ `ignoreVines()`。原版 fancy_oak 是 `(3, 11, 0)`（树干 3~14 格随机）；用户第 172 批口径
「只需要最大的那款」，故 base_height 提到 14、两个随机项归零——树干恒定 14 格、
整树含冠约 18 格（wiki 确认的「大型珍异橡树」最大形态）。
侧枝的 `AXIS` 由 `FancyTrunkPlacer#makeLimb` 按枝条朝向自动摆正——这是选
`RotatedPillarBlock` 而不是整块立方体的原因。

**为什么不在 Java 里注册 ConfiguredFeature**：`TreeGrower`（原版树苗持有的类）只持
`ResourceKey`，必须把树注册进动态注册表；这里改为每次生长现构一个 `ConfiguredFeature`
直接 `place`（`TreeFeature.place` 不查注册表，只吃配置对象）。世界生成那条路则照常用
数据包：`data/skydeitymysticpivot/worldgen/configured_feature/maple_tree.json`
（同一套参数的 JSON 版）+ 两个 placed feature：

* `placed_feature/maple_tree.json`（每区块 **2** 棵）→ `biome_modifier/maple_trees.json`
  （五群系：forest / flower_forest / birch_forest / old_growth_birch_forest / dark_forest）；
* `placed_feature/maple_tree_dense.json`（每区块 **6** 棵，第 168 批）→
  `biome_modifier/maple_trees_cherry.json`（**只对 cherry_grove**，用户口径「樱花林多一点」）。

两者都是平方散布 + 洋底高度图 + `would_survive` 树苗判定 + `vegetal_decoration` 步。
枫木原木/木板/树叶/树苗的创造栏位置（第 168 批用户口径）：**树苗跟在洞虚守一戒（戒指）之后**
（与三件功能物品同组，它是「能长成树的活物」），**原木与树叶排在八卦位石之前**，
木板夹在原木与树叶之间。

**掉落**：原木直掉自身；树叶剪刀/时运掉自身，否则 5%（时运 6.25/8.33/10%）掉树苗
——与原版橡叶同表同率，只把 `stick` 池删了（魔导世界的红叶不折树枝）。

**原版融合**（全部走标签追加，`replace: false`）：
枫木进 `#minecraft:logs`（方块与物品双侧）与 `#minecraft:logs_that_burn` → 顺带进
`#minecraft:mineable/axe`、熔炉燃料 300 tick、熔炉烧成木炭（原版木炭配方的原料标签）；
枫木板进 `#minecraft:planks`（块+物）→ 斧采 + 燃料 300；枫树苗进 `#minecraft:saplings`
→ 燃料 100 tick；枫叶进 `#minecraft:leaves` + `#minecraft:mineable/hoe`。
**要素也是标签送的**：本体
`object_aspects` 的 tags 段给 `#minecraft:logs` = arbor 4、`#minecraft:saplings`
= arbor 1 + herba 2、`#minecraft:leaves` = herba 1，枫树三件**无需自己的要素文件**
即自动获得（该段只在 `direct` 查不到时生效，本体没有枫木条目）。

**贴图**（`tools/gen_maple_tree.py`，全 16×16、有限调色板、零抖动）：

*第 167~168 批*：树皮暖棕六档竖纹；顶面同心年轮；木板四道横板错缝；枫叶「五裂字符图章」；
树苗棕茎三小叶。

*第 169 批三换*（用户反馈）：
① **原木改画银树形制深色化**——竖纹树皮柱（左窄暗柱 → 宽亮柱 → 深缝 → 中明柱 → 右暗棱）
+ 三处对角节疤（扒本体 `silverwoodside.png` 形制），整体亮度压到原版六成
（原版最亮 #DDDACF → 本版 #8A8064）；顶面同心年轮同色系压深；**木板跟着压深**。
② **树叶换原版樱花树叶结构**——`cherry_leaves.png` 的字符矩阵内嵌进 gen 脚本，
只换色表：11 档粉 → 11 档红（明度次序不变）、3 档橄榄绿点缀 → 3 档橘；
覆盖率 41% → **84%**（樱花叶本来就是密花冠）。
③ **树苗换原版橡树树苗结构**——`oak_sapling.png` 的疏枝小苗，叶四档绿 → 红橘四档，
茎四档棕 → 深银四档（与银树原木同色系）。

*第 170 批三修*（用户反馈）：
① **原木不再自绘，直接扒银树原木像素结构改色**——`silverwoodside.png` /
`silverwoodtop.png` 按亮度量化 6 档的字符矩阵内嵌，映射深色银棕 palette。
起因是第 169 批自绘顶面「圆盘 + 四角透明」被用户报 bug（原版原木顶面必须整面不透明，
透明像素在游戏里是黑洞）；银树顶面原图 256/256 全不透明，换真结构后从根上不存在。
② **树叶推鲜红**——樱花结构不变，11 档红 ramp 去粉感提饱和（最浅 #FA9E94 → 最深 #A81414）。
③ **木板改用原版橡木木板结构**——`oak_planks.png`（7 色 / 四道横板 + 板顶深缝）量化 5 档，
映射深银 palette，与深色原木同色系。

*第 172 批再设计*（用户口径「原木风格基础不变学橡木颜色，木板直接用橡木木板修改」）：
① **原木侧面**——结构仍是银树原木（`SILVERWOOD_SIDE` 字符矩阵不动），palette 换成
原版 `oak_log` 侧面的 6 档棕黄（(56,43,24) → (152,120,73)，从原版 jar 逐像素提取）；
② **原木顶面**——结构仍是银树顶面，palette 取原版 `oak_log_top` 年轮色 9 档里的
6 个代表色（(76,61,38) → (194,157,98)）；
③ **木板**——不再手绘，直接照搬原版 `oak_planks` 的字符矩阵与 7 档棕黄
（(103,80,44) → (194,157,98)），与橡木木板同纹同色；
④ **树苗茎**——从深银改橡木棕（与原木同色系，协调性延伸）；
⑤ **世界生成审计**——从 NeoForm 缓存的原版 1.21.1 client jar 提取
`fancy_oak.json` 权威定义，与本 mod `maple_tree.json` 逐字段核对一致（除方块名）。

*第 173 批三调*（用户口径「树叶更鲜红；原木再暗再深→深褐；木板整体深一点、光线纹路
仿橡木」）：
① **树叶**——G/B 压得更低、R 更纯，去掉粉感，11 档红整体更鲜；
② **原木**——侧面/顶面由橡木棕黄**整体压暗成深褐**（色相偏褐红不偏灰，侧面最亮
152→132、顶面 194→148），结构仍银树；
③ **木板**——结构仍原版橡木板，**保留橡木 7 档明暗层次**（板缝/受光上缘/亮棱的
对比关系不变），只把每档色值整体压暗（最亮 194→150）；
④ **树苗茎**——跟原木压成深褐。

**树形离线预览**：`tools/preview_maple_tree.py` 把原版 fancy oak 算法（FancyTrunkPlacer /
FancyFoliagePlacer / TreeFeature 放置流程）逐行移植成 Python，用枫树贴图渲染
正立面 + 顶视图；`tools/preview/maple_tree.png`（单棵）与 `maple_trees.png`
（三棵 5/8/12 格树干并排）。树形本身不是本 mod 手写的，见 §12.171。

### 7.15 lore 约定（第 213 批立规）

用户口径：「以后添加 lore 记住卦石和星象合成需要添加，按之前的格式颜色」。

**凡是配方 / 前置里出现卦石或星象，物品 lore 必须带上对应那一行，不许漏：**

| 情形 | 写法 | 颜色 | 方法 |
|---|---|---|---|
| 注魔 / 合成要**立卦石** | 「所需卦石：乾、兑、离、震、巽、坎、艮、坤」（只写这个配方要的那几卦，八石齐立的就写全） | 淡靛蓝 `0x8FB0FF`，不斜体 | `ModItems#trigramLore(String...)`，键 `item.skydeitymysticpivot.wand_part.lore.trigram` |
| 配方 / 机器要**星象前置** | 「所需星象：北斗·天枢、…（玄枢锭）」——星座用「、」分隔，括号里写激活物 | 同色 `0x8FB0FF`，不斜体 | `ModItems#starLore(Star[], Component)` / `furnaceStarLore`（炎炉带「双倍产物烧矿：」前缀） |
| 纯风味 / 出处说明 | 一句中文 | 灰 `GRAY`，不斜体 | `ModItems#grayLore(String)` |

顺序固定：**风味行 → 卦石行 → 星象行**（先讲是什么，再讲要立什么石，最后讲要什么星）。
这一批补上的例子：洞虚守一戒（八石齐立）多了卦石行；枫凝菼蒄（出处）多了灰 lore。
星象那一行第 148 批起就从 JEI / EMI 的信息条搬到物品 lore 了，别再加回去。

### 7.16 合成表展示对称（第 214 批立规）

用户口径：「戒指注魔的 jei/emi 查看的合成表内容展示要对称，之前说过的你也要记住」。

**注魔页怎么排**（本体 `InfusionRecipeCategory#setRecipe`，第 214 批反编译确认）：中心物品在
`(50,45)`，零件沿半径 **30** 的圆摆一圈，**起始角 −90°（正上）**，每件步进 `360/N`。
所以「对称」= 下面两条同时成立：

1. **件数取偶数**。11 / 9 / 7 件时步进是 32.7° / 40° / 51.4°，无论怎么排都摆不匀
   —— 要么把配方材料调成偶数件，要么补一件同类（第 214 批的戒指就是从 11 件减到 **10 件**）。
2. **JSON 的 `components` 顺序写成镜像序列**：`c[i] == c[N-i]`（`i = 1 … N-1`）。
   `i = 0` 是**正上**、`i = N/2` 是**正下**，这两个是唯一允许左右不对称的位置
   （放龙蛋 / 下界之星这类孤品正合适）；`N = 12` 时步进 30°，坐标是像素级精确镜像。

**3×3 图样同理**：缺的格子要补，别一边空一边满 —— 第 214 批的玄枢耀光就是把原来
十字图样空着的**四个角**补上神秘锭，摆成 `DAD / ABA / DCD`。

第 214 批按这条规矩扫了一遍全部 **33 个**注魔配方：**20 个重排成镜像**（只改顺序、不改材料），
现在 **23 个**是对称的；剩下 **10 个**（5 个奇数件 + 5 个「四类以上各一件」）排不匀，列在
`ROADMAP-tc.md` §2 待定。

---

## 8. 逐批索引

> 一行 = 一批，记的是**当批真正落到代码/资源里的改动**，不含构建日志与已作废的中间方案。
> 节号与原批次一一对应（第 156 批 = `12.156`，见文末）。
> 需要某次改动的完整细节（含被否掉的方案、当时的报错），查 `.none/逐批详录-1-156.md`。

| § | 这一节做了什么 |
|---|---|
| 12.1–12.9 | **第四批**：配方体系全部改注魔（要素消耗全用八卦）/ 八卦要素来源改「稍有门槛」的原版物品 / 书页分类改「玄枢」+ 长页拆分 / 杖端杖芯贴图重制 / 新物品贴图按亮度重映射 / 新增玄枢魔晶 / 四神兽锭烧炼出全八要素 / 创造栏排序 |
| 12.10 | 注魔危险度拉满（锭/碎片/魔晶 50、法杖件/阿卡西 150）· 页签图标改法杖 · 贴图改由本体材质重上色 |
| 12.11 | 动画全删（盐/八卦碎片改静态单帧）· 两杖学宇宙寰宇配色 · 11 条注魔改「八卦各一份起步」· 魔晶改静态棱柱簇模型 |
| 12.12 | 杖芯改称杖柄 · 玄枢魔晶 = 唯一 Accessor 例外（扫描域不确定性清零）· 法杖数值重定 · 新增玄枢锭 · 玄枢链重排 |
| 12.13 | 两柄改名（玄枢银树 / 四象黑曜图层）· 新增布匹 · 玄枢碎片改炼金 · 装配 Vis 1000 / 550 · 新增玄枢镐 |
| 12.14 | 玄枢锭改十字星 · 镐图重做（32×32）· 布匹与碎片调色 · 稀有度分档（玄枢紫 / 四象蓝） |
| 12.15 | 5.5 改动落点与自检 |
| 12.16 | 海金沙砾定为海晶砂晶体 · 寰宇改「方块本体优先」· 新增四神兽碎片 |
| 12.17 | 热修：research 页缺 aspects 崩世界 · 玄武锭兽物改海晶碎片 · 创造栏成组 |
| 12.18 | 热修：recipe_link_policy 必填 · 死代码清查 · 守护饰图挂进档案 |
| 12.19 | 根因：focus_catalog 必须包在 focus_profiles · 核心改名 · lore 去斜体 · 新增 4 个分支节点 |
| 12.20 | 新增朱雀蝶舞宏伟包裹法杖 · 杖端改名寰宇鸿蒙 / 四象麒麟 |
| 12.21 | 热修：COMPLETED_SINGLE 必须恰好 1 个配方 · 新增玄枢炎炉（无燃料熔炼 + 六面侧配） |
| 12.22 | 炎炉 GUI 侧配 |
| 12.23 | 修悬停崩溃（vis_discount）· 饰图补进 item 模型 · 炎炉 GUI 改 Mekanism 式 |
| 12.24 | 饰图路径要带 textures/ · 炎炉内外双 handler + 燃料槽 · 3×3 方向板 |
| 12.25 | 传输配置面板按 Mekanism 重做 · 燃烧逻辑照抄原版熔炉 |
| 12.26 | 面板去文字、中央格渲染方块本体 · 火焰与箭头按原版显示 |
| 12.27 | 风水一期：炎炉贴图重画 / 新增玄枢台座（卦象转化）/ 八卦气脉判定 / 八枚卦位石 + 注魔门禁 |
| 12.28 | 玄枢炎炉重做（原版熔炉 UI + Mekanism 式传输配置） |
| 12.29 | 热修：注魔门禁 Mixin 启动崩溃 |
| 12.30 | 热修：台座同步 / 门禁改区块 / 清理 |
| 12.31 | 侧配加紫色档 / 页签纯色 / 台座显卦象 |
| 12.32 | 配方查看器适配 / 台座取回 / 书里三处修正 |
| 12.33 | 专属烧炼表 / 台座鎏金 / 法杖改名 / 书里「八卦之地气」 |
| 12.34 | 玄枢坩埚 / 台座改易 / 炎炉侧配收尾 |
| 12.35 | 全部坩埚配方 / 要素改「吸力」/ 挖掉掉落 / 镐子 / 炎炉 lore |
| 12.36 | 三台机器全部并行化 + 玄枢炼金炉 |
| 12.37 | 崩溃根因 ItemStackHandler 读档改槽数 / 掉内容 / lore 收敛 / 上书 |
| 12.38 | 界面竖排 / 炼金炉燃料槽 / 红贴图 / 研究树摆位 / 台座卦象根治 |
| 12.39 | 悬停提示 / lore 残留 / 要素输出 / 台座字与零重力粒子 / 朱雀杖 |
| 12.40 | 坩埚吸要素动画收尾 / 坩埚要素行挪位 / 炼金炉「不产不出」总病根 / 玄枢碎片重绘 |
| 12.41 | 炼金炉传出动画收尾 / 坩埚改「吸多少做多少」/ 碎片贴图回平滑渐变 + 星辰 |
| 12.42 | 台座改故宫风 / 卦象字再抬再收 / 卦气环只留一层 |
| 12.43 | 台座贴图整体改黑金像素风 |
| 12.44 | 台座顶面纹样加密：回字 → 八芒星 |
| 12.45 | 八卦/玄枢碎片对调 · JEI 台座页 · ★★ isSpecial 会让 JEI 整页消失 · 贴图无损重压 |
| 12.46 | 玄枢锭十字星放大改金白 · 布匹配色对调 · 卦象 lore 归位到杖材 |
| 12.47 | 台座/卦石改必须镐挖 · 魔导书左配方右介绍统一 · 锭十字星改像素粗十字 · 杖柄+杖端双门禁（配方 id + 台座下方 5×5×1）· 鸿蒙杖端改紫星 |
| 12.48 | 玄枢锭改三颗星（底图破案）· 门禁改数据驱动 · 鸿蒙杖端改蓝紫（原图从预览里救回） |
| 12.49 | 玄枢锭顶星移位 · 鸿蒙杖端紫金 · 新方块四象石砖（贴图重做）· 台座改多方块激活 |
| 12.50 | 开坛才认卦象 · 四象石砖改金色斜面 · 杖柄去黑加金 · 书里补摆法 |
| 12.51 | 去两处物品 lore · 门禁改凑齐种类+ 缺卦动作栏点名 · 杖件门禁分三组 · 八张卦石改奥术石材底 + 刻符 · 石砖造型与台座接缝对齐 |
| 12.52 | 双杖整体改色 · 碎片一炉出二 + 三器 1tick/64 · 炼金炉贴图与 lore · 三器改鎏金进度条 · 石砖改外侧包裹唇 · 三器配方改注魔 · 新方块玄枢净界石 |
| 12.53 | 炎炉配方「看不见」的真因 · 三器配方瘦身 · 台座符环外扩 · 玄枢镐注魔 · 卦石催炉 · 书里补卦石一页 |
| 12.54 | 修「加速后合成不了」（不稳值重标定）· 动画一起快 · 镐子独立条目 · 三器改神秘锭 · 卦石加速写进书里 · 配方需求统一模板 + 颜色加粗 |
| 12.55 | 魔导书乱码的真因 · 首面改平实语言 · 删掉卦石催炉 · 镐子改挂乾卦 · 删总表页 · 八方净界阵 + 9 区块 |
| 12.56 | 贴图统一 16×16 · 魔晶簇重做 · 注魔催炉重做 · 书目改白话 · 净界阵法杖开阵 + 激活外观 |
| 12.57 | 净界阵九块齐亮（卦位石接 active 状态）· 玄枢法杖杖柄重绘（螺旋 + 烘焙明暗） |
| 12.58 | 净界阵改祭坛式建模（九块齐换形）· 玄枢魔晶「乱码」的真因（旋转角白名单）· 玄枢法杖整体提亮 |
| 12.59 | 净界阵去柱去环只留尖晶 + 贴图符文化 · 魔道书净界阵改挂坤卦、组装步骤写清 · 玄枢法杖明暗对调（淡紫金为主） |
| 12.60 | 净界阵尖晶只留阵心（加大加华丽）· 法杖核心:传送（新部件 + 记点传送 + 配方）· 杖柄三条紫纹、图标还原上一版 · 魔晶紧凑不对称 · 镐/台座配方 |
| 12.61 | 法杖核心:传送改回正统核心（基于 focus_cosmos 重做）· 三台工作方块 UI AE2 化 · 三核心创造栏相邻 · 魔晶更大更密更平滑 · 杖柄图标描边深紫 |
| 12.62 | 杖柄深紫主体 + 淡紫粉点缀 · 魔晶真尖头水晶（弃方块塔，改交叉面片）· 三台 UI 照 AE2 压印器重做 |
| 12.63 | 三台 UI 进度改符文青箭 · 杖柄暗紫为主 · 净界石贴图 + 阵心晶尖加大 · 传送核心星辰化 · 魔晶斜张晶叶·鎏金深紫 · 魔导书超页清零（根因：每个 <BR> 多一行） |
| 12.64 | 核心:传送贴图回归圆形 · 玄枢魔晶真几何建模（照本体晶簇结构）· 进度箭青色越界修正 · 杖柄图标借四象轮廓 · 三核心配方要素 128→64 · 魔导书删末句 |
| 12.65 | 玄枢魔晶改直立水晶柱（等宽柱身 + 菱形尖）· 玄枢炼金炉传输配置面板对齐另两台（只统一标题/色例）· 三台机器注魔不稳值统一为 6 · 参考料挪进 .none/ 后修构建（路径 + javac 编码） |
| 12.66 | 魔晶改收拢加粗尖头 · 镐去 lore 改名「八卦开山辟地镐」· 阿卡西改盘起卷轴 · 新增玄枢节点稳定器 |
| 12.67 | 玄枢节点稳定器返工：外观改引本体高级节点稳定器 + 可承节点换能器充能成充能节点 · 玄枢魔晶世界模型全部重画并整体放大 |
| 12.68 | 玄枢节点稳定器崩溃修复（桥类迁出 mixin 包）· 模型复制自包含（OBJ + 贴图，自写方块/物品/blockstate，不做动画） |
| 12.69 | 玄枢充能器（单方块充能灵气节点，全面强于本体装配）· 玄枢镐 / 玄枢锄（贴图 1.png / 2.png，档位高于下界合金） |
| 12.70 | 阿卡西记录改盘起来的卷轴 · 魔晶清零从零重做写实水晶簇 · 两把镐子换贴图 · 镐/锄改八卦镐注魔升级 + 书中牵线 · 稳定器改单方块自生效 |
| 12.71 | 锄头换 axe.png 贴图（柄末端收细）· 水晶簇去像素风 · 锄头配方改锭为钻石锄 · 玄枢节点稳定器整项删除 · 充能器改青铜鼎模型 + 充能后要素随时间涨至 64/t |
| 12.72 | 灵气节点不再随要素数值膨胀（视觉钳制 Mixin）· 水晶簇立体化返工（面朝向 + 六棱色调）· 充能器改鎏金黑金星辰鼎 |
| 12.73 | 水晶去底座 + 大小分档 · 充能器去粒子 + 去横纹 · 书目镐锄挪位 · 创造栏新物前置 · 锄头贴图改深紫星辰 |
| 12.74 | 鼎内外加图案（扉棱 + 星钉 + 双色内壁）· 水晶簇14 枝 / 16 套配色 · 三个法杖核心补上扣魔 · 充能器提速到 4 秒满 64/t 并删掉死状态 |
| 12.75 | 水晶贴图回 16×16 去渐变（照原版紫水晶重做）· 整丛收拢 · 鼎放大 1.10 · 阿卡西改卷起来的卷轴 + 中段绑带 · 净界石改古代石坛 |
| 12.76 | 创造页签：充能器挪到三台工作方块之后 · 阿卡西记录改放平的卷轴（用户素材旋转 90° + 提亮金色） |
| 12.77 | 阿卡西记录再往左转 45°（绶带紫加浓）· 新增授业卷轴（右键补全全部研究笔记）· 净界石加到 26 件 element |
| 12.78 | 阿卡西 / 授业卷轴改名（玄幻长名）· 阿卡西贴图摆正 + 居中 |
| 12.79 | 净界石退回单方块 · 镐锄改名 + 立坎卦 · 书中新增玄枢充能器（配方去魔晶）· 玄枢锭中心改下界之星 · 焚天镐三用 + 隧道掘 + 时运 XVI |
| 12.80 | 成阵态还原 · 净界石改名九天净界玉 · 焚天镐改镐斧铲（提示只报模式名）· 水晶簇中宽两头尖 · 充能器修名、挪位、串链、加巽卦与两枚高级节点稳定器 |
| 12.81 | 水晶簇中腰再放宽 · 整丛放大收拢 · 减到十枝 · 镇海锄做成农事神器（9×9 耕地 + 潜行放水 + 手持催熟）· 书里镐锄改成「功能 → 合成表 → 背景故事」 |
| 12.82 | 创造栏碎片换位 · 净界石/充能器三卦齐立 + 卦象要素 · 水晶簇中腰收回、轮廓放平 · 研究树巽→三器→充能器 · 死代码全项目清零 |
| 12.83 | 焚天镐/镇海锄注魔加八卦要素（各 32） · 水晶簇模型整丛重生成（9 环余弦收分 + 扭转 + 平滑法线） · 编译 |
| 12.84 | 水晶簇回退 batch 78 重生成，只把几枝弄歪一点 · 重新编译 |
| 12.85 | 魔导书研究树挪位：卦石改挂「八卦之地气」之下 · 三法术核心改挂「艮卦」之下 |
| 12.86 | 玄枢台座在研究树里独立成条 · 台座多方块四角连台改平滑丝带 · 台座新增吸纳罐中要素、卦石改走「卦象地形 + 要素」合成（坩埚配方作废）· 四象杖容量 1949、朱雀杖 918 · 书中新增「法杖之装配」条目 |
| 12.87 | 玄枢洞虚灵目贴图重绘：原版像素画风 · 斜置八卦镜 + 金链子 |
| 12.88 | 新增玄枢灵气节点修改器（司南）：右键缸中节点改易要素与类型 · 新增天域之华 ×6 |
| 12.89 | 节点修改器改方块版：司南台上置缸中节点，放入物品后持法杖右键运转（手持版全删） |
| 12.90 | dev 树切 NeoForge 20741（兼容性核对：全过）· 司南补底盖防透视 + 放大 1.35 · 节点修改器 UI 两处修复 · 新增玄枢奥术台（附魔升级：520 魔力换一级附魔） |
| 12.91 | 奥术台星辰贴图 + 复刻翻动的书 + item 模型 · FOCUS_TELEPORT 重绘（星辰 + 圆盘治卡模）· 司南换玉琮 · 两屏 UI 亮色化 · 天域之华六件全删，改用地髓魅华 |
| 12.92 | FOCUS_TELEPORT 贴图回退上版 · 奥术台提亮 + 书全黑的根因修复 · 玉琮 item 图标单画（治花屏） · 两件新物 lore 全删 |
| 12.93 | 玉琮 OBJ vt bug 修复（手持花屏根因） · 手持恢复 3D 模型 · 两屏 UI 恢复共享 XuanshuGui 常量 |
| 12.94 | 玉琮方块黑块（OBJ 缺 vn）· 书黑 + 抽搐根治 · 两槽物品改为存 BE · 全局 UI 提亮 |
| 12.95 | 玉琮 winding 全翻外（黑影根因） · 动画书退场改静态元素书（黑 + 闪烁 + 乱码一次清） · 两屏 UI 照炎炉骨架重写 |
| 12.96 | 奥术台删书立星核、改双槽（物品 + 染灵锭） · 香炉替玉琮 · mayPlace 全放开（治「放不进」） |
| 12.97 | 香炉全黑 + 透视两个落盘根因（OBJ 漏写 vn + MTL 的 map_Kd 写错）· 奥术台删柱立漂浮星 + 两槽改左右 + 面板补高 · 升级附魔改玄枢锭 ×1 + 法杖六元各 129 厘 · 节点修改器两槽改天域之华 + 玄枢锭（只加要素）· focus_teleport 居中 + 闪烁像素 |
| 12.98 | 奥术台删掉「运转消耗」整行 + 「129 点」单位传错（厘 → 整 Vis）· 节点修改器改纯方块（OBJ 整套退役） · 两槽收任意天华 + 玄枢锭 · 节点随机加一门要素 |
| 12.99 | 三个法核价码逐个核对（挖掘 1 点 → 各 10 点）· 节点修改器「天域之华不是安瓿」· 效果改先补六元、之后一直抽奖 |
| 12.100 | 死代码清零（三个扫描器全 0）· 注灵台改仿附魔台的台形（12/16 高 + 台上香炉）· 改名「玄枢注灵台」 · 要素不足提示统一成一句 · 法杖激活也要六元各 129 点 |
| 12.101 | 新增 祛魔书：与带附魔的物品合成，附魔拓成一册附魔书、原物附魔清零而原物退回 |
| 12.102 | 祛魔书改原版附魔书贴图（红带→粉紫）· 镐的时运 XVI 改成成品即附魔（去掉每 tick 补写）· 灵目改戒指栏 · 注灵台去香炉 + 情报两行居中 · 新增玄枢沟灵核心（两台改注魔 + 卦石门禁 离·兑·乾）· 两台与充能器的卦石 lore |
| 12.103 | 仙家化（祛魔书 64×64 / 注灵台玉金 / 镐图 / 卦石与配方） |
| 12.104 | 卦石 lore / 书页体例 / 镐改名换图 / 灵目 / 注灵台压暗 |
| 12.105 | 注灵台青玉 / 分类图标 / 体积审计 |
| 12.106 | 删研究书背景与卦说 / 核心进书 / 星盘 / 星斗十三座 |
| 12.107 | 卦说回滚 / 只留星盘 / 星盘成阵 |
| 12.108 | 要素图标 / 镐攻速 / 三条研究条目 / 星盘与星斗阵重做 |
| 12.109 | 星斗阵改 3×4 / 台高 9 与 11 / 金线光点 / 卦象图标还原 |
| 12.110 | 星盘改真台阶 / 六张贴图 / 金线改气脉丝带 |
| 12.111 | 座侧图案 / 阵眼 / 浮空大太极 |
| 12.112 | 整阵一张星图 / 默认方块改阵眼 |
| 12.113 | 星盘 成型只成一半的真凶 / 侧面纹样画错了半边 |
| 12.114 | 星盘 去太极 / 七星六星互切 / 中间两格置物 / 拆一格全阵失效 |
| 12.115 | 星盘 两组星都铺满 3×4 / 盘心圆徽删除 / 金线改走星座连线 |
| 12.116 | 星盘 金线再抬高 3 像素 |
| 12.117 | 星盘 星位铺满十二格 / 潜行切换 / 星象合成管线 |
| 12.118 | 星盘 wispy 回退 / 7×8 特效圈 + 竖光提示 / 金线再抬高 |
| 12.119 | 星盘 只显示当前这一斗 / 放物分斗 / 金线抬到 1.75 |
| 12.120 | 星盘 星位对齐 / 竖光加长渐隐 / 坩埚配方改熔炉翻倍 / 未成型顶面换星图 |
| 12.121 | 星盘 盘面改回原来那张，供物落到星上 |
| 12.122 | 星盘 清预览 / 修物品与星名错位 / 顶面改星空 |
| 12.123 | 星盘 星位改由图案决定（点击才对得上）/ 竖光加深 / 双倍修好 |
| 12.124 | 星盘 熔炉翻倍的最后一根刺 / 竖光再浓 |
| 12.125 | 星盘 翻倍的真凶（Long.MIN_VALUE 溢出）/ 星点换色 / 光晕压缩 |
| 12.126 | 非 16×16 贴图清点 / 两处压缩 / 看到 20744 新 API |
| 12.127 | 升级到本体 20744（focus 重构） |
| 12.128 | OBJ 变大的原因与复原 |
| 12.129 | 修 20744 法核档案报错 / 星图与顶面压到 16 / 清旧备份 |
| 12.130 | 星图回 32 / 竖光更长更浓 / 灵目换紫色 32 图 / 翻倍加诊断日志 |
| 12.131 | 翻倍溢出修好 / 玄枢锭改奥术工作台 / 书里只留四兽锭 |
| 12.132 | 绿线补上（含本体工作台）/ 红绿线只在工作时出现 |
| 12.133 | 未烬玄枢锭 / 绿线改挂坩埚 / 两台机器南斗前置 / 银白线 |
| 12.134 | 重画十字星 / 书里加星辰合成 |
| 12.135 | 书里位置再挪 / 玄枢锭只走坩埚 |
| 12.136 | 书页简化 / 条目挪远 / 未烬锭接回奥术台 / 按铁锭光影重画两枚锭 |
| 12.137 | 两枚锭改「亮粉紫 · 宇宙」/ 坩埚加诊断 |
| 12.138 | 删诊断 / 以朱雀锭为底重画 / 书里给两枚锭各加一条条目 |
| 12.139 | 底板换成青龙锭 |
| 12.140 | 严格色相平移（光影一点不动） |
| 12.141 | 玄枢复刻台（复制机）+ 玄枢锭配方换料 |
| 12.142 | 复刻台南斗六星前置（紫线）+ 引导线加高加浓 + 玄枢锭配方改回 |
| 12.143 | JEI/EMI 星象条件标注 + 复刻台改形带星辰 + 纲领更名 |
| 12.144 | 引导线换 AFTER_LEVEL 通道 + 复刻台侧配自动 IO + 两台改模 + 星辰配方两条 |
| 12.145 | JEI/EMI 星象注释去分隔符 + 注释改挂玄枢锭 + 侧配页签图标 |
| 12.146 | 注释去「各供」+ 两台辰星重做（平滑四芒星光） |
| 12.147 | 辰星放大 + 八芒星光重绘（奥术台 / 复刻台两颗都动） |
| 12.148 | 换引导线特效 + 星象说明迁 lore + 星芒去圆 + 双台贴图重做 |
| 12.149 | 本体升 20746（API 口径推移）+ 引导线换本体原生光束、加长、从方块正中起 |
| 12.150 | 引导线改回广告牌 + 界面字色去黑 + 台顶星放大 + 魔导书全量改写成三段式 + 研究树重排 |
| 12.151 | 星盘补镐标签 + 机器 16 堆叠 + 星位核供物 + 树连线蓝色加粗 + 星辰多方块页 |
| 12.152 | 蓝色回到文本 + 星位改供玄枢锭 + 星盘换碎片解死锁 + 整体再紧一档 |
| 12.153 | 改名玄枢学 + 引导线换金加深 + 两台界面去投影 + 复刻台改供龙蛋 + 注灵台鎏金 + 文案纠语序 |
| 12.154 | 模组名回到默认 + 侧栏改「玄枢学」+ 口语化措辞清理 + 核心三点换位 |
| 12.155 | 卦石那句全书标蓝 + 选型页与配方页「一行一个内容」 |
| 12.156 | 卦石下三核心改 90°/45°/0° 扇面 · 灵目改 32×32 像素戒指 · 注灵台改古代鎏金（去抖动）· README 与 ROADMAP 整合 |
| 12.157 | 注灵台模型删实心台身、与 Java 碰撞箱逐块对齐 + 贴图按九盒分区重画 · 灵目贴图回退、改名、加「单次伤害 ≤ 最大生命 10%」· 清理备份 |
| 12.167 | 枫树三件套（枫木原木 / 枫树叶 / 枫树树苗，红偏橘叶）：树形照原版大型橡树现构现种 + 世界生成（森林群系 add_features）· 依赖口径推到本体 20748（20748 无 runtime jar，暂借 20744 编译 mixin 目标类） |
| 12.168 | 依赖切回真 20748（用户补放完整运行时 jar）· 新增枫木木板（1 原木 → 4 木板）· 樱花林（cherry_grove）枫树加量到每区块 6 棵 · 创造栏调整（树苗移到戒指后、原木/树叶留卦石前） |
| 12.169 | 原木贴图重做银树形制深色化（+ 木板跟着压深、地图色换深陶灰）· 树叶换原版樱花树叶结构（粉→红、绿点缀→橘点缀）· 树苗换原版橡树树苗结构（叶红橘化、茎深银化）· **修枫木板合成 bug（ingredients 裸字符串 → `{"item": …}` 对象）** · 创造栏木板移到枫树叶后面 |
| 12.170 | 原木改**直接用本体银树原木像素结构**改色（修「顶面四角透明」bug：银树顶面 256/256 全不透明，自绘圆盘版四角漏空）· 树叶红色 ramp 推**鲜红**（去粉感提饱和）· 木板改用**原版橡木木板**像素结构改深银 · 创造栏定为 原木 → 木板 → 树苗 → 树叶 · 世界生成 JSON 全量对照 codec 审计（无问题） |
| 12.171 | 新增 `tools/preview_maple_tree.py`（**把原版 fancy oak 算法移植成 Python 离线渲染树形**，正立面 + 顶视图，回答「树形是不是代码写的」）· 原木 palette 由银灰棕推**褐色**（R 提 G/B 压，地图色换 TERRACOTTA_BROWN / COLOR_BROWN）· 木板按「干净一点」改**手绘干净版**（四道横板 + 板缝 + 一道木纹 + 右侧亮棱，去掉量化噪声） |
| 12.172 | 树形**固定最大高度**（`FancyTrunkPlacer(3,11,0)` → `(14,0,0)`，树干恒定 14 格，Java 与 worldgen JSON 双路同步）· 原木贴图颜色**学原版橡木**（结构保持银树：侧面 6 档照搬 oak_log、顶面取 oak_log_top 年轮色）· 木板**直接用原版橡木木板**（oak_planks 结构 + 7 档棕黄全照搬）· 树苗茎改橡木棕 · 原版生成方式审计（原版 fancy_oak.json 权威对照，逐字段一致） |
| 12.173 | 贴图三调：树叶**再鲜红**（G/B 压低 R 更纯）· 原木**压深褐**（橡木棕黄整体压暗，色相偏褐红）· 木板**整体深一点、光线纹路仿橡木**（保留橡木 7 档明暗层次只压暗色值）· 树苗茎跟原木压深褐 · **大小分布盘点**（jar 701 KB：.class 892 KB / .json 409 KB / .png 99 KB / .obj 44 KB；贴图 144 张共 0.30 MB 显存、class 137 个，无异常大头） |
| 12.174 | **玄枢虚空采矿机**（新多方块）：采矿方块（`xuanshu_void_mining`）墨玉鎏金青蓝贴图（`gen_void_mining.py`，含成型态充能贴图）· 结构 3×3 底座 + 中心上方沟灵核心 + 核心上方采矿方块，`XuanshuSpiritCoreBlock` 法杖右键 `tryForm` / 潜行右键 `dissolve` · `XuanshuVoidMiningBlock`（`FORMED` 属性、`onRemove` 破格散架、`findCore`/`anchorOf` 反推核心）+ BE（锚点 `corePos`、每 100 tick 产一组 1~3 矿、9 格栏）+ Menu（9 产出槽 + 背包、DATA_COUNT=4）+ Screen（`XuanshuGui` AE 亮色 + 竖排进度条）· 矿物来源 `#c:ores` 通用标签（`TagKey.create(Registries.ITEM, "c:ores")`，含下界合金，不单独适配模组） |
| 12.175 | ① 成型变**古代象征物「八卦祭坛」**：新增 `ROLE` 枚举属性（center/edge 四向/corner 四角/top），`tryForm` 按位置写结构位、`dissolve` 复位；贴图 `gen_altar.py`——48×48 整画鎏金大圆（双环 + 八向辐条 + 八卦点 + 坛心太极印）后切九片（corner/edge/center 三张 + blockstate y 旋转），玉坛侧砖 `altar_side`，顶盖换 `altar_finial` JSON elements **塔刹**（金盘 + 玉琮身 + 金环 + 玉柱 + 金顶珠五层缩进）；blockstate 改 **multipart**（formed=false 一条收普通方块 + formed=true×role 十条）；删孤儿模型 `xuanshu_void_mining_formed`。② 魔导书 `trim_guidebook2.py`：卦石句拆粗（`§9§l卦石：…§r` 整句回正文、只 `§9§l<卦名>§r` 蓝粗，zh 26 条 / en 20 条）· 星辰页星座与激活物 `§5§l` 蓝紫粗（zh/en 各 6 处）· 剥配方页 `§8` 灰色补充段。③ 枫木原木结构从银树换成**原版橡木原木真实字符矩阵**（`oak_log` 6 档 / `oak_log_top` 9 档，从 1.21.1 client jar 量化提取），修「银树结构 + 橡木色」明暗错位的不协调，配色仍深褐 |
| 12.177 | ① 采矿机成型模型**改用 OBJ**：`gen_altar_obj.py` 程序化生成 `altar_globe.obj`（天球浑仪：二十面体能量球 r=0.22 + 赤道环/两道正交竖环 r=0.42 各 24 段 + 八棱极轴，316 顶点）与 `altar_base.obj`（八边形圆台 r_bot 0.46→r_top 0.38 高 0.92）；模型 json 走 `neoforge:obj` loader（对齐 xuanshu_charger 惯例），mtl `map_Kd` 复用 `altar_finial_gold` 贴图；blockstate：role=top→`altar_globe`、role=center→`altar_base`；删孤儿模型 altar_finial/altar_center；修 `ring_verts` 的 `base` 索引 bug（原所有环面都引用第一段顶点，环丢失）。② 魔导书 chrono 乱码修复：FOCUS_CHRONO 是 37 个研究条目里唯一缺 `tc.research_name/text` 的（游戏显示原始 key），中英补齐「烛龙衔时 / Torch-Dragon Timehold」；`focus_chrono_lore` 空白页补正常描述（其余 35 个空 `_lore` 是既有精简惯例，不动） |
| 12.178 | 采矿机成型改 **OBJ 三级密檐方塔**（密檐 / 塔刹 / 台基两级收分）· 查清上一批 obj 不显示的真因：`usemtl bronze` 与 `newmtl altar_base` 不同名，`getMaterial` 抛异常、mesh 整个不画，且全文件 0 个 `vt`/`vn` · 四个 obj 分派十一格（外圈 8 格共用一个）· 核心方块加 `FORMED` 否则塔腰缺一格 · 新增 `preview_obj_iso.py`（等距 + 正视）与 `check_obj_models.py`（渲染链路静态校验）|
| 12.179 | ① 采矿机成型模型从**四个分块 obj** 改成**单一整体 OBJ**（三层楼阁式方塔，横跨 3×3×3）：只有中心格渲染 `tower.obj`，其余十格用 `tower_empty`（只有 particle、无 elements）；`tower.obj` 坐标 x/z −1..2、y 0..3，一格承载三格 —— 砖缝与檐口不再被格界切断。形制：台明（47px，铺满 3×3）+ 基座（24px）+ 一层 18px 带四面券门与转角倚柱 + 檐 25px + 二层 14px + 檐 17.5px + 三层 11px 带四面小窗 + 顶檐 13.5px + 塔刹（覆钵 / 三重相轮 / 宝珠），共 283 面 / 1132 顶点。② 烛龙衔时补齐法核规格：新建注魔配方（时钟 + 红石 4 + 水银 4 + 八卦各 64）、卦象门禁 `gen` 一条、研究页注魔图与合成说明两页、中英 `bagua.recipe.focus_chrono`。③ 四枚法核改**锯齿父子链**（震卦→守护→烛龙→宇宙→传送，坐标 1,4 / 2,3 / 3,4 / 4,2），全树复核：线交叉 0、核心区贴线 0、同源夹角全部达标。④ 清理 17 个一次性脚本与 7 张调试渲染图、整个 `tools/work/` |
| 12.180 | ① 塔贴图整套重画为**古风皇宫塔**（汉白玉台基 / 朱红塔身 / 青绿斗拱 / 黄琉璃瓦 / 鎏金刹，256×256 十分区）；② UV 改**世界坐标 1:1**（1 世界像素 = 1 纹素，图案跨盒连续，门缝落在塔心 `u=24`），修「整块拉伸」造成的错位；③ 檐改**下宽上窄三折**（瓦口薄板 + 檐口 + 屋面），斗拱带外挑 0.8px 才看得见；④ 采矿机自动化与加速全部挂到**沟灵核心**那一格：`autoOutput` 推 6 面容器、`getExternalItems` 对外给表、`TemporalCore.rateAt` 轮询倍率（核心无方块实体，故走 polled 通道）、产出按倍率成倍推进；⑤ 四枚法核改**同挂艮卦**的扇形（(5,2)/(4,3)/(3,4)/(2,5)，夹角 19.7/22.6/19.7） |
| 12.181 | ① 塔**整体换风格**：三层朱红方塔 → 八棱「玄枢观测塔」，配色改鎏金 / 白瓷 / 拉丝银，红墙、斗拱、琉璃瓦、板门全部删除；② 底座不再是 47px 方砖台，改**八角悬浮盘**（切角、放射方框徽记、几何金边），三级台阶压成一级；③ 新增 `Obj.prism / band / octahedron` 三个几何原语，侧面 UV 改**棱面归一化**（一棱面 = 一整格 8×8 面板），顶面改**平面归一化**（徽记 / 均匀金 / 均匀银专用分区）；④ 修三处贴图错：顶面用带竖缝的分区被画成虚线网格、平面图案的 45° 边被量化成梳齿、横缝判断顺序导致每个棱面断 2/8 变成一排小窗口；⑤ 绕序改成**按几何外法线自动纠正**（232 面 0 反） |
| 12.182 | ① 成型模型**整个换成悬浮球**：八棱观测塔连同 `tower.obj / tower.mtl / tower_stone.png / gen_tower_obj.py` 全部删除，改「玄枢星枢球」——八角盘 + 收细托座 + 发光小台 + **白瓷球**（32 段 × 11 段纬，球心 30.6px、半径 15.0px，与托座留 9.2px 空隙）；② 配色仍是鎏金 / 白瓷 / 拉丝银，金色只出现在**赤道带 + 两极帽 + 极轴 + 台沿金圈**；③ 新增 `Obj.sphere()` 经纬球原语（三角扇极点 + 顶点 UV 展开）；④ 球面**不画经纬线**（会读成铁丝球），球感全靠 `shade_quads` 的面法线光照；⑤ 修发光台白杠（平面分区带 `lv % 16` 横线 → 顶面上变成两道贯穿白线）；⑥ 倍率悬浮字高度 3.1 → 2.35 格、`LABEL_TOWER_DY` 改名 `LABEL_ORB_DY`，Java 注释里的「塔」全部改成「装置 / 球」 |
| 12.183 | （代码已落、文档缺，第 184 批补记）① 九格底座换回实体方块（第 182 批全空模型会看穿底座），按 `role` 三套贴图 + `cube_bottom_top`；② 球面加纹：球贴图 48→96，赤道金带 + 上下八卦爻纹带，经缝只留腰带；③ 采矿方块未成型态并色为「银白为体、鎏金为框、暗窗为心」 |
| 12.184 | ① **3×3 底座整层删除**，机器从十一格变两格（核心 + 顶盖），锚点从中心底座格翻到顶盖格，`ROLE` 属性删除、blockstate 只剩未成型 / 成型两变种，核心 blockstate 回单模型（基座自渲染）；删 3 个底座模型 + `orb_empty` + 7 张底座贴图；② OBJ 挂点移到顶盖格（y=0 = 核心顶面）+ **古风微调**：主台改上宽下窄出檐（14.0→15.0）、顶面外圈改连珠纹、托座改莲瓣柱身、球顶金针改相轮盘 + 针；③ 采矿方块贴图按**机械修仙 · 鎏金白银**重画：拉丝面板 + 机械分板缝 + 阵纹灵窗（暗窗换暖白灵气 + 回纹阵眼 + L 形回路）+ 铆钉，抖动归零；④ lore 三条 + incomplete 提示精简成清楚直白的正常语言（中英）；⑤ `orphan_scan.py` 收编三类视野外引用（法杖杆帽 / focus 饰纹 / 预览临时件，坑 #54 收口）；修 `gen_orb_obj.py` PNG 写入器的 str/bytes 潜伏 bug；清 2 个历史遗留未用 import |
| 12.185 | ① **热修核心乱码**（第 184 批回归）：blockstate 不能写裸 `{"model": …}`（坑 #67），两个 blockstate 改用 **variants 显式列全状态**，改由 `gen_orb_obj.py` 生成；② **祭坛直接立在地面上**：OBJ 挂点移回核心格（y=0 = 地面），新增**须弥座基座**（暗石底脚 / 银白座身带回纹带与竖砌缝 / 暗银束腰翻沿，0..16px 占满核心格），核心成型态改渲染 orb（基座覆盖立方体）、顶盖改 `orb_empty`；底层**封死不透视**（底面整体抬 0.03px 避共面 z-fight）；全模型 616 面；删 `preview_void_machine.py`（拼接用途没了）；③ 采矿方块贴图再重画：顶面**圆盘推倒**，改方形机械面板（鎏金方框四档包边 + 拉丝面板 + 下沉方窗阵纹灵窗 + 四角十字螺钉），侧面圆铆钉换十字螺钉 |
| 12.186 | ① **星象仪**（新装饰方块，`astrolabe`）：第 182~185 批那套「悬浮八卦球」模型整体搬出来单独立方，`gen_orb_obj.py` → `gen_astrolabe_obj.py`，**须弥座整圈收窄**（底脚边心距 12.6 → 8.4、座身 13.9/13.3 → 9.3/8.9、主台 15.0 → 10.0），球与相轮刹不动；新增 blockstate / obj 模型 / 256 图集 / 16×16 物品图标 / 战利品表 / 镐采标签，方块只做形状与注册（无 BE、无功能）。② **虚空采矿机推倒重来**：旧的「沟灵核心 + 顶盖」两格结构、法杖成型、`coreFor` / `tryForm` / `dissolve` / `selfHeal` 全删，沟灵核心退回纯装饰方块；新结构 `VoidMiningStructure`（**原版石英 112 格**：11×11 地盘圈 + 八根石英柱 + 冠环 + 四向外伸臂与八条支链），结构齐 = 自动激活（每秒自检，`FORMED` 只改亮度与产出）；界面加两枚按钮（一键搭建 / 一键收回，走 `clickMenuButton`），材料从背包结算、缺料点名、背包满了掉脚下；自动输出改挂机器自身四面；烛龙衔时改「点机器即点它自己」、悬浮字抬到华盖之上（6.2 格）。③ 枫叶色板整条从粉红推到**鲜红**（最高一档 G/B 由 120/108 压到 80/66），树苗四档同步偏红。 |
| 12.187 | ① **矿机环坛重做形制**：用户「搭建位子在矿机后面 / 整体圆形 / 再高一点 / 一层层圆环随高度变大 / 带一点特效」——方块加 `FACING`（放置时正对玩家），结构整体**退到机器背面**、圆心离机器 6 格；八层圆环半径 3→3→4→4→5→5→6→6，**厚圈（平滑石英块）与薄圈（下半台阶）交替**，每层四正位立石英柱当竖骨，顶上一圈薄圈收口；共 **232 格**（平滑石英块 100 / 台阶 100 / 石英柱 32）、总高 8 格；激活时沿每一圈炸一圈星火、运转时客户端每 2 tick 放绕圈星火与上升星火。② **星象仪**：底下的须弥座整个撤掉，改「薄底盘（边心距 8.4→4.3）+ 细柱（2.2→1.9）+ 小喇叭口」，上座一整套收细（主台 10.0→3.9、托座 6.6→1.8），球与相轮刹不动；**物品图标改吃 OBJ**（不再用 16×16 手绘），另存 `astrolabe_item.obj` 把几何下移到格心，删 `textures/item/astrolabe.png`。 |
| 12.188 | ① **矿机光坛瘦身 + 科技感**：用户「多方块不要方块那么多，科技感多一点」—— `VoidMiningStructure` 从 **232 格实心八层圆环** 改成 **139 格「四段弧 + 光杆骨架」**（降四成）：六层薄环（下半台阶）每层只留**四段弧**、段心在四个斜角，**每段的长短 = 该层半径**（覆盖率恒为 4/2π ≈ 64%，层层同一个节拍）；竖骨改 **末地烛**（自发光细柱）立在四正位，与弧错开 45°；坛心再竖一根 **7 格通顶光柱**（末地烛）当能量核；总高 7 格。特效同步升级：激活沿每层弧炸星火（撒点与结构同源）、运转中「螺旋扫光 + 中轴光核上升 + 机器→坛心吸能电火花」。② **星象仪底盘再压薄**：`FOOT` 0.87px → **0.38px**（≈2.4cm），薄到像一枚垫片；顺带补上底盘顶面（`top="cap"`）—— 以前被 0.9 的厚盘挡着看不出来，薄了就能从上方看进柱心（坑 #73）。 |
| 12.189 | ① **星象仪砍柱、缩进一格**：用户截图上框掉「鎏金底盘以下」整段（压沿 + 喇叭口 + 细柱 + 薄底盘），口径「框起来的部分不要，然后就一个方块的大小」—— `gen_astrolabe_obj.py` 删掉 FOOT / BODY / WAIST / SOCLE / SOCLE_TOP 五段几何与只服务它们的 5 张图集分区，**银白八角主台成了整座的新底面**（补 `bottom="cap"`，否则能从下方看穿）；剩下的主台 + 托座 + 球 + 相轮刹整体乘 **0.4415** 缩到 **y 0.001 ~ 0.999 格**（原来 3.34 格），宽 0.69 格；缩放走 `fit()` 只动顶点、UV 仍按设计稿像素算（贴图不重画、花纹不缩水）；**物品模型与方块模型合流** —— 删 `astrolabe_item.obj`，item json 直接读 `astrolabe.obj`，display scale 全套 ×2.4；`AstrolabeBlock` 碰撞箱改「底座 + 球」两段 `Shapes.or`。② **新增 `tools/gen_void_altar_plan.py`**（出生图 `tools/preview/void_altar_plan.png`）：把 `VoidMiningStructure.design()` 逐行复刻成 Python，出六层俯视施工图（机器在 dz=0、光坛朝 dz 变大方向长、每层标半径与格数、图例与 139 格统计），回答「我在超平坦建了建筑想变成矿机的建筑该怎么办」—— 结构是写死的 139 格，只能照图搭或一键搭建（坑 #76）。 |
| 12.190 | ① **矿机结构可读 schem（自定义形制）**：用户丢来 `mcedit.schem` 问「除去空气方块，可以变成矿机的多方块么」—— 新增 `StructureSchematic`（读 sponge v2/v3：gzip NBT → `Blocks.Palette` + `Blocks.Data` 的 varint 流 → `BlockState`，含带属性的方块），`VoidMiningStructure` 改成「**有 schem 用 schem、没有用内置光坛**」：`custom()` 懒加载只探一次、`parts / materials / height / layers / centerDistance / axis / toWorld / arcPointNear` 全部分叉，判定唯一分叉点是 `satisfied()`（自定义 = 「不是空气就算」）；锚点 = 非空气包围盒**底面正中**（`floor((min+max)/2), minY`），可用 `structure.properties` 的 `anchor=x,y,z` 覆盖；**自定义下禁用一键收回**（那是玩家自己的建筑）；界面搭建材清单换词。② **新增 `tools/schem_structure.py`**：离线读同一份 schem，打印格数 / 材料 / 逐层统计并出等轴 + 逐层图 `tools/preview/schem_structure.png`。 |
| 12.191 | ① **自定义形制解禁「一键收回」**：用户「现在有问题不能拆了，我希望就可以拆」—— `dismantle()` 拆成 `dismantleBuiltIn` / `dismantleCustom` 两条路，共用 `finishDismantle`（提示 + 音效）；自定义走「一格一格**按那一格现在是什么方块**掉」（`Block.getDrops(state, level, pos, null, player, tool)`），玻璃还玻璃、羊毛还羊毛；带方块实体的（箱子 / 漏斗 / 别的机器）跳过并报数；删 `void_mining.no_dismantle` 文案，新增 `gui.dismantle.custom` 悬停提示。② **粒子全删，改画蓝色竖光**：删 `clientTick` 三种粒子、`ringBurst`、客户端 ticker 与非必需的 `centerDistance()`；新增 `XuanshuVoidMiningRenderer`（方块实体渲染器）—— 四个斜角逐层问 `arcPointNear` 连成折线（半径逐层变大 → 贴着结构外缘往上爬），细芯（半宽 0.035 / α 0.92）+ 光晕（半宽 0.11 / α 0.16）两层、每段十字交叉两片四边形，坛心再加一根淡光柱；走 `RenderType.debugQuads()`（正常深度测试），亮度沿 y 走慢正弦 → 一层层往上亮。 |
| 12.192 | ① **形制从「读文件」改成「纯代码」**：用户「纯代码不但不更占，反而更省一点那就改成纯代码」—— 删 `StructureSchematic`（gzip/NBT/varint/调色板/`structure.properties` 锚点覆盖整条链，410 行），新增 **`VoidAltarBlueprint`**：一座 **8×8 / 高 13 格 / 255 格 / 9 种方块**的塔被编译成 `String[19] STATES` + `int[1020] CELLS` 两组常量，类初始化时展开成 `List<Part>`；运行时零文件 IO、零 NBT 依赖。`VoidMiningStructure` 随之瘦身：139 格光坛那套 `RADII / ring / arc / nearest / design / rotate / PARTS_BY_DIRECTION / ARC_LAYERS / toWorld / offsetX / offsetZ / arcPointNear / layers / back / isCustom` 全删，只剩「一塔一形制」；判定统一成 `satisfied = !isAir()`，收回统一走 `Block.getDrops`。② **光效改成「集结」式**：用户「蓝色的线我希望是集结部队这种长一点竖直的，下面稍微高一点的位子一个雾状的圆」—— `XuanshuVoidMiningRenderer` 重写：塔心**一根竖直光柱**（y 1.3 → 24，高出塔顶 11 格，半宽 0.30 → 0.11 收窄，`(1-u)^1.3` 淡出，慢呼吸）、**四角四根细竖光**（贴包围盒四角，y 0.25 → 18，逐段错相位）、**塔内三层雾环**（y 1.58，半径 1.62 / 2.02 / 2.42，各自不同转向与速度，薄圆柱面 + 内浓外淡的环面）；淡出系数改成静态表 `COLUMN_FADE` / `CORNER_FADE`，不再逐帧算 `pow`。③ **新增 `tools/gen_void_blueprint.py`**（schem → Java 常量，换建筑重跑即可）与 **`tools/preview_void_light.py`**（直接解析 `VoidAltarBlueprint.java` 出侧视 + 俯视光效图）；删 `gen_void_altar_plan.py` / `preview_void_beams.py`（139 格光坛的配套工具）；`gui.build.hint` / `gui.dismantle.hint` 两条死键删除，`build.custom` / `dismantle.custom` 文案改口径。 |
| 12.193 | ① **竖光挪到玻璃柱四棱 + 修抬头闪现**：用户「光束不要再四周，再淡蓝色玻璃上四处即可，现在的光束有问题，抬头会闪现」—— 四根竖光从建筑外角搬到**塔心 4×4 淡蓝玻璃壳四个内角的竖向空腔**里（`BEAM_OFFSET = 1.5`，那四列是全塔唯一 13 层全空的格子，整根不穿方块），塔心那根大光柱整根删除；**闪现的根因是渲染包围盒** —— 默认只包方块自身前后一格，27 格高的光柱一出盒子就被整条剔除，覆写 `getRenderBoundingBox` 给 17×31×17（坑 #85）。② **光效改「折跃」感、单色、更大、下方一团雾盘**：用户「类似折跃的感觉，整体再大一点然后单色，靠近下方是一个模糊的圆形」—— 竖光 y 0.30 → **27.0**（比 13 格高的塔翻一倍还多）、半宽 0.075 → 0.030、26 段、**芯与晕同色**（`0.36/0.76/1.00`，只靠 α 0.52 / 0.20 分层次）；雾从三层环改成 **y 1.35 / 半径 2.25 / 六层同心环**的一整盘，角向 `sin(3θ − t)` 起伏 + 0.16 高薄裙，外缘 `(1−t)^1.7` 淡到零。③ **界面三处**：一键收回那枚按钮的悬停框删除；进度条改成**先灌色、后画框**（`XuanshuGui.ring` 最后落笔），底边不再漏缝（坑 #87）；产出栏 3×3 → **3×4**、整体左移一格（`OUT_X` 62 → 44、新增 `OUT_COLS/OUT_ROWS`、`SLOT_COUNT` 9 → 12）。④ **新增方块「玄枢源质罐」**（`xuanshu_essentia_jar`）：模型与贴图全用 TC4 源质罐，效果同本体罐但**可收纳 16 种要素**、每种 **2048**；BE 同时实现 `EssentiaTransport` / `EssentiaSource` / `EssentiaMultiStoreView`，三能力在自家 `RegisterCapabilitiesEvent` 登记，注魔矩阵与玄枢基座抽取的**丝线动画**因此天然正常；液面改**鎏金三档渐变**不随要素变色（`solid` + `max(200, light)` + 10 段逐段插值 + 微光）；潜行空手倒空、挖起内容随物品走（**只在有量时**写组件，空罐才能叠 16）、比较器按装满程度分档；配方玻璃围金锭、`stacksTo(16)`、三条 lore。 |
| 12.194 | ① **矿机竖光改深蓝、从玻璃顶起、纺锤形**：用户「矿机线改为深蓝色，然后从玻璃上面出发。不要到底部还有。然后你先做一条竖线纯色，然后因为是玻璃开始往上，玻璃那格往上 3-4 格呈现慢慢变粗然后再变细，整体画出来清晰一点」—— 起点 `BEAM_BASE` 0.30 → **9.0**（塔心 4×4 玻璃壳的顶面；`dy=0…8` 才是完整一圈，`dy=9` 起收成小尖塔），塔里**一根光都没有**；顶端 27 → **30**；粗细改 `halfWidth()` 纺锤形（0.050 → 3.2 格 smoothstep 到 0.135 → `(1−t)^0.55` 收到 0.040），段数 26 → 40；颜色浅蓝 `0.36/0.76/1.00` → **深蓝 `0.11/0.20/0.78`**，芯 α 0.52 → 0.85、晕 3×0.20 → 2×0.22；亮度波 `0.55±0.45` → `0.925±0.075`（整根读作一条实线）；雾盘跟着抬到 y 9.02、半径 2.25 → 1.85、薄裙 0.16 → 0.14。② **源质罐五项整改**：`MAX_KINDS` 16 → **32**；液面从「按层竖渐变」改成**逐顶点斜向渐变**（`t = 0.55·高度 + 0.45·对角线`）；贴图改自家三张、**盖子换蓝紫**（`tools/gen_jar_lid.py` 按颜色筛出盖子像素换色相）；**Jade 能看要素表**（`compat/jade/` 两个类，调本体公开入口 `JadeAspectTooltip.append`）；**挖起保留要素**改成物品组件 `jar_essentia` + 战利品表 `copy_components` + 创造模式 `playerWillDestroy` 补刀（坑 #89 / #90）。 |
| 12.195 | ① **竖光整条推到重做：只留一条线 + 修「透视方块」**：用户「蓝色光束还是有问题，你全部是删了重新做，所有光束底座是蓝色玻璃，然后你就先生成一条竖线其他什么都不要，你现在竖线周围会透视方块，先把竖线做了」—— 四根斜角光 / 内芯 / 光晕 / 纺锤收放 / 亮度波 / 根部雾盘 / 外沿薄裙全部删净，剩**一条**钉在中轴的定宽竖线（`BEAM_HALF 0.09`、α=1、`0.11/0.20/0.78`），起点改用 `VoidMiningStructure.height()`（= 13.0，塔顶玻璃上表面）、顶端 36.0；**「透视」的根因不是形状而是写深度**：方块实体在半透明地形之前渲染，`debugQuads()` 的 `COLOR_DEPTH_WRITE` 会把玻璃 / 水挡掉，换成 `debugStructureQuads()`（`COLOR_WRITE`）即解（坑 #93）；渲染包围盒水平改用 `bounds()`，不再写死 ±8。② **源质罐容量 2048 → 1024**（用户「可装 32 种不同要素上限 1024」），lore 中英同步改写；`fillRatio()` 从「总量 / CAPACITY」改成「最满那种 / CAPACITY」，液面不再被别的要素抬高。③ **清死代码**：删 `XuanshuEssentiaJarBlockEntity.kindCount()`（Jade 改读 `contentsView()` 之后没人用）、`VoidMiningStructure.height()/bounds()` 由「192 批之后一直没人调用」改为**真正拿去做起点与包围盒**；全量复核：贴图 152/152、模型 122/122、blockstate 28/28 全部有引用，无孤儿可删。 |
| 12.196 | **跟着本体换 API 20751（用户「更新了 api 再 .none 种」）**：`gradle.properties` 的 `parent_api` / `parent_runtime` 指向 `.none/1.21.1-neoforge-20751-dev/`（20751 的运行时 jar 不再放 `tc/` 里，改在快照根目录，19 MB / 两千多个实现类），六个 mixin 目标类（`InfusionMatrixBlockEntity` / `ArcanePedestalBlockEntity` / `AuraNodeBlock` / `AuraNodeRenderer` / `TaintSpreadLogic` / `PlayerWarp`）在新 jar 上重新 `javap` 核对成员签名，一条没变。**javap 全量签名 diff 的结论是「只有新增、没有删除」**：新增 9 个类（`EssentiaStorageView`、`WandClientStages$BeamId`、`VisChange{Plan,Request,Result,Settlement}`、`WandVisStages{,$Change,$Committed}`），`WandApi` 多一族 `changeVis`；**唯一改行为的差异**是 `EssentiaMultiStoreView` 的父接口从 `AspectContainerView` 换成 `EssentiaStorageView`（四个全 default：`essentiaContents` / `essentiaCapacity` / `sharedEssentiaCapacity` / `essentiaFilter`），而本体给**所有** `BlockEntityType` 登记了 `EssentiaCapabilities.STORAGE` —— 罐子因此**自动拥有本体的 Jade 存储段**，于是把 194 批自写的 `compat/jade/` 两个类、`mods.toml` 的 jade 可选依赖、`deps/jade-1.21.1-neoforge-15.10.6.jar`、两条 `jade.*` 语言键**全部删除**（同一份信息画两遍只是重复，本体版还多给百分比）；罐子补两条 `essentiaCapacity() = 1024` 与 `sharedEssentiaCapacity() = false`。上游依赖重新回到只剩本体一个。 |
| 12.197 | ① **新增方块「玄枢传送门」`xuanshu_portal`**（用户「外形科技风类似星际争霸 2 神族折跃门，先注册 画好贴图」→ 「就当正常的方块，其他什么都不用，把贴图按要求设计，不需要 obj 就正常一个方块即可」）：`Block::new` 直接注册，**无方块实体 / 无朝向 / 无 OBJ**，blockstate 只有 `""`，模型一句 `minecraft:block/cube`；六面四张 **32×32** 贴图（north+south 门面、east+west 侧面装甲、up 八角封盖、down 底板），全部由新增的 `tools/gen_portal_tex.py` 程序化生成。弯角对每像素算**到贝塞尔中线的最短距离**定厚度、再由**跨宽坐标 u∈[-1,1]** 查色带（外缘鎏金 → 亮钢脊 → 暗钢 → 内缘青蓝），所以倒角与镶边严格跟着曲线走；顶盖/底板用切比雪夫距离场切八角与同心环。参数：镐子可采、抗爆 6.0、金属音、**亮度 7**（核在发光）、进 `#minecraft:mineable/pickaxe`、有战利品表、**暂无配方**（与星象仪同）。② **修一处误删**：196 批删 `compat/jade` 时把整个 `compat` 包一起 `rm -rf` 掉了，`NodeStateBridge` / `NodeStructureBridge` / `emi` 三个 / `jei` 四个共 9 个文件被牵连，`gradlew build` 立刻报「找不到符号 NodeStateBridge」（坑 #94），`git checkout -- src/main/java/skydeity/mysticpivot/compat` 全部恢复。③ **矿机光束从「十字」改成四根圆光**（用户上一条需求里没做完的那项：「仔细看是十字，我不希望是这样，用光线一样的圆，然后要 4 条，在四个玻璃上」）：十字来自 195 批那根柱的**两片零厚度交叉面片**，侧看扁、45° 看就是十字；改成绕轴 **12 段的圆柱侧面**（半径 0.09、α 0.9），**4 根**各站在塔顶 `dy = 8` 那层四块露顶淡蓝玻璃的上表面（`y = 9`），顶端仍 36；落点由新加的 `VoidMiningStructure.beamAnchors()` 从结构常量里按「从低往高第一个恰好四块露顶玻璃的层」算出来，不写死坐标，凑不出四块就退回「中轴一根」；`preview_void_light.py` 同步重画。 |
| 12.198 | ① **传送门长出界面**（用户「给传送门添加和矿机一样的 ui，只添加 ui 不设计功能，先把建筑搞定」）：`XuanshuPortalBlock` 加 `facing` / `formed` / 方块实体 / 右键开界面，亮度 7→12；`XuanshuPortalMenu` + `XuanshuPortalScreen` **一个版式数字都没重写**，槽位与按钮坐标直接引用 `XuanshuVoidMiningMenu` 那一套，12 格栏 + 竖排进度条 + 一键搭建 / 一键收回 + 激活点，与水晶塔的界面同构；进度条改量**工程量**（已立起 / 1099），`filled` 只走 `ContainerData` 不写 blockstate。② **建筑用代码描述**（`chuansongmen.schem` → `PortalGateBlueprint.java`）：新增 `tools/gen_portal_blueprint.py` 编译成 `STATES[36]` + `CELLS[4396]`，**23×25 / 高 18 / 1099 格 / 12 种方块**（錾制石英 519、金块 106、紫珀 104、石英楼梯 91、蓝玻璃 64、海晶灯 59、石英台阶 47、黑曜石 42、蓝玻璃板 38、石英柱 13、淡蓝玻璃板 13、**信标 3**），运行时零文件 IO。③ **「生成在方块后面」**（`PortalGateStructure`）：局部 z `+FRONT_SHIFT(13)` 让最前排落在方块背后第 1 格，再按 `facing` 绕 Y 转 0/90/180/270，四份布局静态缓存；新增 `tools/preview_portal_place.py` 用同一套公式离线复算并断言四朝向 `depth == 1`，出 `portal_place_iso.png` / `portal_place_top.png`。④ **施工流程抽出共用**：`StructureDeployer`（`Piece` + 算账 / 清场 / 落块 / 按格战利品表收回 / 缺料点名 / 带方块实体跳过），矿机与传送门共用，语言键靠 `prefix` 分开；`VoidMiningStructure` 只剩「偏移摊成世界坐标」，`itemOf` / `count` / `consume` 删除。⑤ **水晶塔改名**（「矿机名字改为玄枢虚空水晶塔」）：只改显示名与三条 lore（注册 id / 类名不动），`formed` / `stalled` 两条提示文案跟着改。⑥ **光柱根上添光苞**（「光束靠近底部位子画一个我红色一样的」）：`drawSpindle` 沿轴 12 层 × 一圈 16 段，半径 `R·sin(πt)`（上下收尖、中间鼓起），`LENS_RADIUS 0.55`（四根最近相距 1.41，再粗就互相穿插）/ 高 8 / α 0.55；`preview_void_light.py` 同步加画。 |
| 12.199 | ① **传送门升品阶 + 挪摆放**（用户「传送门和水晶塔一样史诗品质颜色、堆叠 16，然后生成的建筑往前一格、往下一格」）：`XUANSHU_PORTAL` 物品 `Rarity.RARE → EPIC`、`stacksTo(MACHINE_STACK)` = 16；`PortalGateStructure.FRONT_SHIFT` 由 `1 − minZ`(13) 改成 `0 − minZ`(12)、新增 `Y_SHIFT = −1`（`rotate()` 里 `y + Y_SHIFT`）—— 广场前沿落在方块**自己那一列**、整体低一格，方块像压在广场尖上的钥石；`preview_portal_place.py` 改判据（深度 0 / 前沿 `y ≤ −1` / `(0,0,0)` 空），并改成**直接解析 `PortalGateBlueprint.java`**（schem 已删，脚本不再依赖它）。② **创造栏挨着摆 + 塔换 lore**（用户「创造栏传送门和水晶塔放一起，水晶塔 lore 改为…」）：`ModTab` 顺序改成 **塔 → 门 → 源质罐**；塔的 lore 压成两行「我们需要重新集结部队，艾尔连接着我们所有人。」+「虚空产出所有矿物，该方块可以自动输出产出」，第三条 lore3 连同中英语言键一起删掉。③ **产量拉到工作方块档且不涨 tick 成本**（用户「输出效率该最快，和工作方块一样，不同类型输出也变快，保证 tps 不要变高」）：`CYCLE_TICKS` 100 → **1**（对齐炎炉 / 坩埚 / 炼金炉的 1）；一批改 `TYPES_PER_CYCLE = 4` 种**不同**矿 × `1~2` 个（随机起点顺序取，保证不重复）→ 每 tick 4~8 件；产物直接 `insertItemStacked` 合并进栏（12 格能压 768 件）；**tick 成本不涨**：方块实体包改 `SYNC_INTERVAL = 10` 节流（`markClientDirty` / `flushSync`）、音效 `SOUND_INTERVAL = 40` 节流、自动输出 `AUTO_IO_TICKS` 10 → 1 但补「栏空直接返回」短路（空闲零能力查询）、矿物清单仍静态缓存、结构自检仍一秒一次；界面竖条因周期缩到 1 tick 失去意义，改画**运转指示**（`getSweep()` = `gameTime % SWEEP_TICKS(20)`），数据槽 0/1 语义跟着改、版式不动。 |
| 12.200 | ① **玄灵三材**（用户「添加几个小物品只注册和画贴图……不用做任何别的」）：`xuanling_rabbit_foot` / `shenjin_demon_bone` / `lingyue_wing_membrane` 三件 `registerSimpleItem` + UNCOMMON，**没有合成表 / 功能 / lore / 门禁 / 研究树**，只补物品模型壳子 + 中英语言 + 创造栏材料区；新增 `gen_spirit_relics.py`：剪影写死成字符串点位图（与原版 `rabbit_foot` / `bone` / `phantom_membrane` 的 alpha 逐像素一致），明暗由「到透明边最短距离」四档 + 左上高光自动算；兔子脚 = 玉银爪垫 + 鎏金爪尖 + 浮空灵光星，魔骨 = 银白骨干 + 鎏金嵌线 + 两端金箍 + 骨心紫焰，翼膜 = 银白膜 + 三道鎏金羽脉 + 月辉 + 浮空月牙（月牙画在剪影内会被幻翼膜鼓包切成一团，改浮空）。② **传送门贴图整体改白金科技风**（用户「整体白色银白色 不要暗色」）：`gen_portal_tex.py` 只换调色板不动形制——白瓷 + 拉丝银 + 冰蓝能量，最深一档也只是浅银灰 `SILVER_DD(150,159,175)`，鎏金全部换提白（框线 / 铆钉 / 肋条 / 顶环 / 铭条），侧面散热槽的明暗关系翻转（槽底改受光面） |
| 12.201 | ① **三材改用原版结构 + 非白底色**（用户「兔子脚用原版的兔子脚改……底色不要白色太怪了」）：`gen_spirit_relics.py` 改成**原版调色板点位图逐像素抄录**（序号按亮度排，对映到新色带——原版趾线 / 骨节 / 膜纹全保留，不再被距离场渐变抹平），底色三换：兔脚走原版的棕（暖一档 + 鎏金爪尖 + 灵光星）、魔骨走**暗旧骨藕褐**（+ 鎏金嵌线 / 金箍 / 骨心紫焰）、翼膜走**月夜青紫**（+ 鎏金羽脉 / 月辉 / 浮空月牙）——没有一件是白的。② **传送门重画折跃漩涡**（用户「全部重新设计，需要有折跃的感觉，像素风格即可 不要太真实」）：门框与双角保留，门洞换成**两臂像素螺旋**（半径 1.2→6.4、每臂 1.5 圈、臂宽 2px、亮度 `(1-t)^1.4` 越靠心越亮）+ 3×3 涡心热核 + 四颗向内火花 + 淡蓝池底；侧面加三枚指向当中的 **› 折跃箭头**、顶面画从上往下看的迷你漩涡、底面一圈虚线能量环；圈数离线并排试出（0.75 圈像逗号、3 圈糊成一团，`tools/preview/vortex_try.png` 的 A = 2 臂 × 1.5 圈 × 2px 胜出）。③ **尺寸口径**：传送门 **32×32** 四张、水晶塔（采矿方块）**16×16** 三张 |
| 12.202 | ① **台座开坛结构改 3×3 砖底座 + 台座上置**（用户「把原来基座的位子换成四象石砖 然后上面放基座，激活时下面一层改变模型托举上面的基座」）：`XuanshuPedestalBlockEntity` 新增 `BASE`（九格、全在下一层），`cornersIntact` → `baseIntact`、`lightBricks` 点九格；`CORNERS`（对角四格）留给渲染器四象絮带（y 偏移 −1）；`SixiangStoneBrickBlock` **删 `CORNER` 朝向属性**（承柱旋转对称，朝向失去意义），blockstate 只剩 active 两态，`onRemove` 改到上一层 3×3 找台座；开坛提示与魔导书两页文案同步改。② **激活态重画九根鎏金承柱**（新增 `gen_pillar_obj.py`）：OBJ 五段（暗石柱础底面封死 / 鎏金柱身中腰回纹带 + 三道竖筋 / 枭混 / 坐斗 / 承台云纹 + 金钉），64 面；整体鎏金无第二色相；删旧激活模型引致的孤儿贴图两张。③ **传送门改紫蓝科幻 + 线条感**（用户「增加紫色蓝色科幻风格，添加一点线条感」）：骨架换深紫蓝金属（最深的档也只是暗紫蓝），漩涡改**紫蓝双绞**（一臂紫一臂蓝，`draw_vortex` 加 `arm_colors`），线条感五处（门框内缘细线 / 双角直通涡心的能量芯线 / 侧甲电路走线串起六道散热槽 / 顶盖八向辐射线 / 底板十字筋 + 虚线环） |
| 12.203 | ① **传送门广场重做：16×16 三层方台**（用户「你给我的预览图最左面是一格……改为 16×16」+「基座底部我希望是连贯在一起，保证美观，然后一层层往上，可以不要 obj 就单纯代码」）：`tools/gen_portal_blueprint.py` 从「编译 schem」整个改成**纯代码算**——`SIZES = (16, 12, 8)` 三层方台（y0 铺地 16×16 整块、y1 12×12、y2 8×8，四周各收 2 格留 2 格走道）+ 8 格宽折跃门（柱脚紫珀 / 柱身两道金箍 / 外侧冰蓝玻璃缝 / 金槛 / 4×7 蓝玻璃门面 / 两级收分门楣 / 嵌海晶灯门冠）；每层同一套花纹：外圈白瓷压四金角、内圈紫珀、紫珀环斜角嵌海晶灯、四边正中嵌冰蓝玻璃。旧版 1099 格 / 23×25 / 12 种含 3 信标的 schem 形制整份退役（连最外那格孤立方块、中央方井一起没了），新版 **604 格 / 16×16 / 高 15 / 7 种方块**（錾制石英 260 / 石英块 144 / 紫珀 80 / 金块 40 / 淡蓝玻璃 36 / 蓝玻璃 28 / 海晶灯 16），**零方块实体**，一键收回不再留信标。`FRONT_SHIFT = 0 − minZ` 现在自然等于 0（minZ = 0），`Y_SHIFT = −1` 不变。② **预览脚本重写 + 三张图**：`preview_portal_place.py` 除原有三条断言，新增「最底层必须整块连通（四连通块数 = 1 且格数 = 边长²）」，出 `portal_place_iso.png` / `portal_place_top.png` / **新增** `portal_place_front.png`（正视 + 每层标高）。③ **玄灵三材挪到戒指后面**（用户「三个小物品骨头 / 翼膜 / 兔子脚放在戒指后面」）：`ModTab` 里从材料区末尾挪到`XUANSHU_VOID_EYE`（万劫玄枢·洞虚守一戒）正后面，顺序按用户所列 = 神浸魔骨 → 翎月翼膜 → 玄灵兔子脚。④ **兔子脚重画**（用户「兔子脚贴图再和原版区别大一点」）：`gen_spirit_relics.py` 里兔脚改**水平镜像**（原版朝右上，这只朝左上）+ 底色暖棕 → **灵玉青**（`RABBIT_RAMP` 整条换冷色）+ 踝部一道**鎏金脚箍**（只箍 y=12 那一斜排，整排都描金会变成一块底板、看着像条鱼）+ 趾尖四点**灵光青爪尖** + 掌心 2×2 **灵纹**；结构仍是原版点位图，趾线 / 爪垫起伏一根不少 |
| 12.204 | ① **恢复旧 schem 的多方块**（用户「多方块给我改回去」「恢复旧 schem 的多方块」）：`tools/gen_portal_blueprint.py` 从第 202 批的「纯代码算三层方台」整个改回「schem 编译」，数据源 `chuansongmen.schem` 从回收站取回、放进 `tools/schematics/` 一起受管；形制回到 **23×25 / 高 18 / 1099 格 / 12 种方块**（錾制石英 519 / 金块 106 / 紫珀 104 / 石英楼梯 91 / 蓝玻璃 64 / 海晶灯 59 / 石英台阶 47 / 黑曜石 42 / 蓝玻璃板 38 / 石英柱 13 / 淡蓝玻璃板 13 / **信标 3**），含最外那一格孤立方、中央 6×6 方井、y4 三颗信标；`FRONT_SHIFT` 自动变回 12（`0 − minZ`），`Y_SHIFT` 仍 −1，界面分母回到 1099。② **数据换成分层字符画**（新增 `tools/blueprint_map.py`）：两个多方块的 `int[] CELLS`（1099 格 × 4 个数字）整份删掉，改成 `CHARS` 调色板 + `STATES` + `MAP`（`== y,x,z` 起头，一格一字符、`.` 是空气），源码从 14 KB 掉到 6 KB、还能一眼看出每层轮廓；`simplify()` 顺带去掉取默认值的属性（`waterlogged=false` / `half=bottom` / `shape=straight` / `axis=y`），36 条状态串又省三分之一。③ **两个多方块瘦身**（用户「缩减一下没用的代码，特别是两个多方块建筑」）：`StructureDeployer` 收编 `Part`（原 `PortalGateStructure.Piece` / `VoidMiningStructure.Part` 合并成一个 record）+ `filled` / `isComplete` / `build` / `dismantle`，两边结构类只剩「形制来源 + 旋转 + 少量查询」，`placements()`、包围盒缓存、`satisfied` 重复实现全部删除（`PortalGateStructure` 230→150 行、`VoidMiningStructure` 300→150 行）；顺手修掉 `build()` 里「带方块实体的格子永远数不到」的判断顺序 bug。④ **传送门贴图改 16×16 三面**（用户「我要你改的是传送门的方块贴图……改为 16×16 也三张」）：`gen_portal_tex.py` 边长 32→16，只出 front（拱门 + 双绞漩涡 + 门楣门槛）/ side（竖肋 + 两道散热槽 + 电路走线）/ top（同心环 + 迷你漩涡），删 `xuanshu_portal_bottom.png`，模型 `down` 改指 top；预览 `portal_faces.png` 三格横排、`portal_iso.png` 重出。⑤ **清掉一次性产物**：`portal_schem.png` / `schem_structure.png`（旧调试图）、`preview_obj_iso.py` 里没人用的 `sample()`；`pyclean_scan.py` 补上跨文件引用判断（共享模块的函数不再误报）。 |
| 12.205 | ① **创造栏：祛魔书挪到戒指后面**（用户「将祛魔书放到戒指后面」）：`ModTab` 现序为 阿卡西记录 → 授业卷轴 → 洞虚守一戒 → **玄枢祛魔书 → 玄枢双生书** → 玄灵三材（骨头 / 翼膜 / 兔子脚）→ 成品法杖……，两本书成对收在戒指正后面。② **新物品「玄枢双生书」`twin_book`**（用户「添加一个新物品 功能是和附魔书合成产出两本一样的附魔书」）：`Items.ENCHANTED_BOOK` + 双生书 → **两本一模一样的附魔书**（`copyWithCount(2)`，`STORED_ENCHANTMENTS` / `CUSTOM_NAME` / 其余组件整份复制），双生书一次性；配方类 `TwinBookRecipe`（`CustomRecipe`，只认 `STORED_ENCHANTMENTS` 非空、`count == 1` 的附魔书），序列化器 `twin_book`，配方 JSON `twin_book_transfer.json`（合成台里的动态配方）＋ `twin_book.json`（书本体；**第 206 批起改为奥术工作台合成**，见 12.206）。③ **贴图**（用户「贴图就用祛魔的贴图，中间紫色带子改为蓝紫色偏蓝色」）：`tools/gen_disenchant_book.py` 改名扩成 `tools/gen_book_tomes.py`，同一张原版底图按时相旋两遍 —— 祛魔 300°（粉紫，原样不动）、双生 **265°（蓝紫偏蓝）**，明度 / 金扣 / 像素走向一律不动，另出并排对比预览 `book_tomes.png`。④ **本批按用户口径未编译**，jar 仍是第 204 批那枚。 |
| 12.206 | **两本书的合成从工作台改到奥术工作台**（用户「玄枢祛魔和双生的合成表改为奥术合成 6 种要素各要 815」，并把九格逐格点名）：`recipe/disenchant_book.json` 与 `recipe/twin_book.json` 的 `type` 从 `minecraft:crafting_shaped` 改成 `thaumcraft:arcane_shaped`，九格按 1→9 行序落成 `ABA / CDC / EFE` —— A 紫水晶（双生为**龙息**）、B 下界合金、C 玄枢八卦之盐、D 书、E 枫树叶、F 玄枢布；`vis` 六基元各 **815**（风 / 地 / 火 / 水 / 秩序 / 熵）。两条 `*_transfer` （祛魔、双生各自的动态配方）仍是合成台的 `CustomRecipe`，没动。 |
| 12.207 | ① **注灵台「六门各满」由 100 提到 2048**（用户「将注灵台要素上限改为 2048」）：`XuanshuNodeModifierMenu.BASIC_CAP` 100 → 2048 —— 这是**分段阈值**（到顶前八成那票只喂六门基础，到顶后才转「喂现有随机一门」），不是硬顶；从 12 涨到 2048 约 170 次运转。语言键两处中英同步（物品 lore1 + 魔导书功能页）。② **玄枢源质罐每种要素上限 1024 → 2048**（用户「罐子要素上限也改为 2048」）：`XuanshuEssentiaJarBlockEntity.CAPACITY` 改 2048（193 批 2048 → 195 批 1024 → 本批回到 2048），`fillRatio()` 分母、Jade 比例条、存档读档的钳位全部跟着常量走；lore 中英与渲染器注释同步。 |
| 12.208 | **本体快照 20751 → 20753 + 编译**：用户已把 `.none` 换成 `1.21.1-neoforge-20753-dev-revealing-abilities-snapshot`（旧的 20751 目录删除），`gradle.properties` 的 `parent_api` / `parent_runtime` 改指新快照；20753 的 revealing-abilities 重构**删掉两个公开接口** `AuraRevealingGear` / `GogglesOverlayGear`（换成 `PlayerAbilityGear#grantsPlayerAbility(stack, player, ability)` + `PlayerAbility.{AURA_REVEALING, GOGGLES_OVERLAY}` 属性通道），`XuanshuVoidEyeItem` 随之改写；其余 60 多个类零改动通过。构建 **815 701 B · SHA256 `00062e12…73a4`**。 |
| 12.209 | ① **注灵台判据落回 256 + 第三段加权**（用户「先检测 6 种基础的是否到 256……还是添加 6 种基础元素概率大一点」）：`BASIC_CAP` 2048 → **256**（12 → 256 约 21 次运转）；三段重写 —— ① 缺门 100% 补；② 六门齐而没满 256：20% 添新 / 80% 六门（未满的等概率）；③ 六门各 ≥ 256：20% 添新 / **六门合计 48%（每门 8%）** / 其余现有复合要素合计 32%（每门 32/N%）—— 新增常量 `BASIC_SHARE_PERCENT = 60`，新方法 `existingCompounds()`（排序保证可复现）与 `isPrimal()`；魔导书 `bagua.page.node_modifier_lore`（原空页）写满概率明细，`node_modifier` 页与物品 lore 同步。② **四神兽锭 vis 100 → 87、未烬的玄枢锭 77 → 129**（五个配方 JSON 的 `vis` 六基元同值），四页材料文案与 `recipe_ingot_note` / `unfired_req` 中英同步。③ **宇宙寰宇支配法杖耗魔 10% → 100%**（`ModWandParts.COST_MODIFIER` 0.1 → 1.0，杖端注册的 `baseCostModifier` 与 `specialCostModifier` 同时变）。④ **枫树树苗新增奥术配方**（`recipe/maple_sapling.json`）：5 位宏伟之木树苗、2/4/6/8 位樱花树叶，六基元各 6。构建 **817 280 B · SHA256 `19b0f439…14e3ad`**，已进 mods（sha 一致）。 |
| 12.210 | ① **玄枢耀光 `xuanshu_nitor`**（用户「添加一个新的物品 玄枢耀光 颜色用 tc4 闪耀之光改，改为紫色带一点星辰颜色」）：`tools/gen_xuanshu_nitor.py` 读 `tools/ref/tc4_nitor.png`，按亮度查 7 段紫色带重上色（剪影与 alpha 一点不动），孤立像素（顶上四粒火星）改星辰色 `#BAE2FF`；另补一条奥术合成（紫水晶 2 夹闪耀之光 + 玄枢八卦之盐，六基元各 30，出 2）。② **源质罐**：lore 压成一行「可以存储 32 种不同的源质，每种最多 2048 容量。」（`lore2`/`lore3` 两键删）；配方由合成台改 `arcane_shaped`（神秘锭 2 / 钻石 1 / 本体源质罐 1 / 玻璃板 5，六基元各 54）。③ **乾元罡气**八格换玄枢三材（水银 4 / 玄枢耀光 4 / 玄枢八卦之盐 2 / 玄枢布 2）。④ **水晶簇中心改下界之星**。⑤ **八台机器与两件工具要素统一到八种八卦**：星盘 / 充能器各 24、玄枢镐 / 锄各 12、注灵台 / 复刻台各 32、奥术台 48，六基元全部退出配方。⑥ **四法核重做**：八卦各 32、不稳 6；镇狱碎青峰（元始珍珠 + 下界合金镐）、赤羽焚天瞬（末影之眼居中）、烛龙衔时（时钟 + 玄枢耀光 2）；**烛龙衔时门禁改 `mode=all` 八种卦石齐立**，物品 lore 第三行 + 魔导书新增「八石镇坛」页（`bagua.page.focus_chrono_gate`），另三张空的风味页一并写满。构建 **819 376 B · SHA256 `e7052e30…b6baad`**，已进 mods（sha 一致）。 |
| 12.211 | ① **沟灵核心 lore 只留卦石那一行**（用户「只保留卦石那一行，其他的删掉」）：删 `lore1` 的代码与中英键，只留「基座下面立坎、巽、艮」。② **四象石砖激活态从九根承柱改成一件鎏金楼台**（用户「我希望是一格整体，类似楼台托举上面的基座」）：新增 `center` 状态，整座 3×3 只出一件跨格 obj `terrace.obj`（五段八角须弥座 + 中心方台身，116 面）由正中最格独家渲染，其余八格换空模型；台面顶在 8px、外圈碰撞箱同步收到半格；絮带起点 −0.48；色板取台座自己的贴图；删 `gen_pillar_obj.py` 与 pillar.obj/mtl/atlas。构建 **822 135 B · SHA256 `655c7bf7…09aec6`**，已进 mods（sha 一致）。 |
| 12.212 | ① **水晶塔输出对齐炎炉 + 三格锭槽**（用户「输出产物的效率跟玄枢熔炉一样，要最快，指的是输出而不是生成矿物」+「产物栏左边隔远一点添加竖着的 3 个格子，里面画上锭的描边」）：`autoOutput` 从东西南北四面改成 **六面**（与炎炉 `Direction.values()` 同口径），停转时也照样把栏里剩的推出去；新增 `OUT_SLOTS 12 + UPGRADE_SLOTS 3`，锭槽收四神兽之锭（+3）/ 玄枢锭（+2）/ 未烬的玄枢锭（+1），不消耗，效率换算成 `typesPerCycle = 4 + 效率`、`maxPerType = 2 + 效率/4`；对外能力只暴露 12 格产出栏；界面左侧竖三格 + 锭描边底图（`ingot_ghost.png`）+ 悬停说明 + 「效率 +N」。② **新物品「枫凝菼蒄」**（`maple_crystal`）：16×16 赤橙冰晶，枫叶 20% 掉落，64 一组。③ **玄灵三材加掉落与 lore**：兔子 40% / 凋零骷髅 40% / 幻翼 80%（`SpiritRelicDrops`，只认玩家击杀）。④ **洞虚守一戒补注魔配方**：中心揭示之护目镜、11 件材料、八卦各 64、不稳 25、八种卦石齐立。 |
| 12.213 | ① **枫凝菼蒄挪位 + lore**（用户「放在翼膜后面，同时添加 lore：破坏枫树树叶 20% 概率掉落」）：创造栏从材料区挪到翎月翼膜正后面（骨 → 翼膜 → 枫凝菼蒄 → 兔脚），补一行灰 lore「破坏枫树树叶有 20% 概率掉落。」。② **洞虚守一戒补卦石行**：八种卦石全写（门禁是 mode=all），`ALL_TRIGRAMS` 挪到类首（静态字段顺序，否则拿到 null）。③ **锭档位反转**（用户「四神兽之锭效率最低，未尽第二，玄枢效率最高」）：四神兽 +1 / 未尽的玄枢锭 +2 / 玄枢锭 +3，界面悬停文案同步。④ **矿机 lore 回到两行**：删 212 批加的 `void_mining.lore3`（锭槽说明）与中英键。⑤ **lore 立规**（§7.15）：凡卦石 / 星象配方必须带对应那行，颜色格式照旧。⑥ **鎏金楼台补底 + 加层**（用户「基座地下是透明的，然后这个底座添加一点花纹层次感」）：七道 0.8px 水平挑檐全改 45° 斜面（视线能顺挑檐钻进中空台心，这就是「透明」的来源），台身变成一整层连续的壳，只在顶 / 底各留一张盖板 + 四片角台；段数 5 → 8（地栿 / 圭角 / 下枋 / 下枭 / 束腰 / 上枭 / 上枋 / 台面），台面往上外放成冰盘沿；图集加「地栿 / 枭」两格，下枋做竖枋 + 上下横线、束腰做竖楞 + 中腰阴线，台面加内圈阴线。OBJ 仍是 116 面。构建 **828 793 B · SHA256 `5c6693a9…9407c4ee`**，已进 mods（sha 一致）。 |
| 12.214 | ① **合成表展示对称立规 + 清扫**（用户「戒指注魔的 jei/emi 查看的合成表内容展示要对称，之前说过的你也要记住」）：反编译确认注魔页是「−90° 起、360/N 均分、半径 30」⇒ 件数必须偶数且 JSON 顺序写成镜像序列（§7.16）。戒指 11 → **10 件**（正上龙蛋、正下下界之星，左右各 2 盐 / 2 布 / 2 锭 / 2 枫晶）；顺手把**能镜像的 20 个**注魔配方重排（只改顺序），**10 个**奇数件 / 多奇数类的列进 ROADMAP 待定（全场 33 个：20 个重排 + 3 个本来就对称 = 23 个已对称）。② **玄枢耀光配方**：六基元 30 → **各 77**，十字图样的四个空角补 `thaumcraft:thaumium_ingot`（`DAD / ABA / DCD`）。③ **枫凝菼蒄换贴图**：按用户给的碎晶参考图翻成三根主晶，五档**鲜红橘**，1 像素墨边、无抖动（`gen_maple_crystal.py` 整份重写）。④ **注灵台**：删掉「与上一瓶同类拒收」整组（`bloomTypeKey` / `lastBloomType` / NBT / 提示键），任意天域之华都收。⑤ **奥术台**：玄枢锭槽 → **可选附魔书槽**（书消耗一本、书上所有附魔直接并入、不判能否生效与互斥），代价改法杖六元各 **77** 点；新增附魔书淡轮廓 `book_ghost.png`，锭描边同步调淡。构建 **829 199 B · SHA256 `1aecacb2…8758201b`**，已进 mods（sha 一致）。 |
| 12.215 | ① **空槽轮廓再淡**（用户「矿机锭的轮廓和奥术台附魔书的轮廓再淡一点」）：`ingot_ghost.png` / `book_ghost.png` 描边 α150 → **110**、填充 α44 → **30**，两脚本重跑。② **奥术台小字只剩名字**：`hint_item` →「物品」、`hint_book` →「附魔书」（英 Item / Enchanted Book）。③ **玄枢布匹换触媒**：坩埚触媒由玄枢八卦之盐改成**四象布匹**，八卦之气各 4 不变。④ **水晶塔「1 秒满」**（本批重点）：`produce()` 改成**先补货、再添新** —— 每刻先给栏里已有的每一格按档位补（三枚玄枢锭 = 每格 +4，12 格 48 件/刻 ⇒ 64 件 16 刻 ≈ 0.85 秒），余下的名额才铺新种类且封顶 12 格；新增 `hasEmptyOutSlot()`。⑤ **页签图标补回**（用户「之前给……自动输入输出按钮添加过图标为什么现在没了」）：炎炉 / 坩埚 / 炼金炉三台页签只剩空白壳，图标抽成 `XuanshuGui.tabIcon()` 由四台共用。⑥ **充能器换料**：八格里两枚 `thaumcraft:nitor` → **玄枢耀光**（镜像位不变）。构建 **829 712 B · SHA256 `1e60f86b…23ac8`**，已进 mods（sha 一致）。 |
| 12.216 | ① **四象石砖激活态改「地坛式三层方台」**（用户「玄枢基座的多方块地下那一层是类似地坛往上，贴图重新设计」）：弃掉八角须弥座，改成**角格 3px / 边格 8px / 中心台身 16px** 的三层方台，每层都是封闭方盒（四面 + 露出顶面 + 整片底面），四角是实心低台、不再留 45° 缝。② **修「框出来的角透视」**：旧版单面壳 + 角上竖三角缝，视线能钻进中空台心；新版全封闭，任意角度看不到底面外的天空 / 土地。③ **碰撞按层高**：新增 `corner` 状态，角格 `box(…,3/16,…)`、边格 `box(…,8/16,…)`、中心整格；旧存档靠每秒自查自动补 `corner`。④ **贴图整份重绘**：汉白玉台面 + 暗铜台基 + 鎏金束带；侧条按 48px 实宽绘制（不再把 8 纹素横拉 6 倍），回纹 / 竖枋 / 束带全是 1:1 硬边；台面保留外沿、阴线、四角方胜。⑤ **絮带起点随角格降到 −0.79**。⑥ **枫凝菼蒄换新贴图**（用户「类似尖的四棱冰晶一样的像素风」）：单根见棱四棱冰晶，1px 尖顶 / 尖底，正面一道亮棱分左右两片折面，赤橙六档 + 墨边，无抖动 / 孤立像素。构建 **824 977 B · SHA256 `34c3a05b…d533a3e`**，已进 mods（sha 一致）。 |
| 12.217 | ① **烛龙衔时 lore 第三行改色**（用户「CHRONO 的 lore 中最后一行卦石的需求改为和其他 lore 一样颜色一样，可以看 FOCUS_TELEPORT」）：`grayLore("…focus_chrono.lore3")` → `trigramLore(ALL_TRIGRAMS)`，与赤羽焚天瞬同一句淡靛蓝 0x8FB0FF、不斜体，八种卦石全列；中英 `lore3` 语言键一并删掉。② **寰宇支配法杖回到最简**（用户「容量 99999、100% 消耗，不要消耗了直接补满，就设置最简单的」）：整个删掉 `WandAutoRefill`（每 5 tick 把六元补满），`recharge` 由 `none()` 改 **`any()`**（与其余六柄同一套被动充能），容量 99 999 / 耗损 ×1.0 不变，法杖不再有任何特殊逻辑。③ **注灵香炉台 129 各要素**：核实已有实现（`VIS_PER_CHANNEL = 129`，六基元各一份，`consumeExactVisFromWand` 收厘），本批无需改动。④ **基座贴图重做成「整体鎏金 + 古代符文」**（用户「更细腻一点，带一点古代的符文，不要想着这样一层一层，然后有点错位，整体鎏金」）：`gen_terrace_obj.py` 的绘制段整份重写——撤掉三层各画一道束带的旧法，侧条整面只有一种鎏金（三档明暗差 ≤20 的 1px 抖动）；横向只留真实台沿（v3 / v8 亮边 + 各自下面一道阴刻），竖向分枋按**块缝 16px** 对齐（不会再出现层与层花纹接不上的错位）；台身每个 16px 面正中刻一枚符文（十 / 王 / 日 / 山 / 爻五枚循环），台面上按**八方位**（±16 轴向 + ±16 对角）镇八枚符文，正好一枚落在每块 16×16 待见台面正中；旧版的同心回纹圈（一层套一层）全撤，只留最外一道亮边 + 里侧阴刻。构建 **823 907 B · SHA256 `10ca49b2…c35924`**，已进 mods（sha 一致）。 |
| 12.218 | ① **基座模型回到八段楼台、贴图改成横向明暗分段**（用户「基座的模型上一版，但是贴图不好看 不要一大块纯色，有层次感」；随后选定「回到第 215 批的八段楼台」+「横向明暗分段」）：`terrace.obj` 弃掉 216/217 的三层方台，重写成**八段**——楼台侧 地栿 0–2 / 圭角 2–3 / 下枋 3–6 / 下枭 6–8，台身侧 束腰 8–10 / 上枭 10–12 / 上枋 12–14 / 冰盘沿 14–16，每段一圈四面梯形、**段与段在交界处半径相同、共用一条棱**，整座是连续闭合的壳（没有水平挑檐、没有缝，视线钻不进去），三张盖板（y0 封底 / y8 台面 / y16 台身顶）；共 **35 面**（140 v / 140 vt / 35 vn，绕序自纠 2 面）。② **贴图按段分色**：挑檐段（圭角 / 下枭 / 上枭 / 冰盘沿）走亮金 GOLD5–6，收进去的段（地栿 / 束腰）压暗 GOLD0–2，竖直段（下枋 / 上枋）中间调 + 竖枋（每 16px 阴刻 + 倒角提亮），台面上下各一道阴刻；楼台每一面正中一枚符文（五枚循环）。③ **台面不再是一整块金**：九格按 16px 交替深浅（170,133,66 / 146,112,52）+ 三道台沿线 + 台身脚下阴影圈 + 亮唇 + 八方位符文。④ **Java 回退**：删 `CORNER` / `CORNER_HEIGHT` / `CORNER_STEP`，外圈一律 8px `DECK`；`lightBricks` 不再写 corner；`RIBBON_FROM_Y` −0.79 → **−0.48**；blockstate 回 4 变体。构建 **825 030 B · SHA256 `4ef9c9ae…dbc94b8`**，已进 mods（sha 一致）。 |
| 12.219 | ① **基座盘面字更小更多、排成一圈**（用户「基座盘面上的字改的小一点多一点以圆形围绕」）：`gen_terrace_obj.py` 的 `RUNES` 由 6×5 收到 **3×3**（十 / 王 / 日 / 山 / 爻），台面符文由 8 枚方位改成 **16 枚**沿半径 **15.5px** 的环带均布（`top_glyph` 遍历 16 枚、盒宽 3，内环线 11.0–11.9 与 12 外的环带分开）；`t_riser` 正中那枚同步改 3×3。② **整座改「一级一级的台阶」**（用户「整体像楼梯一级级升高」）：剖面表 `PLATFORM`/`BODY` 换成一张 `PROFILE = (y0, y1, r)`，每段一圈四面竖直墙 `ring()`，**半径一变就补一圈水平面 `step()`**（变窄朝上 = 踏面 `t_top`、变宽朝下 = 檐底 `b_under`），八段 = 8 圈竖直墙 32 面 + 5 圈水平面 20 面 + 3 张盖板 = **55 面**（220 v / 220 vt / 55 vn，绕序自纠 2 面），所有接缝闭合、无斜裙。③ **和底部方块完全接触**（用户「底部还是会有点透视方块，你改为和底部完全接触」）：最底层外沿 23.9 → **23.99**（3×3 只留 0.01 免得邻块同面打架），封底盖板 `b_under` 改成**整片实心、不做格线**，侧条 v0 不再压黑，改 `tone(GOLD2)`。④ **鎏金再提一档**（用户「鎏金色多一点」）：色板 `GOLD0..GOLD6` 全部上移（92/124/156/182/204/224/246）。⑤ **侧面不再刻字、改古代楼阁镂空花窗**（用户「边上四周就不用字了 贴图像古代的楼阁的镂空来一点」）：删 `wall_rune`；下枋 3–5 与上枋 12–13 换 `window()` —— 每 16px 一格三扇**直棂窗**（3 / 12 亮金窗框、6 / 9 竖棂、2px 满高窗洞），3px 高的下枋不加中横棂（否则窗洞被切成豆腐块）。⑥ Java 侧**零改动**（台面仍是 8px、碰撞箱与絮带 −0.48 不变）。构建 **827 618 B · SHA256 `39b47785…218de6`**，已进 mods（sha 一致）。 |





### 12.156 第 156 批（2026-10-22）：三核心连线重排 / 灵目改像素戒指 / 注灵台改古代鎏金 / 文档整合

**① 魔导书里震卦下三个法核右边两条线还叠着（第三次投诉）**

前两批只是把节点挪开，没解决根因。这批判明了机制：

- `ResearchBrowserScreen.drawResearchPath` 画的**不是直线**，是带正弦抖动的渐变曲线，
  起点有 `sin(...) * 5.0F` 的摆动；两条同源边在起点 20px 内只要夹角小于约 30°，视觉上必然缠在一起。
- 所以判据要从「几何上不相交」换成「**同源夹角 ≥ 30°**」。
- 改成扇面：`FOCUS_GUARDIAN (1,3)` = 90°、`FOCUS_COSMOS (3,3)` = 45°、`FOCUS_TELEPORT (4,1)` = 0°。
- `TERRAIN` 从 `(1,4)` 挪到 `(0,5)`，让出 `(1,3)`/`(1,4)` 并拉开 `GUA_KUN` 的扇面。
- `FOCUS_TELEPORT` 先试 `(5,1)`，发现贴住 `SHARDS → CAPS` 那条线（10.7px），退到 `(4,1)`。
- 复核结果：核心区贴线 **0 处**，全树线交叉 **0 处**。

**② 玄枢洞虚灵目：从高清插画改成像素戒指**

`tools/gen_void_eye.py` 整文件重写（旧版是 16×16 八卦镜 + 金链，备份 `tools/.bak_eye.py`）：

- 32×32、10 色调色板，金环管宽 5.4px（4.2px 像橡胶圈，太细无体积）；
- 光照分两重量化：切向按 `lit` 切四档、径向按管壁位置压暗，背光面留一处「环境反光」抬一档；
- 宝石是归一化菱形 `m = |dx|/W + |dy|/H` 的四层同心，镶口用两行像素夹住。

**③ 玄枢注灵台：古代鎏金**

`tools/gen_node_modifier.py` 调色板与三个贴图函数全部重写（备份 `tools/.bak_nm.py`）：

- 七档鎏金：`GOLD_H / GOLD_L / GOLD / GOLD_M / GOLD_D / GOLD_S / DARK` + 两档灵光；
- **`base()` 的 `jitter` 归零**——这是关键修复，之前的随机抖动让 16×16 出现几百种近似色，
  顶面看着像齿轮；归零后立刻干净；
- 侧面：高光棱 / 亮金 / 深缝 / 上沿高光 / 金面左右云纹 / 正中竖槽；顶面：1px 外框 + 四向刻痕 + 四枚对角钉 + 中心灵珠。

**④ 文档整合**

`README-tc.md` 从 10242 行压到本篇（§7 档案 + §8 索引 + §9 坑集），`ROADMAP-tc.md`
把 11 段「第 N 批实机回归」长句收成一张待验表。原始逐批记录归档在 `.none/逐批详录-1-156.md`。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 643 608 B，
SHA256 `4989a1ddd4bf0de93bae9967c7e52801e8d41bd65af2c1aea43804cc99e2f013`；
`selfcheck` ALL GREEN（models 101 / blockstates 21 / lang keys 413 / resources 399）。

### 12.157 第 157 批（2026-10-22）：注灵台模型与碰撞箱对齐 / 灵目回退 + 削峰 / 清理备份

**① 注灵台「贴图和模型错位」的真因：形制有两个来源，早就不同步了**

用户问「模型用 java 描述的吗」——一半是：**碰撞箱在 Java（`VoxelShape`），模型在 JSON**。
两处从第一百批起就各说各话：

| | 形制 |
|---|---|
| `XuanshuNodeModifierBlock.SHAPE`（Java） | 底板 + **四角立柱** + 顶环，**中间完全镂空**（9 个盒子） |
| `models/block/xuanshu_node_modifier.json` | 底板 + **一整块实心台身 `body`** + 四角柱 + 顶环（10 个元素） |

于是贴图按「实心方块正面」画，玩家看到的却是一个贴了柱面纹样的实心箱子；柱子与框的接缝
又因为两边坐标不同（模型 1~4，碰撞箱 0~3）差一格。**这就是错位的根因。**

改法：以碰撞箱为准，JSON 删掉 `body`，只留 9 个盒子，与 `SHAPE` **逐块相同**
（脚本里直接对账，输出 `True`）。贴图按这 9 个盒子重新分区，每个面都 **1:1 取图、不拉伸**：

- 柱身 `3×8`（`x0..3, y0..8`）→ 四根角柱的四个侧面；
- 底板带 `16×2`（`y8..10`）→ 底板四个侧面；
- 顶框带 `16×2`（`y10..12`）→ 梁的立面；10 宽的两面取 `x3..13`，同样 1:1；
- 梁底 `16×2`（`y12..14`）→ 朝着洞的那一面，压暗，框才有厚度。
- **顶面改成俯视平面图**：四根梁的 `up` 面各自按占地取图（北梁 `0..16 × 0..3` 等），
  四块拼起来是一张完整的回字框。此前顶面画的是「圆盘 + 灵珠」，贴到四根梁上必然错位。
- 底面托盘图同时用作**底板顶面** —— 透过中间的洞往下看到的就是它，正中一点灵光。

顺手修了预览工具的一个错：`preview_block_iso.py` 的深度轴写成 `x+z-y`，而本投影
（`sx=(x-z)C`、`sy=(x+z)/2-y`）的视线轴是 `(1,1,1)`，正确深度是 `x+y+z`；
旧写法会让高柱被梁压住，看图会误判成模型有问题。

**② 灵目：贴图回退 + 改名 + 削峰**

- 贴图与生成脚本一起 `git checkout` 回 32×32 那版（第一百三十批）。
- 新增能力：**单次受伤不超过自身最大生命的 10%**。落点 `LivingIncomingDamageEvent`
  （护甲与附魔结算**之前**），所以是「削峰」而不是「减伤」：小刀小剑不受影响，
  一发 40 点的重击削到 6 点（60 血 × 10%），之后照常走护甲。
  两处刻意放过：`/kill` 走 `DamageTypes.GENERIC_KILL`（`Float.MAX_VALUE`）直接放行；
  本来就不超过上限的伤害零开销。戴没戴用 Curios 的 `isEquipped` 判定（放背包不算）。
- 改名：**玄枢洞虚灵目 → 万劫玄枢·洞虚守一戒**（en `Xuanshu Voidward Ring`），
  注册名 `xuanshu_void_eye` 不动（存档与配方 id 照旧），lore 补第五条。

**③ 清理**

- 删掉 `tools/.bak_*.py` 四份脚本备份、`.none/.bak_ROADMAP-batch156.md`、
  以及 `tools/preview/` 里 12 个一次性渲染图；
- 整合前那份 10200 行的逐批原始记录**不再算备份**，改名 `.none/逐批详录-1-156.md` 存档
  （§8 的索引就是它的目录）。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 646 955 B，
SHA256 `6a29dad100c2a36a0e9e1ffee24f356db871171d0076b561c62c8d3ce2805c28`；
`selfcheck` ALL GREEN（models 101 / blockstates 21 / lang keys 414 / resources 399）。


### 12.158 第 158 批（2026-10-22）：注灵台四角立柱内收 1 像素

模型 JSON、`VoxelShape`、贴图三处继续对齐：四根角柱从 `x/z 0~3`、`13~16` 收成 `1~4`、`12~15`
（往里各 1 像素），顶环与底板不动。`gen_node_modifier.py` 与 `check_node_modifier_shape.py`
的盒子坐标跟着改，对账输出 True。

### 12.159 第 159 批（2026-10-22）：注灵台端面按自己占地取图

3 宽的四根柱端面原先拿的是 16 宽的顶框带（整条带压进 3 格），纹样全糊。改为每个面
**按自己的占地 1:1 取图**，柱端、梁端各有各的纹。

### 12.160 第 160 批（2026-10-22）：注灵台井沿 / 镐改掉落模式

**① 井沿**：顶框方口的内口加一圈 **1px 宽、比梁面低 1px** 的退台（井沿），俯视才看得出「井」。
盒子数由 9 变 **13**（底板 + 四角柱 + 顶框四梁 + 井沿四块），`check_node_modifier_shape.py`
不再写死 9 盒。

**② 镐的普通右键**：新增 `PickaxeDropModes` / `PickaxeDropEvents`，普通右键在
「精准 / 时运 / 直取本体」几档之间切，挖掘时按当前档改掉落；提示只报模式名。

### 12.161 第 161 批（2026-10-22）：青龙 / 白虎 / 玄武三圣兽杖件

四象补齐：`qinglong` / `baihu` / `xuanwu` 各一柄一端的六件（与朱雀同规格），
贴图先照朱雀「同款形状重着色」。部件注册收成 `wandPart(id, rarity, trigrams...)` 一行一个，
`ModWandParts` 改表格驱动（七柄杖一张表，`register()` 照表循环）。

### 12.162 第 162 批（2026-10-22）：精简 / 工具数值 / 华槽同类拒收 / 四杖柄端区分

- **压缩与精简**：136 张 PNG 共约 94 KB，PIL 重压反而大 866 B（已最优）；唯一逐字节重复是
  奥术台与复刻台的辰星贴图（各 745 B，故意各存一份）；Java 死代码 0、436 个 lang 键全有引用。
  只删了 `tools/preview_tree.py` 里没人调的 `link()`。
- **工具数值**：八卦镐总伤 20、玄枢锄 52、玄枢镐 66；三把共用 `TOOL_ATTACK_SPEED = 4.0F`
  加成（总 8.0，间隔减半）。
- **华槽同类拒收**：放进去的天华与**上一瓶同类型**就提示换一种、不耗材料（类型串存
  `LastBloomType`）。
- 删掉玄枢台座 lore 里那句「（自罐吸纳）」。
- **四圣兽杖柄 / 杖端各加本兽记号**（`tools/gen_beast_wand_textures.py`）：杖柄加「结」
  （朱雀金环 / 青龙双竹节 / 白虎冰裂霜环 / 玄武甲片缝），杖端加「兽记」（火舌 / 龙角 / 虎爪 / 龟甲珠）；
  四柄容量统一 **918**。

### 12.163 第 163 批（2026-10-22）：回退传送贴图 / 罡气双环 / 四杖八折 / 改名 / 三段式

- 传送核心贴图与生成脚本 `git checkout` 回原版，备份删净（先用 `git show HEAD:` 重生成并逐像素比对）。
- `focus_guardian_orn.png` 在「new ver」提交里被删（法杖上那枚核心一直缺贴图）→ 补回，画成同心双圆环。
- 四圣兽耗损统一 **×0.8**（删掉 0.9 / 0.85 两档）。
- **改名**：注灵台 → **玄枢注灵香炉台**（zh 14 处；en `Xuanshu Spirit-Infusion Table` →
  `…Censer Table`、研究名 → `Xuanshu Infusion Censer`、`Node Modifier` → `Infusion Censer` 8 处）。
- 提示词：同类型华从「瓶」改「种」；缺华 →「华槽须放一种天域之华。」
- **加要素改三段式**（`XuanshuNodeModifierMenu#nextAspect`）：① 六门基础有缺先补；
  ② 六门齐但每门 < 100 → 80% 涨六门之一、20% 添新；③ 六门各满 100 → 80% 涨现有随机一门、
  20% 添新。每次 +12、基础上限 100；魔导书与方块 lore 中英同步写清。

### 12.164 第 164 批（2026-10-22）：新法核「烛龙衔时」

**① 核心**：`focus_chrono`（`skydeitymysticpivot:chrono`），耗六基元各 25 点，
右键指一台机器让它加速；贴图是钟面法核（蓝表盘 + 金圈金针）+ 杖端表环。
数据包 catalog / recipe / model / 研究页 / 中英 lang 全套。

**② 加速引擎**（`TemporalCore`，只活在内存里）：参考 TIAB 的 `TimeAcceleratorEntity` 路线——
拿 `BlockState#getTicker(level, be.getType())` 的服务端 ticker 每 tick 多跑几次；
没有方块实体但按随机节律走的方块走 `state.randomTick`。TIAB 不完善处逐条补：
每次补跑单独 try-catch（一台机器出事只停它一台）、每 tick 重取方块实体做同一性校验、
全服 32 台上限、区块卸载跳过、`ServerStoppedEvent` 清表。

**③ 特效**：本体 `destination_wisp_blue`（蓝精灵）从玩家眼睛飞到方块，
`sendParticles(count=0, 位移, 1.0)` 的 count=0 分支速度恰好是「位移 × speed」。

### 12.165 第 165 批（2026-10-22）：木星环 / 十字星 / 16~256 倍加码与倍率悬浮字

- **罡气饰纹**：同心双圆环 → **两条横放、倾角不同（-10° / +24°）的扁椭圆环**（木星环），
  环从球背后过。
- **传送饰纹**：改成「几个十字星」（这一批连图标一起改了，166 又回退）。
- **烛龙**：16 倍速起步、**同一台再催翻倍至 256 封顶**、时长 **32 秒（640 tick）**；
  当前倍率用 `Display.TextDisplay` 悬浮字显示在机器正上方——本体 setter 全 private，
  走公开的 `Entity.load(CompoundTag)` 摆字；TextDisplay 区块重载会换对象，所以不存引用，
  每次在机器上方 1×1×1 小盒子里按 scoreboard tag 现找（重载回来的被「收养」）；
  `ServerStoppingEvent`（存档之前触发）把字全撤掉，免得落盘。
- **提示词**只留两条（「这台机器扛不住时序」/「此方块无法加速」），删掉上限/成功/到期三条；
  区块卸载由「继续计时」改为**整条暂停**；顺手修了作物路径 `be.getType()` 的 NPE。

### 12.166 第 166 批（2026-10-22）：环收细 / 特效精简 / 烛龙贴图与删配方 / 灵目 lore / 文档补齐

- **罡气双环收细**：环宽从「按 k 的带宽」（长轴 3.3px、短轴 0.7px）改成**按像素距离的 1.1px**；
  扁轴同时放大到 3.8 / 3.0、核心收到 2.6——扁轴小于核心半径时环的弧线全被球吃掉，
  只剩两侧几粒方块。
- **加速特效**：只留「飞过去」一段（删爆发圈与催动期微光，连在途队列一起删）；
  蓝精灵自带 alpha 0.5 太虚，**同一点叠三颗**（有效不透明度约 0.88）。
- **烛龙贴图**：外圈由 1.35 收成 **0.7 细环**、与外层主体钟之间留**透明缝**（细环用
  `thin_ring` 按距离带画，否则碎成虚线）；主体钟中间再收小一圈；**杖端饰纹整张重做**：
  不画底盘、不画大圆，只用 **1px 白线**勾一圈表环 + 时/分针 + 一点的蓝秒针。
- **传送**：图标 `git checkout` 回原版（与 HEAD 逐字节一致）；杖端饰纹重做成
  「夜空紫底盘 + 暗金细边 + 三颗十字星」，刻意不与图标同构。
- **烛龙删合成表**：删 `recipe/focus_chrono.json`、研究页的注魔配方面与配方文字页、
  门禁表条目、物品 lore 的「所需卦石」行；`bagua.recipe.focus_chrono` 键一并删。
- **灵目 lore 精简**：5 条删到 2 条（留「揭示」与「法杖缓充」，删护甲 / 生命 / 受伤上限）。
- **文档补齐**：README §7.2 / §7.4 / §7.5 / §7.10 / §7.13 与 §8（12.158~166）、§10，
  以及 ROADMAP §0 / §1 / §2 全部对齐到第 166 批。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 681 562 B，
SHA256 `beb7d198d0eb3f522c281fa7aa20fc3a6c0d0c31b0f157bfda98834b90e1e2f2`；
`selfcheck` ALL GREEN（models 108 / blockstates 21 / lang keys 440 / resources 428）。

### 12.167 第 167 批（2026-10-23）：枫树三件套 / 依赖口径推到本体 20748

用户原话：
> 继续制作 D:\.codepy\trea\slash\SkydeityThaumcraft_1.21 的中的模组……我想添加一个树类似原版大型的橡树，
> 树叶和原木和树苗重新根据原版橡木树设计，我希望是枫树，树叶红色偏橘色。

**① 先修依赖口径：20746 快照没了，用户换成了 20748**

进工程第一件事是发现 `.none/1.21.1-neoforge-20746-dev/` 整个被删（`gradle.properties` 的
`parent_api` / `parent_runtime` 两行还指着它），构建直接断；用户同时放上了
`.none/1.21.1-neoforge-20748-dev/`（tc + fm + nm + tb + tm + tt + wt 七棵 sources/api 树）。
按 §6 的升级纪律处理：

- `parent_api` → 20748 api jar；**javap 全量签名 diff**（20744 vs 20748，各 590/598 类、
  8306/8447 行）确认公开 API 差异全是新增：`WandApi.changeVis` 一族 +
  `VisChangePlan` / `VisChangeRequest` / `VisChangeResult` / `VisChangeSettlement`、
  `WandVisStages`、`Thaumonomicon.back()`、Beam / Bolt / Presentation / Origin 的
  `operation` 重载——本 mod 一个都没碰，源码零改动。
- **20748 没有 runtime jar**（六个 mixin 目标类在实现包里，api jar 只有 api）。
  `parent_runtime` 暂借 20744 的运行时 jar 只作编译：逐一核对这六个类在 20744 字节码
  （javap -p）与 20748 sources 里的成员签名完全一致——`InfusionMatrixBlockEntity`
  的 `instability` / `recipeId` / `count` / `countDelay` / `crafting` 五字段与
  `startCrafting(Player)` / `serverTick(Level,BlockPos,BlockState,InfusionMatrixBlockEntity)` /
  `centralPedestal()` / `craftCycle()` / `changedAndSync()` 五方法；
  `AuraNodeBlock.hasValidEnergizedStructure()`；`AuraNodeRenderer.renderNodeLayers(...)`；
  `TaintSpreadLogic.restoreOriginalBiomeColumn(ServerLevel,BlockPos)`；
  `PlayerWarp.set{ Permanent, Sticky, Temporary }Warp(Player,int)`；
  `ArcanePedestalBlockEntity` 类在位。该 jar 只进编译类路径、永不打包；mixin 全部
  `remap=false` 按名字找目标，运行期用哪个版本的本体都不受影响。
- `neoforge.mods.toml` 的 thaumcraft 版本区间本来就是 `[4,)`，不用动。

**② 枫树三件套**

| 文件 | 内容 |
|---|---|
| `MapleSaplingBlock.java` | 树苗。**为什么不 extends `SaplingBlock`**：原版构造器只收 `TreeGrower`（持 `ResourceKey`，逼着注册动态注册表）；改为 extends `BushBlock` 自实现 `BonemealableBlock`，随机 tick / 骨粉 / 两段式 stage 全照搬原版 |
| `MapleTreeGrower.java` | 树形。现构 `FancyTrunkPlacer(3,11,0)` + `FancyFoliagePlacer(2,4,4)` + `TwoLayersFeatureSize(0,0,0,4)` + `ignoreVines()`——与 `minecraft:fancy_oak` 逐项同参，provider 换枫木/枫叶，直接 `ConfiguredFeature#place` |
| `ModBlocks` / `ModItems` / `ModTab` | 三块方块 + 三个 BlockItem + 创造栏三行（排在八枚卦位石之前，与建筑构件分组分开） |
| `tools/gen_maple_tree.py` | 四张 16×16 贴图：树皮暖棕六档竖纹、顶面同心年轮、枫叶「五裂字符图章」（红偏橘、α 镂空、覆盖率 41%）、树苗棕茎三小叶 |
| `assets/...` blockstates / models | 原木 cube_column（轴向旋转照原版 oak_log）；树叶 cube_all + `render_type: cutout`；树苗 cross + cutout；item 模型各指向方块模型 |
| `data/.../loot_table/blocks/maple_*` | 原木直掉；树叶剪刀/时运掉自身否则 5%（时运 6.25/8.33/10%）掉树苗——同原版橡叶，删了 stick 池；树苗直掉 |
| `data/minecraft/tags/**` | 枫木进 `#minecraft:logs`（块+物）与 `#minecraft:logs_that_burn` → 顺带 axe 可采 + 燃料 300 + 熔炉烧木炭；树苗进 `#minecraft:saplings` → 燃料 100；枫叶进 `#minecraft:leaves` + `mineable/hoe` |
| `data/skydeitymysticpivot/worldgen/**` + `data/neoforge/biome_modifier/maple_trees.json` | configured（fancy_oak 参数 JSON 版）+ placed（每区块 2 棵 / in_square / OCEAN_FLOOR 高度图 / `would_survive` 树苗判定）+ `add_features` 到 `vegetal_decoration`，群系列表：forest / flower_forest / birch_forest / old_growth_birch_forest / dark_forest（**刻意不进 grove**） |
| lang（zh/en） | 枫木原木 / 枫树叶 / 枫树树苗 三条 |

**③ 两个踩掉的坑（已进 §9）**

1. **树苗不清空就长树，树干底部缺一格**：`TrunkPlacer#placeLog` 的 `isFree` 只认空气与
   树叶，主干第一个原木的落点正是树苗所在格。原版 `SaplingBlock#advanceTree` 在调
   `TreeGrower` 之前先把那一格换成流体状态——照抄之后才对。
2. **`Blocks::never` 在附属里编译不过**：那个三参 `never` 是 `Blocks` 的 **private**
   方法，公开的是四参版（给 `StateArgumentPredicate` 用）。`isSuffocating` /
   `isViewBlocking` / `isRedstoneConductor` 要的是三参 `StatePredicate`，
   改成 `(state, level, pos) -> false` lambda。

另有两个编译期小坑：`ServerLevel` 在 `net.minecraft.server.level`（不在
`world.level.server`）；`NoteBlockInstrument` 在 `...block.state.properties`
（不在 `...block`）；`isValidBonemealTarget` 在 1.21.1 是**三参**（没有 `isClient`，
1.20.2 的老签名会报「重写的方法不是超类型的方法」）。

**④ 要素是标签送的，没写 object_aspects**

本体 `object_aspects` 的 tags 段写着 `#minecraft:logs` = arbor 4、
`#minecraft:saplings` = arbor 1 + herba 2、`#minecraft:leaves` = herba 1，
且该段只在 `direct` 查不到时生效——枫树三件进了这三个标签就自动拿到要素，
一个数据文件都不用加（§7.14 有完整推导）。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 696 323 B，
SHA256 `0a9523c607876dd41ac1562502717dd71f8a2e349efa0604c1b2fdf3e3f82d0f`
（终版：修「树苗不清空就长树」+ 补 `logs_that_burn` 后的构建；jar 内 19 个 maple
文件与源码逐字节一致）；
`selfcheck` ALL GREEN（models 114 / blockstates 24 / lang keys 443 / resources 456）；
死代码 0、孤儿 0（枫树四张贴图全部有引用）。

### 12.168 第 168 批（2026-10-23）：真 20748 runtime / 枫木木板 / 樱花林加量 / 创造栏调整

用户三条反馈：
> 1. 我给了 tc.jar 放在 .none 中了
> 2. 创造栏原木和树叶放在卦石前面，树苗放在戒指后面
> 3. 生成条件樱花林多一点。其他就保持现在的，然后添加原木分解成木板 1 出四，编译好放入 mods 文件夹

**① 依赖切回真正的 20748**：用户把完整运行时 jar（19.3 MB / 2246 个实现类）放到
`.none/1.21.1-neoforge-20748-dev/tc.jar`。`parent_runtime` 从「借 20744」切到它，
六个 mixin 目标类的成员签名在这个真 jar 上重新 javap 核对（五字段 + 十一个方法全在），
编译通过、源码零改动。§6 的升级表补了 168 行。

**② 枫木木板**（`maple_planks`）：`Block` + 原版木板参数（2.0/3.0、木料音、岩浆点燃），
进 `#minecraft:planks`（块+物）→ 斧采 + 燃料 300。分解配方
`recipe/maple_planks.json`：无序、1 `skydeitymysticpivot:maple_log` → 4 木板。
**原料写死物品 id 而不用 `#minecraft:logs`**——用标签会让橡木原木也合出枫木板，
而原版每棵树有自己的木板（橡木板配方只收 `#minecraft:oak_logs`），照此规。

**③ 樱花林加量**：新增 `placed_feature/maple_tree_dense.json`（count **6**）+
`biome_modifier/maple_trees_cherry.json`（只对 `minecraft:cherry_grove`）——
原版樱花林本就密（约 10 棵/区块），6 棵枫树插进去是「红橘点缀粉白」而不是取代。
五群系森林的 count 2 不动（用户：其他保持现在的）。

**④ 创造栏调整**：树苗从方块组移到**洞虚守一戒（戒指）之后**（功能物品组，
它是「能长成树的活物」）；原木与树叶留在八卦位石之前，**木板夹在原木与树叶之间**
（原木 → 木板 → 树叶，读料的顺序）。类注释的分组说明同步改。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 700 157 B，
SHA256 `89644cfd0719096dab229596813a85e9663d4c057f1f423a8cc1ada0a262a193`
（jar 内 27 个 maple 文件与源码逐字节一致，含木板四件套 + 樱花林两件）；
`selfcheck` ALL GREEN（models 116 / blockstates 25 / lang keys 444 / resources 466）；
死代码 0、孤儿 0。

### 12.169 第 169 批（2026-10-23）：枫树贴图三换（银树原木 / 樱花叶改色 / 橡树苗）+ 修合成 bug

用户三条反馈：
> 1. 枫叶原木贴图改画银树原木，整体深一点。树叶用樱花树叶改色 粉色就改为红色 绿色点缀改为橘色点缀
> 2. 原木分解的代码有问题，合成不了。树苗贴图用橡树树苗改，创造栏位子改为枫树数叶后面

**① 合成 bug 的根因：ingredients 写了裸字符串**。第 168 批的
`recipe/maple_planks.json` 把原料写成 `"skydeitymysticpivot:maple_log"`（裸字符串），
而 1.21.1 的 `Ingredient` codec 是 `either(list, map)`——map 侧是
`xor(ItemValue{"item"}, TagValue{"tag"})`，**裸字符串既不是 list 也不是 map，整条配方
静默解析失败**，游戏里查无此方。修法：`{"item": "skydeitymysticpivot:maple_log"}`。
全 jar 配方扫了一遍，这是唯一一处裸字符串（其余 107 条都是对象式）。
已进 §9 坑集第 45 条。

**② 原木改画银树（深色化）**：形制扒本体 `silverwoodside.png` / `silverwoodtop.png`
（tc.jar 内）——竖纹树皮柱（左窄暗柱 → 宽亮柱 → 深缝 → 中明柱 → 右暗棱）+ 三处对角节疤
（银树招牌）+ 顶面同心年轮；但**不逐像素抄**（原版 231 色带抖动，违反 §7.10 零抖动），
按同一形制用五档平色重画，整体亮度压到原版六成（原版最亮 #DDDACF → 本版 #8A8064，
用户口径「整体深一点」）。**木板跟着压深**（板是原木锯出来的，不同色系说不过去），
枫木原木/木板的 mapColor 也换成深陶灰 `TERRACOTTA_GRAY` / `TERRACOTTA_LIGHT_GRAY`。

**③ 树叶换樱花结构**：原版 `cherry_leaves.png` 15 色 / 216 像素（84% 覆盖的密花冠），
字符矩阵内嵌进 gen 脚本，**只换色表不换结构**——11 档粉逐一映射 11 档红（明度次序不变），
3 档橄榄绿点缀映射 3 档橘。覆盖率从第 167 批自绘的 41% 提到 84%，树冠从此是「一球红花」。

**④ 树苗换橡树结构**：原版 `oak_sapling.png` 9 色的疏枝小苗，叶子四档绿 → 红橘四档，
茎四档棕 → 深银四档（与银树原木同色系，呼应「银树」）。第 167 批自绘的三片大叶子偏满，
换成橡树苗的疏枝尺度更像树苗。

**⑤ 创造栏**：木板从「原木后」移到**枫树叶后面**（原木 → 树叶 → 木板）。

**产物**：`SkydeityMysticPivot-0.1.0.jar`（SHA256 `852f5a45…2aba9e3`）；
`selfcheck` ALL GREEN（models 116 / blockstates 25 / lang keys 444 / resources 466）；
死代码 0、孤儿 0；jar 内 107 条配方全部对象式原料，无裸字符串残留。

### 12.170 第 170 批（2026-10-23）：原木换真银树结构（修透明顶）/ 鲜红枫叶 / 橡木板结构 / 世界生成审计

用户三条反馈：
> 1. 创造栏位子为枫树原木 枫树木板 枫树树苗
> 2. 枫树原木贴图还是有问题，顶部透明的，你就用银树原木改颜色，然后枫树树叶在红一点，鲜红。
>    枫树木板纹理按原版橡木木板修改
> 3. 检测生成的代码有没有问题

**① 「顶部透明」的根因与修法**：第 169 批自绘顶面是「圆盘 + 四角透明」（disc 半径 7.6
盖不满 16×16 四角），而原版原木顶面必须**整面不透明**——透明像素在游戏里是黑洞，
方块顶面缺四角。用户口径「你就用银树原木改颜色」：不再自绘，直接扒本体
`silverwoodtop.png` / `silverwoodside.png` 的**像素结构**（按亮度量化 6 档的字符矩阵
内嵌进 gen 脚本），映射到深色银棕 palette。银树顶面原图 256/256 全不透明，
换真结构后透明问题从根上不存在；顺带第 169 批自绘节疤位置与原版对不上的问题也一起消失。
整体亮度仍压原版六成（用户此前口径「整体深一点」）。

**② 树叶推鲜红**：樱花结构不变，11 档红 ramp 整体去粉感、提饱和
（最浅 #FA9E94 → 最深 #A81414；第 169 批最浅 #F4B2A4 偏粉）。

**③ 木板换原版橡木木板结构**：扒 `oak_planks.png`（7 色 / 四道横板 + 板顶深缝行 3/7/11/15
+ 板身木纹）量化 5 档，映射深银 palette，与深色原木同色系。

**④ 创造栏定为**：枫木原木 → 枫树木板 → 枫树树苗 → 枫树叶（树苗从「戒指后」移回方块组，
按用户给的三件顺序；树叶未点名，收在树苗后）。

**⑤ 世界生成代码审计（用户第 3 条）——逐一对照 codec 核过，无问题**：

* `configured_feature/maple_tree.json`：`minecraft:tree` 的 `TreeConfiguration.CODEC`
  十个字段名，与 `fancy_trunk_placer`（base_height / height_rand_a / height_rand_b）、
  `fancy_foliage_placer`（radius / offset / height）、
  `two_layers_feature_size`（limit / lower_size / upper_size / min_clipped_height）
  全部对上，与原版 fancy_oak.json 逐字段一致；树叶态 `distance=7 / persistent=false /
  waterlogged=false` 即 `defaultBlockState`，与原版橡叶同规。
* `placed_feature/maple_tree{,_dense}.json`：`count` 纯整数合法
  （`IntProvider.CODEC` 是 CONSTANT_OR_DISPATCH，原版 233 个 placed feature 里 90 个用纯整数）；
  `in_square` / `surface_water_depth_filter(max_water_depth)` / `heightmap(OCEAN_FLOOR)` /
  `biome` / `block_predicate_filter(predicate=would_survive, state)` 全部存在且字段名正确。
* `biome_modifier` 两个 JSON：`neoforge:add_features` 的 biomes（id 列表 / 单 id）+
  features + step=vegetal_decoration，格式与 NeoForge `AddFeaturesBiomeModifier` 文档一致。
* 运行时生长路径：`MapleTreeGrower` 现构的 `ConfiguredFeature` 直接 `Feature.TREE#place`，
  与原版世界生成走**同一条** `TreeFeature.place`——包括 `updateLeaves` 的 BFS 叶距重算
  （放置即修正 distance，不会误枯萎）与 `getMaxFreeTreeHeight` 的空间预检
  （长不下返回 false、树苗留任）。

**产物**：`SkydeityMysticPivot-0.1.0.jar`（SHA256 见 §10）；
`selfcheck` ALL GREEN（models 116 / blockstates 25 / lang keys 444 / resources 466）；
死代码 0、孤儿 0。

### 12.171 第 171 批（2026-10-23）：树形离线预览 / 原木转褐 / 木板干净化

用户两条反馈：
> 1. 生成我还没看到 然后树的形状使用代码写的吗
> 2. 原木颜色往褐色一点，木板贴图干净一点

**① 「树形是不是代码写的」——回答 + 离线预览**

树形**不是本 mod 手写的网格**：`MapleTreeGrower` 现构的三件参数
（`FancyTrunkPlacer(3,11,0)` + `FancyFoliagePlacer(2,4,4)` + `TwoLayersFeatureSize(0,0,0,4)`）
喂给**原版** `Feature.TREE`，干多高、枝从哪长、冠多大，全由原版 fancy oak 算法决定；
树苗生长（Java）与世界生成（JSON）两路只是把同一套参数送到同一个原版特征里。

用户说「生成我还没看到」，于是新增 **`tools/preview_maple_tree.py`**：
把 `FancyTrunkPlacer.placeTrunk / makeLimb / makeBranches / treeShape / trimBranches`、
`FancyFoliagePlacer.createFoliage / shouldSkipLocation`、`BlobFoliagePlacer.placeLeavesRow`
以及 `TreeFeature.doPlace` 的高度/半径计算**逐行移植成 Python**，在空世界里跑一遍，
再用枫树自己的贴图渲染「正立面 + 顶视图」。用法：
`python tools/preview_maple_tree.py [种子] [输出] [front]`。
产物 `tools/preview/maple_tree.png`（单棵，正立面 + 顶视）与
`tools/preview/maple_trees.png`（三棵 5/8/12 格树干并排，看尺寸跨度）。

渲染口径：正立面**优先画原木**（木质 X 光）——纯「最近方块」画法会把整棵树埋进树冠，
侧枝本来就长在冠里，优先画木才读得出「干 + 四向侧枝 + 冠」的树形。

**② 原木转褐**：palette 从第 170 批的银灰棕改成**深褐**（R 上提、G/B 压）：
a (34,27,17) → f (158,124,76)；地图色随之从 TERRACOTTA_GRAY / TERRACOTTA_LIGHT_GRAY
换成 TERRACOTTA_BROWN / COLOR_BROWN。结构仍是本体银树原木的（第 170 批口径不变）。

**③ 木板干净化**：第 170 批是量化原版橡木木板改色，量化把原版的细碎木纹噪声一起带进来。
改成**手绘干净版**：每道板只有「上缘受光 + 板身平色 + 下缘深缝 + 一道竖接缝（逐排错开
5/11/3/9）+ 一道 1px 木纹 + 右侧亮棱」，零随机噪声，配色跟着原木转褐。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 701 057 B · SHA256 `a79feb04…b118ab`；
`selfcheck` ALL GREEN（models 116 / blockstates 25 / lang keys 444 / resources 466）；
死代码 0、孤儿 0。

---

### 12.174 第 174 批（2026-10-24）：玄枢虚空采矿机（新多方块）

**① 需求**：新方块「玄枢虚空采矿方块」（科技感 + 仙家风格贴图），新多方块「玄枢虚空采矿机」，
效果是**产出所有矿**——要通用 tag、不单独适配任何模组、含下界合金；结构 3×3 底座 +
中心上一格沟灵核心 + 再上一格采矿方块；法杖右键核心激活变整体模型；UI 与玄枢炼金炉同款亮色 AE 风。

**② 矿物通用标签 `#c:ores`**：NeoForge Conventional Item Tag。判定用
`TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", "ores"))` +
`BuiltInRegistries.ITEM.getTag(...)`，含 `#forge:ores` 兼容（NeoForge 自动桥接），
`c:ores/netherite_scrap` 里就有 `minecraft:ancient_debris`（下界合金原料）。清单在服务端首次使用时静态缓存，换世界/重启自动重建。

**③ 结构**（十一格，核心外全是采矿方块）：

```
  y+2        [采]              ← 顶盖（FORMED 属性单独控制）
  y+1        [核]              ← 玄枢沟灵核心（法杖右键这里激活/解除）
  y+0   [采][采][采]
        [采][采][采]            ← 3×3 底座；中心底座格 = 锚点（BE 干活那格）
        [采][采][采]
```

**④ 成型 / 散架**：`XuanshuSpiritCoreBlock`（原 `Block::new` 升级为内部激活类）法杖右键
`tryForm`（校验 3×3 + 顶盖 + 法杖六基元各 64 点 SIMULATE/EXECUTE 两段扣）→ 11 格 `FORMED=true`、
锚点 BE 记 `corePos`、灯塔音 + 末影珍珠粒子；潜行右键 `dissolve`。`onRemove` 破任一成型格 →
`findCore` 反推核心（垂直 ±2 + 斜向搜索覆盖底座斜角与顶盖）→ `dropContents` + `dissolve` 全体散架。
成型态方块 lightLevel 6→10，贴图换充能版（`_side_formed`/`_top_formed`，青蓝回路点亮）。

**⑤ 产出**：锚点 BE 每 100 tick（5 秒）产一组：随机一种矿 × 1~3 个，塞 9 格产出栏；满仓退回进度下一轮再试（不丢失）。
UI 为 `XuanshuGui` AE 亮色面板 + 3×3 产出栏 + 右侧竖排青金进度条，`ContainerData` 同步进度/周期/锚点坐标。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 701 943 B；
`selfcheck` ALL GREEN（models 119 / blockstates 26 / lang keys 450 / resources 476）；
死代码 0、孤儿 0。

### 12.178 第 178 批（2026-10-24）：采矿机成型改 OBJ 三级密檐方塔

**① 需求**：多方块成型后的模型要「选一个简单好建模的古代塔，用 obj 实现，成型后切换成 obj」。
上一批（第 177 批）已经试过 OBJ，但**法杖激活后什么都不显示**。

**② 为什么不显示：材质名对不上，mesh 被整块丢掉**

上一批 `altar_base.obj` 写的是 `usemtl bronze`，而 `altar_base.mtl` 里定义的是 `newmtl altar_base`。
NeoForge 的解析链是这样断的：

```java
// ObjModel.parse
ObjMaterialLibrary.Material newMat = mtllib.getMaterial(mat);   // ← 找不到就抛 NoSuchElementException

// ObjMaterialLibrary.getMaterial
if (!materials.containsKey(mat))
    throw new NoSuchElementException("The material was not found in the library: " + mat);

// ObjModel.ModelMesh.addQuads
if (mat == null) return;                                        // ← 就算不抛，也是一面都不画
```

叠加第二条：那两个 obj **一个 `vt` 都没有、一个 `vn` 都没有**。没有 `vt` 时
`makeQuad` 会退回 `DEFAULT_COORDS`（整张贴图拉伸到一个面）；没有 `vn` 时法线靠绕序反推，
绕序反了就被 `solid` 渲染层当背面剔掉。

三条一起，编译、`selfcheck`、死代码扫描全绿，游戏里却是一片空气 —— 这类问题只能靠
「对着 loader 源码逐条核」才能提前发现。

**③ 形制：三级密檐方塔**

取「七级浮屠」的密檐语言压成三级（结构只有三格高），塔身逐级收分、檐口层层外挑，
顶上覆钵、相轮、宝珠。48px 正好占满三格：

| 格 | y 范围 | 内容 |
|---|---|---|
| y+0 底座 3×3 | 0..16 | 台基两级须弥座 0..4.2 / 4.2..6，第一级塔身 6..13.2，檐 13.8..16 |
| y+1 核心 | 16..32 | 第二级塔身 16..26.5，檐 28.5..32 |
| y+2 顶盖 | 32..48 | 第三级塔身 32..40.5，檐 41.8..43，塔刹 43..48.4 |

**④ 十一个格子怎么只用四个 obj**

关键点：**外圈八格可以共用一个模型**。每格模型只画「自己这一格范围里的那段台基」，
位置由方块自身坐标决定，不需要按边、角分别做模型。于是：

| obj | 用在哪 | 格子数 |
|---|---|---|
| `tower_base` | 外圈 8 格（边 + 角） | 8 |
| `tower_base_center` | 中心格（台基 + 第一级） | 1 |
| `tower_core` | 核心格（第二级） | 1 |
| `tower_top` | 顶盖格（第三级 + 塔刹） | 1 |

贴图 `tower_stone.png`（32×32）分四区：砌石立面 / 石面 / 鎏金 / 暗金，
每个面按**自己的真实像素尺寸 1:1 取样**（`du = 面宽 / 16`），砖缝才不会被压成一团。

**⑤ 核心格必须一起换模型**

核心正好卡在第二级的位置。不处理的话，塔的腰部会缺一格 —— 玩家看到的是一座腰上开洞的塔。
所以给 `XuanshuSpiritCoreBlock` 补了 `FORMED` 属性，`tryForm` 写、`dissolve` 收。顺带三处：

- `onRemove`：核心不是 `XuanshuVoidMiningBlock`，挖它不会走那边的散架逻辑，
  不补一刀就会留下十格 `formed=true` 的空塔；
- `useWithoutItem`：成型后空手右键核心也能开界面（原来只有法杖右键有反应）；
- `lightLevel`：成型后 6 → 12，塔里透出光。

**⑥ 结构自愈**

锚点每 5 秒（`getGameTime() % 100 == 0`）核一次：核心还停在未成型态就补上（旧存档升级），
核心或顶盖不在、底座缺格就散架。开销是一次方块查询，可以忽略。

**⑦ 两个新工具**

- `tools/gen_orb_obj.py`：程序化生成整体模型 obj + mtl + 贴图 + 模型 json（第 182 批起是悬浮球）。
  内置两条硬校验 —— **每个面的绕序叉积必须与声明的外法线同向**、**退化盒（任一维为 0）直接报错**。
  第二条当场抓到一处 bug：叠涩高度算成了 0（檐底与塔身顶写成了同一个值）。
- `tools/preview_obj_iso.py`：解析 obj + mtl + 贴图，等距投影（含 z-buffer 与方向光）或正交正视图，
  画布按内容自动裁。没有它，这批的收分比例要靠反复进游戏才看得出。
- `tools/check_obj_models.py`：模拟 NeoForge 的解析规则做静态检查 —— blockstate 引用的模型是否存在、
  `mtllib` 是否解析得出、每个 `usemtl` 在 mtl 里有没有同名 `newmtl`、每个 `map_Kd` 贴图是否存在、
  每个面有没有 `vt`/`vn`、绕序与法线是否同向。当前全模组 134 个模型、其中 14 个 obj、1382 个面全部通过。

这三个和 `tools/obj-simplify/` 里既有的三个分工不同，别搞混：

| 工具 | 管什么 |
|---|---|
| `obj-simplify/fixobj.py` | **单个 obj** 的语法与索引合法性（非法行、下标越界） |
| `obj-simplify/render_obj.py` | **单个 obj** 的出图（SlashBlade 刀模型那套） |
| `check_obj_models.py` | **整条链**：blockstate 到 json 到 obj 到 mtl 到贴图，以及 `usemtl`/`newmtl` 对账 |
| `preview_obj_iso.py` | **多格拼装**出图：按世界坐标摆十几个 obj，等距或正视 |

四个都跑一遍，新模型基本不会在游戏里出意外。

**⑧ 删掉的东西**

`altar_base.obj/.mtl`、`altar_globe.obj/.mtl`、四个 `altar_*.json`、六张 `altar_*.png`、
两张 `xuanshu_void_mining_*_formed.png`（早已无人引用）、以及 `tools/gen_altar_obj.py`
与 `tools/gen_altar.py`（重跑只会重新生成孤儿）。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 712 404 B，SHA256 `e53c7b4e…ef429d`；
`selfcheck` ALL GREEN（models 122 / blockstates 26 / lang keys 451 / resources 483）；
OBJ 渲染链路 ALL GREEN；死代码 3（均为历史遗留 import）、孤儿 0。


### 12.179 第 179 批（2026-10-24）：采矿机改单一整体 OBJ / 烛龙补配方 / 四法核锯齿链

**① 从「四个分块 obj」改成「一个整体 obj」**

第 178 批是四个 obj 分派十一格。能显示，但接缝处必然出两件事：台基在每格交界被切一刀，
砖缝对不上；檐口跨格处两段檐各自出檐，厚度不连续。远看就是「九块拼的」。

改法：**让中心格一格承载整座塔**。Minecraft 的方块模型并不要求几何落在 0..16 之内 ——
`BakedQuad` 的顶点是 float，超出去照样画，`automatic_culling: false` 时也不会按邻居剔面。
所以：

| blockstate | 模型 |
|---|---|
| `formed=false` | `xuanshu_void_mining`（未成型的单块） |
| `formed=true, role=center` | `block/tower`（整塔，obj 包围盒 x/z −0.97..1.97、y 0..3） |
| `formed=true` 其余 role、以及核心格 | `block/tower_empty`（只有 particle，没有 elements） |

`tower_empty` 不能是 `minecraft:air` —— blockstate 引不到它，破坏时的粒子也会不对。

代价明说：整座塔共用中心格的光照，夜里或室内顶部会偏暗；区块边界远处也可能整块不画。
三格高的塔可以接受，换来的是几何真正连续。

**② 形制：三层楼阁式方塔**

48px 高、48px 占地的比例只有 1:1，直接画三层必然「矮胖」。解法是把**台明与基座分开**：
台明 47px 铺满 3×3（九格方块都得有个底），基座只 24px，塔身收到 18px，高宽比才拉到 1.85:1。

| y（px） | 构件 |
|---|---|
| 0 – 2.2 | 台明 47px，铺满 3×3 |
| 2.2 – 2.9 | 台明压边 44.8px |
| 2.9 – 5.0 | 基座 24px |
| 5.0 – 20.4 | 一层塔身 18px，四面券门 + 门楣 + 转角倚柱 |
| 20.4 – 23.2 | 叠涩 15px + 一层檐 25px |
| 23.2 – 33.0 | 二层塔身 14px（同带券门与倚柱） |
| 33.0 – 35.4 | 叠涩 12px + 二层檐 17.5px |
| 35.4 – 42.0 | 三层塔身 11px，四面小窗 |
| 42.0 – 43.4 | 顶檐 13.5px |
| 43.4 – 48.0 | 塔刹：覆钵 / 相轮三重 / 刹杆 / 宝珠 |

券门不做真洞 —— obj 里挖洞要把一个盒子拆成四块，接缝比门本身还显眼。改成**凸出 0.4px 的深色门板
加一道鎏金门楣**，16px 的方块尺度上足够读作「门」。三层没有门，改**四面小窗**，否则那一圈塔身
读成一段空墙。

檐拆成两段：**下段暗金裙边（窄 1px）+ 上段鎏金檐板**。只画一块平板的话，2~3px 的厚度在侧面
完全读不出来，远看就是一叠盘子；裙边窄一圈，从下面打上去的阴影把厚度说清。出檐也收到每边
3.5px —— 再宽会在等距视角下把上一层塔身盖掉一半。

贴图换成 64×32（八个 16×16 分区）：细砖 / 石面 / 鎏金 / 暗金 / 门板 / 台基大石板 / 受光石面 / 备用。
**UV 不做 1:1 取样**，整块映射到面，靠「按面的性质选分区」来保质感 —— 这张贴图上的面跨度从 11px 到 47px，
1:1 时大面取不下（要 UV 超过 1 去平铺，atlas 采样不一定 repeat），小面又会正好落在砖缝前面变成纯色。

**③ 烛龙衔时：补回整套法核规格**

第 166 批曾把它的配方删掉（当时当创造栏专属）。用户现在要「和别的法核一样，有合成表有功能」，
于是四处一起补：

- `recipe/focus_chrono.json`：中心 `minecraft:clock` 1，八格红石 4 + 水银 4，八卦各 64，不稳 6；
- `trigram_gate/machines.json` 加 `focus_chrono → gen`（与另三枚同一条，艮卦石一块）；
- 研究页补 `INFUSION_CRAFTING`（recipe id `skydeitymysticpivot:focus_chrono`）与 `bagua.recipe.focus_chrono` 说明页，
  页面顺序对齐另三枚：功能 → 注魔图 → 合成要求 → 逸话；
- 中英 lang 各补一条 `bagua.recipe.focus_chrono`。

**④ 四枚法核：为什么只能排成父子链**

四个方向都挂在震卦下时，父连线的起点抖动 `sin(...)*5.0F` 让同源两条边的夹角必须 ≥30° 才不缠。
GUA_GEN 周围右下有坎卦（−45°）、左上有坤卦（135°），可用扇面只有约 100° 宽 —— 放不下四个
间隔 30° 的方向。整数格上无论怎么摆，总有一对落在 26° 以下。

改成**父子链**后每条边各从自己的父节点出发，天然不重叠，坐标正好排成 W 形：

| 核心 | 坐标 | 父 |
|---|---|---|
| 乾元罡气（守护） | (1, 4) | 震卦 |
| 烛龙衔时 | (2, 3) | 守护 |
| 镇狱碎青峰（宇宙） | (3, 4) | 烛龙 |
| 赤羽焚天瞬（传送） | (4, 2) | 宇宙 |

四枚都带 `AUTO_UNLOCK`，解锁前一个后下一个自动解锁，不会卡流程。全树复核：
**线交叉 0 处、四核心区贴线 0 处、同源夹角全部 ≥30°**。
（另有两条历史遗留未动：`BAGUA→PEDESTAL` 掠 `GUA_ZHEN` 10.7px、`GUA_XUN` 到炎炉/坩埚夹角 26.6°/18.4°，
都是机器链的既有布局，改动会影响解锁路径，留待后续单独处理。）

**⑤ 清理**

删 17 个一次性脚本（`apply_batch74~150`、`batch150_*`、`patch_batch151/155`、`trim_guidebook*`、
`build_batch_index`、`ghost_extract`）、7 张调试渲染图（altar / maple / void_mining / tower_atlas）、
以及整个 `tools/work/`（audit、codex、两份 render_obj）。留下的只有能重建资源的 `gen_*.py`、
自检三件套（`selfcheck` / `deadcode_scan` / `orphan_scan`）与预览工具。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 716 477 B，SHA256 `af3f87e9…d466f9`；
`selfcheck` ALL GREEN（models 120 / blockstates 26 / lang keys 452 / resources 479）；
OBJ 链路 ALL GREEN（134 个模型引用、4 个 obj、1467 个面）；死代码 3（历史遗留 import）、孤儿 0。


### 12.180 第 180 批（2026-10-24）：塔贴图重画成宫塔 / 采矿机自动化挂核心 / 四法核共连艮卦

**① 「贴图错位」的真因：整块映射**

第 179 批的 UV 是「每个盒子的每个面都把一整块 16×16 分区拉满」（`du=dv=1`）。于是：

- 47px 的台明和 2px 的檐口侧面**拉到同样大小** —— 金檐上的竖向錾刻线在檐口被压成几道粗竖条，
  看着像一排木栅栏；
- 门板被拉满整面墙，四个面都是「一整扇大门」；
- 砖缝在盒与盒交界处**必然断开**（每块重新起头）。

改法是**世界坐标 UV**：每个面的 `u/v` 直接取该顶点在整塔像素坐标里的位置，1 世界像素 = 1 纹素：

```python
FACE_AXES = {                      # 面的 (u 轴, v 轴)
    "north": (0, 1), "south": (0, 1), "east": (2, 1), "west": (2, 1),
    "up":    (0, 2), "down":  (0, 2),
}
u, v = uv(region, point[ui], point[vaxis])   # 不归一化，图案因此跨盒连续
```

三个直接收益：砖缝/瓦垄连续；纹素密度处处一致（16 纹素 = 1 格）；**所有面的图案都以塔心
`u = 24` 为对称轴** —— 四个方向的门本来就都以 `C=24` 为中，于是门缝正好落在每扇门正中，
不需要给门单独做「适配 UV」。代价是贴图必须是周期图案（同一分区被不同高度的构件反复取样），
所以每个分区都按「1px 宽的变化 + 周期」来画。

**② 配色：从「灰蓝石头蛋糕」换成宫塔**

| 分区 | 用在哪 | 画法 |
|---|---|---|
| `marble` | 须弥座三级 | 8px 一层、16px 一块错缝，汉白玉近纯色 + 一道上亮棱 |
| `wall` | 三层塔身 | 朱红，6px 细砖错缝，砖面上亮下暗 |
| `pillar` | 转角倚柱 | 深朱红整根一色（柱宽才 1.6px，横向图案看不清） |
| `teal` | 斗拱 / 彩画带 | 青绿，每 4px 一个金点（带高 1px，图案不能随 lv 变） |
| `glaze` | 屋面顶 | 黄琉璃，4×4 一格瓦沟，顶面读成「瓦」而不是「木板条」 |
| `roof` | 檐口侧面 | 匀色黄琉璃，**不画竖纹** |
| `wood` | 檐底望板 / 瓦口薄板 | 深棕，每 4px 一根椽子 |
| `gold` | 塔刹 / 门楣 | 鎏金横向亮带 + 竖向錾刻 |
| `door` | 板门 | 朱漆两扇，边梃常驻 `lu 21/27`，门缝在 24 |
| `window` | 三层直棂窗 | 深窗洞 + 正中一根亮棂 |

**③ 檐：一~两像素厚的侧面放不下竖纹，暗线交给几何**

檐原本是「下窄上宽」两段，像盘子边。现在反过来，并且多一层：

```
bot       ─┬─ 瓦口：比檐口再外挑 0.35px 的深色薄板（0.4px 厚）—— 远看就是檐下一道阴影
bot+0.4   ─┼─ 檐口：最外一圈琉璃（匀色侧面）
mid       ─┼─ 屋面：向内收 2.2px，顶上铺瓦沟
top       ─┘
```

「滴水」这种竖向结构在 1~2px 的侧面里根本无处安放（纹素行就那么一两行，还会被 mipmap 抹平），
所以那道暗线必须由几何给出。斗拱带同时加高到 1.2px 并**外挑 0.8px** —— 之前它比塔身还窄 1.5px，
藏在塔身下沿里，等于没画。

**④ 采矿机：自动化与加速都挂到沟灵核心那一格**

机器的「心脏」是沟灵核心（`corePos`），锚点只是藏在底座里的储物格。所以：

- `autoOutput()`：每 10 tick 把产出栏推给**核心四周**六个面能拿到的 `IItemHandler`
  （跳过本机自己的塔身方块，否则会推给自己）；
- `getExternalItems(side)`：对外给的物品表**只在锚点、且成型时**存在，管道可以从核心相邻格里取放
  （插入只许进空格，免得外面的东西把产出栏占死）；
- 加速：`TemporalCore.rateAt(level, corePos)` —— 点塔上任意一格，`cast` 先折算到核心再登记；
  核心没有方块实体，拿不到 ticker，于是登记表里给这类目标一个 `polled` 标记，`tickEntry` 只走时限、
  倍率由机器的 tick 自己来问一句。倍率悬浮字抬到核心上方 3.1 格（塔顶之上），不然埋在塔身里看不见。

产出循环随之改成 `progress += steps`（`steps = 倍率 or 1`），`while` 里最多补 64 轮；满仓就停在
「下一轮就出」，不丢进度也不空转。

**⑤ 四枚法核同挂艮卦**

用户否掉了第 179 批的父子链。四个方向都从震卦（`GUA_GEN`，(1,1)）出发，落在一条对角线上：

| 核心 | 坐标 | 夹角 | 离源 |
|---|---|---|---|
| 乾元罡气 | (5, 2) | 14.0° | 99px |
| 烛龙衔时 | (4, 3) | 33.7° | 86px |
| 镇狱碎青峰 | (3, 4) | 56.3° | 86px |
| 赤羽焚天瞬 | (2, 5) | 76.0° | 99px |

相邻间隙 19.7° / 22.6° / 19.7°，是这一象限整数格里最均匀的一组（可用扇面只有右侧到下方约 80°，
坎卦、坤卦、地形链把上下都占了）。夹角不到 30° 的合理性见 §9 第 57 条。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 723 910 B，SHA256 `5fb44ff5…45f938`；
`selfcheck` ALL GREEN（models 120 / blockstates 26 / lang keys 453 / resources 479）；
OBJ 链路 ALL GREEN（134 个模型引用 / 4 个 obj / 1490 个面）；死代码 2（历史遗留 import）、孤儿 0。

### 12.181 第 181 批（2026-10-25）：塔整体改科技风 / 八角化 / 贴图三处错位

用户原话：「整体科技风格，不要这么丑，颜色鎏金 白 银为主，底座不要方方正正的瓷砖」+
「贴图还是有问题 修改」。第 180 批的宫塔配色（朱红 / 青绿 / 琉璃）整个作废。

**① 为什么方盒子怎么贴都像瓷砖**

方柱的四面是四块**平板**，砖缝一定是横平竖直的网格 —— 无论画砖、画板、画窗，眼睛都读成
「墙砖」。第 179/180 两批反复改贴图，改的一直是「砖怎么画」，而问题在**形状**。

这一批换成**八棱柱**：每个面只有 7.6px 宽，侧影是多边形，转角自带明暗变化，天然读成
「机械构件」；一棱面正好对应一整格面板图案，绕一圈不留缝。底座同理，47px 方台改成
**八角台**（边心距 23.8，四角切掉）——用户说的「方方正正的瓷砖」就是这块。

**② 形制**

| 段 | y（px） | 尺寸（边心距） | 材质 |
|---|---|---|---|
| 基脚 | 0.00–0.90 | 八角 23.8 | 暗银（0.9px，读作一道阴影） |
| 主台 | 0.90–2.20 | 八角 22.6 | 白瓷 + 放射徽记 + 沿口金圈（0.4px 环带） |
| 台阶 | 2.55–3.80 | 八角 16.4 | 银 |
| 束腰 | 3.80–7.00 | 11.2→10.3 | 银（收分） |
| 金箍 | 7.00–8.10 | 14.2 | 鎏金 |
| 一层 | 8.10–19.40 | 12.4 | 白瓷板 |
| 金檐 | 19.40–21.20 | 13.4 | 鎏金侧沿 + 浅银台面 |
| 二层 | 21.20–28.40 | 10.2 | 白瓷板 |
| 金檐 | 28.40–29.80 | 11.4 | 同上 |
| 三层 | 29.80–34.20 | 8.2 | 白瓷板 |
| 金冠 | 34.20–35.60 | 9.4 | 鎏金 |
| 能量核心 | 35.60–40.40 | 4.9→4.2 | 发亮白金（每 16px 一道白环） |
| 顶盖 | 40.40–41.30 | 4.6 | 鎏金 |
| 悬浮结晶 | 41.80–48.00 | 八面体 r=4.0 | 白金切面（与顶盖留 0.6px 空隙） |

配色只有四档：白瓷 `#E7ECF4`、拉丝银 `#C2CAD6`、鎏金 `#E2B55C`、暗金 `#7C581E`。
金色**只出现在环、冠、盘心和金边**上 —— 塔身一旦加竖金条，就会和横环织成一张网，
整座塔读成鸟笼（中途那一版就是这样，已删）。

**③ 三个几何原语（`tools/gen_tower_obj.py`，第 182 批改名为 `gen_orb_obj.py`）**

```python
o.prism(y0, y1, a0, a1, side, top=None, bottom=None, sides=8)  # 八棱柱，可收分
o.band(y0, y1, a_out, a_in, side, top=None)                    # 八角**环带**（金圈）
o.octahedron(y_bot, y_top, r, region)                          # 悬浮结晶
o.face(pts, region, uvs, expect=(nx,ny,nz))                    # 任意多边形 + 绕序自检
```

`expect` 是「大致朝外」的方向：绕序叉积与它反向时**自动 reverse** 并记账
（`绕序自动纠正 N`）。八棱面、八面体的斜面手算极容易反，而反了在
`automatic_culling:false` 下就是**那一面彻底不见**，且没有任何报错。

**④ 两种 UV，各管一半**

  * **侧面 = 棱面归一化**：`u = t * PANEL`（一棱面 = 8 纹素 = 一整格图案），`v = 世界 y`。
    于是八个棱面图案完全一致，绕一圈天然接得上 —— 第 180 批那种「缝在盒与盒之间断掉」
    从根上不可能再发生。
  * **顶面 = 平面归一化**：八角顶面按包围盒映射到一整格 48×48。只用在**专为平面画的**分区：
    `medal0`（放射徽记）、`cap`（均匀金）、`steel`（均匀浅银）、`socle`（均匀暗银）。
    把带竖缝的 `panel`/`gold` 铺到平面上，竖缝会被画成一圈**虚线网格** —— 这一批踩了三次。

**⑤ 贴图三处错（用户说的「还是有问题」）**

| 现象 | 根因 | 修法 |
|---|---|---|
| 台面外圈一圈金色虚线 | 环宽 0.8 纹素，1:1 取样 + 投影量化，线活不下来 | 环加粗到 ≥2.5 纹素；贴边那圈直接交给**几何**（0.4px 环带） |
| 台面上有一圈「梳齿」 | 平面图案用了**八边形度量**画同心环，45° 边在纹理里先量化一次、投影再量化一次 | 图案元素全部改**横平竖直**（切比雪夫方框 + 连续渐晕），只保留一层投影台阶 |
| 每个棱面上一个「小窗口」 | `g_panel` 先判竖缝再判横缝，横缝在每个棱面两端各断 1 纹素（棱面只有 8 纹素，断 2/8） | **横缝先判、通长画**，竖缝让位 |
| 金面像木纹 | `gold2` 的噪声按 8×4 分块，铺在平面上就是木纹 | 平面金面用 `cap`（纯渐变），`gold` 的噪声只随 u 变（v 向噪声在 1~2px 高的侧面上全变成横条） |

**⑥ 预览器**

`preview_obj_iso.py` 的光照方向原来在 `(-0.42, 0.80, -0.43)`，而等距视线是从 `+x+y+z`
看进来的 —— 屏幕上全是背光面，白瓷被压成土黄，很容易误判成「贴图不对」。改成
`(0.52, 0.72, 0.46)` 与视线同侧。另外补了三角形面的支持（八面体结晶）。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 721 914 B，SHA256 `f02135eb…f63b416`；
`selfcheck` ALL GREEN（models 120 / blockstates 26 / lang keys 453 / resources 479）；
OBJ 链路 ALL GREEN（134 个模型引用 / 4 个 obj / 1416 个面，塔本体 232 面 928 顶点）；
死代码 2（历史遗留 import）、孤儿 0。

### 12.182 第 182 批（2026-10-25）：塔整个换成悬浮球

用户原话：「换一个模型吧，之前举例的球那种，这个直接不要了」。第 181 批那座八棱塔**整段删除**。

**① 形制**

| 段 | y（px） | 尺寸（边心距 / 半径） | 材质 |
|---|---|---|---|
| 基脚 | 0.00–0.90 | 八角 23.8 | 暗银（0.9px，读作一道阴影） |
| 主台 | 0.90–2.20 | 八角 22.6 | 白瓷 + 放射徽记 + 0.4px 几何金边 |
| 台阶 | 2.55–3.80 | 八角 16.4 | 浅银 |
| 托座 | 3.90–6.00 | 12.8 → 9.6（收分） | 拉丝银 |
| 发光台 | 6.00–6.40 | 八角 6.0 | 暗金侧面 + 发光顶面（球从这块面上浮起来） |
| **球** | 15.6–45.6 | **半径 15.0，球心 30.6** | 白瓷；赤道 ±7.5° 与两极帽为鎏金 |
| 极轴 | 13.2–15.6 / 45.2–48.0 | 八棱 0.78 → 0.95 | 鎏金（浑天仪的枢轴，上下各露 2.4px） |

球底 15.6、发光台 6.4 —— 中间**留 9.2px（0.57 格）的空隙**。托座收得比球细 5.4px，
所以从任何角度看球都明显地「浮」在台外，不是「放」在台上。

**② `Obj.sphere()`：经纬球原语**

```python
o.sphere(cy, r, seg=32, lats=LATS, region="orb", band_of=lambda k: ORB_BANDS.get(k, "orb"))
```

  * `lats` 是纬度列表（度），段数 = `len(lats) - 1`；`band_of(k)` 决定第 k 段用哪个分区 ——
    **金色赤道带与两极帽就是这么来的**：哪一段用 `belt`，边界就是硬边、绝不会错位。
  * 极点用三角扇（`|cos(lat)| < 1e-6` 时判为极点），三角面由 `face()` 自动补成退化四边形。
  * UV 是标准经纬展开：`u = 经度一圈`、`v = (纬度 + 90) / 180`，北极在 v 顶。
    绕一圈 u 从 0 到 1，接缝处贴图是均匀色，看不见缝。
  * 外法线用「面心 − 球心」给，`face()` 据此自动纠正绕序 —— 这一批 480 个面里
    有 352 个是程序反过来的，手算根本不可能对。

**③ 为什么球面一条线都不画**

球直径 30px、屏幕上也就三十几个像素。画上经纬网（8 经 + 6 纬）立刻变成**铁丝球 / 灯笼骨架** ——
和上一批「塔身横竖都有线 = 鸟笼」是同一个坑。所以 `g_orb` 只有极淡的釉色起伏（6×6 一格抖动），
球感全部交给 `shade_quads: true` 按面法线的方向光：32 段经 × 11 段纬自带明暗层次。

同理，**水平外环**也试过（一道金环套在球赤道上），在等距视角下前半圈压在球面上，
把球拦腰切成一个「蘑菇」，读不出悬浮。改成**竖直极轴**（`prism` 上下各伸出 2.4px）：
不遮挡球面，反而把球读成浑天仪的枢轴。

**④ 又踩了一次「平面分区带线」**

发光台的顶面用平面 UV（整块 48×48 映射到八角面），而 `g_core` 当时带着
`if lv % 16 == 0: return (255,255,255)` 的白环 —— 那是给**塔身侧面**画能量环用的。
铺到顶面上，两道横线就成了**贯穿整个八角的斜白杠**（第 59 条的同一个坑）。
`g_core` 现在改成纯均匀色 + 缓渐变：**平面分区只允许均匀色，一条线都不能有**。

**⑤ 倍率字与注释**

球顶在核心上方 1.85 格，悬浮字原来按塔高抬 3.1 格（塔刹高度）。改成 2.35 格 —
正好在球面上方 0.5 格。`LABEL_TOWER_DY` 同步改名 `LABEL_ORB_DY`，Java 注释里
「塔腰 / 塔身 / 塔顶」这些说法全部改成「装置 / 球心 / 球顶」，免得下次照着注释找错东西。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 735 575 B，SHA256 `a6908021…b396cd`；
`selfcheck` ALL GREEN（models 120 / blockstates 26 / lang keys 453 / resources 479）；
OBJ 链路 ALL GREEN（134 个模型引用 / 4 个 obj / 1664 个面，球本体 480 面 1920 顶点）；
死代码 2（历史遗留 import）、孤儿 0。

### 12.183 第 183 批（2026-10-25，代码已落、文档缺，本批补记）：底座实心化 + 球面加纹

**这一批做了什么**（改动都在代码里，当时没落文档；结论第 184 批大部分作废，仅存配色口径）：

1. **九格底座换回实体方块**：第 182 批把成型格全换成空模型后，八角盘收角、侧面与四角
   能直接看穿底座 —— 改成每格按 `role`（中心 / 四边 / 四角）各一套贴图与 `cube_bottom_top`
   模型，OBJ 只负责中心格上表面的台座与球；
2. **球面加纹**：球贴图从 48×48 提到 96×96，赤道 8° 宽金带 + 上下各一圈八卦爻纹
   （爻线 2px 粗、5px 宽，相邻卦位错开 1.8px），经缝只留在赤道腰带里；
3. **未成型态并色**：采矿方块贴图从「墨玉深底 + 鎏金回路」改成「银白为体、鎏金为框、
   暗窗为心」，与成型态同一套配色。

### 12.184 第 184 批（2026-10-25）：采矿机去底座改两格结构 / 古风微调 / 贴图重画 / lore 精简

**① 需求**（用户原话，三条）：

1. 「现在矿机形成的多方块是有一个 1 格方块高的底座的，我不想要，然后小修改模型，
   可以整体更符合古代的建筑风格」；
2. 「精简采矿方块的 lore 介绍 描述清楚正常语言风格」；
3. 「修改采矿方块的贴图 机械修仙风格 鎏金白色 银色」。

**② 结构：十一格 → 两格（3×3 底座整层删除）**

```
  旧（第 174~183 批）                新（第 184 批起）
  y+2        [采]   ← 顶盖            y+1        [采]   ← 顶盖（挂祭坛 OBJ）
  y+1        [核]   ← 沟灵核心        y+0        [核]   ← 沟灵核心（= 机器基座）
  y+0   [采][采][采]  ← 3×3 底座
        [采][采][采]
        [采][采][采]
```

锚点跟着换格：从「中心底座格」变成「顶盖格」。所有「核心在下方」的假设全部翻面 ——
`tryForm` 只校验 `core.above()` 一格；`dissolve` 只收顶盖与核心；`coreFor` 简化为
「本格是核心且上方是采矿方块 → 自己」/「本格是采矿方块且下方是核心 → 下方」；
`selfHeal` 改看 `anchor.below()`；`XuanshuSpiritCoreBlock` 的开界面 / 掉落 / 解除三处
`pos.below()` 全改 `pos.above()`。`ROLE` 属性（中心 / 四边 / 四角 / 顶盖）随之删除，
blockstate 只剩未成型 / 成型两个变种：

```json
{ "when": { "formed": "false" }, "apply": { "model": "…:block/xuanshu_void_mining" } },
{ "when": { "formed": "true"  }, "apply": { "model": "…:block/orb" } }
```

核心 blockstate 回到单模型（`gen_spirit_core.py` 现在也负责写 blockstate）——
成型后核心照样渲染自己的白玉鎏金立方体，祭坛第一层就压在它头顶上，不必再藏。
删除的文件：`models/block/void_mining_base_{center,edge,corner}.json`、`orb_empty.json`、
`textures/block/void_mining_base_*.png` 七张。**自动化接口不受影响**（第 180 批起就挂在
`corePos` 上，核心仍是那一格，四面依旧空着接箱子 / 管道）。

**③ 模型微调 · 古代建筑风格**（`tools/gen_orb_obj.py`，形制不动、只动比例与纹样）

OBJ 挂点从中心底座格移到**顶盖格**，y=0 即核心顶面（旧坐标整体减 16px，写入处
`p[1] / PX` 不变，只重排常量）。古风四处：

  * **出檐**：鎏金底盘收窄（15.4 → 13.8 边心距），银白主台改成**上宽下窄的喇叭口**
    （14.0 → 15.0），台沿压在底盘外沿之上 —— 读作古代台基的出檐，全机最宽处就是台沿；
  * **连珠纹**：主台顶面外圈素金环改成 16 颗金珠一环（珠脊亮、珠谷暗，`g_medal`）；
  * **莲瓣柱身**：托座侧面从「一道嵌条」改成**中道鎏金筋 + 筋侧两道暗槽**的柱身断面
    （`g_s_ped`），八棱八面绕一圈就是一根收分柱；
  * **相轮刹**：球顶光溜溜的金针换成**小金盘 + 针**两件套（`FINIAL_DISC` / `FINIAL_PIN`）。

新形制（像素，y=0 = 核心顶面）：底盘 0–1.55（八角 13.8）→ 主台 1.55–3.35（14.0→15.0 出檐）
→ 下箍 3.35–3.95 → 托座 3.95–7.30（10.0→5.6 收分）→ 上箍 7.30–7.85 → 发光台 7.85–8.65
→ 球心 23.0、半径 12.6（球底 10.4，与发光台留 1.75px 缝）→ 相轮刹顶 37.5。
576 面 / 2304 顶点，绕序自动纠正 440 处。`tools/preview_void_machine.py` 跟着改：
基座预览从九格方块换成**一个核心立方体**（用沟灵核心三张贴图），祭坛整体上移一格。

**④ 采矿方块贴图重画 · 机械修仙**（`tools/gen_void_mining.py`，三张 16×16 全重画）

第 183 批的「暗窗 + 虚空微光」读作「虚空」，与机械修仙不是一路。新方案按**鎏金白 + 银**：

  * `_side`：银白**拉丝面板**（每 4 纹素一道亮 / 一道暗，固定分档）+ 两道机械分板缝
    + 上下鎏金压边三档 + 四角铆钉；中央暗窗换成**阵纹灵窗** —— 凹陷窗框（上左暗 /
    下右亮）+ 鎏金阵纹边 + 四角 L 形回路 + 中央回纹阵眼，窗底暖白灵气渐变；
  * `_top`：银白玉盘 + 鎏金双环 + 八向金钉 + 环间机械刻度（与钉错开 22.5°）
    + 中央灵核（金座 + 灵气 + 亮芯）；
  * `_bottom`：暗银底 + 一圈金环 + 四颗金钉。

**抖动归零**（沿第 173 批起的工程口径）：拉丝与纹样全部固定分档，素底不加随机噪声。

**⑤ lore 精简**（中英各三条 + 一条提示，全部改成清楚直白的正常语言）

| 键 | 旧 | 新 |
|---|---|---|
| lore1 | 摆 3×3 底座，中心上一格放沟灵核心，再上一格放此方块 | 在地上放沟灵核心，核心正上方放此方块 |
| lore2 | 法杖右键核心成型，产出世间所有矿石（含下界合金） | 法杖右键核心即可成型，每 5 秒产出一组矿石（含下界合金），产物进入 9 格产出栏 |
| lore3 | 自动输出口就是沟灵核心：核心东、南、西、北四面贴箱子 / 漏斗……（一句长的） | 在核心的东西南北四面放箱子或漏斗，产物会自动送入；用「烛龙衔时」催动核心可加速产出 |
| incomplete | 需 3×3 底座 + 中心上方沟灵核心 + 核心上方本方块 | 需要沟灵核心，核心正上方放此方块 |

**⑥ 工具两修**

  * `orphan_scan.py` 收编三类「视野外引用」（坑 #54 的正式收口）：`textures/models/` 的
    法杖杆 / 杖帽（Java 侧只有 `wandPart("…")` / `WandPreset` 字符串参数）、
    `focus_*_orn.png`（`focus_catalog` 的 `ornament_texture` 全路径）、
    `models/_` 前缀的预览临时文件。收编后报告从 15 个误报归零；
  * `gen_orb_obj.py` 的 PNG 写入器有个潜伏 bug：`chunk(b"IEND", "")` 传 str 不传 bytes，
    一重跑就 `TypeError`（第 183 批改完脚本后没再跑过，所以没暴露）。已修。

**产物**：`SkydeityMysticPivot-0.1.0.jar` 741 100 B，SHA256 `639ea0c8…d4f0`；
`selfcheck` ALL GREEN（models 119 / blockstates 26 / lang keys 453 / resources 480）；
OBJ 链路 ALL GREEN（123 个模型引用 / 4 个 obj / 1760 个面）；死代码 0、孤儿 0
（顺带清掉 `XuanshuVoidMiningMenu` 两个历史遗留未用 import）。

### 12.185 第 185 批（2026-10-25）：核心乱码热修 / 祭坛落地立基座 / 采矿方块贴图再重画

**① 需求**（用户原话，三条）：

1. 「玄枢沟灵核心放下了是乱码了」；
2. 「obj 的建模 不要下面一层高的方块，直接立在地面上（注意底层贴图不要透视）」；
3. 「采矿方块贴图还是太丑了 上面不要圆盘 修改机械风格」。

**② 乱码根因：blockstate 不能写裸模型**（第 184 批回归，当天热修）

第 184 批把核心 blockstate 写成 `{"model": "…"}`（无 `variants` / `multipart`），
游戏里整块显示缺失模型。查证：原版**所有** blockstate——连 `chiseled_tuff` 这种
无属性方块——都包在 `"variants"` 里（无属性时键是空串 `""`），裸形式没有定义可加载。
而第 184 批的采矿方块用 multipart 所以没事，只有核心中招。修法同时服务第 ③ 条：
两个 blockstate 全部改回 **variants 显式列全状态**，由 `gen_orb_obj.py` 一并生成：

```json
// xuanshu_spirit_core.json
{ "variants": {
    "formed=false": { "model": "…:block/xuanshu_spirit_core" },
    "formed=true":  { "model": "…:block/orb" } } }
// xuanshu_void_mining.json
{ "variants": {
    "formed=false": { "model": "…:block/xuanshu_void_mining" },
    "formed=true":  { "model": "…:block/orb_empty" } } }
```

**③ 祭坛直接立在地面上：挂点移回核心格 + 新增须弥座基座**

用户看到的「下面一层高的方块」是第 184 批单独渲染的白玉鎏金核心立方体——祭坛下面
垫了一格，读作两块而不是一台机器。本批把 OBJ 挂点从**顶盖格移回核心格**，y=0 即
**地面**，并给 OBJ 加上**须弥座基座**（占满核心那一格的高度）：

| 段 | y（px） | 边心距 | 材质 |
|---|---|---|---|
| 底脚 | 0.03–1.80 | 八角 12.6 | 暗石 + 顶沿金线；**底面封死**（`b_foot`） |
| 座身 | 1.80–12.60 | 八角 13.9→13.3 | 银白石材：顶沿金带 + 中腰回纹带 + 竖砌缝；顶面露金带（`b_body`） |
| 束腰翻沿 | 12.60–16.00 | 八角 12.0→13.8（外鼓） | 暗银 + 上下金线，上口正好接鎏金底盘 |

  * 核心成型态渲染 orb（立方体被基座覆盖），顶盖成型态渲染 `orb_empty`——
    和第 178~183 批相反的一进一退：那几批是「核心藏起来」，现在是「核心长出来」；
  * **底层不透视**：底脚有完整底面（`b_foot` 暗石 + 金环）；底面整体抬 **0.03px**
    避开与「核心下面那一格」顶面的共面 z-fight（0.0019 格，正常视角看不见）；
  * 台座与球整体 +16（16.00 起），形制与第 184 批一致（出檐主台 / 连珠纹 /
    莲瓣柱身 / 相轮刹）；全模型 616 面 / 2464 顶点；
  * `preview_void_machine.py` 删除（它的「拼一个核心立方体」用途没了），
    预览直接走 `preview_obj_iso.py`。

**④ 采矿方块贴图再重画：方形机械面板（第 184 批的圆盘推倒）**

「上面不要圆盘」——第 184 批顶面是玉盘 + 同心环 + 八向金钉的**圆盘**，是仙家供奉
的语言。三张 16×16 全部按方形机械面板重画（`gen_void_mining.py`）：

  * `_top`：鎏金**方框**（外亮 / 金身 / 内暗 / 外角暗四档包边）+ 拉丝银面板
    + 居中**下沉方窗**（阵纹灵窗，四角引 L 形回路）+ 四角**十字螺钉**——没有一个圆；
  * `_side`：拉丝面板 + 上下鎏金压边 + 两道分板缝 + 阵纹灵窗（方窗收窄一档，
    左右让位给十字螺钉——第 184 批的圆铆钉整个换掉）；
  * `_bottom`：暗银底 + 金环 + 四颗金钉（不变）。
  * 阵纹灵窗三件套不变：凹陷框（上左暗 / 下右亮）+ 暖白灵气渐变 + 鎏金阵纹边
    + 中央 3×3 回纹阵眼。

**产物**：见 §10（本批与第 184 批同版本号 0.1.0，jar 重建）。

### 12.186 第 186 批（2026-10-25）：星象仪单独立方 / 虚空采矿机推倒重来 / 枫叶推鲜红

**① 需求**（用户原话，三条）：

1. 「将矿机的多方块形成的模型单移出来，作为新方块：星象仪的模型，先注册其他功能再说，底座不要很宽窄一点」；
2. 「矿机的多方块相关全删了重新设计，改为用原版石英 平滑石英块 平滑石英台阶 等石英方块组成一个大型的建筑
   （你帮我设计 要求整体大气一点，散开的感觉不需要像一个矿机，那种支链起来的感觉即可）然后用矿机方块激活，
   矿机方块可以右键打开 ui，ae 亮色风格，可以按下 ui 中的按钮根据身上的材料（不够则提示不够）一键搭建，
   也可一键收回，材料放玩家背包，不够则掉地上」；
3. 「枫树树叶改为鲜红色，现在的太粉了」。

**② 星象仪：把第 182~185 批那套「悬浮八卦球」搬出来，底座收窄**

新方块 `astrolabe`（中文「星象仪」）。模型整体搬自虚空采矿机原来的成型外观
（须弥座 + 出檐主台 + 莲瓣托座 + 悬浮八卦白瓷球 + 相轮刹），**只有底座整圈收窄**：

| 段 | 旧边心距（px） | 新边心距（px） |
|---|---|---|
| 底脚 | 12.6 | **8.4** |
| 座身 | 13.9 → 13.3 | 9.3 → 8.9 |
| 束腰翻沿 | 12.0 → 13.8（外鼓） | 8.0 → 9.2 |
| 鎏金底盘 / 压沿 | 13.8 / 13.8 → 13.2 | 9.2 / 9.2 → 8.8 |
| 银白主台 | 14.0 → 15.0（出檐） | 9.4 → 10.0 |
| 托座下箍 / 收分托座 | 10.6→10.0 / 10.0→5.6 | 7.0→6.6 / 6.6→4.4 |
| 发光小台 | 4.6 → 4.2 | 3.6 → 3.2 |

  * 球（r = 12.6）与相轮刹一分不动 —— 底座窄了，球比底座宽出 0.28 格，「浮」的感觉反而更强；
  * 底脚 8.4 边心距 = 16.8px，正好一格（16px）再宽 0.8px：整座台子站满一格，不再压到邻格；
  * 模型 616 面 / 2464 顶点，包围盒 `x/z [-0.280, 1.280]`、`y [0.002, 3.344]`（3.34 格高）；
  * **碰撞箱只给基座**（`Block.box(-0.4, 0, -0.4, 16.4, 16, 16.4)`）：球在头顶两格高，整格算会把头顶三格
    全划成禁区；`noOcclusion()` + `propagatesSkylightDown` 让光透过去，不在旁边留下方格影子；
  * **只注册、不做功能**（用户：「先注册其他功能再说」）：无方块实体、无交互、亮度 10；
  * 物品图标单独画 16×16（`textures/item/astrolabe.png`，白瓷球 + 鎏金纹 + 窄台座）——
    3D 模型直接当图标会花（坑 #23），这次没有再踩。

产物全部由 **`tools/gen_astrolabe_obj.py`** 生成（原 `gen_orb_obj.py` 删除）：
`astrolabe.obj` / `astrolabe.mtl` / `textures/block/astrolabe_stone.png`（256 图集）/
`models/block/astrolabe.json` / `models/item/astrolabe.json` / `blockstates/astrolabe.json`，
外加顺手重建两个 blockstate（矿机与沟灵核心的状态组合都变了）。
`preview_obj_iso.py` 的默认目标同步改成 `astrolabe`（`tools/preview/astrolabe_iso.png`）。

**③ 虚空采矿机：整套多方块删掉，改成「一台机器 + 玩家/一键搭的原版石英华盖」**

用户要的不是机器，是**建筑**：大气、散开、支链状，且不要一眼看出是矿机。于是：

  * **删干净**：旧的「沟灵核心 + 采矿方块」两格结构、`coreFor` 反推、`tryForm` / `dissolve` /
    `selfHeal` 结构自愈、法杖右键成型与潜行右键解除，**全部删除**；
    `XuanshuSpiritCoreBlock` 退回**纯装饰方块**（无 `FORMED`、无交互、亮度 6），
    它仍是两件台的注魔中心（配方不动）；
  * **新结构**：`VoidMiningStructure`（新类）用代码生成 112 格，形制见下表；
  * **激活方式**：结构齐 = 自动激活（方块实体每秒自检一次 `VoidMiningStructure.isComplete`，
    写 `FORMED`；翻转那一刻放音效 + 粒子 + 给 12 格内玩家一句动作栏提示）。
    `FORMED` 只做两件事：亮度 6 → 12、允不允许产出。**未激活不产出、也不攒进度**
    （华盖塌了重立不该立刻吐一组）；
  * **界面**：`useWithoutItem` 一律开界面（没成型时界面正是搭建入口，拦着反而没地方按）；
  * **两个按钮**：面板加宽到 196×188，右侧两枚 AE 压印按钮（`BTN_X=143`，`一键搭建` / `一键收回`），
    下面一枚方点 + 「已激活 / 未激活」。点击走 `clickMenuButton`（与三台老机器侧配面板同一条路），
    服务端在 `XuanshuVoidMiningMenu#clickMenuButton` 里动世界，按完立刻重算激活态
    （`refreshFormedNow`），不用等下一秒的自检；
  * **材料结算**（`VoidMiningStructure#build`）：先算账 → 任何一格缺料就整单不做（不半途而废）、
    动作栏点名缺哪一种、还差几个；够则一次扣完再动土；创造模式无限材料；
  * **清场**：只清**没有方块实体**的占位方块（草、土、水、石头…，掉落照原版散在原地）——
    否则稍微不平的地上「一键搭建」永远缺那几格（坡地是常态，这条是实机前就想清楚的关键点）；
    带方块实体的（箱子、漏斗、别的机器）一律不动，跳过并报数；
  * **收回**（`dismantle`）：倒序遍历（从上往下），只拆「与结构一致」的格子（华盖底下的箱子、灯、
    玩家自己的装饰一块不动），材料走 `Inventory#placeItemBackInInventory` —— 先进背包，
    塞不下的原版语义就是掉在脚下，正好对上「不够则掉地上」；
  * **自动化**：`autoOutput` 与 `getExternalItems` 改挂机器自身四面（华盖的柱子都在第 4 格上，
    贴着的四面永远是空的，箱子/漏斗/管道就摆那儿）；
  * **烛龙衔时**：`polled` 的判定从「点核心 → 折算到核心」改成「点到的就是机器本身」，
    悬浮字高度 `LABEL_ORB_DY 2.35` → `LABEL_CANOPY_DY 6.2`（字要浮到冠环与支链之上）。

**华盖形制**（机器在 `(0,0,0)`，相对坐标；y=0 = 机器所在那一层）：

| 部位 | 位置 | 方块 | 数量 |
|---|---|---|---|
| 地盘圈 | y=0，`max(|dx|,|dz|)=4` 的一圈 | 平滑石英台阶（下半） | 24 |
| 八根柱 | y=0..2，四角 `(±4,±4)` + 四边中点 `(±4,0)/(0,±4)` | 石英柱 | 24 |
| 冠环 | y=3，`max=4` 的一圈（四角压石英块） | 平滑石英块 28 + 石英块 4 | 32 |
| 外伸臂 | y=3，`(±5,0)(±6,0)(0,±5)(0,±6)` | 平滑石英块 | 8 |
| 臂端柱 + 四叉 | y=4，`(±6,0)/(0,±6)` 立柱、其左右各挑一片台阶 | 石英柱 4 + 平滑石英台阶 8 | 12 |
| 臂端梢帽 | y=5，`(±6,0)/(0,±6)` | 平滑石英台阶 | 4 |
| 角枝 | y=4 `(±5,±5)` 平滑石英块、y=5 盖一片台阶 | 平滑石英块 4 + 台阶 4 | 8 |
| **合计** | 11×11 占地、最高 y=5 | | **112** |

材料合计：**平滑石英块 40、平滑石英台阶 40、石英柱 28、石英块 4**（≈ 120 块下界石英）。
读数上它就是一座「八柱 + 冠环 + 八条朝八个方向散开的支链」的亭式构筑物，没有一处读作机器。

**④ 枫叶推鲜红**

`gen_maple_tree.py` 的 11 档 `LEAF_RAMP` 整条重写：旧色最亮一档 `(250,120,108)` 的 G/B 都上百，
16×16 缩进物品栏就是一片粉；新色把 **G/B 全部压到 80 以下**（R 保持 134~244），色相锁在正红偏一点橘：

| 档 | a（受光边） | e | h | k（裂间阴影） |
|---|---|---|---|---|
| 旧 | (250,120,108) | (232,46,38) | (208,20,18) | (172,12,14) |
| 新 | (244,80,66) | (210,26,22) | (172,11,12) | (134,7,10) |

树苗的四档叶色（`SAP_LEAF`）同步从纯橘往红里收（`(235,124,50)` → `(228,82,40)`）——
不然站在鲜红树冠下面，树苗像另一个树种。橘子点缀（`LEAF_ORN`）保持不动。

**⑤ 产物与自检**

  * 自检 **ALL GREEN**：models 120 / blockstates 27 / lang keys 467 / resources 482；
    OBJ 链路 4 个 obj / 1800 面全过；死代码 0、孤儿 0；
  * `SkydeityMysticPivot-0.1.0.jar` **752 553 B** · SHA256 `a59a6bce665e2a86…040d25`；
  * 待实机验收见 `.none/ROADMAP-tc.md` §2 的 186 行。

---

### 12.187 第 187 批（2026-10-25）：矿机改「背后圆环坛」/ 星象仪去底座改细 + 物品吃 OBJ

**① 需求**（用户原话）：

1. 「矿机多方块搭建位子为矿机方块的后面，然后整体圆形，再高一点，一层层圆环随高度变大一点，然后带一点特效」；
2. 「星象仪的物品贴图改为 obj 的模型，然后最下面那层底座不需要，只需要很细就可以」。

**② 矿机：方形亭子 → 背后九格高圆环坛（`VoidMiningStructure` 整套重写）**

第 186 批立的是「八柱 + 冠环 + 四向支链」的方亭，围着机器四边；本批换成**圆的、往后退、更高**：

- **加 `FACING`**（`BlockStateProperties.HORIZONTAL_FACING`）：`getStateForPlacement` 取
  `context.getHorizontalDirection().getOpposite()`，于是方块**正面朝玩家**、环坛长在**背面**
  （`VoidMiningStructure.back(state)` = `FACING.getOpposite()`）。方块模型是四面同贴图的立方体，
  加属性**不影响外观**，只给结构指方向；`rotate` / `mirror` 一并覆写。
- **四份预转格子表** `PARTS_BY_DIRECTION`（下标 = `Direction.get2DDataValue()`：南 0 / 西 1 / 北 2 / 东 3），
  换向不重算，只查表；本地形制按「背面 = +Z」设计，`rotate()` 转 90° 的整数倍。
- **形制**：圆心在机器背后 **6 格**（`CENTER`），八层圆环（`LAYERS = 8`），半径
  **3 → 3 → 4 → 4 → 5 → 5 → 6 → 6**（每两层向外挪一格）；总高 **8 格**：

| 层 | y | 半径 | 用材 | 格数 |
|---|---|---|---|---|
| 1 | 0 | 3 | 平滑石英块（四正位石英柱） | 16 |
| 2 | 1 | 3 | 平滑石英台阶（四正位石英柱） | 16 |
| 3 | 2 | 4 | 平滑石英块（四正位石英柱） | 32 |
| 4 | 3 | 4 | 平滑石英台阶（四正位石英柱） | 32 |
| 5 | 4 | 5 | 平滑石英块（四正位石英柱） | 28 |
| 6 | 5 | 5 | 平滑石英台阶（四正位石英柱） | 28 |
| 7 | 6 | 6 | 平滑石英块（四正位石英柱） | 40 |
| 8 | 7 | 6 | 平滑石英台阶（四正位石英柱） | 40 |

  材料合计 **232 格**：平滑石英块 100 / 平滑石英台阶 100 / 石英柱 32。

  > 第 187 批试过在顶上加「八个方位各挑一片台阶」的花瓣，等轴图里读成一排断齿、
  > 还把逐层收放的轮廓打乱，最终**没留** —— 干净的薄圈收口更好。

- **为什么厚薄交替**：整层用整块会糊成一根实心烟囱，整层用台阶又太碎；`y` 偶数整块、
  奇数下半台阶 —— **厚圈是「层」，薄圈是层与层之间那道缝**，远看就是一摞圆环。
- **四正位石英柱**：每圈东、南、西、北各立一根，把八层串成四条竖骨，圆坛才不会散成一堆浮圈。
- **位置校验**：最近的一圈在背后第 3 格，所以机器**紧挨着的四面永远是空的**（自动化照旧可接）；
  顶圈前缘正好落在机器正上方（`z=0`）── 坛口朝机器张开，机器站在坛口的空当里。
- **特效**（用户「带一点特效」）：
  * **激活瞬间**：`ringBurst()` 沿**每一圈**按方位取 16 个点精确撒星火（`sendParticles` 的 `dx/dy/dz`
    是盒子不是圈，撒一团会糊），从下往上一层层亮起来，顶上再补一撮亮片把「口」点出来；
  * **运转中**：`clientTick` 每 2 tick 放 2 点星火在最外一圈上对向绕行（480 tick 一圈），
    坛心另有 1/6 概率升起一粒向上飘的星火；`formed` 为假时一个都不放。
- **悬浮字**：`TemporalCore` 的 `LABEL_CANOPY_DY` 常量删掉，改
  `VoidMiningStructure.HEIGHT + LABEL_CANOPY_EXTRA` **运行时**算（不在类初始化时算 ——
  `VoidMiningStructure` 的静态表要碰 `Blocks`，早于注册表加载会踩不稳），现在字抬到 8.6 格。
- 语言键把「星枢华盖」统一改成「**星枢环坛**」（中英），lore1 重写为「在它背后立起一座圆坛」。

**③ 星象仪：去掉须弥座、改成细座、物品改吃 OBJ**

- **底下的须弥座整个撤掉**（用户：「最下面那层底座不需要」）：原来 0.03~16px 那三段的
  「底脚 8.4 边心距 / 座身 9.3→8.9 / 束腰翻沿 8.0→9.2」换成：

| 段 | 第 186 批 | 第 187 批 |
|---|---|---|
| 薄底盘（暗石，底面封死） | 底脚 0.03~1.80，边心距 8.40 | **0.03~0.90，边心距 4.30** |
| 细柱（银白石材） | 座身 1.80~12.60，9.30 → 8.90 | **0.90~13.20，2.20 → 1.90** |
| 小喇叭口 | 束腰 12.60~16.00，8.00 → 9.20 | **13.20~16.00，2.10 → 2.60** |
| 鎏金底盘 / 压沿 | 13.8 / 13.8→13.2 | **3.40 / 3.40→3.20** |
| 银白主台 | 9.40 → 10.00（出檐） | **3.60 → 3.90** |
| 托座下箍 / 收分托座 | 7.00→6.60 / 6.60→4.40 | **3.00→2.80 / 2.80→1.80** |
| 发光小台 | 3.60 → 3.20 | **1.55 → 1.40** |

  球（r = 12.6）与相轮刹一分不动 —— 于是球比托它的柱子宽出一大截，整座读作「一颗球悬在细针上」；
  包围盒仍是 `x/z [-0.280, 1.280]`、`y [0.002, 3.344]`，616 面不变。碰撞箱跟着收到
  `Block.box(3.7, 0, 3.7, 12.3, 16, 12.3)`（与薄底盘同宽）。

- **物品模型改吃 OBJ**（用户：「物品贴图改为 obj 的模型」）：`models/item/astrolabe.json` 从
  `minecraft:item/generated` + 16×16 图，改成与方块同一套 `neoforge:obj` loader
  （照 `xuanshu_charger` 的写法），补 `gui_light: side` 与六组 `display`（gui 缩放 0.30）；
  `textures/item/astrolabe.png` 删除，`build_icon()` 与 `ICON` 表一并删。
- **关键坑（本次新增，见坑 #71）**：方块模型 y=0 是方块底面、总高 3.34 格，而物品的旋转中心在
  **格心** —— 直接把方块那份 OBJ 给物品用，模型会整体挂在格子上方一格多（`translate` 又是在旋转
  之后才生效，写死平移对不上）。所以生成器多写一份 **`astrolabe_item.obj`**，顶点整体下移
  `0.5 - 模型中心 y`（-1.171 格），几何正好居中，`display` 里一点平移都不用写。
- 生成器 `gen_astrolabe_obj.py`：`Obj.write(name=None, y_shift=0.0)` 支持另存 + 平移；
  `build_astrolabe()` 只建不写，`__main__` 里连写两份；`ITEM_TEX_DIR` 常量删除。

**④ 产物与自检**

  * 自检 **ALL GREEN**：models 120 / blockstates 27 / lang keys 467 / resources 482；
    OBJ 链路 4 个 obj / 1800 面全过；死代码 0、孤儿 0；
  * `SkydeityMysticPivot-0.1.0.jar` **783 930 B** · SHA256 `19da38bd…a8ca1ca7`；
  * 待实机验收见 `.none/ROADMAP-tc.md` §2 的 187 行。

### 12.188 第 188 批（2026-10-25）：矿机光坛瘦身成「断弧 + 光杆」/ 星象仪底盘再压薄

**① 需求**（用户原话）：

1. 「obj 我要底下那一层不要那么厚」；
2. 「多方块不要方块那么多，科技感多一点」。

**② 矿机：232 格实心圆环 → 139 格「四段弧 + 光杆」（`VoidMiningStructure.design()` 重写）**

第 187 批是八层**完整**圆环（每层一圈 16~40 格 + 四正位石英柱），232 格石英。用户嫌多，于是这一批把
「实心」整个拆掉，**只留骨架**：

| 部件 | 形制 | 方块 | 格数 |
|---|---|---|---|
| 光 环 | 六层圆环，每层只留 **4 段弧**，段心在四个斜角 | 平滑石英台阶（下半） | 108 |
| 竖 骨 | 四正位，每层一根，与弧错开 45° | 末地烛（朝上） | 24 |
| 中轴 | 坛心一根竖直光柱，从坛底直通坛顶再出一格 | 末地烛（朝上） | 7 |
| — | **合计** | — | **139** |

- **六层半径仍是 3 → 4 → 4 → 5 → 5 → 6**（逐层变大），`LAYERS = 6`，`HEIGHT = LAYERS + 1 = 7`。
- **弧长 = 该层半径**（`arcLength(radius)`）：四段弧 × r 占周长 2πr 的 `4/2π ≈ 64%`，
  **每一层的「弧 / 口」比例完全一样** —— 半径 3 的小圈与半径 6 的大圈读起来是同一个节拍，
  这是本批「科技感」的数学骨架（不是随手撒的方块）。
- **弧与骨错开 45°**：骨在四正位（0°/90°/180°/270°）、弧的段心在四个斜角（45°/135°/225°/315°），
  于是从任何一个方向看，都有一根骨在缺口里、一段弧在骨之间 —— 整座坛没有一处是实心的。
  正前方（朝机器那一侧）刻意留空，不挡机器。
- **末地烛**当竖骨与中轴：5/16 粗、自带 14 级发光，叠起来是一根真正意义的「光柱」；
  石英柱（整格方块）那种厚重感也随之消失。
- **判定放宽**：`matches()` 只比方块本体（台阶额外排除双层），末地烛朝向摆歪不算缺格 ——
  玩家手动搭也能认。

**③ 特效升级（`XuanshuVoidMiningBlock` / `XuanshuVoidMiningBlockEntity`）**

- **撒点与结构同源**：新增 `VoidMiningStructure.arcPointNear(y, angle)`（每层弧上的格子，类初始化时缓存）
  与 `toWorld(machine, back, cell, y)`（本地格 → 世界坐标，与 `rotate()` 同一套转法）。
  粒子不再自己算极坐标 —— 环是断的，按整圈撒会撒到空气里（坑 #74）。
- **激活瞬间**：沿每一层的 16 个方位取样点炸星火，从下往上一层层亮；坛顶（中轴顶出那一格）补一撮。
- **运转中**（客户端每 2 tick）：
  1. **螺旋扫光**：六层弧上各有一点在追，层与层相位差 1/6 圈 —— 一道光沿螺旋从坛底爬到坛顶（240 tick 一周）；
  2. **中轴光核**：坛心那根光柱里，每 6 tick 有一粒光从底升到顶（96 tick 一轮）；
  3. **吸能**：每 8 tick 有一粒 `ELECTRIC_SPARK` 从机器顶端沿背面方向飘向坛心，走一条抬起的弧线。
  熄火（`formed = false`）时三种粒子一起停。
- **悬浮字**：`TemporalCore.labelPos()` 仍按 `VoidMiningStructure.HEIGHT + 0.6` 运行时算，自动变 7.6 格，不用改。

**④ 星象仪：底盘再压薄 + 补顶面（`gen_astrolabe_obj.py`）**

- `FOOT` 从 `(0.03, 0.90, 4.30)` 改成 `(0.02, 0.40, 4.30)`：厚 **0.87px → 0.38px**
  （≈5.4cm → 2.4cm），读作一片薄垫片；`BODY` 起点跟着从 0.90 改到 0.40。
- **补顶面**：`o.prism(..., top="cap", bottom="b_foot")`。底盘顶上那一圈环带（4.30 → 2.20 边心距）
  一直是**开顶**的 —— 187 批以前盘厚 0.90，环带埋在里面看不出来；压到 0.40 就从上方能直接看进柱心（坑 #73）。
- 球、相轮刹、柱身的尺寸一分不动；模型 624 面 / 2496 顶点，包围盒 `x/z [-0.280, 1.280]`、`y [0.001, 3.344]`。

**⑤ 措辞统一**：「环坛」→「**光坛**」（Java 注释、`zh_cn` / `en_us` 的 12 条文案、`README`）。
原因：它已经不是「一圈圈的环」，而是「断弧 + 光杆」，叫环坛名不副实。

**⑥ 产物与自检**

  * 自检 **ALL GREEN**：models 120 / blockstates 27 / lang keys 467 / resources 482；
    OBJ 链路 4 个 obj / 1808 面全过；死代码 0、孤儿 0；
  * `SkydeityMysticPivot-0.1.0.jar` **786 626 B** · SHA256 `34043e99…ebb60af`；
  * 待实机验收见 `.none/ROADMAP-tc.md` §2 的 188 行。

### 12.189 第 189 批（2026-10-25）：星象仪砍柱缩进一格 / 光坛施工图

**① 需求**（用户原话）：

1. 「框起来的部分不要，然后就一个方块的大小」（截图红框圈住的是星象仪「鎏金底盘以下」那一整段：
   压沿 + 喇叭口 + 细柱 + 薄底盘）；
2. 「我在超平坦建了一个建筑想变成矿机的建筑，我应该怎么做」—— 这是**问做法**，不是改代码；
   用户明确说「**这次不要编译了**」，所以本批**没有跑 gradlew**，产物仍是第 188 批那份 jar。

**② 星象仪：砍掉柱身 + 整座缩进一格（`tools/gen_astrolabe_obj.py`）**

- **删几何**：`FOOT / BODY / WAIST / SOCLE / SOCLE_TOP` 五段 prism / band 调用全删；
  只服务它们的图集分区 `s_foot / s_body / s_waist / b_body / b_foot` 连同 5 个画家（`g_s_foot` … `g_b_foot`）
  一起删掉 —— 留着就是 256 图集里五块没人取样的区域。`AA_TILES` 同步去掉 `b_body / b_foot`。
- **新底面 = 银白八角主台（`DAIS` 17.35 ~ 19.10）**：它原来被鎏金底盘压着、底下是开着的，
  现在成了整座的最低面，必须 `bottom="cap"` 封死 —— 不然从下方能一眼看穿整座仪器（坑 #78，与 #73 同一个病）。
- **缩到一格**：`CUT = DAIS[0] = 17.35`、`SCALE = (16 - 2×0.02) / (53.50 - 17.35) ≈ 0.4415`，
  所有顶点过 `fit()`（x/z 绕模型轴缩、y 减 CUT 再缩、上下各留 0.02px 防共面）。
  **UV 仍按设计稿的像素坐标算** —— 贴图不用重画，花纹不跟着缩水（缩小后一块花纹反而占更多屏幕像素）。
- **结果**：包围盒 `y 0.001 ~ 0.999`、`x/z 0.156 ~ 0.844`（宽 0.69 格）、560 面 / 2240 顶点
  （面数比 188 批的 552 多了 8 面，是补的主台底面）。
- **物品模型与方块模型合流**：几何本来就居中在格心、高度正好一格，所以 `astrolabe_item.obj` 直接删，
  `models/item/astrolabe.json` 改读 `astrolabe.obj` —— 坑 #71 那套「另存一份居中版」从此不需要了。
  `display` 的 scale 全套 ×2.4（gui 0.30 → 0.72、ground 0.16 → 0.28、fixed 0.30 → 0.55、
  第三人称 0.22 → 0.42、第一人称 0.26 → 0.46）：模型从 3.34 格缩到 1 格，scale 不跟着放大手上会小掉三分之二（坑 #77）。
- **碰撞箱**（`AstrolabeBlock`）改两段 `Shapes.or`：底座 `box(6.28, 0, 6.28, 9.72, 4.02, 9.72)`
  （主台边心距 1.72px）+ 球 `box(2.44, 4.02, 2.44, 13.56, 15.14, 13.56)`（球底 4.02、球顶 15.14）。
  仍然不按整格算 —— 球只占 0.69 格宽，按整格挡会把旁边那圈空气也划成禁区。

**③ 光坛施工图（新增 `tools/gen_void_altar_plan.py` → `tools/preview/void_altar_plan.png`）**

用户想「把超平坦上自己建的建筑变成矿机的建筑」。得先说清实事：**结构不是识别出来的**，
`VoidMiningStructure.design()` 里是一张写死的 139 格坐标表，一格不对就不算成型，
既不会去认玩家的建筑，也没有 NBT / JSON 自定义结构。于是能走的路只有三条：

1. **照图搭**：本批把 `design()` 逐行复刻到 Python（`ring / arc / nearest / design / rotate` + 全部常数），
   出六层俯视施工图：机器画在 `dz = 0`，光坛朝 `dz` 变大方向（机器背面）长出去；每层标半径、格数（弧 / 烛 / 中轴）；
   图例 + 139 格统计（台阶 108 / 末地烛 31）；页脚写明「换朝向把整张图绕机器那格转 90°」「不能镜像」「嫌麻烦就一键搭建」。
2. **不搭，让它自己搭**：空手右键机器 → 界面右侧「一键搭建」，材料从背包结算。
3. **改代码支持自定义**（本批没做，列为下一批候选）：读 `.nbt` 结构文件或 JSON 坐标表，把玩家自建的东西登记成结构。

脚本自带防飘校验：格数必须等于 139，否则直接拒绝出图 —— 防止改了 Java 忘了回来同步这份复刻。

**④ 顺带查出一处工艺瑕疵（本批未改）**

`arc()` 用 `sorted` 的**稳定排序**打破「两个格子离段心角度完全相同」的平手，原表顺序一偏，
同一半径上左右两段弧就差一格：把设计整体镜像，与实物差 **16 格**。肉眼基本看不出（都是 1 格级的错位），
但**图纸不能镜像**，换朝向只能「绕机器转 90°」。要彻底抹平得把平手改成按对称的键排序（记坑 #76，列为下一批候选）。

**⑤ 文案**：`astrolabe.lore1` 中英各一条改写 —— 从「底下只一根细柱托着」改成
「底下只有一座八角小台托着，球离台一指……整座连球带座，正好收在一格里」。

**⑥ 自检（未编译，按用户要求）**

  * `selfcheck.py` **ALL GREEN**：models 120 / blockstates 27 / lang keys 467 / **resources 481**（比 188 批少 1，删了 `astrolabe_item.obj`）；
  * `check_obj_models.py` **ALL GREEN**：4 个 obj / **1744 面**（188 批是 1808）；
  * 预览：`tools/preview/astrolabe_iso.png`、`astrolabe_front.png`（球坐在八角小台上，整座在一格内）、`void_altar_plan.png`；
  * 产物：**本批没跑 gradlew**，jar 仍是第 188 批的 786 626 B · `34043e99…ebb60af`；
  * 待实机验收见 `.none/ROADMAP-tc.md` §2 的 189 行。

### 12.190 第 190 批（2026-10-25）：矿机结构可读 schem（自定义形制）

**① 需求**（用户原话）：附了一份 `mcedit.schem`，「这是我建筑的 schem，**除去空气方块**，
可以变成矿机的多方块么」。第 189 批的答复是「结构写死 139 格，只能照图搭」——
这一批把那条限制拆掉。

**② 拿到手的建筑长什么样**（`tools/schem_structure.py` 读出来的）

| 项 | 值 |
|---|---|
| schem 尺寸 | 8 × 14 × 8（896 格），非空气 **255 格** / 调色板 19 种 |
| 形制 | 8×8 底、逐层收分的**旋转塔**：y+0 只 5 格，y+2~y+4 是 46~52 格的一圈，往上 28 → 20 → 14 → 11 → 7 → 3 → 2 → 1 |
| 材料 | 淡蓝玻璃 73 / 蓝羊毛 44 / 錾制石英 42 / 海晶灯 36 / 石英台阶 32 / 石英楼梯 12 / 玻璃板 8 / 石英砖 6 / 石英柱 2 |
| 锚点（默认） | schem 内 `(3, 1, 3)` —— 底面正中那一格，正好是空气 |

**③ 新增 `StructureSchematic`（`src/main/java/skydeity/mysticpivot/StructureSchematic.java`）**

- 读法：`NbtIo.readCompressed` + `NbtAccounter.unlimitedHeap()`，根下优先取 `Schematic` 子compound；
  `Width/Height/Length` 三个数字、`Blocks.Palette`（名字 → 序号）、`Blocks.Data` 是 **varint 流**
  （schem v3；v2 定长两字节的也照样能读，因为 varint 解码对单字节值天然兼容）；
- 方块：`minecraft:quartz_stairs[facing=east,half=top,...]` 先按 `[` 切开拿 id，再逐条属性
  `getStateDefinition().getProperty(name)` → `Property#getValue(String)` → `setValue`。
  **认不出的方块（别的 mod 没装）整格丢掉**并打日志报数，不会让整份 schem 作废；
- 取格：`air / cave_air / void_air` 跳过，其余都算一格（水、草、火把也算 —— 用户的「除去空气」就是这个意思）；
- 锚点：非空气包围盒的**底面正中**，`x/z = floor((min+max)/2)`（用 `Math.floorDiv`，负数也往下取，
  与 `tools/schem_structure.py` 的 `//` 一致），`y = minY`；**锚点那一格若本来有方块就跳过**，留给矿机；
- 顺序：按 `y → x → z` 排好，一键收回反着遍历就是从上往下拆。

**④ `VoidMiningStructure` 从「一套形制」改成「两套形制一个签名」**

- `custom()`：`FMLPaths.CONFIGDIR/skydeitymysticpivot/structure.schem`，**懒加载 + 只探一次**
  （`customProbed`），并 `synchronized` —— 客户端与服务端线程都会调它；
- `parts(Direction)`：有 schem 直接返回 `shape.parts()`（**不随朝向转**，四个朝向同一份）；否则走老的
  `PARTS_BY_DIRECTION`；
- 判定唯一分叉点 `satisfied(actual, part)`：自定义 = `!actual.isAir()`，内置 = `matches()`（比方块本体）；
- `height() / layers() / centerDistance() / axis() / toWorld() / arcPointNear()` 全部分叉，
  于是 `XuanshuVoidMiningBlock.ringBurst`、`XuanshuVoidMiningBlockEntity.clientTick`、
  `TemporalCore` 的悬浮字高度**一行不用重写**，两种形制共用同一段粒子代码；
- `materials()` 走 `shape.materials()`（按方块物品合并 —— 台阶朝八个方向算一种）；
- **一键收回在自定义形制下禁用**：`dismantle()` 先判 `isCustom()` 直接退回并提示
  `void_mining.no_dismantle`。理由写在代码里 —— 拆玩家的建筑该还什么料？按 schem 还不对、按实际更不对。

**⑤ 锚点可覆盖（`structure.properties`）**

默认锚点只保证「八乘八、正中那格是空」的建筑落在中间；别的尺寸可能正好压在梁上，机器摆不进去。
于是多了一个可选文件：同目录 `structure.properties` 里写一行 `anchor=3,1,3`（单位 = schem 自己的格坐标），
越界会打 warn 并退回默认。

**⑥ 界面**

搭建按钮的悬停提示在自定义形制下换第一行文案（`gui.build.custom`：「形制取自 structure.schem，
材料按你这座建筑算」），否则玩家看到一长串玻璃 / 羊毛会以为装错了 —— 内置那套是石英 + 末地烛。

**⑦ `tools/schem_structure.py`（新增）**

离线半：读同一份 schem、按同一套锚点规则算格子，打印尺寸 / 格数 / 材料 / 逐层统计，
并出 `tools/preview/schem_structure.png`（左等轴 + 右逐层俯视，矿机那一格画红框）。
**Java 是唯一真源**，改取格 / 锚点规则要回来同步这份复刻。

**⑧ 安装方式（也是给用户的答复）**

把 schem 改名成 `structure.schem` 放进 `config/skydeitymysticpivot/`（本批已替他放好），
重启游戏 —— 之后矿机就按那座塔判定；**删掉文件即回到内置星枢光坛**，不用改代码。

**⑨ 自检与产物**

  * `selfcheck.py` **ALL GREEN**：models 120 / blockstates 27 / **lang keys 469**（+2）/ resources 481；
  * `deadcode_scan` 0 / `orphan_scan` 0 / `pyclean_scan` 4（全是历史遗留：`gen_maple_tree` 的 rect/vline/hline、`preview_obj_iso` 的 sample）；
  * 构建：`gradlew.bat build --offline` 通过（只剩 `EventBusSubscriber.bus` 两条既有过时警告）；
    产物 **763 346 B** · SHA256 `f057d2b2…b1b9d899`（比 188 批小 23 KB —— 189 批删掉了 `astrolabe_item.obj`）；
  * 待实机验收见 `.none/ROADMAP-tc.md` §2 的 190 行。

### 12.191 第 191 批（2026-10-25）：自定义形制可拆 / 粒子换蓝色竖光

**① 需求**（用户原话）：

1. 「现在有问题不能拆了，我希望就可以拆，回答我如果用纯代码是不是比现在占用内存更大。」；
2. 「粒子效果消去，改为蓝色的特效，我不要粒子就是画出来的特效再玻璃周围，几条往上的竖线亮一点」。

**② 收回解禁：不是「允许拆」，是「按实际的东西还」**

第 190 批把自定义形制的一键收回直接禁掉了（坑 #79），理由是「拆玩家的建筑该还什么料说不清」。
这一批承认那个理由站不住 —— 玩家按下去就是想拆，机器凭什么拦。真正的答案是**让掉落跟着那一格的实际方块走**：

- `dismantle()` 变成两行分派：`dismantleBuiltIn` / `dismantleCustom`，两者共用 `finishDismantle`
  （动作栏提示 + 紫水晶破碎音；`blocked > 0` 再补一句「有几处被别的方块占着」）；
- 自定义：从顶往下遍历 `parts()`；**空气跳过**（结构里本来就空的格子），
  **带方块实体的跳过并计数**（箱子、漏斗、别的机器 —— 那是家当，不是结构件），其余
  先 `Block.getDrops(state, level, pos, null, player, mainHand)` 算掉落、再 `removeBlock(pos, false)`，
  最后 `placeItemBackInInventory` 一件件塞回背包（塞不下就掉在脚边）；
- 内置光坛**行为不变**：一格一件 `part.state()`（石英台阶 / 末地烛）；
- 文案：`dismantled` 从「星枢光坛收回 %s 格」改成中性的「结构收回 %s 格」；
  `no_dismantle` 整条删除；新增 `gui.dismantle.custom`（「拆的就是你自己那座建筑：一格一格按它现在的样子收进背包」），
  收回按钮的悬停提示按 `isCustom()` 分叉（`XuanshuVoidMiningScreen#renderButtonTooltips`）。

**③ 粒子 → 画出来的蓝色竖光**

原来那套（第 188 批）是三种粒子：螺旋扫光、中轴光核、吸能电火花。粒子是**一颗颗会飘会散、跟镜头无关的点**，
再调也是「脏」的；用户这次要的是**画出来的线**。于是整条链换掉：

| 删掉 | 换成 |
|---|---|
| `XuanshuVoidMiningBlockEntity.clientTick`（三种 `addParticle`） | `XuanshuVoidMiningRenderer`（`BlockEntityRenderer`），每帧画四边形 |
| `getTicker` 的客户端分支 | 客户端直接 `return null`（不再需要 tick） |
| `XuanshuVoidMiningBlock.ringBurst`（激活时炸 `END_ROD`） | 无（光效与「激活事件」解耦，`FORMED` 为真就一直亮） |
| `centerDistance()` | 无（只为「吸能」那一程服务） |
| 两处 `ParticleTypes` import | —— |

新渲染器 `XuanshuVoidMiningRenderer`：

- **四根竖光**：取四个斜角 `π/4, 3π/4, 5π/4, 7π/4`（正好是内置光坛四段弧的段心，也正好避开四正位的末地烛），
  逐层问 `VoidMiningStructure.arcPointNear(y, angle)`，层与层**连成折线** —— 半径逐层变大，
  这条线就一层层往外挪，贴着结构外缘往上爬；顶端再伸 `1.1` 格收口消失；
- **每段画两层**：细芯（半宽 `0.035` 格，冰蓝 `0.62/0.90/1.00`，α `0.92`）+ 光晕（半宽 `0.11` 格，
  深蓝 `0.18/0.52/1.00`，α `0.16`）；每层再画**两片十字交叉**的四边形（一片法向朝 X、一片朝 Z）——
  零厚度面片转到侧面会消失，交叉成十字就任何水平角度都看得见；
- **坛心一根淡光柱**（`axis()`，半宽 `0.075`，α 底部 `0.30` → 顶部 `0`），正好压在中间那根末地烛上；
- **亮度沿 y 走慢正弦**：`0.62 + 0.38·sin(0.06·t − 0.85·y)` —— 同一时刻上一段比下一段亮/暗，
  看起来像一道光从坛底一层层爬到坛顶；
- **渲染类型**：`RenderType.debugQuads()`（`POSITION_COLOR` + `QUADS` + `NO_CULL` + 半透明混合）。
  它本来是给调试线框用的，但正是「不用贴图、可以自定义颜色、关剔除」这三条对上了，而且**正常参与深度测试**
  （`lightning()` 也能画加法混合的光，但它 `setOutputState(WEATHER_TARGET)`，写进天气缓冲，
  会被地形挡着还亮 —— 记坑 #82）；
- **只跟 `isFormed()` 挂钩**，结构塌了立刻黑掉，不需要额外同步；`getViewDistance() = 128`（自定义结构可能十几格高）；
- **格子缓存** `beams()`：`synchronized`、按 `layers()` 记忆 —— 形制运行期固定，第一次算完就不再遍历几百个格子求最近点。

**④ 顺带答了「纯代码是不是更占内存」**

不占。反过来，**纯代码（内置 139 格）比读 schem（255 格）还省一点**：

| | 常驻内存 | 说明 |
|---|---|---|
| 内置光坛 | 约 **6 KB** | `Part` 记录 139 个 × 4 个朝向（BlockPos 24 B + BlockState 引用 + record 头，约 40 B/格） |
| 自定义 schem | 约 **11 KB** | 255 格一份 `List<Part>` + 调色板 Map + 每格 `BlockState` 引用；解析期的 NBT 树是临时的，读完即丢 |

两者都是「十几 KB」量级，等于一个区块数据的零头，实际感知不到；真正占内存的是方块实体本身的 NBT、
渲染批次与粒子系统（这一批反倒把粒子省了）。所以**形制来源与内存无关**，只跟「你要不要用自己的建筑」有关。

**⑤ 自检与产物**

  * `selfcheck.py` **ALL GREEN**：models 120 / blockstates 27 / **lang keys 469**（删 1 加 1）/ resources 481；
  * `deadcode_scan` **0**：删掉未被引用的 `centerDistance()`（`deadcode` 的「公开方法无人引用」那一栏会点名）；
  * `orphan_scan` 0 / `pyclean_scan` 4（全是历史遗留：`gen_maple_tree` 的 rect/vline/hline、`preview_obj_iso` 的 sample）；
  * 构建：`gradlew.bat build --offline` 通过（只剩 `EventBusSubscriber.bus` 两条既有过时警告）；
    产物 **766 094 B** · SHA256 `7263f503…6c927a`（比 190 批大 2.7 KB：新增一个渲染器类，删掉三处粒子代码）；
  * 待实机验收见 `.none/ROADMAP-tc.md` §2 的 191 行。

### 12.192 第 192 批（2026-10-25）：形制收进代码 / 光效改一柱一雾

**① 需求**（用户原话）：

1. 「纯代码不但不更占，反而更省一点那就改成纯代码」；
2. 「然后蓝色的线我希望是集结部队这种长一点竖直的，下面稍微高一点的位子一个雾状的圆」。

**② 形制：把 schem 编译成常量**

上一批答过「纯代码 vs 读 schem 哪个占内存」，结论是纯代码略省。用户据此拍板改成纯代码。
这条要求的实质不是「省几个 KB」，而是**把运行期的不确定项搬到编译期**：

| | 读 schem（190~191 批） | 纯代码（192 批） |
|---|---|---|
| 启动 | 解 gzip → 解 NBT → `Blocks.Data` varint 流 → 拼调色板 | 类初始化读一张 `int[1020]` |
| 常驻 | NBT 树（临时的）+ 调色板 Map + `StructureSchematic` 对象 | `int[1020]`（4 KB）+ 19 个状态字符串 + `List<Part>` |
| 依赖 | 少了 `structure.schem` 就没形制；单人 / 服务器还可能不一致（坑 #80） | 无外部文件，天然不可能分叉 |
| 换形制 | 覆盖文件 + 重启 | 重跑 `gen_void_blueprint.py` + 重新编译 |

数据怎么进去的：新增 `tools/gen_void_blueprint.py`，读 `.schem`（复用 `schem_structure.py` 的解析），
按原锚点规则（非空气包围盒底面正中 `(3,1,3)`）换算成相对坐标，**跳过锚点那一格**，按 `y → x → z` 排序，
写出 `VoidAltarBlueprint.java`：

- `STATES[19]`：19 个完整状态串，如 `minecraft:quartz_stairs[facing=east,half=top,shape=straight,waterlogged=false]`；
- `CELLS[1020]`：255 格 × `dx, dy, dz, 状态序号`；
- 类初始化时把 19 个状态串用**原版注册表**解析成 `BlockState`（`BuiltInRegistries.BLOCK` +
  `getStateDefinition().getProperty(...)`，属性解析不出来就跳过那条属性，不整格丢），再展开成 `List<Part>`。

尺寸对账：**8×8 / 高 13 格 / 255 格 / 9 种方块 / 中轴 (1.0, 1.0)**（8 格宽没有「正中那一格」，
中轴落在格与格之间的那条线上）。材料：玻璃 73 / 羊毛 44 / 錾制石英 42 / 海晶灯 36 / 台阶 32 /
楼梯 12 / 玻璃板 8 / 石英砖 6 / 石英柱 2。

**③ `VoidMiningStructure` 瘦身：只剩「一塔一形制」**

`StructureSchematic` 整个删除（410 行：gzip / NBT / varint / 调色板 / `structure.properties` 锚点覆盖），
连同这些一起没了的成员：`RADII / arcLength / ring / angularDistance / nearest / arc / design / rotate /
buildAll / buildArcs / PARTS_BY_DIRECTION / ARC_LAYERS / MATERIALS(旧) / custom / customProbed / isCustom /
matches / parts(Direction) / layers / back / axis(machine, back) / toWorld / offsetX / offsetZ / arcPointNear /
dismantleBuiltIn / dismantleCustom`。

现在这个类只有：`PARTS / MATERIALS / HEIGHT / BOUNDS` 四个常量 + `height / materials / axis / bounds /
satisfied / isComplete / missing / build / dismantle / count / consume`。
判定统一为 `satisfied(actual) = !actual.isAir()`；收回统一走 `Block.getDrops`（191 批的口径成为唯一口径）。

**④ 光效：一柱、四角、一雾**

旧版是四条沿结构外缘爬的**折线**（逐层问 `arcPointNear`）。用户要「长一点竖直的」，所以整段改直：

| 元素 | 参数 | 说明 |
|---|---|---|
| 塔心光柱 | y 1.30 → **24.0**（塔高 13，冲出 11 格）、半宽 0.30 → 0.11、20 段 | `(1-u)^1.3` 淡出，底部最亮；慢呼吸 `0.86 + 0.14·sin(0.035t)` |
| 四角竖光 | y 0.25 → **18.0**、细芯半宽 0.024、光晕 0.10、8 段 | 钉在 `bounds()` 的四个角上（正是建筑外角），各错开 ¼ 相位往上窜 |
| 雾环 | y **1.58**、半径 1.62 / 2.02 / 2.42、浓度 0.13 / 0.085 / 0.05、48 段 | 每层不同转速与转向；每层画「薄圆柱面 + 内浓外淡的环面」，侧看不会退化成一条线 |

雾环半径是照着塔内空腔选的：y=1 那一层的内壁在偏离中轴 3 格处，2.42 的内外都还有余量（见 `void_light.png` 右图）。
淡出系数从「每帧 `Math.pow`」改成静态表 `COLUMN_FADE` / `CORNER_FADE`（104 次 pow/帧 → 0）。

**⑤ 工具**

- 新增 `tools/gen_void_blueprint.py`：schem → `VoidAltarBlueprint.java`（换建筑重跑它）；
- 新增 `tools/preview_void_light.py`：**直接正则解析 `VoidAltarBlueprint.java`** 出侧视 + 俯视图
  （数据源就是游戏里那份常量，不读 schem，不会两边漂）；
- 删 `tools/gen_void_altar_plan.py` / `tools/preview_void_beams.py`：它们复刻的是已经不存在的 139 格光坛；
- 文案：`gui.build.hint` / `gui.dismantle.hint` 两条死键删除（`isCustom()` 分叉没了），
  `gui.build.custom` 改「形制是内置的星枢塔，材料按这座塔算：」。

**⑥ 自检与产物**

  * `selfcheck.py` **ALL GREEN**：models 120 / blockstates 27 / **lang keys 467**（删 2，回到 188 批的水平）/ resources 481；
  * `deadcode_scan` 0 / `orphan_scan` 0 / `pyclean_scan` 4（历史遗留四项未动）；
  * 构建：`gradlew.bat build --offline` 通过（只剩 `EventBusSubscriber.bus` 两条既有过时警告）；
    产物 **759 493 B** · SHA256 `60ff4b31…dd3e16cb9`（比 191 批小 6.6 KB：删了整个 schem 读取链路，加了一份常量表）；
    jar 内已无 `StructureSchematic.class`，新增 `VoidAltarBlueprint.class`；
  * 待实机验收见 `.none/ROADMAP-tc.md` §2 的 192 行。

### 12.193 第 193 批（2026-10-25）：竖光搬进玻璃壳内角 / 修抬头闪现 / 界面三处 / 玄枢源质罐

**① 需求**（用户原话）：

1. 「光束不要再四周，再淡蓝色玻璃上四处即可，现在的光束有问题，抬头会闪现，第二我希望是类似折跃的感觉，
   整体再大一点然后单色，靠近下方是一个模糊的圆形，然后 ui 一键收回移动上去显示的框删去，
   ui 进度条最底下没有封闭，缓存区改为 3x4 最左侧加一列」；
2. 「创建一个新的物品方块：玄枢源质罐。模型和贴图全用 tc4 的源质罐子，效果是和 tc4 的源质罐子一样
   但是可以收纳不同种的要素，你根据不会爆 component 来设计一个上限，每个要素最大容量为 2048，
   然后中间的液体颜色就改为鎏金色渐变，不用根据不同的要素改颜色 简单一点，
   但是注魔和我的基座合成时候抽取要动画正常显示」。

**② 竖光：从「建筑外角」搬进「玻璃壳的内角空腔」**

第 192 批把四根竖光钉在 `bounds()` 的四个角上 —— 那是**建筑的外角**，用户看到的就是「光在四周」。
要挪到淡蓝玻璃上，先得把塔心的那点几何量出来（不想再靠肉眼对着截图猜）。把 `CELLS` 按
`dy → dz → dx` 逐层打印成平面图（`G` 淡蓝玻璃 / `L` 海晶灯 / `W` 蓝羊毛 / `Q` 錾制石英 …），
`dy = 3` 那一层是这样：

```
        dx=-3 -2 -1  0  1  2  3  4
dz=-3      g  W  Q  .  .  Q  W  g
dz=-2      W  L  W  W  W  W  L  W
dz=-1      Q  W  .  G  G  .  W  Q     ← 玻璃在 dx=0,1
dz= 0      .  W  G  L  L  G  W  .     ← 玻璃在 dx=-1,2；中间是灯
dz= 1      .  W  G  L  L  G  W  .
dz= 2      Q  W  .  G  G  .  W  Q
```

结论与原先的描述不同（原先说「塔心是 2×2 玻璃柱」），实际是**两层**：

| 量 | 值 | 由来 |
|---|---|---|
| 中轴 | `(1.0, 1.0)` | 8 格宽占 `[-3, 5]`，正中那条格线；`axis() = machine + 1.0` |
| 玻璃壳 | **4×4、厚一格**，壁在 `dx, dz = −1 / 2` | 占 `[-1, 3]`，外表面 `中轴 ± 2.0`，**内壁 `中轴 ± 1.0`** |
| 灯芯 | **2×2 海晶灯**，`dx, dz ∈ {0, 1}` | 正好填满玻璃壳的内腔（`dy 3~8` 实心） |
| 竖光落点 | `中轴 ± 1.5` | 四个内角空腔的正中（1×1 的槽，光条半宽 0.075，离两侧墙面 0.4 格） |

**竖光该落哪条线？** 一开始按「贴着玻璃」取了 `中轴 ± 1.08`（玻璃壳内壁往外 0.08），后来换成
`± 2.08`（外壁往外 0.08），两个都不对 —— 于是把 8×8 每一列「占了哪几层」统计了一遍：

```
        dx=-3 -2 -1  0  1  2  3  4
dz=-3     2  3  5  0  0  6  4  2
dz=-2     3  7  3  3  3  3  8  4
dz=-1     5  3  0  7  7  0  3  6      ← (−1,−1) 与 (2,−1) 是 0
dz= 0     0  3  6 10 12  7  3  0
dz= 1     0  3  6 10 11  7  3  0
dz= 2     6  3  0  6  8  0  3  5      ← (−1,2) 与 (2,2) 也是 0
dz= 3     4  8  3  3  3  3  7  3
dz= 4     2  4  6  0  0  5  3  2
```

**全塔只有 `(−1,−1)` / `(2,−1)` / `(−1,2)` / `(2,2)` 这四列是 13 层全空**，它们的中心正好是
`中轴 ± 1.5`（也就是玻璃壳四个内角的那四条竖向空腔）。其余位置：

- `± 1.0`（灯芯的四条竖棱）—— 埋在玻璃方块里，光会被玻璃切掉；
- `± 2.0`（玻璃壳外棱）—— 落进四角的羊毛 / 海晶灯柱，光一段段被截断；
- 更外（`± 4`）—— 就回到第 192 批那个「在建筑四周」，用户正是不要这个。

所以 `BEAM_OFFSET = 1.5D`（§9 坑 #88）：**整根 13 层不穿任何方块**（不会被塔身截成虚线），两侧就是淡蓝玻璃
（读作「光长在玻璃里」），宽度 0.15 格的光落在 1 格空腔正中，离两侧墙面还有 0.4 格，不存在共面。
`beams()` 里四角按 `(i&1)` / `(i&2)` 的正负组合铺开。
同时把第 192 批那根 `y 1.3 → 24` 的**塔心大光柱整根删掉** —— 用户要的是「淡蓝玻璃上四处」，
中间再压一根粗的，四根细的就读不出来了。

**③ 「抬头会闪现」的真正原因：渲染包围盒**

这不是透明度或者排序的问题，是**剔除**：`BlockEntityRenderer` 的默认 `getRenderBoundingBox` 只覆盖
**方块自身前后一格**。27 格高的光柱一抬头就出了这个盒子，整条几何被一次性 `frustum cull` 掉，
所以表现成「平时好好的，一抬头全没了，低头又出来」—— 闪现。

修法就是老老实实把这个盒子报出来：

```java
@Override
public AABB getRenderBoundingBox(XuanshuVoidMiningBlockEntity be) {
    BlockPos pos = be.getBlockPos();
    return new AABB(pos.getX() - 8.0D, pos.getY() - 2.0D, pos.getZ() - 8.0D,
            pos.getX() + 9.0D, pos.getY() + BEAM_TOP + 2.0D, pos.getZ() + 9.0D);
}
```

盒子的口径：横向覆盖整座 8×8 塔（±8）向外再留一格，纵向从机器下一格到**光柱顶端再上两格**。
比结构本体大一圈是有意的 —— 雾盘半径 2.25、竖光顶到 27，都在这盒子里，
而盒子本身不参与渲染，报大一点只是少剔一次，没有代价。

**④ 光效：单色、更高、下面一团雾盘**

用户要「类似折跃的感觉，整体再大一点然后单色，靠近下方是一个模糊的圆形」。三件事分别落到：

| 要求 | 落点 | 参数 |
|---|---|---|
| 单色 | 芯与晕**同色**，只靠 α 分层 | `BEAM_R/G/B = 0.36 / 0.76 / 1.00`；芯 α 0.52、晕 α 0.20 |
| 更大 / 折跃感 | 高度从 18 拉到 **27**（塔高 13，冲出去两倍多） | y **0.30 → 27.0**、26 段、`taper(…, 1.15)` 淡出 |
| 要细 | 往上收窄到几乎一根线 | 半宽 **0.075 → 0.030**；光晕 `× 3.0` |
| 下方模糊圆形 | 三层雾环 → **一整盘六层同心环** | y **1.35**、半径 **2.25**、6 环 × 36 段、α 0.16 |

亮度波 `warp(u, t) = 0.55 + 0.45·sin(u·3·2π − 0.075t)`：正弦抹掉负半周，最小值不是 0，
所以**底段永远亮着**，不会整根一黑一白地闪（那读起来像接触不良，不像折跃）。

雾盘的「模糊」靠三件事叠出来，缺一件就变成一块塑料片：

1. **同心环 + 外缘渐变**：`(1 − t)^1.7` 让外缘淡到零，没有硬边；
2. **角向起伏**：`swirl(θ, t) = 1 + 0.28·sin(3θ − 0.9t)`，浓度随角度慢慢转，看着像雾在打旋；
3. **薄裙** `DISK_SKIRT = 0.16`：零厚度水平面侧看会退化成**一条线**，外圈套一圈很淡的薄裙才有体积。

仍然走 `RenderType.debugQuads()`（`POSITION_COLOR` + `NO_CULL` + 半透明 + **正常深度测试**，
所以绕到山后照样被挡），零厚度面片一律**十字交叉两片**。`getViewDistance()` 给 160。

**⑤ 界面三处**

- **收回按钮悬停框删除**：`renderButtonTooltips` 里只留「一键搭建」那条，收回那枚不再画框；
- **进度条底边漏缝**：根因是**绘制次序** —— 原来是先画框、后灌色，满进度时青色条压住框的内沿，
  框最下面那一条线被吃掉。改成 `ARROW_CHANNEL` 空槽 → `ARROW_FILL_MID/HI` 灌色 → **最后** `XuanshuGui.ring` 画框。
  **凡是「有边界的填充」，边界一定最后落笔**（§9 坑 #87）；
- **产出栏 3×3 → 3×4**：`SLOT_COUNT` 9 → 12，新槽**加在最左侧一列**，所以整块产出栏左移一格：
  `OUT_X` 62 → 44；建槽循环抽成 `OUT_COLS = 4` / `OUT_ROWS = 3`，索引 `row * OUT_COLS + col`。
  槽位坐标按「`OUT_X` + col·18 + 1」重算，确认 3×4 的右边界仍在进度条左侧、与标题不打架
  （`tools/preview/void_ui_full.png` 里对过；那张图是一次性脚本出的，没留生成器）。

**⑥ 玄枢源质罐（新方块）**

模型与贴图**一张不画**：blockstate 一条 `""` → `thaumcraft:block/warded_jar`，物品模型 parent 也是本体罐，
`display` 照抄。要改的只有「一个罐子能装多少、装几种、液体什么颜色」。

*种类上限怎么定的*：用户要求「按不会爆 component 来设计」。罐子要 `saveToItem` 进物品 NBT，
每多一种要素就是一条 `{Aspect, Amount}` 复合标签 —— 于是 `MAX_KINDS = 16`、每种 `CAPACITY = 2048`，
上限合计 32768 份；16 条标签加在物品上，悬停、`matches`、叠放都不受影响。**限制「种类」而不只是
限制「总量」，是为了避免"什么杂质都往里灌"把罐子变成一个不透明的垃圾桶。**

*能力三件套*：BE 实现 `EssentiaTransport`（收 / 放 / 抽空判定）+ `EssentiaSource`（给注魔矩阵与基座抽）
+ `EssentiaMultiStoreView`，三个能力都在**本模组自己的 `RegisterCapabilitiesEvent`** 里登记
（`EssentiaCapabilities.TRANSPORT` / `.SOURCE`，`BlockCapability.createSided`）—— 本体只给自己的 BE 登记，
不登记就是「罐子看着在，管子认不到」。**多要素容器必须覆写两条默认方法**：

```java
@Nullable @Override public AspectId extractableAspect(Direction face) { return dominant(); }
@Override public int availableEssentia(AspectId aspect, Direction face) { return amount(aspect); }
```

默认实现只认「单一类型」，不覆写就只会给一种、其余全当零（§9 坑 #86）。

*抽取动画为什么天然正常*：注魔矩阵的 `drainOneEssentia` 与 `XuanshuPedestalBlockEntity#drawEssentia`
都走 `EssentiaApi.extract(level, pos, aspect, n, search, EXECUTE)`，而它**只认 `EssentiaSource` 能力**。
能力一登记，罐子与注魔台 / 基座之间那条丝线动画**自动就出现**，不需要另写任何渲染 ——
用户那句「抽取要动画正常显示」，答案是「别绕开 API 自己取存量」。

*液面*：`XuanshuEssentiaJarRenderer` 画一格内的盒子（`MIN/MAX = 0.25/0.75`、底 0.0625、量程 0.625 格），
高度取 **总量 / 2048**；三档鎏金 `DEEP {0.42,0.24,0.04}` / `MID {0.94,0.70,0.17}` /
`LIGHT {1.00,0.95,0.70}`，中点 0.55，**10 段**逐段插值，叠 `1 + 0.07·sin(0.05t − 5.5u)` 微光；
`RenderType.solid()` + `max(200, packedLight)`，暗处也看得见。

*其余*：潜行空手右键倒空（`BOTTLE_EMPTY` + `essentia_jar.cleared`）；比较器按装满程度分档；
`strength(0.4F, 1.5F)` / `SoundType.GLASS` / `noOcclusion()` / 亮度 10 / 碰撞整格但外形照本体罐；
配方玻璃围金锭的十字；`Rarity.RARE`、`stacksTo(16)`、三条 lore。

*内容随物品走*：`saveToItem` + `applyComponentsFromItemStack` + 手写 `playerDestroy` 掉落
（不走战利品表），但**只在总量 > 0 时才写组件** —— 空罐带了 `BLOCK_ENTITY_DATA` 就和刚合成的新罐
叠不起来（§9 坑 #86 姊妹条）。

**⑥.5 顺手做完的第 3 项：矿机光束从「十字」改成四根圆光**

这是用户上一条需求里没做完的那一项（「仔细看是十字，我不希望是这样，用光线一样的圆，然后要 4 条，
在四个玻璃上」）。「十字」的成因很具体：195 批那根柱是**两片零厚度四边形十字交叉**，
零厚度面片只有正对时才是「线」，侧看是一片扁的、45° 看就是十字。

| 改了什么 | 之前 | 现在 |
|---|---|---|
| 几何 | 两片交叉面片（8 次 `addVertex`） | 绕轴 12 段的**圆柱侧面**（48 次 `addVertex`） |
| 根数 | 1 根，钉在中轴 | **4 根**，各站一块露顶玻璃 |
| 起点 | `height()` = 13（塔心柱顶） | 各块玻璃的**上表面**（`y = 9`） |
| 粗细 | 半宽 0.09 | 半径 0.09（同宽） |
| α | 1.0 | 0.9（管壁正反两面都画，留一点透才有厚度） |

落点走新加的 `VoidMiningStructure.beamAnchors(machine)`：从结构常量里按
「**从低往高第一个恰好四块露顶淡蓝玻璃的层**」取四块（现在 `dy = 8`：北 `(1,−1)`、南 `(0,2)`、
西 `(−1,0)`、东 `(2,0)`），每块给 `{x+0.5, y+1, z+0.5}`。规则不写死坐标 —— 换形制后只要新塔
也有这么一圈，光柱自己跟着挪；一层凑不出四块就返回空表，渲染器退回「中轴一根、从 `height()` 起」，
于是 `axis()` 与 `height()` 两个访问器仍然有用（否则又会变成死代码）。

圆柱的正反两面都会画到（`NO_CULL`），α 取 0.9 而不是 1：远侧先画、近侧后画，颜色与 α 相同，
叠出来仍是同一支蓝，不会「一根管两个亮度」；也不至于低到看得见管子里透出后面的方块。

`tools/preview_void_light.py` 同步重画（四根竖带 + 俯视四个圆截面），图在 `tools/preview/void_light.png`。

**⑦ 自检与产物**

  * `selfcheck.py` **ALL GREEN**：models 121 / blockstates 28 / **lang keys 471**（本批新增 5 条玄枢源质罐文案：
    名称 + 三条 lore + 倒空提示，中英同步）/ resources 486；
  * `deadcode_scan` 0 / `orphan_scan` 0 / `pyclean_scan` 4（历史遗留四项未动）；
  * 构建：`gradlew.bat build --offline` 通过；
    产物 **774 701 B** · SHA256 `e02c73d06f5e18da0865ec2e23fd3d1fec208f14fde804d1cf3ca151326f93b3`
    （比 192 批大 14.9 KB：三件套 + 配方 / 战利品表 / 贴图 / 文案，以及重写后的渲染器）；
    jar 内已确认含 `XuanshuEssentiaJar*.class`、`essentia_glow.png` 与四份 json；
  * 待实机验收见 `.none/ROADMAP-tc.md` §2 的 193 行。


---

### 12.194 第 194 批（2026-10-25）：竖光改深蓝 / 从玻璃顶起 / 纺锤形 + 源质罐 32 种 / Jade / 斜向鎏金 / 蓝紫盖 / 挖起保留

**需求原话**

> 1. 玄枢罐子要适配 Jade 可以看到有哪些要素，然后改为 32 种一罐，内部颜色改为鎏金色渐变，
>    斜着渐变。盖子改为蓝紫色。破坏罐子要保留要素 不能是空的 和 tc 的源质罐子一样
> 2. 矿机线改为深蓝色，然后从玻璃上面出发。不要到底部还有。然后你先做一条竖线纯色，
>    然后因为是玻璃开始往上，玻璃那格往上 3-4 格呈现慢慢变粗然后再变细，整体画出来清晰一点

**① 先把「玻璃上面」量出来，再改起点**

「从玻璃上面出发」不能靠猜。把 `VoidAltarBlueprint.CELLS` 按层打印了一遍（`dy = 0 … 12`，8×8），
结论只有一句：**`dy = 0 … 8` 九层才是完整一圈 4×4 玻璃壳**（壁在 `dx, dz = −1 / 2`，内里是 2×2 海晶灯芯），
从 `dy = 9` 起就收成一根偏东的小尖塔（`dy = 9` 三格、`10` 两格、`11` 两格、`12` 一格）。

| 量 | 旧（第 193 批） | 新（本批） | 由来 |
|---|---|---|---|
| 起点 `BEAM_BASE` | 0.30（塔底） | **9.0** | `dy = 8` 那格的顶面；塔里从此一根光都没有 |
| 顶端 `BEAM_TOP` | 27.0 | **30.0** | 起点抬高 8.7 格，长度补回来还更长（21 格高） |
| 落点 `BEAM_OFFSET` | 1.5 | 1.5（不变） | 四个内角空腔的中心，13 层全空，与塔顶小尖塔**互不接触** |

竖光起点抬到 9.0 之后仍站在原来那四条空腔里：`dy = 9 … 12` 那几层只有中轴附近有方块，
四个角照旧是空的 —— 所以「往上穿过塔顶」这一段依旧一根方块都不碰（§9 坑 #88 的判据还在管用）。

**② 纺锤形：一条曲线，三个数**

用户口径是「玻璃那格往上 3-4 格呈现慢慢变粗然后再变细」，于是把「上宽下窄」整个换掉，
改成 `halfWidth(y)` 一条分段曲线：

```
dy = y − 9.0
dy ≤ 0            →  0.050                       （贴玻璃，细）
0 < dy < 3.2      →  0.050 → 0.135，smoothstep   （慢慢变粗）
dy ≥ 3.2          →  0.135 → 0.040，(1−t)^0.55   （再一路变细到顶）
```

两个细节：前段用 **smoothstep**（`t²(3−2t)`），起手处斜率为 0，贴玻璃那一截不会突然「鼓」一下；
后段用 **`(1−t)^0.55`** 而不是线性，中段还能保住粗细，不是一路直线抽成一条丝。
段数同时从 26 加到 **40** —— 纺锤的转折在 26 段时能看出棱。

**③ 「清晰」按字面做：把亮度波压死**

第 193 版亮度是 `0.55 + 0.45·sin(…)`，一根线上会出现明暗相差 45% 的段落，远看就是「一段亮一段暗」。
本批压到 `0.925 ± 0.075`（`BEAM_WAVES` 3 → 2、速度 0.075 → 0.06），只留「光在缓缓往上流」的那点活气，
整根读作**一条实线**。芯 α 0.52 → **0.85**、光晕倍数 3 → **2**，线有了实在的边，不再糊成一团。

颜色一并从浅蓝 `0.36 / 0.76 / 1.00` 换成**深蓝 `0.11 / 0.20 / 0.78`**，仍然只有一支色，
芯与晕只差 α（0.85 / 0.22）。

**④ 雾盘跟着抬上来**

竖光既然从玻璃顶起，底下那团雾再留在 y=1.35 就成了「半空吊着的一块」。本批把
`DISK_Y` 挂到 `BEAM_BASE + 0.02 = 9.02`、半径 2.25 → **1.85**（落在 4×4 玻璃柱的投影里，不出格）、
薄裙 0.16 → 0.14，其余（六层环、36 段、角向 `sin(3θ − t)` 起伏、外缘 `(1−t)^1.7`）原样。
现在读作「光从这块玻璃顶面涌出来」。

**⑤ 源质罐：16 种 → 32 种**

`MAX_KINDS` 直接改 32（`CAPACITY` 仍是每种 2048）。上限的**理由不变**（要写进物品组件，
组件体积随种类线性涨），只是 32 种能盖住四原质 / 六元质 / 绝大多数复合要素，
真不够就调这个常数 —— 不存在「改大了会炸」的阈值。

**⑥ 液面改「斜着渐变」：从按层取色改成按顶点取色**

第 193 版是**按层**取色：一层四个面同色，只能竖着渐变，看着像一块分层塑料。
本批把颜色移进顶点：每个顶点算一次

```
t = 0.55 · (y − BOTTOM) / (top − BOTTOM)  +  0.45 · ((x − MIN) + (z − MIN)) / (2(MAX − MIN))
```

再拿 `t` 查同一套三档鎏金（`DEEP → MID` 到 0.55，`MID → LIGHT` 到 1）。于是颜色沿
「西北下角 → 东南上角」那根**斜轴**推进：同一层四个面颜色各不相同，贴着玻璃看是一条斜跨罐子的金带。
`face(...)` 因此重写成「竖直两条边 + 上下两个高度」的形式，一次给四个顶点各算一次色；
顶点顺序仍沿用本体罐的「左下 → 左上 → 右上 → 右下」，反了会被 `solid()` 的面剔除剔掉。

**⑦ 盖子换蓝紫：只换色相，不动明暗**

模型仍照本体罐那两个 element，但**贴图换成自家三张**：`essentia_jar_side` / `essentia_jar_top`
（盖子换蓝紫）/ `essentia_jar_bottom`（原样搬来）。生成脚本 `tools/gen_jar_lid.py` 从 `tc.jar`
读原图，**按颜色**认出盖子（`r > b + 0.02` 且最大分量 `< 0.55` 的深棕），取亮度后乘一支蓝紫
`LID = 0.54 / 0.36 / 0.86` —— 原来「深一块浅一块」的凹凸全在，只是整体成了蓝紫。
不按坐标写死：本体哪天换图也不会错位。罐底原样复制过来，是为了让**模型的每一张贴图都在自家命名空间**
（否则 `selfcheck` 的贴图存在性检查会报红，§9 坑 #91）。

**⑧ Jade：服务端打包、客户端排版**

`compat/jade/` 两个类：

* `XuanshuJadePlugin`（`@WailaPlugin`）：`register` 里用本体公开的
  `JadeRegistration.blockData(...)` 挂服务端数据提供者；`registerClient` 里用
  `registerBlockComponent(...)` 挂客户端排版；
* `EssentiaJarJadeProvider`：服务端把 `contentsView()` 打包成一段 `CompoundTag`（`要素 id → 份数`），
  客户端读回来**转成 `AspectAmounts`**，再调本体公开入口
  `api.client.JadeAspectTooltip.append(tooltip, amounts, config)` —— 图标、配色、每行几个全跟本体一致，
  连 Jade 的「每行几个」设置都跟着走。顶栏是「要素 N / 32 种」，空罐只出一行「空罐」。

本体的 `JadeAspectHelper` 是**包私有**的，抄不了实现，只能用公开入口（§9 坑 #92）。
插件类单独放 `compat/jade/` 包、主代码完全不引用：**装没装 Jade 都不影响启动**。
`neoforge.mods.toml` 里补了一条 `jade` 的 `optional` 依赖（`[15,)`），只作声明用。

**⑨ 「挖起保留要素」重做：物品组件 + 战利品表**

上一版是手写 `playerDestroy` + `saveToItem`。真机上「破坏之后是空的」，根因是两条叠在一起（§9 坑 #89）：

* `ServerPlayerGameMode#destroyBlock` 在**创造模式**下走 `if (isCreative()) { removeBlock(...); return true; }`，
  **根本不调 `playerDestroy`** —— 自己写在 `playerDestroy` 里的掉落，创造模式一颗都不掉；
* 绕开战利品表 ⇒ 假人 / 机械手 / 区块清理器拆走时也带不走内容。

本批改成和本体防护罐**完全同一套**：

1. 新增 `ModDataComponents.JAR_ESSENTIA`（`DataComponentType<CompoundTag>`，平铺 `要素 id → 份数`，
   持久化 `CompoundTag.CODEC` + 网络 `ByteBufCodecs.COMPOUND_TAG`）；
2. BE 覆写 `collectImplicitComponents` / `applyImplicitComponents` 读写它；**空罐不写**（空罐才能叠）；
3. 战利品表加 `minecraft:copy_components`（`source: block_entity`，`include` 只列这一个组件）——
   `CopyComponentsFunction` 的 `BLOCK_ENTITY` 分支正是取 `blockEntity.collectComponents()`，与第 2 步严丝合缝；
4. `playerWillDestroy` 里 `isCreative()` 时补一次 `Block.dropResources(...)`（本体 `WardedJarBlock` 同款）；
5. 删掉手写的 `playerDestroy`，掉落彻底交给战利品表。

★ 同时踩到一个「两条路互相清空」的坑：放置时原版**先** `updateCustomBlockEntityTag` 把
`BLOCK_ENTITY_DATA` 灌进 BE，**后**才 `setPlacedBy` → `applyComponentsFromItemStack`。
所以 `applyImplicitComponents` 必须写成「**没这个组件就一个字节都别动**」（§9 坑 #90）。

**⑩ 自检与产物**

  * `selfcheck.py` **ALL GREEN**：models 122（+1：罐子的方块模型）/ blockstates 28 /
    **lang keys 473**（+2：Jade 顶栏与「空罐」）/ resources 490（+4：三张贴图 + 一个模型）；
  * `deadcode_scan` **0** / `orphan_scan` **0**（新增 `compat/jade/` 两个类与 `ModDataComponents` 都被引用到）；
  * 构建：`gradlew.bat build --offline` 通过；
    产物 **783 652 B** · SHA256 `902efb208da80f2e41380b58011efb20f7241c62037e1f0f8b25c135ff59da17`
    （比 193 批大 8.7 KB：三张贴图 + 方块模型 + 组件 + Jade 两个类）；
    jar 内已确认含 `compat/jade/*.class`、`ModDataComponents.class`、`essentia_jar_{side,top,bottom}.png`；
  * 待实机验收见 `.none/ROADMAP-tc.md` §2 的 194 行。

---

---

## 9. 坑集（按症状查）

> 这些坑都真实踩过并修好了。**先查这张表再动代码**，能省掉大半调试时间。

| # | 症状 | 根因 | 解法 / 落点 |
|---|---|---|---|
| 1 | 进世界直接崩，「无法加载世界」 | 研究页缺 `aspects` 键，本体 `ResearchCatalog` NPE | 每页补 `"aspects": {}` |
| 2 | 补了 `aspects` 还崩 | `recipe_link_policy` 也是必填，缺键走 `valueOf("")` 崩 | 照老页面补齐全字段 |
| 3 | 合成页崩 | `COMPLETED_SINGLE` 页要求**恰好 1 个**配方 id | 一页一配方，多配方拆页 |
| 4 | 法核装上去不能施法、也没报错 | 档案没包在 `root.focus_profiles` 数组里，被静默跳过 | 包一层数组 |
| 5 | 悬停法杖崩 | 本体读 `vis_discount` 参数，缺键抛异常 | 补 `"vis_discount": {base:0,max:1}` |
| 6 | 法杖上核心只显示一个方块剪影 | `ornament_texture` 少写 `textures/` 前缀 | 写完整路径 |
| 7 | 饰图整层不画 | 贴图没被 item 模型引用，没缝合进图集 | 模型加 `layer0=…_orn` |
| 8 | 机器放进去拿不出来、原料不消耗 | 界面与玩家共用同一个「被限制」的 `ItemStackHandler` | 内外双 handler：菜单不限、自动化禁塞输出禁抽输入 |
| 9 | 读档后机器内容物全丢 | `ItemStackHandler` 槽数在读档后才改 | 槽数在构造期固定，读档不改 |
| 10 | 炼金炉「不产不出」 | 输出槽满 / 配方表未命中时的静默失败 | 先查输出空间再查配方，用诊断日志定位后删日志 |
| 11 | 星象翻倍失效 | `Long.MIN_VALUE` 溢出（累加器初值取反） | 换哨兵值，边界单独判 |
| 12 | 注魔永远不安稳，蜡烛压不住 | 本体 API 没有 `instability` 写入口 | 唯一例外：`@Accessor` Mixin 每 5 tick 清零扫描域 |
| 13 | 引导线一抬头就消失 | BE 分区剔除把线剔掉了 | 改走本体 `AFTER_LEVEL` 世界后通道 + 相机朝向广告牌 |
| 14 | 自写 OBJ 模型全黑 | 缺 `vn`（法线） | 导出时补法线 |
| 15 | OBJ 模型黑影 / 里面朝外 | winding 朝内 | 全部翻外 |
| 16 | 手持 OBJ 花屏 | `vt` 索引越界 | 修 `vt` |
| 17 | 模型贴图跑到别处 | MTL 的 `map_Kd` 路径写错 | 改成相对 `textures/` 的正确路径 |
| 18 | 「像素风不够浓」 | 画贴图时给了每像素随机抖动，造出几百种近似色 | `jitter = 0`，改固定分档调色板 |
| 19 | 研究树两条线叠在一起 | 连线是正弦抖动曲线，起点 ±5px；同源夹角 < 30° 必缠 | 改成扇面摆位，同源夹角 ≥ 30° |
| 20 | 书页「超页」/内容被切 | 每个 `<BR>` 会多吃一行 | 算页高时把 `<BR>` 计入，或用一页一段 |
| 21 | 中文源码编译成乱码 | javac 默认平台编码 | `options.encoding = 'UTF-8'` |
| 22 | 方块能透视看到内壁 | 模型缺底盖 / 面没封 | 补底面或用完整六面 |
| 23 | 手持物品图标花屏 | 3D 模型直接当 item 图标 | 单独画 16×16 item 图标 |
| 24 | 灵气节点越充越大 | 本体渲染按要素数值缩放 | `AuraNodeVisualScaleMixin` 视觉钳制 |
| 25 | 启动期崩，报 mixin 找不到目标 | 桥接类被放进 `mixin` 包，被整体重写 | 桥类挪到 `compat/` |
| 26 | JEI 里某个分类整页消失 | 物品挂了 `isSpecial` | 去掉 `isSpecial` |
| 27 | 星盘成型只成一半 | 侧面纹样画错半边，判定按图案而非按位置 | 图案与判定同源 |
| 28 | 拆一块全阵失效 | 判定要求九块齐全 | 设计上如此，写进书里 |
| 29 | 界面「一团黑」 | 字色用了近黑 + 投影 | 正文浅灰蓝、去投影 |
| 30 | 注魔台面「立了卦石还是不认」 | 台座没成坛（多方块未齐） | 先成坛再认卦象 |
| 31 | 门禁提示不指名缺哪块 | 只判数量不判种类 | 改成凑齐种类 + 动作栏点名 |
| 32 | 换本体版本后 `@Shadow` 失配 | 目标字段改名/移位 | 先 javap diff 再改，别猜 |
| 33 | jar 里资源与源码不一致 | 只 build 没重新 processResources，或手里拿的是旧 jar | 逐个 `unzip -p … \| sha256sum` 对 |
| 34 | 64×64 图标在物品栏糊成色块 | 16 格下 64px 必然降采样 | 要极清就 16×16 手画 |
| 35 | 贴图与模型「错位」：柱子对不上框、纹样贴在不存在的面上 | 形制有**两个来源**：碰撞箱在 Java（`VoxelShape`），模型在 JSON，两边各改各的；贴图又按旧形制画 | 以 Java 形状为准重写 JSON，脚本里直接对账「逐块相等」；贴图按盒子分区、每面 1:1 取图 |
| 36 | 预览图里高柱被梁压住、看着像模型错了 | 等距投影 `sx=(x-z)C`、`sy=(x+z)/2-y` 的视线轴是 `(1,1,1)`，深度应写 `x+y+z` | 深度改成 `x+y+z` |
| 37 | 饰品效果「放背包也生效」 | 用「背包里有这件物品」判定 | 用 Curios 的 `isEquipped`，只认已装备槽位 |
| 38 | 16×16 上的细环碎成一串虚线点 | 用 `disc(r_out, r_in)` 的内外半径带判环，0.7 宽的带在某些角度一个像素中心都踩不到 | 按「像素中心到曲线距离 ≤ 0.5px」取像素（把曲线栅格化），见 `gen_focus_chrono.py#thin_ring` |
| 39 | 扁平椭圆环看着不像环，只剩球两侧几粒方块 | 扁轴（ry）小于核心半径，环的上下弧全被球挡住 | 扁轴放大到核心半径的 1.4 倍以上（r 2.6 对 ry 3.8），环的弧才浮得出来 |
| 40 | 透明底饰纹的预览整张白 / 看不到白线 | `Image.paste` 不带 mask，会把画布深色底一起顶成透明 | `alpha_composite`（或 `paste(..., mask)`）合成预览 |
| 41 | 16×16 圆盘内部漏出几个透明小洞 | 相邻两圈用了 0.1 的半径缝（4.3 / 4.4），斜角像素中心正好落进缝里 | 各圈半径互相咬住（外圈 `r_out` 略大于内圈起点），只在**刻意**要缝的地方留缝 |
| 42 | 树苗长成树后，树干最底下缺一格、叶子挂在缺口上 | `TrunkPlacer#placeLog` 的 `isFree` 只认空气与树叶；主干第一个原木的落点正是树苗所在格，不先清空就放不下 | 照原版 `SaplingBlock#advanceTree`：调 grower 之前先把树苗那一格换成流体状态（旱地即空气），长不下再放回去 |
| 43 | `Blocks::never` 在自己代码里编译不过（「不兼容的类型: 方法引用无效」） | `Blocks` 的**三参** `never` 是 private，公开的是四参版（给 `StateArgumentPredicate`）；`isSuffocating` 等要的是三参 `StatePredicate` | 写 `(state, level, pos) -> false` lambda；四参的场景（`isValidSpawn`）才用 `Blocks::ocelotOrParrot` |
| 44 | 覆写 `isValidBonemealTarget` 报「不是超类型的方法」 | 1.20.2 的老签名带第四个 `isClient` 参数，1.21.1 的 `BonemealableBlock` 只有三参 | 按当前版本签名写：`(LevelReader, BlockPos, BlockState)` |
| 45 | 配方「合成不了」、JEI/EMI 里也查不到，控制台只有一行 data pack 警告 | 1.21.1 的 `Ingredient` codec 是 `either(list, map)`，map 侧是 `xor(ItemValue{"item"}, TagValue{"tag"})`——**裸字符串 `"ns:path"` 既不是 list 也不是 map，整条配方静默解析失败**（不报错、不回退，就是不存在） | 原料一律写对象：`{"item": "ns:path"}` 或 `{"tag": "ns:path"}`；`key` 里的值同理。写完扫一遍 jar 里所有配方有没有裸字符串 |
| 46 | 自制 OBJ 模型编译通过、资源自检全绿，游戏里却**完全不显示** | OBJ 的 `usemtl` 与 MTL 的 `newmtl` 不同名。`ObjMaterialLibrary#getMaterial` 找不到会抛 `NoSuchElementException`；即便不抛，`ModelMesh#addQuads` 开头也是 `if (mat == null) return;` | 两边名字一字不差；用 `tools/check_obj_models.py` 在打包前对账 |
| 47 | OBJ 有模型但每个面都是同一张图拉伸、明暗也怪 | 面缺 `vt`（退回 `DEFAULT_COORDS`）或缺 `vn`（法线靠绕序反推） | 每个面都写 `f v/vt/vn`，并显式给外法线；生成器里加绕序叉积自检 |
| 48 | 1:1 UV 取样后，某个面变成一整块纯色、灰缝一根都不剩 | 面比一块砖（8px）窄时，取样窗口正好停在灰缝**前面**那条像素上。7px 的塔身取 tile 的 0..7，而缝在 x=7 | 把窗口往右下推 `max(0, 砖宽 - 面宽)`，保证每个面至少含一条缝 |
| 49 | 用 Python 写的等距预览图里，砖缝全部消失但模型结构正常 | 采样时写成 `u * (贴图宽 - 1)`，整张贴被压进 31/32，逐像素看只差一格，正好把缝错过去 | `u * 贴图宽` 再 clamp 到 `宽 - 1` |
| 50 | 想把整座多方块的模型做成一个整体，但方块模型只能绑在一格上 | 不知道方块模型可以超出 0..16；又怕 `minecraft:air` 当空模型 | 让中心格渲染整体 obj（坐标 x/z −1..2、y 0..3），其余格用**只有 particle、没有 elements** 的空模型；同时 `automatic_culling: false` |
| 51 | 四个并列节点挂同一父节点，连线怎么摆都缠 | 父连线起点有 `±5px` 正弦抖动；父节点周围被别的卦占掉扇面时确实放不下 30° 一档 | 当时改成父子链（每条边各从自己父节点出发）+ `AUTO_UNLOCK`；**但 30° 这条过严，第 180 批起四法核又改回并列，见第 57 条** |
| 52 | 法核研究页只有功能与逸话、翻不到合成表，但材料其实早就有 | 当初有意删过配方（第 166 批）；`pages` 里少了 `INFUSION_CRAFTING` 与合成说明两页，配方、门禁、文案也一并缺 | 四处一起补：recipe json、`trigram_gate` 条目、两页文档、`bagua.recipe.*` 文案 |
| 53 | obj 大面上 UV 1:1 取样后要么一整块纯色、要么砖缝糊成一片 | 同一张 16px 贴图要铺 11~47px 的面；1:1 时大面取不下（需 UV>1 平铺）、小面正好停在缝前 | 不做 1:1，整块映射 + **按面性质选分区**（塔身细砖 / 台基大石板 / 受光石面） |
| 54 | `orphan_scan` 报 `focus_*_orn.png` 与 `models/*.png` 是孤儿，其实都有人用 | 工具只扫「模型 json 里的 textures 键」：`focus_catalog/*.json` 的 `ornament_texture` 与 obj 的 `mtl → map_Kd` 都不在它的视野里 | 看报告先确认这两类，别直接删 |
| 55 | obj 模型「贴图错位」：砖缝在盒与盒交界处断开、檐上竖纹变成粗栅栏、门板撑满整面墙 | 每个面都把一整块分区贴图拉满（`du=dv=1`），面从 2px 到 47px 一律拉伸 | 改**世界坐标 UV**（1 世界像素 = 1 纹素，不归一化）；所有构件又以塔心 `u=24` 对称，细节自动对齐 |
| 56 | 檐口侧面无论怎么画都像木栅栏 / 一片糊 | 侧面只有 1~2 像素高，竖纹塞不进去，mipmap 还会把一两行纹素抹平 | 薄侧面用**匀色分区**，暗线交给几何（外挑 0.35px 的瓦口薄板）；竖向图案只留给大面 |
| 57 | 「同源多边必须 ≥30°」把四个法核逼成父子链，但用户要的是四个并列 | 那条结论过严：起点抖动只有 ±5px，两条边真正重叠的区间是 `5/sin(θ/2)`（θ=20° 时约 29px），而节点本身就遮掉前 11px | 20° 上下也安全；实测 (5,2)/(4,3)/(3,4)/(2,5) 的 19.7~22.6° 间隙看不出缠 |
| 58 | 给采矿机加自动化/加速，接在锚点上没反应 | 锚点藏在 3×3 底座里，外面接不到；核心在装置中段（第 182 批起是球心那一格）、四周才是空的，而且核心**没有方块实体**，催不动也拿不到 ticker | 物品进出与倍率一律以 `corePos` 为准（`autoOutput` 推核心六面、`getExternalItems` 锚点代理）；加速走 `rateAt` 轮询 + 登记表的 `polled` 标记，不再要求 ticker |
| 59 | 台面/檐口的顶面出现一圈**虚线网格** | 顶面用平面 UV，却引用了带竖缝的分区（`panel` / `gold` / `socle`）：竖缝被铺成横竖各一排，看起来像虚线格 | 平面顶面必须用**专为平面画的分区**（均匀色或徽记），见 `cap` / `steel` / `socle` / `medal0` |
| 60 | 平面图案的外圈读成**梳齿** | 用八边形/圆形度量画同心环，边在纹理里是 45°，纹素量化和投影量化叠成两层台阶 | 图案元素全改**正交**（切比雪夫方框），外圈用连续**渐变**收边；贴边的硬环交给几何 |
| 61 | 每个棱面中间出现一个「小窗口」 | `g_panel` 里先判竖缝（`pu == 0`）再判横缝（`pv == 0`），横缝在棱面两端各被吃掉 1 纹素 —— 棱面只有 8 纹素宽，等于断了 2/8 | 横缝**先判、通长画**，竖缝让位；任何「通长线」都要比「竖向装饰线」先判 |
| 62 | `prism` 拿来当环用，台面被一整块金盘铺平 | `prism` 的 `top` 是**实心整片**（三角扇铺满整个八边形），不是环 | 用 `band(y0, y1, a_out, a_in, ...)`：顶/底是「外圈减内圈」的梯形带 |
| 63 | 预览图里白瓷发土黄，误判成贴图错 | `preview_obj_iso.py` 的光照在 `(-0.42,0.80,-0.43)`，而等距视线从 `+x+y+z` 进来 —— 看到的全是背光面 | 光照方向与视线同侧（`(0.52,0.72,0.46)`）；**先怀疑预览器再怀疑贴图** |
| 64 | 顶面（平面 UV）上出现两道**贯穿全宽的斜白杠** | 平面分区里带了 `lv % N == 0` 的横线（原本是给侧面画能量环的），48×48 整块映射到平面后，一条横线就是一条贯穿的杠 | **平面分区只允许均匀色 + 缓渐变**，任何线都画不得；要环就用几何（`band`） |
| 65 | 球赤道上套一道水平金环，等距图里球被拦腰切成「蘑菇」 | 水平环的前半圈投影后压在球面下半，与球底边缘重合，读成「球坐在环上」 | 改成**竖直极轴**（上下各露一小段 `prism`）：不遮挡球面，还多了浑天仪的味道 |
| 66 | 多方块删掉一层后，界面打不开 / 产物不掉 / 核心能单独催 | 锚点藏在被删的那层里，而「核心在下方」的假设全翻了面：`tryForm` 的校验、`dissolve` 的收取、`onRemove` 的掉落、右键开界面，处处都是 `pos.below()` | 删层先把**锚点换到哪一格**定下来，再把所有 `below()` 逐处翻成 `above()`（第 184 批：锚点从中心底座格 → 顶盖格，四处全翻）；自动化若挂在 `corePos` 上则不受影响 |
| 67 | 方块在游戏里显示**缺失模型（乱码）**，日志只有一行 model 报错 | blockstate 写成**裸模型** `{"model": "…"}`（无 `variants` / `multipart`）——原版所有 blockstate 连无属性方块都包在 `"variants"` 里（无属性时键是空串），裸形式没有定义可加载 | blockstate 一律用 `"variants"` 显式列全状态（带属性的方块每个状态一行）或 `"multipart"`；第 184 批核心中招、第 185 批热修，两个 blockstate 改由 `gen_orb_obj.py` 生成 |
| 68 | 「一键搭建」在稍微不平的地上永远缺那几格，机器怎么都不激活 | 搭建只认「空气 / 可替换」，而坡地上的那几格是草方块、泥土、石头——`canBeReplaced()` 全是 false，一律被当成占位跳过 | 允许**清场**：没有方块实体的占位方块直接 `destroyBlock(pos, true)`（掉落照原版散在原地）再放结构件；带方块实体的（箱子、漏斗、别的机器）仍然不碰、跳过并报数。要点是「先算账再动土」，缺料整单不做 |
| 69 | 加速倍率被平方（16x 变 256x 的效果，机器疯产） | 同一台机器既被 `TemporalCore` 的**强制补跑**（非 polled 分支会拿 ticker 跑 rate 次），又在自己 `serverTick` 里读 `rateAt` 再乘一次 | 「自己读倍率」的机器必须登记成 `polled`，只走时限、不让外部补跑。第 186 批机器变单格时顺手把 `polled` 的判定从「点核心折算」改成「点到的就是机器」 |
| 70 | 删掉方块的某个属性（如 `FORMED`）后，旧存档里那一格表现异常 / 模型对不上 | blockstate json 还留着带旧属性的变体；或者代码里还在 `getValue(旧属性)` | 属性一删，**blockstate、模型、代码三处一起删**；旧存档里 NBT 多出来的未知属性读档时会被忽略（`NbtUtils.readBlockState` 按现有属性表匹配），不必写迁移，但 `getValue` 会抛异常，所以删属性后要全局搜一遍 |
| 71 | 3D（OBJ）模型当物品图标，图标整个飘到格子外面 / 在手上顶到天上 | 方块模型的 `y=0` 是**方块底面**、模型还高 3 格多，而物品的旋转中心在**格心**（`ItemRenderer` 在 `translate(-0.5,-0.5,-0.5)` 之后才画模型，display 的 translate 又排在旋转之后，写死平移对不上） | 另存一份**居中的 OBJ**：生成器 `write(name=…, y_shift=0.5 - 模型中心y)` 把顶点整体下移，物品模型直接读它，`display` 里一点平移都不用写（第 187 批 `astrolabe_item.obj`）。**第 189 批把模型整座缩进一格之后这条彻底不用了**：几何本来就在格心、高正好一格，物品与方块共用同一份 obj |
| 72 | 给已有方块**加**一个属性（如 `FACING`）后，旧存档那格看着没事、新放的却方向不对 | 属性没写进 `createBlockStateDefinition`、`blockstate` 没列全变体，或没覆写 `getStateForPlacement` | 三件事一起做：`createBlockStateDefinition` 加属性 → blockstate **列全 朝向×其它** 所有组合（否则整块缺失模型）→ `getStateForPlacement` 写初值。旧存档读档时缺失的属性取默认值，不会崩（与 #70 相反方向） |
| 73 | OBJ 某一段**减薄**之后，从上方能看进柱子内部（一片黑） | `prism(..., top=None)` 的顶面从来不封 —— 厚的时候被上一段压在下面、看不出来，减薄就露缝（星象仪底盘 0.90 → 0.40 时踩到） | 减薄的同时给那一段补 `top="cap"`；凡是「顶面被下一段完全盖住」的部位，都先问一句「它薄了会露吗」 |
| 74 | 粒子撒在空气里 / 与结构对不上，尤其换朝向之后 | 粒子自己另算一套极坐标（`cos/sin × 半径`），而结构是**断弧**、还要按 `FACING` 转一次 —— 两套坐标迟早走散 | 粒子一律走结构的格子表：`VoidMiningStructure.arcPointNear(y, ang)` 取格 + `toWorld(machine, back, cell, y)` 换算，与 `rotate()` 共用同一套转法（本批的扫光、激活星火都这么写） |
| 75 | javadoc `{@value}` 指过去，IDE 标红 / 生成文档失败 | `{@value X#CONST}` **只认编译期常量**（字面量或常量表达式），`PARTS.size()` 这类运行时求值的不算 | 数字直接写进注释，或者把常量改成字面量；本批 `TOTAL_PARTS` 索性删掉，数字只留在注释与文档里 |
| 76 | 同一层左右两段弧差一格；把图纸镜像过去就少格 | `arc()` 用 `sorted` 的**稳定排序**打破「两格离段心角度完全相同」的平手，原表顺序一偏，段就跟着偏半格 | 图纸**照抄、别镜像**，换朝向只能绕机器转 90°；要绝对对称就把平手改成按对称键排序（如 `(abs(x), abs(z))`），改完 `tools/gen_void_altar_plan.py` 的镜像校验会跟着归零 |
| 77 | 模型整体缩小后，方块看着正常，**物品在手上 / 图标里小掉一大截** | `display` 的 5 组 scale 是按模型尺寸调的；模型从 3.34 格缩到 1 格而 scale 没动，等于跟着缩了三分之一 | 缩放模型必须同步调 `display`（本批 ×2.4：gui 0.30→0.72 / ground 0.16→0.28 / fixed 0.30→0.55 / 三 0.22→0.42 / 一 0.26→0.46） |
| 78 | 砍掉模型底部几段之后，**从下方能看穿整座**（一片空） | 保留下来的那一段顶上/底下原本被裁掉的那段压着，`prism` / `band` 自己从来不封（`top` / `bottom` 默认 None） | 删段之后立刻问一句「谁的顶 / 谁的底现在露出来了」，补 `top="cap"` / `bottom="cap"`。是 #73 的对称版（那次是减薄露顶，这次是砍段露底） |
| 79 | 自定义形制下「一键收回」把玩家的建筑拆了，还回来的方块种类也不对 | 自定义的结构 = **玩家自己盖的东西**；`satisfied()` 只问「不是空气」，于是收回时会把玩家的玻璃、羊毛当成「结构件」拆掉，还按 schem 里的方块还料 | 自定义下直接禁用收回（`dismantle()` 先判 `isCustom()`）。凡「松判定」都要顺着问一句：**反过来那条路（拆 / 回收 / 退款）还成立吗**。★ 第 191 批推翻了这个「禁用」答案：改成「按那一格实际的方块掉」就成立了（#81） |
| 80 | 多人服务器上粒子在空气里、材料清单显示石英 | 形制文件只读**本地** `config/`：服务端有、客户端没有，`parts()` 两边就分叉了（判定在服务端，所以成型是对的） | 单机 / 自己开服不受影响；正式多人要同步时，得把形制摘要（格数 / 包围盒）随 `ContainerData` 或自定义包同步给客户端，再让客户端按摘要选分支 —— 本批没做，记在这儿。★ 第 192 批把形制编译进代码之后**此坑自动消失**：两端跑的是同一份常量，不存在分叉 |
| 81 | 自定义形制下按「一键收回」，要么纹丝不动、要么还回来的方块不对 | 第 190 批怕「拆了玩家的建筑该还什么料」干脆禁用（#79）；但玩家按下去就是想拆，禁用只是把问题推给人 | 掉落**跟着那一格实际的方块**走：`Block.getDrops(state, level, pos, null, player, mainHand)` 先算再 `removeBlock(pos, false)`；带方块实体的一律跳过（箱子不是结构）。「松判定」的反面不是「不许操作」，是「按实际状态结算」 |
| 82 | 想要「发光的光带」而不是粒子，画出来却不贴结构 / 被地形挡着还亮 / 侧面看就没了 | ① 光带坐标要是结构自己的格子（`arcPointNear`），自己另算极坐标就飘（#74）；② `RenderType.lightning()` 写 `WEATHER_TARGET`，画在天气缓冲里，**不受深度遮挡**，隔着山也亮；③ 零厚度面片只朝一个方向，转到侧面就消失 | 用 `RenderType.debugQuads()`（`POSITION_COLOR` + `NO_CULL` + 半透明，**正常深度测试**）；每段画两片**十字交叉**的四边形；坐标一律走 `toWorld()`，与结构同源 |
| 83 | 换掉一整套形制之后，`deadcode_scan` 报 0、自检全绿，但 `tools/` 里还躺着只为旧形制服务的脚本，文档里也还在介绍它们 | 死代码扫描只看 **Java 内部引用**：Python 工具、配置路径、文档表格都不在它的视野里（和 #54 同一类盲区） | 换形制的同一批里，把「只为它存在的东西」列一遍再动手：Java 类 → 工具脚本 → 预览 PNG → lang 键 → README 工具表 / 批次表 / 坑表。本批删了 `StructureSchematic` + 两个 Python 脚本，`selfcheck` 的数字变化（lang 469 → 467）就是那一步的回执 |
| 84 | 把「运行期读文件」改成「编译期常量」时，最怕改完才发现某处还指着旧入口 | 形制的两套入口曾经同签名并存（`custom()` 分叉），删一半会留一堆**能编译但走不到**的分支 | 先全局 grep 旧入口名（本批 `StructureSchematic` / `isCustom` / `arcPointNear` / `toWorld` / `parts(Direction)` / `back`），一次性清完再编译；编译通过后 `deadcode_scan` 还会补点名公开但无人引用的成员。另注意 `{@link 已删成员}` 在 javadoc 里不会报错，要手动扫一遍 `{@link VoidMiningStructure#` |
| 85 | 方块实体渲染器画的东西很高 / 很长，**抬头就整条消失**，低头又回来（像在闪） | `BlockEntityRenderer` 的默认 `getRenderBoundingBox` 只覆盖**方块自身前后一格**：几何一超出这个盒子就被视锥剔除整条干掉，和透明度、排序、深度测试全都无关 | 覆写 `getRenderBoundingBox` 报出**真实占的体积**（本批 17×31×17：横向覆盖 8×8 塔再留一格、纵向到光柱顶再上两格）。凡「渲染器画的东西比一格高」，都要先问这句：盒子够不够 |
| 86 | 自制的多要素源质容器接上管子 / 注魔矩阵后，只有一种要素动得了，其余全当零；或者物品 NBT 越存越大 | `EssentiaTransport#availableEssentia(aspect, face)` 与 `#extractableAspect(face)` 的默认实现只认「**单一类型**」；`EssentiaSource#extractEssentia` 又是**一次付清**语义（只许返回 0 或原样 `requested`） | 多要素容器必须**覆写这两条**（本批给「最多存量的那一种」+ 按 aspect 查表）；另外能力要在**自己模组的 `RegisterCapabilitiesEvent`** 里用 `EssentiaCapabilities.TRANSPORT` / `.SOURCE` 登记，本体不会替你登记。种类也要设上限（本批 16 种 × 2048），只限总量会变成「什么都往里灌」的黑洞 |
| 87 | GUI 里「有边界的填充条」**最底下永远差一条线**（尤其满进度时） | 绘制**次序**：先画框、后灌色，颜色把框的内沿吃掉一条 | **边界最后落笔**：先画空槽 → 灌色 → 最后 `ring` 画框。凡是「填充 + 边框」，边框一定是最后一道工序；换成进度条、血条、容量格同理 |
| 88 | 想把「光效塞进建筑里」（贴着某块玻璃 / 某个塔心往上打），结果光**一段段被塔身截断**，或者整根只剩塔顶露出来的那截 | 只按「看起来该在哪儿」挑坐标，没问那一列**到底有没有方块**：光条一旦落在方块体积里，深度测试就把它切掉；落在玻璃里更糟 —— 玻璃是半透明层且写深度，方块实体在方块层**之后**才画，整根都被玻璃挡掉 | 先把结构按 `(dx, dz)` 列统计「这一列占了哪几层」，**找全空的那几列**再定坐标（本批 8×8 塔只有 `(−1,−1)/(2,−1)/(−1,2)/(2,2)` 四列 13 层全空，中心正是 `中轴 ± 1.5`）；顺手把光条的半宽算一下，确保落进 1 格空腔时离墙面还有余量。**先查空腔，再定光位** |
| 89 | 方块「挖掉之后内容是空的」，而且**创造模式一颗都不掉** | 两件事叠在一起：（a）自己写了 `playerDestroy` 手发掉落 —— `ServerPlayerGameMode#destroyBlock` 在创造模式走的是 `if (isCreative()) { removeBlock(...); return true; }`，**根本不调 `playerDestroy`**；（b）手发掉落绕开了战利品表，假人 / 机械手 / 区块清理器拆走时也带不走内容 | 照本体的做法来：内容做成**真正的物品组件**，BE 在 `collectImplicitComponents` 写出去、`applyImplicitComponents` 读回来，战利品表用 `minecraft:copy_components`（`source: block_entity`）复制；再覆写 `playerWillDestroy`，`isCreative()` 时手动 `Block.dropResources(...)` 补那一刀 |
| 90 | 两条路都读内容时，后走的那条把先走的清空了 | 放置一个带内容的方块，原版会先 `updateCustomBlockEntityTag` 把 `BLOCK_ENTITY_DATA` 灌进 BE，随后 `setPlacedBy` 又调 `applyComponentsFromItemStack`；`applyImplicitComponents` 里要是无条件 `contents.clear()` 再读，遇到「物品上只有旧数据、没有新组件」的罐子就被清空 | **「没这个组件就一个字节都别动」**：先 `input.get(TYPE)`，为 null 直接 return，非 null 才 clear + 重填 |
| 91 | `selfcheck` 报「模型的贴图 #x 不存在」，可那贴图明明在别的模组里 | 自检只扫**本模组**的 assets，跨命名空间引用（`thaumcraft:block/xxx`）它解析不到 | 要么认下这条红，要么**把那几张图复制进自家 assets**（本批选了后者：罐底图原样搬过来，模型只引用 `skydeitymysticpivot:` 自己的图） |
| 92 | 给方块加 Jade 显示，抄本体实现抄不动 / 没装 Jade 就崩 | 本体的 `JadeAspectHelper` 是**包私有**，从 API 视角根本拿不到；而 `@WailaPlugin` 类一旦被主代码 `import`，没装 Jade 的客户端一加载就 `NoClassDefFoundError` | 用本体公开的 `api.client.JadeAspectTooltip.append(tooltip, AspectAmounts, config)`（图标 / 配色 / 每行几个全跟本体一致），数据走「服务端 `registerBlockDataProvider` 打包 → 客户端读」；插件类单独放 `compat/jade/` 包、**主代码一律不引用**，靠 Jade 自己扫 `@WailaPlugin` 注解加载 |
| 93 | 竖光周围一圈玻璃「凭空消失」，像能透视方块 | **方块实体渲染在半透明地形之前**（`LevelRenderer.renderLevel`：solid / cutout → 实体 → 方块实体 → `translucentTarget.copyDepthFrom(主缓冲)` → translucent / tripwire）。谁在方块实体里**写深度**，谁就把深度留进主缓冲，再被抄进半透明缓冲；随后画的玻璃、水、雨全被这条深度挡掉 —— 光柱越宽，缺的那一块越大 | 换**只写颜色**的渲染类型：`RenderType.debugStructureQuads()`（`COLOR_WRITE`）。它和 `debugQuads()` 只差这一个写掩码位，深度**测试**照做（被山挡住照样黑掉），但一个深度字节都不写。另：别用 `lightning()`（写进 `WEATHER_TARGET`，坑 #82） |
| 94 | 删掉一个子包，编译却报「找不到符号 NodeStateBridge / NodeStructureBridge」 | `rm -rf src/main/java/skydeity/mysticpivot/compat` 删的是**整个 `compat` 包**，而不只是 `compat/jade` —— 里面还有两座本体实现层的桥（`NodeStateBridge` / `NodeStructureBridge`）与 `emi/`（3 个）、`jei/`（4 个）插件，共 9 个文件被一起带走；`gradlew build` 立刻在 `ModBlockEntities` / `ModBlocks` 上报「找不到符号」 | 删子包只删**那一个目录**（`rm -rf .../compat/jade`）。真删错了就 `git checkout -- <包路径>` 恢复：`compat/` 下这 9 个文件都在 git 里（194 批的 jade 插件是后加、未入库的，所以恢复出来不会把旧插件带回来） |


---

### 12.195 第 195 批（2026-10-25）：竖光推倒重做只留一条线 / 修「透视方块」/ 源质罐 1024 / 清死代码

**① 用户口径（原话）**

> 「1.蓝色光束还是有问题，你全部是删了重新做，所有光束底座是蓝色玻璃，然后你就先生成一条竖线其他什么都不要，
> 你现在竖线周围会透视方块，先把竖线做了，2.玄枢罐子lore改为可装32种不同要素上限1024
> 3.删掉编译mod时没用的代码和贴图 工程文件不用管。」

**② 竖光：全部删掉，只剩一条线**

`XuanshuVoidMiningRenderer` 从 369 行重写成 190 行，删掉的东西一件不留：

| 删掉 | 原来是什么 |
|---|---|
| 四根斜角光 | `BEAM_OFFSET = 1.5`，站在塔心四个内角空腔里，一次画 4 条 |
| 内芯 / 光晕双层 | 芯 α 0.85 + 两倍宽晕 α 0.22 各画一遍，每次 40 段 × 2 片 |
| 纺锤形 `halfWidth()` | 贴玻璃 0.050 → 3.2 格 smoothstep 涨到 0.135 → `(1−t)^0.55` 收到 0.040 |
| 亮度波 `warp()` | `0.925 ± 0.075·sin(u·2·2π − 0.06t)` |
| 根部雾盘 / 薄裙 | 六层同心环 36 段 × α 0.16 + 外沿 0.14 高的裙 |
| `taper()` 预算表 | 给 40 段的淡出系数预生成的 `float[]` |

剩下的是**一条**：钉在 `VoidMiningStructure.axis()`（塔中轴、格边界 `machine + 1.0`）上，
从 `BEAM_BASE` 到 `BEAM_TOP = 36.0`，**定宽** `BEAM_HALF = 0.09`（全宽 0.18 格）、**α = 1 纯色**
`0.11 / 0.20 / 0.78`，几何只有**十字交叉两片四边形**（8 次 `addVertex`）。

起点不再写常数，改成 `VoidMiningStructure.height()`：塔心 2×2 柱是「玻璃 `dy 0…2` / 海晶灯
`3…7` / 玻璃 `8…12`」，最高一格占 `y 12…13`，而塔总高就是 13 —— 所以
**「底座是蓝色玻璃」= `y = height() = 13.0`**，正好落在塔顶那块淡蓝染色玻璃的上表面。
哪天换形制，光柱自己跟着新塔顶走（这条也让「192 批之后一直没人调用」的 `height()` 复活了）。

**③ 「透视方块」的根因：不是形状，是写深度**

先按用户说的把形状全删了才敢下结论 —— 换完形状问题照旧，说明和光柱长什么样无关。
翻 `LevelRenderer.renderLevel` 才看见渲染顺序：

```
solid / cutout_mipped / cutout      不透明地形
实体
方块实体                             ★ 光柱在这里画（debugQuads 写深度）
translucentTarget.copyDepthFrom(主缓冲)   ★ 光柱的深度被一起抄进半透明缓冲
translucent / tripwire               玻璃、水 —— 被光柱的深度挡掉，缺一块
```

所以**方块实体里谁写深度，谁就把后面画的玻璃挖掉一块**。塔心周围全是淡蓝玻璃，
光柱一宽，周围一圈玻璃就整片消失 —— 看着就是「透过方块看到里面」。

两种渲染类型只差一个写掩码位：

| | `debugQuads()`（上一版） | `debugStructureQuads()`（这一版） |
|---|---|---|
| 着色器 / 图元 | `POSITION_COLOR` + `QUADS` | 同 |
| 混合 | `TRANSLUCENT_TRANSPARENCY` | 同 |
| 剔除 | `NO_CULL` | 同 |
| 深度测试 | `LEQUAL` | `LEQUAL`（被山挡住照样黑掉） |
| **写掩码** | **`COLOR_DEPTH_WRITE`（写深度）** | **`COLOR_WRITE`（不写深度）** |

**④ 源质罐：容量 2048 → 1024**

`CAPACITY` 改 1024，lore 中英同步：

- 中：`可装三十二种不同要素，每种上限 1024 份；罐里的液体是斜着渐变的鎏金，不随要素改色。`
- 英：`Thirty-two different aspects, up to 1024 of each. …`

顺手修了一处一直对不上的注释：`fillRatio()` 的 javadoc 写「一种要素装满时液面到顶」，
代码却是 `totalAmount() / CAPACITY` —— 罐子按要素分别计数，32 种各倒 32 份就显示满罐。
改成 `fullestAmount() / CAPACITY`（跟着最满的那一格走）。`ModBlockEntities` / `ModBlocks` /
`XuanshuEssentiaJarBlock` / `XuanshuEssentiaJarRenderer` 四处注释里的 2048 一并改净。

**⑤ 清死代码：先证明「没有可删的」，再删真死的那几处**

三步独立复核（都不依赖项目自带脚本，免得同一个盲区骗自己）：

1. **贴图**：152 张 png，按「精确 token」判引用（`skydeitymysticpivot:block/x`、
   `"block/x"`、`textures/block/x.png`、注册名约定、`wandPart` / `WandPreset` 的两个参数、
   `aspects/<id>.json` 存在性）→ **152/152 全有引用，0 张可删**；
2. **模型**：122 个 json，按 blockstate 引用 + `parent` 链 + 物品注册名约定 + Java 里的
   `ResourceLocation` 拼串算可达 → **122/122 全可达，0 个可删**；
3. **Java**：全项目成员引用计数 → 真死掉的只有两处（都是自己这一批或上一批留下的尾巴）：
   - `XuanshuEssentiaJarBlockEntity.kindCount()` —— Jade 改成读 `contentsView()` 之后没人调，**删**；
   - `VoidMiningStructure.height()` / `bounds()` / `HEIGHT` / `BOUNDS` —— 192 批之后一直没人调用，
     这一批**真的用上了**（竖光起点 + 渲染包围盒水平范围），不再写死 `13.0` 与 `±8`。

另外复核了两处「看着像死其实活着」：`PickaxeDropEvents.onBlockDrops` /
`StarBeamEffects.onRenderLevelStage` 是 `@SubscribeEvent`（不是零引用），
126 个「没在源码里被字面引用」的 lang 键全是**运行期拼出来的**（`tc.research_name.<id>`、
`star.…`、`aspect.…`、EMI 分类、掉落模式提示），删了会变回 raw key，一个都没动。

**⑥ 自检与产物**

* `selfcheck.py` **ALL GREEN**：models 122 / blockstates 28 / lang keys 473 / resources 490；
* `deadcode_scan` **0**（没人用的 import 0 / 私有成员 0 / 空壳覆写 0 / 公开常量无人引用 0）；
* `tools/preview_void_light.py` 按新形制重画（一条线 + 塔顶那一层俯视），图在 `tools/preview/void_light.png`；
* 构建 `gradlew.bat build --offline` 通过（只剩 `EventBusSubscriber.bus` 两条既有过时警告）；
  产物 **781 239 B** · SHA256 `c564b90f926aba8427e5375e25edf712e45be921763818b34c0ba855cec1c492`
  （比 194 批小 2 413 B：渲染器少写 180 行，删一个公开方法）；
* 待实机验收见 `.none/ROADMAP-tc.md` §2 的 195 行。

### 12.196 第 196 批（2026-10-26）：换到本体 API 20751 / 源质罐自动接上本体 Jade / 删掉自家插件

**① 用户口径（原话）**

> 「更新了api再.none种」（`.none` 里原来那份 `1.21.1-neoforge-20748-dev` 被换成了 `1.21.1-neoforge-20751-dev`）

**② 先做事实核对，再动代码**

换 API 最怕的是「照着文档改了个版本号，实际上某条签名早变了」。所以先做两件事：

1. **`javap -p` 全量签名 diff**：把 20744 与 20751 两个快照的 api jar 各自解出两千多个 class，
   逐个 `javap` 出成员列表，再把本模组**真正 import 过**的父 API 类挑出来对比；
2. **六个 mixin 目标逐一核对**：mixin 全是 `remap=false`，按名字找目标，本体一改名就是启动期崩溃，
   编译期抓不到 —— 这类必须单独验。

diff 结论：**只有新增，没有删除**。

| 类别 | 内容 |
|---|---|
| 新增类 9 个 | `EssentiaStorageView`、`WandClientStages$BeamId`、`VisChange{Plan,Request,Result,Settlement}`、`WandVisStages{,$Change,$Committed}` |
| 新增方法 | `WandApi` 多一族 `changeVis` |
| **改行为的差异（唯一一条）** | `EssentiaMultiStoreView` 的父接口从 `AspectContainerView` 换成 **`EssentiaStorageView`** |
| 删除 / 改名 | 无 |

六个 mixin 目标（`InfusionMatrixBlockEntity` 的 `instability` / `recipeId` / `crafting` / `craftCycle` 一族、
`AuraNodeBlock#hasValidEnergizedStructure`、`AuraNodeRenderer#renderNodeLayers`、
`TaintSpreadLogic#restoreOriginalBiomeColumn`、`PlayerWarp#setPermanentWarp` 一族、`ArcanePedestalBlockEntity`）
在新的运行时 jar 上重新 `javap` 过，**一条没变**，mixin 不用改。

顺带记一笔路径变化：20751 的运行时 jar **不再放在 `tc/` 子目录里**，而是直接躺在快照根目录
（19 MB / 两千多个实现类）。`gradle.properties` 的 `parent_runtime` 跟着改。

**③ `EssentiaStorageView`：一条新接口把自家的 Jade 插件整包作废**

新接口四个方法**全是 default**：

```java
public interface EssentiaStorageView {
    default AspectAmounts essentiaContents(Direction face) { … }
    default int essentiaCapacity() { … }              // 每种要素的上限
    default boolean sharedEssentiaCapacity() { … }    // 所有要素是否共用一份容量
    default AspectFilter essentiaFilter() { … }
}
```

配套的是本体 `TCCapabilities` 的写法：它给**所有** `BlockEntityType` 登记了
`EssentiaCapabilities.STORAGE` 能力，走的是 `instanceof EssentiaStorageView` ——
也就是说**不需要本模组再做任何注册**，只要方块实体实现了这个接口，能力就自动暴露。
而本体的 `EssentiaStorageJadeProvider` 是注册在 `Block.class` 上的（对任何方块生效），
于是玄枢源质罐**自己就有了 Jade 存储段**：先一行「要素」，再把每种要素画成 **数量 / 上限** 的比例条。

这比 194 批自写的那套还好 —— 那个只报裸数量，本体版多给了占用百分比。所以：

| 删掉 | 原因 |
|---|---|
| `compat/jade/XuanshuJadePlugin.java` | 本体插件已覆盖，重复 |
| `compat/jade/EssentiaJarJadeProvider.java` | 同上 |
| `mods.toml` 的 jade 可选依赖 | 不再需要 Jade 的存在 |
| `deps/jade-1.21.1-neoforge-15.10.6.jar` | 编译期不再引 `snownee.jade` |
| 两条 `jade.skydeitymysticpivot.*` 语言键 | 没人再读 |

罐子这侧只补两行「上限是多少」：

```java
@Override public int essentiaCapacity() { return CAPACITY; }        // 1024
@Override public boolean sharedEssentiaCapacity() { return false; } // 每种各一格，不共享
```

`sharedEssentiaCapacity() = false` 是关键：罐子按要素分别计数，32 种各占 1024，
不是「一罐总共 1024」—— 报 `true` 的话本体那排比例条会算错。

至此本模组的上游依赖**重新回到只剩本体一个**（Jade 连编译期都不再需要）。

**④ 一处误删，编译立刻抓到**

删 `compat/jade` 的时候手滑 `rm -rf src/main/java/skydeity/mysticpivot/compat` ——
删的是**整个 `compat` 包**。那下面除了 jade 还有两座本体实现层的桥
（`NodeStateBridge` / `NodeStructureBridge`）与 `emi/` 三个、`jei/` 四个插件，一共 9 个文件被一起带走。

`gradlew build` 立刻在 `ModBlockEntities` / `ModBlocks` 上报「找不到符号 NodeStateBridge」——
这类整包误删编译期一定抓得住，不会漏到运行期。恢复用 `git checkout -- <包路径>`：
`compat/` 那 9 个文件都在 git 里，而 194 批的 jade 插件是后加、没入库的，所以恢复出来**不会**把旧插件带回来。
记进 §9 坑 **#94**：删子包只删那一个目录。

**⑤ 自检**

* `selfcheck.py` **ALL GREEN**（这一批还没加传送门，数字见 §12.197）；
* `deadcode_scan` **0**；`orphan_scan` 无孤儿；
* 构建通过，只剩 `EventBusSubscriber.bus` 两条既有的过时警告。

### 12.197 第 197 批（2026-10-26）：新增方块「玄枢传送门」（科技风折跃门 · 只做普通方块）

**① 用户口径（原话，两句）**

> 「创建一个方块，玄枢传送门，外形科技风类似星际争霸2神族折跃门，先注册 画好贴图」

> 「继续，玄枢传送门就当正常的方块，其他什么都不用 把贴图按要求设计，不需要obj就正常一个方块即可」

第二句是对第一句的收窄：**不要 OBJ、不要方块实体、不要朝向、不要功能**，就是一只方块 +
四张自己画的贴图。中间已经写到一半的 `gen_portal_obj.py`（弯角扫掠 + 256 图集那一套）随之作废，
改成纯贴图路线 —— 这也是更稳的一条：OBJ 在 NeoForge 上要 `neoforge:obj`、要 mtl、要 UV 对齐，
一旦哪里差半个像素就是「模型歪了」，而四面贴图只要像素对得上就一定对。

**② 神族折跃门压进 32×32，只剩三条特征**

| 特征 | 落到哪 |
|---|---|
| 两只**向内弯的角** | 门面（front）：从底 (11, 27.4) 起，控制点 (10.3, 14.5)，尖到 (14.6, 6.4)，左角镜像出右角 |
| 角间一颗**发光的核** | 门面正中的菱形距离场：`d ≤ 0.9` 近白、`≤ 2.0` 青白、`≤ 3.2` 青、`≤ 4.4` 深青，再糊一圈辉光 |
| **深蓝紫金属 + 鎏金 + 青蓝** | 全部贴图的调色板只有这三族（`STEEL_*` / `GOLD_*` / `CYAN_*`） |

四面各画什么、为什么要分开画：

| 面 | 贴图 | 内容 |
|---|---|---|
| north / south | `xuanshu_portal_front` | 折跃门本体：双角 + 中央能量核 + 门槛能量线 + 底座压沿 |
| east / west | `xuanshu_portal_side` | 侧面装甲：纵向鎏金肋条 + 每侧五道凹进去的散热槽 + 四颗铆钉 |
| up | `xuanshu_portal_top` | 八角封盖：同心方环（钢 → 凹槽 → 鎏金环）+ 中央青蓝缝 + 四角螺栓 |
| down | `xuanshu_portal_bottom` | 深色底板：内嵌边框 + 十字加强筋 + 四角螺栓 |

侧/顶/底也单独画，是因为**一块方块从任何角度看都得立得住**：侧面做成「机器的侧面」、
顶盖做成「机器的顶盖」，摆在建筑里就不会像一块贴了图案的木板。

**③ 为什么是 32×32**

弯角在 16 像素里只有 16 个采样点，弧线会被压成台阶；32 像素刚好画得出「上段收、下段直」的曲率，
又还没到需要 mipmap 断层的地步。自检脚本的尺寸白名单本来就认 32。

> 第 204 批按用户口径改成 **16×16 三面**（`front` / `side` / `top`，底面借顶盖），形制与配色见 §12.204 ⑤。

**④ 画法：距离场 + 跨宽坐标，而不是「画一条折线再描边」**

弯角没有拿小方块拼，也没有用描边：

```
对每个像素 p：
    d, q, tangent = p 到角中线（贝塞尔采样 64 段折线）的最短距离 / 最近点 / 切向
    half          = 半宽（底 1.72px → 尖 1.05px，按曲线进度插值）
    覆盖          = half + 0.5 − d          # ≤0 就不属于这只角
    u             = ((p − q) · 外法向) / half   # -1 内缘、+1 外缘
    颜色          = arch_color(u)
```

色带按 u 分档：`≥0.55` 亮金 → `≥0.22` 金 → `≥−0.08` 亮钢脊 → `≥−0.52` 钢 → `≥−0.84` 暗钢 → 其余内缘青蓝。
这样倒角、鎏金镶边、内缘那道能量线**都严格跟着曲线走**，不管曲线多弯都不会错位。

第一版是把金色单独描在最外圈（`u ≥ 0.82` 才给金），32 像素里那一圈只有零点几像素宽，
被抖动一吃就成了脏灰 —— 所以改成「把金色直接做成色带的头两档」。

顶盖与底板用**切比雪夫距离场**（`max(|dx|,|dy|)`）切同心方环，再用**菱形距离**（`|dx|+|dy| > 26.6`）
切掉四个角得到八角形；切角必须**最后切**，先切会被后面画的方框盖回去。

其他几处也是刻意的：

* **门里的能量场浓度压到 0.12**。这是一块实心方块，那点青蓝只是「有电」的暗示；
  第一版给到 0.26，整张脸读成一块蓝色地砖；
* **散热槽是「凹」的**：上沿略亮（`STEEL_D`）、槽口 `INK`、槽底阴影 `STEEL_DD`。
  第一版把槽下面画成亮钢，看着像一排**凸起**的横条；
* **门槛那条能量线两端要收**（`0.80 × (1 − 0.85 d²)`），否则会跟两侧的角糊成一团。

**⑤ 注册与资源（一个不多、一个不少）**

| 位置 | 内容 |
|---|---|
| `ModBlocks.XUANSHU_PORTAL` | `Block::new` 直接注册；`mapColor(COLOR_BLUE)`、`strength(3.0F, 6.0F)`、`SoundType.METAL`、`requiresCorrectToolForDrops()`、**亮度 7** |
| `ModItems.XUANSHU_PORTAL` | `BlockItem`，`Rarity.RARE` + 两条 lore（长什么样 / 还没接通） |
| `ModTab` | 排在玄枢源质罐后面、星象仪前面（同为摆设类） |
| `models/block/xuanshu_portal.json` | 一句 `minecraft:block/cube`，六面指四张图，`particle` 用侧面 |
| `models/item` / `blockstates` | item 直接 parent 方块模型；blockstate 只有 `""` 一条 |
| `loot_table/blocks/xuanshu_portal.json` | 掉自己（`survives_explosion`） |
| `#minecraft:mineable/pickaxe` | 加一行 |
| lang | 中英各 3 条（名字 + 两条 lore） |

**亮度 7 是唯一的「非纯装饰」设定** —— 核在发光，不给亮度暗处就是一块黑板。写进方块注释里，
免得以后有人以为是漏改。**没有配方**（与星象仪一致，只能创造取）。

**⑥ 两张预览图**

`tools/gen_portal_tex.py` 顺手出两张图：

* `tools/preview/portal_faces.png`：四面展开（8 倍），带一格四像素的辅助网格；
* `tools/preview/portal_iso.png`：等轴测小方块。

等轴测那张第一版是坏的：拿两条斜边硬凑平行四边形，顶面浮在半空、侧面接缝差半格。
改成**老老实实投影八个顶点**（`X = (0.866, 0.5)s`、`Z = (−0.866, 0.5)s`、`Y = (0, −s)`），
三个可见面各取一个原点 + 两条边向量做线性映射，面片就闭合了。

**⑦ 自检与产物**

* `selfcheck.py` **ALL GREEN**：models **124** / blockstates **29** / lang keys **474** / resources **498**；
  贴图尺寸分布 `16×16: 95 / 16×64: 1 / 32×32: 59 / 256×256: 1`；
* `deadcode_scan` **0**（未用 import 0 / 私有成员 0 / 空壳覆写 0 / 公开常量无人引用 0）；
* `orphan_scan`：模型孤儿 0、资源孤儿 0、blockstate 全部有对应方块；
* 构建 `gradlew.bat build --offline` 通过（只剩 `EventBusSubscriber.bus` 两条既有过时警告）；
  产物 **785 884 B** · SHA256 `fb60d3d26699a725b64d2d86b76fc6744385fc68b83bdaaea5d748a138b11772`
  （比 195 批大 4 645 B：四张贴图 + 三个模型/状态 + 战利品表 + 两个新类 + 渲染器重写），已同步进本机 mods，sha 一致；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 197 行。

### 12.199 第 199 批（2026-10-26）：传送门升品阶 + 广场挪位 / 创造栏挨着摆 / 水晶塔产量拉到工作方块档

**① 用户口径**

> 「1.传送门和水晶塔一样史诗品质颜色 堆叠16 然后生成的建筑往前一格 往下一格，
> 2.创造栏传送门和水晶塔放一起 水晶塔lore改为：我们需要重新集结部队，艾尔连接着我们所有人。（换行）
> 虚空产出所有矿物，该方块可以自动输出产出
> 3.水晶塔的输出效率该最快 和工作方块一样，不同类型输出也变快 保证tps不要变高」

三件事：**品阶与摆放**、**页签顺序与 lore**、**产量与 TPS**。

**② 传送门：史诗品质 + 堆叠 16 + 广场往前一格、往下一格**

* 物品：`Rarity.RARE → Rarity.EPIC`（紫名，与水晶塔同色），`stacksTo(MACHINE_STACK)` = **16**
  —— 用的是三台工作方块与水晶塔共用的那一个常量，不另写 `16`；
* 摆放（`PortalGateStructure`）：第 198 批是 `FRONT_SHIFT = 1 − minZ = 13`（建筑最前排落在方块背后第 1 格），
  本批改成 **`FRONT_SHIFT = 0 − minZ = 12`**（往前一格）+ **`Y_SHIFT = −1`**（往下一格），
  `rotate()` 里就是 `y + Y_SHIFT` 与 `z + FRONT_SHIFT` 两行；
* **为什么下沉那一格是必需的**：局部 `z = −12` 那一整排只有 **1 格**（`y = 0` 的铺地尖）。
  只往前挪不下沉，那格就会与方块**抢同一个位置**（一键收回时还会把门自己算进去）；
  沉一格之后它正好是方块**脚下**那一层，方块像一枚压在广场尖上的钥石 —— 不抢位、也不悬空；
* **判据跟着改**（`tools/preview_portal_place.py`）：断言从「depth == 1」改成
  「depth == 0 **且** 前沿整排 `y ≤ −1` **且** `(0,0,0)` 没被任何一格占」；
* 脚本顺手改了数据源：`chuansongmen.schem` 已经编译进 `PortalGateBlueprint.java` 并删除，
  现在**直接解析那份 Java 的 `STATES` / `CELLS`**，不再依赖 schem 文件（否则重跑必报找不到文件）。

**③ 创造栏挨着摆 + 水晶塔换 lore**

* `ModTab`：`XUANSHU_PORTAL` 从「星象仪前面」挪到**水晶塔正后面**，页签顺序 = **塔 → 门 → 源质罐**；
* 水晶塔 lore 按用户原文压成**两行**：
  「我们需要重新集结部队，艾尔连接着我们所有人。」/「虚空产出所有矿物，该方块可以自动输出产出」；
* 原来第三条 lore（讲 5 秒一组、四面自动输出、烛龙衔时）**连同中英两份语言键一起删掉** ——
  里面「每 5 秒」本批已不成立，留着就是错的；空手右键就开界面，不必占 lore 的位置；
* 英文同步：`We must rally our forces once more. Aiur binds us all together.` /
  `The void yields every ore; this block can output its yield by itself.`

**④ 产量：并到工作方块档，但 tick 成本一分不加**

| 项 | 第 198 批 | 第 199 批 |
|---|---|---|
| `CYCLE_TICKS` | 100（5 秒） | **1**（与炎炉 / 坩埚 / 炼金炉的 `PROCESS_TICKS` 同拍） |
| 一批出什么 | 随机 **1 种** × 1~3 个 | **4 种不同** × 1~2 个（随机起点顺序取，保证不重复） |
| 每秒产出 | 约 0.2~0.6 件 | 约 **80~160 件** |
| 进栏方式 | 找空格 `setStackInSlot` | `insertItemStacked` **合并**（12 格能压 768 件） |
| 方块实体包 | 每产一次发一个 | `SYNC_INTERVAL = 10` tick 节流（`markClientDirty` / `flushSync`） |
| 音效 | 每产一次响一声 | `SOUND_INTERVAL = 40` tick 最多一声 |
| 自动输出 | `AUTO_IO_TICKS = 10` | **1**，但补「栏空直接返回」短路（空闲零能力查询） |
| 结构自检 / 矿物清单 | 1 秒一次 / 静态缓存 | 不变 |

* **为什么「不同类型也变快」要靠一批多钟**：原来一个周期只点一种矿，想凑齐几种就得等几个周期；
  现在一批就铺开 4 种，且**从随机起点顺序取**（`ores.get((start + i) % size)`），
  同一批里不可能重复 —— 这是「不同类型」四个字的实现方式；
* **TPS 的账**：把「快」压进「一批装多少」，tick 任务数量**一条没多**。
  一 tick 的活 = 4 次合并 + 1 次栏空判断，与批量大小无关；真正会毁 TPS 的两件事
  （每 tick 发方块更新包、每 tick 播音效）都上了节流。想再快只调
  `TYPES_PER_CYCLE` / `MAX_PER_TYPE`，不会多出 tick；
* **界面竖条跟着改语义**：周期缩到 1 tick 后，「这一炉还剩多少」永远是 0 或 1，条就没意义了。
  `getSweep()` 直接取 `gameTime % SWEEP_TICKS(20)`，条**一秒扫一圈**当运转指示灯；
  数据槽 0 / 1 从「进度 / 周期总长」改成「指示 / 一圈长度」，**版式一个像素没动**
  （传送门那边量工程量的条不受影响，它有自己的 `ContainerData`）。

**⑤ 自检与产物**

* `selfcheck.py` **ALL GREEN**：models **124** / blockstates **29** / lang keys **482** / resources **498**
  （比 198 批少 1：删掉塔的 `lore3` 与两条语言键，中英各一）；
* `deadcode_scan` **0**（未用 import 0 / 私有成员 0 / 空壳覆写 0 / 公开常量无人引用 0）；
* `orphan_scan`：模型孤儿 0、资源孤儿 0、blockstate 全部有对应方块；
* `preview_portal_place.py`：四朝向 **深度全 0、前沿全在 `y = −1`、方块格全空**，1099 格一个不少；
* 构建 `gradlew.bat build --offline` 通过；产物 **828 370 B** ·
  SHA256 `2f8ad06e2ed5467be09f1c69e2174fcc631597320d9752a0a4279fcd737acb0f`，已同步进本机 mods，sha 一致；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 199 行。

### 12.198 第 198 批（2026-10-26）：传送门接上界面 / 建筑用代码描述 / 水晶塔改名 + 光苞

**① 用户口径**

> 「1.给传送门添加和矿机一样的ui（只添加ui不设计功能，先把建筑搞定）生成的建筑
> `chuansongmen.schem` 为这个，用代码描述，生成位子为方块的后面即可
> 2.矿机名字改为玄枢虚空水晶塔，（截图红笔）光束靠近底部位子画一个我红色一样的」

三件事：**界面**（只壳子，不做传送）、**建筑**（由 schem 编译成代码，长在方块背后）、
**改名 + 光苞**。

**② 界面：一个版式数字都不重写**

* `XuanshuPortalBlock` 从 `Block` 变成 `EntityBlock`：加 `facing`（放置时正面朝玩家，与水晶塔同规矩）
  与 `formed`；空手右键 `player.openMenu`；亮度 **7 → 12**（广场齐了才亮）；
* `XuanshuPortalMenu` / `XuanshuPortalScreen`：**槽位 / 背包 / 按钮 / 进度条的坐标全部直接读
  `XuanshuVoidMiningMenu` 的那几个 `public static final`**（`OUT_X` / `INV_X` / `BTN_X` / `BTN_Y` …），
  没有第二份数字。这是刻意的 —— 「和矿机一样的 ui」如果靠抄一遍常量来实现，两批之后必然分叉；
* 12 格栏 + 竖排进度条 + 一键搭建 / 一键收回 + 激活点，与水晶塔界面**逐像素同构**；
  唯一语义差别是**进度条量工程量**（已立起 / 1099）而不是产出周期 —— 这一批要的就是「先把建筑搞定」，
  按完一键搭建看着条往上走；一键收回仍然**不弹悬停框**，一键搭建的材料提示换成
  `skydeitymysticpivot.gui.build.portal`（「形制是内置的折跃门广场，材料按广场算：」）；
* **三格数据槽**（`FILLED` / `TOTAL` / `FORMED`），`filled` 一秒重算一次但**只走
  `ContainerData`**（原版 `broadcastChanges` 只给开着的界面发变化）：1099 格的广场如果把进度写进
  blockstate，等于每秒一次 `setBlock` + 光照重算，施工时能把客户端刷得发烫；
* 12 格栏眼下就是一只空箱子（能放、挖掉 `dropContents()` 散出来），**传送还没做** ——
  用户口径「只添加 ui 不设计功能」。

**③ 建筑：`chuansongmen.schem` → `PortalGateBlueprint.java`**

```
python tools/gen_portal_blueprint.py <文件.schem>
```

* 新增 `tools/gen_portal_blueprint.py`，结构与 `gen_void_blueprint.py` 同构（复用 `schem_structure.py`
  的 NBT/varint 读取），产出 **`STATES[36]`**（完整状态串，`quartz_stairs` 的 `hald`/`shape` 都带着）
  与 **`CELLS[4396]`**（平铺 `dx, dy, dz, 状态序号`），类初始化时展开成 1099 个 `Piece`；
* **唯一与矿机那份不同的设计**：这份蓝图**保留 schem 的局部坐标**（x −11…11、z −12…12、y 0…17），
  「挪到方块背后 + 按朝向转」留给运行期 —— 形制是形制、摆法是摆法，换形制不会把朝向逻辑一起搅乱；
* 形制：**23×25、高 18 格、1099 格、12 种方块**。逐层收分的八角台基（最外一层是歪成菱形的铺地，
  金块压角、紫珀嵌线）→ 中央 6×6 方井 → 井口两侧各一根方柱（石英 + 黄金 + 紫珀，每隔一层一圈黑曜石束腰）
  → y 8 起两柱之间一道蓝玻璃横梁 → 井心一根蓝玻璃柱（内嵌海晶灯，y 8~12 是 5×5 的灯笼层）
  → 再往上一串散开的蓝玻璃板；**y = 4 的三颗信标**是整座门真正的「芯」；
* 材料：錾制石英 **519** / 金块 106 / 紫珀 104 / 石英楼梯 91 / 蓝玻璃 64 / 海晶灯 59 / 石英台阶 47 /
  黑曜石 42 / 蓝玻璃板 38 / 石英柱 13 / 淡蓝玻璃板 13 / **信标 3**（悬停一键搭建会逐行列出）。

**④ 「生成位子为方块的后面」怎么落地**

`PortalGateStructure` 干两件事，都在**静态初始化**里按朝向算好缓存（四个朝向各一份 `Layout`）：

1. **偏移**：`FRONT_SHIFT = 1 − minZ = 13`，局部 z 全部 +13 → 1…25。于是**建筑最靠前那一排
   正好落在方块背后第 1 格**，方块自己站在门外、不占建筑任何一格（蓝图里本来就没有锚点那一格）；
2. **旋转**：局部 −Z 定义为「门正面」，按 `facing` 绕 Y 转 —— 北 `(x, z)`、东 `(−z, x)`、
   南 `(−x, −z)`、西 `(z, −x)`。因为补正后的 z 全是正数，旋转矩阵自然保证「无论朝哪，建筑都往背后长」；
3. 顺带算出 `bounds(facing)`（格边界口径），给以后的光效 / 选区用。

**离线自检工具**：新增 `tools/preview_portal_place.py`，用**同一套公式**把四个朝向复算一遍，
断言每个朝向的「深度」（沿 facing 反方向最近的那一排）都等于 **1**，并出两张图：

* `tools/preview/portal_place_iso.png`：等距全景，**红格 = 传送门方块自己**（看它是不是站在门外）；
* `tools/preview/portal_place_top.png`：四个朝向并排俯视，红点始终贴着广场的尖端。

**⑤ 施工流程抽出共用：`StructureDeployer`**

* 传送门要跟水晶塔走**一模一样**的施工流程（先算账再动手 / 缺料点名 / 只清可替换方块 /
  收回按每格自己的战利品表掉），一百多行抄一份等于以后修 bug 修两处，所以提成
  `StructureDeployer`：`record Piece(BlockPos pos, BlockState state)` + `build` / `dismantle` / `tally`；
* 提示文案靠 `prefix` 参数分开（`message.skydeitymysticpivot.void_mining.*` 与 `....portal.*`，
  各 8/7 条，中英都补）；音效仍放锚点（方块自己）；
* `VoidMiningStructure` 那边只剩 `placements(machine)`：「把相对偏移摊成世界坐标」，
  `build` / `dismantle` 两个方法变成一行委派；`itemOf`（改由 `tally` 内联）、`count`、`consume`
  三个成员随之删除（`deadcode_scan` 复查 0）；
* **三颗信标收不回来**：信标是方块实体，施工器按规矩「带方块实体的一律不碰」，
  所以一键收回会在原地留下三颗信标并报「有 3 处被别的方块占着」。反过来也无害 ——
  下次一键搭建看见那三格不是空气，就不会再要三颗信标。

**⑥ 改名与光苞**

* **改名**：`block.skydeitymysticpivot.xuanshu_void_mining` 中 → **玄枢虚空水晶塔**、
  英 → **Xuanshu Void Crystal Tower**；`formed` / `stalled` 两条提示文案跟着改；
  **注册 id `xuanshu_void_mining` 与五个 Java 类名一律不动**（改名是显示名的事，
  动 id 会拆老存档与成就）。三条 lore 同时刷新成现在的塔与光效（原来的「六层断开的石英光环」
  早就不存在了）；
* **光苞**：`XuanshuVoidMiningRenderer` 新增 `drawSpindle` —— 每根光柱根部罩一枚**纺锤形的光**：
  沿轴 12 层、一圈 16 段，第 `i` 层半径 `R · sin(πt)`，`t` 是这一层占全高的比例。
  于是上下各收成一个尖、正中间鼓到最粗，侧影正是用户红笔圈的那片「两头尖的叶子」，
  光柱从它中间穿过去；
  - `LENS_RADIUS = 0.55`：四个落点最近两根相距 **1.41** 格（`(1,−1)` 与 `(2,0)`），
    半径必须 `< 0.70` 才不互相穿插，取 0.55 留 0.31 的缝；
  - `LENS_HEIGHT = 8`（根部往上八格，正好罩住「刚出塔顶」那一段）、`LENS_ALPHA = 0.55`
    （比光柱的 0.9 淡一档，读作「光散出来」，太实就成了四个玻璃锥）；
  - 两端的四边形退化成线（四个顶点里两个重合），`NO_CULL` 下看不见，省掉为尖头单写三角；
  - 顶点量：4 根 × 12 层 × 16 段 × 4 = 3072，比一棵树还便宜；渲染类型仍是
    `debugStructureQuads()`（只写颜色不写深度，坑 #93 那条不变）。

**⑦ 一处顺手加固：blockstate 必须覆盖「方块状态的所有属性组合」**

`xuanshu_portal` 这一批多了 `facing` 与 `formed` 两个属性，blockstate 就得写 **8 条**
（4 朝向 × 2 成型态），每条都指同一个模型、只是 `y` 不同（north 0 / east 90 / south 180 / west 270）。
少写一条不会报错，只会让那一组状态**渲染成紫黑缺口方块**。以后给已有方块加属性，记得回来补全。

**⑧ 自检与产物**

* `selfcheck.py` **ALL GREEN**：models **124** / blockstates **29** / lang keys **483** / resources **498**；
  贴图尺寸分布 `16×16: 95 / 16×64: 1 / 32×32: 59 / 256×256: 1`（本批没加贴图）；
* `deadcode_scan` **0**（未用 import 0 / 私有成员 0 / 空壳覆写 0 / 公开常量无人引用 0）；
* `orphan_scan`：模型孤儿 0、资源孤儿 0、blockstate 全部有对应方块；
* `preview_portal_place.py`：四个朝向 **depth 全 = 1**，1099 格一个不少；
* 构建 `gradlew.bat build --offline` 通过（只剩 `EventBusSubscriber.bus` 两条既有过时警告）；
  产物 **828 162 B** · SHA256 `20f934fdb34c9fb73821f7d905ce7fd80605d1cfbac5c29c568791e86f1e5067`
  （比 197 批大 42 278 B：1099 格的蓝图常量 + 界面四个类 + 施工器），已同步进本机 mods，sha 一致；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 198 行。

### 12.200 第 200 批（2026-10-26）：玄灵三材（只注册 + 画贴图）/ 传送门贴图改白金科技风

**① 需求**（用户原话，两条）：

1. 「给模组添加几个小物品只注册和画贴图 贴图你帮我设计好符合我的世界画风 16x16，
   不用做任何别的：玄灵兔子脚（兔子脚改）、神浸魔骨（骨头改）、翎月翼膜」；
2. 「修改传送门方块的贴图，符合科技风格，整体白色银白色 不要暗色」。

**② 玄灵三材：注册 + 贴图 + 模型壳子 + 语言 + 创造栏，别的什么都没有**

按「不用做任何别的」执行：**没有合成表、没有功能、没有 lore、不进门禁、不进研究树**。
三件都是 `registerSimpleItem` + `Rarity.UNCOMMON`，排在创造栏材料区（七）末尾。
新增 `tools/gen_spirit_relics.py` 一次出齐：三张 16×16 贴图 + 三个
`models/item/*.json`（`minecraft:item/generated` + `layer0`）+ 三联预览图。

设计思路是**原版剪影 + 模组画风**（玩家一眼认得是什么，颜色全是我们的）：

| 物品 | id | 剪影来源 | 画法 |
|---|---|---|---|
| 玄灵兔子脚 | `xuanling_rabbit_foot` | 原版 `rabbit_foot` | 玉银爪垫（偏青的银白）+ 三枚鎏金爪尖 + 垫上一道斜亮线 + 右上一颗浮空灵光星 |
| 神浸魔骨 | `shenjin_demon_bone` | 原版 `bone` | 银白骨干 + 顺着骨对角线一道鎏金嵌线（带暗侧，读作「嵌」不是「浮」）+ 两端骨节金箍 + 骨心一点紫焰（上下两点火苗） |
| 翎月翼膜 | `lingyue_wing_membrane` | 原版 `phantom_membrane` | 银白翼膜 + 三道鎏金羽脉（从左下膜柄向右上展开）+ 脉根一个金结 + 膜面右上一片月辉 + 右上一枚**浮空月牙** |

两个实现要点：

  * **剪影是写死的字符串点位图**，与原版 item 贴图的 alpha 逐像素一致——「改」的是颜色
    不是形状，所以兔子脚还是兔子脚、骨头还是骨头；
  * **明暗不手描**：对每个不透明像素算到透明边的最短距离，按距离分四档
    （描边 / 暗部 / 本体 / 亮核），再叠一个左上偏向的高光——这就是原版物品
    「一圈深边、内里渐亮」的通透感来源。金线、爪尖、月牙再逐像素补。
  * 月牙一开始画在膜内，被幻翼膜那块鼓包剪影切成一团——读不出月相。
    改成**画在剪影之外**（和兔子脚的灵光星一样是浮空饰品），月牙才像月牙。

**③ 传送门贴图：深蓝紫鎏金 → 白瓷拉丝银冰蓝（`tools/gen_portal_tex.py`）**

第一百九十七批那版是深蓝紫金属 + 鎏金 + 青蓝，好看但是暗的。这一批形制一条不动
（双弯角 + 中央能量核 + 三色配色那套折跃门语言全保留），只换调色板：

  * 白瓷 `WHITE_HI/WHITE`（252~255 档）+ 拉丝银 `SILVER_HI/SILVER/SILVER_D/SILVER_DD`；
  * **最深的阴影也只是一档浅银灰** `SILVER_DD (150,159,175)`，全图没有一个深色像素；
  * 能量从青蓝淡化成**冰蓝白** `ENERGY_HI/ENERGY/ENERGY_D`（核心越靠心越白）；
  * 鎏金全部换成提白：外框收边白线、铆钉白、肋条白、顶面白环、铭条白；
  * 侧面散热槽的「槽口暗 / 槽底受光」整套明暗关系跟着翻过来（原来槽底是阴影，
    现在是受光面），科技感不减。

四面都是新调色板：正面（双角 + 冰蓝核 + 亮铭条底座）、侧面（白肋条 + 六道散热槽 +
槽里透冰蓝 + 白铆钉）、顶面（八角封盖同心银环 + 中央冰蓝十字缝）、
底面（浅银底板 + 十字加强筋 + 四角白螺栓）。

**④ 自检与产物**

* 见 §10（本批新增 3 物品 + 3 物品模型 + 重画 4 张传送门贴图；lang keys +3）。

### 12.201 第 201 批（2026-10-26）：三材改用原版结构 + 非白底色 / 传送门重画折跃漩涡

**① 需求**（用户原话，两条）：

1. 「兔子脚用原版的兔子脚改，骨头，翎月翼膜也是 底色不要白色太怪了」；
2. 「传送门方块全部重新设计，需要有折跃的感觉，像素风格即可 不要太真实」。

**② 玄灵三材：结构换成原版的，底色全换**

第二百批那版有两个问题：剪影虽然抄了原版 alpha，但明暗是按「到透明边距离」
自动算的——原版兔脚那只分明脚趾的深色趾线、骨头的骨节起伏全被抹成平滑渐变；
而且三件都是白银底，在一堆金 / 青 / 橙的模组材料里白得扎眼。这一批：

  * **结构直接逐像素抄原版**：三张原版贴图（`rabbit_foot` / `bone` /
    `phantom_membrane`）按**调色板点位图**抄进 `gen_spirit_relics.py`
    （`.` = 透明，字符 = 该像素在原版调色板里的序号，序号按亮度排），
    上色时序号一一对映到新色带——原版的趾线、骨节、膜纹一根不少；
  * **底色全部换掉，没有一件是白的**：

| 物品 | 新色带 | 装饰 |
|---|---|---|
| 玄灵兔子脚 | 原版的棕，暖一档（5 档棕） | 鎏金爪尖 + 中脊提亮 + 浮空灵光星 |
| 神浸魔骨 | 暗旧骨的**藕褐**（5 档） | 鎏金嵌线（带暗侧）+ 两端金箍 + 骨心紫焰 |
| 翎月翼膜 | **月夜青紫**（9 档） | 三道鎏金羽脉 + 脉根金结 + 膜面月辉 + 浮空月牙 |

**③ 传送门重画：折跃漩涡（`tools/gen_portal_tex.py`）**

第二百批那版是「白瓷双角 + 中央一颗菱核」——干净但**不动**。这一批整块重画，
门框与双角保留（那是「门」），门洞里换成**折跃漩涡**（那是「门里正在发生的事」）：

  * **两臂像素螺旋**：半径 1.2→6.4、每臂 1.5 圈、两臂差半圈、臂宽 2px，
    亮度按 `(1-t)^1.4` 越靠心越亮——能量正被拧进涡心；
  * **涡心热核**：3×3 一朵白到发青的火；
  * **四颗向内火花**：2 像素短划指向涡心（「吸进去」的方向感）；
  * 门洞池子铺一层很淡的蓝（仍是浅色，不犯上一批「不要暗色」），漩涡才立得起来；
  * 侧面：肋条两侧各三枚 **› 折跃箭头**，尖朝当中；顶面：从上往下看的**迷你漩涡**；
    底面：一圈虚线能量环。

**圈数是试出来的**：0.75 圈读作「逗号」、3 圈以上两臂叠成一团蓝；
离线并排渲染了 2 臂×1.0 / 2 臂×1.5 / 3 臂×0.75 / 加环 四种配置
（`tools/preview/vortex_try.png`），只有 **2 臂 × 1.5 圈 × 2px** 读得出「双臂涡」。

**④ 尺寸口径**（用户问「传送门和水晶塔的 16×16 还是 32×32」）：

  * **传送门 32×32**（四张：front / side / top / bottom）——漩涡一圈要二十几个
    采样点，16 像素里会碎成虚线；
  * **水晶塔（采矿方块）16×16**（三张：side / top / bottom）——它是整格实心方块，
    16 像素够画机械面板，也和模组其余物品 / 方块一个口径。

**⑤ 自检与产物**

* 见 §10。

### 12.202 第 202 批（2026-10-26）：台座改 3×3 砖底座 + 鎏金承柱 / 传送门改紫蓝科幻线条

> ★ 承柱一节已由**第 211 批**整体取代（九根承柱 → 一件半格高的鎏金楼台，见 12.211），
> 第 216 批改成**三层地坛方台**（见 12.216），第 217 批整体鎏金（见 12.217），
> 第 218 批又回到**八段楼台 + 横向明暗贴图**（见 12.218），第 219 批改成**一级一级的台阶剖面 +
> 侧面直棂漏窗 + 台面 16 枚小字绕圈**（见 12.219）；本节留作沿革记录。

**① 需求**（用户原话，两条）：

1. 「修改玄枢基座多方块，把原来基座的位子换成四象石砖 然后上面放基座，激活时下面一层
   改变模型托举上面的基座，修改模型 整体鎏金配色然后符合国风」；
2. 「传送门方块重新设计，增加紫色蓝色科幻风格，添加一点线条感」。

**② 台座开坛结构：四角砖 → 3×3 砖底座 + 台座上置**

开坛判据从「台座坐地、四角（3×3 对角）立四块砖」改成「**地上平铺 3×3 九块
四象石砖、台座站在中心砖上**」：

  * `XuanshuPedestalBlockEntity`：新增 {@code BASE}（九格偏移，全在**下一层**），
    {@code cornersIntact} → {@code baseIntact}（九格都认），{@code lightBricks}
    点亮 / 熄灭九格；{@code CORNERS}（对角四格）保留给渲染器的**四象絮带**用
    （絮带从砖顶往上搭到台座顶心，y 偏移 −1）；
  * `SixiangStoneBrickBlock`：**删掉 {@code CORNER} 朝向属性**（那是为「四角砖各自
    朝台座退台的斜面阶梯」服务的，现在激活态是旋转对称的承柱，朝向失去意义），
    blockstate 只剩 active 两态；{@code onRemove} 改成在**上一层 3×3** 里找台座熄火；
  * 文案跟着改：开坛失败提示「脚下未立 3×3 四象石砖底座」、魔导书两页（铺九块 /
    台座摆正中那块上面）。

**③ 激活态重画：九根鎏金承柱（新增 `tools/gen_pillar_obj.py`）**

激活时九砖一齐从青灰平砖变成**鎏金承柱**，一整圈柱子蹲在台座底下把台座托起来。
OBJ 五段（像素，y=0 砖块底面）：

| 段 | y | 边心距 | 说明 |
|---|---|---|---|
| 柱础 | 0–2.2 | 7.0 | 暗金石础，**底面封死**（不透视） |
| 柱身 | 2.2–12.8 | 5.5→5.0 | 鎏金，微收分；中腰一道**回纹带** + 三道竖筋 |
| 枭混 | 12.8–13.6 | 6.4→6.0 | 外翻的金箍 |
| 坐斗 | 13.6–14.8 | 5.9→5.6 | 斗 |
| 承台 | 14.8–16.0 | 6.6→6.2 | 顶面云纹 + 中心金钉，台座柱础就落在这一圈上 |

整体鎏金：五段全是金（柱础用暗一档的石金压住脚），没有第二种色相。
64 面 / 256 顶点，绕序自动纠正 0。

**④ 传送门重画：紫蓝科幻 + 线条感（`tools/gen_portal_tex.py`）**

第二百零一批那座白金门干净但素；这一批整块翻成**紫蓝科幻**：

  * 骨架换**深紫蓝金属**（`VIOLET_DD(40,34,72)` 起，最深的档也只是暗紫蓝，不再是黑）；
  * 漩涡改**紫蓝双绞**：一臂紫（`EV`）、一臂蓝（`EB`），涡心收到近白
    （{@code draw_vortex} 新增 {@code arm_colors} 参数）；
  * **线条感**五处：门框内缘一道提亮细线、双角正中各一条直通涡心的能量芯线、
    侧甲**电路走线**（细亮线把六道散热槽一颗颗串起来、末端搭上肋条）、
    顶盖八向辐射细线、底板十字筋 + 虚线能量环；
  * 折跃漩涡形制不动（两臂 1.5 圈 × 2px + 3×3 热核 + 四颗向内火花）。

**⑤ 自检与产物**

* 见 §10。

### 12.203 第 203 批（2026-10-26）：传送门广场改 16×16 三层方台 / 三材挪位 / 兔脚重画

**① 需求**（用户原话，三条）：

1. 「你给我的预览图最左面是一格……然后中间传送门……改为 16×16」；
2. 「基座底部我希望是连贯在一起，保证美观，然后一层层往上，可以不要 obj 就单纯代码」；
3. 「三个小物品 骨头 翼膜 兔子脚放在戒指后面，兔子脚贴图再和原版区别大一点」。

**② 广场：从「schem 编译」改成「纯代码算」**

旧形制（第 198 批从 `chuansongmen.schem` 编译来的 23×25 八角广场）有三个问题：最外圈有一格
**孤立方块**（预览图最左边那一格）、中央留了个 6×6 方井（俯视能看穿）、铺地是歪成菱形的、
最底下 201 格里有 1 格与主体不连通。这一批整份退役，`tools/gen_portal_blueprint.py` 重写成
**程序化生成**：

| 局部 y | 内容 | 格数 |
|---|---|---|
| 0 | 16×16 铺地（**整块，四连通 1 块**） | 256 |
| 1 | 12×12 第一层台 | 144 |
| 2 | 8×8 第二层台（门立在这层上） | 64 |
| 3–14 | 门：柱脚 / 柱身（两道金箍 + 冰蓝玻璃缝）/ 金槛 / 4×7 蓝玻璃门面 / 两级门楣 / 门冠 | 140 |

每层同一套花纹，由 `tier()` 一笔画出：**外圈白瓷 + 四角金块**、**第二圈紫珀 + 四个斜角海晶灯**、
**四边正中各嵌两格冰蓝玻璃**——灯位层层对齐，俯视是一圈套一圈。台心一律錾制白瓷，
最底层的心用便宜的石英块（反正被上层盖住）。

材料从 **12 种 1099 格**（含 3 颗信标）降到 **7 种 604 格**：

| 方块 | 数量 |
|---|---|
| chiseled quartz block | 260 |
| quartz block | 144 |
| purpur block | 80 |
| gold block | 40 |
| light blue stained glass | 36 |
| blue stained glass | 28 |
| sea lantern | 16 |

**没有方块实体**，所以一键收回不会再「剩下三颗信标收不回来」。

**③ 摆放公式不用改**

`PortalGateStructure` 一个字没动：`FRONT_SHIFT = 0 − minZ` 在这一版自然等于 **0**（minZ = 0），
`Y_SHIFT = −1` 照旧把整座广场沉到方块脚下；`facing` 旋转、四份布局缓存、`StructureDeployer`
的施工流程全部沿用。改的只有 `PortalGateBlueprint.java`（重生成）和四处注释里的格数
（1099 → 604）。

**④ 预览脚本：三张图 + 第四条断言**

`tools/preview_portal_place.py` 重写：

  * 断言四条 —— 深度 0 / 前沿整排 `y ≤ −1` / `(0,0,0)` 空 / **最底层整块连通**（新增，
    对应用户「基座底部连贯在一起」：四连通块数 = 1 且格数 = 边长²）；
  * 三张图 —— `portal_place_iso.png`（等距，HV 提到 24 让一格格台阶看得清）、
    `portal_place_top.png`（四朝向并排俯视，看得出灯位层层对齐）、
    `portal_place_front.png`（**新增**：正视，右侧标出 y −1 铺地 / y 0 方块层 / y 1 / y 2 / y 13 门冠）。

**⑤ 玄灵三材挪位 + 兔脚重画**

  * `ModTab`：三件从「其余玄枢材料」区末尾挪到 `XUANSHU_VOID_EYE`（万劫玄枢·洞虚守一戒）**正后面**，
    顺序照用户所列：神浸魔骨 → 翎月翼膜 → 玄灵兔子脚；
  * `gen_spirit_relics.py` 的兔脚：**水平镜像** + 暖棕换**灵玉青** + 踝部**鎏金脚箍** +
    趾尖**灵光青爪尖** + 掌心**灵纹**。结构仍是原版点位图（趾线、爪垫起伏都在），
    但方向、色相、部件全变了，进背包一眼就能跟原版 `rabbit_foot` 分开；
  * 中途踩到一次：整排描金（y ≥ 12 全换金）会让脚跟变成一块金底板，整只脚像条鱼 ——
    改成只箍 y = 12 那一斜排（踝），脚跟留玉色。

**⑥ 自检与产物**

* 见 §10。


### 12.204 第 204 批（2026-10-26）：恢复旧 schem 广场 / 贴图 16×16 三面 / 两个多方块瘦身

**① 需求**（用户原话，三句）

1. 「我要你改的是传送门的方块贴图，预览图最左面是一格门然后中间传送门不要只留传送，改为 16×16 也三张把」；
2. 「多方块给我改回去」；
3. 「缩减一下没用的代码，特别是两个多方块建筑，压缩能压缩的」。

**② 先把 schem 找回来**

第 203 批把 `gen_portal_blueprint.py` 整个改成「纯代码算三层方台」时，`chuansongmen.schem`
被当成退役资料删了。这一批要「改回去」，先在回收站里翻到它 ——

```
D:////$RECYCLE.BIN////S-1-5-21-…////$RMBIAOR////worldedit////schematics////chuansongmen.schem   （1 547 B）
```

拷进 `tools/schematics/`（连同水晶塔的 `structure.schem`），**从此两份 schem 跟着项目走**：
生成器默认就读这里，不再要求命令行传路径，也不再依赖回收站。

读出来与第 198 批的记录逐项对上：**23×25×18、1099 格、36 种状态、12 种方块**，底面正中归零后
局部 x −11…11、z −12…12、y 0…17 —— 与 README 第 198 批那张材料表分毫不差，可见当年编译的就是这一份。

**③ 数据从四元组改成分层字符画**（本批真正省体积的地方）

新增 `tools/blueprint_map.py`，两个生成器共用：

| | 第 192 / 198 批 | 第 204 批 |
|---|---|---|
| 传送门 | `int[] CELLS` 1099 × 4 个数字 ≈ **14 KB** | `MAP` 字符画 1099 个字符 + 占位点 ≈ **6 KB** |
| 水晶塔 | `int[] CELLS` 255 × 4 个数字 ≈ **3.3 KB** | `MAP` 255 个字符 + 占位点 ≈ **1.2 KB** |
| 可读性 | 一串坐标，看不出形制 | 一层一段，直接看出轮廓 |

格式（`PortalGateBlueprint.java` 里就是这样）：

```
MAP = """
== 0,-11,-12          ← 层号 + 该层左上角
...........A...........
..........ABA..........
.........CCCCC.........
"""
```

`== ` 行起一层，其后每行沿 +Z、每个字符沿 +X，`.` 是空气，其余字符查 `CHARS`（与 `STATES` 同序）。
`simplify()` 顺手把等于默认值的属性删掉（`waterlogged=false` / `half=bottom` / `shape=straight` /
`type=bottom` / `axis=y` / 六向 `false`）—— `state()` 本来就从 `defaultBlockState()` 起，删掉等价，
36 条状态串因此从平均 90 字降到 45 字。

**④ 两个多方块怎么瘦的**

  * `StructureDeployer` 里定义唯一的 `record Part(BlockPos offset, BlockState state)`
    —— 原来的 `PortalGateStructure.Piece`（绝对坐标）与 `VoidMiningStructure.Part`（相对坐标）合并，
    统一成**相对锚点**，`at(anchor)` 一步算世界坐标；
  * `filled` / `isComplete` / `build` / `dismantle` 全部收进施工器，两边结构类不再各写一遍循环；
  * 删掉的东西：两份 `placements()`（每次点按钮 new 1099 / 255 个短命对象）、
    `Layout` 里那套包围盒缓存（没人用）、`VoidMiningStructure.satisfied` 的重复实现、
    `PortalGateStructure.height/bounds/missing`（无引用）；
  * 顺手修一个真 bug：`build()` 原来先判 `!isAir` 直接 `continue`，再判 `hasBlockEntity` ——
    空气不可能有方块实体，所以「被箱子占着」这条永远数不到，`blocked` 恒为 0。现在改成
    `isAir` 收进待办、`else if (hasBlockEntity)` 才计数；
  * 结果：`PortalGateStructure` 230 → 150 行、`VoidMiningStructure` 300 → 150 行、
    `StructureDeployer` 227 → 230 行（功能变多但去掉了重复）。

**⑤ 传送门贴图：32×32 四面 → 16×16 三面**

`gen_portal_tex.py` 重写：边长 16，只出三张，底面借用顶盖。

  * **front**：拱形门洞（顶上收窄一格）+ 两根门柱（朝洞那侧提亮）+ 门楣两道横梁 + 门槛三点能量 +
    洞里两臂紫蓝双绞漩涡（半径 0.6→3.1）；
  * **side**：中间竖肋（暗边 + 亮线 + 高光）+ 两道散热槽（槽口深、槽底受光、两端点能量）+
    电路走线把槽串起来 + 四枚折跃箭头；
  * **top**：同心方环四圈（亮 / 暗交替）+ 俯视迷你漩涡 + 八向细线 + 八角切角。

`xuanshu_portal_bottom.png` 删除，`models/block/xuanshu_portal.json` 的 `down` 改指 `top`；
预览 `portal_faces.png` 改成三格横排（最左是门），`portal_iso.png` 重出。

**⑥ 预览与自检**

`preview_portal_place.py` 跟着改：数据源从 `STATES` / `CELLS` 换成解析 `MAP` 字符画，
断言回到旧版口径（**深度 0**、前沿整排 `y = −1`、`(0,0,0)` 空），并额外报告底层四连通块数
（旧形制本来就有一格孤岛 + 中央方井，**不再当作错误**）。四朝向都通过：

```
north 深度 0  前沿 y [-1]  高 -1…16  格数 1099  OK
east  深度 0  前沿 y [-1]  高 -1…16  格数 1099  OK
south 深度 0  前沿 y [-1]  高 -1…16  格数 1099  OK
west  深度 0  前沿 y [-1]  高 -1…16  格数 1099  OK
```

出三张图：`portal_place_iso.png`（等距，12 种方块按本色上色）、`portal_place_top.png`
（四朝向并排俯视）、`portal_place_front.png`（正视 + 标高）。

**⑦ 清掉的一次性产物**

  * `tools/preview/portal_schem.png`（旧 schem 全层调试图）、`tools/preview/schem_structure.png`（副产物）；
  * `src/main/resources/.../textures/block/xuanshu_portal_bottom.png`（四面改三面后无引用）；
  * `preview_obj_iso.py` 里从没被调用过的 `sample()`；
  * `pyclean_scan.py` 补一条「跨文件引用」判断 —— 共享模块（`blueprint_map.py`）的函数是别的脚本在调，
    以前按「只在本文件找引用」会误报成死代码。

**⑧ 产物**

* `selfcheck` **ALL GREEN**：models 127 / blockstates 29 / lang keys 485 / resources **504**
  （删了 bottom 那张），贴图尺寸 `16×16: 99 / 16×64: 1 / 32×32: 55 / 256×256: 2`；
* `deadcode_scan` **0**、`orphan_scan` 无孤儿、`pyclean_scan` **0**；
* 构建产物 **811 828 B** · SHA256 `b7827e10c08536ab…53bba6cb`
  （比第 203 批小 **15 202 B**：字符画编码省下的），已自动同步进本机 mods，sha 一致；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 204 行。


### 12.205 第 205 批（2026-10-27）：祛魔书挪位 / 新增玄枢双生书 / 两本书同一套贴图

**① 需求**（用户原话）

1. 「将祛魔书放到戒指后面」；
2. 「添加一个新物品 功能是和附魔书合成产出两本一样的附魔书，帮我取一个名字，贴图就用祛魔的贴图，
   中间紫色带子改为蓝紫色偏蓝色」；
3. 「单这次不要编译」。

**② 创造栏**：祛魔书挪到洞虚守一戒正后面

`ModTab` 的功能物品段重排为：

```
阿卡西记录 → 授业卷轴 → 洞虚守一戒 → 玄枢祛魔书 → 玄枢双生书 → 神浸魔骨 → 翎月翼膜 → 玄灵兔子脚 → 成品法杖……
```

两本书成对挨着、整体收在戒指后：它们都是「拿在手上使一次」的物件，与后面那三件灵兽材料
（进合成台当材料）不是一类。新书紧随祛魔书，是因为它俩是同一套路的两头 —— 一本**取下**附魔、
一本**复制**附魔。

**③ 新物品：玄枢双生书 `twin_book`**

命名取「一本变两本」——「双生」既说了数量（两本），也说了要求（**一模一样**，不是两本随机附魔书）。
英文 `Xuanshu Twin Tome`；稀有度 `RARE`、`stacksTo(16)`，与祛魔书一致。

| | 玄枢祛魔书（第 101 批） | 玄枢双生书（本批） |
|---|---|---|
| 配方 | 祛魔书 + **一件带附魔的物品** | 双生书 + **一本附魔书** |
| 产出 | 一册附魔书（附魔搬过去） | **两册一模一样的附魔书** |
| 原物 | 退回、附魔清零 | 被吃掉（两本出两本） |
| 数据来源 | `ENCHANTMENTS`（物品自身） | `STORED_ENCHANTMENTS`（书里存的） |

实现是新的 `TwinBookRecipe`（`CustomRecipe` 一支，和祛魔书同路）：

* 匹配：非空恰好两件 = 一本 `twin_book`（可整摞，合成只吃一本）+ 一本 `minecraft:enchanted_book`，
  后者必须 `count == 1` 且 `STORED_ENCHANTMENTS` 非空；
* 产物：`book.copyWithCount(2)` —— **整份复制**，附魔等级、铁砧改名、别的模组挂的组件全都照搬，
  不是「重新拼一份附魔」；产物格写 2，取走时由 `Inventory#add` 自动拆成两格（附魔书上限为 1）；
* 序列化器 `skydeitymysticpivot:twin_book`（借原版 `SimpleCraftingRecipeSerializer`），
  配方 JSON 两个：`twin_book_transfer.json`（上面这条动态配方）、`twin_book.json`（书本体；**第 206 批起改为奥术工作台合成**，见 12.206）。

**④ 贴图：同一张底，绑带旋两档**

原 `tools/gen_disenchant_book.py` 改名成 `tools/gen_book_tomes.py`（它现在管两本），顶部一张表：

| 物品 | 色相 | 饱和度 | 观感 |
|---|---|---|---|
| `disenchant_book` | 300° | ×0.88 | 粉紫（与第 102 批一致，本次重出后像素不变） |
| `twin_book` | **265°** | ×0.92 | **蓝紫偏蓝** |

色相只旋绑带（判据仍是「HSV 饱和度 > 0.40 且色相 ≥325° 或 ≤12°」），**明度一格不动** —— 褶皱、暗边、高光全留着，金扣（色相 45°）也不碰。265° 是并排试了 300 / 275 / 265 / 255 四档后定的：
275 还偏紫、255 已经发靛，265 正好是「蓝紫、但一眼看还是紫」的那一档。
另出 `tools/preview/book_tomes.png`（左祛魔、右双生，逐像素放大 + 网格）。

**⑤ 本批不编译**

用户明确「单这次不要编译」，所以**没有跑 gradle、没有新 jar**：`.none` 里记的产物仍是第 204 批那枚
**811 828 B · SHA256 `b7827e10c08536ab…53bba6cb`**。代码与资源已落盘、自检已过，
下次编译时这些改动才会进包。

**⑥ 自检**

* `selfcheck` **ALL GREEN**：models **128**（+1）/ blockstates 29 / lang keys **487**（+2）/
  resources **508**（+4：贴图 1 + 模型 1 + 配方 2），贴图尺寸 `16×16: 100 / 16×64: 1 / 32×32: 55 / 256×256: 2`；
* `deadcode_scan` 0、`orphan_scan` 无孤儿、`pyclean_scan` 0；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 205 行。


### 12.206 第 206 批（2026-10-28）：两本书改奥术合成，六基元各 815

**① 需求**（用户原话）

> 「玄枢祛魔和双生的合成表改为奥术合成 6 种要素各要 815。祛魔的合成表为：1.紫水晶 2.下届合金 3.紫水晶
> 4.模组的盐 5 书 6 模组的盐 7 枫树树叶 8 玄枢布 9 枫树树叶。双生的合成为：1.龙息 2.下届合金 3.龙息
> 4.模组的盐 5 书 6 模组的盐 7 枫树树叶 8 玄枢布 9 枫树树叶。不用编译 就只改这个。」

**② 改法：只换配方类型 + 重排九格**

两个文件原地改内容（文件名不动，所以配方 id 仍是 `disenchant_book` / `twin_book`）：

| | 第 205 批（工作台） | 第 206 批（奥术工作台） |
|---|---|---|
| `type` | `minecraft:crafting_shaped` | `thaumcraft:arcane_shaped` |
| 要素 | 无 | `vis` 六基元各 **815** |
| 格数 | 十字 / 四角 | **九格全占** |

九格按 1→9 的行序（左上 → 右下）落成三行 `ABA / CDC / EFE`：

| 位 | 祛魔书 | 双生书 |
|---|---|---|
| 1 / 3 | `minecraft:amethyst_shard` 紫水晶 | `minecraft:dragon_breath` 龙息 |
| 2 | `minecraft:netherite_ingot` 下界合金 | 同左 |
| 4 / 6 | `skydeitymysticpivot:xuanshu_bagua_salt` 玄枢八卦之盐 | 同左 |
| 5 | `minecraft:book` 书 | 同左 |
| 7 / 9 | `skydeitymysticpivot:maple_leaves` 枫树叶 | 同左 |
| 8 | `skydeitymysticpivot:xuanshu_cloth` 玄枢布匹 | 同左 |

```json
"vis": { "aer": 815, "terra": 815, "ignis": 815,
         "aqua": 815, "ordo": 815, "perditio": 815 }
```

要素量按用户口径 **六基元各 815** 写在 `vis` 里（该字段只收整数）；两本书同价，量产门槛靠材料
（紫水晶 / 龙息 + 下界合金 + 盐 + 枫叶 + 玄枢布）而不是要素，改价只改这两个 `vis` 块。

**③ 没动的地方**

* `recipe/disenchant_book_transfer.json` / `recipe/twin_book_transfer.json` 仍是**合成台**的
  `CustomRecipe`（`skydeitymysticpivot:disenchant_book` / `:twin_book`）——「书 + 带附魔物品 → 附魔书」、
  「书 + 附魔书 → 附魔书 ×2」这两条动态配方原样保留，不受奥术工作台影响；
* 物品、贴图、创造栏顺序、语言键一律没动。

**④ 本批同样按用户口径不编译**

没有跑 gradle、没有新 jar，`.none` 里记的产物仍是第 204 批那枚 **811 828 B · SHA256 `b7827e10…bba6cb`**。
配方 JSON 与代码自检（`selfcheck` / 死代码 / 孤儿 / 脚本残留）全绿，改动要等下次编译才进包。


### 12.207 第 207 批（2026-10-28）：注灵台分段阈值与源质罐都提到 2048

**① 需求**（用户原话）

> 「1.将注灵台要素上限改为 2048　2.玄枢源质罐子要素上限也改为 2048 编译 jar 放入 mods」

**② 注灵台：`BASIC_CAP` 100 → 2048**

这个数在 `XuanshuNodeModifierMenu` 里，只被 `nextAspect` 读一次，作用是**分段**：

| 段 | 条件 | 抽法 |
|---|---|---|
| ① | 六门基础有哪门 ≤ 0 | 直接补那门 |
| ② | 六门齐、但有门 < `BASIC_CAP` | 八成加在没满的六门之一，两成添新要素 |
| ③ | 六门各满 `BASIC_CAP` | 八成加在节点现有要素上，两成添新 |

所以把它抬到 2048 的效果是：**在六门基础各涨到 2048 之前，「八成」那一票一直喂六门**，
不会提前转成「随机喂现有复合要素」。它**不是硬顶** —— `addAspects` 只加不判顶，
节点单门能到多少由本体 `NodeVis` 决定（只限制种类数 ≤ 256）。

一次运转 +12，从 12 喂到 2048 约 **170 次**（170 瓶天域之华 + 170 枚玄枢锭 + 每笔六元各 129 Vis；**第 214 批起改为各 77**）。

**③ 源质罐：`CAPACITY` 1024 → 2048**

`XuanshuEssentiaJarBlockEntity.CAPACITY`，所有用到它的地方都跟着走：

* `fillRatio()` 的分母（液面高度 = 最满那种 / CAPACITY）；
* `EssentiaStorageView.essentiaCapacity()` —— 本体 Jade 那条「数量 / 上限」比例条；
* 灌入时的余量、存档读档的钳位（`Math.min(CAPACITY, …)`）；
* 32 种 × 2048 = 一罐最多 65536 份；种类上限 `MAX_KINDS = 32` 没动，物品组件体积只随**种类**涨。

**④ 文案同步**

`block.skydeitymysticpivot.xuanshu_essentia_jar.lore2`（中英）、
`block.skydeitymysticpivot.xuanshu_node_modifier.lore1`（中英）、
`bagua.page.node_modifier`（中英）里的 1024 / 100 全部改成 2048，免得书里写的和实际不符。


### 12.208 第 208 批（2026-10-28）：本体换 20753 / 灵目改新接口 / 编译

**① 编译第一次就失败，原因不在代码里**

`./gradlew compileJava` 报了上百条「程序包 `dev.tc4port.thaumcraft.api.aspect` 不存在」——
`gradle.properties` 指的 `.none/1.21.1-neoforge-20751-dev/` **整个目录已经不在了**：
用户把快照换成了 `1.21.1-neoforge-20753-dev-revealing-abilities-snapshot`（10-28 19:53），
旧目录删掉，而 `parent_api` / `parent_runtime` 还指着 20751。

于是把两条路径改指新快照（`tc/…-20753-api.jar` 与根目录 `…-20753.jar`），
快照里的 fm / nm / tb / te / tm / tt / wt 七个附属本 mod 一个都不依赖，不引。

**② 20751 → 20753 唯一一处公开 API 删除：两个「揭示」接口**

再编译只剩 6 个错，全在 `XuanshuVoidEyeItem`：

```
AuraRevealingGear / GogglesOverlayGear 找不到符号
revealsThaumcraftAura / showsThaumcraftGogglesOverlay 不覆盖超类型方法
```

20753 的 revealing-abilities 重构把「能力」统一成**属性**：

| 20751 | 20753 |
|---|---|
| `AuraRevealingGear#revealsThaumcraftAura(stack, wearer)` | `PlayerAbilityGear#grantsPlayerAbility(stack, wearer, ability)` |
| `GogglesOverlayGear#showsThaumcraftGogglesOverlay(stack, wearer)` | 同上，`ability == PlayerAbility.GOGGLES_OVERLAY` |
| 渲染端每帧 `instanceof` 物品 | 渲染端只读 `PlayerAbility.AURA_REVEALING` 属性 |

本体 `PlayerAbilitySources` 每 tick 扫「四件盔甲 + 全部 Curios 饰品」，逐个问
`EquipmentAbilities.grantsPlayerAbility(...)`，命中就把属性点亮（`ArmorAndAccessories` 含饰品栏，
所以戒指仍然有效）；揭示之护目镜本体也改成了纯属性路线，类上不再挂接口。

改法：`implements ICurioItem, PlayerAbilityGear`，两条方法合成一条

```java
@Override
public boolean grantsPlayerAbility(ItemStack stack, Player wearer, PlayerAbility ability) {
    return ability == PlayerAbility.AURA_REVEALING || ability == PlayerAbility.GOGGLES_OVERLAY;
}
```

行为不变：戴着就看得见节点、瞄准容器就浮要素。`ModItems` 的类注释同步改写。

**③ 其余一切照旧**

六十多个类**一行没改**就过了 20753 的编译（`EssentiaStorageView` / `WandApi` / mixin 的六个目标类
签名都没再动）。只有两条 `EventBusSubscriber` 的 `bus()` 过时警告，与本次无关。

**④ 构建**

```
SkydeityMysticPivot-0.1.0.jar   815 701 B
SHA256  00062e12f462627c5d9ae5846467a46d3bff11c07be53f75eba33618d1a473a4
```

比第 204 批那枚大 **3 873 B**：205 批的双生书（贴图 + 模型 + 两条配方 + 一个配方类）、
206 批的奥术配方（两个 JSON 改内容）、207 批的两个常数，一起进了包。

自检仍 ALL GREEN（models 128 / blockstates 29 / lang keys 487 / resources 508）。


### 12.209 第 209 批（2026-10-28）：注灵台 256 + 加权三段 / 五张奥术配方改价 / 终局杖不打折 / 枫树苗入炉

**① 需求**（用户原话，四条）

1. 「注灵台改为先检测 6 种基础的是否到 256 如果到了，则按之前的概率加其他新的要素，
   然后还是添加 6 种基础元素概率大一点为基础，魔导书中详细写好概率」；
2. 「四种神兽锭 需要的合成中需要的六种元素修改为 87 未尽的玄枢锭改为 129」；
3. 「宇宙寰宇支配法杖改为 100% 法力消耗」；
4. 「给枫树树苗添加合成表 奥数合成 5 位：宏伟之木树苗 2 4 6 8 位为樱花树叶 各要素 6 点」。

**② 注灵台：阈值落回 256，第三段开始按权重分票**

`XuanshuNodeModifierMenu` 三处改动：

| 量 | 旧 | 新 |
|---|---|---|
| `BASIC_CAP`（六门基础毕业线） | 2048（第 207 批） | **256** |
| `NEW_ASPECT_PERCENT`（添新） | 20 | 20（不变） |
| `BASIC_SHARE_PERCENT`（第三段里六门占八成的几成） | — | **60**（新增） |

三段现在的完整概率（也是魔导书下一页写的表）：

| 段 | 判据 | 抽法 | 全局概率 |
|---|---|---|---|
| ① | 六门有任一门 ≤ 0 | 补最靠前的那门缺（风→地→火→水→序→灭） | 100% |
| ② | 六门齐、有门 < 256 | 添新 / 在未满的 k 门里等概率 | 添新 20%，六门合计 80%（每门 80/k%） |
| ③ | 六门各 ≥ 256 | 添新 / 六门等概率 / 现有复合要素等概率 | 添新 20%，六门合计 48%（每门 8%），其余合计 32%（每门 32/N%） |

一次运转仍是 +12，所以从 12 喂到 256 约 **21 次**（21 瓶天华 + 21 枚玄枢锭）。
第三段的实现：先掷一次 `nextInt(100)` 决定落在哪一块，`roll < 20` 添新、`roll < 68` 六门、
其余落在 `existingCompounds()`；后者把节点里 `> 0` 的非基础要素**排一次序**再抽，
免得 `LinkedHashMap` 的写入历史让同一串随机数抽出不同结果。

**③ 魔导书：把概率写成一整页**

`bagua.page.node_modifier_lore` 原本是空页（书里翻过去一片空白），这一批直接写成
「概率明细」页：三段判据 + 每段百分比 + 「添新只从没见过的复合要素里抽」+ 一个算例
（节点已有 16 门复合要素时，第三段 20% 添新 / 48% 六门 / 32% 摊在 16 门，每门 2%）。
功能页 `bagua.page.node_modifier` 改短为「判据是 256，明细见下一页」，物品 lore 也换成新配比。
中英各一套。

**④ 五张奥术配方改价**

| 配方 | 旧 vis（六基元各） | 新 |
|---|---|---|
| 青龙锭 / 朱雀锭 / 白虎锭 / 玄武锭 | 100 | **87** |
| 未烬的玄枢锭 | 77 | **129** |

改的是 `recipe/*.json` 的 `vis` 块（六基元同值），`pattern` / `key` 未动。文案同步：
`bagua.page.node_ingots`、四条 `bagua.recipe.*_ingot`、`bagua.page.recipe_ingot_note`、
`bagua.page.unfired_req` 的中英文本。

**⑤ 宇宙寰宇支配法杖：耗魔 10% → 100%**

`ModWandParts.COST_MODIFIER` 0.1F → 1.0F。这个值在 `register()` 里同时喂给
`WandCapSpec` 的 `baseCostModifier` 与 `specialCostModifier`，所以普通施法与特化通道一并不再打折。
容量 99 999 与「无需充能」不变 —— 终局杖的强度改由储量与免充能承担。

**⑥ 枫树树苗：新的奥术配方**

`recipe/maple_sapling.json`（新增）：

```
" C "
"CAC"      A = thaumcraft:greatwood_sapling（宏伟之木树苗，第 5 位）
" C "      C = minecraft:cherry_leaves（樱花树叶，第 2/4/6/8 位）
```

`type` 为 `thaumcraft:arcane_shaped`，`vis` 六基元各 **6**。空格在 `ShapedRecipePattern`
里就是「留空」，所以这个十字只占五格。

**⑦ 自检与产物**

* `selfcheck` **ALL GREEN**：models 128 / blockstates 29 / lang keys 487 / resources **509**
  （+1 = 枫树苗配方），贴图尺寸 `16×16: 100 / 16×64: 1 / 32×32: 55 / 256×256: 2`；
* `deadcode_scan` 0、`orphan_scan` 无孤儿、`pyclean_scan` 0；
* 构建 **817 280 B** · SHA256 `19b0f439cfee16e2aa85708ec1daccf3cbed733bdcd35776783e863a3e14e3ad`
  （比第 208 批大 **1 579 B**：多一条配方、语言文本变长）；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 209 行。


### 12.210 第 210 批（2026-10-28）：玄枢耀光 / 四法核重定价与三核设计 / 源质罐入奥术台 / 八台机器要素统一

**① 需求**（用户原话，五条）

1. 「玄枢源质罐子 lore 改为：可以存储 32 中不同最多 2048 容量的源质罐」；
2. 「添加一个新的物品 玄枢耀光 颜色用 tc4 闪耀之光改，改为紫色带一点星辰颜色」；
3. 「FOCUS_GUARDIAN 合成表中布改为玄枢布，闪耀之光改为玄枢耀光 盐改为玄枢盐」；
4. 「玄枢水晶簇中间的八卦碎片改为下界之星。南北斗枢注魔只需要 8 种八卦要素 24，玄枢充能器只需要
   8 种八卦要素 24。玄枢镐和玄枢锄头改为需要 8 种八卦要素 12。注灵台和复刻台改为八种八卦 32，
   奥术台八种八卦 48」；
5. 「玄枢源质罐子合成改为奥数合成 6 种要素 54：1 神秘锭 2 钻石 3 神秘锭 5 源质罐子（tc4 的）
   4 6 7 8 9 位玻璃板」；
6. 「剩余三个法杖核心你帮我设计注魔合成，然后四个法杖核心所需的都是 8 种八卦 32。
   CHRONO 的注魔需要八种卦石 lore 写好，魔导书写好」。

**② 玄枢耀光 `xuanshu_nitor`**

新物品只做「注册 + 贴图 + 模型 + 语言 + 创造栏」，没有方块实体、没有功能。贴图**不重画剪影**：
`tools/gen_xuanshu_nitor.py` 读 `tools/ref/tc4_nitor.png`（本体闪耀之光那张 16×16，678 B），
按亮度查 7 段紫色带重上色（暗紫 `#1A0A28` → 亮紫 → 紫白），alpha 与明暗层次原样保留；
**孤立像素**（8 邻域里透明白像素 ≤ 1）判成「飞散的火星」，换星辰色 `#BAE2FF`（原图顶上正好四粒），
最亮核心（亮度 ≥ 245）退一档 `#D6F0FF` 紫白。**只换色，一格剪影都没动。**

它同时被补了一条奥术合成（用户没要求，但不给就只能创造栏拿，而两枚法核都要它）：
紫水晶 2 夹闪耀之光、下垫一枚玄枢八卦之盐，六基元各 30，一次 **2** 枚；书里写在
`bagua.page.materials_b` 的「材料页」，随时可以删。

**③ 乾元罡气：材料换成玄枢三材**

`recipe/focus_guardian.json` 的八格从「水银 4 / 闪耀之光 4 / 世界盐 2 / 魔力布匹 2」换成
「水银 4 / **玄枢耀光 4** / **玄枢八卦之盐 2** / **玄枢布匹 2**」，中心仍是不死图腾。

**④ 源质罐：lore 一行 + 奥术合成**

- lore 从三行压成一行（代码里的 `ItemLore` 也只剩一个键，`lore2` / `lore3` 两键中英一并删除）：
  「可以存储 32 种不同的源质，每种最多 2048 容量。」
- `recipe/xuanshu_essentia_jar.json` 由 `minecraft:crafting_shaped` 改 `thaumcraft:arcane_shaped`：

```
"ABA"      A = thaumcraft:thaumium_ingot（神秘锭，第 1 / 3 位）
"CDC"      B = minecraft:diamond（钻石，第 2 位）
"CCC"      C = minecraft:glass_pane（玻璃板，第 4 / 6 / 7 / 8 / 9 位）
           D = thaumcraft:warded_jar（本体源质罐子，第 5 位）
```

  `vis` 六基元各 **54**。

**⑤ 要素票：八台机器与两件工具统一到「八种八卦」**

| 配方 | 旧 `aspects` | 新 |
|---|---|---|
| `star_dial` 南北斗枢玄星盘 | 序 64、熵 32、风 32、火/水/地 24、离兑乾 24 | **八卦各 24** |
| `xuanshu_charger` 玄枢充能器 | 六基元各 32、巽乾坎各 20 | **八卦各 24** |
| `xuanshu_pickaxe` 玄枢镐 | 地/序 64、火/风/水/熵 48、八卦各 32 | **八卦各 12** |
| `xuanshu_hoe` 玄枢锄 | 地 64、水/序 48、风/火/熵 32、八卦各 32 | **八卦各 12** |
| `xuanshu_node_modifier` 注灵台 | 风 48、其余五基元 32、离兑乾 24 | **八卦各 32** |
| `xuanshu_replicator` 复刻台 | 序 48、风/熵 32、火/水/地 24、坎巽 24 | **八卦各 32** |
| `xuanshu_arcane_table` 奥术台 | 序/地 48、其余四基元 32、离兑乾 24 | **八卦各 48** |

**六基元一律不再收**——这些机器与工具从此只吃八卦票，和四法核、杖件同一套口径。
`xuanshu_crystal_cluster` 的中心由 `bagua_shard` 改 `minecraft:nether_star`（八卦碎片不再消耗），
其余材料与「八卦各 6」不动；书里 `bagua.page.materials_b` 的「八卦碎片居中」同步改成「下界之星居中」。

**⑥ 四法核：重新设计 + 全部八卦各 32**

| 核心 | 中心 | 八格 | 要素 | 卦石 |
|---|---|---|---|---|
| 镇狱碎青峰 | 元始珍珠 | 下界合金镐 1、玄枢锭 1、玄枢碎片 2、玄枢八卦之盐 2、八卦碎片 2 | 八卦各 32 | 艮 ×1 |
| 乾元罡气 | 不死图腾 | 水银 4、玄枢耀光 4、玄枢八卦之盐 2、玄枢布匹 2 | 八卦各 32 | 艮 ×1 |
| 赤羽焚天瞬 | **末影之眼**（原元始珍珠） | 末影珍珠 2、龙息 1、玄枢碎片 2、玄枢八卦之盐 2、八卦碎片 1 | 八卦各 32 | 艮 ×1 |
| 烛龙衔时 | 时钟 | 红石 2、水银 2、下界之星 1、玄枢耀光 2、八卦碎片 1 | 八卦各 32 | **八卦石八种齐立** |

不稳一律 6。**烛龙衔时是唯一 `mode=all` 的法核**：`trigram_gate/machines.json` 里
`trigram` 列全八卦、`mode=all`，判定中心是台座正下方那层 5×5，**缺一种就不启动**，
动作栏点名缺哪几卦；多出来的每块按 `relief 2` 压不稳、`relief_max 3`。
物品 lore 因此多出第三行（`focus_chrono.lore3`），研究页在配方页后插了一张
「八石镇坛」页（`bagua.page.focus_chrono_gate`，中英各一份，写清「凑种类不凑块数」与减压规则）。
另外三张一直空着的法核风味页（`focus_guardian_lore` / `focus_teleport_lore` / `focus_cosmos_lore`）
这一批一起写满了。

**⑦ 自检与产物**

* `selfcheck` **ALL GREEN**：models **129**（+1 玄枢耀光）/ blockstates 29 / lang keys **488**
  （+1 `bagua.page.focus_chrono_gate`，`xuanshu_nitor` 名称与 `focus_chrono.lore3` 计入、源质罐两键删除）/
  resources **512**（+1 贴图、+1 模型、+1 配方）；
* 贴图尺寸 `16×16: 101 / 16×64: 1 / 32×32: 55 / 256×256: 2`；
* `deadcode_scan` 0、`orphan_scan` 无孤儿、`pyclean_scan` 0；
* 构建 **819 376 B** · SHA256 `e7052e300b1437763aa9f4ab3af19120b2a9c7d3a3dfd49657a0e8b71eb6baad`
  （比第 209 批大 **2 096 B**）；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 210 行。

### 12.211 第 211 批（2026-10-29）：台座底座改成一件楼台 / 沟灵核心 lore 只留卦石

1. 「玄枢沟灵核心的 lore 只保留卦石那一行，其他的删掉」；
2. 「修改基座的多方块下面那一层。如截图所示，我希望是一格整体，类似楼台托举上面的基座」；
3. 「玄枢耀光是 16×16 吗」（答：是，见下）。

**① 沟灵核心 lore 只留卦石**

`XUANSHU_SPIRIT_CORE` 的 `ItemLore` 从两条压成一条：删掉
`block.skydeitymysticpivot.xuanshu_spirit_core.lore1`（「注魔祭炼而成的白玉之心。
奥术台与注灵香炉台，皆以此为基。」）这句的代码与中英两个键，只留 `trigramLore("kan", "xun", "gen")`
那一行「基座下面立坎、巽、艮」。成品说明本来都在魔导书里，物品上只该留**用时真会卡住人**的条件。

**② 四象石砖激活态：九根承柱 → 一件鎏金楼台**

第 202 批那套是**一格一根柱**：九根各带柱础、各立各的，从外面看是九个小台子拼在一起，
柱础一圈一圈、格界一道一道，截图里红圈圈的就是这片接缝。这一批改成**一整件**：

* **整座 3×3 只出一件 obj**（`models/terrace.obj`，坐标 x/z `−1..2`、y `0..1`，116 面），
  由**正中那一格**独家渲染（新状态 `center=true`），另外八格换成
  `models/block/sixiang_brick_empty.json`（只有 particle、没有 elements）——
  于是台沿绕着整座台子转一圈，中间再没有格界；
* **形制**：须弥座 + 中心方台身。须弥座这一段**第 213 批整份重写（五段 → 八段、水平挑檐全改
  45° 斜面）**，**第 218 批回到八段楼台并按段分色（现行形制见 §12.218 / §12.219）**，211 当时是：
  圭角（边心距 24，铺满 3×3）/ 下枋 23.2 /
  束腰 21.8（收进去）/ 上枋 23.2 / 台面 24（挑出一档），八棱、四个角切 45° 的斜面 ——
  就是「须弥座」的转角，也让整块读起来是**一件石作**；台面顶在 **8px（半格）**，人能踩上去。
  中心台身是**方**的（右旋 45° 的四棱柱），8.6 → 9.2（枭混外翻）→ 8.6 → 8.0 收分，
  到 16px 正好与台座那截方柱础**齐边**，不会出现上下两截错台；
* **配色**：211 当时色板直接取台座自己的贴图（`xuanshu_pedestal_base/top.png`）——
  暗墨 (22,22,22) / 暗金 (92,66,18) / 中金 (138,106,42) / 金 (184,145,46) /
  亮金 (212,175,55) / 高光 (240,208,96)；**第 217 批起改成整件鎏金九档金板
  （`GOLD0..GOLD6`），见 §12.217 / §12.218 / §12.219**；
* **碰撞箱跟着收**：外圈八格 `getShape` 给 `box(0,0,0,16,8,16)`（正中那格仍是整格，那里是台身）。
  不改的话方块仍是整格碰撞，玩家会「浮在台面上方半格」走路。原版 `getCollisionShape`
  默认取 `getShape`，覆写一处即可；
* **四象絮带起点**从「砖顶 1.02」挪到「楼台台面 −0.48」（台面在砖块底面往上 8px
  ＝台座那一格原点的 −0.5，抬两分不陷进石头里）；
* **两条贴图上的硬约束**（都写进了 `gen_terrace_obj.py` 的注释）：
  * 平台侧面一格 48px 宽只摊到 8 个纹素（横向拉 6 倍），所以侧面**一律只做垂直渐变 +
    一条横线**，放进去的 1 纹素竖缝会被放大成 6px 色块；
  * 台面上的边线一律取整 **1 纹素**（`_oct` 的 0.0417 一档）；更细的档在 45° 斜面上会被
    采成「一亮一暗」，整圈台沿就成了拉链锯齿（第一版正是这样，已修）。

**③ 玄枢耀光是 16×16**

`tools/gen_xuanshu_nitor.py` 出的就是 16×16（自检的贴图尺寸表里算在 `(16,16): 101` 那一档），
与本体那些物品贴图同一规格；贴图本身是「本体闪耀之光换紫带 + 四粒星辰色火星」，
剪影与 alpha 一点没动。

**④ 自检与产物**

* `selfcheck` **ALL GREEN**：models **130**（+1 `sixiang_brick_empty`；激活态模型换了内容）/
  blockstates 29 / lang keys **487**（−1 沟灵核心 `lore1`）/ resources **512**；
* 贴图尺寸 `16×16: 100 / 16×64: 1 / 32×32: 55 / 256×256: 2`（`pillar_stone.png` 退役，
  `sixiang_terrace.png` 接位）；
* `check_obj_models` 全过（obj 5 个 / 1860 面）、`deadcode_scan` 0、`orphan_scan` 无孤儿、`pyclean_scan` 0；
* 删掉第 202 批的 `tools/gen_pillar_obj.py`、`models/pillar.obj`、`models/pillar.mtl`、
  `textures/block/pillar_stone.png`、`textures/block/sixiang_stone_brick_active.png`
  （最后一张是第 202 批之前的平砖换色贴图，早没人引用了）；
* 构建 **822 135 B** · SHA256 `655c7bf7a97b53e5091e9d2c8bb927cc29f5010e84bef6f815fad7a44e09aec6`
  （比第 210 批大 **2 759 B**：obj 30 KB 换掉 3 KB 的柱 obj，贴图与空模型另算），
  sha 与构建产物一致；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 211 行。

### 12.212 第 212 批（2026-10-29）：水晶塔输出对齐炎炉 + 三格锭槽 / 枫凝菼蒄 / 三材出处 / 戒指注魔

用户四项：

1. 「我想要的是水晶塔输出产物的效率跟玄枢熔炉一样，要最快。指的是输出而不是生成矿物。
   给产物栏左边隔远一点添加竖着的 3 个格子，里面画上锭的描边，整体亮色 AE 风格不变，
   可以添加四神兽锭或者玄枢锭、未尽的玄枢锭添加矿机的产矿效率」；
2. 「添加一个新物品：枫凝菼蒄。打破枫叶树叶 20% 概率掉落。贴图为 16×16，不要太清晰，
   我的世界像素风；冰晶整体颜色为红色橘色，你先设计，64 一组」；
3. 「给兔子脚：添加杀死兔子 40% 概率掉落；骨头添加：击杀凋零骷髅 40% 概率掉落；
   翼膜添加：击杀幻翼 80% 概率掉落。同时添加 lore」；
4. 「戒指添加合成表，注魔合成，需要 8 种卦石，8 种卦象要素各 64，难度最高。中心物品：
   揭示之护目镜；周围：玄枢盐 2、玄枢布 2、玄枢锭 2、下界之星 2、龙蛋 1、枫凝菼蒄 2」。

**① 水晶塔：输出六面 + 三格锭槽**

第 199 批那次「提速」只动了**周期**（`CYCLE_TICKS` 100 → 1）与**每批几种**，输出这一侧
仍旧只推**东西南北四面**。炎炉的 `autoOutput` 走的是 `Direction.values()`，默认配置里
**下面就是输出面** —— 所以玩家把漏斗摆在塔底下时，炎炉出得来、水晶塔出不来，看上去就是
「产物堵在塔里、输出不是最快」。这一批把两侧对齐：

* `autoOutput` 的循环从 `Direction.Plane.HORIZONTAL` 改成 `Direction.values()`，
  仍是**每 tick 一轮、整摞推**（`AUTO_IO_TICKS` 第 199 批就是 1，没动）；
* 顺序也理顺了：原来 `if (!formed) return;` 在 `autoOutput` **之前**，光坛一塌，栏里的矿
  就再也推不出去。现在停转分支里先跑一次 `autoOutput` 再清进度；
* `SLOT_COUNT` 拆成 `OUT_SLOTS = 12`（产出栏）+ `UPGRADE_SLOTS = 3`（锭槽），合计仍是 15；
* 对外那份 `IItemHandler` 的 `getSlots()` 改成 `OUT_SLOTS` —— 锭槽是玩家插的升级件，
  不能被管道当成「机器的一个格子」抽走。

**锭槽**：12 格产出栏之后接 3 格（`UPGRADE_BASE = 12`），每格一枚、`stacksTo(1)`、**不消耗**，
按锭的难得程度给效率：

| 锭 | 效率 |
|---|---|
| 四神兽之锭（`azure_dragon_ingot` 等四枚） | **+3** |
| 玄枢锭（`xuanshu_ingot`） | **+2** |
| 未烬的玄枢锭（`xuanshu_ingot_unfired`） | **+1** |

三格合计 0~9，只加**产出批量**，不动周期：

* `typesPerCycle() = min(4 + 效率, 13)` —— 一批点几种矿；
* `maxPerType() = 2 + 效率 / 4` —— 每种几个的上限（满级 4）。

满级一批 **13 种 × 1~4 个**（空槽时是 4 种 × 1~2 个）。合并次数 = 种类数 ≤ 13，
仍是常数级，不多出 tick 任务 —— 第 199 批那条「TPS 不许涨」的约束继续成立。

**界面**（仍是 196×188 的亮色 AE 面板，一个像素没挪）：

* 左侧竖排三格：槽框 `UP_X = 13`、`UP_Y = 22`、步进 18；产物栏第一格框在 43，
  中间空 **14 像素**（用户「隔远一点」）；
* 空槽铺一张 `textures/gui/ingot_ghost.png`（16×16）—— 由 `tools/gen_void_mining_gui.py`
  读 **原版铁锭的 alpha** 当剪影，只描一圈轮廓（`#4A5478` α235）+ 极淡填充（`#8E93A8` α70）。
  用真·原版剪影而不是手绘：玩家一眼认得出「这是锭」，又不会像真放了东西；
* 悬停空槽弹三行说明（收哪三种、各加几级、不消耗）；装了锭就不弹，让物品自己的提示说话；
* 槽下写一行「效率 +N」（数据槽 5，`EFFICIENCY_DATA`），0 级时用灰字；
* 左上角三格与标题（居中 y=6）、背包标签（y=92）、产出栏（x≥43）都不重叠。

**② 枫凝菼蒄 `maple_crystal`**

* 新物品，`Rarity.RARE`，**64 一组**（原版默认，故不写 `stacksTo`）；
* **掉落**：枫叶自己的战利品表里追加一个独立池 —— `survives_explosion` + `random_chance 0.2`。
  写在战利品表而不是事件里，是因为「打破枫叶树叶就掉」这句话对剪刀 / 精准采集也成立；
* **贴图**（`tools/gen_maple_crystal.py`，16×16）：一根尖头六棱晶柱，五档赤橙
  （墨赤边 `#471609` / 深赤 `#7E240E` / 赤 `#C24A18` / 橙 `#EE7C2B` / 亮橙金 `#FFC172`），
  左暗右亮、中脊一列最亮；**没有抖动噪声、没有高光点**（用户「不要太清晰」）；
* 模型 `item/generated`、创造栏排在玄枢耀光之后。

**③ 玄灵三材：出处 + lore**

新类 `SpiritRelicDrops`（`@EventBusSubscriber`，挂 `LivingDropsEvent`）：

| 材料 | 怪 | 概率 |
|---|---|---|
| 玄灵兔子脚 `xuanling_rabbit_foot` | 兔子 `Rabbit` | **40%** |
| 神浸魔骨 `shenjin_demon_bone` | 凋零骷髅 `WitherSkeleton` | **40%** |
| 翎月翼膜 `lingyue_wing_membrane` | 幻翼 `Phantom` | **80%** |

三个口径：**只认玩家击杀**（`getSource().getEntity() instanceof Player`，弓箭 / 法杖都算，
岩浆摔落铁傀儡一律不补）、**固定概率**（不吃抢夺）、**每次一件**。
原版那三样掉落照旧，这里是**追加**。三件各加一行 lore 写明出处与概率（中英同步）。

**④ 洞虚守一戒：注魔配方**

`recipe/xuanshu_void_eye.json`，`thaumcraft:infusion`：

* 中心：**揭示之护目镜**（`thaumcraft:goggles_of_revealing`）；
* 外围 11 件：玄枢八卦之盐 ×2、玄枢布匹 ×2、玄枢锭 ×2、下界之星 ×2、**龙蛋 ×1**、枫凝菼蒄 ×2；
* 要素：八种卦象**各 64**；**不稳 25**（本 mod 现有配方里最高的一档，炎炉 / 坩埚那几台是 8）；
* 门禁：`trigram_gate/machines.json` 加 `xuanshu_void_eye`，`mode=all` 八种卦石齐立
  （乾、兑、离、震、巽、坎、艮、坤各一块，缺一种不启动）；
* 配方没有 `research` 字段 —— 这颗戒指本来就没有研究条目，带上 `research` 反而会因为
  「玩家没完成该研究」而永不匹配。

**⑤ 自检与产物**

* `selfcheck` **ALL GREEN**：models **131**（+`maple_crystal`）/ blockstates 29 /
  lang keys **496**（+9）/ resources **516**（+4：贴图 2、模型 1、配方 1，
  外加战利品表与门禁表是改既有文件）；
* 贴图尺寸 `16×16: 102 / 16×64: 1 / 32×32: 55 / 256×256: 2`；
* `check_obj_models` 全过、`deadcode_scan` 0、`orphan_scan` 无孤儿、`pyclean_scan` 0；
* 构建 **828 874 B** · SHA256 `d7815b1636b8a91ea6dea1d5895d62f60b4376c3150c893e3ab5b5c6cf5bf7f8`
  （比第 211 批大 **6 739 B**）；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 212 行。

### 12.213 第 213 批（2026-10-29）：枫凝菼蒄挪位 + 戒指卦石 lore / 锭档位反转 / 楼台补底加层

用户六项：

1. 「MAPLE_CRYSTAL 放在翼膜后面，同时添加 lore：破坏枫树树叶 20% 概率掉落」；
2. 「戒指合成需要卦石，添加 lore，不要忘记了」；
3. 「四神兽之锭效率最低，未尽第二，玄枢效率最高」；
4. 「矿机的 lore 你新加的两行不要」；
5. 「以后添加 lore 记住卦石和星象合成需要添加，按之前的格式颜色」；
6. 「基座地下是透明的（截图），然后这个底座添加一点花纹层次感」。

**① 枫凝菼蒄：挪位 + 一行 lore**

`ModTab` 里从「七、其余玄枢材料」挪到**翎月翼膜正后面**（骨 → 翼膜 → 枫凝菼蒄 → 兔脚）——
它也是「打东西才掉的材料」，跟三材一起收在戒指后面才顺。lore 一句灰字
「破坏枫树树叶有 20% 概率掉落。」（`item.skydeitymysticpivot.maple_crystal.lore`），
格式与玄灵三材那三行一致。

**② 洞虚守一戒：补「所需卦石」行**

注魔门禁是 `mode=all`（八石齐立），所以 lore 写全八种，用 `trigramLore(ALL_TRIGRAMS)`，
淡靛蓝 `0x8FB0FF`。`ALL_TRIGRAMS` 原本声明在戒指**后面**（法杖部件那一段），静态字段按源码
顺序初始化会拿到 `null` —— 挪到类首（`NANDOU_ALL` 之后），并把那段杖件注释改写成
「各件杖上所需卦石的集合」。

**③ 锭档位反转**

`XuanshuVoidMiningBlockEntity#tierOf`：**四神兽之锭 +1 / 未尽的玄枢锭 +2 / 玄枢锭 +3**
（212 批是反的）。三格全插**玄枢锭**才是满级 9，一批 13 种 × 1~4 个；界面空槽悬停的
`gui.void_mining.upgrade_tiers` 文案中英同步改序。

**④ 矿机 lore 回到两行**

212 批补的第三行（「左边三格锭槽：四神兽之锭 +3 …」）在提示框里折成两行，用户要删。
`ModItems` 去掉 `lore3` 一档，`zh_cn` / `en_us` 两条键一并删；「锭能加效率」改由界面自己说 ——
空槽悬停三行 + 槽下一行「效率 +N」。

**⑤ lore 立规（写进 §7.15）**

「凡卦石 / 星象配方，必须带对应那一行」立成项目铁律，连颜色与顺序一起定死：
风味行（灰）→ 卦石行（`0x8FB0FF`）→ 星象行（同色）。这一批的两处正好各示范一次。

**⑥ 鎏金楼台：补底 + 加层**

用户截图圈出的是**底座四角透空**：从斜上方能看见底下的世界（那抹浅蓝）。

*根因*：211 批的八角是按 `rot=22.5` 生成的，平边朝四角、尖角朝四方 —— 3×3 正方形的四个角
被整个切掉；再加上段与段之间留了 0.8px 宽的水平挑檐，等距斜看时视线正好顺着挑檐钻进
**中空**的台心，再从对面（背面剔除后）穿出去。**补一块方底并不能根治**：挑檐那条缝依旧存在。

*解法*（`tools/gen_terrace_obj.py` 重写形制部分）：

| 项 | 之前（211） | 这一批（213） |
|---|---|---|
| 段数 | 5（圭角 / 下枋 / 束腰 / 上枋 / 台面） | **8**（地栿 / 圭角 / 下枋 / 下枭 / 束腰 / 上枭 / 上枋 / 台面） |
| 段间过渡 | 7 道水平挑檐（露明面 / 挑檐底面） | **全改 45° 斜面**（下枋收、下枭收、上枭放、台面外放成冰盘沿） |
| 盖板 | 台面顶、最底段底 | 台面顶 + 地栿底（各 8 片扇面）+ 四片角台（`corner_shelf`） |
| 半径 | 24 / 23.2 / 21.8 / 23.2 / 24 | 23.9 / 23.9 / 23.1 / 21.7 / 23.1 / 23.9 |
| 切角 | 八角 45°（`rot=22.5`，四角全切） | 八边形按「正方形四边各切 3px」生成，四角是一条连续的 45° 棱 |
| 面数 | 116 | 116（段多了，挑檐与环带没了） |

台身因此是一整层**连续的壳**：任何一条视线都只能打到某个面上，钻不进台心。
`tools/preview_obj_iso.py` 用洋红底（`bg=(255,0,255)`）逐列扫内缝做过验证：211 版在四角各有一条
几十像素的透空楔子，213 版只剩斜到与 45° 棱面完全平行时的亚像素级掠射（`x=70` 那一列 9px，
约 0.04 格），其余全无。

「层次感」落在两处：

* **几何**：8 道高程 + 2 道斜面收放 + 台面冰盘沿，侧面有一道道明暗；
* **贴图**（图集加两格 `s_dibo` / `s_nie`）：地栿最暗只留顶上一道受光线；下枋四格一组**竖枋**
  + 上下两道横线（回纹骨架）；束腰两格一组**竖楞**（壸门）+ 中腰阴线；枭是光滑亮金箍；
  台面加一圈**内圈阴线**把四角方胜圈住（原来那圈阴线压在方胜上，糊成一条粗带）。

**⑦ 自检与产物**

* `selfcheck` **ALL GREEN**：models 131 / blockstates 29 / lang keys **496**（删 `void_mining.lore3`、
  增 `maple_crystal.lore`，一进一出）/ resources **516**；
* `check_obj_models` 5 个 obj / 1860 面全过；`deadcode_scan` 0、`orphan_scan` 无孤儿、`pyclean_scan` 0；
* 构建 **828 793 B** · SHA256 `5c6693a96f6cb66cdbed85862c39d37e5652f30d7092811ae6f030fa9407c4ee`
  （比 212 批小 81 B；最后这 2 B 来自收尾时给 `tierOf` 上方补的一行注释 —— 行号表跟着挪了一位，
  字节码语义未动）；jar 内 `terrace.obj` / `sixiang_terrace.png`
  与源码逐字节一致；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 213 行。

### 12.214 第 214 批（2026-10-29）：合成表对称 / 耀光配方 / 枫晶换图 / 注灵台去同类 / 奥术台书槽

**① 注魔页为什么「不对称」，以及怎么才算对称**

反编译本体 `compat/jei/InfusionRecipeCategory#setRecipe`（`javap -c`）拿到排布公式：

```
θ = -90° + i · 360/N                       # i = 0 在正上，顺时针
x = 50 + (int)(cos θ · 30) - 8             # 半径 30，槽宽 16
y = 45 + (int)(sin θ · 30) - 8 + 2
```

于是两件事同时决定「看起来对不对称」：

* **件数必须偶数**。11 件步进 32.7°，没有任何一件落在正左 / 正右；7 件 51.4°、9 件 40° 同理。
* **JSON 的 `components` 顺序要写成镜像序列**：`c[i] == c[N-i]`。`i = 0`（正上）与
  `i = N/2`（正下）是唯二允许左右不同物的位置。

戒指这次从 **11 件减到 10 件**（去掉一颗下界之星，龙蛋挪到正上、下界之星挪到正下），
顺序 = `龙蛋 → 玄枢锭 → 玄枢布 → 八卦盐 → 枫凝菼蒄 → 下界之星 → 枫凝菼蒄 → 八卦盐 → 玄枢布 → 玄枢锭`。
10 件的坐标逐像素对过：`59↔25`、`70↔14`、`70↔14`、`59↔25`，y 成对相同 —— 精确镜像（不是肉眼差不多）。

顺手扫了全部 33 个注魔配方，**20 个重排成了镜像**（另有 3 个本来就是）（只改顺序、材料一件不多一件不少，
脚本里断言「重排前后多重集一致 + 镜像成立」）：bagua_pickaxe、四柄兽杖端 ×4、四柄兽杖柄 ×4、
鎏金杖端、玄枢杖柄 ×2、四个法核、充能器、炼狱炉、玄枢镐、复刻台。

**排不匀的 10 个**（列进 ROADMAP 待定，没动）：

| 配方 | 件数 | 为什么排不匀 |
|---|---|---|
| `sixiang_wand_rod` | 11 | 奇数件，步进 32.7° |
| `xuanshu_crucible` / `xuanshu_alchemy_furnace` / `xuanshu_hoe` | 7 | 奇数件，步进 51.4° |
| `xuanshu_purifying_stone` | 9 | 奇数件，步进 40° |
| `star_dial` / `xuanshu_arcane_table` / `xuanshu_crystal_cluster` / `xuanshu_spirit_core` | 12 | 四类各 1 件（奇数），要补成对就得加材料 |
| `xuanshu_node_modifier` | 12 | 十类各 1 件，同上 |

**② 玄枢耀光的配方**（用户「6 种要素 77 点，合成表的空位增加神秘锭」）

`recipe/xuanshu_nitor.json`：`vis` 六基元 30 → **各 77**；图样由留白的十字

```
. A .        D A D
A B A   →    A B A          A 紫水晶 / B 本体闪耀之光 / C 八卦盐 / D 神秘锭
. C .        D C D
```

四个空角补 `thaumcraft:thaumium_ingot`（`D`）。这也正好是 §7.16 说的「3×3 缺格要补」——
原来那个十字图样在 JEI 里是一边空一边满，看着就不齐。

**③ 枫凝菼蒄换贴图**（用户「作为枫凝菼蒄的贴图，改为 16×16，像素风再浓一点，颜色改为鲜红橘色」）

参考图是 139×102 的一堆紫粉碎晶。**直接缩到 16×16 不可行**：先试了 LANCZOS + 量化
（3/4/5 档都试过），出来是一团蜜色糊块，晶面全丢；再试「按晶体中心分块」的镶嵌，暗线又把
整张图吃掉。最后按「像素风要更浓 = 更少的块、更大的块」翻成**三根主晶**：

* 中高（尖 y=1）/ 左低（尖 y=5）/ 右中（尖 y=4），底端齐 y=13，三根贴在一起成一簇；
* 每根的结构固定：亮橙金尖 → 3 宽（墨边 / 橙 / 墨边）→ 5 宽（墨边 / 橙 / 橙 / 赤 / 墨边）→ 3 宽收尾；
* 五档**鲜红橘**：`#400A04` / `#8E1F07` / `#D2380A` / `#F76616` / `#FFB458`；
* **没有抖动、没有孤立像素**，每块色至少 2×2 —— 这是「像素风浓」的正解（212 批那版是单柱，
  这次多了两根、也更红）。

**④ 注灵台：删掉「同类拒收」**（用户「只要是天域之华，不需要检测是否相同，把没用的代码删干净」）

删干净的东西（一件不剩，`grep` 过）：

* `XuanshuNodeModifierMenu#bloomTypeKey`（读天华封着哪几门要素、拼类型串的整个方法）；
* `XuanshuNodeModifierBlockEntity.lastBloomType` 字段、`saveAdditional` / `loadAdditional` 的
  `LastBloomType` 键、运转里那一段「撞类型就 `return`」；
* 语言键 `message.skydeitymysticpivot.node_modifier.same_bloom`（中英）；
* 三处注释里「第一百六十二批」那套说明改成「第二百一十四批已删」的沿革。

华槽现在只判 `isWispEssence`（是不是本体天域之华），加哪一门仍由三段式 `nextAspect` 掷。
旧存档里残留的 `LastBloomType` 标签没人读，读档时按未知标签忽略。

**⑤ 奥术台：锭槽 → 可选附魔书槽**（用户「改为可以放附魔书（可选）放了就是给物品添加这个附魔书上所有，
然后附魔书消失……无论附魔能不能发挥作用直接加上。升级一次消耗 77 点法杖 6 种要素」）

* **槽位**：`SLOT_DYE` → `SLOT_BOOK`、`DYE_X/DYE_Y` → `BOOK_X/BOOK_Y`；`DYE_ITEM` / `DYE_COST`
  与「缺锭」提示整组删除。界面上那一格的说明改成「书槽：附魔书（可选）」。
* **一次运转做两件事**：
  1. 物品原有每一项附魔 **+1**（封顶 128，与旧版一致）；
  2. 书槽有附魔书 → 把书上 **所有**附魔并进来（同一条取两者较高等级），**书消耗一本**。
* **「不判能否生效」怎么实现**：附魔就是 `DataComponents.ENCHANTMENTS` 这张表。原版铁砧 /
  附魔台会先过 `Enchantment.canEnchant` 与互斥表，这里**两道都不走**，直接往表里写 ——
  于是「时运 + 精准采集」「锋利刻镐上」都成立。这是用户明确要的，不是漏判（注释里写死了）。
* **价码**：`VIS_PER_CHANNEL` 129 → **77**，六路各 77 整 Vis（送 API 时 ×100 厘），
  逐路足额、不够整笔不扣；**不再收玄枢锭**。
* **没有东西可加**：既没附魔、书槽也空 → 「无从添加」；原有附魔全封顶且书带不进新东西 →
  「诸附魔已至绝顶」。两种都**不扣魔**。
* **两枚空槽剪影**：新增 `textures/gui/book_ghost.png`（`tools/gen_arcane_table_gui.py` 读
  `disenchant_book.png` 的 alpha，轮廓 `#4A5478` α150 + 填充 α44），书槽空着时贴在槽底
  （有书就不画，免得影子透到真书下面）；同时把水晶塔那枚锭描边从 α235/α70 调到 **α150/α44**，
  两个界面同一浓度（**第 215 批两者再一起降到 α110/α30**）。
* 文案：物品 lore1、魔导书 `bagua.page.arcane_table`、界面两行小字、`no_enchant` 全部改成新口径
  （中英各一套）。

**⑥ 自检与产物**

* `selfcheck` **ALL GREEN**：models 131 / blockstates 29 / lang keys **494**（删 `same_bloom` 与
  `no_dye` 两键、`hint_dye` 改名 `hint_book`）/ resources **517**（+1 `book_ghost.png`）；
  贴图尺寸 `16×16: 103 / 16×64: 1 / 32×32: 55 / 256×256: 2`；
* `check_obj_models` 5 个 obj / 1860 面全过；`deadcode_scan` 0（含「没人用的 import」0）、
  `orphan_scan` 无孤儿、`pyclean_scan` 0；
* 构建 **829 199 B** · SHA256 `1aecacb2216dfe5399ee3c3a5fe69a1a978151d7288771d992afcaff8758201b`
  （比 213 批大 406 B：新贴图 + 配方顺序）；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 214 行。

### 12.215 第 215 批（2026-10-29）：轮廓再淡 / 小字精简 / 布匹换触媒 / 矿速补货 / 页签图标 / 充能器换料

**① 两张空槽轮廓再淡一档**（用户「矿机锭的轮廓和奥术台附魔书的轮廓再淡一点」）

* `gen_void_mining_gui.py`（锭）与 `gen_arcane_table_gui.py`（附魔书）的 `EDGE` / `FILL`
  同步降档：**描边 α150 → 110、填充 α44 → 30**。两个脚本各跑一遍重生：
  `ingot_ghost.png` 135 个不透明像素（描边 37）、`book_ghost.png` 152 个（描边 38）。
* 颜色没动（`#4A5478` / `#8E93A8`），只降 alpha —— 压在 AE 浅灰蓝底上只剩一道浅影，
  抬头才看得出「这格是插锭 / 放书的」，不会跟真放了东西混淆。

**② 奥术台界面小字只留名字**（用户「物品……只留下物品两个字，后面只留下附魔书这三个字」）

* `hint_item`：「物品槽：附魔之物」→「**物品**」；`hint_book`：「书槽：附魔书（可选）」→「**附魔书**」；
  英文 `Item` / `Enchanted Book`。两槽的用法（可选、书耗一本）改由「+」号与悬停 / 书页交代，
  小字不再抢地方。

**③ 玄枢布匹换触媒**（用户「玄枢布匹合成 盐改为八卦布匹」）

* `recipe/xuanshu_cloth.json`：`catalyst` 由 `xuanshu_bagua_salt` 改成 **`sixiang_cloth`（四象布匹）**，
  要素不变（八卦之气各 4）。玄枢八卦之盐仍用在别的配方里，这里只是把入口前移一层 ——
  布匹线成了「白羊毛 → 四象布匹 → 玄枢布匹」。
* 书页 `bagua.recipe.xuanshu_cloth` 中英同步：「坩埚：四象布匹 1，八卦之气各 4」/「1 sixiang cloth」。

**④ 水晶塔「1 秒满」**（用户「水晶塔产矿效率再快一点，放上 3 个玄枢锭要 1 秒满」）

* 问题不在单价，在**摊得太开**：旧 `produce()` 每 tick 从随机起点取 13 种**新**矿，
  12 格里每格要等很久才轮到第二次，于是永远是一层「每种十来个」，锭槽插满也等不到「满」。
* 改成**先补货、再添新**：
  1. 先按当前档位给栏里**已有的每一格**各补一份（`perStack = maxPerType()`）；
     三枚玄枢锭 = 效率 9 ⇒ 每格 +4，12 格共 **48 件/刻**，从空栏到 12 格各 64 只要
     **16 刻**（约 **0.8 秒**，含首刻铺种约 0.85 秒）；
  2. 剩下的名额才拿去铺**没出现过**的种类，且上限压到产出栏格数（`OUT_SLOTS` = 12），
     不会再选到第 13 种却无处安放。
* 新增 `hasEmptyOutSlot()`（12 次引用比较）；`produce()` 只多了十几次比较，**tick 成本不变**。
* 空栏 / 低档位的手感照旧：首刻仍铺 4 种（空槽）→ 12 种（满槽）；不插锭时每格 +2，
  约 30 刻满，插满约 16 刻 —— 差距一眼看得出来。

**⑤ 毛边页签的图标补回来**（用户「之前给功能工作方块的自动输入输出按钮……添加过图标为什么现在没了」）

* 四台带六面配的机器（炎炉 / 坩埚 / 炼金炉 / 复刻台）都有左缘那枚 14×16 页签，点开是「传输配置」。
  复刻台一直画着三条滑杆的图标，另外三台在第 190 几批重排界面时只剩**空白页签壳**。
* 把图标抽成 `XuanshuGui.tabIcon(graphics, tx, ty, color)`（三条滑杆 + 三粒错开滑钮），
  四台各调一句 —— 以后不会再有人漏画。
* 函数注释里写死了「四台必须都有」，并引了用户这次的原话，避免又被当成可删的装饰。

**⑥ 玄枢充能器换料**（用户「玄枢充能器的闪耀之光换玄枢耀光」）

* `recipe/xuanshu_charger.json`：八格里的两枚 `thaumcraft:nitor` 换成
  **`skydeitymysticpivot:xuanshu_nitor`（玄枢耀光）**；位置仍是镜像的第 4 / 6 位（10 件，对称不破）。
* 书页同步：中文「玄枢耀光 2」，英文原来写的是「2 glowstone dust」（本来就不对，闪耀之光不是萤石粉），
  一并改成「2 xuanshu nitor」。

**⑦ 自检与产物**

* `selfcheck` **ALL GREEN**：models 131 / blockstates 29 / lang keys **494**（只改值、不增删键）/
  resources **517**；贴图尺寸 `16×16: 103 / 16×64: 1 / 32×32: 55 / 256×256: 2`；
* `check_obj_models` 5 个 obj / 1860 面全过；`deadcode_scan` 0、`orphan_scan` 无孤儿、
  `pyclean_scan` 0；
* 构建 **829 712 B** · SHA256 `1e60f86b…186b23ac8`（比 214 批大 513 B：两张贴图重生成 + 配方文本），
  sha 与构建产物一致；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 215 行。

### 12.216 第 216 批（2026-10-30）：四象石砖改三层地坛 / 修角部透视 / 枫凝菼蒄四棱冰晶

用户两项：

1. 「玄枢基座的多方块地下那一层是类似地坛往上，贴图重新设计，然后我框出来的部分地下是透明的会透视，修改 bug」；
2. 「给 MAPLE_CRYSTAL 设计新的贴图，我希望要类似尖的四棱冰晶一样的像素风图」。

**① 底座：八角须弥座 → 三层方台（地坛形制）**

第 213 批那套是**八角须弥座 + 中心方台身**：3×3 的四角只在上、下补了角台，从 y=0 到 y=8
角上是一条**竖着的三角缝**；模型又是单面壳，斜着看视线顺着缝钻进中空台心、再从对面穿出去 ——
截图红圈里「角是透明的」就是这条缝。这一批整份重写 `tools/gen_terrace_obj.py`：

* **形制**（地坛本来就是方的，方形象地）：
  * 角格（四个对角格）**3px** 最低一层；
  * 边格（四边格）**8px** 第二层；
  * 中心格 **8 → 16px** 方台身，台座柱础仍落在 16px、16×16 齐边；
  * 从外往里看是一级一级**往上收**的方台，四角不再是空的，而是实打实的低台。
* **全封闭**：每层都是「四面 + 露出的顶面 + 整片底面」的封闭方盒，连台身底面都补了盖板；
  以前那种「单面壳 + 缺盖板」的地方一处不剩，任意角度都看不到底面外的天空 / 土地。
* **碰撞按层高**：`SixiangStoneBrickBlock` 重新引入 `corner` 状态（第 202 批删掉的是「朝向」，
  这次只用来记层高）；角格 `box(…, 3/16, …)`、
  边格 `box(…, 8/16, …)`、中心整格。踩在哪一层就站哪一层，不再浮空。旧存档开着的坛
  由 `lightBricks` 每秒自查时补写 `corner`，不用重新开坛。
* **絮带起点**：丝带从四个对角格牵出，角格降到 3px，`RIBBON_FROM_Y` 由 −0.48 改 **−0.79**。

**② 贴图重绘：汉白玉台面 + 暗铜台基 + 鎏金束带**

旧图集只有 8 纹素宽的侧条，横向拉到 48px ⇒ 放大 6 倍，远看是一块拉丝黄板。新版：

* 图集改为 **48px 实宽 × 16px 高的整条侧带**（`s_side`），面 UV 直接按世界坐标取
  （x 或 z 的 0..48px、y 的 0..16px），一格一格接得上，回纹、竖枋、束带都是 1:1 硬边；
* 三层各有自己的带：0–3px 暗铜台基 + 石缝，3–8px 鎏金中层 + 回纹，8–16px 台身竖楞 + 束带；
* 台面（`t_top` 48×48）改成**汉白玉芯 + 鎏金外沿 + 内圈阴线 + 四角方胜**，不再一色金；
* 底面（`b_under` 48×48）整片封死，暗铜带墨边。

**③ 枫凝菼蒄：四棱冰晶**

`tools/gen_maple_crystal.py` 整份重写：单根晶柱，**1 像素尖顶 / 尖底**，柱身笔直，
正面一道高光亮棱把左、右两片折面分开（方截面看过去就是「四棱」），外沿深墨描边；
色阶仍是赤橙六档（墨边 / 暗折缝 / 深红 / 橙 / 亮橙 / 高光），无抖动、无孤立像素。
上一版的三根圆短柱（第 214 批）退役。

**④ 自检与产物**

* 新增 `corner` 状态后 blockstate 是 8 个组合（未激活一律平砖；激活时中心出模型、其余八格空模型）。
* `terrace.obj` 由 116 面降到 **39 面**（156 v / 156 vt / 39 vn），包围盒 x/z `−0.994..1.994`、y `0..1`。
* `selfcheck` **ALL GREEN**：models 131 / blockstates 29 / lang keys 494 / resources 517；
  贴图尺寸 `16×16: 103 / 16×64: 1 / 32×32: 55 / 256×256: 2`；
* `check_obj_models` 全过（6 个 obj / 1822 面），`compileJava` 只余 `EventBusSubscriber.bus` 的过时告警；
* 构建 **824 977 B** · SHA256 `34c3a05b9c1e04e46dc13b3c443ac381f79a2e35a30a2261a5f08f96fd533a3e`，
  sha 与构建产物一致；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 216 行。

### 12.217 第 217 批（2026-10-30）：烛龙 lore 改色 / 寰宇杖回到最简 / 香炉台 129 核实 / 基座整体鎏金 + 符文

用户四项：

1. 「CHRONO 的 lore 中最后一行卦石的需求改为和其他 lore 一样颜色一样，可以看 FOCUS_TELTPORT」；
2. 「寰宇支配法杖就改为容量 99999 100% 消耗，不要消耗了直接补满，就设置最简单的」；
3. 「玄枢注灵香炉台每次消耗法杖魔力为 129 各要素一次」；
4. 「基座的贴图更细腻一点，带一点古代的符文，不要想着这样一层一层，然后有点错位，整体鎏金」。

**① 烛龙衔时：第三行与赤羽焚天瞬同一句**

`ModItems.FOCUS_CHRONO` 原先第三行是 `grayLore("…focus_chrono.lore3")`（整句灰字
「注魔须在坛脚立……八种卦石各一块」）。改成 `trigramLore(ALL_TRIGRAMS)` —— 与
`FOCUS_TELEPORT` 的 `trigramLore("gen")` 同一个构造函数（淡靛蓝 `0x8FB0FF`、不斜体、
前缀「所需卦石：」），八种卦石按 `ALL_TRIGRAMS` 顺序全列。中英 `focus_chrono.lore3`
两个语言键随之删除（不再有引用），所以 lang 键总数 494 → **493**。

**② 寰宇支配法杖：删掉「一用就补满」，只留容量与价码**

原来 `WandAutoRefill` 每 5 tick 把背包里这把杖的六元补回容量（「用掉立刻补满」）。
这一批整个类删掉，`PRESETS` 里它那行的 `WandRechargeSpec.none()` 改 **`any()`** ——
与其余六柄同一套被动充能口径。容量 **99 999**、耗损 **×1.0** 不变，除此之外这把杖
不再有任何特殊逻辑（没有配方、不进 JEI/EMI 装配页这两条仍然照旧）。

**③ 注灵香炉台：129 各要素（核实，无改动）**

`ModBlockEntities.XuanshuNodeModifierBlockEntity` 里 `VIS_PER_CHANNEL = 129`，
`VIS_COST` 对 `VisChannel.values()` 每个通道各记 `129 × 100` 厘，运转时
`consumeExactVisFromWand` 一次收齐；物品 lore 与书页写的也是 129。用户这条要求
与现状一致，本批只做核对，未改代码。

**④ 基座贴图：整体鎏金 + 古代符文，撤掉「一层一层」**

`tools/gen_terrace_obj.py` 的绘制段整份重写（几何不动，仍是 39 面的三层方台）：

* **色板**：九档金（`GOLD0` 阴刻 → `GOLD6` 亮边），相邻差 ≤ 20，所以整面读起来是同一片金；
* **侧条 `s_side`**（48×16，u/v 都取世界坐标）：撤掉「0–3 暗铜台基 / 3–8 鎏金中层 /
  8–16 台身束带」三段各画一道回纹的旧法 —— 那正是「一层一层」与层间接不上（错位）的来源。
  现在竖直方向只剩**真实台沿**（v3 角台沿、v8 边格沿，各 1px 亮边 + 下面 1px 阴刻）与
  贴地的一道影，其余全交给竖枋与符文；
* **竖枋对齐块缝**：阴刻线落在 `u % 16 == 0`（块缝）上、旁边 1px 提亮做倒角，
  三层、四面的枋线因此**在同一条竖线上**，不会再错位；
* **符文**：台身每个 16px 面正中刻一枚（十 / 王 / 日 / 山 / 爻五枚循环，6×5 阴刻）；
  台面按**八方位**（±16 轴向 + ±16 对角）镇八枚，正好一枚落在每块 16×16 待见台面
  （四角格 + 四边格）的正中，中央 16×16（被台身盖住）那一块不刻；
* **台面 `t_top`**：旧版一层套一层的同心回纹圈全撤，只剩最外 1px 亮边 + 里侧 1px 阴刻，
  中间是一整片细腻金面 + 八枚符文；
* **台身顶 `t_riser`**：亮边 + 阴刻 + 正中一枚符文；
* **`b_under`**：暗金 + 墨边，仍整片封死；
* 「细腻」靠 `grain(lu, lv)` 的确定性抖动（12% 暗一档、6% 亮一档，与主色只差几个色阶），
  远看是平的，近看才见极细的颗粒 —— 不是上一版那种一眼能看出色块的花。

**⑤ 自检与产物**

* `selfcheck` **ALL GREEN**：models 131 / blockstates 29 / lang keys **493** / resources 517；
* `check_obj_models` 全过（6 个 obj / 1822 面）；`terrace.obj` 仍 39 面（156 v / 156 vt / 39 vn）；
* `gradlew build --offline` 成功，只余 `EventBusSubscriber.bus` 那条原有告警；
* 构建 **823 907 B** · SHA256 `10ca49b29b19c551448bb2dc61155e93372e709dbdeaf08d288c34263fc35924`，
  sha 与构建产物一致；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 217 行。

### 12.218 第 218 批（2026-10-30）：基座回到八段楼台 / 贴图横向明暗分段

用户原话：「基座的模型上一版，但是贴图不好看 不要一大块纯色，有层次感」。
随后二选一里用户选了 **「回到第 215 批的八段楼台」+「横向明暗分段」** ——
216 的三层地坛方台（块面太大）与 217 的平金贴图（远看是一块黄板）一并推翻。

**① 几何：八段楼台，每段一圈四面梯形**

`tools/gen_terrace_obj.py` 的 `PLATFORM` / `BODY` 两张分段表就是全部形制：

```
楼台（3×3 满铺，0 → 8px）      台身（中心 16×16，8 → 16px）
  地栿 0–2   r 23.9 → 23.9      束腰   8–10  r 8.0 → 7.2（收）
  圭角 2–3   r 23.9 → 23.0（收） 上枭  10–12  r 7.2 → 8.0（挑）
  下枋 3–6   r 23.0 → 23.0      上枋  12–14  r 8.0 → 8.0
  下枭 6–8   r 23.0 → 23.9（挑） 冰盘沿 14–16 r 8.0 → 8.5（外放）
```

`ring(o, y0, y1, r0, r1)` 每次画四面梯形；**段与段在交界处半径相同**，那条棱就是两段的
共用棱 —— 整座是连续闭合的壳，既没有水平挑檐、也没有可钻进去的缝（216 修掉的「角透视」
不会回来）。盖板三张：y0 封底（`b_under`，frame 24）、y8 台面（`t_top`，frame 24）、
y16 台身顶（`t_riser`，frame 8.5）。共 **35 面**（140 v / 140 vt / 35 vn，绕序自纠 2 面，
都是内收段）。删掉旧 `box` / `top_quad`，换成一个 `cap(...)`。

**② 贴图：跟段走的横向明暗**

`s_side`（48×16，u = 世界坐标、v = 世界高度）按高度行着色：

| v | 段 | 处理 |
|---|---|---|
| 0 | 落地 | `GOLD0` 一道影 |
| 1–2 | 地栿 / 圭角 | 压暗 `GOLD2`，圭角那一行走亮棱 `GOLD5` |
| 3–5 | 下枋 | 中间调 `GOLD3` + 竖枋（`lu%16==0` 阴刻、旁边 1px 加 30 色阶提亮） |
| 6–7 | 下枭 | `GOLD5` / `GOLD4` 挑檐 |
| 8 | 台面下 | `GOLD0` 阴刻 |
| 9–10 | 束腰 | 压到 `GOLD0` / `GOLD1`（整段收进去，作阴影） |
| 11 | 上枭 | `GOLD6` 最亮一道棱 |
| 12–13 | 上枋 | `GOLD3` + 竖枋 |
| 14–15 | 冰盘沿 | `GOLD0` 阴刻 + `GOLD6` 亮边 |

**符文**：楼台每一面（每 16px 一格）正中一枚古代符文，v1–5 之间、五枚（十 / 王 / 日 /
山 / 爻）循环；台身只有 8px 高，不放。

**台面 `t_top`**：不再是整片金 —— 底色按 16px 一格**交替深浅**（`(170,133,66)` /
`(146,112,52)`，九块石板），加三道台沿线（外侧亮边 `GOLD6` / 阴刻 `GOLD0` / 再收一档
`GOLD4`）、台身脚下一圈投影（9.3–10.1）与外侧亮唇（10.1–10.9），八方位符文照旧。
`t_riser` / `b_under` 未动。

**③ Java 回退（跟 216 的 corner 一起撤）**

* `ModBlocks.SixiangStoneBrickBlock`：删 `CORNER` 属性、`CORNER_HEIGHT`、`CORNER_STEP`；
  `getShape` 回到「激活且非中心 → `DECK`（8px）」，`createBlockStateDefinition` 只剩
  `ACTIVE` / `CENTER`；
* `XuanshuPedestalBlockEntity.lightBricks`：不再写 `corner`；
* `XuanshuPedestalRenderer.RIBBON_FROM_Y`：`-0.79` → **`-0.48`**（对角格台面回到 8px）；
* `blockstates/sixiang_stone_brick.json`：由脚本重生成，8 变体 → **4 变体**。

**④ 自检与产物**

* `selfcheck` **ALL GREEN**：models 131 / blockstates 29 / lang keys **493** / resources 517；
* `check_obj_models` 全过（5 个 obj / 1779 面）；`terrace.obj` **35 面**；
* `deadcode_scan` 0 处；`orphan_scan` 贴图 / 资源 / blockstate / 注册全空；
* `gradlew build --offline` 成功，只余 `EventBusSubscriber.bus` 那条原有告警；
* 构建 **825 030 B** · SHA256 `4ef9c9aea68d2fbaa40562721f13a1afa51c2494a8fc7afb9d2f643ddabc94b8`，
  sha 与构建产物一致；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 218 行。

### 12.219 第 219 批（2026-10-30）：台阶化剖面 / 台面环形小字 / 侧面直棂窗

用户原话：「基座盘面上的字改的小一点多一点以圆形围绕，整体像楼梯一级级升高，
底部还是会有点透视方块，你改为和底部完全接触，鎏金色多一点，然后边上四周就不用字了
贴图像古代的楼阁的镂空来一点」。

**① 几何：一张 `PROFILE` 画出「一级一级」**

`PLATFORM` / `BODY` 两张 (y0, y1, r0, r1) 四元组表换成一张三元的剖面表，语义变成
「这一段是一圈**竖直墙**，半径就是 r」：

```
楼台（3×3 满铺，0 → 8px）            台身（中心 16×16，8 → 16px）
  地栿 0–2  r 23.99（贴地）             柱身   8–10  r 8.00
  圭角 2–3  r 22.40（收 1.59）          束腰  10–12  r 6.60（收 1.40）
  下枋 3–6  r 22.40（竖直，贴花窗）      上枋  12–14  r 8.00（挑 1.40）
  下枭 6–8  r 23.99（挑 1.59）          冰盘沿 14–16  r 8.80（再挑 0.80）
```

`build_terrace()` 逐段 `ring(o, r, y0, y1)`，**只要下一段半径与这一段不同，就在交界高度补一圈
`step(o, y, r0, r1)`** —— 变窄的朝上（踏面，贴 `t_top`）、变宽的朝下（檐底，贴 `b_under`）。
于是轮廓是一级一级的台阶，而不是原来的 45° 斜裙；所有水平面都补满，任意角度都钻不进壳里。
y=8 那一圈收进**故意不补**（由台面盖板自己代劳，再补一张会与它共面 z-fighting）。

面数：8 圈竖直墙 **32** + 5 圈水平面（y=2 / 6 / 10 / 12 / 14）**20** + 3 张盖板 = **55 面**
（220 v / 220 vt / 55 vn；绕序按 `expect` 法线自纠 2 面）。

**② 和底部完全接触**

* 3×3 外沿 `HALF` 23.9 → **23.99**（只留 0.01，免得与邻块共面打架）；
* 封底盖板 `b_under` 重画成**整片实心、不做格线**（旧版按 16px 画十字格，远看像一格格的
  「透明方块」），只有最外 0.7px 压一道 `INK`；
* 侧条 v0（贴地那一行）不再压黑，改 `tone(GOLD2)` —— 不能读成悬空。

**③ 鎏金再提一档**

`INK / GOLD0..GOLD6` 整条色板上移：`GOLD0 (92,69,32)`、`GOLD1 (124,94,44)`、`GOLD2 (156,120,56)`、
`GOLD3 (182,143,68)`、`GOLD4 (204,164,88)`、`GOLD5 (224,184,108)`、`GOLD6 (246,212,142)`；
`gold_field` 的抖动档同步换成 (172,135,62) / (194,156,80)。

**④ 台面：字更小、更多、绕一圈**

`RUNES` 从 6×5 收到 **3×3**（十 / 王 / 日 / 山 / 爻五枚），`TOP_RUNE_COUNT` = **16**、
`TOP_RUNE_RING` = **15.5px**。`top_glyph(lu, lv)` 逐个算出 16 个环上圆心的像素位置，
把每个中心格附近的像素投到 3×3 盒里（`floor(px - cx + 1.5)`，盒宽 3 与 3×3 对齐），
命中刻痕就返回 True → `t_top` 把它染成 `GOLD0`。环带夹在**内环线 11.0–11.9** 与
**外环线 19.0–19.9** 之间，不压台沿、不进台身脚下。`t_riser` 正中那枚同步收到 3×3
（`col = lu - 7` / `row = lv - 7`）。

**⑤ 侧面：不刻字，改古代楼阁的直棂窗**

删掉 `wall_rune`；`s_side` 的竖直段（下枋 3–5、上枋 12–13）换 `window(lu, lv)`：

* `lu % 16` 为 **3 / 12** → `GOLD5` 窗框；
* 4..11 为窗内：`inner = m - 4`，`inner ∈ {2, 5}` → `GOLD4` 竖棂，其余 `tone(GOLD2)` 窗洞；
* 其余列 `slat(GOLD3)`（每 16px 一道阴刻 + 倒角提亮）。

**下枋只有 3px 高，不加中横棂** —— 加了会把 2px 窗洞切成豆腐块、远看读成砖缝；
现在满高 2px 窗洞 + 两道竖棂，读成「一格一格的直棂漏窗」。台面沿口 / 踏面 / 檐底照旧按
`t_top` / `b_under` 的段色走。

**⑥ 自检与产物**

* `selfcheck` **ALL GREEN**：models 131 / blockstates 29 / lang keys 493 / resources 517；
* `check_obj_models` 全过（5 个 obj / **1799** 面）；`terrace.obj` **55 面**；
* `deadcode_scan` 0 处；`orphan_scan` 贴图 / 资源 / blockstate / 注册全空；
* Java 侧**零改动**（`ModBlocks` 的 `DECK`、`lightBricks`、`RIBBON_FROM_Y = -0.48` 都不动）；
* `gradlew build --offline` 成功，只余 `EventBusSubscriber.bus` 那条原有告警；
* 构建 **827 618 B** · SHA256 `39b47785d0359a315a8eee3a47aaa0c5bfbcb37bf360960e429b768681218de6`，
  sha 与构建产物一致；
* 实机验收点见 `.none/ROADMAP-tc.md` §2 第 219 行。

## 10. 当前状态

| 项 | 值 |
|---|---|
| 最新批次 | 第 219 批（2026-10-30） |
| 本体 | 4.2.3.5-1.21.1-port.0.1.0 **20753**（第二百零八批：用户把 `.none` 快照换成 `1.21.1-neoforge-20753-dev-revealing-abilities-snapshot`，旧的 20751 目录已删；api jar 在 `<快照>/tc/`、运行时 jar 在**快照根目录**；20751 → 20753 只删了一处公开 API —— 见 12.208） |
| 产物 | `SkydeityMysticPivot-0.1.0.jar` **827 618 B** · SHA256 `39b47785…218de6`（第 219 批重建） |
| 自检 | ALL GREEN（models 131 / blockstates 29 / lang keys 493 / resources 517；OBJ 链路 5 个 obj / 1799 面全过；死代码 0 / 孤儿 0 / 脚本残留 0；传送门四朝向摆放断言通过） |
| 待实机回归 | 见 `.none/ROADMAP-tc.md` §2 的待验表 |

后续目标与验收清单不放这里，统一放 `.none/ROADMAP-tc.md`；
面向玩家的功能清单放 `.none/开发进度总结.md`。三份文件分工，互不重复。
