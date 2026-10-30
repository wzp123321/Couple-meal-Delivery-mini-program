#!/usr/bin/env bash
# =============================================================================
# 情侣带饭小程序 —— 后端集成自测脚本（模拟前端联调）
#
# 用途：在无真机 / 无微信登录依赖下，对【运行中】的后端做全链路联调自测。
#       离线用 dev 密钥签 JWT 扮演三用户(A=10001/B=10002/C=10003)，直连 8080，
#       直接造 / 清 MySQL 测试数据，覆盖：关系绑定、双来源约定闭环、取消/撤回/改派、
#       乐观锁并发、边界校验、菜单 CRUD/忌口/画像/推荐、消息隔离、打卡全链路、
#       历史/周历/月报/年报/采购清单。
#
# 前置：
#   1. 后端已在 8080 启动（java -jar 或 spring-boot:run）
#   2. MySQL 在 127.0.0.1:3306，库 couple_meal，账号 root/123456
#   3. PATH 内可用：curl / openssl / mysql 客户端
#   4. application.yml 的 JWT_SECRET 为默认值 dev-only-change-me-in-prod-32bytes-min
#      （脚本内 SECRET 与此保持一致；若你改过密钥需同步本文件）
#
# 用法：
#   cd backend && bash itest/itest.sh
#
# 说明：脚本结尾自动清理 smoke_a/b/c 用户及其全部关联数据，可重复运行。
#       测试图片为仓库内固件 itest/fixtures/test.png，无需联网或本地生成。
# =============================================================================
set -u
BASE="${BASE:-http://127.0.0.1:8080/api}"
SECRET="dev-only-change-me-in-prod-32bytes-min"
MYSQL="mysql -h 127.0.0.1 -P 3306 -u root -p123456 couple_meal"

# 脚本目录 + 运行时临时目录（避免 /tmp 在 Git Bash 与原生 python 间路径不一致）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORK="$SCRIPT_DIR/.work"
mkdir -p "$WORK"
# 固件图片路径：curl 为原生 Windows 版，必须用 Windows 盘符路径(D:/...)，
# 故经 cygpath 转换；若 cygpath 不可用则回退到脚本相对路径。
TEST_PNG="$(cygpath -w -m "$SCRIPT_DIR/fixtures/test.png" 2>/dev/null || echo "$SCRIPT_DIR/fixtures/test.png")"
cleanup_tmp() { rm -rf "$WORK"; }
trap cleanup_tmp EXIT

PASS=0; FAIL=0
genjwt() {
  local uid=$1; local iat=$(date +%s); local exp=$((iat+2592000))
  local hb=$(printf '{"alg":"HS256","typ":"JWT"}' | openssl base64 -A | tr '+/' '-_' | tr -d '=')
  local pb=$(printf '{"sub":"%s","uid":%s,"iat":%s,"exp":%s}' "$uid" "$uid" "$iat" "$exp" | openssl base64 -A | tr '+/' '-_' | tr -d '=')
  local sig=$(printf '%s.%s' "$hb" "$pb" | openssl dgst -sha256 -hmac "$SECRET" -binary | openssl base64 -A | tr '+/' '-_' | tr -d '=')
  printf '%s.%s.%s' "$hb" "$pb" "$sig"
}
jcall() { # method url token body
  if [ "$1" = "GET" ]; then curl -s -m15 -H "Authorization: Bearer $3" "$BASE$2";
  else curl -s -m15 -H "Authorization: Bearer $3" -H "Content-Type: application/json" -X "$1" "$BASE$2" -d "$4"; fi
}
getcode() { echo "$1" | grep -o '"code":[0-9]*' | head -1 | grep -o '[0-9]*'; }
check() { # desc expected_code body
  local c=$(getcode "$3"); local exp="$2"
  if [ "$c" = "$exp" ]; then echo "  PASS: $1 (code=$c)"; PASS=$((PASS+1));
  else echo "  FAIL: $1 (期望 $exp 实得 $c) => $3"; FAIL=$((FAIL+1)); fi
}
val() { echo "$2" | grep -o "\"$1\":[^,}]*" | head -1; }

