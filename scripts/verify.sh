#!/usr/bin/env bash
# =============================================================================
# 一键本地验收 —— 本项目「算不算做完」的唯一机械判据
#
# 一次跑完：后端构建 → 起后端 → 两组 itest → 契约对拍 → 前端类型检查 → 前端构建 → 汇总表
#
# 用法（在仓库根目录执行）：
#   bash scripts/verify.sh                  # 全量
#   bash scripts/verify.sh --no-build       # 跳过 mvn package（复用已有 jar）
#   bash scripts/verify.sh --no-frontend    # 只验后端
#   bash scripts/verify.sh --only=itest_rps # 只跑一个 itest
#   bash scripts/verify.sh --reuse          # 8080 已有服务则复用（默认拒绝）
#
# 前置：MySQL 8 在 127.0.0.1:3306（库 couple_meal，root/123456）；
#       java / mvn / node / curl / mysql / openssl / timeout 在 PATH。
# 日志：backend/target/verify/（已 gitignore）
#
# 为什么必须用本脚本，而不是手动一条条跑（都踩过）：
#   1. JVM 会被环境注入 server.port=57230，直接 java -jar 会绑错端口 → 这里强制 --server.port；
#   2. 后台进程在「当前 bash 调用结束」时会被回收 → 起服务 / 等健康 / 跑测试必须同一次调用内完成；
#   3. 8080 上残留旧进程会让 itest 跑到旧 class，整轮假失败 → 默认拒绝复用，强制重起；
#   4. 定时任务类用例（提醒 / 审核兜底）需要把 cron 调成高频、超时阈值压到 1 分钟，
#      用默认配置跑必挂 → 故分两阶段起两次后端，见 PHASE_B_ARGS。
# =============================================================================
set -u -o pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PORT="${PORT:-8080}"
BASE="http://127.0.0.1:$PORT/api"
OUT="$ROOT/backend/target/verify"
JAR_GLOB="$ROOT/backend/target/couple-meal-*.jar"
ITEST_TIMEOUT="${ITEST_TIMEOUT:-420}"

SKIP_BUILD=0
SKIP_FE=0
REUSE=0
ONLY=""

for a in "$@"; do
  case "$a" in
    --no-build) SKIP_BUILD=1 ;;
    --no-frontend) SKIP_FE=1 ;;
    --reuse) REUSE=1 ;;
    --only=*) ONLY="${a#--only=}" ;;
    -h|--help) sed -n '3,22p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "未知参数：$a（-h 看用法）"; exit 2 ;;
  esac
done

mkdir -p "$OUT"
SECONDS=0

# 阶段 A：默认配置。阶段 B：定时任务需要高频 cron + 更短超时阈值，否则要等 11:00 / 17:00 / 03:50
PHASE_A=("itest.sh:主链路" "itest_dinner.sh:晚市餐段" "itest_rps.sh:石头剪刀布" "itest_subscribe.sh:订阅消息")
PHASE_B=("itest_remind.sh:定时提醒" "itest_audit.sh:图片审核与清理")
PHASE_B_ARGS=(
  "--couple-meal.schedule.daily-remind-cron=*/5 * * * * *"
  "--couple-meal.schedule.daily-remind-dinner-cron=*/5 * * * * *"
  "--couple-meal.schedule.audit-pending-expire-cron=*/5 * * * * *"
  "--couple-meal.audit.pending-timeout-minutes=1"
)

RES_NAMES=(); RES_STATUS=(); RES_NOTE=()
record() { RES_NAMES+=("$1"); RES_STATUS+=("$2"); RES_NOTE+=("$3"); }
hr() { printf '%s\n' "────────────────────────────────────────────────────────────"; }

# ---------- 0. 环境预检 ----------
hr; echo "[0/6] 环境预检"
MISSING=""
for c in java mvn node curl mysql openssl timeout; do
  command -v "$c" >/dev/null 2>&1 || MISSING="$MISSING $c"
