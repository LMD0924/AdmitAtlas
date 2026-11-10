package com.admitatlas.authuser;

import com.admitatlas.api.dto.AuthDTO;
import com.admitatlas.common.BizException;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.auth.AuthContext;
import com.admitatlas.common.auth.AuthUser;
import com.admitatlas.common.auth.JwtUtil;
import com.admitatlas.common.auth.PasswordHasher;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 学生 / 管理员共用的认证服务。
 *
 * <p>注册只能创建 STUDENT；管理员账号由数据迁移内置或由已有管理员在后台创建，
 * 防止匿名接口越权提权。密码走 PBKDF2，token 走 HS256，均由 JDK 原生实现。
 */
@Service
public class AuthService {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{3,32}$");
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 64;

    /** 连续输错 5 次冻结 30 分钟；计数按用户名维度，账号不存在也计数，避免被枚举探测。 */
    public static final int MAX_FAILED_ATTEMPTS = 5;
    public static final long LOCK_MILLIS = 30L * 60 * 1000;

    private final AdmitAtlasRepository repo;
    private final JwtUtil jwt;

    public AuthService(AdmitAtlasRepository repo, JwtUtil jwt) {
        this.repo = repo;
        this.jwt = jwt;
    }

    public AuthDTO.LoginResponse login(AuthDTO.LoginRequest req) {
        if (req == null || isBlank(req.username()) || isBlank(req.password())) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名和密码不能为空");
        }
        String username = req.username().trim();
        long now = System.currentTimeMillis();

        Models.LoginAttempt attempt = repo.loginAttempt(username).orElse(null);
        if (attempt != null && attempt.lockedUntil() != null) {
            if (attempt.lockedUntil() > now) {
                long seconds = (attempt.lockedUntil() - now + 999) / 1000;
                throw new BizException(ResultCode.ACCOUNT_LOCKED,
                        "账号已冻结，请 " + seconds / 60 + " 分 " + seconds % 60 + " 秒后重试",
                        lockData(attempt.lockedUntil(), seconds));
            }
            // 冻结已到期：本次请求按全新计数处理（不提前删库，
            // 由登录成功或下一次失败写入时覆盖，避免重复 DELETE）。
            attempt = null;
        }

        Optional<Models.UserAccount> found = repo.userByUsername(username);
        boolean matched = found.isPresent()
                && PasswordHasher.matches(req.password(), found.get().passwordHash());
        if (!matched) {
            int failed = (attempt == null ? 0 : attempt.failedCount()) + 1;
            if (failed >= MAX_FAILED_ATTEMPTS) {
                long until = now + LOCK_MILLIS;
                repo.saveLoginAttempt(username, failed, until);
                throw new BizException(ResultCode.ACCOUNT_LOCKED,
                        "连续输错 " + MAX_FAILED_ATTEMPTS + " 次，账号已冻结 30 分钟",
                        lockData(until, LOCK_MILLIS / 1000));
            }
            repo.saveLoginAttempt(username, failed, null);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("remainingAttempts", MAX_FAILED_ATTEMPTS - failed);
            data.put("maxAttempts", MAX_FAILED_ATTEMPTS);
            throw new BizException(ResultCode.UNAUTHORIZED,
                    "用户名或密码错误，还可尝试 " + (MAX_FAILED_ATTEMPTS - failed) + " 次", data);
        }

        Models.UserAccount account = found.get();
        if (!"ACTIVE".equals(account.status())) {
            throw new BizException(ResultCode.FORBIDDEN, "该账号已停用，请联系管理员");
        }
        repo.clearLoginAttempt(username);
        repo.touchLogin(account.id());
        return new AuthDTO.LoginResponse(jwt.issue(account), jwt.ttlSeconds(), "Bearer",
                toView(AuthUser.from(account), false));
    }

    private static Map<String, Object> lockData(long until, long seconds) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lockedUntil", until);
        data.put("retryAfterSeconds", seconds);
        data.put("maxAttempts", MAX_FAILED_ATTEMPTS);
        return data;
    }

    /** 自助注册：角色固定 STUDENT，省份缺省湖北（P1 试点省）。 */
    public AuthDTO.UserView register(AuthDTO.RegisterRequest req) {
        if (req == null || isBlank(req.username()) || isBlank(req.password())) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名和密码不能为空");
        }
        String username = req.username().trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST,
                    "用户名须为 3~32 位字母、数字、下划线、点或连字符");
        }
        if (req.password().length() < MIN_PASSWORD || req.password().length() > MAX_PASSWORD) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码长度须为 8~64 位");
        }
        if (repo.userByUsername(username).isPresent()) {
            throw new BizException(ResultCode.CONFLICT, "该用户名已被注册");
        }
        String province = isBlank(req.provinceCode()) ? "42" : req.provinceCode().trim();
        String displayName = isBlank(req.displayName()) ? username : req.displayName().trim();
        long id = repo.createUser(username, PasswordHasher.hash(req.password()),
                displayName, province, AuthUser.ROLE_STUDENT);
        repo.writeAudit(id, username, "REGISTER", "USER", String.valueOf(id),
                "自助注册学生账号");
        Models.UserAccount created = repo.userById(id).orElseThrow();
        return toView(AuthUser.from(created), false);
    }

    /** 退出登录：递增 token_version，使已签发 token 全部失效。 */
    public void logout() {
        AuthUser user = AuthContext.require();
        repo.bumpTokenVersion(user.id());
    }

    /** 游客返回 guest=true，公开接口据此展示「登录后可保存」提示。 */
    public AuthDTO.UserView current() {
        AuthUser user = AuthContext.get();
        return user == null ? guest() : toView(user, false);
    }

    public static AuthDTO.UserView guest() {
        return new AuthDTO.UserView(0L, "guest", "游客", "GUEST", "42", false, true);
    }

    public static AuthDTO.UserView toView(AuthUser user, boolean guest) {
        return new AuthDTO.UserView(user.id(), user.username(),
                user.displayName() == null ? user.username() : user.displayName(),
                user.role(), user.provinceCode(), user.isAdmin(), guest);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
