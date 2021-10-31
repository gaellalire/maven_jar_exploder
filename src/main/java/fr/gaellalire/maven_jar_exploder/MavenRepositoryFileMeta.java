package fr.gaellalire.maven_jar_exploder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import fr.gaellalire.maven_jar_exploder.DynamicZip.FileMeta;

public class MavenRepositoryFileMeta extends AbstractZipArchiveEntryFileMeta implements FileMeta {

    private String groupId;

    private String artifactId;

    private String version;

    private String extension;

    private File localRepository;

    public MavenRepositoryFileMeta(ZipArchiveEntry zipArchiveEntry, String sha512, File localRepository) {
        super(zipArchiveEntry, sha512, sha512);
    }

    @Override
    public InputStream getInputStream(long from, long size) throws IOException {
        // already resolved
        File file = new File(localRepository,
                groupId.replace('.', File.separatorChar) + artifactId + File.separatorChar + version + File.separatorChar + artifactId + "-" + version + "." + extension);
        return new SkipAndLimitFilterInputStream(new FileInputStream(file), from, size);
    }

}
