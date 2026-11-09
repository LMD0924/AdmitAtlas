package com.admitatlas.authuser;

import com.admitatlas.common.BizException;
import com.admitatlas.common.ResultCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 本地磁盘文件存储（F-C-51 用户头像 / 院校配图）。
 *
 * <p>上传根目录由 {@code admitatlas.upload-dir} 控制（默认 ./uploads），按 category 分子目录；
 * 返回相对 URL {@code /files/{category}/{name}}，由 launcher 的 StaticResourceConfig 暴露为静态资源。
 * 仅接受图片类型，单文件上限 5MB，文件名以 UUID 重命名，杜绝冲突与路径穿越。
 */
@Service
public class FileStorageService {

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/webp");

    private final Path root;

    public FileStorageService(@Value("${admitatlas.upload-dir:./uploads}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建上传目录: " + root, e);
        }
    }

    public String store(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.BAD_REQUEST, "上传文件不能为空");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new BizException(ResultCode.BAD_REQUEST, "仅支持 PNG / JPG / GIF / WEBP 图片");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ResultCode.BAD_REQUEST, "图片大小不能超过 5MB");
        }
        String safeCategory = sanitizeCategory(category);
        String name = UUID.randomUUID().toString().replace("-", "") + extension(contentType);
        Path categoryDir = root.resolve(safeCategory);
        Path target = categoryDir.resolve(name).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法存储路径");
        }
        try {
            Files.createDirectories(categoryDir);
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new BizException(ResultCode.INTERNAL_ERROR, "文件保存失败");
        }
        return "/files/" + safeCategory + "/" + name;
    }

    private static String sanitizeCategory(String category) {
        if (category == null) return "general";
        String c = category.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
        return c.isEmpty() ? "general" : c;
    }

    private static String extension(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }
}
