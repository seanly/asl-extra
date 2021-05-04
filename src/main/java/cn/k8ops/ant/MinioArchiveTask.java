package cn.k8ops.ant;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.SneakyThrows;
import org.apache.tools.ant.BuildException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import static org.apache.tools.ant.util.StringUtils.trimToNull;

public class MinioArchiveTask extends ArchiveTask {

    @SneakyThrows
    @Override
    public void execute() {
        MinioClient client = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();

        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
            throw new BuildException("bucket is not exists");
        }

        List<File> files = scanFileSets();

        for (File file: files) {
            if (!file.exists()) {
                throw new BuildException(String.format("file(%s) is not exists", file.getName()));
            }

            String url = archiveFile(client, file);
            if (trimToNull(url) == null) {
                throw new BuildException("file archive error");
            }
        }
        log("archive(minio) is ok");
    }

    @SneakyThrows
    private String archiveFile(MinioClient client, File file) {
        String archivePath = String.format("%s/%s", remotePath, file.getName());

        try (InputStream pis = new BufferedInputStream(new FileInputStream(file))) {
            client.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(archivePath)
                    .stream(pis, pis.available(), -1)
                    .build());
        } catch (Exception ex) {
            throw new BuildException("archive put error");
        }

        return String.format("%s/%s/%s", endpoint, bucket, archivePath);
    }
}
