package fr.gaellalire.maven_jar_exploder.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.IOUtils;

public class CompareContent {
    
    public static boolean compare(File file1, File file2) throws IOException {
        try (ZipArchiveInputStream zis1 = new ZipArchiveInputStream(new FileInputStream(file1)); ZipArchiveInputStream zis2 = new ZipArchiveInputStream(new FileInputStream(file2));) {
            ZipArchiveEntry nextEntry1 = zis1.getNextZipEntry();
            ZipArchiveEntry nextEntry2 = zis2.getNextZipEntry();
            
            if (nextEntry1 == null || nextEntry2 == null) {
                if (nextEntry2 != null || nextEntry2 != null) {
                    return false;
                }
            }
            
            if (nextEntry1.getExternalAttributes() != nextEntry2.getExternalAttributes()) {
                return false;
            }
            if (nextEntry1.getTime() != nextEntry2.getTime()) {
                return false;
            }
            if (!nextEntry1.getName().equals(nextEntry2.getName())) {
                return false;
            }
            if (nextEntry1.getUnixMode() != nextEntry2.getUnixMode()) {
                return false;
            }
            
            if (!IOUtils.contentEquals(zis1, zis2)) {
                return false;
            }


        }
        return true;
    }

}
