package com.company.knowledge.common.context;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;

/**
 * 当前登录用户的 ThreadLocal 上下文。
 *
 * <p>由 {@code SessionInterceptor} 在请求开始时 {@link #set(CurrentUser)}，
 * 在请求结束时 {@link #clear()}，避免线程池复用导致的串号。
 */
public final class UserContext {

    private static final ThreadLocal<CurrentUser> HOLDER = new ThreadLocal<>();

    private UserContext() {}

    public static void set(CurrentUser user) { HOLDER.set(user); }

    public static CurrentUser get() { return HOLDER.get(); }

    public static CurrentUser require() {
        CurrentUser u = HOLDER.get();
        if (u == null) {
            throw new IllegalStateException("UserContext is empty; not logged in or interceptor not applied");
        }
        return u;
    }

    public static void clear() { HOLDER.remove(); }

    @Data
    @AllArgsConstructor
    public static class CurrentUser {
        /** 海康 personId */
        private String personId;
        /** 显示名 */
        private String personName;
        /** 所属组织（部门）orgIndexCode */
        private String orgIndexCode;
        /** 组织层级路径，如 {@code @root@集团@华东@某公司@} */
        private String orgPath;
        /** 本地角色 code 集合，如 ADMIN/AUDITOR_GROUP/EDITOR */
        private Set<String> roles;

        public Set<String> safeRoles() {
            return roles == null ? new HashSet<>() : roles;
        }

        public boolean hasRole(String code) {
            return safeRoles().contains(code);
        }
    }
}
