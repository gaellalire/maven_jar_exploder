package fr.gaellalire.maven_jar_exploder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import fr.gaellalire.maven_jar_exploder.DynamicZip.FileMeta;

public class MavenRepositoryFileMeta extends AbstractZipArchiveEntryFileMeta implements FileMeta {

    private File file;

    public MavenRepositoryFileMeta(ZipArchiveEntry zipArchiveEntry, String sha512, String url, File localRepository) {
        super(zipArchiveEntry, sha512, sha512);
        file = MavenJarExploder.getFile(localRepository, url);
    }

    @Override
    public InputStream getInputStream(long from, long size) throws IOException {
        // already resolved
        return new SkipAndLimitFilterInputStream(new FileInputStream(file), from, size);
    }

}
