package com.company.knowledge.api;

import com.company.knowledge.common.context.UserContext;
import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.common.result.Result;
import com.company.knowledge.integration.sso.SessionManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 当前登录用户信息端点。
 *
 * <p>前端顶栏从此取 personName/orgPath/roles。
 */
@RestController
@RequestMapping("/api")
public class CurrentUserController {

    @GetMapping("/me")
    public Result<SessionManager.SessionUser> me() {
        UserContext.CurrentUser ctx = UserContext.get();
        if (ctx == null) {
            throw BizException.of(401, "not logged in");
        }
        SessionManager.SessionUser out = new SessionManager.SessionUser(
                ctx.getPersonId(),
                ctx.getPersonName(),
                ctx.getOrgIndexCode(),
                ctx.getOrgPath(),
                ctx.safeRoles());
        return Result.success(out);
    }
}
