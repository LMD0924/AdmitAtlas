-- F-C-50 院校 Markdown 资料；F-C-51 用户头像（学生 / 管理员通用）。
-- 与 dim_school 1:1：主表保留轻量检索字段，富文本资料独立存放，避免大字段拖累列表查询。

CREATE TABLE school_profile (
    school_code      VARCHAR(32) NOT NULL,
    profile_markdown TEXT,
    updated_at       DATETIME,
    updated_by       VARCHAR(64),
    PRIMARY KEY (school_code)
);

-- 用户头像 URL，存相对路径 /files/avatars/...，由 StaticResourceConfig 暴露为静态资源。
ALTER TABLE student_profile ADD COLUMN avatar_url VARCHAR(512);
