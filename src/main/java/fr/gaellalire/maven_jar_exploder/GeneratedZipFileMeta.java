package fr.gaellalire.maven_jar_exploder;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import fr.gaellalire.maven_jar_exploder.DynamicZip.FileMeta;

public class GeneratedZipFileMeta extends AbstractZipArchiveEntryFileMeta implements FileMeta {
    
    private List<FileMeta> fileMetas;
    
    private boolean zip64;
    
    public GeneratedZipFileMeta(ZipArchiveEntry zipArchiveEntry, String sha512, List<FileMeta> fileMetas) throws IOException {
        super(zipArchiveEntry, sha512, null);
        if (zipArchiveEntry.getSize() == DynamicZip.calculateZipMeta(fileMetas, true).getSize()) {
            zip64 = true;
        } else {
            if (zipArchiveEntry.getSize() != DynamicZip.calculateZipMeta(fileMetas, false).getSize()) {
                throw new IOException("Unable to match size");
            }
        }
    }

    @Override
    public InputStream getInputStream(long from, long size) throws IOException {
        return DynamicZip.getInputStream(fileMetas, from, size, zip64);
    }

}
