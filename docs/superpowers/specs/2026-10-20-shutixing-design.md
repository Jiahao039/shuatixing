# 刷题星（ShuaTiXing）Android App 设计文档

- 日期：2026-10-20
- 状态：待评审
- 目标平台：Android（Kotlin + Jetpack Compose）

## 1. 产品概述

刷题星是一款**完全离线**的 Android 刷题应用。用户导入文字版 PDF 题库，App 在本地提取题目、选项、答案与解析，生成结构化题库；用户每日按设定题量刷题，App 自动判分，错题在本轮末尾循环重测直至答对，并按改良艾宾浩斯间隔安排后续复习。

### 1.1 核心目标

1. 导入文字版 PDF（题目 + 紧跟其后的答案），自动生成题库
2. 每日题量可设置（默认 30 题），按“到期错题 → 到期复习 → 新题”组卷
3. 客观题（单选 / 多选 / 判断）自动判分
4. 答错题末尾重测至答对；错题进入错题本并按间隔复习
5. 错题本、学习统计、题库管理、题库导入导出分享

### 1.2 非目标（v1 不做）

- 扫描版 / 图片版 PDF 的 OCR
- 题目内嵌图片的提取与展示（校对页可手动删除此类题）
- 账号体系、云同步、任何网络请求
- 填空题、简答题等主观题（仅预留数据结构扩展位）
- 每日提醒推送、TTS 朗读、AI 解析
- iOS 版本

### 1.3 使用场景与用户

- 单机使用，自用 + 分享 APK 给朋友
- 无账号、无网络权限；所有数据存于本机
- 朋友间通过导出的 `.json` 题库文件共享题库内容（不含个人作答记录）

## 2. 技术架构

### 2.1 技术选型

| 层 | 选择 | 说明 |
|---|---|---|
| 语言 / UI | Kotlin + Jetpack Compose (Material 3) | 原生 Android，深色模式跟随系统 |
| 最低版本 | minSdk 26（Android 8.0），targetSdk 35 | 覆盖绝大多数在用设备 |
| 数据库 | Room（SQLite） | 题库、作答记录、复习状态 |
| 设置 | DataStore Preferences | 每日题量、重测开关、主题 |
| PDF 文字提取 | PdfBox-Android (`com.tom-roush:pdfbox-android`) | 逐页提取带坐标的文字行 |
| 导航 | Compose Navigation | 单 Activity + 多 Composable 页面 |
| 图表 | Compose Canvas 自绘 | 避免引入图表库 |
| 依赖注入 | 手动 DI（`AppContainer`） | 个人项目，不引 Hilt |
| 构建 | Gradle（Kotlin DSL），AGP 8.x | 需 JDK 17 |

### 2.2 包结构（单 module：`app`）

```
com.shutixing.app
├── MainActivity.kt
├── di/AppContainer.kt
├── parser/                  # 纯 Kotlin，不依赖 Android API（PdfTextExtractor 除外）
│   ├── PdfTextExtractor.kt  # PdfBox-Android 包装：PDF → List<PageLines>
│   ├── QuestionParser.kt    # 文本行 → ParsedQuestion 列表
│   ├── Normalizer.kt        # 全半角、空白、页眉页脚清洗
│   └── model/               # TextLine, PageLines, ParsedQuestion, ParseReport
├── domain/
│   ├── review/Scheduler.kt  # 纯函数：答题结果 → 新复习状态
│   ├── session/SessionBuilder.kt  # 组卷与重测队列
│   └── model/               # QuestionType, ReviewLevel, SessionItem
├── data/
│   ├── db/                  # Room entities, DAOs, AppDatabase
│   ├── repository/          # BankRepository, PracticeRepository, StatsRepository
│   └── export/              # BankExporter, BankImporter（JSON 格式 v1）
└── ui/
    ├── theme/  navigation/  components/
    ├── home/  practice/  result/  wrongbook/
    ├── banks/  bankdetail/  import/  stats/  settings/
```

约束：`parser`（除 `PdfTextExtractor`）与 `domain/review`、`domain/session` 为纯 Kotlin，不引用 Android API，保证可用 JVM 单测直接验证。

## 3. 数据模型

### 3.1 表结构

**banks**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | TEXT PK | UUID |
| name | TEXT | 题库名称，默认取 PDF 文件名 |
| sourceFileName | TEXT? | 来源文件名 |
| createdAt | INTEGER | 毫秒时间戳 |
| sortOrder | INTEGER | 自定义排序 |

