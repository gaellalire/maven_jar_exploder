package fr.gaellalire.maven_jar_exploder;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;

import fr.gaellalire.maven_jar_exploder.DynamicZip.FileMeta;

public abstract class AbstractZipArchiveEntryFileMeta implements FileMeta {

    private ZipArchiveEntry zipArchiveEntry;
    
    private String sha512;
    
    private String compressedSha512;
    
    public AbstractZipArchiveEntryFileMeta(ZipArchiveEntry zipArchiveEntry, String sha512, String compressedSha512) {
        this.zipArchiveEntry = zipArchiveEntry;
        this.sha512 = sha512;
        this.compressedSha512 = compressedSha512;
    }
    
    @Override
    public String getComment() {
        return zipArchiveEntry.getComment();
    }
    
    @Override
    public byte[] getCentralDirectoryExtra() {
        return zipArchiveEntry.getCentralDirectoryExtra();
    }
    
    @Override
    public int getFlag() {
        return zipArchiveEntry.getRawFlag();
    }
    
    public String getSha512() {
        return sha512;
    }
    
    public String getCompressedSha512() {
        return compressedSha512;
    }

    @Override
    public long getCrc32() {
        return zipArchiveEntry.getCrc();
    }

    @Override
    public String getName() {
        return zipArchiveEntry.getName();
    }

    @Override
    public long getSize() {
        return zipArchiveEntry.getSize();
    }

    @Override
    public long getTime() {
        return zipArchiveEntry.getTime();
    }

    @Override
    public long getCompressedSize() {
        return zipArchiveEntry.getCompressedSize();
    }

    @Override
    public int getMethod() {
        return zipArchiveEntry.getMethod();
    }
    
    @Override
    public int getInternalFileAttributes() {
        return zipArchiveEntry.getInternalAttributes();
    }
    
    @Override
    public long getExternalFileAttributes() {
        return zipArchiveEntry.getExternalAttributes();
    }
    
    @Override
    public int getVersionMadeBy() {
        return zipArchiveEntry.getVersionMadeBy();
    }
    
    @Override
    public int getVersionRequired() {
        return zipArchiveEntry.getVersionRequired();
    }
    

}
