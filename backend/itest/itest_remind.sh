#!/usr/bin/env bash
# =============================================================================
# 每日提醒（type=5）—— 后端集成自测（fix-phase2-gaps §3）
# 覆盖：
#   1. 关系开关 remind_enabled=0 时，当天待认领约定不产生 type=5 通知
#   2. 开关 =1 时午饭提醒产生 1 条（携带 plan_id），重复触发仍是 1 条（唯一索引幂等）
#   3. 同一天晚饭另产生 1 条（plan_id 不同、文案不同，两条互不抑制）
#   4. 周末不提醒：周六的约定不产生通知；且 WorkdayUtil.isWorkday(周六)=false
#
# 前置：后端在 8080 运行，且两个提醒 cron 已调成高频（否则要等到 11:00 / 17:00）：
#   java -jar target/couple-meal-0.0.1-SNAPSHOT.jar --server.port=8080 \
#     --couple-meal.schedule.daily-remind-cron="*/5 * * * * *" \
#     --couple-meal.schedule.daily-remind-dinner-cron="*/5 * * * * *"
# 用法：bash itest/itest_remind.sh      （需 MySQL 可达，测试数据自建自清）
# 注：午饭/晚饭用例要求「今天」是工作日，否则 Job 会因 isWorkday 早退而跳过（脚本会 SKIP 并提示）。
# =============================================================================
set -u
BASE="${BASE:-http://127.0.0.1:8080/api}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
CP="$BACKEND_DIR/target/classes"
MYSQL="mysql -h 127.0.0.1 -P 3306 -u root -p123456 couple_meal"

TODAY=$(date +%Y-%m-%d)
TODAY_DOW=$(date +%u)                       # 1..7，6=周六 7=周日

# 最近的一个周六（含今天），用于「周末不提醒」用例
SAT=""
i=0
while [ "$i" -le 6 ]; do
  d=$(date -d "-$i day" +%Y-%m-%d)
  if [ "$(date -d "$d" +%u)" = "6" ]; then SAT="$d"; break; fi
  i=$((i+1))
done

PASS=0; FAIL=0
chk() {
  local desc="$1" exp="$2" got="$3"
  if [ "$exp" = "$got" ]; then echo "  PASS: $desc (=$got)"; PASS=$((PASS+1));
  else echo "  FAIL: $desc (期望 $exp 实得 $got)"; FAIL=$((FAIL+1)); fi
}
q() { $MYSQL -N -B -e "$1" 2>/dev/null | grep -v Warning | tr -d '\r'; }
cnt() { q "SELECT COUNT(*) FROM notification WHERE $1;"; }
# 触发一次提醒轮询后等待 Job 跑完（cron 为 */5s，等 8s 覆盖至少一次）
tick() { sleep 8; }

echo "===== 每日提醒验证 (TODAY=$TODAY 周$TODAY_DOW SAT=$SAT) ====="

# ---------- 造数据：两组关系（开关关 / 开），均为待认领约定 ----------
$MYSQL -e "
DELETE FROM notification WHERE receiver_id IN (10042,10044) OR plan_id IN (92001,92002,92003,92004);
DELETE FROM meal_plan WHERE id IN (92001,92002,92003,92004);
DELETE FROM couple WHERE id IN (91001,91002) OR user_a IN (10041,10042,10043,10044);
DELETE FROM user WHERE id IN (10041,10042,10043,10044);
INSERT INTO user (id,openid,nickname,create_time,update_time) VALUES
  (10041,'remind_a','提A',NOW(),NOW()),(10042,'remind_b','提B',NOW(),NOW()),
  (10043,'remind_c','提C',NOW(),NOW()),(10044,'remind_d','提D',NOW(),NOW());
INSERT INTO couple (id,user_a,user_b,remind_enabled,status,create_time,update_time) VALUES
  (91001,10041,10042,0,1,NOW(),NOW()),
  (91002,10043,10044,1,1,NOW(),NOW());
" 2>&1 | grep -v Warning

# ========== 1. 开关关闭：不发提醒 ==========
echo "----- 1. remind_enabled=0 不提醒 -----"
$MYSQL -e "INSERT INTO meal_plan (id,couple_id,orderer_id,meal_type,source,status,plan_date,version,create_time,update_time)
           VALUES (92001,91001,10041,1,0,0,'$TODAY',0,NOW(),NOW());" 2>&1 | grep -v Warning
tick
chk "开关关闭的午饭约定不产生 type=5" 0 "$(cnt "plan_id=92001 AND type=5")"
chk "开关关闭时收件人 10042 无任何 type=5" 0 "$(cnt "receiver_id=10042 AND type=5")"

