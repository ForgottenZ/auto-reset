# agent.md — 给 Codex 的实现提示词（Minecraft 1.20.1 Forge 模组：从本地压缩包还原世界）

你是一个资深 Minecraft Forge（1.20.1）模组工程师。请在一个全新的代码仓库中生成**可编译、可运行**的 Forge 模组工程（Gradle + Forge MDK 方式），实现“从本地指定路径的压缩包解压并还原一个完整世界（方块 + 实体）”，并通过**新增维度**来承载被还原的世界。

> 目标：玩家进入你新增的维度后，看到的地形/建筑/方块状态应来自压缩包中的存档数据；实体（如村民、怪物、掉落物、命名生物等）也应随区块加载而出现（从 entities 数据恢复）。

---

## 1. 版本与硬性约束

- Minecraft：**1.20.1**
- Forge：**47.x（与 1.20.1 匹配）**
- Java：**17**
- 必须支持：单人（集成服务器）与专用服务器（dedicated server）
- 必须是**纯 Forge 模组**（不要 Fabric）
- 不依赖外部原生程序；解压用 Java 标准库（`java.util.zip`），除非确有必要再引入库（尽量不引）
- 文件解压必须防止 **Zip Slip**（路径穿越）漏洞
- 任何磁盘 IO 不要长时间阻塞主线程：大型解压需要放到后台线程，并在必要步骤回到服务器线程做状态切换/提示

---

## 2. 核心玩法与用户体验

### 2.1 模组做什么
- 读取一个**本地指定位置**的压缩包（zip），将其中的世界数据解压到当前存档的某个维度目录，使该维度加载时直接从这些 region/entity 数据读取，从而“还原世界”。

### 2.2 用新增维度承载还原世界
- 模组新增维度：例如 `worldrestorer:restored_world`
- 维度必须是“可进入”的：提供命令把玩家传送进去，或提供传送方块/传送物品（二选一；优先命令）
- 维度的“生成器”建议设为**Void/Flat-Air**（空白世界），以避免在还原区块之外生成随机地形：  
  - 逻辑：**已存在的区块数据**从 region 读取；不存在的区块生成为空（防止串味）

### 2.3 实体恢复
- 1.20.1 中实体数据通常在维度目录下的 `entities/`（region-like）中。要求：  
  - 当玩家进入维度并加载区块时，原存档内的实体应出现（由原版区块实体存储机制加载）
- 你需要确保解压后的目录结构正确，并落到该维度的实际保存路径下。

---

## 3. 压缩包输入格式（你要支持并在 README 写清楚）

压缩包（zip）内容**推荐**是一个“维度数据根目录”，即包含如下结构（至少 region + entities）：

- `region/`（必须，里面是 `.mca`）
- `entities/`（强烈建议，里面是 `.mca`）
- `poi/`（可选但建议）
- `data/`（可选，如地图、结构数据等）

你需要同时兼容两种常见情况：
1) zip 根目录直接就是上述目录  
2) zip 根目录只有一个文件夹（例如 `RestoredDimension/region/...`），上述目录在这个单一顶层文件夹内  
→ 你要能自动识别并定位“维度根目录”。

---

## 4. 配置与命令设计（必须实现）

### 4.1 Forge Config（`common`）
提供可编辑配置（`config/worldrestorer-common.toml`），至少包含：

- `archivePath`：压缩包路径（绝对路径或相对路径）
  - 默认值给一个示例，如：`./worldrestorer/restore.zip`
- `dimensionId`：维度 ResourceLocation（默认 `worldrestorer:restored_world`）
- `extractMode`：
  - `REPLACE`（默认）：删除目标维度目录后再解压（更一致）
  - `MERGE`：仅覆盖同名文件
- `autoExtractOnServerStart`：是否在服务器启动时自动解压（默认 true）
- `requireRestartForReextract`：若维度已被加载/区块文件可能打开，是否强制“仅在下次启动解压”（默认 true，保证安全）

### 4.2 命令（Brigadier）
注册命令根：`/worldrestore`

至少实现：

1) `/worldrestore status`  
   - 显示：当前配置的 archivePath、维度 id、目标维度目录、是否检测到 zip、上次解压时间/结果（你自行记录）

2) `/worldrestore extract`  
   - 触发解压
   - 仅允许 OP（权限等级 2 或 3）
   - 若 `requireRestartForReextract=true` 且服务器已创建/加载过该维度：提示“已标记，下次启动自动解压”，并写入一个标记文件（例如 `worldrestorer.pending`）或保存到 config/state

