-- =====================================================================
-- 任务一：盲索引去关联化（Blind Index Linkage）数据库迁移脚本
-- =====================================================================
-- 适用场景：sys_user_credential 表从 user_id(主键) 迁移到 link_token(主键)
--
-- ⚠️ 当前数据库状态：
--   sys_user_credential 表主键是 user_id，没有 link_token 列
--   sys_user 表的 password 字段保存着 BCrypt 密文
--
-- 迁移策略（一次性完成，无需中间启动应用）：
--   1. 添加 link_token 列
--   2. 删除旧主键约束和 user_id 列
--   3. 设置 link_token 为新主键
--   4. 启动应用时，DataMigrationRunner.migrateAllLegacyPasswords()
--      会自动从 sys_user.password 读取密码并写入 sys_user_credential
--
-- ⚠️ 执行前请务必备份数据库！
-- =====================================================================

-- 步骤0：安全备份（建议执行）
-- CREATE TABLE sys_user_credential_backup_blindidx AS SELECT * FROM sys_user_credential;
-- CREATE TABLE sys_user_backup_blindidx AS SELECT * FROM sys_user;

-- 步骤1：添加 link_token 列（VARCHAR(64)，用于存储 HMAC-SHA256 哈希值）
ALTER TABLE sys_user_credential ADD COLUMN link_token VARCHAR(64);

-- 步骤2：删除旧主键约束
ALTER TABLE sys_user_credential DROP PRIMARY KEY;

-- 步骤3：删除旧的 user_id 列（彻底切断明文ID关联）
ALTER TABLE sys_user_credential DROP COLUMN user_id;

-- 步骤4：将 link_token 设为新主键
ALTER TABLE sys_user_credential ADD PRIMARY KEY (link_token);

-- 步骤5：验证表结构
-- 期望结果：link_token 为 PRI，user_id 列已不存在
-- DESC sys_user_credential;

-- =====================================================================
-- 迁移完成后，sys_user_credential 表结构如下：
-- +------------------+--------------+------+-----+---------+-------+
-- | Field            | Type         | Null | Key | Default | Extra |
-- +------------------+--------------+------+-----+---------+-------+
-- | link_token       | varchar(64)  | NO   | PRI | NULL    |       |
-- | password         | varchar(255) | NO   |     | NULL    |       |
-- | last_login_time  | datetime     | YES  |     | NULL    |       |
-- | last_login_ip    | varchar(64)  | YES  |     | NULL    |       |
-- | create_time      | datetime     | YES  |     | NULL    |       |
-- | update_time      | datetime     | YES  |     | NULL    |       |
-- +------------------+--------------+------+-----+---------+-------+
--
-- ⚠️ 重要：执行完本脚本后，sys_user_credential 表是空的（旧数据已随 user_id 列删除）
--   请启动后端应用，DataMigrationRunner.migrateAllLegacyPasswords() 会自动：
--   1. 读取 sys_user 表所有用户的 password（BCrypt 密文）
--   2. 计算 link_token = HMAC_SHA256(user_id, secret)
--   3. 将 (link_token, password) 写入 sys_user_credential
--
-- 安全效果：拖库后凭证表中只有无规律的 link_token 和 BCrypt 密文，
-- 缺少服务器 blind-index-secret 时无法将密码对应到具体用户。
-- =====================================================================
