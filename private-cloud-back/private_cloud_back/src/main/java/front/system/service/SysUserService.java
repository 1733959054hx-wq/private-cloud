package front.system.service;

import com.alibaba.fastjson.JSON;
import front.cache.service.TwoLevelCacheService;
import front.hxconfig.SecurityHashUtil;
import front.system.entity.*;
import front.system.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class SysUserService {

    private static final Logger log = LoggerFactory.getLogger(SysUserService.class);

    private static final String USER_INFO_PREFIX = "sys:user:info:";
    private static final String USER_NAME_INDEX_PREFIX = "sys:user:name:";
    private static final String USER_ROLES_PREFIX = "sys:user:roles:";
    private static final String USER_PERMS_PREFIX = "sys:user:perms:";
    private static final String CREDENTIAL_CACHE_PREFIX = "sys:credential:";
    private static final long USER_CACHE_TTL_HOURS = 3;
    private static final int TTL_JITTER_MINUTES = 30;
    private static final long CREDENTIAL_CACHE_TTL_MINUTES = 5;
    private static final Random RANDOM = new Random();

    /** L1 Caffeine 缓存名 */
    private static final String L1_CACHE_NAME = "userPermissions";

    @Autowired
    private SysUserRepository userRepository;

    @Autowired
    private SysUserCredentialRepository credentialRepository;

    @Autowired
    private SysUserRoleRepository userRoleRepository;

    @Autowired
    private SysRoleRepository roleRepository;

    @Autowired
    private SysRolePermissionRepository rolePermissionRepository;

    @Autowired
    private SysPermissionRepository permissionRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private TwoLevelCacheService twoLevelCacheService;

    @Autowired
    private SecurityHashUtil securityHashUtil;

    public SysUser findByUsername(String username) {
        try {
            String nameKey = USER_NAME_INDEX_PREFIX + username;
            Object userIdObj = redisTemplate.opsForValue().get(nameKey);
            // 缓存空值标记，防止缓存穿透
            if ("__NULL__".equals(userIdObj)) {
                throw new RuntimeException("用户不存在");
            }
            if (userIdObj != null) {
                Long userId = Long.valueOf(userIdObj.toString());
                SysUser cached = getUserFromCache(userId);
                if (cached != null) {
                    log.info("[二级缓存] L2(Redis)命中(用户信息), userId={}", userId);
                    return cached;
                }
            }
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("用户不存在")) throw e;
            log.warn("[Redis缓存] 按用户名查找缓存失败: {}", e.getMessage());
        }

        log.info("[二级缓存] 缓存未命中，回源DB, username={}", username);
        SysUser user = userRepository.findByUsernameAndDeleted(username, 0)
                .orElse(null);

        if (user == null) {
            // 用户不存在时缓存空值，防止缓存穿透
            try {
                redisTemplate.opsForValue().set(USER_NAME_INDEX_PREFIX + username, "__NULL__", 5, TimeUnit.MINUTES);
            } catch (Exception e) {
                log.warn("[Redis缓存] 写入空值缓存失败: {}", e.getMessage());
            }
            throw new RuntimeException("用户不存在");
        }

        cacheUserInfo(user);
        return user;
    }

    public SysUser findById(Long id) {
        SysUser cached = getUserFromCache(id);
        if (cached != null) {
            log.info("[二级缓存] L2(Redis)命中(用户信息), userId={}", id);
            return cached;
        }

        log.info("[二级缓存] 缓存未命中，回源DB, userId={}", id);
        SysUser user = userRepository.findById(id)
                .filter(u -> u.getDeleted() == 0)
                .orElse(null);

        if (user != null) {
            cacheUserInfo(user);
        }
        return user;
    }

    public List<SysRole> getUserRoles(Long userId) {
        String key = USER_ROLES_PREFIX + userId;
        // L1：Caffeine
        try {
            Cache l1 = twoLevelCacheService.getCacheManager().getCache(L1_CACHE_NAME);
            if (l1 != null) {
                Cache.ValueWrapper wrapper = l1.get(key);
                if (wrapper != null && wrapper.get() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<SysRole> l1Result = (List<SysRole>) wrapper.get();
                    log.info("[二级缓存] L1(Caffeine)命中, key={}, size={}", key, l1Result.size());
                    return l1Result;
                }
            }
        } catch (Exception e) {
            log.warn("[L1] 读取用户角色缓存失败: {}", e.getMessage());
        }
        // L2：Redis
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                List<SysRole> roles = JSON.parseArray(JSON.toJSONString(cached), SysRole.class);
                log.info("[二级缓存] L2(Redis)命中并回填L1, key={}, size={}", key, roles.size());
                putToL1(key, roles);
                return roles;
            }
        } catch (Exception e) {
            log.warn("[L2] 读取用户角色缓存失败: {}", e.getMessage());
        }

        log.info("[二级缓存] 缓存未命中，回源DB, key={}", key);
        List<Long> roleIds = userRoleRepository.findByUserId(userId)
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<SysRole> roles = roleRepository.findAllById(roleIds);

        try {
            redisTemplate.opsForValue().set(key, roles, randomTtlHours(), TimeUnit.HOURS);
            putToL1(key, roles);
            log.info("[二级缓存] 写入用户角色缓存, key={}, size={}", key, roles.size());
        } catch (Exception e) {
            log.warn("[二级缓存] 写入用户角色缓存失败: {}", e.getMessage());
        }
        return roles;
    }

    public List<String> getUserRoleCodes(Long userId) {
        return getUserRoles(userId).stream()
                .map(SysRole::getRoleCode)
                .collect(Collectors.toList());
    }

    public List<String> getUserPermissionCodes(Long userId) {
        String key = USER_PERMS_PREFIX + userId;
        // L1：Caffeine
        try {
            Cache l1 = twoLevelCacheService.getCacheManager().getCache(L1_CACHE_NAME);
            if (l1 != null) {
                Cache.ValueWrapper wrapper = l1.get(key);
                if (wrapper != null && wrapper.get() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> l1Result = (List<String>) wrapper.get();
                    log.info("[二级缓存] L1(Caffeine)命中, key={}, size={}", key, l1Result.size());
                    return l1Result;
                }
            }
        } catch (Exception e) {
            log.warn("[L1] 读取用户权限缓存失败: {}", e.getMessage());
        }
        // L2：Redis
        try {
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                List<String> permCodes = JSON.parseArray(JSON.toJSONString(cached), String.class);
                log.info("[二级缓存] L2(Redis)命中并回填L1, key={}, size={}", key, permCodes.size());
                putToL1(key, permCodes);
                return permCodes;
            }
        } catch (Exception e) {
            log.warn("[L2] 读取用户权限缓存失败: {}", e.getMessage());
        }

        log.info("[二级缓存] 缓存未命中，回源DB, key={}", key);
        List<Long> roleIds = userRoleRepository.findByUserId(userId)
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<Long> permissionIds = roleIds.stream()
                .flatMap(roleId -> rolePermissionRepository.findByRoleId(roleId).stream())
                .map(SysRolePermission::getPermissionId)
                .distinct()
                .collect(Collectors.toList());
        List<String> permCodes = permissionRepository.findAllById(permissionIds).stream()
                .map(SysPermission::getPermissionCode)
                .collect(Collectors.toList());

        try {
            redisTemplate.opsForValue().set(key, permCodes, randomTtlHours(), TimeUnit.HOURS);
            putToL1(key, permCodes);
            log.info("[二级缓存] 写入用户权限缓存, key={}, size={}", key, permCodes.size());
        } catch (Exception e) {
            log.warn("[二级缓存] 写入用户权限缓存失败: {}", e.getMessage());
        }
        return permCodes;
    }

    @Transactional
    public SysUser createUser(SysUser user, List<Long> roleIds) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("用户名已存在");
        }
        String rawPassword = user.getPassword();
        user.setPassword(null);
        SysUser saved = userRepository.save(user);

        // 密码存入独立凭证表（使用 HMAC linkToken 去关联化）
        String linkToken = securityHashUtil.computeLinkToken(saved.getId());
        SysUserCredential credential = new SysUserCredential();
        credential.setLinkToken(linkToken);
        credential.setPassword(passwordEncoder.encode(rawPassword));
        credentialRepository.save(credential);

        if (roleIds != null) {
            for (Long roleId : roleIds) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(saved.getId());
                userRole.setRoleId(roleId);
                userRoleRepository.save(userRole);
            }
        }
        evictUserCache(saved.getId(), saved.getUsername());
        return saved;
    }

    @Transactional
    public SysUser updateUser(Long id, SysUser updates, List<Long> roleIds) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        String oldUsername = user.getUsername();
        if (updates.getRealName() != null) user.setRealName(updates.getRealName());
        if (updates.getEmail() != null) user.setEmail(updates.getEmail());
        if (updates.getPhone() != null) user.setPhone(updates.getPhone());
        if (updates.getAvatar() != null) user.setAvatar(updates.getAvatar());
        if (updates.getDepartmentId() != null) user.setDepartmentId(updates.getDepartmentId());
        if (updates.getStatus() != null) user.setStatus(updates.getStatus());

        if (roleIds != null) {
            userRoleRepository.deleteByUserId(id);
            for (Long roleId : roleIds) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(id);
                userRole.setRoleId(roleId);
                userRoleRepository.save(userRole);
            }
        }
        SysUser saved = userRepository.save(user);
        evictUserCache(id, oldUsername);
        return saved;
    }

    @Transactional
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        String linkToken = securityHashUtil.computeLinkToken(userId);
        SysUserCredential credential = credentialRepository.findById(linkToken)
                .orElseThrow(() -> new RuntimeException("用户凭证不存在"));
        if (!passwordEncoder.matches(oldPassword, credential.getPassword())) {
            throw new RuntimeException("原密码错误");
        }
        credential.setPassword(passwordEncoder.encode(newPassword));
        credentialRepository.save(credential);
        evictCredentialCache(linkToken);
        SysUser user = userRepository.findById(userId).orElse(null);
        if (user != null) evictUserCache(userId, user.getUsername());
    }

    @Transactional
    public void deleteUser(Long id) {
        SysUser user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setDeleted(1);
        userRepository.save(user);
        String linkToken = securityHashUtil.computeLinkToken(id);
        credentialRepository.deleteById(linkToken);
        evictCredentialCache(linkToken);
        evictUserCache(id, user.getUsername());
    }

    /**
     * 获取用户密码（通过 HMAC linkToken 从独立凭证表读取）
     * 整合 L2 Redis 缓存：查询结果缓存 5 分钟，改密/删除用户时失效
     */
    public String getUserPassword(Long userId) {
        if (userId == null) return null;
        String linkToken = securityHashUtil.computeLinkToken(userId);
        String cacheKey = CREDENTIAL_CACHE_PREFIX + linkToken;

        // L2 Redis 缓存读取
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                log.debug("[二级缓存] L2(Redis)命中(用户凭证), userId={}", userId);
                return cached.toString();
            }
        } catch (Exception e) {
            log.warn("[Redis缓存] 读取用户凭证缓存失败: {}", e.getMessage());
        }

        String password = credentialRepository.findById(linkToken)
                .map(SysUserCredential::getPassword)
                .orElse(null);

        // 写入 L2 Redis 缓存（敏感信息短 TTL，降低拖库风险）
        if (password != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, password, CREDENTIAL_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
                log.debug("[二级缓存] 写入用户凭证缓存, userId={}", userId);
            } catch (Exception e) {
                log.warn("[Redis缓存] 写入用户凭证缓存失败: {}", e.getMessage());
            }
        }
        return password;
    }

    /**
     * 兼容旧数据：若 sys_user 中仍有密码且凭证表不存在，则迁移到凭证表（使用 linkToken 去关联化）
     */
    public String migratePasswordIfNeeded(SysUser user) {
        if (user == null || user.getId() == null) {
            return null;
        }
        String linkToken = securityHashUtil.computeLinkToken(user.getId());
        // 先查凭证表是否已有记录
        SysUserCredential credential = credentialRepository.findById(linkToken).orElse(null);
        if (credential != null) {
            return credential.getPassword();
        }
        // 凭证表不存在，尝试从 sys_user.password 迁移
        if (user.getPassword() == null || user.getPassword().isEmpty()) {
            return null;
        }
        credential = new SysUserCredential();
        credential.setLinkToken(linkToken);
        // sys_user.password 已经是 BCrypt 哈希，直接原样拷贝即可，
        // 不要再次 encode，否则登录比对会失败
        credential.setPassword(user.getPassword());
        credentialRepository.save(credential);
        return credential.getPassword();
    }

    /**
     * 应用启动时执行一次：把所有用户从 sys_user 迁移到 sys_user_credential（使用 linkToken 去关联化），
     * 保证已存在的老用户无需重新登录就能正常走新登录逻辑。
     * 单条独立事务，单条失败不影响其它记录。
     */
    public void migrateAllLegacyPasswords() {
        try {
            List<SysUser> users = userRepository.findAll();
            int migrated = 0;
            for (SysUser u : users) {
                if (u == null || u.getId() == null) continue;
                if (u.getPassword() == null || u.getPassword().isEmpty()) continue;
                String linkToken = securityHashUtil.computeLinkToken(u.getId());
                if (credentialRepository.existsById(linkToken)) continue;
                try {
                    migrateSingleLegacyPassword(u.getId(), u.getPassword());
                    migrated++;
                } catch (Exception e) {
                    log.warn("[SysUserService] 迁移用户 {} 密码失败: {}", u.getId(), e.getMessage());
                }
            }
            if (migrated > 0) {
                log.info("[SysUserService] 已迁移 {} 个用户的密码到 sys_user_credential (linkToken)", migrated);
            }
        } catch (Exception e) {
            log.warn("[SysUserService] 迁移旧密码失败: {}", e.getMessage());
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void migrateSingleLegacyPassword(Long userId, String bcryptHash) {
        String linkToken = securityHashUtil.computeLinkToken(userId);
        SysUserCredential credential = new SysUserCredential();
        credential.setLinkToken(linkToken);
        credential.setPassword(bcryptHash);
        credentialRepository.save(credential);
    }

    /**
     * 更新用户登录审计信息（通过 linkToken 定位凭证记录）
     */
    @Transactional
    public void recordLoginInfo(Long userId, String ip) {
        String linkToken = securityHashUtil.computeLinkToken(userId);
        SysUserCredential credential = credentialRepository.findById(linkToken).orElse(null);
        if (credential == null) return;
        credential.setLastLoginTime(LocalDateTime.now());
        if (ip != null && !ip.isBlank()) {
            credential.setLastLoginIp(ip.length() > 64 ? ip.substring(0, 64) : ip);
        }
        credentialRepository.save(credential);
    }

    public List<SysUser> getAllActiveUsers() {
        return userRepository.findAll().stream()
                .filter(u -> u.getDeleted() == 0)
                .collect(Collectors.toList());
    }

    @Transactional
    public void updateWorkspaceLayout(Long userId, String layoutJson) {
        SysUser user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("用户不存在"));
        user.setWorkspaceLayout(layoutJson);
        userRepository.save(user);
        evictUserCache(userId, user.getUsername());
    }

    private SysUser getUserFromCache(Long userId) {
        try {
            String key = USER_INFO_PREFIX + userId;
            Object cached = redisTemplate.opsForValue().get(key);
            if (cached != null) {
                return JSON.parseObject(JSON.toJSONString(cached), SysUser.class);
            }
        } catch (Exception e) {
            log.warn("[Redis缓存] 读取用户信息缓存失败: {}", e.getMessage());
        }
        return null;
    }

    private void cacheUserInfo(SysUser user) {
        try {
            long ttl = randomTtlHours();
            String infoKey = USER_INFO_PREFIX + user.getId();
            redisTemplate.opsForValue().set(infoKey, user, ttl, TimeUnit.HOURS);
            String nameKey = USER_NAME_INDEX_PREFIX + user.getUsername();
            redisTemplate.opsForValue().set(nameKey, user.getId(), ttl, TimeUnit.HOURS);
            log.info("[二级缓存] 写入用户信息缓存, userId={}, ttl={}h", user.getId(), ttl);
        } catch (Exception e) {
            log.warn("[Redis缓存] 写入用户信息缓存失败: {}", e.getMessage());
        }
    }

    private void evictCredentialCache(String linkToken) {
        try {
            redisTemplate.delete(CREDENTIAL_CACHE_PREFIX + linkToken);
            log.debug("[二级缓存] 失效用户凭证缓存, linkToken={}", linkToken);
        } catch (Exception e) {
            log.warn("[Redis缓存] 失效用户凭证缓存失败: {}", e.getMessage());
        }
    }

    private void evictUserCache(Long userId, String username) {
        log.info("[二级缓存] 失效用户缓存, userId={}, username={}", userId, username);
        // 清 L1
        evictFromL1(USER_INFO_PREFIX + userId);
        evictFromL1(USER_ROLES_PREFIX + userId);
        evictFromL1(USER_PERMS_PREFIX + userId);
        // 清 L2
        try {
            redisTemplate.delete(USER_INFO_PREFIX + userId);
            redisTemplate.delete(USER_NAME_INDEX_PREFIX + username);
            redisTemplate.delete(USER_ROLES_PREFIX + userId);
            redisTemplate.delete(USER_PERMS_PREFIX + userId);
        } catch (Exception e) {
            log.warn("[L2] 清除用户缓存失败: {}", e.getMessage());
        }
    }

    private long randomTtlHours() {
        return USER_CACHE_TTL_HOURS + RANDOM.nextInt(TTL_JITTER_MINUTES) / 60;
    }

    // ============ L1 辅助方法 ============

    private void putToL1(String key, Object value) {
        try {
            Cache l1 = twoLevelCacheService.getCacheManager().getCache(L1_CACHE_NAME);
            if (l1 != null) {
                l1.put(key, value);
            }
        } catch (Exception e) {
            log.warn("[L1] 写入失败, key={}, error={}", key, e.getMessage());
        }
    }

    private void evictFromL1(String key) {
        try {
            Cache l1 = twoLevelCacheService.getCacheManager().getCache(L1_CACHE_NAME);
            if (l1 != null) {
                l1.evict(key);
            }
        } catch (Exception e) {
            log.warn("[L1] 失效失败, key={}, error={}", key, e.getMessage());
        }
    }
}
