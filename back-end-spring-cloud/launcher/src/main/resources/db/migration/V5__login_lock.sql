-- F-S-01 加固：登录失败锁定 + 演示账号哈希与前端一键填入值严格对齐。
-- 不修改 V1~V4（已应用迁移改内容会触发 Flyway checksum mismatch），以新增迁移修正。
-- 哈希由 PasswordHasher 从前端 LoginView.vue 演示账号的真实密码生成。

CREATE TABLE login_attempt (
  username      VARCHAR(64) NOT NULL,
  failed_count  INT          NOT NULL DEFAULT 0,
  locked_until  DATETIME,
  updated_at    DATETIME,
  PRIMARY KEY (username)
);

UPDATE student_profile SET password_hash = 'pbkdf2$120000$XufKVVNHMCshwN-Xi6tmAQ$naIvJ30aDz8ShhZHS-RhE0nAp_va3b-PCjAEcGhj-rU' WHERE username = 'student';
UPDATE student_profile SET password_hash = 'pbkdf2$120000$Plzu8RjvsS0JjpHrYvK_yQ$pWaMDQMBIAyr620MveEIK6jRUo-lRleVqTy9yAGg-Ac' WHERE username = 'admin';
UPDATE student_profile SET password_hash = 'pbkdf2$120000$BiR82eLf2-ZmEwMp2bIyAg$VY-XUtdq5BBFcucinY7nA_sCSxRy_Upb_YrzMccVsCY' WHERE username = 'demo';
