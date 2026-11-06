# Nacos 共享配置（P1 / 拆分微服务时启用）

P0 是模块化单体，唯一配置文件为 `back-end-spring-cloud/launcher/src/main/resources/application.yml`，其中：

```yaml
spring.cloud.nacos.discovery.enabled: false
spring.cloud.nacos.config.enabled: false
```

接入步骤：

1. 启动 `back-end-spring-cloud/deploy/docker-compose.yml` 中的 Nacos（默认 `http://127.0.0.1:8848/nacos`）。
2. 在 `launcher` 的 `application.yml` 中把上述两项改为 `true`，并添加 `spring.config.import: nacos:<dataId>`。
3. 拆分为独立微服务后，各服务自行持有配置；网关路由 `uri` 使用 `lb://admitatlas-xxx`。
4. 在 Nacos 中创建以下共享配置（按环境分组）：

| Data ID | 内容 |
|---|---|
| `admitatlas-datasource.yaml` | MySQL / Redis 连接（密码用环境变量占位） |
| `admitatlas-common.yaml` | JWT 密钥、MinIO 密钥、数据采集限频参数 |
| `admitatlas-match.yaml` | 冲稳保分档阈值、风险模型权重 |

敏感值一律通过环境变量注入，禁止写入本目录或提交到仓库。
