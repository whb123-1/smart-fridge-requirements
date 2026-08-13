-- 智慧冰箱与智能菜谱系统 数据库设计
-- MySQL 8.0+  utf8mb4

CREATE DATABASE IF NOT EXISTS smart_fridge DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE smart_fridge;

-- 用户
CREATE TABLE IF NOT EXISTS sys_user (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username      VARCHAR(50)  NOT NULL COMMENT '用户名',
  password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
  nickname      VARCHAR(50)  DEFAULT NULL COMMENT '昵称',
  email         VARCHAR(100) DEFAULT NULL COMMENT '邮箱',
  phone         VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  avatar        VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB COMMENT='用户表';

-- 饮食偏好
CREATE TABLE IF NOT EXISTS user_preference (
  id               BIGINT      NOT NULL AUTO_INCREMENT,
  user_id          BIGINT      NOT NULL COMMENT '用户',
  taste            VARCHAR(200) DEFAULT NULL COMMENT '口味偏好，逗号分隔：清淡,微辣,少油,低盐',
  allergy          VARCHAR(500) DEFAULT NULL COMMENT '过敏食材，逗号分隔',
  avoid_foods      VARCHAR(500) DEFAULT NULL COMMENT '忌口食材，逗号分隔',
  diet_goal        VARCHAR(20)  DEFAULT '均衡' COMMENT '饮食目标：均衡/减脂/增肌/控制热量',
  target_calories  INT          DEFAULT NULL COMMENT '每日目标热量（千卡）',
  created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at       DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB COMMENT='用户饮食偏好';

-- 冰箱分区
CREATE TABLE IF NOT EXISTS fridge_zone (
  id              BIGINT       NOT NULL AUTO_INCREMENT,
  user_id         BIGINT       NOT NULL,
  name            VARCHAR(50)  NOT NULL COMMENT '分区名称',
  zone_type       VARCHAR(20)  NOT NULL DEFAULT '冷藏区' COMMENT '冷藏区/冷冻区/保鲜区/变温区/常温区/自定义',
  target_temp     DECIMAL(5,1) DEFAULT NULL COMMENT '目标温度',
  target_humidity DECIMAL(5,1) DEFAULT NULL COMMENT '目标湿度%',
  temp_unit       VARCHAR(1)   NOT NULL DEFAULT 'C' COMMENT 'C/F，内部统一转摄氏',
  min_temp        DECIMAL(5,1) DEFAULT NULL COMMENT '建议温度下限',
  max_temp        DECIMAL(5,1) DEFAULT NULL COMMENT '建议温度上限',
  min_humidity    DECIMAL(5,1) DEFAULT NULL,
  max_humidity    DECIMAL(5,1) DEFAULT NULL,
  status          VARCHAR(20)  NOT NULL DEFAULT 'normal' COMMENT 'normal/abnormal/stale',
  last_record_at  DATETIME     DEFAULT NULL COMMENT '最近一次有效温湿度时间',
  sort            INT          NOT NULL DEFAULT 0,
  created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='冰箱分区';

-- 温湿度记录
CREATE TABLE IF NOT EXISTS zone_record (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  zone_id           BIGINT       NOT NULL,
  temp_c            DECIMAL(5,1) NOT NULL COMMENT '摄氏温度',
  humidity          DECIMAL(5,1) DEFAULT NULL COMMENT '湿度%',
  source            VARCHAR(20)  NOT NULL DEFAULT 'manual' COMMENT 'sensor/manual',
  record_time       DATETIME     NOT NULL COMMENT '记录时间',
  abnormal_seconds  INT          NOT NULL DEFAULT 0 COMMENT '异常持续秒数',
  created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_zone_time (zone_id, record_time)
) ENGINE=InnoDB COMMENT='温湿度记录';

-- 食物分类（含营养与参考保质期）
CREATE TABLE IF NOT EXISTS food_category (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  name              VARCHAR(50)  NOT NULL,
  parent_id         BIGINT       DEFAULT NULL,
  item_type         VARCHAR(20)  NOT NULL DEFAULT '食材' COMMENT '食材/零食/饮料/调味品',
  default_unit      VARCHAR(10)  DEFAULT '克' COMMENT '推荐计量单位',
  unit_type         VARCHAR(10)  NOT NULL DEFAULT 'weight' COMMENT 'count/weight/volume',
  shelf_life_days   INT          DEFAULT NULL COMMENT '未开封参考保质期(天)',
  opened_days       INT          DEFAULT NULL COMMENT '开封后参考期限(天)',
  per100g_calorie   DECIMAL(8,1) DEFAULT NULL COMMENT '每100克/毫升热量',
  protein           DECIMAL(8,1) DEFAULT NULL,
  fat               DECIMAL(8,1) DEFAULT NULL,
  carb              DECIMAL(8,1) DEFAULT NULL,
  icon              VARCHAR(50)  DEFAULT NULL,
  sort              INT          NOT NULL DEFAULT 0,
  created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_type (item_type)
) ENGINE=InnoDB COMMENT='食物分类';

-- 常见食物重量估算表
CREATE TABLE IF NOT EXISTS food_estimate (
  id            BIGINT       NOT NULL AUTO_INCREMENT,
  name          VARCHAR(50)  NOT NULL,
  unit          VARCHAR(10)  NOT NULL COMMENT '记录单位，如 个/根/盒',
  weight_grams  DECIMAL(8,1) NOT NULL COMMENT '每单位参考克数',
  category_id   BIGINT       DEFAULT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='常见食物重量估算表';

-- 库存食材
CREATE TABLE IF NOT EXISTS food_item (
  id                     BIGINT        NOT NULL AUTO_INCREMENT,
  user_id                BIGINT        NOT NULL,
  zone_id                BIGINT        DEFAULT NULL COMMENT '存放分区',
  category_id            BIGINT        DEFAULT NULL,
  name                   VARCHAR(100)  NOT NULL,
  quantity               DECIMAL(10,2) NOT NULL DEFAULT 1 COMMENT '当前数量',
  unit                   VARCHAR(10)   NOT NULL DEFAULT '个',
  unit_type              VARCHAR(10)   NOT NULL DEFAULT 'count',
  status                 VARCHAR(20)   NOT NULL DEFAULT 'in_stock' COMMENT 'in_stock/expired/consumed/discarded',
  entry_date             DATE          NOT NULL COMMENT '入库日期',
  opened_date            DATE          DEFAULT NULL COMMENT '开封日期',
  package_expiry_date    DATE          DEFAULT NULL COMMENT '包装标注保质期',
  suggested_expiry_date  DATE          DEFAULT NULL COMMENT '系统建议食用期限',
  expiry_basis           VARCHAR(20)   DEFAULT '系统估算' COMMENT '包装标注/系统估算/按参考温湿度估算',
  low_stock_threshold    DECIMAL(10,2) DEFAULT NULL COMMENT '低库存阈值',
  is_low_stock           TINYINT       NOT NULL DEFAULT 0,
  note                   VARCHAR(500)  DEFAULT NULL,
  created_at             DATETIME      DEFAULT CURRENT_TIMESTAMP,
  updated_at             DATETIME      DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted                TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user_status (user_id, status),
  KEY idx_zone (zone_id)
) ENGINE=InnoDB COMMENT='库存食材';

-- 库存变更日志
CREATE TABLE IF NOT EXISTS inventory_log (
  id                BIGINT       NOT NULL AUTO_INCREMENT,
  user_id           BIGINT       NOT NULL,
  food_item_id      BIGINT       DEFAULT NULL,
  food_name         VARCHAR(100) NOT NULL,
  change_type       VARCHAR(20)  NOT NULL COMMENT 'in/consume/expire/discard/adjust',
  change_qty        DECIMAL(10,2) NOT NULL,
  change_unit       VARCHAR(10)  DEFAULT NULL,
  before_qty        DECIMAL(10,2) DEFAULT NULL,
  after_qty         DECIMAL(10,2) DEFAULT NULL,
  related_recipe_id BIGINT       DEFAULT NULL,
  remark            VARCHAR(500) DEFAULT NULL,
  created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB COMMENT='库存变更日志';

-- 菜谱
CREATE TABLE IF NOT EXISTS recipe (
  id                  BIGINT       NOT NULL AUTO_INCREMENT,
  name                VARCHAR(100) NOT NULL,
  cover_url           VARCHAR(255) DEFAULT NULL,
  cuisine             VARCHAR(30)  DEFAULT NULL COMMENT '菜系',
  taste               VARCHAR(50)  DEFAULT NULL COMMENT '口味',
  cook_time_min       INT          DEFAULT NULL,
  difficulty          VARCHAR(10)  DEFAULT '简单',
  servings            INT          NOT NULL DEFAULT 1,
  per_serving_calorie DECIMAL(8,1) DEFAULT NULL COMMENT '单份热量(千卡)',
  description         VARCHAR(500) DEFAULT NULL,
  created_by          BIGINT       DEFAULT NULL COMMENT 'NULL=系统内置',
  status              TINYINT      NOT NULL DEFAULT 1,
  created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP,
  updated_at          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted             TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id)
) ENGINE=InnoDB COMMENT='菜谱';

-- 菜谱原料
CREATE TABLE IF NOT EXISTS recipe_ingredient (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  recipe_id    BIGINT       NOT NULL,
  name         VARCHAR(100) NOT NULL,
  quantity     DECIMAL(8,2) NOT NULL,
  unit         VARCHAR(10)  NOT NULL,
  is_essential TINYINT      NOT NULL DEFAULT 1 COMMENT '1必需/0可替代',
  alternative  VARCHAR(200) DEFAULT NULL COMMENT '替代食材，逗号分隔',
  is_condiment TINYINT      NOT NULL DEFAULT 0 COMMENT '是否调味品',
  is_staple    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否主料',
  min_scale    DECIMAL(4,2) DEFAULT NULL COMMENT '用量调整下限系数',
  max_scale    DECIMAL(4,2) DEFAULT NULL COMMENT '用量调整上限系数',
  PRIMARY KEY (id),
  KEY idx_recipe (recipe_id)
) ENGINE=InnoDB COMMENT='菜谱原料';

-- 菜谱步骤
CREATE TABLE IF NOT EXISTS recipe_step (
  id        BIGINT  NOT NULL AUTO_INCREMENT,
  recipe_id BIGINT  NOT NULL,
  step_no   INT     NOT NULL,
  content   TEXT    NOT NULL,
  cook_min  INT     DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_recipe (recipe_id)
) ENGINE=InnoDB COMMENT='菜谱步骤';

-- 收藏
CREATE TABLE IF NOT EXISTS user_recipe_favorite (
  id         BIGINT   NOT NULL AUTO_INCREMENT,
  user_id    BIGINT   NOT NULL,
  recipe_id  BIGINT   NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_recipe (user_id, recipe_id)
) ENGINE=InnoDB COMMENT='菜谱收藏';

-- 菜谱浏览/生成/制作历史
CREATE TABLE IF NOT EXISTS recipe_history (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  recipe_id   BIGINT      NOT NULL,
  action_type VARCHAR(20) NOT NULL COMMENT 'browse/generate/cook',
  servings    INT         DEFAULT 1,
  note        VARCHAR(255) DEFAULT NULL,
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_time (user_id, created_at)
) ENGINE=InnoDB COMMENT='菜谱历史';

-- 每日饮食记录
CREATE TABLE IF NOT EXISTS diet_record (
  id          BIGINT       NOT NULL AUTO_INCREMENT,
  user_id     BIGINT       NOT NULL,
  record_date DATE         NOT NULL,
  meal_type   VARCHAR(10)  NOT NULL DEFAULT '午餐' COMMENT '早餐/午餐/晚餐/加餐',
  recipe_id   BIGINT       DEFAULT NULL,
  custom_name VARCHAR(100) DEFAULT NULL,
  quantity    DECIMAL(8,2) NOT NULL DEFAULT 1 COMMENT '份数或克数',
  unit        VARCHAR(10)  NOT NULL DEFAULT '份',
  calorie     DECIMAL(8,1) DEFAULT NULL COMMENT '本次摄入热量',
  protein     DECIMAL(8,1) DEFAULT NULL,
  fat         DECIMAL(8,1) DEFAULT NULL,
  carb        DECIMAL(8,1) DEFAULT NULL,
  created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_date (user_id, record_date)
) ENGINE=InnoDB COMMENT='饮食记录';

-- 购物清单
CREATE TABLE IF NOT EXISTS shopping_list (
  id          BIGINT      NOT NULL AUTO_INCREMENT,
  user_id     BIGINT      NOT NULL,
  name        VARCHAR(50) NOT NULL COMMENT '清单名称',
  status      VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT 'pending/partial/done',
  source_type VARCHAR(20) NOT NULL DEFAULT 'manual' COMMENT 'manual/auto',
  created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
  updated_at  DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_user (user_id)
) ENGINE=InnoDB COMMENT='购物清单';

CREATE TABLE IF NOT EXISTS shopping_list_item (
  id               BIGINT       NOT NULL AUTO_INCREMENT,
  list_id          BIGINT       NOT NULL,
  food_name        VARCHAR(100) NOT NULL,
  category_id      BIGINT       DEFAULT NULL,
  quantity         DECIMAL(8,2) NOT NULL DEFAULT 1,
  unit             VARCHAR(10)  NOT NULL DEFAULT '个',
  purchased        TINYINT      NOT NULL DEFAULT 0,
  source_recipe_id BIGINT       DEFAULT NULL COMMENT '因制作某菜谱缺少而生成',
  remark           VARCHAR(200) DEFAULT NULL,
  created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_list (list_id)
) ENGINE=InnoDB COMMENT='购物清单明细';

-- 提醒
CREATE TABLE IF NOT EXISTS reminder (
  id           BIGINT       NOT NULL AUTO_INCREMENT,
  user_id      BIGINT       NOT NULL,
  food_item_id BIGINT       DEFAULT NULL,
  zone_id      BIGINT       DEFAULT NULL COMMENT '关联分区',
  type         VARCHAR(20)  NOT NULL COMMENT 'expiry/low_stock/zone_abnormal/custom',
  title        VARCHAR(100) NOT NULL,
  content      VARCHAR(500) NOT NULL,
  remind_time  DATETIME     DEFAULT NULL,
  is_read      TINYINT      NOT NULL DEFAULT 0,
  status       VARCHAR(20)  NOT NULL DEFAULT 'active' COMMENT 'active/done/dismissed',
  created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user_status (user_id, status)
) ENGINE=InnoDB COMMENT='提醒';

-- 初始分类数据
INSERT INTO food_category (name, parent_id, item_type, default_unit, unit_type, shelf_life_days, opened_days, per100g_calorie, protein, fat, carb, sort) VALUES
('肉类', NULL, '食材', '克', 'weight', 7, 2, 150, 18, 8, 0, 1),
('蔬菜', NULL, '食材', '克', 'weight', 5, 3, 30, 2, 0.5, 5, 2),
('水果', NULL, '食材', '克', 'weight', 7, 3, 55, 1, 0.5, 13, 3),
('蛋奶', NULL, '食材', '个', 'count', 30, 7, 90, 7, 6, 2, 4),
('水产', NULL, '食材', '克', 'weight', 3, 1, 120, 20, 5, 1, 5),
('豆制品', NULL, '食材', '克', 'weight', 5, 2, 80, 8, 4, 3, 6),
('主食', NULL, '食材', '克', 'weight', 180, 7, 110, 3, 0.5, 24, 7),
('零食', NULL, '零食', '包', 'count', 180, NULL, 250, 5, 10, 35, 8),
('饮料', NULL, '饮料', '瓶', 'count', 360, NULL, 45, 0, 0, 11, 9),
('调味品', NULL, '调味品', '瓶', 'count', 720, 90, 100, 0, 0, 20, 10);

-- 常见食物重量估算
INSERT INTO food_estimate (name, unit, weight_grams, category_id) VALUES
('鸡蛋', '个', 55, 4),
('番茄', '个', 150, 2),
('土豆', '个', 150, 7),
('苹果', '个', 200, 3),
('香蕉', '根', 120, 3),
('黄瓜', '根', 200, 2),
('鸡胸肉', '块', 150, 1),
('大米', '碗', 150, 7),
('牛奶', '盒', 250, 4),
('食用油', '勺', 10, 10),
('盐', '勺', 5, 10),
('生抽', '勺', 10, 10);

-- 示例菜谱
INSERT INTO recipe (name, cuisine, taste, cook_time_min, difficulty, servings, per_serving_calorie, description, created_by, status) VALUES
('番茄炒蛋', '家常菜', '清淡', 15, '简单', 2, 220, '酸甜开胃的家常快手菜', NULL, 1),
('青椒土豆丝', '家常菜', '清淡', 20, '简单', 2, 200, '脆爽下饭的经典素菜', NULL, 1),
('清炒时蔬', '家常菜', '清淡', 10, '简单', 1, 120, '少油健康的绿叶菜', NULL, 1),
('红烧鸡腿', '家常菜', '咸鲜', 40, '中等', 3, 380, '酱香浓郁的硬菜', NULL, 1),
('黄瓜炒蛋', '家常菜', '清淡', 15, '简单', 2, 180, '清爽不腻的时令小炒', NULL, 1),
('番茄蛋汤', '汤羹', '清淡', 15, '简单', 2, 150, '暖胃的经典家常汤', NULL, 1),
('牛奶燕麦粥', '早餐', '清淡', 10, '简单', 1, 320, '营养快捷的早餐选择', NULL, 1),
('苹果酸奶沙拉', '凉菜', '甜口', 5, '简单', 1, 200, '低卡轻食水果沙拉', NULL, 1);

INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '番茄', 200, '克', 1, '圣女果', 0, 1, NULL, NULL FROM recipe WHERE name = '番茄炒蛋';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '鸡蛋', 2, '个', 1, NULL, 0, 0, NULL, NULL FROM recipe WHERE name = '番茄炒蛋';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '盐', 5, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '番茄炒蛋';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '食用油', 15, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '番茄炒蛋';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 1, '番茄切块，鸡蛋打散加少许盐。', 3 FROM recipe WHERE name = '番茄炒蛋';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 2, '热锅倒油，先炒鸡蛋至凝固盛出。', 4 FROM recipe WHERE name = '番茄炒蛋';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 3, '下番茄炒出汁，倒回鸡蛋，加盐调味即可。', 5 FROM recipe WHERE name = '番茄炒蛋';

INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '土豆', 300, '克', 1, '胡萝卜', 0, 1, NULL, NULL FROM recipe WHERE name = '青椒土豆丝';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '青椒', 50, '克', 1, '彩椒', 0, 0, NULL, NULL FROM recipe WHERE name = '青椒土豆丝';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '盐', 4, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '青椒土豆丝';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '食用油', 15, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '青椒土豆丝';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 1, '土豆切丝泡水去淀粉，青椒切丝。', 5 FROM recipe WHERE name = '青椒土豆丝';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 2, '热油爆炒土豆丝至半透明。', 8 FROM recipe WHERE name = '青椒土豆丝';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 3, '加入青椒丝翻炒，加盐出锅。', 5 FROM recipe WHERE name = '青椒土豆丝';

INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '蔬菜', 300, '克', 1, '白菜,菠菜', 0, 1, NULL, NULL FROM recipe WHERE name = '清炒时蔬';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '蒜', 5, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '清炒时蔬';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '盐', 3, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '清炒时蔬';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '食用油', 10, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '清炒时蔬';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 1, '蔬菜洗净切段，蒜切片。', 3 FROM recipe WHERE name = '清炒时蔬';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 2, '热油爆香蒜片，大火快炒蔬菜。', 4 FROM recipe WHERE name = '清炒时蔬';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 3, '加盐调味，断生即可出锅。', 2 FROM recipe WHERE name = '清炒时蔬';

INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '鸡腿', 500, '克', 1, '鸡翅', 0, 1, NULL, NULL FROM recipe WHERE name = '红烧鸡腿';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '生抽', 20, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '红烧鸡腿';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '老抽', 5, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '红烧鸡腿';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '冰糖', 10, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '红烧鸡腿';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '姜', 5, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '红烧鸡腿';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 1, '鸡腿焯水去浮沫，姜切片。', 5 FROM recipe WHERE name = '红烧鸡腿';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 2, '炒糖色后下鸡腿翻炒上色。', 8 FROM recipe WHERE name = '红烧鸡腿';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 3, '加生抽、老抽和热水，小火炖 20 分钟收汁。', 25 FROM recipe WHERE name = '红烧鸡腿';

INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '黄瓜', 250, '克', 1, '西葫芦', 0, 1, NULL, NULL FROM recipe WHERE name = '黄瓜炒蛋';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '鸡蛋', 2, '个', 1, NULL, 0, 0, NULL, NULL FROM recipe WHERE name = '黄瓜炒蛋';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '盐', 4, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '黄瓜炒蛋';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '食用油', 12, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '黄瓜炒蛋';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 1, '黄瓜切片，鸡蛋打散。', 3 FROM recipe WHERE name = '黄瓜炒蛋';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 2, '先炒鸡蛋盛出，再炒黄瓜片。', 6 FROM recipe WHERE name = '黄瓜炒蛋';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 3, '合炒加盐，快速出锅。', 4 FROM recipe WHERE name = '黄瓜炒蛋';

INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '番茄', 200, '克', 1, '圣女果', 0, 1, NULL, NULL FROM recipe WHERE name = '番茄蛋汤';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '鸡蛋', 1, '个', 1, NULL, 0, 0, NULL, NULL FROM recipe WHERE name = '番茄蛋汤';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '盐', 4, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '番茄蛋汤';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '香油', 3, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '番茄蛋汤';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 1, '番茄切块加水煮开。', 6 FROM recipe WHERE name = '番茄蛋汤';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 2, '淋入蛋液搅成蛋花。', 4 FROM recipe WHERE name = '番茄蛋汤';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 3, '加盐和香油调味。', 2 FROM recipe WHERE name = '番茄蛋汤';

INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '牛奶', 250, '毫升', 1, '豆浆', 0, 1, NULL, NULL FROM recipe WHERE name = '牛奶燕麦粥';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '燕麦', 50, '克', 1, NULL, 0, 0, NULL, NULL FROM recipe WHERE name = '牛奶燕麦粥';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '蜂蜜', 10, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '牛奶燕麦粥';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 1, '牛奶加热至微沸。', 4 FROM recipe WHERE name = '牛奶燕麦粥';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 2, '加入燕麦小火煮 3 分钟。', 4 FROM recipe WHERE name = '牛奶燕麦粥';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 3, '稍凉后加蜂蜜拌匀。', 1 FROM recipe WHERE name = '牛奶燕麦粥';

INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '苹果', 200, '克', 1, '梨', 0, 1, NULL, NULL FROM recipe WHERE name = '苹果酸奶沙拉';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '酸奶', 100, '克', 1, NULL, 0, 0, NULL, NULL FROM recipe WHERE name = '苹果酸奶沙拉';
INSERT INTO recipe_ingredient (recipe_id, name, quantity, unit, is_essential, alternative, is_condiment, is_staple, min_scale, max_scale)
SELECT id, '蜂蜜', 5, '克', 0, NULL, 1, 0, 0.5, 1.5 FROM recipe WHERE name = '苹果酸奶沙拉';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 1, '苹果切块。', 2 FROM recipe WHERE name = '苹果酸奶沙拉';
INSERT INTO recipe_step (recipe_id, step_no, content, cook_min)
SELECT id, 2, '加入酸奶和蜂蜜拌匀。', 2 FROM recipe WHERE name = '苹果酸奶沙拉';
