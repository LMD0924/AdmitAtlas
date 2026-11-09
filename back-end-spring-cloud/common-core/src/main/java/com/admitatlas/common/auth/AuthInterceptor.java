package com.admitatlas.common.auth;

import com.admitatlas.common.BizException;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;

/**
 * 三级访问控制：PUBLIC（游客可用）/ AUTHENTICATED（学生与管理员）/ ADMIN（仅管理员）。
 *
 * <p>采用「显式登记受保护路径」而非「默认全保护」，原因有二：
 * ① 游客可浏览首页、院校库、一分一段等公开信息（产品要求保留试用）；
 * ② 未登记也不存在的路径仍由 Spring MVC 返回真正的 404，不会被鉴权层改成 401。
 * 新增写接口时必须在 PROTECTED 或 ADMIN 列表中登记，见 docs/接口说明.md。
 *
 * <p>token 校验不止验签：还要回查账号 status、role 与 token_version，
 * 因此停用账号、重置密码、退出登录后旧 token 立即失效。
 */
public class AuthInterceptor implements HandlerInterceptor {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private static final String[] ADMIN_PATHS = {
            "/api/v1/admin/**"
    };

    private static final String[] PROTECTED_PATHS = {
            "/api/v1/student/**",
            "/api/v1/plans/**",
            "/api/v1/match/analyze",
            "/api/v1/match/tasks/**",
            "/api/v1/auth/logout",
            "/api/v1/users/profile",
            "/api/v1/users/password",
            "/api/v1/users/avatar"
    };

    private enum Level { PUBLIC, AUTHENTICATED, ADMIN }

    private final JwtUtil jwt;
    private final AdmitAtlasRepository repo;

    public AuthInterceptor(JwtUtil jwt, AdmitAtlasRepository repo) {
        this.jwt = jwt;
        this.repo = repo;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        // CORS 预检不带 Authorization，必须直接放行。
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        Level level = levelOf(request.getRequestURI());
        AuthUser user = resolveUser(request);

        if (level == Level.PUBLIC) {
            if (user != null) {
                AuthContext.set(user);
            }
            return true;
        }
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED, "请先登录后再使用该功能");
        }
        if (level == Level.ADMIN && !user.isAdmin()) {
            throw new BizException(ResultCode.FORBIDDEN, "该操作仅管理员可用");
        }
        AuthContext.set(user);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        AuthContext.clear();
    }

    private Level levelOf(String path) {
        for (String p : ADMIN_PATHS) {
            if (MATCHER.match(p, path)) {
                return Level.ADMIN;
            }
        }
        for (String p : PROTECTED_PATHS) {
            if (MATCHER.match(p, path)) {
                return Level.AUTHENTICATED;
            }
        }
        return Level.PUBLIC;
    }

    /** 解析失败一律视为游客：公开接口不受影响，受保护接口统一回 401。 */
    private AuthUser resolveUser(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        try {
            JwtUtil.Claims claims = jwt.verify(header.substring(7).trim());
            Optional<Models.UserAccount> account = repo.userById(claims.userId());
            if (account.isEmpty()) {
                return null;
            }
            Models.UserAccount a = account.get();
            if (!"ACTIVE".equals(a.status())
                    || a.tokenVersion() != claims.tokenVersion()
                    || !a.role().equals(claims.role())) {
                return null;
            }
            return AuthUser.from(a);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