3) `/worldrestore tp [player]`  
   - 将执行者或指定玩家传送到还原维度的安全位置（例如 `(0, 80, 0)`），并在需要时创建一个基岩平台/安全点（避免虚空掉落）
   - 若维度未完成解压或目录缺失，给出明确错误提示

可选加分：
- `/worldrestore back`：返回原维度（记录玩家上次位置）

---

## 5. 维度注册方式（必须可用）

采用 data pack JSON 方式（放在 `src/main/resources/data/<modid>/dimension/` 和 `dimension_type/`），让维度在游戏中存在。

建议方案：
- `dimension_type`：沿用 Overworld 类似设置（或自定义），但请确保**可正常保存/读取区块**
- `dimension`：使用 `minecraft:flat` 生成器 + 全空气层（void-ish），并允许结构为 false

关键点：
- 维度保存路径必须落在存档目录：`<world>/dimensions/<modid>/<dimension_path>/`
- 你的解压目标必须与该维度的真实路径一致（才能被原版 RegionFileStorage 读取）

---

## 6. 解压实现细节（必须符合安全与健壮性）

### 6.1 Zip Slip 防护
对 zip 内每个 entry：
- 计算目标路径 `target.resolve(entryName).normalize()`
- 必须保证 `normalized.startsWith(target.normalize())`，否则拒绝并报错

### 6.2 目录选择与“维度根识别”
你需要从 zip 中识别出“维度根”：
- 若 zip 根下直接有 `region/` 目录：根即维度根
- 若 zip 根只有一个顶层目录且该目录下有 `region/`：顶层目录即维度根
- 其他情况：报错并提示用户压缩包结构要求

### 6.3 REPLACE 模式
- 如果目标维度目录存在：
  - 安全删除（递归删除）或先移动到备份目录：  
    - 例如：`<world>/worldrestorer_backups/<timestamp>/...`
- 然后解压

### 6.4 线程与生命周期（关键）
为了避免“region 文件句柄已打开导致覆盖无效/损坏”，遵循：
- **优先在 ServerAboutToStartEvent（或等价的更早阶段）解压**，确保维度真正被创建前文件已就位
- 如果用户用命令触发解压且服务器已在运行：
  - 默认：写入 pending 标记，提示需要重启
  - 若 `requireRestartForReextract=false`，可以尝试执行但要给出风险提示（并尽量在执行前阻止维度被使用，例如禁止 tp 或提示踢出维度内玩家）

---

## 7. 验收标准（你生成的项目必须满足）

1) `./gradlew build` 成功产出 jar
2) 把 jar 放进 1.20.1 Forge 的 mods 后：
   - 启动后存在维度 `worldrestorer:restored_world`
   - 配置 `archivePath` 指向一个包含 region/entities 的 zip
   - 启动服务器时（autoExtract=true）自动解压到目标维度目录
3) 在游戏里执行：
   - `/worldrestore status` 显示 zip 检测与解压结果
   - `/worldrestore tp` 进入维度后能看到还原的方块结构
   - 已还原区块内的实体会随加载出现（来自 entities 数据）
4) 安全性：
   - zip slip 被拦截（写单元测试或至少在日志中可验证）
   - 非 OP 不能执行 extract/tp（tp 可选允许所有人，但 extract 必须限制）

---

## 8. 你需要生成的仓库内容（必须齐全）

- 完整 Forge MDK 工程（含 `gradlew`、`build.gradle`、`settings.gradle` 等）
- 模组主类、事件注册、命令注册、config 定义与加载
- 维度 JSON（`dimension` 与 `dimension_type`）
- 解压工具类（含 zip slip 防护、维度根识别、备份/替换逻辑）
- `README.md`：
  - 如何配置 zip 路径
  - zip 应该长什么样（示例树）
  - 如何启动与使用命令
  - 常见问题（实体不出现/维度为空/需要重启等）
- 日志要清晰：解压开始/结束、耗时、文件数量、错误原因

---

## 9. 实现建议（可参考但不强制）

- modid：`worldrestorer`
- 维度名：`restored_world`
- 目标目录定位（示意）：
  - 通过服务器的世界根路径拼出：`worldRoot/dimensions/worldrestorer/restored_world/`
- 传送安全点：
  - 传送前确保 `(0, 80, 0)` 附近有落脚方块；若是空气则放一块基岩 + 给玩家缓降（可选）

---

## 10. 输出要求（你作为 Codex 的输出）

- 直接输出**整个仓库的文件**（按路径分段展示），确保我复制后即可编译
- 代码必须有必要注释，关键点（生命周期、路径、解压安全）要解释清楚
- 不要留 TODO 才能跑的关键逻辑；必须可运行

开始吧：生成这个 Forge 1.20.1 模组工程并满足以上验收。
