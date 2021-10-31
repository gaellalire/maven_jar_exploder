package fr.gaellalire.maven_jar_exploder;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import fr.gaellalire.maven_jar_exploder.DynamicZip.FileMeta;

public class NoContentFileMeta extends AbstractZipArchiveEntryFileMeta implements FileMeta {

    public NoContentFileMeta(ZipArchiveEntry zipArchiveEntry) {
        super(zipArchiveEntry, null, null);
    }

    @Override
    public InputStream getInputStream(long from, long size) throws IOException {
        return new InputStream() {

            @Override
            public int read() throws IOException {
                return -1;
            }
            
        };
    }

}
