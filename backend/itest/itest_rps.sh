#!/usr/bin/env bash
# =============================================================================
# 石头剪刀布协商 —— 后端集成自测（phase-2）
# 覆盖：发起幂等 / 出拳胜负 / 平局重出 / 已完成后再出拳拦截 / 未分胜负采纳拦截 / 非法出拳 / 采纳生成约定(carrier=输家)
# 依赖：后端已在运行；MySQL 可用。脚本自管测试用户与关系，结尾清理。
# 出拳取值（与 RpsService 常量一致）：1石头 2剪刀 3布
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
jcall() {
  if [ "$1" = "GET" ]; then curl -s -m15 -H "Authorization: Bearer $3" "$BASE$2";
  else curl -s -m15 -H "Authorization: Bearer $3" -H "Content-Type: application/json" -X "$1" "$BASE$2" -d "$4"; fi
}
getcode() { echo "$1" | grep -o '"code":[0-9]*' | head -1 | grep -o '[0-9]*'; }
# 取响应里的数字值（不含键名）
num() { echo "$2" | grep -o "\"$1\":[0-9]*" | grep -o '[0-9]*' | head -1; }
check() { local c=$(getcode "$3"); local exp="$2"
  if [ "$c" = "$exp" ]; then echo "  PASS: $1 (code=$c)"; PASS=$((PASS+1));
  else echo "  FAIL: $1 (期望 $exp 实得 $c) => $3"; FAIL=$((FAIL+1)); fi; }
# 直接比较取出的值
checkval() { local v="$3"; local exp="$2"
  if [ "$v" = "$exp" ]; then echo "  PASS: $1 (=$v)"; PASS=$((PASS+1));
  else echo "  FAIL: $1 (期望 $exp 实得 $v)"; FAIL=$((FAIL+1)); fi; }
# 取响应里的布尔值（true/false）
bool() { echo "$2" | grep -o "\"$1\":\(true\|false\)" | grep -o '\(true\|false\)' | head -1; }

A=$(genjwt 10021); B=$(genjwt 10022)
PASS=0; FAIL=0

# 未来的一个工作日，作为对局约定日期（避开当日午饭截止，保证采纳可落库）
NW=""
for i in $(seq 1 9); do d=$(date -d "+$i days" +%Y-%m-%d); dow=$(date -d "$d" +%u); if [ "$dow" -le 5 ]; then NW=$d; break; fi; done

# 清理（幂等，关 FK 检查防阻塞；先删子表再删主表）
cleanup() {
  $MYSQL -e "SET FOREIGN_KEY_CHECKS=0;
DELETE FROM meal_plan_item WHERE plan_id IN (SELECT id FROM meal_plan WHERE couple_id=20021);
DELETE FROM meal_plan WHERE couple_id=20021;
DELETE FROM rps_round WHERE game_id IN (SELECT id FROM rps_game WHERE couple_id=20001);
DELETE FROM rps_game WHERE couple_id=20021;
DELETE FROM couple WHERE id=20021;
DELETE FROM user WHERE id IN (10021,10022);
SET FOREIGN_KEY_CHECKS=1;" 2>&1 | grep -v Warning
}

echo "===== 0. 准备测试用户与关系 ====="
cleanup
$MYSQL -e "INSERT INTO user (id,openid,nickname,create_time,update_time) VALUES (10021,'rps_a','小A',NOW(),NOW()),(10022,'rps_b','小B',NOW(),NOW());
INSERT INTO couple (id,user_a,user_b,status,create_time,update_time) VALUES (20021,10021,10022,1,NOW(),NOW());" 2>&1 | grep -v Warning
echo "  用户/关系就绪 A=10021(user_a) B=10022(user_b) NW=$NW"

