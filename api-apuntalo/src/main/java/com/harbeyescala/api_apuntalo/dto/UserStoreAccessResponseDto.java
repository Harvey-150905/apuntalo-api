package com.harbeyescala.api_apuntalo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserStoreAccessResponseDto {
    private Long storeId;
    private String storeName;
    private String storeCode;
    private String timezone;
    private Boolean storeActive;
    private Boolean primaryStore;
    private Boolean defaultStore;
    private Boolean activeStore;
    private Boolean accessActive;
}
