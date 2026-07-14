package com.harbeyescala.api_apuntalo.dto;

import com.harbeyescala.api_apuntalo.entity.Role;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponseDto {

    /**
     * @deprecated mantenido por compatibilidad con clientes existentes que
     * leen "token". Usar {@link #accessToken}.
     */
    @Deprecated
    private String token;

    private String accessToken;
    private String tokenType;
    private long expiresIn;

    private UserSummary user;
    private TenantSummary tenant;

    @Getter
    @Builder
    public static class UserSummary {
        private Long id;
        private String username;
        private Role role;
    }

    @Getter
    @Builder
    public static class TenantSummary {
        private Long id;
        private String name;
    }
}
