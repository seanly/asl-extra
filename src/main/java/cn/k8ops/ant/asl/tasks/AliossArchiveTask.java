package cn.k8ops.ant.asl.tasks;

import cn.k8ops.ant.asl.reports.ArchiveXmlReport;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import lombok.SneakyThrows;
import org.apache.tools.ant.BuildException;

import java.io.File;
import java.io.FileInputStream;
import java.util.List;

import static org.apache.tools.ant.util.StringUtils.trimToNull;

public class AliossArchiveTask extends ArchiveTask{

    @Override
    public void execute() {
        OSS client = new OSSClientBuilder()
                .build(endpoint, accessKey, secretKey);

        if (!client.doesBucketExist(bucket)) {
            throw new BuildException("bucket is not exists");
        }

        List<File> files = scanFileSets();
        ArchiveXmlReport report = new ArchiveXmlReport();

        for (File file: files) {
            if (!file.exists()) {
                throw new BuildException(String.format("file(%s) is not exists", file.getName()));
            }

            String url = archiveFile(client, file);
            if (trimToNull(url) == null) {
                throw new BuildException("file archive error");
            }
            report.addArchive(url);
        }

        client.shutdown();

        report.save(xmlReport);
        log("archive(alicloud oss) is ok");
    }

    @SneakyThrows
    private String archiveFile(OSS client, File file) {
        String archivePath = String.format("%s/%s", remotePath, file.getName());
        client.putObject(bucket, archivePath, new FileInputStream(file));
        return String.format("%s/%s/%s", endpoint, bucket, archivePath);
    }
}
