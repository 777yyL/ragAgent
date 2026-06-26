package com.company.knowledge.integration.sso;

import com.company.knowledge.org.entity.SysPerson;
import com.company.knowledge.org.mapper.SysPersonMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * 开发模式 Mock 登录。仅 {@code dev} profile 激活。
 *
 * <p>绕过海康 OAuth2，直接创建 mock 会话 + upsert sys_person，用于本地开发测试。
 * 生产环境（prod profile）此类不会被 Spring 加载。
 *
 * <p>访问 {@code GET /sso/dev-login?menu_uri=/index} 即可直接登录。
 * OAuth2Controller.login() 在 dev 时会自动重定向到此端点。
 */
@Slf4j
@RestController
@RequestMapping("/sso")
@Profile("dev")
@RequiredArgsConstructor
public class DevLoginController {

    private final SessionManager sessionManager;
    private final SysPersonMapper personMapper;

    @GetMapping("/dev-login")
    public ResponseEntity<Void> devLogin(
            @RequestParam(value = "menu_uri", required = false, defaultValue = "/index") String menuUri,
            HttpServletResponse response) {

        String personId = "dev-user-001";
        String personName = "开发测试员";

        // upsert sys_person（保证 /api/me 能关联到人员记录）
        upsertMockPerson(personId, personName);

        // 创建 mock session（ADMIN 角色，全部权限）
        SessionManager.SessionUser user = new SessionManager.SessionUser(
                personId,
                personName,
                "root000000",
                "@root000000@开发测试部@",
                new HashSet<>(Set.of("ADMIN")));
        String sid = sessionManager.create(user);

        // Cookie
        Cookie cookie = new Cookie(OAuth2Controller.SESSION_COOKIE, sid);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge((int) SessionManager.TTL.getSeconds());
        response.addCookie(cookie);

        log.info("[DevLogin] mock session created for personId={}, redirect to {}", personId, menuUri);
        return ResponseEntity.status(302).location(URI.create(menuUri)).build();
    }

    private void upsertMockPerson(String personId, String name) {
        try {
            SysPerson entity = personMapper.selectById(personId);
            boolean isNew = entity == null;
            if (isNew) entity = new SysPerson();

            entity.setPersonId(personId);
            entity.setPersonName(name);
            entity.setOrgIndexCode("root000000");
            entity.setOrgPath("@root000000@");
            entity.setOrgPathName("@开发测试部@");
            entity.setJobNo("DEV001");
            entity.setPhone("13800000000");
            entity.setEmail("dev@knowledge.local");
            entity.setCompany("开发测试公司");
            entity.setPost("开发测试");
            entity.setPostType("DEV");
            entity.setStatus(0);
            entity.setSyncTime(LocalDateTime.now());

            if (isNew) personMapper.insert(entity);
            else personMapper.updateById(entity);
        } catch (Exception e) {
            log.warn("[DevLogin] upsert sys_person failed (DB not ready?): {}", e.getMessage());
        }
    }
}
