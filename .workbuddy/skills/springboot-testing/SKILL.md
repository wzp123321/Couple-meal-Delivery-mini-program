---
name: springboot-testing
description: Spring Boot 3.x + JDK 21 测试落地规范（DDD + MyBatis-Plus + MySQL），基于历史支付中心工程实战沉淀，含测试库隔离、透明加密文件与构建解耦、Dubbo 契约测试等定制修正与已知缺口台账。适用于 DDD 工程编写或评审单元/集成测试。
---

# <目标工程> 测试落地规范（Spring Boot 3.x + JDK 21 + MyBatis-Plus + MySQL 8.0 + DDD）

基线：Spring Boot 3.x、JDK 21、MyBatis-Plus 3.5.x（Boot 3 用 `mybatis-plus-spring-boot3-starter`）、MySQL 8.0、JUnit 5、AssertJ、Testcontainers、Awaitility。**本文件是通用 springboot-testing 规范针对目标工程（`<目标工程>`，源自历史工程实战沉淀）的修正版，先读本工程定制节，再读通用规范。**

## ⚠️ 本工程定制修正（务必先读）

### C1. 测试库隔离（硬约束）
- 集成测试必须连**独立测试库**（`<独立测试库名>`，seed 见 `<目标工程>/db/test_seed.sql`），**禁止** `application-test.yml` 指向开发库 `<开发库名>`——测试基类 `<集成测试基类>` 的 `@BeforeEach` 会 `DELETE FROM <业务表>`，连错库会清真实业务数据。
- 数据策略：用例间清业务表、保留字典/商户/应用/渠道种子（test_seed.sql）；无外键问题，直接 DELETE 即可。

### C2. 透明加密文件与构建解耦（如 E-SafeNet；历史工程最大的坑）
- 历史参考工程中大量源码/测试曾是 E-SafeNet 类 LOCK 加密文件（NUL 字节）。**javac 无法编译加密源文件**；maven 增量编译一旦发现"源 mtime 比 class 新"就会硬啃加密文件，产出**损坏 class**（症状：所有依赖该文件的测试报 `No results for path: $['code']` 或"找不到符号"）。
- 约束：**加密文件必须保证 target 有对应预编译 class 且 mtime 不旧于源**；加密落地（并行 agent/流程）与构建解耦——编译用明文工作副本，加密只在提交时做。遇到"加密文件阻塞编译"时：临时隔离该文件（及其唯一引用方）验证其他改动，验证后原样恢复，不要覆盖加密文件。
- 解密工具：`<加密解锁工具路径>`（如 Unlock.exe + Code.exe 组合：Code.exe 扫描当前目录及子目录，递归调用同目录的 Unlock.exe 就地解密为明文 .java；加密流程会自动再加密明文文件）。**解密只为只读审查/临时验证，写回仓库需谨慎（会与并行加密流程冲突）。**

### C3. Dubbo 跨服务测试（本工程用 Dubbo，非 HTTP/Pact/WireMock）
- 跨服务契约：`<Xxx>DubboService`（api 层接口）即契约，出入参对齐 mall-saas 老 `<Xxx>FeignClient`（出参 `ServerResponseEntity<vo>`、内部支付/退款 secretPackage 为对象包裹不走 RSA）。
- 单侧行为：集成测试用 `@MockitoBean` mock 外部 Dubbo 端口（memberDubboService/prepaidCardDubboService）与基础设施端口（cryptoGateway 解密原样返回、distributedLockGateway 直通、redissonClient、NotifyLogRepository 隔离 BUG-01）。
- 不需要 Pact/WireMock/ComposeContainer（内部服务间用 Dubbo，无 HTTP 契约漂移问题）。

