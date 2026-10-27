---
name: java-logging-standard
description: 指导 Agent 在 Java 编码与 Code Review 时遵循日志打印规范（SLF4J 占位符、异常堆栈+业务ID、日志级别、敏感字段脱敏、防自杀式日志、分支/入参出参埋点、结构化 event 字段、trace_id 规范、异步非阻塞原则、避免重复打日志）。仅包含"怎么打日志"的代码级规范，不含任何 logback/yml/拦截器等配置类内容。
version: 1.0.0
tags: [java, logging, slf4j, code-review, observability, standard]
agent_created: true
---

# Java 日志打印规范（Agent 编码版）

本 skill 只规定**写日志语句时该怎么做**，不沾任何配置（logback XML、yml 级别、AsyncAppender、MDC 拦截器、RollingPolicy、归档、additivity 等都不在此）。配置类由基础设施/运维负责，这里只立规范。

## 何时使用
- 写/改 Java 业务代码，需要新增或调整日志。
- Code Review 涉及日志正确性、性能、安全。
- 用户问："这里日志怎么打 / 异常怎么打 / 日志级别用什么 / review 下日志"。

## 核心心法（一句话）
日志 = 系统的可观测性。目标：出问题时**能用日志定位根因**，且日志**自己不打垮系统、不泄露敏感信息**。

---

## 规范清单（写每条日志时对照）

### 1. 用 SLF4J 门面，不直接用 Log4j/Logback 原生 API
```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
private static final Logger log = LoggerFactory.getLogger(OrderService.class);
```
门面模式，换底层实现不改业务代码。

### 2. 一律用 `{}` 占位符，禁止字符串拼接
```java
// ✅ 推荐：lazy 求值，level 关闭时不拼接、不执行 toString
log.info("order created, orderId={}, userId={}, amount={}", orderId, userId, amount);

// ❌ 禁止：eager 求值，即使 level 关闭也先拼接；对象还会触发 toString()
log.info("order created, orderId=" + orderId + ", userId=" + userId);

// ❌ 禁止：抛 RuntimeException 只是为了打日志
log.error("error: " + orderId, new RuntimeException("fake"));
```
低级别（debug/trace）若参数构造有成本，加开关：
```java
if (log.isDebugEnabled()) {
    log.debug("userId is: {}", user.getId());
}
```

### 3. 异常日志：完整堆栈 + 业务上下文（最重要）
```java
// ✅ 正确：最后一个参数传 Throwable，框架自动打完整堆栈
try {
    processOrder(order);
} catch (Exception e) {
    log.error("order creation failed, orderId={}, userId={}, amount={}",
              orderId, userId, amount, e);  // 📌 e 必须是最后一个参数
}
```
三条铁律：
1. **`e` 必须是最后一个参数**——SLF4J 签名里放中间会被当 `{}` 替换，堆栈丢失。
2. **异常日志至少带 ≥3 个业务 ID**（订单号/用户号/业务流水号），缺一不可。
3. **异常要么往上抛、要么打日志+降级，不能默默吞掉**，更不能用 `return` 假装没事。

反模式（全部禁止）：
```java
catch (Exception e) { log.error("order error"); }                 // ❌ 吞异常、无堆栈
catch (Exception e) { log.error("error: " + e.getMessage()); }   // ❌ 只打 message，堆栈全丢
catch (Exception e) { e.printStackTrace(); }                     // ❌ 写 System.err，无级别/时间/线程，生产丢失、可能卡请求
```

**不要"既打日志又抛原始异常再包装"**——会打印两份堆栈：
```java
// ❌ 反例：捕获处打一次，包装后在上层再打一次，双份堆栈
log.error("IO exception", e);
throw new MyException(e);
// ✅ 二选一：打日志就别抛（或抛自定义且不再打），包装后抛就别在捕获处打
```

### 4. 严禁"自杀式日志"
```java
// ❌ 自杀式：每次请求序列化整个订单（100+ 字段），高并发拖垮 CPU
log.info("order processed: {}", order.toString());

// ✅ 正确：只打关键字段
log.info("order processed: orderId={}, status={}, amount={}",
         order.getId(), order.getStatus(), order.getAmount());
```
- 循环里**不要每轮打** → 循环外聚合，或采样。
- 不触发序列化框架的反射调用（用专门 POJO/投影取字段）。
- 高并发路径（秒杀、支付回调）尤其敏感。

### 5. 敏感字段必须脱敏（合规红线）
**严禁明文打印**：密码 / 支付密码 / 短信验证码、身份证号 / 银行卡号 / CVV、用户 token / session / cookie、内部接口的 AK/SK。触犯《个保法》，安全审计会通报。
```java
// ✅ 脱敏后打
log.info("user login: userId={}, phone={}", userId, maskPhone(phone));
```
打日志前默认当作"会进日志系统、会被人看到"，敏感字段一律脱敏或跳过。

