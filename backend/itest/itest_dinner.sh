#!/usr/bin/env bash
# 晚市(meal_type=2) 定向验证：独立于 itest.sh 的 50/50 主流程，验证晚饭全链路 + 按餐段截止。
# 用法：bash itest/itest_dinner.sh   （需后端在 8080 运行、MySQL 可达）
set -u
BASE="${BASE:-http://127.0.0.1:8080/api}"
SECRET="${COUPLE_JWT_SECRET:-dev-only-change-me-in-prod-32bytes-min}"
MYSQL="mysql -h 127.0.0.1 -P 3306 -u root -p123456 couple_meal"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TEST_PNG="$(cygpath -w -m "$SCRIPT_DIR/fixtures/test.png" 2>/dev/null || echo "$SCRIPT_DIR/fixtures/test.png")"

genjwt() {
  local uid=$1 iat now; now=$(date +%s); iat=$now; exp=$((iat+2592000))
  local hb pb sig
  hb=$(printf '{"alg":"HS256","typ":"JWT"}' | openssl base64 -A | tr '+/' '-_' | tr -d '=')
  pb=$(printf '{"sub":"%s","uid":%s,"iat":%s,"exp":%s}' "$uid" "$uid" "$iat" "$exp" | openssl base64 -A | tr '+/' '-_' | tr -d '=')
  sig=$(printf '%s.%s' "$hb" "$pb" | openssl dgst -sha256 -hmac "$SECRET" -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')
  printf '%s.%s.%s' "$hb" "$pb" "$sig"
}
jcall() {
  if [ "$1" = "GET" ]; then curl -s -m15 -H "Authorization: Bearer $3" "$BASE$2";
  else curl -s -m15 -H "Authorization: Bearer $3" -H "Content-Type: application/json" -X "$1" "$BASE$2" -d "$4"; fi
}
code_of() { echo "$1" | grep -o '"code":[0-9]*' | head -1 | grep -o '[0-9]*'; }
val() { echo "$2" | grep -o "\"$1\":[^,}]*" | head -1; }

PASS=0; FAIL=0
chk() {
  local desc="$1" exp="$2" got="$3"
  if [ "$exp" = "$got" ]; then echo "  PASS: $desc (=$got)"; PASS=$((PASS+1));
  else echo "  FAIL: $desc (期望 $exp 实得 $got)"; FAIL=$((FAIL+1)); fi
}

TA=$(genjwt 10011); TB=$(genjwt 10012)
TOM=$(date -d "+1 day" +%Y-%m-%d 2>/dev/null || date -v+1d +%Y-%m-%d)
TODAY=$(date +%Y-%m-%d)
HOUR=$(date +%H)
echo "===== 晚市定向验证 (TODAY=$TODAY HOUR=$HOUR TOM=$TOM) ====="
$MYSQL -e "DELETE FROM couple WHERE user_a IN (10011,10012) OR user_b IN (10011,10012); DELETE FROM user WHERE openid IN ('dinner_x','dinner_y');" 2>&1 | grep -v Warning
$MYSQL -e "INSERT INTO user (id,openid,nickname,create_time,update_time) VALUES (10011,'dinner_x','晚A',NOW(),NOW()),(10012,'dinner_y','晚B',NOW(),NOW());" 2>&1 | grep -v Warning
INV=$(jcall POST /couple/invite "$TA" '{}'); CODE=$(echo "$INV" | grep -o '"code":"[0-9]\{6\}"' | grep -o '[0-9]*')
jcall POST /couple/join "$TB" "{\"code\":\"$CODE\"}" >/dev/null

# 1) 创建晚饭约定(mealType=2)，从详情取 mealType
CR=$(jcall POST /plan/create "$TA" "{\"source\":0,\"mealType\":2,\"planDate\":\"$TOM\",\"items\":[{\"name\":\"宵夜\"}]}")
PID=$(echo "$CR" | grep -o '"planId":[0-9]*' | grep -o '[0-9]*')
chk "创建晚饭约定" 0 "$(code_of "$CR")"
DET=$(jcall GET "/plan/$PID" "$TA")
chk "晚饭详情 mealType=2" '"mealType":2' "$(val mealType "$DET")"
echo "  PID=$PID"

# 2) 非法餐次(3) -> PARAM_INVALID(4000)
CRBAD=$(jcall POST /plan/create "$TA" "{\"source\":0,\"mealType\":3,\"planDate\":\"$TOM\",\"items\":[{\"name\":\"x\"}]}")
chk "非法餐次(3)拒绝(4000)" 4000 "$(code_of "$CRBAD")"

# 3) 生命周期：认领->交付->完成
jcall POST "/plan/$PID/claim" "$TB" '{}' >/dev/null
jcall POST "/plan/$PID/deliver" "$TB" '{"deliverLocation":"门口"}' >/dev/null
jcall POST "/plan/$PID/complete" "$TA" '{}' >/dev/null
DET2=$(jcall GET "/plan/$PID" "$TA")
chk "晚饭后状态=已完成(4)" '"status":4' "$(val status "$DET2")"

# 4) 按餐段截止（仅工作时段 11<=HOUR<18 时可确定性验证）
if [ "$HOUR" -ge 11 ] && [ "$HOUR" -lt 18 ]; then
  LUNCH_TODAY=$(jcall POST /plan/create "$TA" "{\"source\":0,\"mealType\":1,\"planDate\":\"$TODAY\",\"items\":[{\"name\":\"午饭\"}]}")
  chk "午饭今天(已过点)被拦(1010)" 1010 "$(code_of "$LUNCH_TODAY")"
  DINNER_TODAY=$(jcall POST /plan/create "$TA" "{\"source\":0,\"mealType\":2,\"planDate\":\"$TODAY\",\"items\":[{\"name\":\"晚饭\"}]}")
  chk "晚饭今天(未过点)放行(0)" 0 "$(code_of "$DINNER_TODAY")"
  DPID=$(echo "$DINNER_TODAY" | grep -o '"planId":[0-9]*' | grep -o '[0-9]*')
  [ -n "$DPID" ] && jcall POST "/plan/$DPID/cancel" "$TA" '{}' >/dev/null
else
  echo "  SKIP: 按餐段截止断言需 11<=HOUR<18（当前 $HOUR，跳过）"
fi

# 清理
$MYSQL -e "DELETE FROM couple WHERE user_a IN (10011,10012) OR user_b IN (10011,10012); DELETE FROM user WHERE openid IN ('dinner_x','dinner_y');" 2>&1 | grep -v Warning
echo "============ 晚市验证: PASS=$PASS FAIL=$FAIL ============"