**questions**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | TEXT PK | UUID |
| bankId | TEXT FK → banks.id | 级联删除 |
| type | TEXT | `SINGLE` / `MULTIPLE` / `JUDGE` |
| stem | TEXT | 题干 |
| optionsJson | TEXT? | `[{"key":"A","text":"..."}]`，判断题为空 |
| answer | TEXT | 归一化答案：`A`、`AC`、`T` / `F` |
| explanation | TEXT? | 解析 |
| orderIndex | INTEGER | 在题库中的顺序 |
| stemHash | TEXT | 题干 + 选项文本（不含答案）归一化后的 SHA-256，用于去重 |
| isFavorite | INTEGER | 0/1 |
| createdAt | INTEGER | 毫秒时间戳 |

索引：`(bankId, orderIndex)`、`stemHash`。

**review_states**（题目导入时即创建：`level = 0`，`nextReviewDate = 导入当天`）

| 字段 | 类型 | 说明 |
|---|---|---|
| questionId | TEXT PK FK → questions.id | 级联删除 |
| level | INTEGER | 0–6，0=未掌握，6=已掌握 |
| nextReviewDate | TEXT | 本地日期 `yyyy-MM-dd` |
| consecutiveCorrect | INTEGER | 连续答对次数 |
| wrongCount | INTEGER | 累计答错次数 |
| lastAnsweredAt | INTEGER? | 毫秒时间戳 |

**attempts**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | INTEGER PK 自增 | |
| questionId | TEXT FK → questions.id | 级联删除 |
| localDate | TEXT | 本地日期 `yyyy-MM-dd`，用于按天聚合 |
| answeredAt | INTEGER | 毫秒时间戳 |
| isCorrect | INTEGER | 0/1 |
| selectedAnswer | TEXT | 用户答案（归一化） |
| durationMs | INTEGER | 作答耗时 |
| mode | TEXT | `DAILY` / `RETEST` / `WRONG_BOOK` |

索引：`(localDate)`、`(questionId)`。

### 3.2 派生状态（不落库）

- **错题本成员**：`wrongCount > 0 且 consecutiveCorrect < 2`。连续答对 2 次后自动移出；再次答错则重新进入。
- **已掌握**：`level >= 6`；题库掌握度 = 已掌握题数 / 题库总题数。
- **每日统计**：由 `attempts` 按 `localDate` 聚合，不单独建表。
- **连续打卡天数**：截至今天（或昨天）连续、每天至少有 1 条作答记录的自然日天数。

### 3.3 设置项（DataStore）

| 键 | 默认值 | 说明 |
|---|---|---|
| dailyTarget | 30 | 每日题量（1–500） |
| retestWrongInSession | true | 答错题本轮末尾重测；v1 固定为 true（保留键以便后续开关） |
| themeMode | SYSTEM | SYSTEM / LIGHT / DARK |

## 4. PDF 解析流程

### 4.1 流程

1. 用户通过 SAF（`ACTION_OPEN_DOCUMENT`，`application/pdf`）选择文件
2. `PdfTextExtractor` 用 PdfBox-Android 逐页提取文本行（文本 + 基线坐标），带页级进度回调；逐页加载、用完即弃，避免大文件 OOM
3. `Normalizer` 清洗：
   - 去页眉页脚：同一文本在超过 50% 页面同一相对位置出现则视为页眉/页脚删除
   - 去纯页码行（如 `- 12 -`、`第 12 页`）
   - 全角字母数字转半角（仅选项标记、答案字母、题号范围），合并多余空白
   - 按坐标把同一视觉行的文本块合并为一行
4. `QuestionParser` 分块与识别（见 4.2）
5. 生成校对列表 → 用户编辑 / 删除 / 补答案
6. 确认后入库存为新题库（或合并进已有题库）

### 4.2 解析规则

**分块**：遇到题号行即开始新题。题号行正则：
`^\s*(\d{1,3})\s*[.、．)）]\s*` 或 `^\s*第\s*(\d{1,3})\s*题`

**块内行分类**（按优先级）：

- 答案行：`^\s*[【\[]?\s*(答案|正确答案|参考答案|Answer|Ans)\s*[】\]]?\s*[:：]?\s*(.+)$`
  - 答案值支持：`A`、`AC`、`A、C`、`A C`、`对`、`错`、`正确`、`错误`、`√`、`×`、`T`、`F`、`是`、`否`
  - 兼容答案与最后一个选项同行：`A. xx  答案：B`（选项解析时先剥离末尾答案片段）
- 解析行：`^\s*[【\[]?\s*(解析|分析|解答|答案解析)\s*[】\]]?\s*[:：]?\s*(.*)$`；解析可跨多行，直到下一题号或块结束
- 选项行：`^\s*([A-H])\s*[.、．)）]\s*(.*)$`
  - 同一行多选项：按 `(?=[A-H]\s*[.、．)）])` 切分
  - 选项文本可跨行续接，直到下一个选项标记或答案/解析行
