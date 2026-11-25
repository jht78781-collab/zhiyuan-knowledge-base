package cn.enterprise.kb.storage;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import java.io.InputStream;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MinioStorage {
    public static class Failure extends RuntimeException {
        Failure(String message) { super(message); }
    }

    private final MinioClient client;
    private final String bucket;

    MinioStorage(@Value("${app.minio.endpoint}") String endpoint, @Value("${app.minio.access-key}") String access,
                 @Value("${app.minio.secret-key}") String secret, @Value("${app.minio.bucket}") String bucket) {
        client = access.isBlank() || secret.isBlank() ? null
                : MinioClient.builder().endpoint(endpoint).credentials(access, secret).build();
        if (client != null) client.setTimeout(5_000, 25_000, 25_000);
        this.bucket = bucket;
    }

    public void put(String key, long size, InputStream stream) {
        var storage = configured();
        try {
            storage.putObject(PutObjectArgs.builder().bucket(bucket).object(key).stream(stream, size, -1)
                    .contentType("application/octet-stream").build());
        } catch (Exception e) {
            throw new Failure("无法保存原文件，请检查 MinIO 服务、私有桶及写权限。");
        }
    }

    public InputStream open(String key) {
        var storage = configured();
        try {
            return storage.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new Failure("无法读取原文件，请检查 MinIO 服务和对象状态。");
        }
    }

    public boolean contains(String key) {
        var storage = configured();
        try {
            storage.statObject(StatObjectArgs.builder().bucket(bucket).object(key).build());
            return true;
        } catch (io.minio.errors.ErrorResponseException e) {
            if (Set.of("NoSuchKey", "NoSuchObject", "NotFound").contains(e.errorResponse().code())) return false;
            throw new Failure("无法查询原文件，请检查 MinIO 服务和读取权限。");
        } catch (Exception e) {
            throw new Failure("无法查询原文件，请检查 MinIO 服务和读取权限。");
        }
    }

    public void delete(String key) {
        var storage = configured();
        try {
            storage.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new Failure("无法清理原文件，请检查 MinIO 服务和删除权限。");
        }
    }

    private MinioClient configured() {
        if (client == null) throw new Failure("服务端尚未配置 MinIO 凭证。");
        return client;
    }
}