echo "===== 1. 发起(幂等) ====="
CR=$(jcall POST /rps/create "$A" "{\"mealType\":1,\"planDate\":\"$NW\"}")
check "A 发起午饭对局(NW)" 0 "$CR"
GID=$(num gameId "$CR")
echo "  gameId=$GID status=$(num status "$CR")"
CR2=$(jcall POST /rps/create "$A" "{\"mealType\":1,\"planDate\":\"$NW\"}")
GID2=$(num gameId "$CR2")
checkval "A 重复发起返回同一局(幂等)" "$GID" "$GID2"
check "C(无关系)发起应 1002" 1002 "$(jcall POST /rps/create "$(genjwt 10003)" "{\"mealType\":1,\"planDate\":\"$NW\"}")"

echo "===== 2. 出拳与胜负(rock=1 胜 scissors=2) ====="
TH=$(jcall POST /rps/$GID/throw "$A" '{"choice":1}')
check "A 出石头" 0 "$TH"
checkval "A 出拳后 myChoice=1" "1" "$(num myChoice "$TH")"
checkval "A 出拳后对方未出" "false" "$(bool opponentThrown "$TH")"
THB=$(jcall POST /rps/$GID/throw "$B" '{"choice":2}')
check "B 出剪刀 -> A 胜" 0 "$THB"
checkval "B 出拳后状态=已完成(1)" "1" "$(num status "$THB")"
checkval "胜方=10021" "10021" "$(num winnerId "$THB")"
check "完成后 B 再出拳应 6003" 6003 "$(jcall POST /rps/$GID/throw "$B" '{"choice":3}')"

echo "===== 3. 平局重出 ====="
CR3=$(jcall POST /rps/create "$A" "{\"mealType\":2,\"planDate\":\"$NW\"}")
GID3=$(num gameId "$CR3")
T1=$(jcall POST /rps/$GID3/throw "$A" '{"choice":3}')
check "A 出布" 0 "$T1"
T2=$(jcall POST /rps/$GID3/throw "$B" '{"choice":3}')
check "B 也出布 -> 平局" 0 "$T2"
checkval "平局后仍为进行中(0)" "0" "$(num status "$T2")"
checkval "平局后回合=2" "2" "$(num currentRound "$T2")"
check "非法出拳(5)应 6004" 6004 "$(jcall POST /rps/$GID3/throw "$A" '{"choice":5}')"
T3=$(jcall POST /rps/$GID3/throw "$A" '{"choice":1}')
check "重出 A 石头" 0 "$T3"
T4=$(jcall POST /rps/$GID3/throw "$B" '{"choice":2}')
check "B 剪刀 -> A 胜(第2回合)" 0 "$T4"
checkval "第2回合胜方=10021" "10021" "$(num winnerId "$T4")"

echo "===== 4. 采纳生成约定(carrier=输家) ====="
AD=$(jcall POST /rps/$GID/adopt "$A" '{"items":[{"name":"输家的便当"}],"remark":"猜拳输了，我来带"}')
check "A 采纳生成约定" 0 "$AD"
PID=$(num planId "$AD")
echo "  planId=$PID"
ROW=$($MYSQL -N -e "SELECT carrier_id,source,status FROM meal_plan WHERE id=$PID;" 2>/dev/null)
echo "  plan 行: $ROW (期望 10022 1 1)"
checkval "约定 carrier=输家(10022)" "10022" "$(echo "$ROW" | awk '{print $1}')"
checkval "约定 source=1(主动带)" "1" "$(echo "$ROW" | awk '{print $2}')"
checkval "约定 status=1(待接收)" "1" "$(echo "$ROW" | awk '{print $3}')"
AD2=$(jcall POST /rps/$GID/adopt "$B" '{}')
checkval "B 再采纳幂等返回同一 planId" "$PID" "$(num planId "$AD2")"
# 未分胜负局采纳应 6003（另起一局不投掷）
CR4=$(jcall POST /rps/create "$A" "{\"mealType\":1,\"planDate\":\"$NW\"}")
GID4=$(num gameId "$CR4")
check "未分胜负局采纳应 6003" 6003 "$(jcall POST /rps/$GID4/adopt "$A" '{}')"
check "不存在局采纳应 6001" 6001 "$(jcall POST /rps/999999999/adopt "$A" '{}')"

echo "===== 清理 ====="
cleanup

echo ""
echo "================ RPS 结果: PASS=$PASS FAIL=$FAIL ================"
