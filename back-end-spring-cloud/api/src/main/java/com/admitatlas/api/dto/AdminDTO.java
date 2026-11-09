package com.admitatlas.api.dto;

/** F-B-22 / F-B-08 / F-B-12 / F-B-20：管理端写操作契约。 */
public final class AdminDTO {

    private AdminDTO() {
    }

    public record UserCreateRequest(String username, String password, String displayName,
                                    String provinceCode, String role) {
    }

    public record StatusRequest(String status) {
    }

    public record PasswordRequest(String password) {
    }

    public record PolicyUpdateRequest(String volunteerUnit, Integer volunteerCount,
                                      String batches) {
    }

    public record SchoolUpdateRequest(String schoolName, String supervisor, String provinceCode,
                                      String city, String tierTags, String schoolType,
                                      String schoolLevel, String intro) {
    }

    public record SchoolProfileRequest(String markdown) {
    }
}
