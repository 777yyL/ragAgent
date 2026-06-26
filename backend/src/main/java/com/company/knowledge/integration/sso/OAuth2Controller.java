package com.company.knowledge.integration.sso;

import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.integration.hikvision.OAuth2Client;
import com.company.knowledge.integration.hikvision.PersonApi;
import com.company.knowledge.integration.hikvision.dto.HikPerson;
import com.company.knowledge.integration.hikvision.dto.TokenResponse;
import com.company.knowledge.org.entity.SysPerson;
import com.company.knowledge.org.mapper.SysPersonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashSet;

/**
 * OAuth2 SSO 入口与回调。
 *
 * <p>三个端点：
 * <ul>
 *   <li>{@code GET  /sso/login}    — 重定向到海康 authorize（含 state 防 CSRF）</li>
 *   <li>{@code GET  /sso/callback} — 接收 code/state（含 logoutOauth2 登出回调分支）</li>
 *   <li>{@code POST /sso/logout}   — 清本地会话</li>
 * </ul>
 *
 * <p>被 {@code WebMvcConfig} 排除在 SessionInterceptor 之外（SSO 端点本身不能要求登录）。
 */
@Slf4j
@RestController
@RequestMapping("/sso")
@RequiredArgsConstructor
public class OAuth2Controller {

    /** 本地会话 Cookie 名 */
    public static final String SESSION_COOKIE = "KNOWLEDGE_SID";

    private final OAuth2Client oauth2Client;
    private final StateCache stateCache;
    private final SessionManager sessionManager;
    private final PersonApi personApi;
    private final SysPersonMapper personMapper;

    @Value("${spring.profiles.active:prod}")
    private String activeProfile;

    /**
     * 触发 SSO：生成 state → 302 跳海康 authorize。
     *
     * @param menuUri 登录后目标页面（默认 /index）
     */
    @GetMapping("/login")
    public ResponseEntity<Void> login(
            @RequestParam(value = "menu_uri", required = false, defaultValue = "/index") String menuUri) {
        // dev 模式：绕过海康 OAuth2，直接走 mock 登录
        if ("dev".equals(activeProfile)) {
            String devUrl = "/sso/dev-login?menu_uri=" + menuUri;
            return ResponseEntity.status(302).location(URI.create(devUrl)).build();
        }
        // 正常流程：跳海康 authorize
        String state = stateCache.issue();
        String url = oauth2Client.buildAuthorizeUrl(state, menuUri);
        return ResponseEntity.status(302).location(URI.create(url)).build();
    }

    /**
     * 海康回调。两条分支：
     * <ul>
     *   <li>带 {@code logoutOauth2} 参数 → 海康登出回调，清本地会话</li>
     *   <li>带 {@code code+state} → 换 token → 拿 userId → upsert sys_person → 签发会话</li>
     * </ul>
     */
    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "menu_uri", required = false, defaultValue = "/index") String menuUri,
            @RequestParam(value = "logoutOauth2", required = false) String logoutToken,
            HttpServletResponse response) {

        // —— 登出回调分支 ——
        if (logoutToken != null) {
            log.info("[SSO] logout callback received, token={}", abbreviate(logoutToken, 12));
            return ResponseEntity.ok().build();
        }

        // —— 登录回调分支 ——
        if (code == null || code.isEmpty()) {
            throw BizException.of(6201, "callback missing code");
        }
        // ① 防 CSRF
        if (!stateCache.consume(state)) {
            throw BizException.of(6202, "invalid state (CSRF or expired)");
        }
        // ② code → access_token
        TokenResponse token = oauth2Client.exchangeCodeForToken(code);
        // ③ access_token → userId
        String userId = oauth2Client.getUserId(token.getAccessToken());
        // ④ 拉人员详情（实时兜底）
        HikPerson hik = personApi.getById(userId);
        if (hik == null) {
            throw BizException.of(6203, "person not found: " + userId);
        }
        // ⑤ 校验 status（海康已删除则拒绝）
        if (hik.getStatus() != null && hik.getStatus() < 0) {
            throw BizException.of(6204, "person disabled/deleted in upstream");
        }
        // ⑥ upsert sys_person
        upsertPerson(hik);
        // ⑦ 签发本地会话（角色暂为空集，Phase 5 由 RoleService 补）
        SessionManager.SessionUser sessionUser = new SessionManager.SessionUser(
                hik.getPersonId(), hik.getPersonName(),
                hik.getOrgIndexCode(), hik.getOrgPath(),
                new HashSet<>());
        String sid = sessionManager.create(sessionUser);
        // ⑧ Cookie + 重定向
        Cookie cookie = new Cookie(SESSION_COOKIE, sid);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge((int) SessionManager.TTL.getSeconds());
        response.addCookie(cookie);
        log.info("[SSO] login success, personId={}, name={}", userId, hik.getPersonName());
        return ResponseEntity.status(302).location(URI.create(menuUri)).build();
    }

    /**
     * 主动登出（前端顶栏"退出"按钮调用）。
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(value = SESSION_COOKIE, required = false) String sid,
            HttpServletResponse response) {
        if (sid != null) {
            sessionManager.invalidate(sid);
        }
        Cookie cookie = new Cookie(SESSION_COOKIE, "");
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.ok().build();
    }

    private void upsertPerson(HikPerson p) {
        SysPerson entity = personMapper.selectById(p.getPersonId());
        boolean isNew = entity == null;
        if (isNew) entity = new SysPerson();

        entity.setPersonId(p.getPersonId());
        entity.setPersonName(p.getPersonName());
        entity.setOrgIndexCode(p.getOrgIndexCode());
        entity.setOrgPath(p.getOrgPath());
        entity.setOrgPathName(p.getOrgPathName());
        entity.setJobNo(p.getJobNo());
        entity.setPhone(p.getPhoneNo());
        entity.setEmail(p.getEmail());
        entity.setCompany(p.getCompany());
        entity.setPost(p.getPost());
        entity.setPostType(p.getPostType());
        entity.setStatus(0);
        entity.setSyncTime(LocalDateTime.now());

        if (isNew) personMapper.insert(entity);
        else personMapper.updateById(entity);
    }

    private static String abbreviate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
