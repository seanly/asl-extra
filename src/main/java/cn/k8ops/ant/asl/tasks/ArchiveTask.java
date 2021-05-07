package cn.k8ops.ant.asl.tasks;

import lombok.Getter;
import lombok.Setter;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Task;
import org.apache.tools.ant.types.FileSet;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public abstract class ArchiveTask extends Task {

    @Getter
    @Setter
    protected String endpoint;

    @Getter
    @Setter
    protected String accessKey;

    @Getter
    @Setter
    protected String secretKey;

    @Getter
    @Setter
    protected String bucket;

    @Getter
    @Setter
    protected String remotePath;

    protected List<FileSet> fileSets = new ArrayList<>();

    @Setter
    @Getter
    protected String xmlReport;

    public void addFileSet(FileSet fs) {
        fileSets.add(fs);
    }

    protected List<File> scanFileSets() {
        final List<File> list = new ArrayList<>();

        for (FileSet fs : fileSets) {
            DirectoryScanner ds = fs.getDirectoryScanner(getProject());
            ds.scan();

            String[] names = ds.getIncludedFiles();
            for (String element : names) {
                list.add(new File(ds.getBasedir(), element));
            }
        }

        return list;
    }
}
