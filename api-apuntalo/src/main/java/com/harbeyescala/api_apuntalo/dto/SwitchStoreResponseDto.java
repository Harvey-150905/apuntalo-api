package com.harbeyescala.api_apuntalo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SwitchStoreResponseDto {
    @Deprecated
    private String token;
    private String accessToken;
    private String tokenType;
    private long expiresIn;
    private StoreResponseDto activeStore;
}
