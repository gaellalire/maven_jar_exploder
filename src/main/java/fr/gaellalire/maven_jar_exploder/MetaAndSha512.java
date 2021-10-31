package fr.gaellalire.maven_jar_exploder;

public class MetaAndSha512 {
    
    private String sha512;
    
    private int position;
    
    private String name;
    
    private long size;
    
    private long crc32;
    
    private long time;
    
    private long deflatedSize;
    
    private String url;

    public MetaAndSha512(String sha512, int position, String name, long size, long crc32, long time, long deflatedSize, String url) {
        this.sha512 = sha512;
        this.position = position;
        this.name = name;
        this.size = size;
        this.crc32 = crc32;
        this.time = time;
        this.deflatedSize = deflatedSize;
        this.url = url;
    }

    @Override
    public String toString() {
        return "MetaAndSha512 [sha512=" + sha512 + ", position=" + position + ", url=" + url + ", name=" + name + ", size=" + size + ", crc32=" + crc32 + ", time="
                + time + ", deflatedSize=" + deflatedSize + "]";
    }

    public String getSha512() {
        return sha512;
    }

    public int getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public long getCrc32() {
        return crc32;
    }

    public long getTime() {
        return time;
    }

    public long getDeflatedSize() {
        return deflatedSize;
    }
    
    public String getUrl() {
        return url;
    }

}