### 6. 日志级别用对，不要全用 INFO
| 级别 | 何时用 |
|------|--------|
| ERROR | 影响业务、需立即介入（下单/支付失败、外部依赖异常） |
| WARN  | 可恢复异常 / 接近阈值（重试后成功、QPS 接近限流、缓存命中低） |
| INFO  | 关键业务节点（订单创建、支付成功、登录登出、配置变更） |
| DEBUG | 排查细节（中间状态、SQL 参数） |
| TRACE | 框架内部 |

反模式：
- ❌ 全用 INFO（生产关不掉 DEBUG，磁盘爆炸）。
- ❌ 生产开 DEBUG（性能掉半、打满磁盘）。
- ❌ INFO 打心跳（每秒几十 MB）。

### 7. 关键事件打结构化日志（带 `event` 字段）
关键业务节点用结构化字段，便于 ELK/Loki/SLS 直接检索聚合（按 error_code 分组、按 region 统计失败数）。

业务代码里用 **key=value 格式**，不要手拼 JSON 字符串（容易漏转义，双引号/换行符等会让 JSON 直接炸掉）：
```java
// ✅ 推荐：key=value，日志平台自动解析
log.info("event=order_success orderId={} userId={} costMs={}",
         orderId, userId, costMs);
log.error("event=order_failed orderId={} userId={} costMs={} error={}",
          orderId, userId, costMs, e.getClass().getSimpleName(), e);

// ❌ 禁止：手拼 JSON，特殊字符会破坏格式
log.info("{\"event\":\"order_success\",\"order_id\":\"{}\",\"user_id\":\"{}\"}", ...);
```
建议字段：`event` / `orderId` / `userId` / `costMs` / `region` / `errorCode`。
JSON 序列化由 logback encoder 配置统一处理（属于配置类，不在本 skill），业务代码只负责打 key=value。

### 8. 全链路 Trace ID（规范层面）
规范：**每条日志都应携带 `trace_id`**（通过 MDC 透传），否则跨服务/跨线程无法关联一笔请求。
- 这是日志的规范要求，写代码时假设 `trace_id` 已存在于 MDC，日志 pattern 由配置负责带上。
- 跨线程（线程池 / `@Async` / `parallelStream`）MDC 会丢——这是基础设施需解决的透传问题，规范只要求"最终日志里有 trace_id"。

### 9. 分支首行 + 入参出参埋点
- **if/else/switch 分支首行打日志**，排查时一眼看出走了哪个分支：
```java
if (user.isVip()) {
    log.info("user is vip, userId={}, enter vip logic", user.getUserId());
} else {
    log.info("user is not vip, userId={}, enter normal logic", user.getUserId());
}
```
- **方法入参/出参打关键字段**（userId / bizSeq），不是越多越好：
```java
log.debug("method enter, userId={}", userId);
// ...
log.debug("method exit, resultId={}", resultId);
```
- 核心 / 逻辑复杂模块：打较完整日志，标准只有一个——**出错了能通过日志定位到**。

### 10. 避免重复 / 冗余日志
- 同一逻辑不要打多遍（如 if 块里连续两条同义日志）。
- 不要"既打日志又抛"（见第 3 条）。
- 规范层要求避免重复；`additivity=false` 等去重配置属于配置类，不在本 skill。

### 11. 异步 / 采样 / 归档（规范层面）
- 规范：日志输出**不应阻塞业务线程**（用异步 appender）；日志量要有**采样 / 清理 / 归档**策略，不能靠"运维会记得"。
- 具体实现（AsyncAppender 参数、RollingPolicy、OSS 归档）是配置，不属于本 skill。

### 12. 关键事件要被测（测试规范）
订单创建/支付/退款、登录登出、配置变更、外部依赖调用等关键事件**必须被测**，避免漏打导致出问题时两眼一抹黑。（用 ListAppender 捕获并断言属于测试手法，按需使用。）

---

## 速查表（Code Review 直接对照）

| 维度 | ❌ 反模式 | ✅ 最佳实践 |
|------|----------|-----------|
| 拼接方式 | `"orderId=" + id` | `log.info("orderId={}", id)` |
| 异常处理 | `catch { log.error("error"); }` | 最后一个参数传 `Throwable e` |
| 业务 ID | 没打 / 只打一个 | 至少 3 个核心 ID |
| 堆栈 | `e.printStackTrace()` / 只打 message | 完整堆栈（e 末参） |
| 大对象 | `log.info(obj.toString())` | 只打关键字段 |
| 敏感字段 | 明文手机/密码/token | 脱敏后打 / 跳过 |
| 循环里 | 每轮都打 | 聚合 / 采样 |
| 日志级别 | 全 INFO / 线上 DEBUG | 按语义选级 |
| 分支 | 无 | 首行打 |
| 重复 | 打又抛 / 同义重复 | 二选一、不冗余 |
| 链路 | 无 trace_id | MDC 透传 trace_id |
| 格式 | 纯文本流水账 | 结构化 `event` 字段 |
| 性能 | 同步阻塞 / 自杀式 | 异步非阻塞 + 采样 |
| 测试 | 不验证日志 | 关键事件被测 |

## 触发示例
用户说："这里加个日志" / "异常怎么打" / "日志级别用什么" / "帮我 review 下日志" → 加载本 skill，按上表核对其代码。
