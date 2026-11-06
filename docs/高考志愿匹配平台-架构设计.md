# 全国高校录取数据平台 · 功能与项目结构设计

> 目标：采集全国高校历年录取分数（总分 + 科目相关分数口径）+ 各省一分一段/招生计划，建立高校档案库；学生输入高考成绩后，按其所在省份的志愿填报规则进行匹配，输出「冲 / 稳 / 保」志愿建议。
> 技术栈：后端 Spring Boot + Spring Cloud，前端 Vue3 + JavaScript + TailwindCSS。

---

## 一、三个决定成败的前提（先于任何设计）

1. **分数不能跨年直接比较。** 2024 年的 600 分和 2025 年的 600 分不是同一概念。所有历史数据必须借助「一分一段表」换算成 **位次（省排名）** 后再比较，并保留分数仅用于展示。这是整个系统的正确性基石。
2. **志愿规则是三维变量：省份 × 年份 × 考试模式。** 传统文理分科、3+3、3+1+2 的志愿单位（院校 / 院校专业组 / 专业+院校）、志愿数量（45 / 96 / 112）、选科要求、同分排序规则、批次设置全都不同。规则必须配置化 + 策略化，**严禁硬编码 if-else**。
3. **数据采集与治理占约 60% 工作量，且是合规红线。** 没有全国统一开放 API，数据源分散在各省考试院、阳光高考、高校招生网，格式含 PDF 表格 / Excel / HTML / 图片。必须做成独立子系统：留证 → 解析 → 校验 → 人工审核 → 版本发布。

**关于「对应科目的分数」的现实口径**：公开投档数据一般只有「最低投档分 + 最低位次」，几乎不公开单科成绩。单科分在系统中有三种用途，应作为**规则条件**而非录取数据建模：
- ① 同分排序（各省规则不同，如依次比语文、数学、外语、首选科目）；
- ② 院校/专业的单科门槛（如外语 ≥ 120 分、数学 ≥ 100 分）；
- ③ 外语语种、口试、体检等限制条件。

---

## 二、业务功能全景

### C 端（学生）
| 模块 | 功能 |
|---|---|
| 成绩档案 | 分步向导录入：省份 → 年份 → 考试模式 → 选科组合 → 总分 → 单科分 → 自动生成位次；支持多份成绩（模考） |
| 院校库 | 按层次（985/211/双一流）、地域、类型、学科检索；院校详情（简介、专业设置、历年录取趋势）；多校对比 |
| 专业库 | 学科门类 → 专业类 → 专业三级目录；开设院校、选科要求、就业方向 |
| 分数线查询 | 省控线 / 批次线、一分一段表、院校投档线、专业录取分（近 3–5 年趋势图） |
| 智能匹配 | 输入偏好（地域、专业关键词、院校层次、优先级）→ 输出冲/稳/保分档候选 |
| 志愿方案 | 按本省志愿结构生成可编辑志愿表（拖拽调序、数量校验、冲突提示）、保存多套方案、PDF/Excel 导出 |
| 政策百科 | 本省当年招生政策、志愿填报时间、规则说明 |
| 风险提示 | 每条建议标注依据（位次差、近三年波动、计划变化）与「仅供参考」免责声明 |

### B 端（运营 / 数据管理员）
采集任务监控与重试、原始数据留证查看、数据审核队列（通过/驳回/修正）、规则配置（志愿结构、选科要求、同分排序、批次线）、院校档案维护、数据版本发布与回滚、用户反馈与纠错、字典维护。

### 系统侧
调度、清洗校验、缓存预热、质量报告（覆盖率/缺失率/异常率）、操作审计、监控告警。

---

## 三、总体架构与微服务划分

```
[ Vue3 SPA ] ──CDN/Nginx──> [ Spring Cloud Gateway ] ──JWT/限流/灰度──> 业务微服务
                                                                          │
        ┌──────────────┬──────────────┬──────────────┬──────────────┬─────┴────────┐
    auth-user      school         score        policy-rule     match-engine    plan-report
     用户/成绩      院校档案      录取数据        规则中心         匹配引擎        方案/报告
        └──────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
                                      │ Feign / MQ
        ┌─────────────────────────────┴──────────────────────────────────────┐
        │  Nacos · Sentinel · Redis · MySQL · Elasticsearch · MQ · XXL-Job     │
        │  MinIO(原始文件) · Prometheus/Grafana · SkyWalking · Flyway          │
        └─────────────────────────────────────────────────────────────────────┘

[ data-collector 独立子系统 ] ──(离线/定时)──> 审核通过后才写入 score/school 库
```

