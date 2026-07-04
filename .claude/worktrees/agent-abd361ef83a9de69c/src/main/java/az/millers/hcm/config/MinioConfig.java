package az.millers.hcm.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;

/**
 * MinIO (S3-compatible) storage for file attachments (PRD 14.6, 16.4).
 *
 * <p>The bean is only created when {@code hcm.storage.minio.enabled=true};
 * with it disabled, the AttachmentService degrades to "storage unavailable"
 * and clients receive a 503 from the upload endpoint — the rest of the
 * platform stays operational.
 */
@Configuration
@ConditionalOnProperty(prefix = "hcm.storage.minio", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class MinioConfig {

    private static final Logger log = LoggerFactory.getLogger(MinioConfig.class);

    @Value("${hcm.storage.minio.endpoint}")   private String endpoint;
    @Value("${hcm.storage.minio.access-key}") private String accessKey;
    @Value("${hcm.storage.minio.secret-key}") private String secretKey;
    @Value("${hcm.storage.minio.bucket}")     private String bucket;
    @Value("${hcm.backup.bucket:hcm-backups}") private String backupBucket;

    @Bean
    public MinioClient minioClient() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        ensureBucket(client, bucket);
        ensureBucket(client, backupBucket);
        return client;
    }

    private void ensureBucket(MinioClient client, String bucketName) {
        try {
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
                log.info("MinIO bucket '{}' created at {}", bucketName, endpoint);
            } else {
                log.info("MinIO bucket '{}' ready at {}", bucketName, endpoint);
            }
        } catch (Exception e) {
            // Don't kill the app — storage just won't work until MinIO is up.
            log.warn("MinIO unreachable at {} on startup ({}). Bucket '{}' will be created on first use.",
                    endpoint, e.getMessage(), bucketName);
        }
    }
}
