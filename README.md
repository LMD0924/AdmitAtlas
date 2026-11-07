# AdmitAtlas

> 全国高校录取数据档案库 · 高考志愿智能匹配平台

**AdmitAtlas** = *Admit*（录取）+ *Atlas*（地图集 / 档案集）。既指覆盖全国高校的录取数据版图，也指为每位考生绘制的志愿导航图。

基于各省历年录取投档线、一分一段表、招生计划与院校专业档案，把学生成绩换算为**省排名（位次）**，再按所在省份当年的志愿填报规则，输出「冲 / 稳 / 保」分档的可编辑志愿方案。

> ⚠️ 本项目当前处于 **P0 骨架阶段**：工程结构、配置、契约与占位接口已就绪，数据采集与匹配算法为待实现占位。所有建议仅供参考，最终以各省教育考试院公布数据为准。

---

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.2.5 · Spring Cloud 2023.0.1 · Spring Cloud Alibaba 2023.0.1.0 · Maven 多模块 · Java 17 |
| 运行形态 | **模块化单体**：全后端只有一个启动类、一个端口、一个可执行 jar；Spring Cloud / Nacos 依赖保留但默认关闭 |
| 前端 | Vue 3 · Vite 5 · JavaScript · TailwindCSS 3 · Pinia · Vue Router · Axios · Naive UI · ECharts |
| 存储 | MySQL 8（按年分区）· Redis 7 · Elasticsearch 8 · MinIO（原始文件留证） |
| 基础设施 | P3 演进目标：Nacos 注册/配置、Spring Cloud Gateway、Sentinel、XXL-Job、MQ、Prometheus / SkyWalking |

## 目录结构

外层是项目根目录，里面平铺三个部分：后端工程、前端工程、项目文档。参考 `Chronicles-Of-Time` 的组织方式。

```
AdmitAtlas/                              # 外层：项目根 / Git 仓库根
├── back-end-spring-cloud/               # 后端聚合工程（父 POM 在此目录下）
│   ├── pom.xml                          # 聚合 11 个平铺模块 + 依赖版本仲裁
│   ├── common-core/                     # 统一响应、错误码、异常、模块探针接口
│   ├── api/                             # 跨模块共享 DTO 契约
│   ├── gateway/                         # 统一 Web 入口配置（CORS / 拦截器）
│   ├── auth-user/                       # 用户与成绩档案
│   ├── school/                          # 院校 / 专业档案
│   ├── score/                           # 投档线、一分一段、批次线
│   ├── policy-rule/                     # 志愿规则配置
│   ├── match-engine/                    # 匹配引擎（策略 + Controller，核心）
│   ├── plan-report/                     # 方案与导出
│   ├── data-collector/                  # 离线采集
│   ├── launcher/                        # ★ 唯一启动类 / 唯一 application.yml / 唯一可执行 jar
│   ├── sql/                             # Flyway 建表脚本
│   ├── deploy/                          # docker-compose、Nacos 共享配置说明
│   └── docs/后端模块总览.md              # 后端模块职责说明
├── front-end/                           # Vue 3 SPA（Vite 5）
├── docs/                                # 全部设计文档（中文命名）
│   ├── 架构设计.md                       # 功能、服务、表、算法、路线
│   ├── 数据模型.md                       # 数据模型与字段字典
│   ├── 接口说明.md                       # REST 接口约定
│   ├── 开发指南.md                       # 本地开发与启动
│   ├── 数据合规.md                       # 数据来源与合规
│   └── 路线图.md                         # P0 - P3 路线图
└── README.md / LICENSE / .gitignore / .gitattributes
```

### 为什么是单启动类

`common-core` 到 `data-collector` 这 10 个模块都是**普通 library jar**：没有 `main` 方法、没有自己的 `application.yml`、不应用 `spring-boot-maven-plugin`。只有 `launcher` 依赖它们并持有唯一的 `AdmitAtlasApplication`，位于 `com.admitatlas` 根包，一次组件扫描装配全部模块，对外只暴露 `8080`。

这样做的好处：一个进程就能跑通全链路、本地调试简单、模块边界仍然清晰；等匹配计算或数据量成为瓶颈时，给目标模块补回启动类和配置即可拆成独立微服务，业务代码不用改。

`GET /api/v1/system/modules` 会列出被启动类扫描到的全部模块，可直接用来验证装配是否完整。

## 快速开始

```bash
# 后端：编译 11 个模块（需 JDK 17+ 与 Maven 3.8+）
cd back-end-spring-cloud
mvn -B -DskipTests clean package

# 后端：启动唯一的应用（默认 8080）
java -jar launcher/target/admitatlas-launcher-0.1.0-SNAPSHOT.jar

# 验证：应返回 8 个模块 + 三种考试模式
curl http://localhost:8080/api/v1/system/modules
curl http://localhost:8080/api/v1/match/strategies

# 前端：安装依赖并启动（需 Node 20+）
cd ../front-end
npm install
npm run dev          # http://localhost:5173
```

详细配置、验证命令与常见问题见 [docs/开发指南.md](docs/开发指南.md)，后端模块职责见 [back-end-spring-cloud/docs/后端模块总览.md](back-end-spring-cloud/docs/后端模块总览.md)。

## 三条设计基线

1. **跨年只比位次，不比分数** —— 一切历史数据经「一分一段表」换算为省排名后比较。
2. **志愿规则配置化** —— 省份 × 年份 × 考试模式（传统文理 / 院校专业组 / 专业+院校）由规则表 + 策略模式驱动，禁止硬编码。
3. **数据版本化 + 人工审核** —— 采集数据留证、校验、复核后才能发布，输出「匹配度 / 风险等级」而非「录取率」。

## 文档索引

| 文档 | 内容 |
|---|---|
| [架构设计](docs/架构设计.md) | 功能全景、服务划分、采集治理、匹配算法、前后端结构、风险 |
| [数据模型](docs/数据模型.md) | 核心表 DDL、索引、量级估算 |
| [接口说明](docs/接口说明.md) | REST 端点、请求/响应约定 |
| [开发指南](docs/开发指南.md) | 环境、构建、启动、验证、常见问题 |
| [数据合规](docs/数据合规.md) | 数据源分级、采集流水线、合规底线 |
| [路线图](docs/路线图.md) | P0 - P3 交付范围 |
| [后端模块总览](back-end-spring-cloud/docs/后端模块总览.md) | 后端 11 个模块的职责与依赖方向 |

## License

[MIT](LICENSE) © 2026 LMD0924
