package fr.gaellalire.maven_jar_exploder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;

public final class DynamicZip {
    
    private DynamicZip() {
    }

    public static final long ZIP64_MAGIC = 0xFFFFFFFFL;

    static abstract class ZipHeader {

        public void writeShort(OutputStream os, int shortValue) throws IOException {
            os.write(shortValue & 0xFF);
            os.write((shortValue & 0xFF00) >> 8);
        }

        public void writeInt(OutputStream os, long intValue) throws IOException {
            os.write((int) (intValue & 0xFF));
            os.write((int) ((intValue & 0xFF00) >> 8));
            os.write((int) ((intValue & 0xFF0000) >> 16));
            os.write((int) ((intValue & 0xFF000000) >> 24));
        }

        public void writeLong(OutputStream os, long longValue) throws IOException {
            os.write((int) (longValue & 0xFF));
            os.write((int) ((longValue & 0xFF00) >> 8));
            os.write((int) ((longValue & 0xFF0000) >> 16));
            os.write((int) ((longValue & 0xFF000000) >> 24));
            os.write((int) ((longValue & 0xFF00000000L) >> 32));
            os.write((int) ((longValue & 0xFF0000000000L) >> 40));
            os.write((int) ((longValue & 0xFF000000000000L) >> 48));
            os.write((int) ((longValue & 0xFF00000000000000L) >> 56));
        }

        public abstract int getSize(boolean zip64);

        public abstract void write(OutputStream os, boolean zip64) throws IOException;

    }

    static abstract class ZipFileHeader extends ZipHeader {

        static int zip64Id = 0x0001;

        static int flag = 0x800;
        
        int versionRequired;

        int compressionMethod;

        long lastModificationTime;

        long crc32;

        long compressedSize;

        long uncompressedSize;

        protected byte[] fileName;