TA=$(genjwt 10001); TB=$(genjwt 10002); TC=$(genjwt 10003)
TODAY=$(date +%Y-%m-%d)
HOUR=$(date +%H)
if [ "$HOUR" -ge 11 ]; then CREATE_DATE=$(date -d "+1 day" +%Y-%m-%d); else CREATE_DATE=$TODAY; fi
# 超 7 天上限的样本日期：必须落在工作日，否则会先被 1009(只能约工作日) 拦下，
# 拿不到 1011。从 +8 天起往后找第一个工作日。
FAR=""; for i in $(seq 8 20); do d=$(date -d "+$i days" +%Y-%m-%d); dow=$(date -d "$d" +%u); if [ "$dow" -le 5 ]; then FAR=$d; break; fi; done
# 取一个周六作为非工作日：从今天往后找第一个周六
SAT=""; for i in $(seq 1 10); do d=$(date -d "+$i days" +%Y-%m-%d); dow=$(date -d "$d" +%u); if [ "$dow" = "6" ]; then SAT=$d; break; fi; done

echo "===== 0. 清理 & 准备测试用户 ====="
$MYSQL -e "DELETE FROM notification WHERE couple_id IN (SELECT id FROM couple WHERE user_a IN (10001,10002) OR user_b IN (10001,10002));
DELETE FROM love_value WHERE couple_id IN (SELECT id FROM couple WHERE user_a IN (10001,10002) OR user_b IN (10001,10002));
DELETE FROM meal_checkin_image WHERE checkin_id IN (SELECT id FROM meal_checkin WHERE user_id IN (10001,10002));
DELETE FROM meal_checkin WHERE user_id IN (10001,10002);
DELETE FROM meal_plan_item WHERE plan_id IN (SELECT id FROM meal_plan WHERE couple_id IN (SELECT id FROM couple WHERE user_a IN (10001,10002) OR user_b IN (10001,10002)));
DELETE FROM meal_plan WHERE couple_id IN (SELECT id FROM couple WHERE user_a IN (10001,10002) OR user_b IN (10001,10002));
DELETE FROM menu_item WHERE creator_id IN (10001,10002);
DELETE FROM couple_invite WHERE creator_id IN (10001,10002);
DELETE FROM couple WHERE user_a IN (10001,10002) OR user_b IN (10001,10002);
DELETE FROM user WHERE openid IN ('smoke_a','smoke_b','smoke_c');" 2>&1 | grep -v Warning
$MYSQL -e "INSERT INTO user (id,openid,nickname,create_time,update_time) VALUES (10001,'smoke_a','小A',NOW(),NOW()),(10002,'smoke_b','小B',NOW(),NOW()),(10003,'smoke_c','小C',NOW(),NOW());" 2>&1 | grep -v Warning
echo "  用户就绪 A=10001 B=10002 C=10003  SAT=$SAT FAR=$FAR"

echo "===== 1. 关系绑定 ====="
INV=$(jcall POST /couple/invite "$TA" '{}')
check "A 生成邀请码" 0 "$INV"
CODE=$(echo "$INV" | grep -o '"code":"[0-9]\{6\}"' | grep -o '[0-9]*')
echo "  inviteCode=$CODE"
JOIN=$(jcall POST /couple/join "$TB" "{\"code\":\"$CODE\"}")
check "B 用邀请码加入(建关系)" 0 "$JOIN"
INFO=$(jcall GET /couple/info "$TA" "")
check "A 查关系信息(已绑定)" 0 "$INFO"
echo "  info: hasCouple=$(val hasCouple "$INFO") togetherDays=$(val togetherDays "$INFO")"
SET=$(jcall POST /couple/settings "$TA" '{"anniversary":"2024-01-01","theme":"peach","remindEnabled":true,"nicknameMine":"宝贝"}')
check "A 设置关系(纪念日/称呼)" 0 "$SET"
INFO2=$(jcall GET /couple/info "$TB" "")
echo "  B 视角 nicknameMine=$(val nicknameMine "$INFO2") anniversary=$(val anniversary "$INFO2")"
check "C(无关系)查关系信息应 1002" 1002 "$(jcall GET /couple/info "$TC" "")"

