package com.company.knowledge.integration.hikvision;

import com.company.knowledge.common.exception.BizException;
import com.company.knowledge.config.HikvisionProperties;
import com.company.knowledge.integration.hikvision.dto.TokenResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 海康 OAuth2 客户端（Authorization Code Flow）。
 *
 * <p>三步走：
 * <ol>
 *   <li>{@link #buildAuthorizeUrl(String, String)} — 拼装 authorize URL（前端直接 302 到此）</li>
 *   <li>{@link #exchangeCodeForToken(String)} — code → access_token</li>
 *   <li>{@link #getUserId(String)} — access_token → userId</li>
 * </ol>
 *
 * <p>异常码：6101 token 交换失败；6102 网络错；6103 userinfo 空；6104 userinfo 网络错。
 */
@Slf4j
@Component
public class OAuth2Client {

    private static final String AUTHORIZE_PATH =
            "/artemis/api/application/auth/v2/app/oauth2/authorize";
    private static final String TOKEN_PATH =
            "/artemis/api/application/auth/v2/app/oauth2/token";
    private static final String USERINFO_PATH =
            "/artemis/api/application/auth/v2/app/oauth2/userinfo";

    private final HikvisionProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient http = com.company.knowledge.integration.hikvision.HttpClientFactory.createTrustAllClient();

    public OAuth2Client(HikvisionProperties props) {
        this.props = props;
    }

    /**
     * 生成 authorize URL（前端 /sso/login 直接 302 到此）。
     *
     * @param state    CSRF 防护随机串
     * @param menuUri  登录成功后的目标页面（如 /index）
     */
    public String buildAuthorizeUrl(String state, String menuUri) {
        return "https://" + props.getHost() + AUTHORIZE_PATH
                + "?response_type=code"
                + "&client_id=" + urlEncode(props.getAppKey())
                + "&redirect_uri=" + urlEncode(props.getOauthRedirectUri())
                + "&state=" + urlEncode(state)
                + "&menu_uri=" + urlEncode(menuUri == null ? "/index" : menuUri);
    }

    /** code 换 access_token。code 5 分钟内一次性使用。 */
    @SuppressWarnings("unchecked")
    public TokenResponse exchangeCodeForToken(String code) {
        FormBody.Builder fb = new FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("client_id", props.getAppKey())
                .add("client_secret", props.getAppSecret())
                .add("redirect_uri", props.getOauthRedirectUri());
        Request req = new Request.Builder()
                .url("https://" + props.getHost() + TOKEN_PATH)
                .post(fb.build())
                .build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "{}";
            Map<String, Object> node = mapper.readValue(body, Map.class);
            if (!"0".equals(String.valueOf(node.get("code")))) {
                throw BizException.of(6101, "OAuth2 token exchange failed: " + node.get("msg"));
            }
            Map<String, Object> data = (Map<String, Object>) node.get("data");
            if (data == null) {
                throw BizException.of(6101, "OAuth2 token data empty");
            }
            return mapper.convertValue(data, TokenResponse.class);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("OAuth2 token exchange failed: {}", e.getMessage());
            throw BizException.of(6102, "OAuth2 token network error: " + e.getMessage(), e);
        }
    }

    /** 用 access_token 拿 userId。 */
    @SuppressWarnings("unchecked")
    public String getUserId(String accessToken) {
        Request req = new Request.Builder()
                .url("https://" + props.getHost() + USERINFO_PATH)
                .post(RequestBody.create("", null))
                .header("access_token", accessToken)
                .build();
        try (Response resp = http.newCall(req).execute()) {
            String body = resp.body() != null ? resp.body().string() : "{}";
            Map<String, Object> node = mapper.readValue(body, Map.class);
            Map<String, Object> data = (Map<String, Object>) node.get("data");
            if (data == null) {
                throw BizException.of(6103, "OAuth2 userinfo empty");
            }
            return String.valueOf(data.get("userId"));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("OAuth2 userinfo failed: {}", e.getMessage());
            throw BizException.of(6104, "OAuth2 userinfo network error: " + e.getMessage(), e);
        }
    }

    private static String urlEncode(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return s;
        }
    }
}
