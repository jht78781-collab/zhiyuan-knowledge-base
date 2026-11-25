package cn.enterprise.kb.dify;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
class DifyHttp {
    private final RestClient rest;
    private final CloseableHttpClient client;

    DifyHttp(DifyConfiguration configuration) {
        var manager = PoolingHttpClientConnectionManagerBuilder.create().setDefaultConnectionConfig(ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(configuration.connectTimeout()))
                .setSocketTimeout(Timeout.of(configuration.readTimeout())).build()).build();
        client = HttpClients.custom().setConnectionManager(manager).disableRedirectHandling().disableAutomaticRetries().build();
        var requests = new HttpComponentsClientHttpRequestFactory(client);
        requests.setConnectionRequestTimeout(configuration.connectTimeout());
        requests.setReadTimeout(configuration.readTimeout());
        rest = RestClient.builder().baseUrl(configuration.base().toString()).requestFactory(requests).build();
    }

    @PreDestroy
    void close() throws IOException { client.close(); }

    <T> T call(java.util.function.Function<RestClient, T> request) {
        try {
            return request.apply(rest);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404) throw new DifyFailure("DIFY_NOT_FOUND", "Dify 资源不存在，请检查绑定或索引状态。", true, false);
            if (status == 401 || status == 403) {
                throw new DifyFailure("DIFY_AUTH", "Dify 凭证无效或权限不足，请检查服务端配置。", false, false);
            }
            if (status == 429 || status >= 500) throw DifyFailure.io();
            throw new DifyFailure("DIFY_REJECTED", "Dify 拒绝了请求，请检查文件和知识库配置。", false, false);
        } catch (ResourceAccessException e) {
            throw DifyFailure.io();
        } catch (RestClientException e) {
            throw DifyFailure.invalid();
        }
    }
}
