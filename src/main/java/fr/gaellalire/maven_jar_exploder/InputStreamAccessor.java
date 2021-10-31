package fr.gaellalire.maven_jar_exploder;

import java.io.IOException;
import java.io.InputStream;

public interface InputStreamAccessor {

    InputStream getInputStream(long from, long size) throws IOException;

}