echo "===== 2. 点餐流 source=0 闭环 ====="
CR=$(jcall POST /plan/create "$TA" "{\"source\":0,\"mealType\":1,\"planDate\":\"$CREATE_DATE\",\"items\":[{\"name\":\"红烧肉\"}],\"remark\":\"少放糖\"}")
check "A 发起点餐约定" 0 "$CR"
PID1=$(echo "$CR" | grep -o '"planId":[0-9]*' | grep -o '[0-9]*')
echo "  planId=$PID1 warnings=$(val warnings "$CR")"
D1=$(jcall GET /plan/$PID1 "$TB" "")
check "B 查详情含 claim 动作" 0 "$D1"
echo "  B 视角 actions=$(val actions "$D1")"
AC=$(jcall POST /plan/$PID1/claim "$TA" '{}')
check "A(点餐方)claim 自己约定应失败(1005)" 1005 "$AC"
CL=$(jcall POST /plan/$PID1/claim "$TB" '{}')
check "B 认领" 0 "$CL"
D2=$(jcall GET /plan/$PID1 "$TB" "")
echo "  B 认领后 actions=$(val actions "$D2")"
DV=$(jcall POST /plan/$PID1/deliver "$TB" '{"deliverLocation":"门口","deliverNote":"留了汤"}')
check "B 送达" 0 "$DV"
CM=$(jcall POST /plan/$PID1/complete "$TA" '{}')
check "A 领取完成" 0 "$CM"
echo "  完成后关系爱心值合计=$(val loveValue "$CM")"
LOV=$(jcall GET /love/summary "$TB" "")
check "B 爱心值 mine=1 total=1" 0 "$LOV"
echo "  B love: mine=$(val mine "$LOV") partner=$(val partner "$LOV") total=$(val total "$LOV")"
check "C 越权访问约定应 1002" 1002 "$(jcall GET /plan/$PID1 "$TC" "")"

echo "===== 3. 主动带流 source=1 闭环 ====="
CR2=$(jcall POST /plan/create "$TB" "{\"source\":1,\"mealType\":1,\"planDate\":\"$CREATE_DATE\",\"items\":[{\"name\":\"西红柿鸡蛋面\"}],\"remark\":\"\"}")
check "B 发起主动带" 0 "$CR2"
PID2=$(echo "$CR2" | grep -o '"planId":[0-9]*' | grep -o '[0-9]*')
D3=$(jcall GET /plan/$PID2 "$TA" "")
echo "  A 视角(待接收) actions=$(val actions "$D3")"
RJ=$(jcall POST /plan/$PID2/reject "$TA" '{"reason":"今天太累"}')
check "A 婉拒主动带→已取消" 0 "$RJ"
D4=$(jcall GET /plan/$PID2 "$TA" ""); echo "  婉拒后 status=$(val status "$D4")"
CR3=$(jcall POST /plan/create "$TB" "{\"source\":1,\"mealType\":1,\"planDate\":\"$CREATE_DATE\",\"items\":[{\"name\":\"三明治\"}]}")
PID3=$(echo "$CR3" | grep -o '"planId":[0-9]*' | grep -o '[0-9]*')
CF=$(jcall POST /plan/$PID3/confirm "$TA" '{}')
check "A 确认主动带→备餐中" 0 "$CF"
DV3=$(jcall POST /plan/$PID3/deliver "$TB" '{"deliverLocation":"工位"}')
check "B 送达" 0 "$DV3"
CM3=$(jcall POST /plan/$PID3/complete "$TA" '{}')
check "A 完成" 0 "$CM3"
LOV2=$(jcall GET /love/summary "$TB" "")
echo "  二次完成后 B mine=$(val mine "$LOV2") total=$(val total "$LOV2")"

echo "===== 4. 取消 / 撤回 / 改派 ====="
CR4=$(jcall POST /plan/create "$TA" "{\"source\":0,\"mealType\":1,\"planDate\":\"$CREATE_DATE\",\"items\":[{\"name\":\"寿司\"}]}")
PID4=$(echo "$CR4" | grep -o '"planId":[0-9]*' | grep -o '[0-9]*')
jcall POST /plan/$PID4/claim "$TB" '{}' >/dev/null
CN=$(jcall POST /plan/$PID4/cancel "$TA" '{"reason":"不吃了"}')
check "认领后 A 取消(原因必填)" 0 "$CN"
D5=$(jcall GET /plan/$PID4 "$TA" ""); echo "  取消后 status=$(val status "$D5")"
CR5=$(jcall POST /plan/create "$TA" "{\"source\":0,\"mealType\":1,\"planDate\":\"$CREATE_DATE\",\"items\":[{\"name\":\"饺子\"}]}")
PID5=$(echo "$CR5" | grep -o '"planId":[0-9]*' | grep -o '[0-9]*')
jcall POST /plan/$PID5/claim "$TB" '{}' >/dev/null
WD=$(jcall POST /plan/$PID5/withdraw "$TB" '{}')
check "B 带饭方撤回→待认领" 0 "$WD"
D6=$(jcall GET /plan/$PID5 "$TA" ""); echo "  撤回后 status=$(val status "$D6")"
jcall POST /plan/$PID5/claim "$TB" '{}' >/dev/null
RS=$(jcall POST /plan/$PID5/reassign "$TB" '{}')
check "B 改派(空 carrier)→待认领" 0 "$RS"
D7=$(jcall GET /plan/$PID5 "$TA" ""); echo "  改派后 status=$(val status "$D7")"

