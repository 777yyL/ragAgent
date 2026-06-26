package com.company.knowledge.integration.hikvision;

import org.junit.jupiter.api.Test;

import java.util.SortedMap;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ArtemisSigner 单元测试。
 *
 * <p>核心约束：
 * <ul>
 *   <li>签名是 HmacSHA256 → 32 字节 → 64 个 hex 字符</li>
 *   <li>同输入必须产生同输出（确定性）</li>
 *   <li>密钥或输入变化时签名必须变化</li>
 * </ul>
 */
class ArtemisSignerTest {

    @Test
    void sign_shouldReturn64HexCharsForHmacSha256() {
        ArtemisSigner signer = new ArtemisSigner("test-secret");
        String sign = signer.sign("POST", "application/json", "",
                "application/json", "", "/artemis/api/x", new TreeMap<>());
        assertNotNull(sign);
        assertEquals(64, sign.length(), "HmacSHA256 hex 应为 64 字符");
        assertDoesNotMatch(sign, ".*[^0-9a-f].*"); // 仅小写 hex
    }

    @Test
    void sign_shouldBeDeterministic_sameInputSameOutput() {
        ArtemisSigner signer = new ArtemisSigner("k1");
        SortedMap<String, String> q = new TreeMap<>();
        q.put("a", "1");
        q.put("b", "2");
        String s1 = signer.sign("POST", "application/json", "", "application/json", "",
                "/api/x", q);
        String s2 = signer.sign("POST", "application/json", "", "application/json", "",
                "/api/x", q);
        assertEquals(s1, s2, "同输入必须同输出");
    }

    @Test
    void sign_shouldDifferWhenSecretChanges() {
        SortedMap<String, String> q = new TreeMap<>();
        String s1 = new ArtemisSigner("secret-A").sign("POST", "application/json",
                "", "application/json", "", "/api/x", q);
        String s2 = new ArtemisSigner("secret-B").sign("POST", "application/json",
                "", "application/json", "", "/api/x", q);
        assertNotEquals(s1, s2, "密钥变化签名必须变化");
    }

    @Test
    void sign_shouldDifferWhenMethodChanges() {
        ArtemisSigner signer = new ArtemisSigner("s");
        String post = signer.sign("POST", "application/json", "", "application/json", "",
                "/api/x", new TreeMap<>());
        String get = signer.sign("GET", "application/json", "", "application/json", "",
                "/api/x", new TreeMap<>());
        assertNotEquals(post, get, "HTTP 方法变化签名必须变化");
    }

    @Test
    void sign_shouldDifferWhenQueryChanges() {
        ArtemisSigner signer = new ArtemisSigner("s");
        SortedMap<String, String> q1 = new TreeMap<>();
        q1.put("a", "1");
        SortedMap<String, String> q2 = new TreeMap<>();
        q2.put("a", "2");
        String s1 = signer.sign("POST", "application/json", "", "application/json", "",
                "/api/x", q1);
        String s2 = signer.sign("POST", "application/json", "", "application/json", "",
                "/api/x", q2);
        assertNotEquals(s1, s2, "query 变化签名必须变化");
    }

    @Test
    void sign_queryShouldBeSorted_bAbDoesNotEqualAbB() {
        ArtemisSigner signer = new ArtemisSigner("s");
        // TreeMap 自动按 key 排序，验证 b/a 与 a/b 顺序不影响签名
        SortedMap<String, String> q1 = new TreeMap<>();
        q1.put("b", "1");
        q1.put("a", "2");
        SortedMap<String, String> q2 = new TreeMap<>();
        q2.put("a", "2");
        q2.put("b", "1");
        String s1 = signer.sign("POST", "application/json", "", "application/json", "",
                "/api/x", q1);
        String s2 = signer.sign("POST", "application/json", "", "application/json", "",
                "/api/x", q2);
        assertEquals(s1, s2, "query 排序后应一致（与 put 顺序无关）");
    }

    private static void assertDoesNotMatch(String s, String regex) {
        assertFalse(s.matches(regex), "不应匹配 " + regex + "，实际：" + s);
    }
}
