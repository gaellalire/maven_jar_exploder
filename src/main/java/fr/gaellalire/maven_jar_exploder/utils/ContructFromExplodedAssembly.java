package fr.gaellalire.maven_jar_exploder.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;

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

    static class Dep implements Comparable<Dep> {
        int position;

        String name;

        long time;

        String url;

        long externalAttributes;

        String[] positions;

        String[] times;

        String[] externalAttributesList;

        String[] files;

        public Dep(int position, String name, long time, long externalAttributes, String[] positions, String[] times, String[] externalAttributesList, String[] files, String url) {
            this.position = position;
            this.name = name;
            this.time = time;
            this.externalAttributes = externalAttributes;
            this.positions = positions;
            this.times = times;
            this.externalAttributesList = externalAttributesList;
            this.files = files;
            this.url = url;
        }

        @Override
        public int compareTo(Dep o) {
            return position - o.position;
        }
    }

    static class FileOverride implements Comparable<FileOverride> {
        int position;

        String name;
        
        String explodedName;

        long time;

        long externalAttributes;

        public FileOverride(int position, String explodedName, String name, long time, long externalAttributes) {
            this.position = position;
            this.explodedName = explodedName;
            this.name = name;
            this.time = time;
            this.externalAttributes = externalAttributes;
        }

        @Override
        public int compareTo(FileOverride o) {
            return position - o.position;
        }
    }

    public static void construct(File explodedAssembly, File destination) throws IOException, ArtifactResolutionException {
        File localRepository = new File(System.getProperty("user.home"), ".m2" + File.separator + "repository");
        RepositorySystem repositorySystem = MavenJarExploder.newRepositorySystem();
        RepositorySystemSession session = MavenJarExploder.newSession(repositorySystem, localRepository);

        List<RemoteRepository> repositories = Arrays.asList(new RemoteRepository.Builder("gaellalire-repo", "default", "https://gaellalire.fr/maven/repository/").build(),
                new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build());

        File tmpFiles = new File("tmpFiles");
        tmpFiles.mkdirs();
        Properties properties = new Properties();
        TreeSet<Dep> depSet = new TreeSet<Dep>();
        try (ZipFile zipFile = new ZipFile(explodedAssembly)) {
            ZipArchiveEntry entry = zipFile.getEntry("META-INF/exploded-assembly.properties");
            properties.load(zipFile.getInputStream(entry));
        
            for (String dep : properties.getProperty("dependencies").split(",")) {
                int position = Integer.parseInt(properties.getProperty(dep + ".position"));
                String name = properties.getProperty(dep + ".name");
                long time = Long.parseLong(properties.getProperty(dep + ".time"));
                long externalAttributes = Long.parseLong(properties.getProperty(dep + ".externalAttributes"));
                String url = properties.getProperty(dep + ".url");
                String property = properties.getProperty(dep + ".repackage.positions");
                String[] positions = null;
                String[] times = null;
                String[] externalAttributesList = null;
                String[] files = null;
                if (property != null) {
                    positions = property.split(",");
                    times = properties.getProperty(dep + ".repackage.times").split(",");
                    externalAttributesList = properties.getProperty(dep + ".repackage.externalAttributesList").split(",");
                    property = properties.getProperty(dep + ".repackage.files");
                    if (property != null) {
                        files = property.split(",");
                        for (String fileName : files) {
                            try (FileOutputStream fos = new FileOutputStream(new File(tmpFiles, fileName))) {
                                IOUtils.copy(zipFile.getInputStream(zipFile.getEntry("META-INF/exploded-assembly-files/" + fileName)), fos);
                            }
                        }
                    }

                }
                depSet.add(new Dep(position, name, time, externalAttributes, positions, times, externalAttributesList, files, url));
            }
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
                        Matcher matcher = MavenJarExploder.MVN_URL_PATTERN.matcher(currentDep.url);
                        if (matcher.matches()) {
                            String extension = matcher.group(4);
                            if (extension == null) {
                                extension = "jar";
                            }
                            DefaultArtifact artifact = new DefaultArtifact(matcher.group(1), matcher.group(2), matcher.group(5), extension, matcher.group(3));
                            ArtifactResult resolveArtifact = repositorySystem.resolveArtifact(session, new ArtifactRequest(artifact, repositories, null));

                            File file = resolveArtifact.getArtifact().getFile();
                            if (currentDep.positions != null) {
                                // need to create a new file
                                File tmpFile = new File("data.zip");
                                List<FileOverride> fileOverrides = new ArrayList<FileOverride>();
                                if (currentDep.files != null) {
                                    for (String fileNumber : currentDep.files) {
                                        fileOverrides.add(new FileOverride(Integer.parseInt(properties.getProperty(fileNumber + ".position")), fileNumber,
                                                properties.getProperty(fileNumber + ".name"), Long.parseLong(properties.getProperty(fileNumber + ".time")),
                                                Long.parseLong(properties.getProperty(fileNumber + ".externalAttributes"))));
                                    }
                                }
                                FileOverride cFO = null;
                                Iterator<FileOverride> iteratorFO = fileOverrides.iterator();
                                if (iteratorFO.hasNext()) {
                                    cFO = iteratorFO.next();
                                }

                                try (ZipArchiveOutputStream zosSub = new ZipArchiveOutputStream(new FileOutputStream(tmpFile))) {
                                    int zosPos = 1;
                                    for (int subPos = 0; subPos < currentDep.positions.length; subPos++) {
                                        while (cFO != null && zosPos == cFO.position) {
                                            ZipArchiveEntry zae = new ZipArchiveEntry(cFO.name);
                                            zae.setExternalAttributes(cFO.externalAttributes);
                                            zae.setTime(cFO.time);
                                            zosSub.putArchiveEntry(zae);
                                            try (FileInputStream fis = new FileInputStream(new File(tmpFiles, cFO.explodedName))) {
                                                IOUtils.copy(fis, zosSub);
                                            }
                                            zosSub.closeArchiveEntry();
                                            zosPos++;

                                            if (iteratorFO.hasNext()) {
                                                cFO = iteratorFO.next();
                                            }
                                        }
                                        int depSubPos = Integer.parseInt(currentDep.positions[subPos]);
                                        int currentSubPos = 1;
                                        try (ZipArchiveInputStream zisSub = new ZipArchiveInputStream(new FileInputStream(file))) {
                                            ZipArchiveEntry nextSubEntry = zisSub.getNextZipEntry();
                                            while (currentSubPos != depSubPos) {
                                                currentSubPos++;
                                                nextSubEntry = zisSub.getNextZipEntry();
                                            }
                                            ZipArchiveEntry zae = new ZipArchiveEntry(nextSubEntry.getName());
                                            String s = currentDep.externalAttributesList[subPos];
                                            if (!"_".equals(s)) {
                                                zae.setExternalAttributes(Long.parseLong(s));
                                            } else {
                                                zae.setExternalAttributes(nextSubEntry.getExternalAttributes());
                                            }
                                            s = currentDep.times[subPos];
                                            if (!"_".equals(s)) {
                                                zae.setTime(Long.parseLong(s));
                                            } else {
                                                zae.setTime(nextSubEntry.getTime());
                                            }
                                            zosSub.putArchiveEntry(zae);
                                            IOUtils.copy(zisSub, zosSub);
                                            zosSub.closeArchiveEntry();
                                            zosPos++;
                                        }
                                    }
                                }

                                file = tmpFile;
                            }

                            ZipArchiveEntry archiveEntry = new ZipArchiveEntry(file, currentDep.name);
                            archiveEntry.setTime(currentDep.time);
                            archiveEntry.setExternalAttributes(currentDep.externalAttributes);
                            zos.putArchiveEntry(archiveEntry);
                            try (FileInputStream input = new FileInputStream(file)) {
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
