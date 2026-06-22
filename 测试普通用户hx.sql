-- ====== 新增普通用户 hx ======
-- 密码: 123456 (BCrypt加密)

-- 0. 临时禁用安全更新模式（MySQL Workbench 需要）
SET SQL_SAFE_UPDATES = 0;

-- 1. 修复可能存在的 deleted 列 NULL 问题
UPDATE sys_user SET deleted = 0 WHERE deleted IS NULL;

-- 2. 恢复安全更新模式
SET SQL_SAFE_UPDATES = 1;

-- 3. 插入用户（显式指定 deleted=0，避免 Hibernate ddl-auto=update 丢失默认值导致 NULL）
INSERT INTO sys_user (username, password, real_name, email, phone, department_id, status, deleted) VALUES 
    ('hx', '$2a$10$BrB80CeidzvI29IWfvnI.uHVxnEUKE8YVjDfXSz38SZATpqOHf50m', 'Danny', 'hx@example.com', '13800138001', 1, 1, 0)
ON DUPLICATE KEY UPDATE 
    deleted = 0, status = 1;

-- 4. 分配普通用户角色 (ROLE_USER, id=2)
INSERT INTO sys_user_role (user_id, role_id) VALUES 
    ((SELECT id FROM sys_user WHERE username = 'hx'), 2)
ON DUPLICATE KEY UPDATE role_id = 2;

-- ====== 验证 ======
SELECT u.id, u.username, u.real_name, u.deleted, r.role_code 
FROM sys_user u 
LEFT JOIN sys_user_role ur ON u.id = ur.user_id 
LEFT JOIN sys_role r ON ur.role_id = r.id 
WHERE u.username = 'hx';
