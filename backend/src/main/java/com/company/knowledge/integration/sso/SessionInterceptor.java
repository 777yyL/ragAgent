package com.company.knowledge.integration.sso;

import com.company.knowledge.common.context.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashSet;

/**
 * 会话拦截器。对 {@code /api/**} 生效，注入 {@link UserContext}。
 *
 * <p>放行：{@code /sso/**}, {@code /health}, {@code /actuator/**}。
 * 失败：返回 401 + {@code Location: /sso/login}，前端拿到后跳登录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionInterceptor implements HandlerInterceptor {

    private final SessionManager sessionManager;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) throws Exception {
        String sid = extractSid(req);
        if (sid == null) {
            return unauthorized(resp);
        }
        SessionManager.SessionUser user = sessionManager.get(sid);
        if (user == null) {
            return unauthorized(resp);
        }
        // 滑动续期（活跃用户会话不过期）
        sessionManager.renew(sid);

        UserContext.set(new UserContext.CurrentUser(
                user.getPersonId(),
                user.getPersonName(),
                user.getOrgIndexCode(),
                user.getOrgPath(),
                user.getRoles() != null ? user.getRoles() : new HashSet<>()));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        UserContext.clear();
    }

    private boolean unauthorized(HttpServletResponse resp) {
        resp.setStatus(401);
        resp.setHeader("Location", "/sso/login");
        return false;
    }

    private String extractSid(HttpServletRequest req) {
        if (req.getCookies() != null) {
            for (Cookie c : req.getCookies()) {
                if (OAuth2Controller.SESSION_COOKIE.equals(c.getName())) {
                    return c.getValue();
                }
            }
        }
        // 兼容非浏览器调用（如 OpenAPI 测试）：Header X-Knowledge-Sid
        return req.getHeader("X-Knowledge-Sid");
    }
}
