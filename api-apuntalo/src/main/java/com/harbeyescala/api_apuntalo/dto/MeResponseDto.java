package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class MeResponseDto {

    private Long userId;
    private String username;
    private Role role;
    private TenantInfo tenant;

    @Getter
    @Builder
    public static class TenantInfo {
        private Long id;
        private String name;
    }
}