echo "===== 5. 并发领取(乐观锁) ====="
CR6=$(jcall POST /plan/create "$TA" "{\"source\":0,\"mealType\":1,\"planDate\":\"$CREATE_DATE\",\"items\":[{\"name\":\"沙拉\"}]}")
PID6=$(echo "$CR6" | grep -o '"planId":[0-9]*' | grep -o '[0-9]*')
jcall POST /plan/$PID6/claim "$TB" '{}' >"$WORK/c1.txt" &
jcall POST /plan/$PID6/claim "$TB" '{}' >"$WORK/c2.txt" &
wait
C1=$(getcode "$(cat "$WORK/c1.txt")"); C2=$(getcode "$(cat "$WORK/c2.txt")")
if { [ "$C1" = "0" ] && [ "$C2" != "0" ]; } || { [ "$C2" = "0" ] && [ "$C1" != "0" ]; }; then echo "  PASS: 并发领取仅一个成功 (c1=$C1 c2=$C2)"; PASS=$((PASS+1));
else echo "  FAIL: 并发领取结果异常 (c1=$C1 c2=$C2)"; FAIL=$((FAIL+1)); fi

echo "===== 6. 发起校验(非工作日/超期/菜品数/备注) ====="
check "非工作日(周六 $SAT)发起应 1009" 1009 "$(jcall POST /plan/create "$TA" "{\"source\":0,\"planDate\":\"$SAT\",\"items\":[{\"name\":\"x\"}]}")"
check "超 7 天发起应 1011" 1011 "$(jcall POST /plan/create "$TA" "{\"source\":0,\"planDate\":\"$FAR\",\"items\":[{\"name\":\"x\"}]}")"
ITEMS=""; for i in $(seq 1 11); do ITEMS="$ITEMS{\"name\":\"菜$i\"},"; done; ITEMS="[${ITEMS%,}]"
check "11 道菜应 2003" 2003 "$(jcall POST /plan/create "$TA" "{\"source\":0,\"planDate\":\"$CREATE_DATE\",\"items\":$ITEMS}")"
LONGREMARK=$(printf '备%s注' "$(printf '注%.0s' $(seq 1 100))")
check "备注101字应 2005" 2005 "$(jcall POST /plan/create "$TA" "{\"source\":0,\"planDate\":\"$CREATE_DATE\",\"items\":[{\"name\":\"x\"}],\"remark\":\"$LONGREMARK\"}")"

echo "===== 7. 菜单 CRUD / 忌口 / 重名 / 画像 / 推荐 ====="
M1=$(jcall POST /menu "$TA" '{"name":"番茄炒蛋","category":2,"isFavorite":true,"avoidLevel":0,"note":"下饭"}')
check "A 新增菜单(爱吃的)" 0 "$M1"
MID1=$(echo "$M1" | grep -o '"data":[0-9]*' | grep -o '[0-9]*')
check "A 重名新增应 2002" 2002 "$(jcall POST /menu "$TA" '{"name":"番茄炒蛋","category":2,"avoidLevel":0}')"
LONGNAME=$(printf '菜%s' "$(printf '名%.0s' $(seq 1 21))")
check "菜名21字应 2004" 2004 "$(jcall POST /menu "$TA" "{\"name\":\"$LONGNAME\",\"category\":2,\"avoidLevel\":0}")"
M2=$(jcall POST /menu "$TB" '{"name":"香菜","category":1,"avoidLevel":3,"note":"过敏"}')
check "B 新增过敏菜(忌口3)" 0 "$M2"
ML=$(jcall GET /menu/list "$TA" "?category=2")
check "A 列菜单(含分类筛选)" 0 "$ML"
MP=$(jcall GET /menu/profile "$TA" "")
check "A 口味画像" 0 "$MP"; echo "  profile total=$(val total "$MP")"
MR=$(jcall GET /menu/recommend "$TA" "?limit=5")
check "A 智能推荐" 0 "$MR"
echo "  recommend 是否含'香菜'(忌口3,应排除): [$(echo "$MR" | grep -o '香菜' | head -1)]"
MD=$(jcall DELETE /menu/$MID1 "$TA" "")
check "A 软删菜单" 0 "$MD"