### C4. 本工程测试现状与已知缺口（历史工程参考，BUG 台账）
- 已有：领域层 `CenterOrderTest`/`CenterRefundOrderTest`（状态机纯单测）；路由契约 `AdminControllerContractTest`/`ExternalPayControllerContractTest`/回调契约测试（@WebMvcTest + @MockitoBean 门面）；渠道路由/行为 `ChannelRouterTest`/`CashDirectAdapterTest`/`RealAccountChannelAdapterTest`/`RealPrepaidCardAdapterTest`；契约约束 `SupportTypeConformanceTest`（所有 PayChannelAdapter.supportType 必须是 PayMethod_ChannelType 合法组合）+ `PayChannelKeyTest`；落库集成 `PaymentCreateIntegrationTest`/`PaymentRefundIntegrationTest`（双断言：HTTP 响应 + jdbcTemplate 查库）；架构守护 `ArchitectureTest`（ArchUnit 12 条）。
- **缺口（待补）**：应用层 `PayCmdExe`/`RefundCmdExe`/回调 Executor 单测（源码加密，黑盒覆盖）；事务失败回滚用例（退款失败→订单状态回退）；异步链路 Awaitility（NotifyRetryTask 定时重试最终态，需先解 BUG-01）。
- **本工程业务坑**：BUG-01 `NotifyLogRepositoryImpl` 插入缺 `request_body`（NOT NULL）回滚事务→测试 mock NotifyLogRepository 隔离；CSCANB_UNIPAY 双实现冲突（`UnipayCScanBAdapter` vs `PayChannelAdapters$UnipayCscanbAdapter`，ChannelRouterTest 固化）；payParams 对象化（setPayParams(String) 自动解析 JSON 文本）；`%X{traceId}` 日志 MDC。

### C5. 命名/结构建议
- 慢集成测试（连真库）建议命名 `XxxIT` + failsafe（`mvn verify`），快单测 `XxxTest` + surefire（`mvn test`）——当前全为 `XxxTest`，`mvn test` 依赖外部 MySQL 可用，改造需确认 CI 构建。
- 测试按功能分包（adapter.web / adapter.callback.web / domain.model / infra.gateway 等）而非 unit/it 分包，可接受。

## 通用规范（未修正部分，仍适用）

### 分层测试落位
| 层 | 测什么 | 工具 | 需要 Spring |
|---|---|---|---|
| domain | 聚合不变式、值对象、领域服务 | JUnit5+AssertJ 纯单测 | 否 |
| application | 用例编排、事务边界、调用顺序 | Mockito mock 端口 | 否 |
| infrastructure | Repository 端口契约、ORM 映射、SQL | Testcontainers/真库 | 是 |
| interfaces | HTTP 契约 + 落库双断言 | MockMvc/RestAssured | 是 |

### 高频坑（通用）
1. 领域测试不起 Spring（架构坏味道）。
2. `@Transactional` 是应用层职责；集成测试方法**禁止**标 `@Transactional`（会藏真实提交/回滚路径）。
3. 断言走领域 Repository 端口而非 Mapper；Mapper 级验证作补充（JdbcTemplate 数原始行兜底）。
4. `save → findById` 必须完整还原聚合图（根+子表），否则领域逻辑拿到半截聚合。
5. Boot 3 必须 `mybatis-plus-spring-boot3-starter`（Boot 2 坐标起不来）。
6. 不用 H2 糊弄（测不出 MySQL 方言差异：utf8mb4、ON DUPLICATE KEY）。
7. 逻辑删除只是置 deleted=1，行还在——JdbcTemplate 数原始行、断言 deleted 字段。
8. 固定端口会撞并行：RANDOM_PORT + @LocalServerPort。
9. `@Container` 必须 static（否则每用例起库，MySQL 启动慢）。
10. 测试数据用 ObjectMother/Builder 集中管理（本工程基类 payDto()/refundCommand() 即此模式）。
11. 接口测试坚持双断言（响应契约 + 落库状态），否则回滚/半截数据 bug 全漏。
12. 异步链路用 Awaitility 轮询最终状态（接口返回 ≠ 落库/消费完成）。
