package com.eaglepoint.storehub.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private String type;
    private String username;
    private String role;
    private Long siteId;

    public AuthResponse(String token, String username, String role, Long siteId) {
        this.token = token;
        this.type = "Bearer";
        this.username = username;
        this.role = role;
        this.siteId = siteId;
    }
}
