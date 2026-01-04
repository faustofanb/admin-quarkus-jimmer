# Observable (日志、指标、链路追踪) 使用手册

> **目标**：为 Quarkus 项目提供统一、可配置、可扩展的可观测性能力，包括 **HTTP 日志、SQL 日志、业务日志、指标、链路追踪**。本手册覆盖从配置到代码使用的完整流程，帮助开发者快速上手并在生产环境中获得高质量的可观测数据。

---

## 📦 目录

1. [前置条件](#前置条件)
2. [全局配置 (`application.yaml`)](#全局配置-applicationyaml)
3. [自动 HTTP 日志](#自动-http-日志)
4. [手动记录 HTTP/业务日志](#手动记录-http业务日志)
5. [自动 SQL 日志](#自动-sql-日志)
6. [业务日志、指标、链路追踪 (Facade)](#业务日志指标链路追踪-facade)
7. [常见问题 & 排查指南](#常见问题--排查指南)
8. [完整示例项目结构](#完整示例项目结构)
9. [最佳实践 & 小贴士](#最佳实践--小贴士)

---

## 1️⃣ 前置条件

| 条件 | 说明 |
|------|------|
| **Quarkus 版本** | `>= 3.8.0`（已集成 SmallRye Config、Micrometer、OpenTelemetry） |
| **Jimmer** | 已在项目中使用 `JSqlClient`（本手册的 SQL 自动日志基于 Jimmer） |
| **Micrometer** | 已在 `pom.xml` 中加入 `micrometer-registry-prometheus`（或其他后端） |
| **OpenTelemetry** | 若需要链路追踪，请在 `pom.xml` 中加入 `quarkus-opentelemetry`（本手册默认关闭） |

> **提示**：所有依赖已在项目的 `pom.xml` 中声明，无需额外操作。

---

## 2️⃣ 全局配置 (`application.yaml`)

将以下块粘贴到 `src/main/resources/application.yaml` **`admin.observable`** 前缀下（已在项目中实现）。

```yaml
admin:
  observable:
    # ---------- 开关 ----------
    enabled: true                     # 总开关，关闭后所有可观测功能失效

    # ---------- 日志 ----------
    log:
      enabled: true                   # 日志总开关
      http:
        enabled: true                 # HTTP 请求/响应日志
        log-headers: true            # 记录 Header（敏感 Header 会被脱敏）
        log-body: true               # 记录请求/响应体（大文件会被截断）
        max-body-length: 2000        # Body 最大打印长度（字符）
        sensitive-headers: Authorization,Cookie,Set-Cookie
        exclude-patterns: ^/q/.*|^/swagger.*   # 正则，匹配的 URI 不记录
        slow-threshold-ms: 3000      # 超过该阈值标记为 SLOW
        pretty-print: true           # 多行美化（默认开启）
      sql:
        enabled: true                 # SQL 日志总开关
        pretty-print: true           # 多行美化
        log-parameters: true         # 记录绑定参数
        log-row-count: true          # 记录受影响行数
        max-length: 5000             # SQL 最大打印长度（字符）
        slow-threshold-ms: 1000      # 慢查询阈值（毫秒）

    # ---------- 指标 ----------
    metrics:
      enabled: true                   # Micrometer 指标开关

    # ---------- 链路追踪 ----------
    trace:
      enabled: true                   # OpenTelemetry 链路追踪开关
      sample-rate: 0.1                # 采样率 0.0~1.0
```

> **注意**：`ObservableConfig` 使用 `@ConfigMapping(prefix = "admin.observable")`，因此所有配置必须位于 `admin.observable` 节点下。

---

## 3️⃣ 自动 HTTP 日志

### 3.1 实现原理
* `HttpLoggingFilter`（`io.github.faustofan.admin.shared.observable.logging.HttpLoggingFilter`）被标记为 `@Provider`，Quarkus 会在启动时自动注册为 JAX‑RS 过滤器。
* 过滤器在 **请求入口** 捕获请求信息并写入 MDC，**响应返回** 时收集响应信息并调用 `ObservableFacade.logHttpResponse`。
* 通过 MDC，所有后续日志（业务日志、异常日志）会自动携带 **traceId / spanId**，实现链路关联。

### 3.2 开启方式
只要 `admin.observable.log.http.enabled: true`（默认已开启），无需额外代码。

### 3.3 手动记录（特殊场景）
```java
@Inject ObservableFacade observableFacade;

// 手动构造请求信息（例如在过滤器之外的异步任务）
HttpLogFormatter.HttpRequestInfo requestInfo = HttpLogFormatter.HttpRequestInfo.builder()
        .method("POST")
        .uri("/api/v1/orders")
        .clientIp("10.1.2.3")
        .headers(Map.of("User-Agent", List.of("curl/7.68.0")))
        .body("{\"orderId\":123}")
        .startTime(System.currentTimeMillis())
        .build();
observableFacade.logHttpRequest(requestInfo);
```
响应同理使用 `logHttpResponse`。

---

## 4️⃣ 手动记录业务日志、指标、链路追踪

| 功能 | Facade 方法 | 示例 |
|------|-------------|------|
| **业务日志** | `logBusiness(String module, String operation, LogLevel level, String message)` | `observableFacade.logBusiness("User", "Create", LogLevel.INFO, "User created");` |
| **计数器** | `incrementCounter(String metricName, Map<String, String> tags)` | `observableFacade.incrementCounter(ObservableConstants.HttpMetric.REQUESTS_TOTAL, Map.of("method", "GET"));` |
| **计时器** | `recordTimer(String metricName, long durationMs, Map<String, String> tags)` | `observableFacade.recordTimer(ObservableConstants.HttpMetric.REQUEST_DURATION, duration, Map.of("uri", "/login"));` |
| **直方图** | `recordHistogram(String metricName, double value, Map<String, String> tags)` | `observableFacade.recordHistogram(ObservableConstants.SqlMetric.QUERY_DURATION, durationMs, Map.of("sql", "SELECT"));` |
| **链路追踪** | `startTrace() / endTrace()` | `String traceId = observableFacade.startTrace(); … observableFacade.endTrace();` |
| **子 Span** | `startSpan(String spanName) / endSpan()` | `String spanId = observableFacade.startSpan("db-query"); … observableFacade.endSpan();` |

---

## 5️⃣ 自动 SQL 日志

### 5.1 实现原理
* `SqlLoggingInterceptor`（`io.github.faustofan.admin.shared.observable.logging.SqlLoggingInterceptor`）实现 `org.babyfish.jimmer.sql.runtime.SqlLogger`。
* 在 Jimmer `JSqlClient` 构建时通过 `setSqlLogger(sqlLoggingInterceptor)` 注入。
* 每条 SQL 执行结束后，拦截器收集 **SQL 文本、绑定参数、执行耗时、受影响行数、错误信息**，并调用 `ObservableFacade.logSql`。

### 5.2 注册方式（Quarkus CDI）
在项目的 CDI 配置类中提供 `JSqlClient` Bean（如果已有，请在构建链中加入 `setSqlLogger`）：

```java
package io.github.faustofan.admin.config;

import io.github.faustofan.admin.shared.observable.logging.SqlLoggingInterceptor;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.babyfish.jimmer.sql.JSqlClient;

@ApplicationScoped
public class JimmerConfig {

    @Inject
    SqlLoggingInterceptor sqlLoggingInterceptor;

    @Produces
    public JSqlClient jSqlClient() {
        return JSqlClient.newBuilder()
                .setSqlLogger(sqlLoggingInterceptor) // 自动日志拦截
                .build();
    }
}
```
> **如果项目已经有 `JSqlClient` Bean**，只需在构建时调用 `setSqlLogger(sqlLoggingInterceptor)` 即可。

### 5.3 示例日志输出
```
│ 🗄️ SQL Query
│ • TraceId: 8f3c2a1b9e7d4c6f
│ • Duration: 18ms
│ ├────────────────────
│ SQL:
│   SELECT
│       u.id,
│       u.username,
│       u.email
│   FROM
│       system_user u
│   WHERE
│       u.active = ?
│ ├────────────────────
│ Parameters: [true]
│ Rows affected: 5
│ └────────────────────
```

---

## 6️⃣ 业务日志、指标、链路追踪 (Facade) 详细说明

### 6.1 `ObservableFacade` 概览
```java
@ApplicationScoped
public class ObservableFacade {
    private final BusinessLogger businessLogger;
    private final HttpLogFormatter httpLogFormatter;
    private final SqlLogFormatter sqlLogFormatter;
    private final MetricsRecorder metricsRecorder;
    private final TraceContext traceContext;
    // … 方法略 …
}
```
* **职责单一**：对外统一入口，内部委派给对应实现。
* **可替换**：若后续需要切换日志实现，只需替换 `ObservableFacade` 中的成员实现即可。

### 6.2 常用调用示例
```java
@Inject ObservableFacade observableFacade;

// 业务日志
observableFacade.logBusiness("Order", "Create", LogLevel.INFO, "Order 123 created");

// HTTP 日志（手动）
observableFacade.logHttpRequest(requestInfo);
observableFacade.logHttpResponse(responseInfo);

// SQL 日志（手动）
observableFacade.logSql(sqlInfo);

// 指标
observableFacade.incrementCounter("http.requests.total", Map.of("method", "GET"));
observableFacade.recordTimer("http.request.duration", 120, Map.of("uri", "/login"));

// 链路追踪
String traceId = observableFacade.startTrace();
String spanId = observableFacade.startSpan("db-query");
// …业务代码…
observableFacade.endSpan();
observableFacade.endTrace();
```

---

## 7️⃣ 常见问题 & 排查指南

| 场景 | 可能原因 | 解决方案 |
|------|----------|----------|
| **日志没有出现** | `admin.observable.enabled` 或对应子开关为 `false` | 确认 `application.yaml` 中 `enabled: true`，并检查 Quarkus 控制台日志级别（`INFO` 以上）。 |
| **SQL 被截断** | `max-length` 配置过小 | 增大 `admin.observable.log.sql.max-length`（如 `10000`）。 |
| **敏感 Header 未脱敏** | `sensitive-headers` 配置错误或拼写错误 | 确认 `Authorization,Cookie,Set-Cookie` 与实际 Header 名称匹配（大小写不敏感）。 |
| **链路追踪 ID 不一致** | 未在入口调用 `startTrace()` 或拦截器未生效 | 在入口（如过滤器、拦截器）调用 `observableFacade.startTrace()`，并确保 `admin.observable.trace.enabled: true`。 |
| **指标未上报到 Prometheus** | Micrometer 未绑定后端或 `metrics.enabled` 为 `false` | 添加 `quarkus-micrometer-registry-prometheus` 依赖，确认 `admin.observable.metrics.enabled: true`，访问 `/q/metrics` 检查指标。 |

---

## 8️⃣ 完整示例项目结构
```
src/main/java/io/github/faustofan/admin/shared/observable/
│   ObservableFacade.java
│   TraceContext.java
│   metrics/MetricsRecorder.java
│   logging/BusinessLogger.java
│   logging/HttpLogFormatter.java
│   logging/SqlLogFormatter.java
│   logging/HttpLoggingFilter.java   ← 自动 HTTP 日志
│   logging/SqlLoggingInterceptor.java ← 自动 SQL 日志
│   config/ObservableConfig.java
│   constants/* (LogLevel, LogCategory, MetricType, TraceStatus, ObservableConstants)
│
src/main/java/io/github/faustofan/admin/config/
│   JimmerConfig.java   ← 注册 SqlLoggingInterceptor
│
src/main/resources/application.yaml   ← 完整 Observable 配置块

docs/observable.md   ← 本使用手册
```

---

## 9️⃣ 最佳实践 & 小贴士

* **统一开启/关闭**：通过 `admin.observable.enabled` 控制所有可观测功能，便于在不同环境（dev / prod）快速切换。
* **慢查询阈值**：根据业务特性调优 `slow-threshold-ms`，避免大量正常查询被误标记为慢。
* **MDC 清理**：在异步线程或线程池任务结束后，调用 `TraceContext.clearMdc()` 防止上下文泄漏。
* **指标命名**：遵循 Prometheus 推荐的 `snake_case` 命名规则，统一使用 `ObservableConstants` 中的常量。
* **链路追踪采样**：生产环境建议将 `sample-rate` 调低（如 `0.05`），降低采样成本。
* **日志存储**：若使用 Loki/ELK，建议在日志格式中保留 `traceId`、`spanId`，便于后端关联查询。

---

> **至此**，您已拥有一套完整、可配置、可扩展的可观测性方案。只需在业务代码中注入 `ObservableFacade`，即可随时记录日志、指标与链路信息。祝开发顺利 🚀