| 服务 | 核心职责 | 主要技术 | 独立库 |
|---|---|---|---|
| `gateway` | 路由、JWT 鉴权、限流、跨域、灰度 | Spring Cloud Gateway + Sentinel | 否 |
| `auth-user` | 学生/管理员账号、成绩档案、偏好 | Spring Boot + JWT/Sa-Token | `user_db` |
| `school` | 院校档案、专业目录、专业组、招生计划 | Spring Boot + MyBatis-Plus + ES | `school_db` |
| `score` | 投档线、专业录取分、一分一段、批次线 | Spring Boot + Redis 缓存 | `score_db` |
| `policy-rule` | 省份考试模式、志愿结构、选科要求、同分排序 | 规则表 + 策略工厂 | `rule_db` |
| `match-engine` | 位次换算、资格过滤、风险打分、冲稳保、方案组装 | 独立计算服务 + 线程池 + 预计算候选池 | `match_db` |
| `plan-report` | 方案持久化、版本对比、PDF/Excel 导出 | EasyExcel + OpenPDF | `plan_db` |
| `data-collector` | 调度、抓取、解析、清洗、校验、审核、版本发布 | XXL-Job + MQ + PDFBox/EasyExcel + MinIO | `collect_db` + 对象存储 |
| `common / api` | 统一响应、异常、DTO、Feign 契约、工具类 | — | — |

> **工程建议（重要）**：初期不要一上来就拆 8 个微服务。数据量在千万行以内、团队规模有限时，先做 **模块化单体 Spring Boot（按 domain 分包）+ 独立的 data-collector 子系统**，配 Nacos + Redis + MySQL 即可。等匹配计算成为瓶颈或团队扩张，再按 `match-engine` → `score` → 其余的顺序拆出。微服务拆太早，分布式事务、链路排查、联调成本会吃掉全部收益。

---

## 四、核心数据模型

### 表清单
| 分类 | 表 | 说明 |
|---|---|---|
| 维度 | `dim_province` | 省份、当前考试模式、政策版本 |
| 维度 | `dim_school` | 院校代码、名称、主管部门、所在地、层次标签、类型、简介 |
| 维度 | `dim_major` | 学科门类 / 专业类 / 专业代码（教育部目录） |
| 维度 | `dim_subject_requirement` | 选科要求（物理必选、物化绑定、不限…） |
| 事实 | `fact_admission_plan` | 招生计划：**省 + 年 + 院校 + 专业组/专业**，人数、选科要求、批次 |
| 事实 | `fact_admission_score` | 录取结果：**最低分 / 最低位次 / 平均分 / 最高分 / 计划数 / 实录数** |
| 事实 | `fact_score_segment` | 一分一段：分数、本段人数、**累计人数（即位次）** |
| 事实 | `fact_batch_line` | 批次线 / 省控线（省 × 年 × 科类 × 批次） |
| 规则 | `rule_policy` | 志愿单位类型、批次、志愿数量、投档规则、**同分排序字段序列**、单科门槛 |
| 用户 | `student_profile` / `student_score` / `student_preference` | 成绩、选科、偏好 |
| 结果 | `match_task` / `suggestion_plan` / `suggestion_item` | 匹配任务、方案、明细（含依据与风险等级） |

### 关键 DDL 片段
```sql
CREATE TABLE fact_admission_score (
  id              BIGINT PRIMARY KEY AUTO_INCREMENT,
  province_code   VARCHAR(6)  NOT NULL,
  exam_year       SMALLINT    NOT NULL,
  category        VARCHAR(16) NOT NULL,   -- 理科/文科/物理类/历史类/综合
  batch           VARCHAR(16) NOT NULL,   -- 本科批/本科一批/专科批
  school_code     VARCHAR(10) NOT NULL,
  group_code      VARCHAR(32) DEFAULT NULL, -- 院校专业组（3+3/3+1+2）
  major_code      VARCHAR(16) DEFAULT NULL, -- 专业(类)+院校 模式下的专业
  min_score       SMALLINT    NOT NULL,
  min_rank        INT         NOT NULL,    -- 核心比较字段
  avg_score       SMALLINT,
  max_score       SMALLINT,
  plan_count      INT,
  actual_count    INT,
  source_url      VARCHAR(512) NOT NULL,   -- 留证
  data_version    VARCHAR(32) NOT NULL,    -- 省+年+批次+发布日期
  audit_status    TINYINT     NOT NULL,    -- 0待审 1通过 2驳回
  created_at      DATETIME    NOT NULL,
  KEY idx_query (province_code, exam_year, category, batch, min_rank),
  KEY idx_school (school_code, exam_year)
) PARTITION BY RANGE (exam_year) (...);

CREATE TABLE fact_score_segment (
  province_code VARCHAR(6), exam_year SMALLINT, category VARCHAR(16),
  score SMALLINT, segment_count INT, cumulative_count INT,  -- 累计人数 = 位次
  PRIMARY KEY (province_code, exam_year, category, score),
  data_version VARCHAR(32), source_url VARCHAR(512)
);
```