        public void setFileName(String fileName) {
            try {
                this.fileName = fileName.getBytes("UTF-8");
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        public void setCompressedSize(long compressedSize) {
            this.compressedSize = compressedSize;
        }

        public void setUncompressedSize(long uncompressedSize) {
            this.uncompressedSize = uncompressedSize;
        }

        public void setCrc32(long crc32) {
            this.crc32 = crc32;
        }
        
        public void setVersionRequired(int versionRequired) {
            this.versionRequired = versionRequired;
        }

    }

    static class ZipLocalHeader extends ZipFileHeader {

        public static final int ZIP64_LOCAL_HEADER_SIZE = 20;

        static long sig = 0x04034b50;

        static int getSize(String fileName, boolean zip64) {
            try {
                int zip64Amount = 0;
                if (zip64) {
                    zip64Amount = ZIP64_LOCAL_HEADER_SIZE;
                }
                return zip64Amount + 30 + fileName.getBytes("UTF-8").length;
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        public int getSize(boolean zip64) {
            int zip64Amount = 0;
            if (zip64) {
                zip64Amount = ZIP64_LOCAL_HEADER_SIZE;
            }
            return zip64Amount + 30 + fileName.length;
        }

        public void write(OutputStream os, boolean zip64) throws IOException {
            writeInt(os, sig);
            // version needed to extract (minimum)
            writeShort(os, versionRequired);
            writeShort(os, flag);
            writeShort(os, compressionMethod);
            writeInt(os, lastModificationTime);
            writeInt(os, crc32);
            if (zip64) {
                writeInt(os, ZIP64_MAGIC);
                writeInt(os, ZIP64_MAGIC);
            } else {
                writeInt(os, compressedSize);
                writeInt(os, uncompressedSize);
            }
            writeShort(os, fileName.length);
            // extra field size
            if (zip64) {
                writeShort(os, ZIP64_LOCAL_HEADER_SIZE);
            } else {
                writeShort(os, 0);
            }
            os.write(fileName);
            if (zip64) {
                writeShort(os, zip64Id);
                writeShort(os, 0x10); // extra field
                writeLong(os, uncompressedSize);
                writeLong(os, compressedSize);
            }
        }

    }

    static class ZipCentralHeader extends ZipFileHeader {

        public static final int ZIP64_CENTRAL_HEADER_SIZE = 32;

        static long sig = 0x02014b50;

        byte[] comment = new byte[0];
        
        int versionMadeBy;
        
        int diskNumber;

        int internalFileAttributes;

        long externalFileAttributes;

        long offset;

        static ZipCentralHeader fromZipLocalHeader(ZipLocalHeader zipLocalHeader, long offset, int internalFileAttributes, long externalFileAttributes, int versionMadeBy) {
            ZipCentralHeader zipCentralHeader = new ZipCentralHeader();
            zipCentralHeader.compressionMethod = zipLocalHeader.compressionMethod;
            zipCentralHeader.lastModificationTime = zipLocalHeader.lastModificationTime;
            zipCentralHeader.crc32 = zipLocalHeader.crc32;
            zipCentralHeader.compressedSize = zipLocalHeader.compressedSize;
            zipCentralHeader.uncompressedSize = zipLocalHeader.uncompressedSize;
            zipCentralHeader.fileName = zipLocalHeader.fileName;
            zipCentralHeader.versionRequired = zipLocalHeader.versionRequired;
            zipCentralHeader.offset = offset;
            zipCentralHeader.internalFileAttributes = internalFileAttributes;
            zipCentralHeader.externalFileAttributes = externalFileAttributes;
            zipCentralHeader.versionMadeBy = versionMadeBy;
            return zipCentralHeader;
        }

        static int getSize(String fileName, String comment, boolean zip64) {
            int zip64Amount = 0;
            if (zip64) {
                zip64Amount = ZIP64_CENTRAL_HEADER_SIZE;
            }
            try {
                return zip64Amount + 46 + fileName.getBytes("UTF-8").length + comment.getBytes("UTF-8").length;
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        public int getSize(boolean zip64) {
            int zip64Amount = 0;
            if (zip64) {
                zip64Amount = ZIP64_CENTRAL_HEADER_SIZE;
            }
            return zip64Amount + 46 + fileName.length + comment.length;
        }

        public void write(OutputStream os, boolean zip64) throws IOException {
            writeInt(os, sig);
            // version made by
            writeShort(os, versionMadeBy);
            // version needed to extract (minimum)
            writeShort(os, versionRequired);
            writeShort(os, flag);
            writeShort(os, compressionMethod);
            writeInt(os, lastModificationTime);
            writeInt(os, crc32);
            if (zip64) {
                writeInt(os, ZIP64_MAGIC);
                writeInt(os, ZIP64_MAGIC);
            } else {
                writeInt(os, compressedSize);
                writeInt(os, uncompressedSize);
            }
            writeShort(os, fileName.length);
            if (zip64) {
                writeShort(os, ZIP64_CENTRAL_HEADER_SIZE);
            } else {
                writeShort(os, 0);
            }
            writeShort(os, comment.length);
            writeShort(os, diskNumber);
            writeShort(os, internalFileAttributes);
            writeInt(os, externalFileAttributes);
            if (zip64) {
                writeInt(os, ZIP64_MAGIC);
            } else {
                writeInt(os, offset);
            }
            os.write(fileName);
            if (zip64) {
                writeShort(os, zip64Id);
                writeShort(os, ZIP64_CENTRAL_HEADER_SIZE - 4); // extra field
                writeLong(os, uncompressedSize);
                writeLong(os, compressedSize);
                writeLong(os, offset);
                writeInt(os, diskNumber); // disk
            }
            os.write(comment);
        }

    }

    static class ZipCentralHeaderEnd extends ZipHeader {

        public static final int ZIP64_END_HEADER_SIZE = 56;

        public static final int ZIP64_END_LOCATOR_HEADER_SIZE = 20;

        static long sig = 0x06054b50;

        static long zip64Sig = 0x06064b50;

        static long zip64LocatorSig = 0x07064b50;

        int diskNumber;

        int centralDiskNumber;

        long diskCentralHeaderNumber;

        long totalCentralHeaderNumber;

        long centralSize;

        long centralOffset;

        byte[] comment = new byte[0];

        public static int getSize(boolean zip64, String comment, boolean empty) {
            try {
                int zip64Amount = 0;
                if (zip64 && !empty) {
                    zip64Amount = ZIP64_END_HEADER_SIZE + ZIP64_END_LOCATOR_HEADER_SIZE + comment.getBytes("UTF-8").length;
                }
                return zip64Amount + 22 + comment.getBytes("UTF-8").length;
            } catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }

        public int getSize(boolean zip64) {
            int zip64Amount = 0;
            if (zip64 && centralSize != 0) {
                zip64Amount = ZIP64_END_HEADER_SIZE + ZIP64_END_LOCATOR_HEADER_SIZE + comment.length;
            }
            return zip64Amount + 22 + comment.length;
        }

        public void write(OutputStream os, boolean zip64) throws IOException {
            if (zip64 && centralSize != 0) {
                writeInt(os, zip64Sig);
                writeLong(os, ZIP64_END_HEADER_SIZE - 12 + comment.length); // rest
                                                                            // of
                                                                            // header
                                                                            // size
                writeShort(os, 0x2d);
                writeShort(os, 0x2d);
                writeInt(os, diskNumber); // this disk
                writeInt(os, centralDiskNumber); // disk for central
                writeLong(os, diskCentralHeaderNumber);
                writeLong(os, totalCentralHeaderNumber);
                writeLong(os, centralSize);
                writeLong(os, centralOffset);
                os.write(comment);

                writeInt(os, zip64LocatorSig);
                writeInt(os, 0); // diskNumberStart
                writeLong(os, centralOffset + centralSize); // our offset
                writeInt(os, 1); // one for not split zip
            }
            writeInt(os, sig);
            writeShort(os, diskNumber);
            writeShort(os, centralDiskNumber);
            writeShort(os, (int) diskCentralHeaderNumber);
            writeShort(os, (int) totalCentralHeaderNumber);
            writeInt(os, Math.min(centralSize, ZIP64_MAGIC));
            writeInt(os, Math.min(centralOffset, ZIP64_MAGIC));
            writeShort(os, comment.length);
            os.write(comment);
        }

    }

    public static class ZipMeta {

        private long size;

        private long time;

        public ZipMeta(long size, long time) {
            this.size = size;
            this.time = time;
        }

        public long getSize() {
            return size;
        }

        public long getTime() {
            return time;
        }
    }

    public static interface FileMeta {

        InputStream getInputStream(long from, long size) throws IOException;

        long getCrc32();

        String getName();

        long getSize();

        long getTime();

        long getCompressedSize();

        int getMethod();
        
        int getInternalFileAttributes();

        long getExternalFileAttributes();
        
        int getVersionMadeBy();

        int getVersionRequired();

    }

    static class ResumeInfo {
        
        int itemNumber;

        int part; // 0: local, 1: central, 2: endcentral

        ZipHeader header;

        int headerPosition;

        long filePosition;

        long centralOffset;

        public ResumeInfo(int part, int fileNumber, ZipHeader header, int headerPosition, long filePosition, long centralOffset) {
            this.part = part;
            this.itemNumber = fileNumber;
            this.header = header;
            this.headerPosition = headerPosition;
            this.filePosition = filePosition;
            this.centralOffset = centralOffset;
        }

    }

    public static ResumeInfo resume(List<? extends FileMeta> fileMetas, long offset, List<ZipCentralHeader> centralHeaders, boolean zip64) {
        long roffset = offset;
        int itemNumber = -1;
        long currentOffset = 0;
        ZipHeader header = null;
        int headerPosition = -2;
        int part = 0;
        long centralOffset = -1;

        if (fileMetas.size() == 0) {
            // handle empty zip
            ZipCentralHeaderEnd zipCentralHeaderEnd = new ZipCentralHeaderEnd();

            zipCentralHeaderEnd.centralOffset = 0;
            zipCentralHeaderEnd.diskCentralHeaderNumber = 0;
            zipCentralHeaderEnd.totalCentralHeaderNumber = 0;
            zipCentralHeaderEnd.centralSize = 0;

            int size = zipCentralHeaderEnd.getSize(zip64);
            currentOffset += size;

            roffset -= size;
            if (roffset < 0) {
                header = zipCentralHeaderEnd;
                headerPosition = size + (int) roffset;
            }
            return new ResumeInfo(2, itemNumber, header, headerPosition, roffset, centralOffset);
        }

        while (roffset > 0) {
            itemNumber++;
            headerPosition = -2; // header no read
            if (itemNumber >= fileMetas.size()) {
                // central header resume
                part++;
                break;
            }

            FileMeta fileMeta = fileMetas.get(itemNumber);

            ZipLocalHeader zipLocalHeader = new ZipLocalHeader();
            zipLocalHeader.setFileName(fileMeta.getName());
            int compressionMethod = fileMeta.getMethod();
            if (compressionMethod != ZipEntry.STORED) {
                zipLocalHeader.compressionMethod = compressionMethod;
                zipLocalHeader.setCompressedSize(fileMeta.getCompressedSize());
            } else {
                zipLocalHeader.setCompressedSize(fileMeta.getSize());
            }
            zipLocalHeader.setUncompressedSize(fileMeta.getSize());
            zipLocalHeader.setCrc32(fileMeta.getCrc32());
            zipLocalHeader.lastModificationTime = javaToExtendedDosTime(fileMeta.getTime());
            zipLocalHeader.setVersionRequired(fileMeta.getVersionRequired());
            centralHeaders.add(ZipCentralHeader.fromZipLocalHeader(zipLocalHeader, currentOffset, fileMeta.getInternalFileAttributes(), fileMeta.getExternalFileAttributes(), fileMeta.getVersionMadeBy()));
            int size = zipLocalHeader.getSize(zip64);
            currentOffset += size;

            roffset -= size;
            if (roffset < 0) {
                header = zipLocalHeader;
                headerPosition = size + (int) roffset;
                break;
            }

            headerPosition = -1; // header read
            long fileMetaSize = getSize(fileMeta);
            if (roffset <= fileMetaSize) {
                break;
            }
            currentOffset += fileMetaSize;
            roffset -= fileMetaSize;
        }

        if (part == 1) {
            itemNumber = -1;
            centralOffset = currentOffset;
            // write central
            while (roffset > 0) {
                itemNumber++;
                if (itemNumber >= centralHeaders.size()) {
                    // endcentral header resume
                    part++;
                    break;
                }
                ZipCentralHeader zipCentralHeader = centralHeaders.get(itemNumber);
                int size = zipCentralHeader.getSize(zip64);
                currentOffset += size;

                roffset -= size;
                if (roffset <= 0) {
                    header = zipCentralHeader;
                    headerPosition = size + (int) roffset;
                    break;
                }
            }

            if (part == 2) {
                ZipCentralHeaderEnd zipCentralHeaderEnd = new ZipCentralHeaderEnd();

                zipCentralHeaderEnd.centralOffset = centralOffset;
                zipCentralHeaderEnd.diskCentralHeaderNumber = centralHeaders.size();
                zipCentralHeaderEnd.totalCentralHeaderNumber = centralHeaders.size();
                zipCentralHeaderEnd.centralSize = currentOffset - centralOffset;

                int size = zipCentralHeaderEnd.getSize(zip64);
                currentOffset += size;

                roffset -= size;
                if (roffset < 0) {
                    header = zipCentralHeaderEnd;
                    headerPosition = size + (int) roffset;
                }
            }
        }

        return new ResumeInfo(part, itemNumber, header, headerPosition, roffset, centralOffset);
    }

    static final long DOSTIME_BEFORE_1980 = (1 << 21) | (1 << 16);

    @SuppressWarnings("deprecation") // Use of date methods
    private static long javaToDosTime(long time) {
        Date d = new Date(time);
        int year = d.getYear() + 1900;
        if (year < 1980) {
            return DOSTIME_BEFORE_1980;
        }
        return (year - 1980) << 25 | (d.getMonth() + 1) << 21 | d.getDate() << 16 | d.getHours() << 11 | d.getMinutes() << 5 | d.getSeconds() >> 1;
    }

    public static long javaToExtendedDosTime(long time) {
        if (time < 0) {
            return DOSTIME_BEFORE_1980;
        }
        long dostime = javaToDosTime(time);
        return (dostime != DOSTIME_BEFORE_1980) ? dostime + ((time % 2000) << 32) : DOSTIME_BEFORE_1980;
    }

    static class SkipAndLimitFilterOutputStream extends FilterOutputStream {

        private long skip;

        private long size;

        public SkipAndLimitFilterOutputStream(OutputStream out, long skip, long size) {
            super(out);
            this.skip = skip;
            this.size = size;
        }

        @Override
        public void write(int b) throws IOException {
            if (skip > 0) {
                skip--;
            } else {
                if (size < 0) {
                    out.write(b);
                } else if (size != 0) {
                    size--;
                    out.write(b);
                }
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (skip > 0) {
                if (skip >= len) {
                    skip -= len;
                } else {
                    if (size < 0) {
                        out.write(b, (int) (off + skip), (int) (len - skip));
                    } else if (size != 0) {
                        if (size <= len - skip) {
                            out.write(b, (int) (off + skip), (int) size);
                            size = 0;
                        } else {
                            out.write(b, (int) (off + skip), (int) (len - skip));
                            size -= len - skip;
                        }
                    }
                    skip = 0;
                }
            } else {
                if (size < 0) {
                    out.write(b, off, len);
                } else if (size != 0) {
                    if (size <= len) {
                        out.write(b, off, (int) (size));
                        size = 0;
                    } else {
                        out.write(b, off, len);
                        size -= len;
                    }
                }
            }
        }

    }

    public static interface InputStreamFactory {
        InputStream create() throws IOException;
    }

    public static class HeaderInputStreamFactory implements InputStreamFactory {

        ZipHeader header;

        int from;

        long size;

        boolean zip64;

        public HeaderInputStreamFactory(ZipHeader header, int from, long size, boolean zip64) {
            this.header = header;
            this.from = from;
            this.size = size;
            this.zip64 = zip64;
        }

        public InputStream create() throws IOException {
            ByteArrayOutputStream result = new ByteArrayOutputStream();
            if (from != 0 || size != -1) {
                header.write(new SkipAndLimitFilterOutputStream(result, from, size), zip64);
            } else {
                header.write(result, zip64);
            }
            return new ByteArrayInputStream(result.toByteArray());
        }
    }

    public static class FileMetaInputStreamFactory implements InputStreamFactory {

        FileMeta fileMeta;

        long from;

        long size;

        public FileMetaInputStreamFactory(FileMeta fileMeta, long from, long size) {
            this.fileMeta = fileMeta;
            this.from = from;
            this.size = size;
        }

        public InputStream create() throws IOException {
            return fileMeta.getInputStream(from, size);
        }
    }

    public static class DynamicZipInputStream extends InputStream {

        InputStream currentDelegate;

        Iterator<InputStreamFactory> delegateIterator;

        public DynamicZipInputStream(Iterator<InputStreamFactory> delegateIterator) throws IOException {
            this.delegateIterator = delegateIterator;
            if (delegateIterator.hasNext()) {
                currentDelegate = delegateIterator.next().create();
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (currentDelegate == null) {
                return -1;
            }
            int read;
            do {
                read = currentDelegate.read(b, off, len);
                if (read == -1) {
                    nextDelegate();
                } else {
                    return read;
                }
            } while (currentDelegate != null);
            return -1;
        }

        public int read() throws IOException {
            if (currentDelegate == null) {
                return -1;
            }
            int read;
            do {
                read = currentDelegate.read();
                if (read == -1) {
                    nextDelegate();
                } else {
                    return read;
                }
            } while (currentDelegate != null);
            return -1;
        }

        @Override
        public void close() throws IOException {
            if (currentDelegate != null) {
                currentDelegate.close();
                while (delegateIterator.hasNext()) {
                    delegateIterator.next();
                }
                currentDelegate = null;
            }
        }

        public void nextDelegate() throws IOException {
            currentDelegate.close();
            if (delegateIterator.hasNext()) {
                currentDelegate = delegateIterator.next().create();
            } else {
                currentDelegate = null;
            }
        }

    }

    public static long getSize(FileMeta fileMeta) {
        if (fileMeta.getMethod() != ZipEntry.STORED) {
            return fileMeta.getCompressedSize();
        }
        return fileMeta.getSize();
    }

    public static InputStream getInputStream(final List<? extends FileMeta> fileMetas, final long offset, final long size, final boolean zip64) throws IOException {
        List<InputStreamFactory> delegates = new ArrayList<>();
        if (size < -1 || size == 0) {
            return new DynamicZipInputStream(delegates.iterator());
        }
        long currentOffset = offset;
        long remainingSize = size;
        List<ZipCentralHeader> centralHeaders = new ArrayList<>();
        ResumeInfo resume = resume(fileMetas, offset, centralHeaders, zip64);
        if (resume.part <= 0) {
            Iterator<? extends FileMeta> iterator = fileMetas.iterator();
            for (int i = 0; i < resume.itemNumber; i++) {
                iterator.next();
            }
            FileMeta fileMeta = iterator.next();
            boolean callNext = true;

            long fileMetaSize = getSize(fileMeta);
            if (resume.headerPosition >= 0) {
                int sizeToWrite = resume.header.getSize(zip64) - resume.headerPosition;
                if (remainingSize != -1 && remainingSize <= sizeToWrite) {
                    delegates.add(new HeaderInputStreamFactory(resume.header, resume.headerPosition, remainingSize, zip64));
                    return new DynamicZipInputStream(delegates.iterator());
                }

                delegates.add(new HeaderInputStreamFactory(resume.header, resume.headerPosition, -1, zip64));
                if (remainingSize != -1) {
                    remainingSize -= sizeToWrite;
                }
                currentOffset += sizeToWrite;

                if (remainingSize != -1 && remainingSize <= fileMetaSize) {
                    delegates.add(new FileMetaInputStreamFactory(fileMeta, 0, remainingSize));
                    return new DynamicZipInputStream(delegates.iterator());
                }
                delegates.add(new FileMetaInputStreamFactory(fileMeta, 0, -1));
                if (remainingSize != -1) {
                    remainingSize -= fileMetaSize;
                }
                currentOffset += fileMetaSize;
            } else if (resume.headerPosition == -1) {
                long sizeToWrite = fileMetaSize - resume.filePosition;
                if (remainingSize != -1 && remainingSize <= sizeToWrite) {
                    delegates.add(new FileMetaInputStreamFactory(fileMeta, resume.filePosition, remainingSize));
                    return new DynamicZipInputStream(delegates.iterator());
                }
                delegates.add(new FileMetaInputStreamFactory(fileMeta, resume.filePosition, -1));
                if (remainingSize != -1) {
                    remainingSize -= sizeToWrite;
                }
                currentOffset += sizeToWrite;
            } else {
                callNext = false;
            }
            while (true) {
                if (callNext) {
                    if (!iterator.hasNext()) {
                        break;
                    }
                    fileMeta = iterator.next();
                    fileMetaSize = getSize(fileMeta);
                } else {
                    callNext = true;
                }
                ZipLocalHeader zipLocalHeader = new ZipLocalHeader();
                zipLocalHeader.setFileName(fileMeta.getName());
                int compressionMethod = fileMeta.getMethod();
                if (compressionMethod != ZipEntry.STORED) {
                    zipLocalHeader.compressionMethod = compressionMethod;
                    zipLocalHeader.setCompressedSize(fileMeta.getCompressedSize());
                } else {
                    zipLocalHeader.setCompressedSize(fileMeta.getSize());
                }
                zipLocalHeader.setUncompressedSize(fileMeta.getSize());
                zipLocalHeader.setCrc32(fileMeta.getCrc32());
                zipLocalHeader.setVersionRequired(fileMeta.getVersionRequired());
                zipLocalHeader.lastModificationTime = javaToExtendedDosTime(fileMeta.getTime());
                if (remainingSize != -1 && remainingSize <= zipLocalHeader.getSize(zip64)) {
                    delegates.add(new HeaderInputStreamFactory(zipLocalHeader, 0, remainingSize, zip64));
                    return new DynamicZipInputStream(delegates.iterator());
                }
                delegates.add(new HeaderInputStreamFactory(zipLocalHeader, 0, -1, zip64));
                centralHeaders.add(ZipCentralHeader.fromZipLocalHeader(zipLocalHeader, currentOffset, fileMeta.getInternalFileAttributes(), fileMeta.getExternalFileAttributes(), fileMeta.getVersionMadeBy()));
                if (remainingSize != -1) {
                    remainingSize -= zipLocalHeader.getSize(zip64);
                }
                currentOffset += zipLocalHeader.getSize(zip64);

                if (remainingSize != -1 && remainingSize <= fileMetaSize) {
                    delegates.add(new FileMetaInputStreamFactory(fileMeta, 0, remainingSize));
                    return new DynamicZipInputStream(delegates.iterator());
                }
                delegates.add(new FileMetaInputStreamFactory(fileMeta, 0, -1));
                if (remainingSize != -1) {
                    remainingSize -= fileMetaSize;
                }
                currentOffset += fileMetaSize;
            }
        }
        long centralOffset;
        if (resume.part >= 1) {
            centralOffset = resume.centralOffset;
        } else {
            centralOffset = currentOffset;
        }
        if (resume.part <= 1) {
            Iterator<ZipCentralHeader> iterator = centralHeaders.iterator();
            if (resume.part == 1) {
                int sizeToWrite = resume.header.getSize(zip64) - resume.headerPosition;
                if (remainingSize != -1 && remainingSize <= sizeToWrite) {
                    delegates.add(new HeaderInputStreamFactory(resume.header, resume.headerPosition, remainingSize, zip64));
                    return new DynamicZipInputStream(delegates.iterator());
                }
                delegates.add(new HeaderInputStreamFactory(resume.header, resume.headerPosition, -1, zip64));
                if (remainingSize != -1) {
                    remainingSize -= sizeToWrite;
                }
                currentOffset += sizeToWrite;

                for (int i = 0; i < resume.itemNumber + 1; i++) {
                    iterator.next();
                }
            }

            // write central
            while (iterator.hasNext()) {
                ZipCentralHeader zipCentralHeader = iterator.next();

                if (remainingSize != -1 && remainingSize <= zipCentralHeader.getSize(zip64)) {
                    delegates.add(new HeaderInputStreamFactory(zipCentralHeader, 0, remainingSize, zip64));
                    return new DynamicZipInputStream(delegates.iterator());
                }
                delegates.add(new HeaderInputStreamFactory(zipCentralHeader, 0, -1, zip64));

                if (remainingSize != -1) {
                    remainingSize -= zipCentralHeader.getSize(zip64);
                }
                currentOffset += zipCentralHeader.getSize(zip64);
            }
        }
        if (resume.part <= 2) {
            if (resume.part == 2) {
                int sizeToWrite = resume.header.getSize(zip64) - resume.headerPosition;
                if (remainingSize != -1 && remainingSize <= sizeToWrite) {
                    delegates.add(new HeaderInputStreamFactory(resume.header, resume.headerPosition, remainingSize, zip64));
                    return new DynamicZipInputStream(delegates.iterator());
                }
                delegates.add(new HeaderInputStreamFactory(resume.header, resume.headerPosition, -1, zip64));
                return new DynamicZipInputStream(delegates.iterator());
            }

            ZipCentralHeaderEnd zipCentralHeaderEnd = new ZipCentralHeaderEnd();

            zipCentralHeaderEnd.centralOffset = centralOffset;
            zipCentralHeaderEnd.diskCentralHeaderNumber = centralHeaders.size();
            zipCentralHeaderEnd.totalCentralHeaderNumber = centralHeaders.size();
            zipCentralHeaderEnd.centralSize = currentOffset - centralOffset;

            if (remainingSize != -1 && remainingSize <= zipCentralHeaderEnd.getSize(zip64)) {
                delegates.add(new HeaderInputStreamFactory(zipCentralHeaderEnd, 0, remainingSize, zip64));
                return new DynamicZipInputStream(delegates.iterator());
            }
            delegates.add(new HeaderInputStreamFactory(zipCentralHeaderEnd, 0, -1, zip64));
        }

        return new DynamicZipInputStream(delegates.iterator());
    }

    public static ZipMeta calculateZipMeta(List<? extends FileMeta> fileMetas, boolean zip64) {
        long size = 0;
        long time = 0;
        boolean empty = true;
        for (FileMeta fileMeta : fileMetas) {
            empty = false;
            if (fileMeta.getTime() > time) {
                time = fileMeta.getTime();
            }
            size += ZipLocalHeader.getSize(fileMeta.getName(), zip64);
            size += getSize(fileMeta);
            size += ZipCentralHeader.getSize(fileMeta.getName(), "", zip64);
        }
        size += ZipCentralHeaderEnd.getSize(zip64, "", empty);
        return new ZipMeta(size, time);
    }

}