# ========== 2. 开关开启：午饭提醒 1 条 ==========
echo "----- 2. remind_enabled=1 午饭提醒 -----"
$MYSQL -e "INSERT INTO meal_plan (id,couple_id,orderer_id,meal_type,source,status,plan_date,version,create_time,update_time)
           VALUES (92002,91002,10043,1,0,0,'$TODAY',0,NOW(),NOW());" 2>&1 | grep -v Warning
tick
chk "午饭约定产生 1 条 type=5" 1 "$(cnt "plan_id=92002 AND type=5")"
chk "收件人是带饭方 10044" 1 "$(cnt "plan_id=92002 AND type=5 AND receiver_id=10044")"
chk "午饭标题=今天午饭谁带呀" "今天午饭谁带呀" "$(q "SELECT title FROM notification WHERE plan_id=92002 AND type=5 LIMIT 1;")"
chk "通知携带 plan_id" 1 "$(cnt "plan_id=92002 AND type=5 AND plan_id IS NOT NULL")"

# ========== 3. 幂等：再跑几轮仍是 1 条 ==========
echo "----- 3. 重复触发幂等 -----"
tick
chk "多轮触发后仍只有 1 条（唯一索引去重）" 1 "$(cnt "plan_id=92002 AND type=5")"

# ========== 4. 晚饭单独提醒 ==========
echo "----- 4. 晚饭另发一条 -----"
$MYSQL -e "INSERT INTO meal_plan (id,couple_id,orderer_id,meal_type,source,status,plan_date,version,create_time,update_time)
           VALUES (92003,91002,10043,2,0,0,'$TODAY',0,NOW(),NOW());" 2>&1 | grep -v Warning
tick
chk "晚饭约定产生 1 条 type=5" 1 "$(cnt "plan_id=92003 AND type=5")"
chk "午饭+晚饭共 2 条（互不抑制）" 2 "$(cnt "plan_id IN (92002,92003) AND type=5")"
chk "两条 plan_id 不同" 2 "$(q "SELECT COUNT(DISTINCT plan_id) FROM notification WHERE plan_id IN (92002,92003) AND type=5;")"
chk "晚饭标题=今天晚饭谁带呀" "今天晚饭谁带呀" "$(q "SELECT title FROM notification WHERE plan_id=92003 AND type=5 LIMIT 1;")"
tick
chk "晚饭多轮触发后仍 1 条" 1 "$(cnt "plan_id=92003 AND type=5")"

# ========== 5. 周末不提醒 ==========
echo "----- 5. 周末不提醒 (SAT=$SAT) -----"
if [ -z "$SAT" ]; then
  echo "  SKIP: 未算出最近周六日期"
else
  $MYSQL -e "INSERT INTO meal_plan (id,couple_id,orderer_id,meal_type,source,status,plan_date,version,create_time,update_time)
             VALUES (92004,91002,10043,1,0,0,'$SAT',0,NOW(),NOW());" 2>&1 | grep -v Warning
  tick
  chk "周六的待认领约定不产生 type=5" 0 "$(cnt "plan_id=92004 AND type=5")"
  # Job 只在 isWorkday(today)=true 时处理；周末分支由该早退保证，用纯静态工具类直接断言
  WD=$(printf 'import com.couplemeal.common.util.WorkdayUtil;\nimport java.time.LocalDate;\nSystem.out.println("SATWD=" + WorkdayUtil.isWorkday(LocalDate.parse("%s")));\nSystem.out.println("TODAYWD=" + WorkdayUtil.isWorkday(LocalDate.parse("%s")));\n/exit\n' "$SAT" "$TODAY" \
        | jshell --class-path "$CP" -q 2>/dev/null | grep -o 'SATWD=[a-z]*' | head -1 | cut -d= -f2)
  chk "WorkdayUtil.isWorkday(周六)=false（周末提前返回）" "false" "${WD:-unknown}"
fi

# 今日非工作日时，第 2/4 组断言本应为 0，明确提示免得误判为通过
if [ "$TODAY_DOW" -ge 6 ]; then
  echo "  WARN: 今天 $TODAY 是周末，Job 因 isWorkday 早退，第 2~4 组「应有提醒」断言会失败——请在工作日回归"
fi

# ---------- 清理 ----------
$MYSQL -e "
DELETE FROM notification WHERE receiver_id IN (10042,10044) OR plan_id IN (92001,92002,92003,92004);
DELETE FROM meal_plan WHERE id IN (92001,92002,92003,92004);
DELETE FROM couple WHERE id IN (91001,91002) OR user_a IN (10041,10042,10043,10044);
DELETE FROM user WHERE id IN (10041,10042,10043,10044);
" 2>&1 | grep -v Warning

echo ""
echo "================ REMIND 结果: PASS=$PASS FAIL=$FAIL ================"
