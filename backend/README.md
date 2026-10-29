# 后端联调与集成自测指南

本文件说明如何在本地启动后端、跑通前后端联调，以及如何使用 `itest` 集成自测脚本。

## 1. 环境要求

| 组件 | 版本 | 说明 |
|------|------|------|
| JDK | 21 | 后端编译/运行 |
| Maven | 3.9.x | 仅用于解析依赖 classpath（可用离线 `-o`） |
| MySQL | 8.0 | 库名 `couple_meal`，账号 `root` / `123456`，地址 `127.0.0.1:3306` |
| bash | Git Bash 推荐 | 运行 `itest.sh`；Windows 下原生 `curl` 需走 Git Bash 以使用 `cygpath` |
| curl / openssl / mysql CLI | — | `itest.sh` 依赖 |

## 2. 数据库准备

若库尚未建立（首次或换机），先执行建库建表脚本：

```bash
mysql -h 127.0.0.1 -P 3306 -u root -p123456 < db/schema.sql
```

> 本机 MVP 开发库已就绪（12 张表、唯一索引齐全），跳过即可。

## 3. 启动后端

`mvn -o spring-boot:run` 在本机离线环境会因缺插件缓存失败，改用「先解析 classpath → 直接 `java -cp` 启动」：

```bash
cd backend

# ① 编译（如 target/classes 已是最新可跳过）
mvn -o compile

# ② 生成运行期 classpath（输出文件勿用 .txt 扩展名，会被透明加密）
mvn -o dependency:build-classpath -Dmdep.outputFile=/tmp/cp_noe

# ③ 启动（强制指定端口，避免偶发走随机端口）
java -cp "target/classes;$(cat /tmp/cp_noe)" \
     com.couplemeal.CoupleMealApplication --server.port=8080
```

启动后接口根路径为 `http://127.0.0.1:8080/api`，健康检查：

```bash
curl -s -o /dev/null -w "%{http_code}\n" \
     -H "Authorization: Bearer x" http://127.0.0.1:8080/api/checkin/gallery
# 期望 200
```

### 关键配置（`src/main/resources/application.yml`）

- `server.port` / `server.servlet.context-path`：`8080` / `/api`
- 数据源：`jdbc:mysql://127.0.0.1:3306/couple_meal`
- `couple-meal.jwt.secret`：鉴权密钥，**dev 默认值为占位密钥**；`itest.sh` 用同一默认值离线签测试 JWT。若改了密钥，需同步改 `itest.sh` 顶部 `SECRET` 变量。
- `couple-meal.upload.base-dir`：图片落盘目录（默认 `/data/upload`），需真实存在且可被 Nginx 静态映射（生产）。
- `couple-meal.plan.order-cutoff-hour`：当日约定发起截止小时（默认 `18`）。

## 4. 集成自测 `itest`

脚本位置：`backend/itest/itest.sh`。它模拟前端完整流程，覆盖 50 个用例（含正向 + 边界 + 权限隔离）。

### 原理

- 用 openssl HS256 **离线签 JWT**（仅含 `uid`），绕过真机微信 `code2Session` 登录。
- 直连 `http://127.0.0.1:8080/api` 驱动实时后端。
- 直接对 MySQL 造/清测试数据（用户 `smoke_a`/`smoke_b`、临时情侣关系）。
- 打卡上传用例使用提交的固件 `backend/itest/fixtures/test.png`（合法 PNG），不依赖运行时 python 生成。
- 临时并发文本写入 `backend/itest/.work/`（已在 `.gitignore` 忽略）。

### 运行

```bash
cd backend
bash itest/itest.sh          # 输出逐条 PASS/FAIL 与汇总
# 期望：============ 结果: PASS=50 FAIL=0 ============
```

### 前置

1. 后端已在 `8080` 启动（见第 3 节）。
2. MySQL 可达且 `couple_meal` 库就绪。
3. `SECRET` 变量与 `application.yml` 的 `couple-meal.jwt.secret` 一致（默认一致）。
4. Windows 下在 **Git Bash** 内运行（脚本内部用 `cygpath` 把固件路径转成 `D:/` 原生格式传给 `curl`；原生 CMD/PowerShell 的 `curl` 不认 `/d/...`）。

### 覆盖范围

情侣绑定（邀请码 6 位/24h/限频）、双来源约定全生命周期（认领/备餐/交付/完成/取消/撤回/改派）、状态机 + `actions[]`、乐观锁并发认领、边界校验（周末/+8 天/11 项/超长备注）、菜单 CRUD/软删/口味画像/智能推荐、消息按人隔离 + 已读清零、打卡全链路（上传→提交→修改→回应→相册可见性）、历史/周历/月报/年报/采购清单。

### 已知限制（dev 环境固有，非 bug）

- 微信出网调用（token / `imgSecCheck` / `msgSecCheck` / `mediaCheckAsync`）在 dev 凭证下会快速失败（已配 2s/3s 超时），图片照存、`trace_id` 留空走补偿任务；真实审核回调需真机 + 真实微信凭证。
- 不覆盖：微信登录 `code2Session`、真实内容审核回填、照片实际落地与 Nginx/HTTPS 交付、双号真机联调、小程序提审。

## 5. 前端联调前置

前端工程在 `../couple-meal-miniprogram`。本地联调时：

- `.env.development` 的 `VITE_API_BASE` 指向后端地址（默认占位域名，本地需改为 `http://127.0.0.1:8080` 或部署地址）。
- 请求路径自带 `/api`，与后端 context-path 一致。
- 错误码 `1001`（未登录）会静默重登重放，`1002`（无情侣关系）跳转绑定页——与后端一致。

## 6. 退出与清理

- 后端：在启动它的终端 `Ctrl+C`；后台托管进程用任务管理器结束 `com.couplemeal.CoupleMealApplication`。
- `itest.sh` 结束会自动清理自己造的测试数据；若中途中断，可重跑一次以触发清理分支。
