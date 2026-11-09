-- ============================================================================
-- V3: 双身份（学生 STUDENT / 管理员 ADMIN）、登录凭据、操作审计、数据版本登记。
--
-- 兼容 H2(MySQL mode) 与 MySQL 8：不使用 ENGINE / COMMENT / JSON 列 / 内联 KEY。
-- V1 的 student_profile 已有 password_hash 与 user_role，V2 只插入了演示账号
-- id=1 且密码是占位文本；这里补齐可登录字段并内置两个演示账号。
--
-- 演示账号（全部为合成演示数据，生产环境必须删除并通过环境变量重置）：
--   student / Student@123456  -> STUDENT
--   admin   / Admin@123456    -> ADMIN
--   demo    / Demo@123456     -> STUDENT（沿用 P1 演示数据归属 id=1）
-- 密码格式：pbkdf2$<iterations>$<salt-base64url>$<hash-base64url>
--           PBKDF2WithHmacSHA256, 32-byte derived key，见 PasswordHasher。
-- ============================================================================

ALTER TABLE student_profile ADD COLUMN display_name  VARCHAR(64);
ALTER TABLE student_profile ADD COLUMN account_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE student_profile ADD COLUMN token_version INT NOT NULL DEFAULT 0;
ALTER TABLE student_profile ADD COLUMN last_login_at DATETIME;
CREATE INDEX idx_profile_role ON student_profile (user_role, account_status);

-- B 端所有写操作留痕：谁、何时、对什么、做了什么、前后值。
CREATE TABLE audit_log (
  id            BIGINT      NOT NULL,
  operator_id   BIGINT,
  operator_name VARCHAR(64),
  action        VARCHAR(64) NOT NULL,
  target_type   VARCHAR(32),
  target_id     VARCHAR(64),
  detail        VARCHAR(2000),
  created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
);
CREATE INDEX idx_audit_created ON audit_log (created_at);

-- 在线数据版本登记：演示阶段仅做公示与切换留痕，不改写事实表。
CREATE TABLE data_version_registry (
  data_version  VARCHAR(32) NOT NULL,
  status        VARCHAR(16) NOT NULL,
  coverage_note VARCHAR(512),
  demo_data     SMALLINT    NOT NULL DEFAULT 1,
  activated_by  VARCHAR(64),
  activated_at  DATETIME,
  PRIMARY KEY (data_version)
);

UPDATE student_profile
   SET password_hash  = 'pbkdf2$120000$WtyiCb4oSJtHTJVoIU1u4g$0x86ukJ640zLnvuqXXggKTc5q66mf8sc_hYC4kss27I',
       display_name   = '演示学生',
       account_status = 'ACTIVE',
       token_version  = 0
 WHERE id = 1;

INSERT INTO student_profile (id, username, password_hash, province_code, user_role,
                             display_name, account_status, token_version)
VALUES (2, 'student',
        'pbkdf2$120000$RiMVLhCa2BAg26rB2mNAng$WGs3sK3IxdzMy8-sFlhpXVwCL7LE5eRpYqr8izCi5V0',
        '42', 'STUDENT', '试点学生', 'ACTIVE', 0);

INSERT INTO student_profile (id, username, password_hash, province_code, user_role,
                             display_name, account_status, token_version)
VALUES (3, 'admin',
        'pbkdf2$120000$M3edsr5nP5dkNj7TjV8Cfg$d8dWbxAUoRxoqp4Z3q8iyI1foQ1rF8Zz3UFQc0Zw99k',
        '42', 'ADMIN', '数据管理员', 'ACTIVE', 0);

INSERT INTO data_version_registry (data_version, status, coverage_note, demo_data,
                                   activated_by, activated_at)
VALUES ('demo-2026-hb-v1', 'ACTIVE',
        '湖北 / 2026 / 物理类单省试点；全部数值为合成演示值，非真实录取数据',
        1, 'system', CURRENT_TIMESTAMP);