**量级估算**：31 省 × 3~5 年 × ~3000 院校 × 平均 20~40 条（专业组/专业）≈ 500 万~1000 万行。MySQL 单库 + 按年分区 + 上述联合索引完全够用，**不需要分库分表**；位次区间检索走 `(province_code, exam_year, category, min_rank)` 覆盖索引即可。

**数据版本化是刚需**：每年政策与计划都会变，`data_version` + `audit_status` 让「重算历史方案」「回滚错误数据」「多版本对比」成为可能。

---

## 五、数据采集与治理方案

### 来源分级
- A（权威）：各省教育考试院官网 —— 投档线公示、一分一段表、招生计划、政策文件
- B（权威）：阳光高考平台、教育部院校/专业目录 —— 院校档案、专业目录
- C（补充）：各高校招生网 —— 专业录取分、招生章程（单科/体检/语种要求）
- D（仅交叉校验）：第三方数据平台，**不作为权威源入库**

### 处理流水线
```
XXL-Job 调度 → 抓取(限频/UA/重试，必要时 Playwright 渲染)
  → 原始文件入 MinIO(留证：URL + 抓取时间 + 文件哈希)
  → 解析(PDF 表格 PDFBox/Tabula、Excel EasyExcel、HTML Jsoup、图片 OCR)
  → 字段映射与字典归一(院校代码、专业代码、科类)
  → 质量校验(分数区间合理、位次单调不减、计划数一致、跨源比对、同比波动阈值)
  → 人工审核队列(异常自动打标，管理员复核)
  → 版本发布(data_version 灰度生效) → 缓存预热
```

### 合规底线
仅采集**公开发布**数据；遵守 robots 与限频；每条记录保留 `source_url` 与采集日期；提供纠错入口；不转售原始数据；页面显著位置标注「数据来源 XX 考试院，仅供参考，最终以官方公布为准」。

---

## 六、志愿匹配引擎（系统核心）

### 输入
```json
{
  "provinceCode": "42", "examYear": 2026, "examMode": "3+1+2",
  "category": "物理类", "subjectCombo": ["物理", "化学", "生物"],
  "totalScore": 612,
  "subjectScores": { "语文": 112, "数学": 126, "外语": 128, "物理": 92 },
  "preferences": {
    "regions": ["湖北", "江苏", "北京"],
    "majorKeywords": ["计算机", "电子信息"],
    "schoolTiers": ["985", "211"],
    "priority": "专业优先"
  }
}
```

### 计算流程
1. **归一化**：查 `fact_score_segment` 把总分换算为位次；当年表未发布时用最近年份 + 计划数变化做插值，并明确标注「估算」。
2. **资格过滤**：批次线、选科要求（2024 起理工农医大量「物化绑定」）、招生地域、单科门槛、语种/体检限制。
3. **候选集**：取近 3 年该省同科类/同专业组的录取记录，按 `min_rank` 与考生位次的差值 Δ 做区间检索（走索引，不捞全表）。
4. **风险打分**：`Δ + 三年波动 σ + 计划数变化率 + 大小年修正 + 院校热度` → 0~100 匹配度。**输出「匹配度 / 风险等级」，不承诺「录取率」**，避免误导与合规风险。
5. **分档**：按位次百分位（而非绝对分差）划分冲 / 稳 / 保，三档比例可按用户风险偏好调整。
6. **排序打散**：偏好加权 + 匹配度，同时做地域/专业/层次去重打散，避免志愿表全挤在同一类院校。
7. **组装**：读取 `rule_policy` 生成本省合规志愿表（如「96 个 专业+院校」或「45 个 院校专业组 + 每组 6 专业」），逐条附依据与风险提示。

