# 路线图

## P0 · 骨架（当前阶段 ✅）

- [x] Maven 多模块工程 + 依赖版本仲裁（Spring Boot 3.2.5 / Spring Cloud 2023.0.1 / Alibaba 2023.0.1.0）
- [x] 外层项目根 + `back-end-spring-cloud`（11 个模块平铺）+ `front-end` + `docs` 的目录结构
- [x] 模块化单体：全后端唯一启动类、唯一 `application.yml`、唯一可执行 jar（8080）
- [x] 统一响应 / 错误码 / 全局异常处理 / 共享 DTO 契约 / 模块自检端点
- [x] 匹配策略接口与三种考试模式实现、策略工厂
- [x] Vue3 + Vite + TailwindCSS 前端骨架与主流程页面
- [x] 数据模型建表脚本、接口约定、开发指南、合规说明
- [ ] 单省 3 年录取数据人工整理（数据侧，需确认数据源）
- [ ] 位次换算接口实现

## P1 · 单省试点

- 接入 MySQL / Redis，`score` 服务实现 `convert-rank`、`segments`、`admission/scores`
- `school` 服务实现院校 / 专业档案检索，接入 Elasticsearch
- `policy-rule` 服务读取 `rule_policy`，输出本省志愿结构
- `match-engine` 实现位次换算 → 资格过滤 → 候选检索 → 风险打分 → 冲稳保分档
- `plan-report` 实现方案保存与 PDF / Excel 导出
- 前端主流程打通：成绩向导 → 匹配看板 → 志愿表编辑

## P2 · 数据流水线与全国规则

- `data-collector` 全流水线：调度、抓取、MinIO 留证、PDF/Excel 解析、质量校验、人工审核、版本发布
- 管理后台：采集任务监控、审核队列、规则配置编辑器、版本发布 / 回滚
- 规则配置化覆盖传统文理 / 院校专业组 / 专业+院校，支持 45 / 96 / 112 志愿结构
- 选科过滤（含 2024 起「物化绑定」）、单科门槛、同分排序
- 专业录取分与招生计划入库

## P3 · 提质与拆分

- 风险模型调优（三年波动、计划数变化、大小年修正）
- 候选池预计算 + Redis 缓存 + 匹配异步化，压测与水平扩容
- 数据版本灰度、质量报告（覆盖率 / 缺失率 / 异常率）
- 接入 Sentinel 限流、SkyWalking 链路追踪、Prometheus / Grafana 监控
- `search` / `job-center` 视负载拆为独立服务；引入 Spring Cloud OpenFeign 契约

## 明确不在当前范围

- 真实录取概率预测与「保录取」承诺（合规风险）
- 商业付费报告、专家复核、志愿代填报
- 未经授权的第三方数据采购与转售
