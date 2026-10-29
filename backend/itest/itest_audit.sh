#!/usr/bin/env bash
# =============================================================================
# 图片审核挂起兜底 —— 后端集成自测（tasks 6.1 / 6.2 / 6.5）
#
# 背景：打卡图片异步审核（mediaCheckAsync）靠回调写状态。回调丢失（地址未配 / 网络丢包）时
#       图片会永久停在 audit_status=0（审核中），永不展示且用户毫不知情。
#       MediaAuditServiceImpl.expirePendingAudit() 负责把「超时未回调」的图置为屏蔽（2）。
#
# 覆盖：
#   1. audit_status=0 + create_time 早于 pending-timeout-minutes → 被置为 2，且写 audited_time、trace_id 不变
#   2. 同批 audit_status=1（已通过）的旧图 → 仍是 1（绝不回退终态）
#   3. 所属 meal_checkin 汇总态被重算：一张被屏蔽 + 一张已通过 → 2
#   4. 新建（未超时）的审核中图片 → 仍是 0，所属打卡汇总态仍是 0（兜底不会误伤新图）
#
# 前置：后端在 8080 运行，且把兜底任务调成高频，并把超时阈值压到 1 分钟：
#   java -jar target/couple-meal-0.0.1-SNAPSHOT.jar --server.port=8080 \
#     --couple-meal.schedule.audit-pending-expire-cron="*/5 * * * * *" \
#     --couple-meal.audit.pending-timeout-minutes=1
# 用法：bash itest/itest_audit.sh      （需 MySQL 可达；测试数据自建自清）
# =============================================================================
set -u
BASE="${BASE:-http://127.0.0.1:8080/api}"
MYSQL="mysql -h 127.0.0.1 -P 3306 -u root -p123456 couple_meal"

# 本脚本专用 ID 段，避免与其他 itest 脚本/其他 agent 的数据相撞
U1=10901; U2=10902
COUPLE=91091
PLAN1=94001; PLAN2=94002
CHK1=94101; CHK2=94102
IMG1=95101; IMG2=95102; IMG3=95103
TRACE1="itest_audit_trace_1"
TRACE2="itest_audit_trace_2"
TRACE3="itest_audit_trace_3"

PASS=0; FAIL=0
chk() { # desc expected got
  local desc="$1" exp="$2" got="$3"
  if [ "$exp" = "$got" ]; then echo "  PASS: $desc (=$got)"; PASS=$((PASS+1));
  else echo "  FAIL: $desc (期望 $exp 实得 $got)"; FAIL=$((FAIL+1)); fi
}
chknz() { # desc value —— 断言非空
  local desc="$1" got="$2"
  if [ -n "$got" ]; then echo "  PASS: $desc (=$got)"; PASS=$((PASS+1));
  else echo "  FAIL: $desc (期望非空 实得空)"; FAIL=$((FAIL+1)); fi
}
q() { $MYSQL -N -B -e "$1" 2>/dev/null | grep -v Warning | tr -d '\r'; }
# 兜底 cron 为 */5s，等 8s 覆盖至少一次执行
tick() { sleep 8; }

echo "===== 图片审核挂起兜底验证 ====="

# ---------- 0. 就绪探测（无 token 访问会返回 401，只要不是连不上即视为已启动） ----------
CODE=$(curl -s -o /dev/null -m 5 -w '%{http_code}' "$BASE/plan/today" 2>/dev/null)
if [ "$CODE" = "000" ] || [ -z "$CODE" ]; then
  echo "  后端未就绪（$BASE 连不上），请先启动并覆盖 cron / pending-timeout-minutes"
  echo ""
  echo "================ AUDIT 结果: PASS=0 FAIL=1 ================"
  exit 1
fi

# ---------- 1. 造数据 ----------
# 清理上次残留（先子后主，避免任何外键约束踩坑）
$MYSQL -e "
DELETE FROM meal_checkin_image WHERE id IN ($IMG1,$IMG2,$IMG3);
DELETE FROM meal_checkin WHERE id IN ($CHK1,$CHK2);
DELETE FROM meal_plan WHERE id IN ($PLAN1,$PLAN2);
DELETE FROM couple WHERE id = $COUPLE;
DELETE FROM user WHERE id IN ($U1,$U2);
" 2>&1 | grep -v Warning

$MYSQL -e "
INSERT INTO user (id,openid,nickname,create_time,update_time) VALUES
  ($U1,'itest_audit_u1','审核兜底A',NOW(),NOW()),
  ($U2,'itest_audit_u2','审核兜底B',NOW(),NOW());
INSERT INTO couple (id,user_a,user_b,remind_enabled,status,create_time,update_time) VALUES
  ($COUPLE,$U1,$U2,0,1,NOW(),NOW());
INSERT INTO meal_plan (id,couple_id,orderer_id,meal_type,source,status,plan_date,version,create_time,update_time) VALUES
  ($PLAN1,$COUPLE,$U1,1,0,3,CURDATE(),0,NOW(),NOW()),
  ($PLAN2,$COUPLE,$U1,2,0,3,CURDATE(),0,NOW(),NOW());
