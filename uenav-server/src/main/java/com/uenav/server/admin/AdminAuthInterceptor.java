package com.uenav.server.admin;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Blocks every /api/admin/** request (except login) that has no valid Bearer token. */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    public static final String SESSION = "uenav.admin.session";

    private final AdminAuthService auth;

    public AdminAuthInterceptor(AdminAuthService auth) {
        this.auth = auth;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        Optional<Admin.Session> session = auth.session(bearerToken(request));
        if (session.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Login required\"}");
            return false;
        }
        request.setAttribute(SESSION, session.get());
        return true;
    }

    static String bearerToken(HttpServletRequest request) {
        String h = request.getHeader("Authorization");
        if (h != null && h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return h.substring(7).trim();
        }
        return null;
    }
}
