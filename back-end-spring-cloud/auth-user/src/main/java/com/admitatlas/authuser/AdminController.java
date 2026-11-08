package com.admitatlas.authuser;

import com.admitatlas.api.dto.AdminDTO;
import com.admitatlas.common.BizException;
import com.admitatlas.common.Result;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.auth.AuthContext;
import com.admitatlas.common.auth.AuthUser;
import com.admitatlas.common.auth.PasswordHasher;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * B 端管理接口，整段 /api/v1/admin/** 由 AuthInterceptor 强制 ADMIN 角色。
 *
 * <p>所有写操作都写 audit_log（操作人、动作、目标、前后值摘要）；
 * 不返回 password_hash。演示阶段的数据版本切换只改登记状态，不改写事实表。
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{3,32}$");

    private final AdmitAtlasRepository repo;

    @Value("${admitatlas.demo-data:true}")
    private boolean demoData;

    @Value("${admitatlas.stage:P1-pilot}")
    private String stage;

    public AdminController(AdmitAtlasRepository repo) {
        this.repo = repo;
    }

    /** 后台首页：账号 / 数据 / 业务量计数 + 最近审计。 */
    @GetMapping("/overview")
    public Result<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stage", stage);
        data.put("demoData", demoData);
        data.put("dataVersion", repo.latestDataVersion());
        data.put("counts", repo.adminCounts());
        data.put("recentAudit", repo.auditLogs(5));
        data.put("activeVersions", repo.dataVersions().stream()
                .filter(v -> "ACTIVE".equals(v.status())).toList());
        return Result.ok(data);
    }

    // ------------------------------------------------------------ 用户与角色

    @GetMapping("/users")
    public Result<Map<String, Object>> users(
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String keyword) {
        List<Map<String, Object>> items = repo.users(role, keyword).stream()
                .map(AdminController::userView).toList();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("total", items.size());
        data.put("roles", List.of(AuthUser.ROLE_STUDENT, AuthUser.ROLE_ADMIN));
        return Result.ok(data);
    }

    @PostMapping("/users")
    public Result<Map<String, Object>> createUser(
            @RequestBody(required = false) AdminDTO.UserCreateRequest req) {
        if (req == null || blank(req.username()) || blank(req.password())) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名和密码不能为空");
        }
        String username = req.username().trim();
        if (!USERNAME.matcher(username).matches()) {
            throw new BizException(ResultCode.BAD_REQUEST, "用户名须为 3~32 位字母、数字、_ . -");
        }
        checkPassword(req.password());
        String role = blank(req.role()) ? AuthUser.ROLE_STUDENT : req.role().trim().toUpperCase();
        if (!AuthUser.ROLE_STUDENT.equals(role) && !AuthUser.ROLE_ADMIN.equals(role)) {
            throw new BizException(ResultCode.BAD_REQUEST, "角色只能是 STUDENT 或 ADMIN");
        }
        if (repo.userByUsername(username).isPresent()) {
            throw new BizException(ResultCode.CONFLICT, "该用户名已存在");
        }
        long id = repo.createUser(username, PasswordHasher.hash(req.password()),
                blank(req.displayName()) ? username : req.displayName().trim(),
                blank(req.provinceCode()) ? "42" : req.provinceCode().trim(), role);
        audit("CREATE_USER", "USER", String.valueOf(id),
                "新建 " + role + " 账号 " + username);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", id);
        data.put("user", userView(repo.userById(id).orElseThrow()));
        return Result.ok(data);
    }

    @PostMapping("/users/{id}/status")
    public Result<Map<String, Object>> updateStatus(
            @PathVariable long id,
            @RequestBody(required = false) AdminDTO.StatusRequest req) {
        String status = req == null || blank(req.status()) ? "" : req.status().trim().toUpperCase();
        if (!"ACTIVE".equals(status) && !"DISABLED".equals(status)) {
            throw new BizException(ResultCode.BAD_REQUEST, "status 只能是 ACTIVE 或 DISABLED");
        }
        Models.UserAccount target = repo.userById(id)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "未找到账号: " + id));
        if (id == AuthContext.userId()) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能停用当前登录的账号");
        }
        repo.updateUserStatus(id, status);
        audit("UPDATE_USER_STATUS", "USER", String.valueOf(id),
                target.username() + " 状态 " + target.status() + " -> " + status);
        return Result.ok(Map.of("id", id, "status", status, "tokenRevoked", true));
    }

    @PostMapping("/users/{id}/reset-password")
    public Result<Map<String, Object>> resetPassword(
            @PathVariable long id,
            @RequestBody(required = false) AdminDTO.PasswordRequest req) {
        if (req == null) {
            throw new BizException(ResultCode.BAD_REQUEST, "新密码不能为空");
        }
        checkPassword(req.password());
        Models.UserAccount target = repo.userById(id)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "未找到账号: " + id));
        repo.updatePassword(id, PasswordHasher.hash(req.password()));
        audit("RESET_PASSWORD", "USER", String.valueOf(id),
                "重置 " + target.username() + " 的密码");
        return Result.ok(Map.of("id", id, "reset", true, "tokenRevoked", true));
    }

    // ------------------------------------------------------------ 审计与版本

    @GetMapping("/audit-logs")
    public Result<Map<String, Object>> auditLogs(
            @RequestParam(defaultValue = "30") int limit) {
        return Result.ok(Map.of("items", repo.auditLogs(limit)));
    }

    @GetMapping("/data-versions")
    public Result<Map<String, Object>> dataVersions() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", repo.dataVersions());
        data.put("current", repo.latestDataVersion());
        data.put("demoData", demoData);
        return Result.ok(data);
    }

    @PostMapping("/data-versions/{version}/activate")
    public Result<Map<String, Object>> activate(@PathVariable String version) {
        repo.activateDataVersion(version, AuthContext.require().username());
        audit("ACTIVATE_DATA_VERSION", "DATA_VERSION", version,
                "切换在线公示数据版本为 " + version);
        return Result.ok(Map.of("dataVersion", version, "status", "ACTIVE",
                "note", "演示阶段仅切换登记状态，事实表数据未改写"));
    }

    // ------------------------------------------------------------ 规则与档案

    @GetMapping("/policies")
    public Result<Map<String, Object>> policies() {
        return Result.ok(Map.of("items", repo.policies(), "total", repo.policies().size()));
    }

    @PutMapping("/policies/{provinceCode}/{year}")
    public Result<Map<String, Object>> updatePolicy(
            @PathVariable String provinceCode, @PathVariable int year,
            @RequestBody(required = false) AdminDTO.PolicyUpdateRequest req) {
        if (req == null || req.volunteerCount() == null || req.volunteerCount() < 1
                || req.volunteerCount() > 200) {
            throw new BizException(ResultCode.BAD_REQUEST, "volunteerCount 须在 1~200 之间");
        }
        Models.RulePolicy before = repo.policy(provinceCode, year)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND,
                        "未找到规则: " + provinceCode + "/" + year));
        String unit = blank(req.volunteerUnit()) ? before.volunteerUnit() : req.volunteerUnit().trim();
        String batches = blank(req.batches()) ? before.batches() : req.batches().trim();
        repo.updatePolicy(provinceCode, year, unit, req.volunteerCount(), batches);
        audit("UPDATE_POLICY", "RULE_POLICY", provinceCode + "/" + year,
                "志愿数 " + before.volunteerCount() + " -> " + req.volunteerCount()
                        + "；单位 " + before.volunteerUnit() + " -> " + unit);
        return Result.ok(Map.of("provinceCode", provinceCode, "examYear", year,
                "policy", repo.policy(provinceCode, year).orElseThrow()));
    }

    @GetMapping("/schools")
    public Result<Map<String, Object>> schools(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", repo.countSchools(keyword, null, null));
        data.put("items", repo.searchSchools(keyword, null, null, page, size));
        return Result.ok(data);
    }

    @PutMapping("/schools/{code}")
    public Result<Map<String, Object>> updateSchool(
            @PathVariable String code,
            @RequestBody(required = false) AdminDTO.SchoolUpdateRequest req) {
        if (req == null || blank(req.schoolName())) {
            throw new BizException(ResultCode.BAD_REQUEST, "schoolName 不能为空");
        }
        Models.School before = repo.school(code).orElse(null);
        repo.upsertSchool(code, req.schoolName().trim(),
                empty(req.supervisor()), empty(req.provinceCode()), empty(req.city()),
                empty(req.tierTags()), empty(req.schoolType()), empty(req.schoolLevel()),
                empty(req.intro()));
        audit(before == null ? "CREATE_SCHOOL" : "UPDATE_SCHOOL", "SCHOOL", code,
                before == null ? "新增院校 " + req.schoolName()
                        : before.schoolName() + " -> " + req.schoolName());
        return Result.ok(Map.of("school", repo.school(code).orElseThrow()));
    }

    // ------------------------------------------------------------ 业务核查（只读）

    @GetMapping("/plans")
    public Result<Map<String, Object>> plans(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(Map.of("items", repo.allPlans(limit)));
    }

    @GetMapping("/match-tasks")
    public Result<Map<String, Object>> tasks(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(Map.of("items", repo.recentTasks(limit)));
    }

    // ------------------------------------------------------------ helpers

    /** 脱敏：账号列表绝不返回 password_hash。 */
    private static Map<String, Object> userView(Models.UserAccount a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.id());
        m.put("username", a.username());
        m.put("displayName", a.displayName());
        m.put("provinceCode", a.provinceCode());
        m.put("role", a.role());
        m.put("status", a.status());
        m.put("lastLoginAt", a.lastLoginAt());
        m.put("createdAt", a.createdAt());
        return m;
    }

    private void audit(String action, String targetType, String targetId, String detail) {
        AuthUser user = AuthContext.require();
        repo.writeAudit(user.id(), user.username(), action, targetType, targetId, detail);
    }

    private static void checkPassword(String password) {
        if (blank(password) || password.length() < 8 || password.length() > 64) {
            throw new BizException(ResultCode.BAD_REQUEST, "密码长度须为 8~64 位");
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String empty(String s) {
        return s == null ? "" : s.trim();
    }
}
