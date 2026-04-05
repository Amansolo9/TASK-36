package com.eaglepoint.storehub.dto;

import com.eaglepoint.storehub.enums.Role;
import lombok.Data;

@Data
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private Role role;
    private Long siteId;
    private String siteName;
    private String address;
    private String deviceId;
    private boolean enabled;
}