- 其余行：并入题干（若在首个选项之前）或并入上一个选项文本

**题型推断**：
- 存在选项：答案字母数 > 1 → `MULTIPLE`，否则 `SINGLE`
- 无选项且答案 ∈ {对, 错, 正确, 错误, √, ×, T, F, 是, 否} → `JUDGE`，答案归一化为 `T`/`F`
- 无选项且答案非法 → 标为“待补答案”，默认为 `JUDGE`，由用户在校对页修改

**答案归一化**：字母去分隔符、转大写、去重、按字母排序（`CA` → `AC`）。

**可靠性要求**：
- 每题输出 `ParseWarning`（缺答案、缺选项、答案字母不在选项中、题干为空）
- 解析失败/无任何题目时，提供“查看提取的原始文本”入口，提示改用手动添加
- 单题库题目上限 5000；超过提示分批导入

### 4.3 去重

- 题库内去重：`stemHash` 相同视为重复（题干 + 选项文本一致才算重复）
- 导入时可选择“跳过重复”或“仍然导入”
- 已有题库合并导入时，同样按 `stemHash` 提示重复

### 4.4 支持边界与错误处理

| 情况 | 处理 |
|---|---|
| 加密 / 需要密码的 PDF | 提示“暂不支持加密 PDF” |
| 扫描版 PDF（提取文本为空） | 提示“未检测到文字，可能是扫描版 PDF，暂不支持” |
| 文件损坏 / 无法打开 | 提示读取失败，可重试 |
| 解析中途取消 | 丢弃中间结果，不入库 |
| 题目含图片 | 图片不提取，校对页提示删除该题 |

## 5. 复习算法（改良艾宾浩斯）

### 5.1 间隔表

| level | 下次复习间隔 |
|---|---|
| 0 | 当天（错题重测）；次日进入复习 |
| 1 | 1 天 |
| 2 | 2 天 |
| 3 | 4 天 |
| 4 | 7 天 |
| 5 | 15 天 |
| 6 | 30 天（已掌握） |

### 5.2 状态转移（纯函数 `Scheduler.onAnswer(state, correct, today)`）

- **答对**：
  - `level = min(level + 1, 6)`
  - `consecutiveCorrect += 1`
  - `nextReviewDate = today + interval[level]`
- **答错**：
  - `level = 0`
  - `consecutiveCorrect = 0`
  - `wrongCount += 1`
  - `nextReviewDate = today + 1 天`
- 新题首次作答前 `level = 0`，`nextReviewDate = 导入当天`

说明：错题（含重测答对）最终都会以 level 0/1 的状态在次日及之后进入复习，符合“加入后几天的复习计划”。

## 6. 每日刷题与组卷

### 6.1 组卷规则（`SessionBuilder`）

设每日目标为 N（默认 30）：

1. **到期复习池**：`nextReviewDate <= 今天` 且**已作答过**的题（`attempts` 中存在记录）
2. **新题池**：从未作答过的题
3. 排序优先级：
   1. 错题本成员且到期
   2. 其他到期复习题
   3. 新题（按题库顺序、跨题库按 `banks.sortOrder` 与题序）
4. 取前 N 题组成主队列；题量不足 N 时取全部可用题。N 只统计主队列，重测题不占名额

### 6.2 错题重测队列

- 刷题过程中答错 → 该题加入**重测队列**（本轮末尾）
- 主队列结束后进入重测：答对则该题本次结束；答错则重新排到重测队列末尾，循环直到答对
- 当日计划完成条件：**主队列完成 且 重测队列清空**
- 重测作答同样写入 `attempts`（`mode = RETEST`），并更新复习状态：
  - 错题首次答错已进入 level 0、次日复习；重测答对后 `consecutiveCorrect = 1`，`nextReviewDate` 保持次日
- 会话状态（主队列题目 id、当前索引、重测队列、模式）持久化到 DataStore，退出或进程被杀后重新进入可继续本轮；当日计划完成或用户主动结束则清除

### 6.3 错题本专项

- 错题本页可发起“错题专项”练习（`mode = WRONG_BOOK`），不受每日题量限制，按到期时间排序；作答计入统计，但不占用每日题量
- 连续答对 2 次后自动移出错题本，但仍保留常规间隔复习

## 7. 页面设计