done
if [ -n "$MISSING" ]; then echo "  ✗ 缺少命令：$MISSING"; exit 3; fi
echo "  ✓ java $(java -version 2>&1 | head -1 | sed 's/.*version "\([^"]*\)".*/\1/')  node $(node -v)"
if ! mysql -h 127.0.0.1 -P 3306 -u root -p123456 -e "SELECT 1" couple_meal >/dev/null 2>&1; then
  echo "  ✗ MySQL 连不上（期望 127.0.0.1:3306 / couple_meal / root:123456）"; exit 3
fi
echo "  ✓ MySQL couple_meal 可连"

# ---------- 1. 后端构建 ----------
hr; echo "[1/6] 后端构建"
if [ "$SKIP_BUILD" = "1" ]; then
  echo "  跳过（--no-build）"
  record "后端构建" "SKIP" "--no-build"
elif ( cd "$ROOT/backend" && mvn -q -DskipTests package ) > "$OUT/build.log" 2>&1; then
  echo "  ✓ mvn package 通过"
  record "后端构建" "PASS" ""
else
  echo "  ✗ mvn package 失败，见 $OUT/build.log"
  tail -25 "$OUT/build.log"
  record "后端构建" "FAIL" "见 build.log"
fi
JAR="$(ls -1 $JAR_GLOB 2>/dev/null | head -1)"
[ -z "$JAR" ] && { echo "  ✗ 找不到 jar：$JAR_GLOB"; exit 3; }

# ---------- 后端进程管理 ----------
BPID=""
stop_backend() {
  [ -z "$BPID" ] && return 0
  kill "$BPID" 2>/dev/null
  for _ in 1 2 3 4 5 6 7 8 9 10; do kill -0 "$BPID" 2>/dev/null || break; sleep 0.5; done
  kill -9 "$BPID" 2>/dev/null
  BPID=""
  for _ in 1 2 3 4 5 6 7 8 9 10; do port_busy || break; sleep 0.5; done
}
trap 'stop_backend' EXIT INT TERM

port_busy() { [ "$(curl -s -o /dev/null -m 2 --noproxy '*' -w '%{http_code}' "$BASE/auth/me" 2>/dev/null)" != "000" ]; }

# $1 = 日志文件；其余 = 额外的 --key=value 覆盖
start_backend() {
  local log="$1"; shift
  ( cd "$ROOT/backend" && exec java -jar "$JAR" --server.port="$PORT" "$@" ) > "$log" 2>&1 &
  BPID=$!
  local i
  for i in $(seq 1 60); do
    port_busy && return 0
    kill -0 "$BPID" 2>/dev/null || return 1
    sleep 2
  done
  return 1
}

# ---------- itest 执行器 ----------
run_itest() {
  local script="$1" label="$2"
  local log="$OUT/${script%.sh}.log"
  ( cd "$ROOT/backend" && timeout "$ITEST_TIMEOUT" bash "itest/$script" ) > "$log" 2>&1
  local rc=$?
  local stat; stat=$(grep -oE 'PASS=[0-9]+ FAIL=[0-9]+' "$log" | tail -1)
  local p=${stat#PASS=}; p=${p%% *}; local f=${stat#*FAIL=}
  if [ "$rc" = "124" ]; then
    echo "  ✗ $label：超时 ${ITEST_TIMEOUT}s 被终止，最后进展："
    grep -vE '^\s*$' "$log" | tail -4 | sed 's/^/      /'
    record "$label" "FAIL" "超时终止"
  elif [ -z "$stat" ]; then
    echo "  ✗ $label：无统计输出（rc=$rc），日志尾部："
    grep -vE '^\s*$' "$log" | tail -6 | sed 's/^/      /'
    record "$label" "FAIL" "无统计输出"
  elif [ "${f:-1}" != "0" ]; then
    echo "  ✗ $label：PASS=$p FAIL=$f"
    grep -E 'FAIL' "$log" | sed 's/^/      /' | head -10
    record "$label" "FAIL" "PASS=$p FAIL=$f"
  else
    echo "  ✓ $label：PASS=$p FAIL=0"
    record "$label" "PASS" "$p 项"
  fi
}

want() { # 当前 --only 是否包含该脚本
  [ -z "$ONLY" ] && return 0
  case "$ONLY" in "$1"|"${1%.sh}") return 0 ;; *) return 1 ;; esac
}

# ---------- 2. 阶段 A ----------
hr; echo "[2/6] 启动后端 :$PORT（默认配置）"
if port_busy; then
  if [ "$REUSE" = "1" ]; then
    echo "  复用 :$PORT 上已有服务（--reuse）"
    record "启动后端(默认)" "REUSE" "复用已有进程"
  else
    echo "  ✗ :$PORT 已有服务在响应。"
    echo "    残留进程会让 itest 跑到旧 class（曾因此整轮假失败）。"
    echo "    先停掉它，或加 --reuse 明确复用。"
    exit 4
  fi
elif start_backend "$OUT/backend.log"; then
  echo "  ✓ 后端就绪（${SECONDS}s 内）"
  record "启动后端(默认)" "PASS" ""
else
  echo "  ✗ 后端未就绪，日志尾部："; tail -30 "$OUT/backend.log"
  record "启动后端(默认)" "FAIL" "见 backend.log"
  hr; echo "后端没起来，后续全部跳过。"; exit 1
fi

hr; echo "[3/6] 阶段 A · 业务链路 itest"
ANYA=0
for entry in "${PHASE_A[@]}"; do
  s="${entry%%:*}"; l="${entry#*:}"
  want "$s" || continue
  ANYA=1; run_itest "$s" "$l"
done
[ "$ANYA" = "0" ] && echo "  （--only 指定的是阶段 B，跳过）"

# ---------- 3. 阶段 B（需高频 cron） ----------
NEED_B=0
for entry in "${PHASE_B[@]}"; do want "${entry%%:*}" && NEED_B=1; done
if [ "$NEED_B" = "1" ]; then
  hr; echo "[3b] 重启后端（高频 cron + 审核超时 1 分钟）"
  if [ "$REUSE" = "1" ]; then
    echo "  ⚠ --reuse 下无法变更启动参数，阶段 B 用例大概率失败（提醒要等 11:00 / 17:00）"
    record "重启后端(cron)" "REUSE" "未变更参数"
  else
    stop_backend
    if start_backend "$OUT/backend-b.log" "${PHASE_B_ARGS[@]}"; then
      echo "  ✓ 后端已带测试 cron 重启"
      record "重启后端(cron)" "PASS" ""
    else
      echo "  ✗ 重启失败，日志尾部："; tail -30 "$OUT/backend-b.log"
      record "重启后端(cron)" "FAIL" "见 backend-b.log"
    fi
  fi
  for entry in "${PHASE_B[@]}"; do
    s="${entry%%:*}"; l="${entry#*:}"
    want "$s" || continue
    run_itest "$s" "$l"
  done
fi

# ---------- 4. 契约对拍 ----------
hr; echo "[4/6] 前后端契约对拍（scripts/contract-check.mjs）"
if node "$ROOT/scripts/contract-check.mjs" > "$OUT/contract.log" 2>&1; then
  cw=$(grep -oE '需人工确认 [0-9]+' "$OUT/contract.log" | grep -oE '[0-9]+')
  echo "  ✓ 路由与页面注册一致（需人工确认 ${cw:-0} 项）"
  grep -E '^  !' "$OUT/contract.log" | sed 's/^/    /' | head -12
  record "契约对拍" "PASS" "人工确认 ${cw:-0} 项"
else
  echo "  ✗ 存在不一致："
  grep -E '^  ✗' "$OUT/contract.log" | sed 's/^/    /'
  record "契约对拍" "FAIL" "见 contract.log"
fi

# ---------- 5/6. 前端 ----------
if [ "$SKIP_FE" = "1" ]; then
  hr; echo "[5/6] 前端：跳过（--no-frontend）"
  record "前端类型检查" "SKIP" "--no-frontend"
  record "前端构建" "SKIP" "--no-frontend"
else
  hr; echo "[5/6] 前端类型检查"
  if ( cd "$ROOT/couple-meal-miniprogram" && npm run type-check ) > "$OUT/type-check.log" 2>&1; then
    echo "  ✓ vue-tsc 无错误"; record "前端类型检查" "PASS" ""
  else
    echo "  ✗ vue-tsc 报错："
    grep -E 'error TS' "$OUT/type-check.log" | sed 's/^/    /' | head -10
    record "前端类型检查" "FAIL" "见 type-check.log"
  fi

  hr; echo "[6/6] 前端构建 mp-weixin"
  if ( cd "$ROOT/couple-meal-miniprogram" && npm run build:mp-weixin ) > "$OUT/build-fe.log" 2>&1; then
    echo "  ✓ 产物：couple-meal-miniprogram/dist/build/mp-weixin"; record "前端构建" "PASS" ""
  else
    echo "  ✗ 构建失败，日志尾部："; tail -15 "$OUT/build-fe.log" | sed 's/^/    /'
    record "前端构建" "FAIL" "见 build-fe.log"
  fi
fi

# ---------- 汇总 ----------
stop_backend
hr; echo "验收汇总（耗时 ${SECONDS}s）"; hr
FAILED=0; SKIPPED=0
for i in "${!RES_NAMES[@]}"; do
  st="${RES_STATUS[$i]}"
  case "$st" in
    PASS) mark="✓ PASS" ;;
    FAIL) mark="✗ FAIL"; FAILED=$((FAILED+1)) ;;
    SKIP) mark="- SKIP"; SKIPPED=$((SKIPPED+1)) ;;
    REUSE) mark="~ REUSE" ;;
    *) mark="$st" ;;
  esac
  printf '  %-8s %-18s %s\n' "$mark" "${RES_NAMES[$i]}" "${RES_NOTE[$i]}"
done
hr
if [ "$FAILED" -gt 0 ]; then
  echo "结论：不通过（$FAILED 项失败）。"
else
  echo "结论：机械层全绿（跳过 $SKIPPED 项）。"
fi
echo "本脚本只证明「后端接口行为 + 前端可编译」，**不证明真机可跑**。"
echo "仍需真机验证：登录换 openid、打卡拍照上传、订阅消息授权弹窗、审核回调真实触发（见 AGENT.md §10.2）。"
[ "$FAILED" -gt 0 ] && exit 1
exit 0
