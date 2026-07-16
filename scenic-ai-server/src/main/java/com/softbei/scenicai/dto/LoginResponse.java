package com.softbei.scenicai.dto;

import java.util.List;

public record LoginResponse(
        String token,
        String displayName,
        List<String> roles
) {
}
