package fr.gaellalire.maven_jar_exploder;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import fr.gaellalire.maven_jar_exploder.DynamicZip.FileMeta;

public class CompressedStoreFileMeta extends AbstractZipArchiveEntryFileMeta implements FileMeta {
    
    private CompressedStore compressedStore;

    public CompressedStoreFileMeta(ZipArchiveEntry zipArchiveEntry, String sha512, String compressedSha512, CompressedStore compressedStore) {
        super(zipArchiveEntry, sha512, compressedSha512);
        this.compressedStore = compressedStore;
    }

    @Override
    public InputStream getInputStream(long from, long size) throws IOException {
        return new SkipAndLimitFilterInputStream(compressedStore.getInputStream(getSha512(), getSize(), getCrc32(), getCompressedSize(), getCompressedSha512()), from, size);
    }

}
