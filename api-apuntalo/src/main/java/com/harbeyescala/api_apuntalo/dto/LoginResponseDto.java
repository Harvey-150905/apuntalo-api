package com.harbeyescala.api_apuntalo.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginResponseDto {

    private String token;
}