```java
// 伪代码：策略按 省 + 年 选择，规则全部来自配置表
MatchStrategy strategy = strategyFactory.of(provinceCode, examYear);
Rank rank = rankService.convert(studentScore);                    // 1
List<AdmissionRecord> pool = candidateRepo.byRankRange(           // 3
        provinceCode, last3Years, rank.value(), strategy.range());
List<Candidate> ok = strategy.filter(pool, studentScore);        // 2 选科/单科/批次
ok.forEach(c -> c.setMatchScore(riskModel.score(rank, c)));      // 4
VolunteerPlan plan = strategy.assemble(                          // 5~7
        tiering(ok, preference), rulePolicy.structure(provinceCode, examYear));
```

### 规则配置化设计
`rule_policy` 存结构差异（志愿单位类型、数量、批次、同分排序字段序列、单科门槛表达式），代码层用策略模式：
```
MatchStrategy
 ├─ TraditionalStrategy      // 院校 + 专业，文理分科
 ├─ MajorSchoolStrategy      // 专业(类)+院校，96/112 个志愿（浙江/山东/河北/重庆…）
 └─ SchoolGroupStrategy      // 院校专业组（上海/北京/江苏/湖北/广东…）
```
> 不建议用 Drools：这里的差异是**数据结构与流程差异**，不是简单条件判断，规则表 + 策略工厂更易维护、更易测试。

---

## 七、前端设计（Vue3 + JS + TailwindCSS）

### 技术组件
| 用途 | 选型 |
|---|---|
| 构建 / 框架 | Vite + Vue3 `<script setup>` Composition API + JavaScript（中期建议迁 TS） |
| 状态 / 路由 | Pinia + Vue Router（路由懒加载） |
| 样式 / UI | TailwindCSS + **Naive UI 或 Element Plus**（表格、表单、弹窗、步骤条），配合 `tailwindcss/typography`、`@tailwindcss/forms` |
| 请求 | Axios（统一拦截器：token、错误码、loading、重试） |
| 图表 | ECharts（近三年位次/分数趋势、一分一段分布、冲稳保分布） |
| 交互 | `vuedraggable@next`（志愿表拖拽排序）、VueUse（防抖/本地缓存）、虚拟滚动（大列表） |
| 导出 | `html2canvas` + `jspdf`（报告）、`xlsx`（志愿表） |
| 工程 | ESLint + Prettier + `unplugin-auto-import` + 环境变量分环境 |

### 页面 / 路由
| 路由 | 页面 | 核心组件 |
|---|---|---|
| `/` | 首页 + 政策公告 | PolicyBanner |
| `/score/wizard` | 成绩录入向导 | ScoreWizard、SubjectPicker、RankResult |
| `/schools` `/schools/:code` | 院校库检索 / 详情 | SchoolFilter、SchoolCompare、ScoreTrendChart |
| `/majors` | 专业库 | MajorTree、SubjectRequirementTag |
| `/lines` | 省控线 / 一分一段 / 投档线 | SegmentChart、BatchLineTable |
| `/match` | 智能匹配（主流程） | PreferenceForm、RiskBoard（冲/稳/保三栏） |
| `/plan/:id` | 志愿方案编辑 | VolunteerTable（拖拽 + 数量校验）、ReasonPopover、ExportBar |
| `/policies/:province` | 本省政策百科 | PolicyDoc |
| `/admin/*` | 管理后台（独立布局/权限） | CollectTaskMonitor、AuditQueue、RuleConfigEditor、VersionPublish |

### 目录结构
```
src/
├── api/                # 按领域拆：school.js score.js match.js plan.js rule.js auth.js
├── assets/             # 静态资源
├── components/         # 通用组件（SearchSelect, RankBadge, RiskTag, EmptyState...）
├── composables/        # useMatch.js useScoreWizard.js usePermission.js
├── layouts/            # DefaultLayout.vue / AdminLayout.vue
├── router/             # index.js + 路由守卫（登录、省份政策版本）
├── stores/             # user.js score.js plan.js dict.js
├── utils/              # request.js format.js validate.js constant.js
├── views/              # 页面（按上表分目录）
└── widgets/            # 业务强相关复合组件（RiskBoard, VolunteerTable...）
```

---

## 八、后端工程结构（Maven 多模块）

