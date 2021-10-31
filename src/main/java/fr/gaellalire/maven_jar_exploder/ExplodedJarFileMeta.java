package fr.gaellalire.maven_jar_exploder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import fr.gaellalire.maven_jar_exploder.DynamicZip.FileMeta;

public class ExplodedJarFileMeta extends AbstractZipArchiveEntryFileMeta implements FileMeta {

    private File explodedJar;

    private long offset;

    public ExplodedJarFileMeta(ZipArchiveEntry zipArchiveEntry, String sha512, File explodedJar, long offset) {
        super(zipArchiveEntry, sha512, null);
        this.explodedJar = explodedJar;
        this.offset = offset;
    }

    @Override
    public InputStream getInputStream(long from, long size) throws IOException {
        long remainSize = getCompressedSize() - from;
        if (size != -1 && size < remainSize) {
            remainSize = size;
        }
        return new SkipAndLimitFilterInputStream(new FileInputStream(explodedJar), offset + from, remainSize);
    }

}
