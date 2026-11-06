# 开发指南

## 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 17+（已在 JDK 21 / 24 下验证） | 编译目标 `release 17` |
| Maven | 3.8+ | 父 POM 统一仲裁依赖版本 |
| Node.js | 20+（已在 22 下验证） | Vite 5 |
| MySQL | 8.0+ | 按年分区、`utf8mb4` |
| Redis | 7+ | 缓存一分一段表与候选池 |
| Nacos | 2.2+ | 可选：P0 骨架默认关闭，不启动也能跑 |
| Docker | 可选 | `backend/deploy/docker-compose.yml` 一键起基础设施 |

## 构建

```bash
# 后端：全量编译（10 个模块）
cd backend
mvn -B -DskipTests clean package

# 只编译某模块及其依赖
mvn -B -DskipTests -pl services/match-engine -am package

# 前端
cd frontend
npm install
npm run dev        # 开发服务器 http://localhost:5173
npm run build      # 产物 frontend/dist
npm run preview    # 预览构建产物
```

## 端口分配

| 端口 | 服务 | 说明 |
|---|---|---|
| 8080 | gateway | 统一入口，前端只访问该端口 |
| 8081 | auth-user | 用户与成绩档案 |
| 8082 | school | 院校 / 专业档案 |
| 8083 | score | 投档线、一分一段、批次线 |
| 8084 | policy-rule | 志愿规则配置 |
| 8085 | match-engine | 匹配引擎（核心） |
| 8086 | plan-report | 方案与导出 |
| 8087 | data-collector | 离线采集，不挂网关 |
| 5173 | frontend | Vite 开发服务器 |
| 8848 | Nacos | 可选 |

## 启动顺序（P0 最小可验证链路）

```bash
# 1. 先起匹配引擎
cd backend && mvn -pl services/match-engine spring-boot:run

# 2. 再起网关（另一个终端）
cd backend && mvn -pl services/gateway spring-boot:run

# 3. 验证
curl http://localhost:8085/api/v1/match/strategies
curl http://localhost:8080/api/v1/match/strategies
curl http://localhost:8080/actuator/health
```

`GET /api/v1/match/strategies` 应返回三种考试模式，说明 Spring Cloud Gateway 路由、统一响应、策略工厂三条链路均正常。

## 配置说明

- 各服务 `application.yml` 中 `spring.cloud.nacos.discovery.enabled: false`。接入注册中心时改为 `true` 并启动 Nacos，网关路由的 `uri` 由 `http://localhost:PORT` 换成 `lb://admitatlas-xxx`。
- 敏感配置（数据库密码、MinIO 密钥、JWT 密钥）一律走环境变量或 Nacos 配置中心，**禁止硬编码**；本地可新建 `application-local.yml`（已在 `.gitignore` 中忽略）。
- 数据库初始化：执行 `backend/deploy/sql/V1__init.sql`，或由 docker-compose 挂载自动执行。

## 常见问题

| 现象 | 原因与处理 |
|---|---|
| Gateway 启动报 servlet 冲突 | gateway 不能引入 `spring-boot-starter-web`，只能用 `spring-cloud-starter-gateway`（WebFlux） |
| 启动时反复连 Nacos 报错 | 确认 `spring.cloud.nacos.discovery.enabled: false`；或启动 Nacos 后改为 `true` |
| `mvn package` 报找不到 main class | 只有 `services/*` 模块声明了 `spring-boot-maven-plugin`，`common-core` / `api` 是普通 jar |
| 前端请求 404 | 确认 Vite `proxy` 指向 8080，且对应后端服务已启动 |
| 中文乱码 | 数据库使用 `utf8mb4`，Maven 与前端文件统一 UTF-8 |
| `npm install` 返回 403 | 部分环境默认镜像为 `http://registry.npmmirror.com`（HTTP 可能被拦截），改用 `npm install --registry=https://registry.npmjs.org` |
| 服务启动时端口被占用 / 端口被环境变量覆盖 | 显式传参：`java -jar xxx.jar --server.port=8085`，命令行参数优先级高于环境变量与 yml |

## 代码规范

- 后端包名必须合法（模块名带连字符时，Java 包名去掉连字符，如 `policy-rule` → `com.admitatlas.policyrule`）。
- 所有 Controller 返回 `Result<T>`，业务异常抛 `BizException`。
- 新增省份规则只改数据（`rule_policy`），不改代码；新增策略实现 `MatchStrategy` 接口并注册为 Spring Bean。