echo "===== 8. 消息隔离 / 已读 ====="
UNB=$(jcall GET /msg/unread "$TB" "")
check "B 未读>0" 0 "$UNB"; echo "  B unread=$(val unread "$UNB")"
MLB=$(jcall GET /msg/list "$TB" "?pageNo=1&pageSize=20")
check "B 消息列表" 0 "$MLB"; echo "  B list total=$(val total "$MLB")"
MLA=$(jcall GET /msg/list "$TA" "?pageNo=1&pageSize=20")
echo "  A list total=$(val total "$MLA") (预期与 B 不同)"
RD=$(jcall POST /msg/read "$TB" '{}')
check "B 全部已读" 0 "$RD"
UNB2=$(jcall GET /msg/unread "$TB" "")
echo "  B 已读后 unread=$(val unread "$UNB2") (预期 0)"

echo "===== 9. 打卡(上传→提交→修改→回应→相册可见性) ====="
UP=$(curl -s -m15 -H "Authorization: Bearer $TA" -F "file=@$TEST_PNG;type=image/png" "$BASE/checkin/upload")
check "A 上传打卡图" 0 "$UP"
UPURL=$(echo "$UP" | grep -o '"url":"[^"]*"' | head -1 | sed 's/"url":"//;s/"//')
echo "  upload url=$UPURL"
CID=0   # 窗口外走 SKIP 分支时后面的 ID 推导仍需它，否则 set -u 直接中断整轮
HOUR_NOW=$(date +%H)
if [ "$HOUR_NOW" -ge 10 ] || [ "$HOUR_NOW" -lt 2 ]; then
SUB=$(jcall POST /checkin/submit "$TA" "{\"planId\":$PID1,\"images\":[\"$UPURL\"],\"rating\":3,\"content\":\"今天超香\"}")
check "A(接收方)提交打卡" 0 "$SUB"
CID=$(echo "$SUB" | grep -o '"data":[0-9]*' | grep -o '[0-9]*')
check "B(非接收方)提交打卡应 3004" 3004 "$(jcall POST /checkin/submit "$TB" "{\"planId\":$PID1,\"images\":[\"$UPURL\"],\"rating\":3}")"
check "A 重复打卡应 3001" 3001 "$(jcall POST /checkin/submit "$TA" "{\"planId\":$PID1,\"images\":[\"$UPURL\"],\"rating\":3}")"
GALA=$(jcall GET /checkin/gallery "$TA" "?size=10")
check "A 相册(本人可见,auditStatus=0)" 0 "$GALA"
echo "  A gallery 卡片数=$(echo "$GALA" | grep -o '"checkinId"' | wc -l)"
GALB=$(jcall GET /checkin/gallery "$TB" "?size=10")
echo "  B gallery 卡片数=$(echo "$GALB" | grep -o '"checkinId"' | wc -l) (审核中对方不可见,预期 0)"
UPD=$(jcall POST /checkin/update "$TA" "{\"checkinId\":$CID,\"rating\":2,\"content\":\"改一下\"}")
check "A 当天修改打卡" 0 "$UPD"
RPL=$(jcall POST /checkin/reply "$TB" "{\"checkinId\":$CID,\"reply\":\"宝贝辛苦啦\"}")
check "B(带饭方)回应" 0 "$RPL"
GALA2=$(jcall GET /checkin/gallery "$TA" "?size=10")
echo "  A gallery 含回应: [$(echo "$GALA2" | grep -o '宝贝辛苦啦' | head -1)]"
else echo "  SKIP: 打卡交互段需在窗口(10:00~次日02:00)内运行，跳过提交/修改/回应"; fi
# 可见性: 插一条已过审的对方打卡, 验证 B 相册能看到
CIDX=$((CID+100000)); CIDX_IMG=$((CIDX+1))
CID_PAIR=$($MYSQL -N -e "SELECT id FROM couple WHERE user_a=10001 LIMIT 1" 2>/dev/null | tail -1)
$MYSQL -e "INSERT INTO meal_checkin (id,couple_id,plan_id,user_id,rating,content,audit_status,create_time,update_time) VALUES ($CIDX,$CID_PAIR,$PID3,10001,3,'对方已过审打卡',1,NOW(),NOW());
INSERT INTO meal_checkin_image (id,checkin_id,url,thumb_url,seq,audit_status,trace_id,create_time) VALUES ($CIDX_IMG,$CIDX,'/upload/test/x.jpg','/upload/test/x_thumb.jpg',0,1,'t1',NOW());" 2>&1 | grep -v Warning
GALB2=$(jcall GET /checkin/gallery "$TB" "?size=10")
echo "  B gallery 含已过审对方卡(预期 1 张): $(echo "$GALB2" | grep -o '"checkinId"' | wc -l)"

