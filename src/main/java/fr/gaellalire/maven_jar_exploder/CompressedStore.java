package fr.gaellalire.maven_jar_exploder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.IOUtils;

public class CompressedStore {

    static class Key {
        String sha512;

        long size;

        long crc32;

        long compressedSize;

        String compressedSha512;

        public Key(String sha512, long size, long crc32, long compressedSize, String compressedSha512) {
            this.sha512 = sha512;
            this.size = size;
            this.crc32 = crc32;
            this.compressedSize = compressedSize;
            this.compressedSha512 = compressedSha512;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((compressedSha512 == null) ? 0 : compressedSha512.hashCode());
            result = prime * result + (int) (compressedSize ^ (compressedSize >>> 32));
            result = prime * result + (int) (crc32 ^ (crc32 >>> 32));
            result = prime * result + ((sha512 == null) ? 0 : sha512.hashCode());
            result = prime * result + (int) (size ^ (size >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof Key)) {
                return false;
            }
            Key other = (Key) obj;
            if (compressedSha512 == null) {
                if (other.compressedSha512 != null) {
                    return false;
                }
            } else if (!compressedSha512.equals(other.compressedSha512)) {
                return false;
            }
            if (compressedSize != other.compressedSize) {
                return false;
            }
            if (crc32 != other.crc32) {
                return false;
            }
            if (sha512 == null) {
                if (other.sha512 != null) {
                    return false;
                }
            } else if (!sha512.equals(other.sha512)) {
                return false;
            }
            if (size != other.size) {
                return false;
            }
            return true;
        }

    }

    private Map<Key, File> fileByKey = new HashMap<Key, File>();

    private AtomicInteger number = new AtomicInteger();
    
    private File home;
    
    public CompressedStore() {
        home = new File("compressedStore");
        home.mkdirs();
    }

    public InputStream getInputStream(String sha512, long size, long crc32, long compressedSize, String compressedSha512) throws IOException {
        File file = fileByKey.get(new Key(sha512, size, crc32, compressedSize, compressedSha512));
        return new FileInputStream(file);
    }

    public String store(String sha512, long size, long crc32, long compressedSize, InputStream inputStream) throws IOException {
        int incrementAndGet = number.incrementAndGet();
        File file = new File(home, "comp" + incrementAndGet + ".cache");
        MessageDigest messageDigest;
        try {
            messageDigest = MessageDigest.getInstance("SHA-512");
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
        try (FileOutputStream fileOutputStream = new FileOutputStream(file)) {
            IOUtils.copy(new DigestInputStream(inputStream, messageDigest), fileOutputStream);
        }
        String compressedSha512 = MavenJarExploder.toHexString(messageDigest.digest());
        fileByKey.put(new Key(sha512, size, crc32, compressedSize, compressedSha512), file);
        return compressedSha512;
    }

}
