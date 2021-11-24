package fr.gaellalire.maven_jar_exploder.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.IOUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import fr.gaellalire.maven_jar_exploder.MavenJarExploder;

public class ContructFromExplodedAssembly {

    public static final Pattern MVN_URL_PATTERN = Pattern.compile("mvn:([^/]*)/([^/]*)/([^/]*)(?:/([^/]*)(?:/([^/]*))?)?");

    static class Dep implements Comparable<Dep> {
        int position;

        String name;

        long time;

        String url;

        long externalAttributes;

        public Dep(int position, String name, long time, long externalAttributes, String url) {
            this.position = position;
            this.name = name;
            this.time = time;
            this.externalAttributes = externalAttributes;
            this.url = url;
        }

        @Override
        public int compareTo(Dep o) {
            return position - o.position;
        }
    }

    public static void construct(File explodedAssembly, File destination) throws IOException, ArtifactResolutionException {
        File localRepository = new File(System.getProperty("user.home"), ".m2" + File.separator + "repository");
        RepositorySystem repositorySystem = MavenJarExploder.newRepositorySystem();
        RepositorySystemSession session = MavenJarExploder.newSession(repositorySystem, localRepository);

        List<RemoteRepository> repositories = Arrays.asList(new RemoteRepository.Builder("gaellalire-repo", "default", "https://gaellalire.fr/maven/repository/").build(),
                new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build());

        Properties properties = new Properties();
        try (ZipFile zipFile = new ZipFile(explodedAssembly)) {
            ZipArchiveEntry entry = zipFile.getEntry("META-INF/exploded-assembly.properties");
            properties.load(zipFile.getInputStream(entry));
        }
        TreeSet<Dep> depSet = new TreeSet<Dep>();
        for (String dep : properties.getProperty("dependencies").split(",")) {
            int position = Integer.parseInt(properties.getProperty(dep + ".position"));
            String name = properties.getProperty(dep + ".name");
            long time = Long.parseLong(properties.getProperty(dep + ".time"));
            long externalAttributes = Long.parseLong(properties.getProperty(dep + ".externalAttributes"));
            String url = properties.getProperty(dep + ".url");
            depSet.add(new Dep(position, name, time, externalAttributes, url));
        }
        int position = 0;
        Iterator<Dep> iterator = depSet.iterator();
        Dep currentDep = null;
        if (iterator.hasNext()) {
            currentDep = iterator.next();
        }

        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new FileOutputStream(destination))) {
            try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new FileInputStream(explodedAssembly))) {
                ZipArchiveEntry nextEntry = zis.getNextZipEntry();
                while (nextEntry != null) {
                    position++;
                    while (currentDep != null && position == currentDep.position) {
                        Matcher matcher = MVN_URL_PATTERN.matcher(currentDep.url);
                        if (matcher.matches()) {
                            String extension = matcher.group(4);
                            if (extension == null) {
                                extension = "jar";
                            }
                            DefaultArtifact artifact = new DefaultArtifact(matcher.group(1), matcher.group(2), matcher.group(5), extension, matcher.group(3));
                            ArtifactResult resolveArtifact = repositorySystem.resolveArtifact(session, new ArtifactRequest(artifact, repositories, null));

                            ZipArchiveEntry archiveEntry = new ZipArchiveEntry(resolveArtifact.getArtifact().getFile(), currentDep.name);
                            archiveEntry.setTime(currentDep.time);
                            archiveEntry.setExternalAttributes(currentDep.externalAttributes);
                            zos.putArchiveEntry(archiveEntry);
                            try (FileInputStream input = new FileInputStream(resolveArtifact.getArtifact().getFile())) {
                                IOUtils.copy(input, zos);
                            }
                            zos.closeArchiveEntry();
                        }
                        if (iterator.hasNext()) {
                            currentDep = iterator.next();
                        }
                        position++;
                    }
                    if ("META-INF/exploded-assembly.properties".equals(nextEntry.getName())) {
                        // skip exploded-assembly.properties
                        nextEntry = zis.getNextZipEntry();
                        continue;
                    }

                    zos.putArchiveEntry(nextEntry);
                    IOUtils.copy(zis, zos);
                    zos.closeArchiveEntry();
                    nextEntry = zis.getNextZipEntry();
                }

            }
        }

    }

}
