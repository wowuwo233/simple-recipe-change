# Simple Recipe Change — Minecraft 1.20.1 Forge 模组

在游戏内可视化编辑配方的 Forge 模组：按 **G 键**打开全中文编辑器，
可以**添加配方**、**修改已有配方**、**删除配方**，并把结果写成 **KubeJS 可读的脚本**。

当前版本 **1.11.0**。不添加任何物品或方块，只做配方编辑。

---

## 1. 功能一览

| 功能 | 说明 |
|---|---|
| 快捷键 | **G** 打开配方编辑器（可在「选项 → 按键控制」改） |
| 三个独立模式 | **添加配方 / 修改配方 / 删除配方**，各自一个按钮，不合并 |
| 配方类型（11 种） | **原版 7 种**：工作台合成（3×3）、物品栏合成（2×2）、**熔炉**、**高炉**、**烟熏炉**、**营火**、**锻造台**<br>**农夫乐事 2 种**：**烹饪锅**、**切菜板**<br>**机械动力 2 类**：**处理配方**（14 台机器，见 [§4.7](#47-机械动力处理配方)）、**序列组装**（4 步，见 [§4.10](#410-机械动力--序列组装)） |
| 类型分组 | **原版 / 农夫乐事 / 机械动力分开选**：「分组」按钮在可用分组间切换，「类型」按钮只在当前组内循环，不用一路按过去 |
| 机械动力副产物 | 副产物栏位与概率框**按机器显隐**——统计过 Create 自带的 1766 个官方配方，只有粉碎(179/192)、研磨(159/222)、洗涤(9/38)、缠魂(1/21) 有多产物，给其余机器摆副产物格纯属误导 |
| 备注 | 「备注」输入框的内容写成脚本里的 **`//` 行注释**放在配方上方；多行备注每行各加一个 `//` |
| 界面随类型变 | 合成类添加模式像合成台（材料 → 产物），修改/删除模式**产物在前**；烧炼是「原料 → 产物」；锻造是「模板 / 基础物品 / 升级物品 → 产物」；烹饪锅是「材料 ×6 + 容器 → 产物」；切菜板是「材料 + 工具 → 产物」 |
| 切菜板多产物 | 产物格下方有 **4 个额外产物格**，每格右边一个**概率输入框**（0–1，留空为必定产出） |
| 物品用标签 | **所有类型通用**的「标签：是/否」开关。勾上后输入槽的物品会写成 `#标签`——放石斧就生成 `#minecraft:axes`，钻石斧同样能满足这条配方 |
| 栏位标注 | **每个槽位下面都写清了是什么**（合成格 / 原料 / 模板 / 基础物品 / 升级物品 / 产物） |
| 烧炼可配置 | 经验（默认 0）与烧制时间（tick，默认 200）都有输入框 |
| 原版配方反查 | 放入产物即自动查出对应分类的原版配方并**回填进输入槽**；查不到就不显示配方内容 |
| 搜索框 | 界面内嵌搜索框，输入即出结果浮层，点一下放进目标格 |
| 搜索语法 | 中文名 / 英文名 / 物品 ID / **拼音全拼** / **拼音首字母** / **`@模组名`**（与 JEI 同款） |
| 物品来源 | 在「全部物品」（注册表，即 JEI 展示的那一套）与「我的背包」之间切换 |
| 我的配方 | 查看自己写过的配方，可载回编辑器改，也可直接删除 |
| **重载按钮** | 触发资源重载（等同 `/reload`）。需要 **OP 等级 2**（单人游戏直接放行）；没权限时会在聊天栏收到明确提示，不再点了没反应 |
| **产物 NBT** | 产物格里的物品带 NBT（附魔、药水、自定义名称……）时，脚本写成 `'1x minecraft:potion {Potion:"minecraft:healing"}'`，标签不再被洗掉 |
| **产物NBT 开关** | 底栏「产物NBT：是 / 否」：产物带 NBT 时决定写不写进脚本；产物没有 NBT 时显示「无」并置灰 |
| 保存反馈 | 界面显示状态，**聊天栏输出写入文件的完整绝对路径** |
| 界面语言 | 全中文（硬编码，无翻译层；`en_us.json` 只放两个按键名，填的是英文） |
| 其它 | 不会被 E（物品栏）键关掉；Esc 先关搜索浮层再关界面 |

> **权限说明**：写文件类操作（保存配方 / 删除配方 / 重载）在**专用服务器**上要求 OP 等级 2
> （`ModNetwork.canWrite()`），单人游戏直接放行；被拒时会收到聊天提示。

---

## 2. 环境版本

| 组件 | 版本 | 位置 |
|---|---|---|
| Minecraft | 1.20.1 | `.gradle-home\caches\forge_gradle\` |
| Forge | 47.4.26（1.20.1 推荐版） | 同上 |
| Java (JDK) | **17**（Oracle JDK 17.0.12） | `C:\Program Files\Java\jdk-17` |
| Gradle | 8.8 | `D:\DSH work\.gradle-home` |
| 模组版本 | **1.11.0** | `gradle.properties` → `mod_version` |

> MC 1.20.1 / Forge 47.x 的编译与运行目标都是 **Java 17**，用 JDK 21 会报 class file version 错误。

---

## 3. 常用命令

```powershell
.\dev.ps1 build            # 编译，产物 build\libs\simple_recipe_change-1.11.0.jar
.\dev.ps1 runClient        # 启动开发版客户端
.\dev.ps1 runServer        # 启动开发版服务端
.\dev.ps1 runData          # 数据生成
.\dev.ps1 clean
.\dev.ps1 genIntellijRuns  # 重新生成 IntelliJ 运行配置
.\dev.ps1 genEclipseRuns
```

CMD 用户用 `dev.cmd build`。PowerShell 若报「禁止运行脚本」，直接用 `dev.cmd`。

---

## 4. 配方编辑器怎么用

按 **G** 打开。第一行共 **5 个**按钮：**添加配方 / 修改配方 / 删除配方 / 我的配方 / 重载**。

### 4.1 添加配方

界面像普通合成台：左边 3×3 合成格，右边产物格。

1. 拖物品进合成格与产物格（**产物格里的数量决定产出数量**，放 4 个就是 `4x`）
   - 也可直接**点输入槽**：左键放光标上的物品，右键清空该格
2. 需要无序点「无序：是」；关闭镜像点「镜像：否」
3. 合成格尺寸按「类型」按钮切：工作台（3×3）/ 物品栏（2×2）
   - 容器实际始终是 3×3；切到 2×2 时多出的行列会被**禁用并画成红色**
4. 填「配方名」（可留空自动生成）与「文件名」
5. 产物带 NBT 时，底栏「**产物NBT**」可以选是否把它写进脚本（默认「是」）；
   产物没有 NBT 时这个按钮显示「无」并置灰
6. 点「保存并写入」
7. 改错了点底部「**清空编辑区**」——会把产物格里的东西**还回背包**并清空输入槽

### 4.2 修改配方

**布局和合成台相反：产物格在最前面（左边），合成格在后面。**

1. 把要改的产物放进**产物格**（可以从背包拖，也可以用上面的搜索框找）
2. 模组自动查出它的原版配方：
   - 材料**回填进合成格**——**配方只在这里显示一次**，不再另画一份预览（那是重复的）
   - 配方右上角只留一行类型说明，如「原版配方：有序合成 · 工作台合成（3×3）· 共 2 种材料」
   - **查不到配方就只提示「未找到该物品的原版配方」，不显示任何配方内容**
   - 一个产物对应**多个**原版配方时，按配方 ID 排序**只回填第一个**
   - 烧炼类反查时，会连**经验与烧制时间**一起回填到对应输入框
3. 改好后填配方名 / 文件名，保存

生成的是**先删原配方、再写新版本**两条语句，放在同一个托管块里。

### 4.3 删除配方

同样**产物在前**。放入产物后会显示原版配方的所需材料，确认要删的是这个配方再保存。
注意删除模式下**所有输入槽都是禁用态**（画红色遮罩），只用于展示材料、不可编辑——
删除配方本来就不需要改材料。

「依据」按钮可切换两种删法：

- **按产物物品** —— 删除所有产出该物品的配方
- **按配方 ID** —— 精确删除某一个；放入产物时会把查到的配方 ID 自动填进「配方名」

### 4.4 烧炼配方（熔炉 / 高炉 / 烟熏炉）

「类型」按钮切到熔炉、高炉或烟熏炉后，界面变成一行：**原料 → 产物**，并多出两个输入框：

- **获得经验** —— 默认 0，写成 `.xp(0.35)`
- **烧制时间** —— 单位 tick，默认值随类型走：熔炉/高炉/烟熏炉 **200**、营火 **600**；不等于默认值时才写出 `.cookingTime(...)`

生成：

```js
event.smelting('minecraft:glass', 'minecraft:sand').xp(0.35).cookingTime(100)
event.blasting(...)         // 高炉
event.smoking(...)          // 烟熏炉
event.campfireCooking(...)  // 营火（默认 600 tick）
```

### 4.5 锻造配方

界面是 **模板 / 基础物品 / 升级物品 → 产物** 三个输入槽，每个槽下面都标了名字。

> **三个栏位都必填**。原版 `SmithingTransformRecipe` 用 `getNonNull` 读取 `template`、`base`、
> `addition`，缺任何一个这条配方都不会生效，所以编辑器也会在保存时报错提示。

生成：

```js
event.smithing(
  'minecraft:netherite_sword',
  'minecraft:netherite_upgrade_smithing_template',
  'minecraft:diamond_sword',
  'minecraft:netherite_ingot'
).id('...')
```

### 4.6 农夫乐事的两种配方

需要额外安装 **KubeJSDelight**（`kubejsdelight`）——农夫乐事的配方类型是通过它的 KubeJS schema 暴露的。

> ⚠️ **调用路径不是 `event.cooking(...)`**。原版那九种在 KubeJS 里是写死在
> `RecipesEventJS` 上的字段，所以能写 `event.smelting(...)`；而 mod 提供的类型挂在
> **命名空间对象**下，必须写成 `event.recipes.farmersdelight.cooking(...)`。
> 写成 `event.cooking(...)` 在游戏里只会静默报 undefined。

**烹饪锅** —— 材料最多 6 种，容器可选：

```js
event.recipes.farmersdelight.cooking(
  [
    'minecraft:beef',
    '#forge:vegetables',
  ],
  'farmersdelight:beef_stew',
  1,                 // 经验
  200,               // 烧制时间 tick
  'minecraft:bowl'   // 容器（留空则整项不写）
).id('...')
```

**切菜板** —— 产物是数组，可以多个；概率小于 1 的用 `ChanceResult`：

```js
event.recipes.farmersdelight.cutting(
  'minecraft:cobblestone',
  '#minecraft:pickaxes',                       // 工具（留空默认 #forge:tools/knives）
  [
    'minecraft:stone',                          // 主产物，必定产出
    ChanceResult.of('minecraft:flint', 0.75),   // 额外产物，75%
    '2x minecraft:gravel'                       // 概率 1 就写普通物品
  ]
).id('...')
```

> 目前**不支持反查**：修改/删除模式下放产物不会自动查出农夫乐事的原配方，
> 因为那需要依赖农夫乐事自己的类，本模组不直接依赖它。

### 4.7 机械动力处理配方

「分组」切到**机械动力**后，「类型」只有一个「处理」。**具体用哪台机器由「机器」按钮切换**，
按钮上显示「机器名：动作」，比如 `粉碎轮：粉碎`、`动力冲压机：冲压`——
机器名取自 Create 官方 `zh_cn.json`，不是自己翻的。

| 按钮显示 | 配方类型 | 副产物 |
|---|---|---|
| 粉碎轮：粉碎 | `create:crushing` | ✅ |
| 石磨：研磨 | `create:milling` | ✅ |
| 鼓风机：洗涤 | `create:splashing` | ✅ |
| 鼓风机：缠魂 | `create:haunting` | ✅ |
| 动力锯：切割 | `create:cutting` | |
| 动力冲压机：冲压 | `create:pressing` | |
| 动力搅拌器：混合 / 压缩 | `create:mixing` / `compacting` | |
| 砂纸：打磨 | `create:sandpaper_polishing` | |
| 注液器：注液 | `create:filling` | |
| 工作盆：抽液 / 处理 | `create:emptying` / `basin` | |
| 机械手：部署 / 物品应用 | `create:deploying` / `item_application` | |

> **有 4 组机器是共用的**：动力搅拌器同时负责混合和压缩，鼓风机负责洗涤和缠魂，
> 机械手负责部署和物品应用，工作盆既抽液也做通用处理。所以光看机器名不够，
> **动作名是必要的**——这正是按钮显示「机器名：动作」而不是只显示机器名的原因。

界面上：

- 材料 **3×3 九格**，产物在右侧
- 「**处理时间**」输入框（tick，默认 **100**，不是原版烧炼的 200）
- 「**热度**」按钮循环 **无 → 加热 → 超热**，对应 `heated` / `superheated`
- **副产物**栏位只有上表标 ✅ 的四台机器才出现，每格右边一个概率框（0–1）

生成：

```js
event.recipes.create.crushing(
  [
    'minecraft:gravel',
    'minecraft:sand',
  ],
  [
    'minecraft:cobblestone',
  ]
).processingTime(100).heatRequirement('heated').id('...')
```

- 调用路径是**命名空间对象**：`event.recipes.create.crushing(...)`，
  写成 `event.crushing(...)` 在游戏里会报 undefined
- 可选的 `processingTime` / `heatRequirement` 一律用**链式方法**写，不靠位置占坑——
  否则「跳过时间直接写热度」就得凭空补一个 null
- 默认值（100 tick / 无热度）**不写出来**

### 4.8 我的配方

查看本模组写过的所有配方（列表里显示产物图标、名称、操作类型、文件名）：

- **载入** —— 把这条配方连材料一起载回编辑器修改
- **删除** —— 从 .js 文件里整块移除该配方，并移除索引记录
- 支持翻页

### 4.9 保存后

- 界面显示简短状态（绿=成功 / 红=失败）
- **聊天栏输出文件的完整绝对路径**，例如：
  `[简易配方修改] 配方已保存，文件路径：D:\...\kubejs\server_scripts\xxx.js`
- 未安装 KubeJS 时会提示「未检测到 kubejs 目录，安装后才会生效」
- 点第一行的「**重载**」即可让 KubeJS 重新读取，等同 `/reload`。
  该操作需要 **OP 等级 2**（单人游戏直接放行）；没权限时会在聊天栏收到提示（见 [§1](#1-功能一览)）。

### 4.10 机械动力 · 序列组装

类型切到「序列组装」后，界面是：

```
[输入物品] [过渡物品] [产物]          循环次数：[ 4 ]
[1 部署]  [2 部署]  [3 压印]  [4 切割]
  [槽]      [槽]      [槽]      [槽]
```

- **输入物品** —— 第一步的原料
- **过渡物品**（半成品）—— 整个流程里被反复加工的那个物品，每一步的输入和输出都是它
- **4 个步骤按钮** —— 点一下在 部署 / 压印 / 切割 之间循环
  - 部署要额外材料，放进按钮下面的格子
  - 压印、切割只用过渡物品本身，对应格子会置灰
  - 灌装要流体，本模组不处理流体，所以没提供
- **循环次数** —— 整串步骤重复几次，默认 4
- **产物** —— 只支持单产物（Create 原版那些「废料」暂不支持）

生成：

```js
event.recipes.create.sequenced_assembly(
  ['create:precision_mechanism'],
  'create:golden_sheet',
  [
    event.recipes.create.deploying(
      'create:incomplete_precision_mechanism',
      ['create:incomplete_precision_mechanism', 'create:cogwheel']
    ),
    event.recipes.create.pressing(
      'create:incomplete_precision_mechanism',
      'create:incomplete_precision_mechanism'
    )
  ]
).transitionalItem('create:incomplete_precision_mechanism').loops(5)
```

---

## 5. 搜索框

编辑器上方的搜索框，输入即出结果浮层。**语法与 JEI 一致**：

| 输入 | 效果 |
|---|---|
| `木板` | 按中文名匹配 |
| `oak_planks` | 按物品 ID 匹配 |
| `muban` | **拼音全拼**匹配（橡木**木板** → xiangmu**muban**） |
| `xmmb` | **拼音首字母**匹配（**x**iang**m**u**m**u**b**an） |
| `@create` | 只看 create 模组的物品 |
| `木板 @minecraft` | 空格分词，**全部命中**才算匹配 |

- **来源**可在浮层右下角切换：`全部物品` / `我的背包`
- 悬停任意格子会显示**物品名与物品 ID**。
  ⚠️ 早期版本文档写过「以及背包里有多少个」——当前实现**没有画数量**，
  对应的 `ItemSearchCache.countInInventory()` 是未被调用的死方法。
- 点击结果即放入：添加模式进**合成格**，修改/删除模式进**产物格**
- **右键搜索框可清空搜索**；滚轮翻页；`Esc` 先关浮层
- 一输入内容，**下半部分（合成区、控件、玩家背包）就整块隐藏**，只留搜索结果面板，
  所以不会出现「文字从底下透出来」的重叠；清空搜索后立刻恢复

> 物品列表取自游戏注册表（原版 + 所有模组），这正是 JEI 展示的那一套，
> 所以**不需要装 JEI 也能搜到全部物品**。若以后要接入 JEI 的额外过滤，
> 在 `ItemSearchCache` 里加一个来源分支即可。

> 拼音表来自 [pinyin-data](https://github.com/mozillazg/pinyin-data)（Unihan 读音），
> 已压成两行资源 `src/main/resources/simple_recipe_change/pinyin.txt`（26,700 字，185 KB）。
> 重新生成：`java -cp <tools> GenPinyin <pinyin.txt> <输出路径>`（`tools/GenPinyin.java`）。

---

## 6. 生成的脚本长什么样

**添加配方：**

```js
// ==== SIMPLE_RECIPE_CHANGE:BEGIN simple_recipe_change:cheap_torch
// 由「简易配方修改」自动生成，请勿手动编辑本块（下次保存会覆盖）
ServerEvents.recipes(event => {
  event.shaped(
    '4x minecraft:torch',
    [
      'A',
      'B'
    ],
    {
      A: 'minecraft:coal',
      B: 'minecraft:stick'
    }
  ).id('simple_recipe_change:cheap_torch').noMirror()
})
// ==== SIMPLE_RECIPE_CHANGE:END simple_recipe_change:cheap_torch
```

**修改配方**（先删后加，同一个块）：

```js
ServerEvents.recipes(event => {
  event.remove({ id: 'minecraft:torch' })
  event.shaped(
    '8x minecraft:torch',
    ...
  ).id('simple_recipe_change:cheap_torch')
})
```

**删除配方：**

```js
event.remove({ id: 'minecraft:torch' })       // 按配方 ID
event.remove({ output: 'minecraft:torch' })   // 按产物
```

**带 NBT 的产物**：产物格里的物品有附魔、药水效果、自定义名称这些 NBT 时，NBT 会被原样写进脚本：

```js
event.shaped(
  '3x minecraft:potion {Potion:"minecraft:healing"}',
  ...
)
```

> ⚠️ **物品 ID 和 `{` 之间那个空格不能省**。KubeJS 的 `ItemStackJS.parse(String)` 用**第一个空格**
> 切出物品 ID，剩下的部分只有以 `{` 开头才会被当成 SNBT（内部走 `NBTUtils.toTagCompound` →
> `TagParser.parseTag`）。写成 `'minecraft:potion{...}'` 会把整串当成物品 ID，直接报错。
> 数量前缀 `3x ` 写在最前面。

不想带 NBT 时，把底栏「**产物NBT**」切到「否」，生成的仍是纯 `'3x minecraft:potion'`。

**标签会被保留**：如果原版配方用的是 `#minecraft:planks` 这类标签，
反查时不会退化成某一种具体木材，而是继续写 `#minecraft:planks`。

### 文件写入规则

写入 `kubejs/server_scripts/<文件名>.js`，内容被标记块包住：

```
// ==== SIMPLE_RECIPE_CHANGE:BEGIN <标识> ====
...
// ==== SIMPLE_RECIPE_CHANGE:END <标识> ====
```

- **同一个标识**再次保存 → 整体替换旧块
- **新标识** → 追加到文件末尾
- **块外内容原样保留**，手写脚本不会被覆盖；从「我的配方」删除时也只移除对应块

另外维护一份索引 `kubejs/server_scripts/simple_recipe_change.index.json`，
记录每条配方的结构化快照，供「我的配方」载入与删除（删掉它不影响已生成的 .js）。

---

## 7. 关于原版配方的一些事实

生成格式不是猜的，是从**本机反编译的 1.20.1 源码**和 **KubeJS 1.20.1 源码（分支 `2001`）**确认的：

- **关闭镜像是 `.noMirror()`**，不是 `.mirrored(false)`：
  ```java
  // dev.latvian.mods.kubejs.recipe.schema.minecraft.ShapedRecipeSchema
  public RecipeJS noMirror() { return setValue(KJS_MIRROR, false); }
  RecipeKey<Boolean> KJS_MIRROR = ...key("kubejs:mirror").optional(true).exclude();
  ```
  镜像**默认开启**，所以只有选「镜像：否」才输出该方法。
  注意还有个额外条件（`KubeJsWriter.java:134`）：`.noMirror()` **只在有序合成时输出**——
  无序配方即便选了「镜像：否」也不写，因为无序配方本来就没有方向可言。

- **原版 shaped 配方永远同时尝试正向与镜像**，JSON 里根本没有 `mirrored` 字段：
  ```java
  // net.minecraft.world.item.crafting.ShapedRecipe
  if (this.matches(container, i, j, true)) return true;   // 正向
  if (this.matches(container, i, j, false)) return true;  // 镜像
  ```
  所以「关闭镜像」只能靠 KubeJS 自己的 `kubejs:mirror` 机制。

- **「物品栏合成」和「工作台合成」不是两种配方类型**，都用 `minecraft:crafting_shaped`，
  区别只在合成格尺寸（物品栏 2×2、工作台 3×3）。原版 `ShapedRecipe.canCraftInDimensions`
  要求网格不小于图案尺寸，所以 2×2 图案物品栏里能合成，3×3 必须用工作台。

- **修改原版配方必须「先删后加」**——原版配方无法原地替换。

- **输入槽做成「展示槽」而不是真实槽位**：里面从来不放假物品，材料以<b>物品 ID 字符串</b>保存在客户端，
  由界面自己画出来。原因是回填的原版材料只用于展示与编辑，一旦放进真实槽位，
  「放一个铁块进产物格 → 输入槽被填入 9 个铁锭 → 拿走或关掉界面」就能凭空刷材料。
  改成 ID 字符串后这类漏洞从根上消失；附带好处是 `#标签` 写法能自然保留（ID 本身就是 `#minecraft:planks`），
  不必再额外维护一份标签快照。只有**产物格**是真实槽位（要用它的堆叠数量表示产出数量）。

- **烧炼与锻造的 KubeJS 写法**（同样来自 2001 分支源码）：
  `CookingRecipeSchema` 的键是 `result / ingredient / experience(别名 xp, 默认 0) /
  cookingtime(别名 cookingTime, 默认 200)`；`SmithingTransformRecipeSchema` 的键是
  `result / template / base / addition`，`event.smithing()` 的参数顺序就是输出、模板、基础物品、升级物品。

---

## 8. 第三方配方类型的支持情况

这一节记录的是**实际反编译 `kubejs-forge-2001.6.5-build.26.jar` 得出的结论**（用 `javap` 读的字节码），
不是查文档推测的。改动前想扩展新类型，请先照这个方法核实。

### 8.1 依赖规则：装了本体就必须装对应的 KubeJS 适配

| 装了 | 没装 | 结果 |
|---|---|---|
| — | KubeJS | **Forge 拒绝加载**（`mods.toml` 里的硬依赖） |
| 农夫乐事 | KubeJSDelight | **本模组拒绝加载**，并在报错里写明缺什么 |
| 机械动力 | KubeJS Create（modid 是 **`kubejs_create`**，下划线） | **本模组拒绝加载** |
| 农夫乐事 / 机械动力都没装 | — | 不报错，对应**分组不显示**，只要求原版 KubeJS |

> **为什么这条规则写在代码里而不是 `mods.toml`**：Forge 的依赖声明只能表达
> 「A 必须装」或「A 装了更好」，**没法表达「如果装了 A 就必须装 B」**
> （`mandatory=false` 的依赖不会因为对方存在而变严格）。所以只能放在模组构造函数里查
> `ModList`，不满足就抛异常。

拒绝加载的理由是：这种情况下本模组照常能打开，但生成的脚本在游戏里**不会生效**，
玩家只会以为「这模组坏了」——与其让人摸不着头脑，不如在加载阶段就把原因说清楚。

### 8.2 KubeJS 内置支持的全部类型

KubeJS 原生注册了这些事件方法（`BuiltinKubeJSPlugin` 的静态初始化里无条件注册）：

| KubeJS 事件方法 | 配方类型 ID | 本模组 |
|---|---|---|
| `shaped` / `shapeless` | `minecraft:crafting_shaped` / `_shapeless` | ✅ |
| `smelting` / `blasting` / `smoking` | `minecraft:smelting` 等 | ✅ |
| `smithing` | `minecraft:smithing_transform` | ✅ |
| `dankStorageUpgrade` | `dankstorage:upgrade` | ❌ |
| `custom(JsonObject)` | 任意 | ❌ 未使用 |

> `SpecialRecipeSchema`（烟花、地图克隆、鞘翅修补等）也注册了，但那些配方**不可编辑**，
> 所以没有接。

### 8.3 没被 KubeJS 支持的模组

**农夫乐事（Farmer's Delight）、Create 等都不在内置列表里。** 它们需要：

- 外部独立插件（如 kubejs 插件生态里的第三方包），**不在 kubejs 本体 jar 内**；或
- 退回 `event.custom()` 直接写 JSON 配方

`event.custom()` 的约束很宽松——字节码里**只校验 JSON 里有没有 `type` 字段**，其余原样透传。
而且 KubeJS 的 `RecipeJS` 有 `public JsonObject originalJson` 字段，说明它手里握着每条配方的原始 JSON，
理论上**不需要逆向每个模组的配方格式**就能通用回填。

### 8.4 想继续扩展时的做法

1. 照 8.1 的方法 `javap` 反编译对应版本的 kubejs jar，确认事件方法名与参数顺序
2. 在 `RecipeType` 枚举加类型，指定 `Category` 与 `kubeJsMethod()`
3. 在 `KubeJsWriter` 的 `switch` 加渲染分支
4. 在 `VanillaRecipeLookup` 加反查分支（1.20.1 的 `Recipe.getType()` 返回 `RecipeType<?>`，
   **它没有 `id()` 方法**，那是 `RecipeSerializer` 上的；判断第三方配方类型目前只能靠命名空间）
5. 在 `tools/WriterCheck.java` 补回归用例再动手

---

## 9. 回归测试

KubeJS 输出格式和拼音搜索是风险最高的两块，都被写成**不依赖 Minecraft 的纯 Java**，可脱机验证：

```powershell
cd "D:\DSH work\simple-recipe-change"
$jdk = "C:\Program Files\Java\jdk-17\bin"
foreach ($t in "WriterCheck","SearchCheck") {
  & "$jdk\javac.exe" -encoding UTF-8 -d build\tools-check -sourcepath src\main\java "tools\$t.java"
  & "$jdk\java.exe" "-Dfile.encoding=UTF-8" -cp "build\tools-check;src\main\resources" $t
}
```

- `WriterCheck`：有序/无序/镜像、2×2 裁剪、图案裁剪与字母复用、名称规范化、
  托管块合并与移除、添加/修改/删除三种输出、各类校验报错
- `SearchCheck`：拼音全拼与首字母、中文/ID 匹配、`@模组名`、空格分词、列表过滤

---

## 10. 项目结构

```
simple-recipe-change\
├── dev.ps1 / dev.cmd                    ← 构建与运行入口
├── gradle.properties                    ← modid / 名称 / 版本 / MC 与 Forge 版本
├── tools\
│   ├── WriterCheck.java                 ← KubeJS 输出回归测试
│   ├── SearchCheck.java                 ← 拼音 / 搜索回归测试
│   ├── GenPinyin.java                   ← 拼音表生成器
│   └── build-out.init.gradle            ← 产物被占用时改输出目录用
└── src\main\
    ├── java\com\wowuwo233\simple_recipe_change\
    │   ├── SimpleRecipeChangeMod.java   ← 主类
    │   ├── kubejs\                      ← ★ 纯逻辑：数据模型 + 脚本生成（可脱机测试）
    │   │   ├── Operation / RemoveBy / RecipeType / RecipeDraft
    │   │   ├── KubeJsWriter             ← 渲染 KubeJS 代码 + 托管块增删改
    │   │   ├── KubeJsFileWriter         ← 写入 kubejs/server_scripts
    │   │   └── RecipeIndex / RecipeIndexEntry  ← 「我的配方」索引
    │   ├── search\                      ← ★ 纯逻辑：拼音表 + 搜索匹配（可脱机测试）
    │   ├── server\VanillaRecipeLookup   ← 按产物反查原版配方，含标签保留
    │   ├── menu\                        ← 编辑菜单（真实槽位，可从背包拖物品）
    │   ├── network\                     ← 12 个网络包
    │   └── client\                      ← G 键、编辑器界面、我的配方列表、物品索引缓存
    └── resources\
        ├── simple_recipe_change\pinyin.txt   ← 拼音表（26700 字）
        └── ...
```

---

## 11. 改成你自己的 mod

标识集中在 `gradle.properties`：

```properties
mod_id=simple_recipe_change
mod_name=Simple Recipe Change
mod_group_id=com.wowuwo233.simple_recipe_change
mod_authors=wowuwo233
mod_version=1.11.0
```

改动后需同步：主类的 `MODID` 常量、Java 包目录与 `package` 语句、
`resources\assets\<modid>\` 与 `data\<modid>\` 目录名。`mods.toml` 不用动（占位符自动替换）。

> ⚠️ modid 和包名**不能有空格**。Forge 强校验 `^[a-z][a-z0-9_]{1,63}$`，含空格会直接启动崩溃。
> 显示名（`mod_name`）随便写。

---

## 12. 已验证的内容

分两类标注：**实跑验证过** 与 **仅代码核实**。别把后者当成实测。

**实跑验证（本次实际执行）：**

- [x] `gradlew compileJava` → **BUILD SUCCESSFUL**（整个项目含 Minecraft 依赖的类都能编译）
- [x] `WriterCheck` 脱机回归测试 **ALL CHECKS PASSED**
      （含有序/无序/镜像、2×2 裁剪、托管块增删改、添加/修改/删除三种输出、
      熔炉/高炉/烟熏炉的 `event.smelting/blasting/smoking`、xp 与 cookingTime 的默认值省略、
- [x] `SearchCheck` 脱机回归测试 **ALL CHECKS PASSED**
      （拼音全拼与首字母、中文名/物品 ID 匹配、`@模组名`、空格分词全部命中、列表过滤）
- [x] KubeJS 支持的配方类型清单，由 `javap` 反编译
      `kubejs-forge-2001.6.5-build.26.jar` 逐个确认（方法名、参数顺序、键名），
      结果记在 [§8](#8-第三方配方类型的支持情况)
- [x] `dev.cmd build` → **BUILD SUCCESSFUL**，`jar` 与 `reobfJar` 都正常执行
- [x] 产物 `simple_recipe_change-1.11.0.jar`（235.4 KB，**50 个类**）内部结构正确：
      `mods.toml` 的 `modId=simple_recipe_change` / `version=1.11.0` / `license=GPL-3.0-only`
      与 `gradle.properties` 一致；
      资源里**无任何示例物品**（MDK 示例物品已清干净），只有 `pack.mcmeta`、两个 lang 文件、
      `mods.toml` 和 189 KB 的 `pinyin.txt`
- [x] 已输出到工作区：`D:\DSH work\simple_recipe_change-1.11.0.jar`
- [x] **1.8.4 修完遗留项后复验**：`compileJava` 与 `build` 均 **BUILD SUCCESSFUL**；
      `WriterCheck`（含新增的「切换类型时格子重排」用例）与 `SearchCheck` 双双 **ALL CHECKS PASSED**；
      jar 内 `description` 的中文正常（`gradle.properties` 里用 `\uXXXX` 转义后写入），
      `Config.class` 已不再打进包
- [x] **1.9.0 产物 NBT**：`WriterCheck.outputNbt()` 覆盖「单个产物带 NBT / 数量前缀位置 /
      无 NBT 时保持原样 / NBT 内单引号转义 / 修改模式两条语句都带 NBT」，
      与 `SearchCheck` 一起 **ALL CHECKS PASSED**

**此前构建时验证（沿用历史记录，本次未重跑）：**

- [x] Gradle 8.8（腾讯镜像）+ ForgeGradle（Forge maven）解析成功；Java 17 工具链识别正确
- [x] Minecraft 1.20.1 完整反编译（`decompile → inject → patch`）
- [x] `gradlew build` → **BUILD SUCCESSFUL**
- [x] `gradlew runData` → **BUILD SUCCESSFUL**，运行时确认模组加载、菜单与网络注册正常

**仅代码核实（读源码确认，未在游戏里点过）：**

- [x] 12 个网络包全部注册（`ModNetwork.java:54-77`）
- [x] 9 种配方类型的界面布局随模式/类型切换（`RecipeEditorMenu.java` 的 `layoutSlots`）
- [x] 输入槽确为不可交互的「展示槽」，产物格是真实槽位（`RecipeEditorMenu.java:107-130`）
- [x] `#标签` 在反查时保留（`VanillaRecipeLookup.java:248-306`，要求标签内物品数**完全相等**才输出）

> ⚠️ **界面本身没有在真实客户端里点开验证过**——需要图形显示，当前自动化环境做不到。
> 你 `.\dev.ps1 runClient` 后按 G 即可看到；发现布局或交互问题告诉我具体现象即可修。
>
> 要让配方真正生效需要额外安装 **KubeJS**（把它的 jar 放进 `mods`）。
>
> 工作区里还留着旧版 `simple_recipe_change-1.8.3.jar` / `1.8.4.jar` / `1.9.0.jar` / `1.9.1.jar`，可手动删除；
> **请用 `simple_recipe_change-1.11.0.jar`**。

---

## 13. 遗留项处理记录（1.8.4 – 1.11.0）

1.8.1 审计出来的问题在 1.8.4 逐条处理了一遍。下表是结论，细节在后文。

| # | 问题 | 处理 |
|---|---|---|
| ① | 重载注释与实现矛盾 | **保留 OP 限制**，改注释；被拒时补聊天提示 |
| ② | `Config.java` 是 MDK 残留却注册成真配置 | 删除文件与注册 |
| ③ | 两个重载按钮 | 删掉底栏那个，只留顶部 |
| ④ | 未被使用的死代码 | 删除确认无引用的；测试在用的保留 |
| ⑤ | `mod_description` 是英文占位 | 改成中文真实描述 |
| ⑥ | `en_us.json` 里填的是中文 | 改成英文 |
| ⑦ | 索引 500 条上限静默丢弃 | 触顶时在保存反馈里明确提示 |
| ⑧ | `cells` 排布宽度随类型变 | `withType()` 按新旧宽度重排，并加回归用例 |
| ⑨ | 产物 NBT 被洗掉（1.9.0） | 保存时读产物槽的 SNBT，写进脚本与索引 |
| ⑩ | 快捷栏被窗口底边裁掉（1.9.1） | 状态提示挪到标题栏，窗口 388 → 370，背包整体上移 |
| ⑪ | 缺营火配方（1.10.0） | 新增 `CAMPFIRE` 类型，默认 600 tick |
| ⑫ | 缺序列组装（1.11.0） | 新增类型：输入 / 过渡 / 4 步 / 循环次数，生成 `sequenced_assembly` |

### ① 重载权限：保留限制，而不是放开

原注释写「不依赖 OP」，实现里却调了 `canWrite`。两条路都能消除矛盾，这里选了**保留限制**：
`server.reloadResources(...)` 会重建全部数据包与配方，一次几百毫秒的服务端停顿，
任何玩家都能按的话，几个人连点就能把服务器卡住。

改法是：注释如实写明「需要 OP 等级 2（单人游戏直接放行）」，
并在被拒时回一句 `[简易配方修改] 重载需要管理员权限（OP 等级 2）`——之前是静默 `return`，
玩家点了没反应也查不出原因。

### ② 删掉 `Config.java`

`logDirtBlock` / `magicNumber` / `magicNumberIntroduction` / `items` 四个配置项全仓库无人读取，
却因为 `registerConfig` 出现在玩家的 `config/simple_recipe_change-common.toml` 里，
改了毫无作用。文件和注册一起删除，`build.gradle` 不用动。

### ③ 重载入口只留一个

底栏的「重载脚本」与顶部的「重载」行为完全相同，删掉底栏那个；
「清空编辑区」左移到它原来的位置，底栏变成「保存并写入 / 清空编辑区」两个按钮。

### ④ 死代码：删了 6 处，保留了 2 处

删除（确认全仓库无引用）：

| 位置 | 内容 |
|---|---|
| `ItemSearchCache.java` | `countInInventory()` |
| `RecipeDraft.java` | `distinctIngredients()` / `trimmedPattern()` / `contentBox()` |
| `RecipeIndex.java` | `toJson(RecipeIndexEntry)` / `parseJson(String)` / `shortName()` / `prim()` |
| `RecipeIndexEntry.java` | `toDraft()` |
| `Operation.java` | `description()` 字段与 `writesGrid()` |
| `RecipeType.java` / `CreateMachine.java` | 各自的 `byId()` |

**保留**（原审计把它们算作死代码，其实测试在用）：

- `KubeJsWriter.renderBlock(String, String)` 单条重载 —— `tools/WriterCheck.java` 有 8 处调用
- `RecipeDraft` 的 `withXxx()` —— `WriterCheck` 的用例 API，删了测试编不过

### ⑤ 模组描述

`gradle.properties` 的 `mod_description` 换成中文真实描述。

⚠️ 这里有个坑：**Gradle 按 ISO-8859-1 读 `gradle.properties`**，直接写中文会在
`mods.toml` 里变成乱码（`å¨æ¸¸æ` 那种）。必须写成 `\uXXXX` 转义，文件里留了一行解码后的原文备查。

### ⑥ `en_us.json` 改成英文

界面本身是硬编码中文，这个文件只剩两个按键名。既然它是 `en_us`，就填英文：
`Simple Recipe Change` / `Open Recipe Editor`。

### ⑦ 索引触顶不再静默

`RecipeIndex.upsert()` 现在返回「是否丢了旧记录」，`KubeJsFileWriter` 把它拼进保存反馈：

> 注意：索引已达 500 条上限，最旧的一条已从「我的配方」移出（它的 .js 脚本仍然有效）

上限本身没动——500 条够用，真要改得连索引结构一起动。

### ⑧ `withType()` 现在会重排格子

`cells` 是按**当前类型宽度**行优先排布的（`cell(x,y)` → `y * gridWidth + x`）。
3×3 的左上 2×2 是下标 0、1、3、4；直接换成 2×2 会被读成 0、1、2、3，第 2 行整个错位。
`withType()` 现在按新旧宽度重排，只保留重叠的左上角区域。
`WriterCheck.typeChangeReflow()` 盯着这个行为（改动前该用例会挂）。

### ⑨ 产物 NBT（1.9.0）

原来的 `quoteOutput` 只写物品 ID，产物格里的附魔、药水效果、自定义名称全被洗掉，
合成出来是个白板物品。

现在的做法：

1. 保存时由**服务端**读产物槽里真实的 `ItemStack`，`getTag().toString()` 拿到 SNBT
   （客户端只有 ID 和数量，拿不到 NBT，所以这一步只能在服务端做）；
2. 渲染成 `'3x minecraft:potion {Potion:"minecraft:healing"}'`；
3. NBT 一起写进索引的 `outputNbt` 字段，「我的配方」载入时用 `stackOf(id, nbt)` 还原到产物格，
   再保存不会丢；旧索引没有这个字段，按空串处理（向后兼容）。

**两个坑都记在 `KubeJsWriter` 的注释里**：

- **ID 与 `{` 之间的空格是硬性要求**。KubeJS 的 `ItemStackJS.parse(String)` 用第一个空格切
  物品 ID，剩下的部分只有以 `{` 开头才交给 `NBTUtils.toTagCompound` → `TagParser.parseTag`。
  少了这个空格，整串会被当成物品 ID 直接报错。
- **NBT 必须做 JS 字符串转义**（`\` `'` 换行）。自定义名称里出现单引号很常见，
  不转义会让生成的 `.js` 变成语法错误，整个脚本文件都加载不了。

`WriterCheck.outputNbt()` 覆盖了这两点。

1.9.1 又在底栏加了「**产物NBT**」开关：默认「是」，产物没有 NBT 时显示「无」并置灰；
关掉之后写进脚本和索引的都不带 NBT（适合「不管放什么进去，都产出干净物品」的整合包）。

### ⑩ 窗口高度与快捷栏（1.9.1）

1080p + GUI 缩放 3 时可用高度只有 360（1080/3），而窗口是 388——上下各被裁掉十几像素，
最直观的现象就是玩家背包的快捷栏只剩一半。

改法：把原来占着背包上方 16px 的**状态提示挪到标题栏右侧**（单行、超宽用省略号，
完整信息仍在聊天栏），背包整块上移（`PLAYER_Y` 304→292、`HOTBAR_Y` 362→348），
窗口高度 **388 → 370**。370 在 360 的可用高度里只溢出 10px（上下各 5px），
快捷栏能完整露出来（最下面 1px 边框可能贴着屏幕边，不影响使用）。

### ⑪ 营火（1.10.0）

补上 `event.campfireCooking(...)`。它和熔炉同属「单原料 → 产物」，但原版默认时间是
**600 tick**（熔炉 200），所以 `defaultProcessingTime()` 改成按类型返回，时间框的默认值和
标签也跟着类型走；`appendCooking` 判断「要不要写 cookingTime」也改成对比该类型的默认值，
否则营火每条都会多写一个 `.cookingTime(600)`。

**铁砧 / 酿造台为什么没做**：1.20.1 原版只有 7 种配方类型（crafting / smelting / blasting /
smoking / campfire_cooking / stonecutting / smithing），**没有铁砧和酿造台的配方类型**；
KubeJS 2001.6.5 里也没有任何 anvil / brewing 相关类。铁砧逻辑硬编码在 `AnvilMenu`，
只能靠 Forge 的 `AnvilUpdateEvent` 接管；酿造台的 `PotionBrewing.addMix` 是 **private static**。
两者都必须在 Java 模组里实现，**做不到「生成 KubeJS 脚本、卸载模组后仍生效」**——
这是平台限制，不是实现选择。

### ⑫ 序列组装（1.11.0）

参考 ViScriptRecipe 的序列组装界面做了一版：它用 LDLib2 画布 + 动态步骤列表，
我们这边是**固定 4 步**（覆盖 Create 自带配方足够），步骤类型用按钮循环。

实现上**没有新增数据字段**：`cells` 前两位是输入 / 过渡物品，2..5 是四步的材料；
步骤类型借用 `heat`（本类型不用热度），循环次数借用 `cookingTime`。
索引、网络包因此完全不用改结构。

**只支持部署 / 压印 / 切割**：灌装要流体，本模组不处理流体；「废料」多产物也没做。

---

## 14. 仍未覆盖的部分

- **界面没有在真实客户端里点开验证过**：需要图形显示，自动化环境做不到。
  `.\dev.ps1 runClient` 后按 G 即可看到；布局或交互有问题，描述现象即可修。
- **配方生效需要额外装 KubeJS**：把 jar 放进 `mods`，本模组只是生成脚本。
- **额外产物仍不带 NBT**：切菜板的 4 个额外产物格、机械动力的副产物都是从搜索框按 ID 选的，
  没有 NBT 来源，生成的还是纯 ID。要支持得先把这些格子改成真实槽位。
- **输入材料仍不带 NBT**：输入槽是「展示槽 + 字符串」，只记物品 ID 或 `#标签`。
  需要「必须用某个特定 NBT 物品当材料」时，得另外设计 `Item.of(...)` 的输入写法。
- **序列组装只做了子集**：固定 4 步、只有部署 / 压印 / 切割（灌装要流体）、单产物（无废料）。
- 旧版本 jar 仍留在 `D:\DSH work\`（`1.8.3`、`1.8.4`、`1.9.0`、`1.9.1`、`1.10.0`），**请用 `simple_recipe_change-1.11.0.jar`**。
