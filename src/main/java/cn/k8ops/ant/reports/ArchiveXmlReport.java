package cn.k8ops.ant.reports;

import lombok.Data;
import lombok.SneakyThrows;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.util.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;

import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

@Data
public class ArchiveXmlReport {

    private List<String> archives = new ArrayList<>();

    public void addArchive(String archive) {
        archives.add(archive);
    }

    @SneakyThrows
    public void save(String filePath) {
        if (StringUtils.trimToNull(filePath) == null) {
            return;
        }

        File xmlFile = new File(filePath);

        if (!xmlFile.getParentFile().exists()) {
            xmlFile.getParentFile().mkdirs();
        }

        Document doc = DocumentHelper.createDocument();
        Element archiveElement = doc.addElement("archive");
        Element filesElement = archiveElement.addElement("files");

        for(String archive: archives) {
            filesElement.addElement("file").addAttribute("path", archive);
        }

        FileWriter out = new FileWriter(xmlFile);
        doc.write(out);
        out.close();
    }
}
