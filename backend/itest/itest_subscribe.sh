#!/usr/bin/env bash
# =============================================================================
# 订阅消息 —— 后端集成自测（phase-2）
# 覆盖：模板 ID 列表接口（dev 未配置模板时返回空串，不下发）。
# 注：实际微信下发在 dev 不可达，发送路径由主 itest 的认领/送达流程间接覆盖（失败仅记录）。
# BASE 可经环境变量覆盖（默认 8080）。
# =============================================================================
set -u
BASE="${BASE:-http://127.0.0.1:8080/api}"
SECRET="dev-only-change-me-in-prod-32bytes-min"
MYSQL="mysql -h 127.0.0.1 -P 3306 -u root -p123456 couple_meal"

TA=$(printf '{"alg":"HS256","typ":"JWT"}' | openssl base64 -A | tr '+/' '-_' | tr -d '=')
genjwt() {
  local uid=$1; local iat=$(date +%s); local exp=$((iat+2592000))
  local pb=$(printf '{"sub":"%s","uid":%s,"iat":%s,"exp":%s}' "$uid" "$uid" "$iat" "$exp" | openssl base64 -A | tr '+/' '-_' | tr -d '=')
  local sig=$(printf '%s.%s' "$TA" "$pb" | openssl dgst -sha256 -hmac "$SECRET" -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')
  printf '%s.%s.%s' "$TA" "$pb" "$sig"
}
jcall() { curl -s -m15 -H "Authorization: Bearer $3" "$BASE$2"; }
getcode() { echo "$1" | grep -o '"code":[0-9]*' | head -1 | grep -o '[0-9]*'; }
check() { local c=$(getcode "$3"); local exp="$2"
  if [ "$c" = "$exp" ]; then echo "  PASS: $1 (code=$c)"; PASS=$((PASS+1));
  else echo "  FAIL: $1 (期望 $exp 实得 $c) => $3"; FAIL=$((FAIL+1)); fi; }
checkstr() { local v="$3"; local exp="$2"
  if echo "$v" | grep -q "$exp"; then echo "  PASS: $1"; PASS=$((PASS+1));
  else echo "  FAIL: $1 (期望含 $exp 实得 [$v])"; FAIL=$((FAIL+1)); fi; }
A=$(genjwt 10031)
PASS=0; FAIL=0

# 自建一个登录用户（与 rps / 主 itest 隔离，避免被其清理误删或冲突）
$MYSQL -e "DELETE FROM user WHERE id=10031; INSERT INTO user (id,openid,nickname,create_time,update_time) VALUES (10031,'sub_a','小A',NOW(),NOW());" 2>&1 | grep -v Warning

echo "===== 1. 模板 ID 列表 ====="
TP=$(jcall GET /subscribe/templates "$A")
check "获取订阅模板列表" 0 "$TP"
echo "  templates: $(echo "$TP" | grep -o '"data":{[^}]*}')"
checkstr "含 claimed 键" "claimed" "$TP"
checkstr "含 delivered 键" "delivered" "$TP"
checkstr "含 checkinRemind 键" "checkinRemind" "$TP"

echo "===== 清理 ====="
$MYSQL -e "DELETE FROM user WHERE id=10031;" 2>&1 | grep -v Warning

echo ""
echo "================ SUBSCRIBE 结果: PASS=$PASS FAIL=$FAIL ================"
