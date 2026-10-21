# Thaumcraft Skydeity Mystic Pivot
## 神秘时代：天神玄枢

> 立项文档 · 2026-10-08

---

## 1. 项目标识

| 项目 | 取值 |
|---|---|
| 英文名 | Thaumcraft Skydeity Mystic Pivot |
| 中文名 | 神秘时代：天神玄枢 |
| modId | `skydeitymysticpivot` |
| Java 包根 | `skydeity.mysticpivot` |
| 资源命名空间 | `skydeitymysticpivot` |
| 当前版本 | `0.1.0` |
| 构建产物 | `SkydeityMysticPivot-0.1.0.jar` |
| 作者 / 许可 | skydeity / MIT |

| 项目 | 版本 |
|---|---|
| JDK | 21（java-21.0.3-delta） |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.248（`[21.1.248,)`） |
| 本体 | `4.2.3.5-1.21.1-port.0.1.0-20746`（api jar 与运行时 jar 同版本） |
| Gradle | 8.9（wrapper 自带） |
| moddev 插件 | `net.neoforged.moddev` 1.0.17 |

```
SkydeityThaumcraft_1.21/            ← 项目根（Gradle 工程根）
├── build.gradle                    NeoForge/moddev 构建脚本
├── settings.gradle
├── gradle.properties               ★ 版本与依赖路径总开关
├── gradlew.bat / gradle/wrapper/   Gradle 8.9 wrapper
├── .gitignore
├── README.md                       本文档
├── .none/1.21.1-neoforge-20746-dev/ ← 本体参考料（只读，不进 git）
│   └── tc/*-api.jar                 编译依赖（唯一）
│       ├── *-sources.jar            只读参考
│       └── *-runtime.jar            仅编译 mixin 用（compileOnly，不打包）
├── TC4R-1.21.1-NeoForge-API.md   逐类型 API 符号参考
├── Avaritia-MC1.7.10-1.13.jar    Avaritia 参考料（只读，不依赖）
└── src/main/
    ├── java/skydeity/mysticpivot/
    │   ├── MysticPivot.java        主类（MODID / LOGGER / 注册装配）
    │   ├── ModItems.java           物品注册
    │   ├── ModTab.java             创造模式页签
    │   ├── AkashicRecordsItem.java 阿卡西记录（全要素 +999）
    │   ├── ModWandParts.java       杖芯 / 杖帽的 API 注册与满魔力法杖构造
    │   └── WandAutoRefill.java     背包内自动补满魔力
    └── resources/
        ├── META-INF/neoforge.mods.toml
        ├── pack.mcmeta
        ├── assets/skydeitymysticpivot/
        │   ├── lang/{en_us,zh_cn}.json
        │   ├── models/item/*.json                 6 件物品的模型
        │   ├── textures/item/*.png                物品图标（中子素/水晶矩阵图来自 Avaritia）
        │   ├── textures/models/*.png              ★ 法杖渲染用的杖芯/杖帽贴图（32×32）
        │   ├── textures/aspects/*.png             八卦要素图标（32×32，与本体同规格）
        │   └── textures/misc/bagua.png            魔导书分类/根条目图标（16×16 像素画 2× 放大）
        └── data/skydeitymysticpivot/
            ├── recipe/*.json                      4 个奥术（含 2 件鎏金法杖部件）+ 2 个注魔配方
            └── thaumcraft/
                ├── aspects/*.json                 8 个八卦要素（数据包注册表）
                └── research/bagua.json            BAGUA 分类 + 9 条研究（4 页要素页）
```

