package fr.gaellalire.maven_jar_exploder.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

public class CompareContent {

    public static void compare(File file1, File file2) throws IOException {
        try (ZipArchiveInputStream zis1 = new ZipArchiveInputStream(new FileInputStream(file1));
                ZipArchiveInputStream zis2 = new ZipArchiveInputStream(new FileInputStream(file2));) {
            do {
                ZipArchiveEntry nextEntry1 = zis1.getNextZipEntry();
                ZipArchiveEntry nextEntry2 = zis2.getNextZipEntry();

                if (nextEntry1 == null || nextEntry2 == null) {
                    if (nextEntry2 != null || nextEntry2 != null) {
                        throw new IOException("number of file is different");
                    }
                    break;
                }

                if (nextEntry1.getExternalAttributes() != nextEntry2.getExternalAttributes()) {
                    throw new IOException(nextEntry1.getName() + " externalAttributes is different");
                }
                if (nextEntry1.getTime() != nextEntry2.getTime()) {
                    throw new IOException(nextEntry1.getName() + " time is different");
                }
                if (!nextEntry1.getName().equals(nextEntry2.getName())) {
                    throw new IOException(nextEntry1.getName() + " name is different");
                }

                File subFile1 = File.createTempFile("tmp", ".data");
                File subFile2 = File.createTempFile("tmp", ".data");
                try {
                    try (FileOutputStream fos1 = new FileOutputStream(subFile1); FileOutputStream fos2 = new FileOutputStream(subFile2)) {
                        IOUtils.copy(zis1, fos1);
                        IOUtils.copy(zis2, fos2);
                    }
                    if (!FileUtils.contentEquals(subFile1, subFile2)) {
                        try {
                            compare(subFile1, subFile2);
                        } catch (IOException e) {
                            throw new IOException(nextEntry1.getName() + " is different", e);
                        }
                    }
                } finally {
                    subFile1.delete();
                    subFile2.delete();
                }

            } while (true);

        }
    }

}
