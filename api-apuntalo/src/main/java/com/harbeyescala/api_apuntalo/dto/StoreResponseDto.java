package com.harbeyescala.api_apuntalo.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreResponseDto {
    private Long id;
    private String name;
    private String code;
    private String timezone;
    private Boolean active;
    private Boolean primaryStore;
    private String address;
    private String city;
    private String countryCode;
    private Boolean cashReconciliationEnabled;
}