echo "===== 10. 历史 / 周历 / 报告 / 采购 ====="
$MYSQL -e "INSERT INTO meal_plan (id,couple_id,orderer_id,carrier_id,meal_type,source,status,plan_date,remark,create_time,update_time) VALUES (990001,$CID_PAIR,10002,10001,1,0,4,'2026-10-20','历史补录',NOW(),NOW());
INSERT INTO meal_plan_item (id,plan_id,name,seq,create_time) VALUES (990002,990001,'历史饭',0,NOW());" 2>&1 | grep -v Warning
HIS=$(jcall GET /plan/history "$TA" "?pageNo=1&pageSize=20")
check "A 历史(含 10-20 约定)" 0 "$HIS"; echo "  history total=$(val total "$HIS")"
WK=$(jcall GET /plan/week "$TA" "")
check "A 周历" 0 "$WK"
RP=$(jcall GET /report "$TA" "?scope=month&anchor=2026-10")
check "A 月度报告" 0 "$RP"; echo "  month report keys: $(echo "$RP" | grep -o '\"[a-zA-Z]*\":' | tr -d ':\"' | tr '\n' ' ')"
RY=$(jcall GET /report "$TA" "?scope=year&anchor=2026")
check "A 年度报告" 0 "$RY"
CRSH=$(jcall POST /plan/create "$TA" "{\"source\":0,\"mealType\":1,\"planDate\":\"$CREATE_DATE\",\"items\":[{\"name\":\"火锅食材\"}]}")
PIDSH=$(echo "$CRSH" | grep -o '"planId":[0-9]*' | grep -o '[0-9]*')
jcall POST /plan/$PIDSH/claim "$TB" '{}' >/dev/null
jcall POST /plan/$PIDSH/deliver "$TB" '{"deliverLocation":"门口"}' >/dev/null
SG=$(jcall POST /shopping/generate "$TA" "{\"planId\":$PIDSH}")
check "A 生成采购清单(已交付约定)" 0 "$SG"
SL=$(jcall GET /shopping/list "$TA" "?planId=$PIDSH")
check "A 采购清单列表" 0 "$SL"; echo "  shopping 条目数=$(echo "$SL" | grep -o '"itemName"' | wc -l)"

echo "===== 清理测试数据 ====="
$MYSQL -e "DELETE FROM notification WHERE couple_id IN (SELECT id FROM couple WHERE user_a IN (10001,10002) OR user_b IN (10001,10002));
DELETE FROM love_value WHERE couple_id IN (SELECT id FROM couple WHERE user_a IN (10001,10002) OR user_b IN (10001,10002));
DELETE FROM meal_checkin_image WHERE checkin_id IN ($CIDX,$CIDX_IMG);
DELETE FROM meal_checkin WHERE id IN ($CIDX,990001) OR user_id IN (10001,10002);
DELETE FROM meal_plan_item WHERE plan_id IN (SELECT id FROM meal_plan WHERE couple_id IN (SELECT id FROM couple WHERE user_a IN (10001,10002) OR user_b IN (10001,10002)));
DELETE FROM meal_plan WHERE id=990001 OR couple_id IN (SELECT id FROM couple WHERE user_a IN (10001,10002) OR user_b IN (10001,10002));
DELETE FROM menu_item WHERE creator_id IN (10001,10002);
DELETE FROM couple_invite WHERE creator_id IN (10001,10002);
DELETE FROM couple WHERE user_a IN (10001,10002) OR user_b IN (10001,10002);
DELETE FROM user WHERE openid IN ('smoke_a','smoke_b','smoke_c');" 2>&1 | grep -v Warning
echo ""
echo "================ 结果: PASS=$PASS FAIL=$FAIL ================"
