package com.company.knowledge.integration.hikvision;

import com.company.knowledge.config.HikvisionProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OAuth2Client 单元测试（仅覆盖 buildAuthorizeUrl 的纯逻辑，
 * token/userinfo 因需真实海康，留给集成测试）。
 */
class OAuth2ClientTest {

    private OAuth2Client client;

    @BeforeEach
    void setUp() throws Exception {
        HikvisionProperties props = new HikvisionProperties();
        props.setHost("hik.test.com:443");
        props.setAppKey("app-key-123");
        props.setAppSecret("app-secret-456");
        props.setOauthRedirectUri("https://knowledge.test.com/sso/callback");
        client = new OAuth2Client(props);
    }

    @Test
    void buildAuthorizeUrl_shouldContainAllRequiredParams() {
        String url = client.buildAuthorizeUrl("state-xyz", "/workbench");
        assertTrue(url.startsWith("https://hik.test.com:443/artemis/api/application/auth/v2/app/oauth2/authorize?"),
                "应以 authorize 接口为前缀");
        assertTrue(url.contains("response_type=code"), "应含 response_type=code");
        assertTrue(url.contains("client_id=app-key-123"), "应含 client_id");
        assertTrue(url.contains("redirect_uri="), "应含 redirect_uri");
        assertTrue(url.contains("state=state-xyz"), "应含 state");
        assertTrue(url.contains("menu_uri="), "应含 menu_uri");
    }

    @Test
    void buildAuthorizeUrl_shouldUrlEncodeSpecialChars() {
        String url = client.buildAuthorizeUrl("a b&c", "/path?a=1");
        // 空格应编码成 +，& 编码成 %26
        assertTrue(url.contains("state=a+b%26c"));
    }

    @Test
    void buildAuthorizeUrl_menuUriNull_shouldFallbackToIndex() {
        String url = client.buildAuthorizeUrl("s", null);
        assertTrue(url.contains("menu_uri="), "menuUri=null 也应有 menu_uri 参数");
        // 不抛异常即通过
    }
}
