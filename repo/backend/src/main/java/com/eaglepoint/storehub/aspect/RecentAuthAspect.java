package com.eaglepoint.storehub.aspect;

import com.eaglepoint.storehub.config.RecentAuthRequiredException;
import com.eaglepoint.storehub.security.UserPrincipal;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

@Aspect
@Component
public class RecentAuthAspect {

    @Value("${app.security.recent-auth-window-ms}")
    private long recentAuthWindowMs;

    @Before("@annotation(com.eaglepoint.storehub.annotation.RequiresRecentAuth)")
    public void checkRecentAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }

        Instant lastAuth = principal.getLastAuthenticatedAt();
        if (lastAuth == null || Instant.now().toEpochMilli() - lastAuth.toEpochMilli() > recentAuthWindowMs) {
            throw new RecentAuthRequiredException();
        }
    }
}
