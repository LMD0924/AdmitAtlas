-- P1 增量能力：院校收藏、方案只读分享。
-- 严禁修改 V1~V5（已应用迁移内容变更会触发 Flyway checksum mismatch）。
-- DDL 同时兼容 H2 2.x（MODE=MySQL）与 MySQL 8：无 ENGINE / JSON 列 / 自增序列。

CREATE TABLE user_favorite (
  id          BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  school_code VARCHAR(10) NOT NULL,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT uk_favorite UNIQUE (user_id, school_code)
);
CREATE INDEX idx_favorite_user ON user_favorite (user_id, created_at);

CREATE TABLE plan_share (
  share_token VARCHAR(48) NOT NULL,
  plan_id     BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  mask_owner  SMALLINT    NOT NULL DEFAULT 1,
  expires_at  DATETIME,
  created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  revoked     SMALLINT    NOT NULL DEFAULT 0,
  PRIMARY KEY (share_token)
);
CREATE INDEX idx_share_plan ON plan_share (plan_id, revoked);
