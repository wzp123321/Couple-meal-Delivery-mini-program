-- ============================================================
-- 情侣带饭点餐小程序 · 数据库结构（MySQL 8.0+）
-- 库名：couple_meal
-- 字符集：utf8mb4 / utf8mb4_0900_ai_ci（昵称、备注含 emoji 不乱码）
-- 引擎：InnoDB
--
-- 约定（对齐公司 SQL 规范）：
--   1. 主键 id BIGINT NOT NULL，**雪花 ID，禁自增**；采用 53bit 版本，
--      保证返回小程序后 JS Number 不丢精度（见 00-全局约定 D13）
--   2. 公共字段固定三列：id + create_time + update_time
--      —— 公司规范的四列含 row_update_time，它与 update_time 定义完全相同，
--         本项目无多系统数据同步场景，省去；决策见 00-全局约定 D14
--   3. 禁止 create_by / update_by / delete_flag；软删用 status 枚举
--   4. 布尔语义字段一律 _flag / _status 后缀，**禁止 is_ 开头**
--   5. 索引命名 uk_{表名}_{字段} / idx_{表名}_{字段}
--   6. 建表一律 CREATE TABLE IF NOT EXISTS，脚本可重复执行
--   7. 枚举值在字段 COMMENT 中标注，Java 侧用枚举类映射，不建字典表
--   8. 不使用外键（与公司实际脚本一致），一致性由应用层保证
-- ============================================================

CREATE DATABASE IF NOT EXISTS `couple_meal`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE `couple_meal`;

-- ------------------------------------------------------------
-- 1. 用户
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
  `id`          BIGINT      NOT NULL                 COMMENT '主键（53bit 雪花 ID）',
  `openid`      VARCHAR(64) NOT NULL                 COMMENT '微信 openid，登录唯一身份',
  `nickname`    VARCHAR(64)     NULL                 COMMENT '昵称，用户自行填写',
  `avatar`      VARCHAR(255)    NULL                 COMMENT '头像 URL',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_openid` (`openid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户（微信身份）';

