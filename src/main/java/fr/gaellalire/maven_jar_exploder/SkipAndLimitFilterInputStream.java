package fr.gaellalire.maven_jar_exploder;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class SkipAndLimitFilterInputStream extends FilterInputStream {
    
    private long size;

    public SkipAndLimitFilterInputStream(InputStream in, long skip, long size) throws IOException {
        super(in);
        in.skip(skip);
        this.size = size;
    }

    @Override
    public int read() throws IOException {
        if (size == 0) {
            return -1;
        }
        int count = in.read();
        if (count > 0 && size > 0) {
            size--;
        }
        return count;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (size == 0) {
            return -1;
        }
        int rlen = len;
        if (size > 0) {
            if (len > size) {
                rlen = (int) size;
            }
        }
        int count = in.read(b, off, rlen);
        if (count > 0 && size > 0) {
            size -= count;
        }
        return count;
    }

}
