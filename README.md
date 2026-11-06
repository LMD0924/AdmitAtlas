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
| 前端 | Vue 3 · Vite 5 · JavaScript · TailwindCSS 3 · Pinia · Vue Router · Axios · Naive UI · ECharts |
| 存储 | MySQL 8（按年分区）· Redis 7 · Elasticsearch 8 · MinIO（原始文件留证） |
| 基础设施 | Nacos（注册/配置）· Spring Cloud Gateway · Sentinel · XXL-Job · MQ · Prometheus / SkyWalking |

## 目录结构

```
AdmitAtlas/
├── docs/                     # 全部设计文档
│   ├── architecture-design.md    # 架构设计（功能、服务、表、算法、路线）
│   ├── data-model.md             # 数据模型与字段字典
│   ├── api-reference.md          # REST 接口约定
│   ├── development-guide.md      # 本地开发与启动
│   ├── data-compliance.md        # 数据来源与合规
│   └── roadmap.md                # P0 - P3 路线图
├── backend/
│   ├── pom.xml                   # 父 POM（依赖版本仲裁）
│   ├── common/common-core/       # 统一响应、异常、全局异常处理
│   ├── api/                      # 跨服务 DTO 契约
│   ├── services/                 # 8 个可独立启动的 Spring Boot 服务
│   └── deploy/                   # SQL 迁移脚本、docker-compose、Nacos 配置
└── frontend/                    # Vue 3 SPA
```

## 快速开始

```bash
# 后端：编译全部模块（需 JDK 17+ 与 Maven 3.8+）
cd backend && mvn -q -DskipTests package

# 后端：启动网关（其余服务按需单独启动）
mvn -pl services/gateway spring-boot:run

# 前端：安装依赖并启动（需 Node 20+）
cd frontend && npm install && npm run dev
```

详细启动顺序、端口分配与配置说明见 [docs/development-guide.md](docs/development-guide.md)。

## 三条设计基线

1. **跨年只比位次，不比分数** —— 一切历史数据经「一分一段表」换算为省排名后比较。
2. **志愿规则配置化** —— 省份 × 年份 × 考试模式（传统文理 / 院校专业组 / 专业+院校）由规则表 + 策略模式驱动，禁止硬编码。
3. **数据版本化 + 人工审核** —— 采集数据留证、校验、复核后才能发布，输出「匹配度 / 风险等级」而非「录取率」。

## 文档索引

| 文档 | 内容 |
|---|---|
| [架构设计](docs/architecture-design.md) | 功能全景、微服务划分、采集治理、匹配算法、前后端结构、风险 |
| [数据模型](docs/data-model.md) | 核心表 DDL、索引、量级估算 |
| [接口说明](docs/api-reference.md) | REST 端点、请求/响应约定 |
| [开发指南](docs/development-guide.md) | 环境、构建、端口、启动顺序、常见问题 |
| [数据合规](docs/data-compliance.md) | 数据源分级、采集流水线、合规底线 |
| [路线图](docs/roadmap.md) | P0 - P3 交付范围 |

## License

[MIT](LICENSE) © 2026 LMD0924