-- ------------------------------------------------------------
-- 2. 情侣关系
--    规范化：user_a < user_b，保证同一对只可能有一条有效关系（uk_couple_pair）
--    解绑 = status 置 0 + unbound_time，记录保留
--    复合 = 复活原记录（status 置回 1），不新建，避免 uk_couple_pair 冲突
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `couple` (
  `id`           BIGINT      NOT NULL                COMMENT '主键（53bit 雪花 ID）',
  `user_a`       BIGINT      NOT NULL                COMMENT '较小 user_id',
  `user_b`       BIGINT      NOT NULL                COMMENT '较大 user_id',
  `nickname_a`   VARCHAR(32)     NULL                COMMENT 'A 对 B 的专属称呼',
  `nickname_b`   VARCHAR(32)     NULL                COMMENT 'B 对 A 的专属称呼',
  `anniversary`  DATE            NULL                COMMENT '纪念日',
  `theme`        VARCHAR(16) NOT NULL DEFAULT 'cream' COMMENT '主题色标识',
  `status`       TINYINT     NOT NULL DEFAULT 1      COMMENT '1有效 0已解绑',
  `unbound_time` DATETIME        NULL                COMMENT '解绑时间',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_couple_pair` (`user_a`, `user_b`),
  KEY `idx_couple_user_b` (`user_b`),
  CONSTRAINT `chk_couple_pair` CHECK (`user_a` < `user_b`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='情侣关系';

-- ------------------------------------------------------------
-- 3. 邀请码
--    6 位纯数字、24h 有效、一次性；同一用户每小时限 5 次
--    生成时双方尚未绑定，故此处不存 couple_id，绑定成功后才建 couple
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `couple_invite` (
  `id`          BIGINT      NOT NULL                 COMMENT '主键（53bit 雪花 ID）',
  `code`        CHAR(6)     NOT NULL                 COMMENT '6 位数字邀请码',
  `creator_id`  BIGINT      NOT NULL                 COMMENT '发起方 user_id',
  `status`      TINYINT     NOT NULL DEFAULT 0       COMMENT '0待使用 1已使用 2已过期 3已作废',
  `expire_time` DATETIME    NOT NULL                 COMMENT '过期时间 = 创建 + 24h',
  `used_by`     BIGINT          NULL                 COMMENT '加入方 user_id',
  `used_time`   DATETIME        NULL                 COMMENT '使用时间',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_couple_invite_code` (`code`),
  KEY `idx_couple_invite_creator_time` (`creator_id`, `create_time`) COMMENT '限频统计：一小时内生成次数'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='关系邀请码';

-- ------------------------------------------------------------
-- 4. 点餐约定（核心表）
--    source=0 点餐：orderer 发起 → 待认领 → carrier 认领
--    source=1 主动带：carrier 发起 → 待接收 → orderer 确认/婉拒
--    菜品明细见 meal_plan_item（存菜名快照，菜单改名不影响历史）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `meal_plan` (
  `id`               BIGINT      NOT NULL              COMMENT '主键（53bit 雪花 ID）',
  `couple_id`        BIGINT      NOT NULL              COMMENT '关系隔离键，只从会话取',
  `orderer_id`       BIGINT      NOT NULL              COMMENT '点餐方（想吃什么的人）',
  `carrier_id`       BIGINT          NULL              COMMENT '带饭方（认领后 / 主动带时写入）',
  `meal_type`        TINYINT     NOT NULL DEFAULT 1    COMMENT '1午饭 2晚饭（MVP 仅 1）',
  `source`           TINYINT     NOT NULL DEFAULT 0    COMMENT '0点餐 1主动带',
  `status`           TINYINT     NOT NULL DEFAULT 0    COMMENT '0待认领 1待接收 2备餐中 3已交付 4已完成 5已取消 6已过期',
  `plan_date`        DATE        NOT NULL              COMMENT '约定日期（工作日）',
  `remark`           VARCHAR(512)    NULL              COMMENT '口味/时间/忌口备注 ≤100字',
  `deliver_location` VARCHAR(64)     NULL              COMMENT '交付位置：前台/工位/门口等',
  `deliver_note`     VARCHAR(128)    NULL              COMMENT '便签留言 ≤80字',
  `claimed_time`     DATETIME        NULL              COMMENT '认领时间',
  `delivered_time`   DATETIME        NULL              COMMENT '交付时间（24h 自动完成基准）',
  `completed_time`   DATETIME        NULL              COMMENT '完成时间',
  `cancelled_time`   DATETIME        NULL              COMMENT '取消时间',
  `version`          INT         NOT NULL DEFAULT 0    COMMENT '乐观锁，防双端并发改状态',
  `create_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_meal_plan_couple_date` (`couple_id`, `plan_date`, `status`) COMMENT '今日 / 周历 / 历史主查询',
  KEY `idx_meal_plan_carrier` (`carrier_id`, `status`)                 COMMENT '「我带的饭」列表',
  KEY `idx_meal_plan_orderer` (`orderer_id`, `status`)                 COMMENT '「我点的饭」列表',
  KEY `idx_meal_plan_status_delivered` (`status`, `delivered_time`)    COMMENT '扫描：已交付超 24h 自动完成',
  KEY `idx_meal_plan_status_date` (`status`, `plan_date`)              COMMENT '扫描：次日 02:30 仍未交付转已过期'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='点餐约定';

-- ------------------------------------------------------------
-- 5. 约定菜品明细
--    存菜名快照：菜单库改名/删除后，历史约定仍显示当时的菜名
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `meal_plan_item` (
  `id`           BIGINT      NOT NULL                 COMMENT '主键（53bit 雪花 ID）',
  `plan_id`      BIGINT      NOT NULL                 COMMENT '所属约定',
  `menu_item_id` BIGINT          NULL                 COMMENT '来源菜品，手输菜名为空',
  `name`         VARCHAR(64) NOT NULL                 COMMENT '菜名快照',
  `seq`          TINYINT     NOT NULL DEFAULT 0       COMMENT '排序，1–10 道',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_meal_plan_item_plan` (`plan_id`, `seq`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='约定菜品明细（快照）';

-- ------------------------------------------------------------
-- 6. 菜单 / 忌口库
--    删除用软删除：口味画像要统计历史，硬删会丢数据
--    status 同时承担数据状态（与公司脚本一致）：0异常 1正常 2已删除
--    所有业务查询固定带 status = 1
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `menu_item` (
  `id`            BIGINT      NOT NULL                COMMENT '主键（53bit 雪花 ID）',
  `couple_id`     BIGINT      NOT NULL                COMMENT '关系隔离键',
  `name`          VARCHAR(64) NOT NULL                COMMENT '菜名 ≤20字',
  `category`      TINYINT     NOT NULL DEFAULT 1      COMMENT '1荤 2素 3汤 4主食',
  `favorite_flag` TINYINT     NOT NULL DEFAULT 0      COMMENT '0普通 1爱吃',
  `avoid_level`   TINYINT     NOT NULL DEFAULT 0      COMMENT '0无 1少吃 2不吃 3过敏（只提醒不硬拦）',
  `creator_id`    BIGINT      NOT NULL                COMMENT '谁加的菜（区分「我的」「TA的」）',
  `note`          VARCHAR(512)    NULL                COMMENT '菜谱笔记',
  `status`        TINYINT     NOT NULL DEFAULT 1      COMMENT '数据状态：0异常 1正常 2已删除',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_menu_item_couple_status_category` (`couple_id`, `status`, `category`) COMMENT '菜单库筛选',
  KEY `idx_menu_item_couple_status_name` (`couple_id`, `status`, `name`)         COMMENT '同关系内重名校验'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='菜单与忌口库';

-- ------------------------------------------------------------
-- 7. 爱心值流水
--    uk_love_value_plan_user 是幂等的核心：同一约定对同一人只可能加一次，
--    并发点「完成」由数据库兜底，不依赖应用层判断
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `love_value` (
  `id`         BIGINT      NOT NULL                  COMMENT '主键（53bit 雪花 ID）',
  `couple_id`  BIGINT      NOT NULL                  COMMENT '关系隔离键',
  `user_id`    BIGINT      NOT NULL                  COMMENT '获得方（carrier）',
  `plan_id`    BIGINT          NULL                  COMMENT '来源约定；NULL 表示非约定来源',
  `delta`      INT         NOT NULL DEFAULT 1        COMMENT '增量，正数',
  `reason`     VARCHAR(64)     NULL                  COMMENT '如：带饭完成',
  `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_love_value_plan_user` (`plan_id`, `user_id`) COMMENT '幂等：重复插入由唯一约束拒绝',
  KEY `idx_love_value_couple_user` (`couple_id`, `user_id`)   COMMENT '累计与双方贡献统计'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='爱心值流水';

-- ------------------------------------------------------------
-- 8. 采购清单
--    uk_shopping_list_plan_item 保证同一约定重复生成时不产生重复条目
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `shopping_list` (
  `id`           BIGINT       NOT NULL               COMMENT '主键（53bit 雪花 ID）',
  `plan_id`      BIGINT       NOT NULL               COMMENT '关联约定',
  `couple_id`    BIGINT       NOT NULL               COMMENT '关系隔离键',
  `item_name`    VARCHAR(128) NOT NULL               COMMENT '食材 / 商品',
  `checked_flag` TINYINT      NOT NULL DEFAULT 0     COMMENT '0未买 1已买',
  `checked_time` DATETIME         NULL               COMMENT '勾选时间',
  `seq`          TINYINT      NOT NULL DEFAULT 0     COMMENT '排序',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shopping_list_plan_item` (`plan_id`, `item_name`) COMMENT '重复生成不产生重复条目',
  KEY `idx_shopping_list_couple_checked` (`couple_id`, `checked_flag`) COMMENT '未买清单'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='采购清单';

-- ------------------------------------------------------------
-- 9. 取消日志（防错账：取消不发爱心值，但没有记录就无法复盘）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `cancel_log` (
  `id`          BIGINT       NOT NULL                COMMENT '主键（53bit 雪花 ID）',
  `plan_id`     BIGINT       NOT NULL                COMMENT '关联约定',
  `couple_id`   BIGINT       NOT NULL                COMMENT '关系隔离键',
  `operator_id` BIGINT       NOT NULL                COMMENT '谁取消的',
  `reason`      VARCHAR(255) NOT NULL                COMMENT '取消原因，必填',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cancel_log_plan` (`plan_id`)        COMMENT '一个约定只记一次取消'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='取消日志';

-- ------------------------------------------------------------
-- 10. 站内通知（红点来源）
--     uk_notification_plan_type_receiver：同一约定同一类型的通知只发一次
--     plan_id 为 NULL 的记录（每日提醒）不参与该唯一性约束
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `notification` (
  `id`          BIGINT       NOT NULL                COMMENT '主键（53bit 雪花 ID）',
  `couple_id`   BIGINT       NOT NULL                COMMENT '关系隔离键',
  `receiver_id` BIGINT       NOT NULL                COMMENT '收件人',
  `plan_id`     BIGINT           NULL                COMMENT '关联约定；每日提醒为空',
  `type`        TINYINT      NOT NULL                COMMENT '1新约定 2被认领 3已送达 4已完成 5每日提醒 6已打卡',
  `title`       VARCHAR(64)      NULL,
  `content`     VARCHAR(255)     NULL,
  `read_flag`   TINYINT      NOT NULL DEFAULT 0      COMMENT '0未读 1已读',
  `read_time`   DATETIME         NULL                COMMENT '已读时间',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_notification_plan_type_receiver` (`plan_id`, `type`, `receiver_id`) COMMENT '同约定同类型只发一次',
  KEY `idx_notification_receiver_read` (`receiver_id`, `read_flag`, `create_time`)    COMMENT '未读数 + 列表',
  KEY `idx_notification_receiver_type_time` (`receiver_id`, `type`, `create_time`)    COMMENT '每日提醒当天是否已发'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='站内通知';

-- ------------------------------------------------------------
-- 11. 吃饭打卡（饭桌相册）
--     uk_meal_checkin_plan：一个约定只能打一次卡，重复提交由数据库拒绝
--     图片明细见 meal_checkin_image（审核需按单张回写）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `meal_checkin` (
  `id`           BIGINT      NOT NULL                 COMMENT '主键（53bit 雪花 ID）',
  `plan_id`      BIGINT      NOT NULL                 COMMENT '一个约定只打一次卡',
  `couple_id`    BIGINT      NOT NULL                 COMMENT '关系隔离键',
  `user_id`      BIGINT      NOT NULL                 COMMENT '打卡人（必为接收方 = orderer）',
  `rating`       TINYINT         NULL                 COMMENT '1还差点意思 2好吃 3香',
  `content`      VARCHAR(255)    NULL                 COMMENT '点评 ≤100字',
  `reply`        VARCHAR(128)    NULL                 COMMENT '带饭方回应 ≤50字',
  `reply_by`     BIGINT          NULL                 COMMENT '回应人',
  `reply_time`   DATETIME        NULL                 COMMENT '回应时间',
  `audit_status` TINYINT     NOT NULL DEFAULT 0       COMMENT '汇总态：0审核中 1全部通过 2含屏蔽图',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_meal_checkin_plan` (`plan_id`)       COMMENT '打卡幂等',
  KEY `idx_meal_checkin_couple_time` (`couple_id`, `create_time`) COMMENT '相册按月倒序翻页'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='吃饭打卡';

-- ------------------------------------------------------------
-- 12. 打卡图片明细
--     拆表原因：mediaCheckAsync 按 trace_id 异步回写单张结果，
--     逗号串字段无法定位「3 张里是哪 1 张没过审」
--     未过审的图片不返回给前端，也不计入展示
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `meal_checkin_image` (
  `id`            BIGINT      NOT NULL                COMMENT '主键（53bit 雪花 ID）',
  `checkin_id`    BIGINT      NOT NULL                COMMENT '所属打卡',
  `url`           VARCHAR(255) NOT NULL               COMMENT '相对路径 /upload/yyyyMM/x.jpg',
  `seq`           TINYINT     NOT NULL DEFAULT 0      COMMENT '排序 0–2，最多 3 张',
  `audit_status`  TINYINT     NOT NULL DEFAULT 0      COMMENT '0审核中 1通过 2屏蔽',
  `trace_id`      VARCHAR(64)     NULL                COMMENT '微信 mediaCheckAsync 返回的追踪号',
  `audited_time`  DATETIME        NULL                COMMENT '审核回调时间',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_meal_checkin_image_checkin` (`checkin_id`, `seq`),
  UNIQUE KEY `uk_meal_checkin_image_trace` (`trace_id`) COMMENT '异步回调按 trace_id 定位单张'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='打卡图片明细（审核粒度）';

-- ============================================================
-- 设计补充说明（相对初版 PRD §7 的 9 张表）
--   PRD v0.9 起 §7 已同步为 12 张表，与本文表数、字段一一对应；
--   本段保留当时新增 / 移除的原因，作为设计依据备查。
--   新增 couple_invite       —— 邀请码语义无处落地
--   新增 meal_plan_item      —— 菜名快照 + 口味画像需按菜品聚合
--   新增 meal_checkin_image  —— 图片审核需按单张 trace_id 回写
--   移除 meal_checkin.images —— 由 meal_checkin_image 取代
--   meal_plan 增 source / 时间戳字段 / version
-- ============================================================