```
gaokao-platform/
├── pom.xml                       # 父 POM：依赖版本仲裁、插件统一
├── common/
│   ├── common-core/              # Result、BizException、常量、工具
│   ├── common-mybatis/           # 分页插件、字段填充、数据权限
│   ├── common-redis/             # 缓存封装、分布式锁
│   └── common-security/          # JWT 解析、鉴权注解
├── api/                          # Feign 契约 + DTO（按服务分包，避免循环依赖）
├── services/
│   ├── gateway/
│   ├── auth-user/
│   ├── school/
│   ├── score/
│   ├── policy-rule/
│   ├── match-engine/
│   ├── plan-report/
│   └── data-collector/           # 独立部署，不注册到 C 端网关
├── jobs/                         # XXL-Job Handler（采集、缓存预热、质量报告）
├── deploy/
│   ├── docker-compose.yml
│   ├── nacos/                    # 分环境配置
│   ├── sql/                      # Flyway 迁移脚本 V1__init.sql ...
│   └── k8s/                      # 可选
└── docs/                         # 接口文档、数据字典、规则配置说明
```
**服务内部分包**：`controller / service / domain(entity,dto,vo) / mapper / strategy / client / config`。
**匹配引擎额外**：`strategy/`、`model/`（风险模型）、`candidate/`（候选池预计算）。

---

## 九、关键接口示例

```
POST /api/v1/scores/convert-rank          # 分数→位次（含估算标注）
POST /api/v1/match/analyze                # 主匹配（异步：返回 taskId，前端轮询/WS 推送）
GET  /api/v1/match/tasks/{taskId}
GET  /api/v1/schools/{code}/admission-scores?province=42&years=3
GET  /api/v1/segments?province=42&year=2025&category=物理类
GET  /api/v1/rules/policy?province=42&year=2026
POST /api/v1/plans  /  GET /api/v1/plans/{id}  /  GET /api/v1/plans/{id}/export
POST /api/v1/admin/collect/tasks          # 管理端：采集任务
POST /api/v1/admin/data-versions/publish  # 管理端：版本发布
```
统一 `Result<T>` 响应、全局异常处理、幂等键、分页规范、接口文档走 SpringDoc OpenAPI。

---

## 十、分阶段落地路线

| 阶段 | 范围 | 目标 |
|---|---|---|
| **P0 试点** | 单省（建议先选一个 3+1+2 省份）、院校/专业档案、近 3 年投档线、一分一段、位次换算、冲稳保基础匹配、前端主流程 | 端到端跑通，验证数据口径与算法正确性。保持模块化单体 |
| **P1 数据** | data-collector 全流水线 + 审核后台 + ES 检索 + 方案保存/导出 + 质量报告 | 数据可持续更新，不再靠手工导入 |
| **P2 全国规则** | 规则配置化覆盖 3+3 / 3+1+2 / 传统文理；选科过滤；96/112 志愿结构；专业录取分 | 多省可用 |
| **P3 提质** | 风险模型调优、数据版本灰度、监控告警、压测、`match-engine` 拆为独立服务并水平扩容 | 稳定性与性能 |

---

## 十一、风险与对策

| 风险 | 对策 |
|---|---|
| 数据合规 / 版权 | 仅公开数据、留证、限频、标注来源、纠错入口、不转售 |
| 数据权威性错误 | 人工审核 + 跨源比对 + 版本回滚；界面明确「仅供参考，以省考试院为准」 |
| 政策年年变 | 规则全部配置化，每年只更新 `rule_policy` 与数据版本，不改代码 |
| 跨年分数不可比 | 全链路以位次为准，分数仅展示；插值结果显式标注「估算」 |
| 建议被当作录取承诺 | 输出「匹配度 / 风险等级」而非「录取率」，全站免责声明 |
| 冷启动数据从 0 到 1 | 优先 P0 单省试点，先人工整理 3 年数据验证模型，再上自动化采集 |
| 匹配计算耗时 | 候选池按 `(省,年,科类,位次区间)` 预计算入 Redis；匹配异步化 + 结果缓存 |
| 同分排序各省不同 | 抽象为 `rule_policy.sort_fields` 有序字段序列，由策略消费 |

---

## 十二、开工前需确认的 3 个问题

1. **数据来源**：是否已有采购/授权的官方数据源？若从零采集公开数据，需确认合规边界与可投入的数据运营人力（这是最大成本项）。
2. **覆盖范围**：先做单省试点（强烈建议）还是一次性全国全模式铺开？后者规则与数据工作量呈数量级上升。
3. **产品定位**：仅做「参考性冲稳保建议」，还是要面向商业化志愿填报（涉及概率模型精度、付费报告、专家复核、更重的合规资质）。

---

*本文档为架构设计基线，可直接用于拆分开发任务与建库建表。*
