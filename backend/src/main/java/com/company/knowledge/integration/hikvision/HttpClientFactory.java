package com.company.knowledge.integration.hikvision;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

/**
 * 信任所有 SSL 证书的 OkHttpClient 工厂。
 *
 * <p>海康平台使用自签名证书或内部 CA，Java 默认不信任导致 PKIX 错误。
 * 此工厂创建的 OkHttpClient 跳过证书校验，仅用于内部企业网络。
 */
public final class HttpClientFactory {

    private HttpClientFactory() {}

    public static okhttp3.OkHttpClient createTrustAllClient() {
        try {
            // 创建信任所有证书的 TrustManager
            X509TrustManager trustAllManager = new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            };

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAllManager}, null);

            return new okhttp3.OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory(), trustAllManager)
                    .hostnameVerifier((hostname, session) -> true)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(60, TimeUnit.SECONDS)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create trust-all OkHttpClient", e);
        }
    }
}
