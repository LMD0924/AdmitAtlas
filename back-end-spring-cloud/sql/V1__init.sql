-- AdmitAtlas initial schema (MySQL 8.0+)
-- Flyway: V1__init.sql
-- 约定：位次(min_rank)是唯一可跨年比较的字段；所有事实表带 data_version 与留证字段。

CREATE DATABASE IF NOT EXISTS admitatlas DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE admitatlas;

CREATE TABLE dim_province (
  province_code   VARCHAR(6)  NOT NULL COMMENT '行政区划代码',
  province_name   VARCHAR(32) NOT NULL,
  exam_mode       VARCHAR(24) NOT NULL COMMENT 'TRADITIONAL/GROUP_3_3/GROUP_3_1_2',
  policy_version  VARCHAR(32) DEFAULT NULL,
  PRIMARY KEY (province_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='省份维度';

CREATE TABLE dim_school (
  school_code  VARCHAR(10) NOT NULL,
  school_name  VARCHAR(128) NOT NULL,
  supervisor   VARCHAR(128) DEFAULT NULL COMMENT '主管部门',
  province_code VARCHAR(6) DEFAULT NULL,
  city         VARCHAR(64)  DEFAULT NULL,
  tier_tags    VARCHAR(128) DEFAULT NULL COMMENT '985/211/双一流',
  school_type  VARCHAR(32)  DEFAULT NULL COMMENT '综合/理工/师范...',
  level        VARCHAR(32)  DEFAULT NULL COMMENT '本科/专科',
  intro        TEXT,
  PRIMARY KEY (school_code),
  KEY idx_province (province_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高校档案';

CREATE TABLE dim_major (
  major_code   VARCHAR(16) NOT NULL,
  major_name   VARCHAR(128) NOT NULL,
  category     VARCHAR(64)  DEFAULT NULL COMMENT '学科门类',
  major_class  VARCHAR(64)  DEFAULT NULL COMMENT '专业类',
  degree_years SMALLINT     DEFAULT NULL,
  PRIMARY KEY (major_code),
  KEY idx_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='专业目录';

CREATE TABLE dim_subject_requirement (
  req_code    VARCHAR(24) NOT NULL,
  req_name    VARCHAR(128) NOT NULL COMMENT '物理必选/物化绑定/不限',
  req_expr    VARCHAR(256) DEFAULT NULL COMMENT '可解析的选科表达式',
  PRIMARY KEY (req_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='选科要求字典';

CREATE TABLE fact_admission_plan (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  province_code  VARCHAR(6)  NOT NULL,
  exam_year      SMALLINT    NOT NULL,
  category       VARCHAR(16) NOT NULL,
  batch          VARCHAR(16) NOT NULL,
  school_code    VARCHAR(10) NOT NULL,
  group_code     VARCHAR(32) DEFAULT NULL,
  major_code     VARCHAR(16) DEFAULT NULL,
  req_code       VARCHAR(24) DEFAULT NULL,
  plan_count     INT         DEFAULT NULL,
  data_version   VARCHAR(32) NOT NULL,
  source_url     VARCHAR(512) DEFAULT NULL,
  audit_status   TINYINT     NOT NULL DEFAULT 0,
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_plan (province_code, exam_year, category, school_code),
  KEY idx_version (data_version, audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='招生计划';

CREATE TABLE fact_admission_score (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  province_code  VARCHAR(6)  NOT NULL,
  exam_year      SMALLINT    NOT NULL,
  category       VARCHAR(16) NOT NULL,
  batch          VARCHAR(16) NOT NULL,
  school_code    VARCHAR(10) NOT NULL,
  group_code     VARCHAR(32) DEFAULT NULL,
  major_code     VARCHAR(16) DEFAULT NULL,
  min_score      SMALLINT    NOT NULL COMMENT '仅展示，不用于跨年比较',
  min_rank       INT         NOT NULL COMMENT '最低录取位次，核心比较字段',
  avg_score      SMALLINT    DEFAULT NULL,
  max_score      SMALLINT    DEFAULT NULL,
  plan_count     INT         DEFAULT NULL,
  actual_count   INT         DEFAULT NULL,
  data_version   VARCHAR(32) NOT NULL,
  source_url     VARCHAR(512) NOT NULL COMMENT '数据留证',
  source_hash    VARCHAR(64)  DEFAULT NULL,
  audit_status   TINYINT     NOT NULL DEFAULT 0 COMMENT '0待审/1通过/2驳回',
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id, exam_year),
  KEY idx_query (province_code, exam_year, category, batch, min_rank),
  KEY idx_school (school_code, exam_year),
  KEY idx_version (data_version, audit_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='录取投档数据'
PARTITION BY RANGE (exam_year) (
  PARTITION p2022 VALUES LESS THAN (2023),
  PARTITION p2023 VALUES LESS THAN (2024),
  PARTITION p2024 VALUES LESS THAN (2025),
  PARTITION p2025 VALUES LESS THAN (2026),
  PARTITION p2026 VALUES LESS THAN (2027),
  PARTITION pmax  VALUES LESS THAN MAXVALUE
);

CREATE TABLE fact_score_segment (
  id                BIGINT NOT NULL AUTO_INCREMENT,
  province_code     VARCHAR(6)  NOT NULL,
  exam_year         SMALLINT    NOT NULL,
  category          VARCHAR(16) NOT NULL,
  score             SMALLINT    NOT NULL,
  segment_count     INT         NOT NULL DEFAULT 0 COMMENT '本段人数',
  cumulative_count  INT         NOT NULL COMMENT '累计人数 = 位次',
  data_version      VARCHAR(32) NOT NULL,
  source_url        VARCHAR(512) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_segment (province_code, exam_year, category, score),
  KEY idx_score (province_code, exam_year, category, score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='一分一段表';

CREATE TABLE fact_batch_line (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  province_code  VARCHAR(6)  NOT NULL,
  exam_year      SMALLINT    NOT NULL,
  category       VARCHAR(16) NOT NULL,
  batch          VARCHAR(16) NOT NULL,
  line_score     SMALLINT    NOT NULL,
  data_version   VARCHAR(32) NOT NULL,
  source_url     VARCHAR(512) DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_line (province_code, exam_year, category, batch)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次线/省控线';

CREATE TABLE rule_policy (
  id                 BIGINT NOT NULL AUTO_INCREMENT,
  province_code      VARCHAR(6)  NOT NULL,
  exam_year          SMALLINT    NOT NULL,
  exam_mode          VARCHAR(24) NOT NULL,
  volunteer_unit     VARCHAR(24) NOT NULL COMMENT 'SCHOOL_MAJOR/SCHOOL_GROUP/MAJOR_SCHOOL',
  volunteer_count    INT         NOT NULL COMMENT '可填志愿数',
  group_major_count  INT         DEFAULT NULL COMMENT '每组内专业数',
  batches            VARCHAR(128) DEFAULT NULL,
  sort_fields        JSON        DEFAULT NULL COMMENT '同分排序字段有序序列',
  subject_rules      JSON        DEFAULT NULL COMMENT '选科与单科门槛',
  effective_from     DATE        DEFAULT NULL,
  data_version       VARCHAR(32) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_policy (province_code, exam_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿填报规则配置';

CREATE TABLE student_profile (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  username       VARCHAR(64)  NOT NULL,
  password_hash  VARCHAR(256) NOT NULL,
  province_code  VARCHAR(6)   DEFAULT NULL,
  role           VARCHAR(24)  NOT NULL DEFAULT 'STUDENT',
  created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

CREATE TABLE student_score (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  user_id        BIGINT      NOT NULL,
  province_code  VARCHAR(6)  NOT NULL,
  exam_year      SMALLINT    NOT NULL,
  exam_mode      VARCHAR(24) NOT NULL,
  category       VARCHAR(16) NOT NULL,
  subject_combo  JSON        DEFAULT NULL,
  total_score    SMALLINT    NOT NULL,
  subject_scores JSON        DEFAULT NULL COMMENT '单科分，仅用于同分排序与门槛',
  rank_value     INT         DEFAULT NULL COMMENT '换算后的省排名',
  rank_estimated TINYINT     NOT NULL DEFAULT 0 COMMENT '1=插值估算',
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_user (user_id, exam_year)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='成绩档案';

CREATE TABLE student_preference (
  id             BIGINT NOT NULL AUTO_INCREMENT,
  user_id        BIGINT NOT NULL,
  regions        JSON   DEFAULT NULL,
  major_keywords JSON   DEFAULT NULL,
  school_tiers   JSON   DEFAULT NULL,
  priority       VARCHAR(24) DEFAULT NULL COMMENT 'MAJOR_FIRST/SCHOOL_FIRST',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿偏好';

CREATE TABLE match_task (
  task_id        VARCHAR(40) NOT NULL,
  user_id        BIGINT      NOT NULL,
  province_code  VARCHAR(6)  NOT NULL,
  exam_year      SMALLINT    NOT NULL,
  status         VARCHAR(24) NOT NULL COMMENT 'PENDING/RUNNING/DONE/FAILED',
  input_snapshot JSON        DEFAULT NULL,
  data_version   VARCHAR(32) DEFAULT NULL,
  created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (task_id),
  KEY idx_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='匹配任务';

CREATE TABLE suggestion_plan (
  plan_id        BIGINT NOT NULL AUTO_INCREMENT,
  user_id        BIGINT NOT NULL,
  task_id        VARCHAR(40) DEFAULT NULL,
  plan_name      VARCHAR(128) DEFAULT NULL,
  volunteer_unit VARCHAR(24)  DEFAULT NULL,
  volunteer_count INT         DEFAULT NULL,
  created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (plan_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案';

CREATE TABLE suggestion_item (
  id                 BIGINT NOT NULL AUTO_INCREMENT,
  plan_id            BIGINT NOT NULL,
  position_no        INT    NOT NULL COMMENT '志愿序号，须满足本省数量规则',
  school_code        VARCHAR(10) NOT NULL,
  school_name        VARCHAR(128) DEFAULT NULL,
  group_or_major_code VARCHAR(32) DEFAULT NULL,
  tier               VARCHAR(16) NOT NULL COMMENT 'CHONG/WEN/BAO',
  match_score        SMALLINT NOT NULL COMMENT '0-100 匹配度，禁止称录取率',
  history_min_rank   INT      DEFAULT NULL,
  reason             VARCHAR(512) DEFAULT NULL,
  PRIMARY KEY (id),
  KEY idx_plan (plan_id, position_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方案明细';
