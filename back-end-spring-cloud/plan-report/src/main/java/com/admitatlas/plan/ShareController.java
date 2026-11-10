package com.admitatlas.plan;

import com.admitatlas.common.BizException;
import com.admitatlas.common.Result;
import com.admitatlas.common.ResultCode;
import com.admitatlas.common.auth.AuthContext;
import com.admitatlas.common.model.Models;
import com.admitatlas.common.repository.AdmitAtlasRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * F-C-44：方案只读分享。
 *
 * <p>写端点 {@code /api/v1/plans/{id}/share} 落在 AuthInterceptor 的 {@code /plans/**}
 * 受保护范围内；读端点 {@code /api/v1/share/{token}} 为公开只读，令牌自带有效期与撤销位。
 * 分享内容只暴露志愿条目，不返回 planId / userId / taskId 等内部标识，默认隐藏账号信息。
 */
@RestController
@RequestMapping("/api/v1")
public class ShareController {

    private final AdmitAtlasRepository repo;

    public ShareController(AdmitAtlasRepository repo) {
        this.repo = repo;
    }

    /** 生成（或覆盖）分享令牌；同一方案再次生成会吊销旧令牌。 */
    @PostMapping("/plans/{id}/share")
    public Result<Map<String, Object>> create(
            @PathVariable long id,
            @RequestBody(required = false) Map<String, Object> req) {
        long userId = AuthContext.userId();
        Models.PlanRecord plan = repo.plan(id)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "未找到方案: " + id));
        PlanService.assertOwner(plan);

        int hours = 72;
        boolean masked = true;
        if (req != null) {
            if (req.get("expiresInHours") != null) {
                hours = ((Number) req.get("expiresInHours")).intValue();
            }
            if (req.get("masked") != null) {
                masked = Boolean.parseBoolean(String.valueOf(req.get("masked")));
            }
        }
        if (hours < 0 || hours > 720) {
            throw new BizException(ResultCode.BAD_REQUEST, "有效期需在 0~720 小时之间（0 表示长期有效）");
        }
        repo.revokeShares(id, userId);
        String token = "SH-" + UUID.randomUUID().toString().replace("-", "");
        Timestamp expires = hours == 0 ? null
                : Timestamp.valueOf(LocalDateTime.now().plusHours(hours));
        Models.PlanShare share = repo.createShare(token, id, userId, masked, expires);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("sharePath", "/share/" + token);
        data.put("expiresAt", share.expiresAt());
        data.put("masked", share.maskOwner());
        data.put("planId", id);
        return Result.ok(data);
    }

    /** 撤销该方案的全部有效分享链接。 */
    @DeleteMapping("/plans/{id}/share")
    public Result<Map<String, Object>> revoke(@PathVariable long id) {
        long userId = AuthContext.userId();
        Models.PlanRecord plan = repo.plan(id)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "未找到方案: " + id));
        PlanService.assertOwner(plan);
        repo.revokeShares(id, userId);
        return Result.ok(Map.of("revoked", true, "planId", id));
    }

    /** 游客只读：令牌无效 / 已撤销 / 已过期统一回 404，避免枚举。 */
    @GetMapping("/share/{token}")
    public Result<Map<String, Object>> read(@PathVariable String token) {
        Models.PlanShare share = repo.activeShare(token)
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND,
                        "分享链接不存在、已撤销或已过期"));
        Models.PlanRecord plan = repo.plan(share.planId())
                .orElseThrow(() -> new BizException(ResultCode.NOT_FOUND, "方案已不存在"));
        List<Models.PlanItemRecord> items = repo.planItems(share.planId());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planName", plan.planName());
        data.put("createdAt", plan.createdAt());
        data.put("expiresAt", share.expiresAt());
        data.put("volunteerUnit", plan.volunteerUnit());
        data.put("volunteerCount", plan.volunteerCount());
        data.put("dataVersion", plan.dataVersion());
        data.put("ownerName", share.maskOwner() ? "匿名用户" : null);
        data.put("masked", share.maskOwner());
        data.put("items", items);
        data.put("total", items.size());
        data.put("demoData", true);
        data.put("disclaimer", "本页为只读分享快照，数据为演示合成值，不构成录取承诺；"
                + "最终以各省教育考试院公布为准");
        return Result.ok(data);
    }
}