INSERT INTO meal_checkin (id,plan_id,couple_id,user_id,content,audit_status,create_time,update_time) VALUES
  ($CHK1,$PLAN1,$COUPLE,$U1,'兜底用例：超时图+已通过图',0,NOW(),NOW()),
  ($CHK2,$PLAN2,$COUPLE,$U1,'兜底用例：新图不误伤',0,NOW(),NOW());
-- IMG1：审核中 + 3 小时前创建 + 有 trace_id（模拟回调永久丢失）→ 应被置为屏蔽
INSERT INTO meal_checkin_image (id,checkin_id,url,thumb_url,seq,audit_status,trace_id,audited_time,create_time,update_time) VALUES
  ($IMG1,$CHK1,'/upload/200001/itest_audit_a.jpg','/upload/200001/itest_audit_a_thumb.jpg',0,0,'$TRACE1',NULL,DATE_SUB(NOW(), INTERVAL 3 HOUR),DATE_SUB(NOW(), INTERVAL 3 HOUR)),
-- IMG2：已通过（旧图）→ 必须保持 1，不能被兜底回退
  ($IMG2,$CHK1,'/upload/200001/itest_audit_b.jpg','/upload/200001/itest_audit_b_thumb.jpg',1,1,'$TRACE2',DATE_SUB(NOW(), INTERVAL 3 HOUR),DATE_SUB(NOW(), INTERVAL 3 HOUR),DATE_SUB(NOW(), INTERVAL 3 HOUR)),
-- IMG3：审核中但刚创建（未超时）→ 应保持 0
  ($IMG3,$CHK2,'/upload/200001/itest_audit_c.jpg','/upload/200001/itest_audit_c_thumb.jpg',0,0,'$TRACE3',NULL,NOW(),NOW());
" 2>&1 | grep -v Warning

echo "----- 等待兜底任务执行（cron */5s） -----"
tick

# ---------- 2. 超时图被置为屏蔽 ----------
echo "----- 1. 超时审核中 → 屏蔽 -----"
chk "IMG1 超时审核中图被置为 2" 2 "$(q "SELECT audit_status FROM meal_checkin_image WHERE id=$IMG1;")"
chknz "IMG1 audited_time 已写入" "$(q "SELECT audited_time FROM meal_checkin_image WHERE id=$IMG1 AND audited_time IS NOT NULL;")"
chk "IMG1 trace_id 未被改写" "$TRACE1" "$(q "SELECT trace_id FROM meal_checkin_image WHERE id=$IMG1;")"

# ---------- 3. 已通过不回退 ----------
echo "----- 2. 已通过不回退 -----"
chk "IMG2（已通过）仍为 1" 1 "$(q "SELECT audit_status FROM meal_checkin_image WHERE id=$IMG2;")"
chk "IMG2 audited_time 未被兜底覆盖（仍是 3 小时前）" 1 \
  "$(q "SELECT COUNT(*) FROM meal_checkin_image WHERE id=$IMG2 AND audited_time < DATE_SUB(NOW(), INTERVAL 2 HOUR);")"

# ---------- 4. 汇总态重算 ----------
echo "----- 3. 打卡汇总态重算 -----"
chk "CHK1 汇总态 = 2（含屏蔽图）" 2 "$(q "SELECT audit_status FROM meal_checkin WHERE id=$CHK1;")"

# ---------- 5. 未超时的新图不误伤 ----------
echo "----- 4. 未超时新图不误伤 -----"
chk "IMG3（新图）仍为 0" 0 "$(q "SELECT audit_status FROM meal_checkin_image WHERE id=$IMG3;")"
chk "IMG3 audited_time 仍为空" 0 "$(q "SELECT COUNT(*) FROM meal_checkin_image WHERE id=$IMG3 AND audited_time IS NOT NULL;")"
chk "CHK2 汇总态仍为 0（仍有审核中）" 0 "$(q "SELECT audit_status FROM meal_checkin WHERE id=$CHK2;")"

# ---------- 6. 幂等：再跑一轮不影响终态 ----------
echo "----- 5. 多轮执行幂等 -----"
tick
chk "IMG1 仍为 2" 2 "$(q "SELECT audit_status FROM meal_checkin_image WHERE id=$IMG1;")"
chk "IMG2 仍为 1" 1 "$(q "SELECT audit_status FROM meal_checkin_image WHERE id=$IMG2;")"
chk "CHK1 汇总态仍为 2" 2 "$(q "SELECT audit_status FROM meal_checkin WHERE id=$CHK1;")"

# ---------- 清理 ----------
$MYSQL -e "
DELETE FROM meal_checkin_image WHERE id IN ($IMG1,$IMG2,$IMG3);
DELETE FROM meal_checkin WHERE id IN ($CHK1,$CHK2);
DELETE FROM meal_plan WHERE id IN ($PLAN1,$PLAN2);
DELETE FROM couple WHERE id = $COUPLE;
DELETE FROM user WHERE id IN ($U1,$U2);
" 2>&1 | grep -v Warning

echo ""
echo "================ AUDIT 结果: PASS=$PASS FAIL=$FAIL ================"