| # | 页面 | 内容 |
|---|---|---|
| 1 | 今日（首页） | 今日进度环（已完成/N）、开始刷题按钮、连续打卡天数、今日正确率、快捷入口（错题本、题库、统计） |
| 2 | 刷题页 | 顶部进度条与剩余数；题干；选项（单选 / 多选复选框 / 判断对错）；提交后即时判分（绿色/红色、正确答案、解析）；“下一题”；退出保存 |
| 3 | 本轮结果 | 本轮正确率、耗时、错题列表、“再来一轮”/返回首页 |
| 4 | 错题本 | 错题列表（题干摘要、题库、错误次数、下次复习日），筛选与排序，开始错题专项 |
| 5 | 题库列表 | 卡片（名称、题数、掌握度百分比）、导入按钮、搜索 |
| 6 | 题库详情 | 题目列表、搜索、题型筛选、收藏筛选、编辑/手动添加题目、导出题库、删除题库 |
| 7 | 导入向导 | 选文件 → 解析进度 → 校对列表（逐题编辑、删除、补答案、警告标记）→ 确认导入 |
| 8 | 统计 | 打卡日历（本月）、近 7/30 天答题量柱状图、正确率趋势、各题库掌握度 |
| 9 | 设置 | 每日题量、深色模式、题库导出/导入、清空数据、关于 |

交互约定：
- 全中文界面；Material 3；品牌色青蓝（#1565C0 系）+ 橙色强调（错题/重测）
- 提交前可修改选择；提交后锁定并显示反馈
- 多选未选择时提交按钮禁用
- 退出刷题中间确认，自动保存进度

## 8. 题库导入 / 导出（分享）

### 8.1 文件格式（`.json`，UTF-8）

```json
{
  "format": "shuatixing.bank",
  "version": 1,
  "exportedAt": "2026-10-20T12:00:00+08:00",
  "bank": { "name": "计算机二级题库", "sourceFileName": "题库.pdf" },
  "questions": [
    {
      "type": "SINGLE",
      "stem": "TCP 协议工作在 OSI 模型的哪一层？",
      "options": [
        { "key": "A", "text": "物理层" },
        { "key": "B", "text": "传输层" },
        { "key": "C", "text": "网络层" },
        { "key": "D", "text": "应用层" }
      ],
      "answer": "B",
      "explanation": "TCP 是传输层协议。"
    }
  ]
}
```

### 8.2 导出

- 从题库详情页导出，写入用户通过 SAF 选择的位置
- 不包含作答记录、复习状态、收藏
- 文件名：`<题库名>.shuatixing.json`

### 8.3 导入

- 校验 `format` 与 `version`，不匹配则拒绝并提示
- 逐题校验：题干非空、答案合法、选项与答案匹配；非法题跳过并汇总提示
- 选择：新建题库 / 合并到已有题库
- 合并时按 `stemHash` 去重，导入后新题创建 `review_states`（level 0，当天到期）

## 9. 测试策略

### 9.1 单元测试（JVM，`:app:test`）

- **QuestionParser**：文本样例覆盖
  - 单选 / 多选 / 判断
  - 答案紧跟题目、答案与选项同行、答案格式变体（`【答案】`、`Answer:`、`A、C`）
  - 含解析、跨行选项、跨行题干
  - 页眉页脚污染、页码行
  - 缺答案、缺选项、非法答案
  - 同一行多选项
- **Scheduler**：等级升降、掌握判定、日期计算（含跨月/闰年）
- **SessionBuilder**：优先级排序、题量截断、重测队列循环、无可用题
- **BankImporter / Exporter**：导入导出往返一致、版本校验、去重

### 9.2 数据库测试

- Room DAO：插入/查询/级联删除、聚合统计 SQL、错题本查询

### 9.3 手工验收（模拟器或真机）

全链路：导入 PDF → 校对 → 题库 → 今日刷题 → 答错重测至答对 → 次日到期复习 → 错题本恢复 → 统计变化 → 导出题库 → 新设备导入。

## 10. 构建与分发

- 开发环境：JDK 17、Android SDK（platform-tools、build-tools、platform 35、emulator）
- 调试：USB 真机或模拟器
- 分发给朋友：Gradle 生成签名 release APK（自建 keystore，保存在项目外，绝不提交）
- 版本号从 0.1.0 开始

## 11. 里程碑

1. 环境搭建 + 项目骨架（Compose、Room、DataStore）
2. 数据层 + 复习调度 + 组卷（含单测）
3. PDF 解析 + 导入校对流程（含单测）
4. 刷题会话 + 判分 + 重测队列 + 结果页
5. 错题本、题库管理、统计、设置、导入导出
6. 视觉打磨、空态/错误态、APK 打包与真机验收

## 12. 风险与对策

| 风险 | 对策 |
|---|---|
| 各来源 PDF 排版差异大，解析准确率不齐 | 解析器纯 Kotlin + 样例驱动单测；校对页兜底人工修正 |
| 中文 PDF 字体 / CMap 导致提取乱码 | PdfBox-Android 内置字体处理；实测多份样例后调整 |
| 大 PDF 内存占用 | 逐页提取、及时释放、进度可取消 |
| 单机数据丢失 | 设置页明确提示；题库导出即备份 |
