# 开发指南

## 运行形态

后端是**模块化单体**：`back-end-spring-cloud` 下 11 个模块全部平铺，其中 10 个是普通 library jar（没有 `main`、没有自己的 `application.yml`），只有 `launcher` 是唯一可执行的 Spring Boot 应用。

```
launcher (唯一 AdmitAtlasApplication, 8080)
  ├── common-core   ├── api           ├── gateway
  ├── auth-user     ├── school        ├── score
  ├── policy-rule   ├── match-engine  ├── plan-report
  └── data-collector
```

所有 Controller 在同一个 Spring MVC 上下文里，前端只访问 `8080`；`gateway` 模块在 P0 承载 CORS 等统一 Web 配置，不再启动反向代理网关。Spring Cloud / Nacos 依赖保留在 BOM 与 launcher 中，但默认关闭，不启动 Nacos 也能跑。

## 环境要求

| 工具 | 版本 | 说明 |
|---|---|---|
| JDK | 17+（已在 JDK 21 / 24 下验证） | 编译目标 `release 17` |
| Maven | 3.8+ | 父 POM 统一仲裁依赖版本 |
| Node.js | 20+（已在 22 下验证） | Vite 5 |
| MySQL | 8.0+ | 按年分区、`utf8mb4` |
| Redis | 7+ | 缓存一分一段表与候选池 |
| Nacos | 2.2+ | 可选：P0 默认关闭 |
| Docker | 可选 | `back-end-spring-cloud/deploy/docker-compose.yml` 一键起基础设施 |

## 构建

```bash
# 后端：全量编译（父 POM + 11 个模块），只有 launcher 生成可执行 jar
cd back-end-spring-cloud
mvn -B -DskipTests clean package

# 只编译某模块及其依赖
mvn -B -DskipTests -pl match-engine -am package

# 前端
cd ../front-end
npm install
npm run dev        # 开发服务器 http://localhost:5173
npm run build      # 产物 front-end/dist
npm run preview    # 预览构建产物
```

## 端口

| 端口 | 用途 | 说明 |
|---|---|---|
| 8080 | 后端统一入口 | 唯一进程，所有 `/api/**` 与健康检查都在这里 |
| 5173 | 前端 | Vite 开发服务器，代理 `/api` → `http://localhost:8080` |
| 8848 | Nacos | 可选，P0 不启用 |

原来的 8081~8087 各自端口已取消；模块间调用改为进程内方法调用。

## 启动与验证

```bash
# 1. 启动唯一应用
cd back-end-spring-cloud
java -jar launcher/target/admitatlas-launcher-0.1.0-SNAPSHOT.jar

# 2. 验证（另一个终端）
curl http://localhost:8080/actuator/health
curl http://localhost:8080/api/v1/system/modules      # 应列出 8 个模块
curl http://localhost:8080/api/v1/match/strategies   # 应返回三种考试模式
curl -X POST -H "Content-Type: application/json" \
  -d '{"score":{"provinceCode":"42","examYear":2026,"examMode":"GROUP_3_1_2","category":"physics","totalScore":612}}' \
  http://localhost:8080/api/v1/match/analyze          # P0 返回 501 占位
```

`/api/v1/system/modules` 用于确认唯一启动类的组件扫描覆盖了所有平铺模块；`/api/v1/match/strategies` 用于确认策略接口、策略工厂与统一响应链路正常。

## 配置说明

- **唯一配置文件**：`back-end-spring-cloud/launcher/src/main/resources/application.yml`。要改端口、应用名、actuator 暴露项都改这一处。
- `spring.cloud.nacos.discovery.enabled: false`、`config.enabled: false`。接入注册中心时改为 `true` 并启动 Nacos。
- 敏感配置（数据库密码、MinIO 密钥、JWT 密钥）一律走环境变量或配置中心，**禁止硬编码**；本地可新建 `application-local.yml`（已在 `.gitignore` 中忽略）。
- 数据库初始化：执行 `back-end-spring-cloud/sql/V1__init.sql`，或由 docker-compose 挂载自动执行。

## 从单体拆成微服务（P3）

1. 给目标模块补 `XxxApplication` 启动类与 `application.yml`（端口、应用名）。
2. 在该模块 `pom.xml` 中重新应用 `spring-boot-maven-plugin`，从 `launcher` 的依赖里移除它。
3. 把 `gateway` 模块升级为真正的 Spring Cloud Gateway（WebFlux），配置 `lb://admitatlas-xxx` 路由。
4. 模块间调用改为 `api` 模块中声明的 Feign 契约；业务代码与 `MatchStrategy` 实现无需改动。

## 常见问题

| 现象 | 原因与处理 |
|---|---|
| `mvn package` 报找不到 main class | 正常：只有 `launcher` 声明了 `spring-boot-maven-plugin`，其余模块是普通 jar。请从 `launcher/target/` 启动 |
| 新增模块没被扫描到 | 确认包名在 `com.admitatlas` 下，且 `launcher/pom.xml` 已声明该模块依赖；用 `/api/v1/system/modules` 复核 |
| 启动时反复连 Nacos 报错 | 确认 `spring.cloud.nacos.discovery.enabled: false`；或启动 Nacos 后改为 `true` |
| 访问不存在的路径返回 500 | 全局兜底异常处理器只捕获 `RuntimeException`，404 由 Spring 默认处理；若自定义了 advice 不要覆盖 checked `ServletException` |
| 前端请求 404 | 确认 Vite `proxy` 指向 8080，且后端已启动 |
| 中文乱码 | 数据库使用 `utf8mb4`，Maven 与前端文件统一 UTF-8 |
| `npm install` 返回 403 | 部分环境默认镜像为 `http://registry.npmmirror.com`（HTTP 可能被拦截），改用 `npm install --registry=https://registry.npmjs.org` |
| 端口被环境变量覆盖 | 沙箱可能注入 `SERVER_PORT`，启动需显式覆盖：`java -jar ... --server.port=8080`；普通环境直接用 yml 即可 |

## 代码规范

- 后端包名必须合法（模块名带连字符时，Java 包名去掉连字符，如 `policy-rule` → `com.admitatlas.policyrule`）。
- 所有 Controller 返回 `Result<T>`，业务异常抛 `BizException`。
- 新增业务模块时：在 `back-end-spring-cloud/` 下建平铺目录 → 父 POM 加 `<module>` 与版本仲裁 → 实现 `ModuleProbe` → `launcher` 加依赖。
- 新增省份规则只改数据（`rule_policy`），不改代码；新增策略实现 `MatchStrategy` 接口并注册为 Spring Bean。
