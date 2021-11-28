package fr.gaellalire.maven_jar_exploder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

public class FilePartFileMeta extends AbstractZipArchiveEntryFileMeta  {
    
    private File file;
    
    private long from;

    private long size;

    public FilePartFileMeta(ZipArchiveEntry zipArchiveEntry, String sha512, File file, long from, long size) {
        super(zipArchiveEntry, sha512, null);
        this.file = file;
        this.from = from;
        this.size = size;
    }

    @Override
    public InputStream getInputStream(long from, long size) throws IOException {
        return new SkipAndLimitFilterInputStream(new SkipAndLimitFilterInputStream(new FileInputStream(file), this.from, this.size), from, size);
    }

}
