# REST 接口约定

## 通用约定

- Base URL：`/api/v1`。P0 为模块化单体，后端只有 `8080` 一个端口（launcher 应用）；前端 Vite 代理 `/api` → `http://localhost:8080`。
- `GET /api/v1/system/modules` 为单应用自检端点，返回当前启动类扫描到的全部业务模块与运行端口。
- 所有响应统一包装：

```json
{ "code": 0, "message": "success", "data": {}, "timestamp": 1730000000000 }
```

- `code = 0` 表示成功；非 0 时 `data` 为 `null`，前端统一在 Axios 拦截器中提示 `message`。
- 分页参数：`page`（从 1 开始）、`size`（默认 20，最大 200）。
- 写接口需携带 `Authorization: Bearer <JWT>`（P1 接入）。
- **禁止**在任何响应中出现「录取率 / 保证录取」等承诺性字段，统一使用 `matchScore`（0~100 匹配度）与 `tier`（CHONG / WEN / BAO）。

## 错误码

| code | 含义 |
|---|---|
| 0 | success |
| 400 | 请求参数不合法 |
| 401 | 未登录或已过期 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 状态冲突 |
| 500 | 服务内部错误 |
| 501 | 能力尚未实现（P0 骨架占位接口返回此码） |
| 502 | 依赖服务调用失败 |
| 503 | 该省 / 年份数据尚未发布 |

## 端点清单

| 方法 | 路径 | 归属服务 | 状态 | 说明 |
|---|---|---|---|---|
| POST | `/api/v1/scores/convert-rank` | score | 计划 | 分数 → 位次，返回是否为估算值 |
| GET | `/api/v1/segments` | score | 计划 | 一分一段表（省 / 年 / 科类） |
| GET | `/api/v1/batch-lines` | score | 计划 | 批次线 |
| GET | `/api/v1/admission/scores` | score | 计划 | 院校 / 专业组历年投档线 |
| GET | `/api/v1/schools` | school | 计划 | 院校库检索（ES） |
| GET | `/api/v1/schools/{code}` | school | 计划 | 院校档案详情 |
| GET | `/api/v1/majors` | school | 计划 | 专业目录树 |
| GET | `/api/v1/rules/policy` | policy-rule | 计划 | 本省当年志愿结构与同分排序规则 |
| GET | `/api/v1/policies/{province}` | policy-rule | 计划 | 政策百科 |
| POST | `/api/v1/student/scores` | auth-user | 计划 | 保存成绩档案 |
| GET | `/api/v1/users/me` | auth-user | 计划 | 当前用户信息 |
| **GET** | `/api/v1/match/strategies` | match-engine | **已实现** | 返回已注册的考试模式 |
| **POST** | `/api/v1/match/analyze` | match-engine | **占位** | 校验参数 + 策略解析，算法 P1 实现 |
| GET | `/api/v1/match/tasks/{taskId}` | match-engine | 计划 | 异步匹配结果轮询 |
| POST | `/api/v1/plans` | plan-report | 计划 | 保存志愿方案 |
| GET | `/api/v1/plans/{id}/export` | plan-report | 计划 | 导出 PDF / Excel |
| POST | `/api/v1/admin/collect/tasks` | data-collector | 计划 | 管理端：创建采集任务 |
| POST | `/api/v1/admin/data-versions/publish` | data-collector | 计划 | 管理端：数据版本发布 |

## 主接口示例

### `POST /api/v1/match/analyze`

请求：

```json
{
  "score": {
    "provinceCode": "42",
    "examYear": 2026,
    "examMode": "GROUP_3_1_2",
    "category": "物理类",
    "subjectCombo": ["物理", "化学", "生物"],
    "totalScore": 612,
    "subjectScores": { "语文": 112, "数学": 126, "外语": 128, "物理": 92 }
  },
  "preference": {
    "regions": ["湖北", "江苏"],
    "majorKeywords": ["计算机", "电子信息"],
    "schoolTiers": ["985", "211"],
    "priority": "MAJOR_FIRST"
  }
}
```

P0 响应（占位）：

```json
{
  "code": 501,
  "message": "匹配算法待 P1 实现；策略解析成功: {examMode=GROUP_3_1_2, volunteerUnit=SCHOOL_GROUP, ...}",
  "data": null,
  "timestamp": 1730000000000
}
```

P1 响应 `data` 结构见 `back-end-spring-cloud/api/src/main/java/com/admitatlas/api/dto/MatchResultDTO.java`。
