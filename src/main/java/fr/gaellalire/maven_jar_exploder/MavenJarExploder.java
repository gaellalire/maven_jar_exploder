package fr.gaellalire.maven_jar_exploder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.gaellalire.maven_jar_exploder.DynamicZip.FileMeta;
import fr.gaellalire.maven_jar_exploder.utils.CompareContent;
import fr.gaellalire.maven_jar_exploder.utils.ContructFromExplodedAssembly;

public class MavenJarExploder {

    private static final Logger LOGGER = LoggerFactory.getLogger(MavenJarExploder.class);

    public static final Pattern MVN_URL_PATTERN = Pattern.compile("mvn:([^/]*)/([^/]*)/([^/]*)(?:/([^/]*)(?:/([^/]*))?)?");

    public static RepositorySystem newRepositorySystem() {
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);

    }

    public static RepositorySystemSession newSession(RepositorySystem system, File localRepository) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository(localRepository.toString());
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }

    public static String toHexString(final byte[] bytes) {
        if (bytes == null) {
            return null;
        }

        StringBuilder buffer = new StringBuilder(bytes.length * 2);

        for (byte aByte : bytes) {
            int b = aByte & 0xFF;
            if (b < 0x10) {
                buffer.append('0');
            }
            buffer.append(Integer.toHexString(b));
        }

        return buffer.toString();
    }

    public static File getFile(File localRepository, String url) {
        String groupId;
        String artifactId;
        String version;
        String extension;
        String classifier;

        Matcher matcher = MavenJarExploder.MVN_URL_PATTERN.matcher(url);
        if (!matcher.matches()) {
            return null;
        }
        groupId = matcher.group(1);
        artifactId = matcher.group(2);
        version = matcher.group(3);
        extension = matcher.group(4);
        if (extension == null) {
            extension = "jar";
        }
        classifier = matcher.group(5);
        String classifierS = "";
        if (classifier != null && classifier.length() != 0) {
            classifierS = "-" + classifier;
        }
        return new File(localRepository, groupId.replace('.', File.separatorChar) + File.separatorChar + artifactId + File.separatorChar + version + File.separatorChar + artifactId
                + "-" + version + classifierS + "." + extension);
    }

    public static List<FileMeta> subFile(List<MetaAndSha512> subMetas, File expectedFile, RepackagedJar repackagedJar, File localRepository, CompressedStore compressedStore)
            throws Exception {
        List<FileMeta> fileMetas = new ArrayList<FileMeta>();
        File originalFile = MavenJarExploder.getFile(localRepository, repackagedJar.getUrl());
        try (ZipFile originalZipFile = new ZipFile(originalFile)) {
            Iterator<MetaAndSha512> iterator = subMetas.iterator();
            try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new FileInputStream(expectedFile)); ZipFile zipFile = new ZipFile(expectedFile)) {
                ZipArchiveEntry nextEntry = zis.getNextZipEntry();
                while (nextEntry != null) {
                    String name = nextEntry.getName();
                    nextEntry = zipFile.getEntry(name);
                    MetaAndSha512 metaAndSha512 = iterator.next();

                    if (nextEntry.isDirectory() || nextEntry.getSize() == 0) {
                        fileMetas.add(new NoContentFileMeta(nextEntry));
                        nextEntry = zis.getNextZipEntry();
                        continue;
                    }

                    String sha512 = metaAndSha512.getSha512();

                    ZipArchiveEntry originalEntry = originalZipFile.getEntry(name);
                    try (InputStream is = new SkipAndLimitFilterInputStream(new FileInputStream(expectedFile), nextEntry.getDataOffset(), nextEntry.getCompressedSize());
                            InputStream ois = new SkipAndLimitFilterInputStream(new FileInputStream(originalFile), originalEntry.getDataOffset(),
                                    originalEntry.getCompressedSize())) {
                        if (IOUtils.contentEquals(is, ois)) {
                            fileMetas.add(new FilePartFileMeta(nextEntry, sha512, originalFile, originalEntry.getDataOffset(), originalEntry.getCompressedSize()));
                            nextEntry = zis.getNextZipEntry();
                            continue;
                        }
                    }

                    String compressedSha512;
                    try (InputStream is = new SkipAndLimitFilterInputStream(new FileInputStream(expectedFile), nextEntry.getDataOffset(), nextEntry.getCompressedSize())) {
                        compressedSha512 = compressedStore.store(sha512, nextEntry.getSize(), nextEntry.getCrc(), nextEntry.getCompressedSize(), is);
                    }

                    fileMetas.add(new CompressedStoreFileMeta(nextEntry, sha512, compressedSha512, compressedStore));

                    nextEntry = zis.getNextZipEntry();
                }

            }
        }

        return fileMetas;
    }

    static class AssemblyFile {
        int position;

        int subPosition;

        String name;

        public AssemblyFile(int position, int subPosition, String name) {
            this.position = position;
            this.subPosition = subPosition;
            this.name = name;
        }

    }

    public static void main(String[] args) throws Exception {
        String groupId = args[0];
        String artifactId = args[1];
        String version = args[2];
        String extension = args[3];

        boolean repackageExpected = "ear".equals(extension);
        File localRepository = new File(System.getProperty("user.home"), ".m2" + File.separator + "repository");

        RepositorySystem repositorySystem = newRepositorySystem();
        RepositorySystemSession session = newSession(repositorySystem, localRepository);

        List<RemoteRepository> repositories = Arrays.asList(new RemoteRepository.Builder("gaellalire-repo", "default", "https://gaellalire.fr/maven/repository/").build());

        CollectRequest collectRequest = new CollectRequest(null, repositories);

        collectRequest.setDependencies(Collections.singletonList(new Dependency(new DefaultArtifact(groupId, artifactId, "", extension, version), "runtime")));

        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, null);
        DependencyResult resolveDependencies = repositorySystem.resolveDependencies(session, dependencyRequest);

        boolean first = true;
        File jarWithDependencies = null;
        List<ArtifactResult> artifactResults = resolveDependencies.getArtifactResults();

        Map<String, MetaAndSha512> metaBySha512 = new HashMap<String, MetaAndSha512>();
        SubMetaIndexer subMetaIndexer = new SubMetaIndexer();

        // calculate sha512 in dependencies
        LOGGER.info("Analyzing dependencies");
        for (ArtifactResult artifactResult : artifactResults) {
            if (first) {
                jarWithDependencies = artifactResult.getArtifact().getFile();
                first = false;
                continue;
            }

            File dependency = artifactResult.getArtifact().getFile();

            Artifact artifact = artifactResult.getArtifact();
            String url;
            String classifier = artifact.getClassifier();
            String extension2 = artifact.getExtension();
            if (classifier != null && classifier.length() != 0) {
                url = "mvn:" + artifact.getGroupId() + "/" + artifact.getArtifactId() + "/" + artifact.getVersion() + "/" + extension2 + "/" + classifier;
            } else if ("jar".equals(extension2)) {
                url = "mvn:" + artifact.getGroupId() + "/" + artifact.getArtifactId() + "/" + artifact.getVersion();
            } else {
                url = "mvn:" + artifact.getGroupId() + "/" + artifact.getArtifactId() + "/" + artifact.getVersion() + "/" + extension2;
            }

            int position = 0;
            MessageDigest mainDigest = MessageDigest.getInstance("SHA-512");
            List<MetaAndSha512> subMetas = new ArrayList<MetaAndSha512>();
            try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new DigestInputStream(new FileInputStream(dependency), mainDigest))) {
                ZipArchiveEntry nextEntry = zis.getNextZipEntry();
                while (nextEntry != null) {
                    position++;

                    if (repackageExpected) {
                        MessageDigest subDigest = MessageDigest.getInstance("SHA-512");
                        DigestInputStream digestInputStream = new DigestInputStream(zis, subDigest);
                        while (digestInputStream.read() != -1)
                            ;

                        MetaAndSha512 metaAndSha512 = new MetaAndSha512(nextEntry.getName(), position, url, toHexString(subDigest.digest()), nextEntry.getExternalAttributes(),
                                nextEntry.getTime());

                        subMetas.add(metaAndSha512);
                    } else {
                        while (zis.read() != -1)
                            ;

                    }

                    nextEntry = zis.getNextZipEntry();
                }

            }
            MetaAndSha512 metaAndSha512 = new MetaAndSha512(dependency.getName(), -1, url, toHexString(mainDigest.digest()), null, null);
            subMetaIndexer.add(metaAndSha512, subMetas);
            metaBySha512.put(metaAndSha512.getSha512(), metaAndSha512);
        }
        LOGGER.info("Dependencies analyzed");

        subMetaIndexer.setMetaBySha512(metaBySha512);

        // compare with sha512 in assembly and create properties and fileMetas
        Properties properties = new Properties();
        List<FileMeta> fileMetas = new ArrayList<FileMeta>();
        CompressedStore compressedStore = new CompressedStore();
        // explodedJarFile should be attach to the original artifact with
        // exploded-assembly classifier and keep same extension than original
        // artifact

        LOGGER.info("Creating exploded-assembly.zip");

        File explodedJarFile = new File("exploded-assembly.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new FileOutputStream(explodedJarFile))) {
            int position = 0;
            int depNum = 0;
            int explodedFile = 0;
            List<String> urlList = new ArrayList<String>();
            List<AssemblyFile> assemblyFiles = new ArrayList<AssemblyFile>();
            // use ZipArchiveInputStream to get the correct order
            // use ZipFile to fetch attributes in central header
            try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new FileInputStream(jarWithDependencies)); ZipFile zipFile = new ZipFile(jarWithDependencies)) {
                ZipArchiveEntry nextEntry = zis.getNextZipEntry();
                while (nextEntry != null) {
                    position++;
                    String name = nextEntry.getName();
                    nextEntry = zipFile.getEntry(name);
                    if (nextEntry.isDirectory() || nextEntry.getSize() == 0) {
                        zos.putArchiveEntry(nextEntry);
                        zos.closeArchiveEntry();
                        fileMetas.add(new NoContentFileMeta(nextEntry));
                        nextEntry = zis.getNextZipEntry();
                        continue;
                    }
                    MessageDigest mainDigest = MessageDigest.getInstance("SHA-512");
                    DigestInputStream dis = new DigestInputStream(zis, mainDigest);
                    boolean validZip = false;
                    List<MetaAndSha512> subMetas = new ArrayList<MetaAndSha512>();
                    @SuppressWarnings("resource") // cannot close dis, it will
                                                  // also close zis
                    ZipArchiveInputStream subZis = new ZipArchiveInputStream(dis);
                    try {
                        int subPosition = 0;
                        ZipArchiveEntry subNextEntry = subZis.getNextZipEntry();
                        while (subNextEntry != null) {
                            validZip = true;
                            subPosition++;

                            if (repackageExpected) {
                                // this will be used for ear where war may
                                // be repackage (skinny option), we may need
                                // to reorder entries change time and
                                // replace some modified file (like
                                // manifest)
                                MessageDigest subDigest = MessageDigest.getInstance("SHA-512");
                                DigestInputStream digestInputStream = new DigestInputStream(subZis, subDigest);
                                while (digestInputStream.read() != -1)
                                    ;
                                subMetas.add(new MetaAndSha512(subNextEntry.getName(), subPosition, null, toHexString(subDigest.digest()), subNextEntry.getExternalAttributes(),
                                        subNextEntry.getTime()));
                            } else {
                                while (subZis.read() != -1)
                                    ;
                            }

                            subNextEntry = subZis.getNextZipEntry();
                        }
                    } catch (Exception e) {
                        validZip = false;
                    }

                    while (dis.read() != -1)
                        ;

                    String sha512 = toHexString(mainDigest.digest());
                    boolean found = false;
                    if (validZip) {
                        MetaAndSha512 dependencyMetaAndSha512 = metaBySha512.get(sha512);

                        if (dependencyMetaAndSha512 != null) {
                            found = true;
                            depNum++;
                            String property = properties.getProperty("dependencies");
                            if (property == null) {
                                properties.setProperty("dependencies", "dep" + depNum);
                            } else {
                                properties.setProperty("dependencies", property + ",dep" + depNum);
                            }
                            // following data are mandatory, help to reproduce
                            // the archive content as it was
                            properties.setProperty("dep" + depNum + ".position", String.valueOf(position));
                            properties.setProperty("dep" + depNum + ".name", nextEntry.getName());
                            properties.setProperty("dep" + depNum + ".externalAttributes", String.valueOf(nextEntry.getExternalAttributes()));
                            properties.setProperty("dep" + depNum + ".time", String.valueOf(nextEntry.getTime()));
                            properties.setProperty("dep" + depNum + ".url", dependencyMetaAndSha512.getUrl());

                            if (nextEntry.getMethod() == ZipEntry.STORED) {
                                // we can use the file from our repository, we
                                // may have to import it before (immediate space
                                // gain)
                                fileMetas.add(new MavenRepositoryFileMeta(nextEntry, sha512, dependencyMetaAndSha512.getUrl(), localRepository));
                            } else {
                                // if this is a war in ear which has same
                                // lifecyle than its ear, this case is very bad.
                                // The war is not skinny and
                                // it has a new version for each new version of
                                // ear. So we never gain space, no space lost
                                // though.
                                String compressedSha512;
                                try (InputStream is = new SkipAndLimitFilterInputStream(new FileInputStream(jarWithDependencies), nextEntry.getDataOffset(),
                                        nextEntry.getCompressedSize())) {
                                    compressedSha512 = compressedStore.store(sha512, nextEntry.getSize(), nextEntry.getCrc(), nextEntry.getCompressedSize(), is);
                                }
                                // copy compressed entry in a store so it can be
                                // reused when same compression is used (future
                                // space gain)
                                fileMetas.add(new CompressedStoreFileMeta(nextEntry, sha512, compressedSha512, compressedStore));
                            }
                        } else {
                            RepackagedJar repackagedJar = subMetaIndexer.search(subMetas);
                            if (repackagedJar != null) {
                                List<MetaAndSha512> filesToAdd = repackagedJar.getFilesToAdd();
                                StringBuilder fileNames = new StringBuilder();
                                boolean subFirst = true;
                                for (MetaAndSha512 fileMeta : filesToAdd) {
                                    explodedFile++;
                                    if (subFirst) {
                                        subFirst = false;
                                    } else {
                                        fileNames.append(",");
                                    }
                                    fileNames.append("file");
                                    fileNames.append(explodedFile);
                                    properties.setProperty("file" + explodedFile + ".name", fileMeta.getName());
                                    properties.setProperty("file" + explodedFile + ".externalAttributes", String.valueOf(fileMeta.getExternalAttributes()));
                                    properties.setProperty("file" + explodedFile + ".time", String.valueOf(fileMeta.getTime()));
                                    properties.setProperty("file" + explodedFile + ".position", String.valueOf(fileMeta.getPosition()));
                                    assemblyFiles.add(new AssemblyFile(position, fileMeta.getPosition(), "META-INF/exploded-assembly-files/file" + explodedFile));
                                }

                                found = true;
                                depNum++;
                                String property = properties.getProperty("dependencies");
                                if (property == null) {
                                    properties.setProperty("dependencies", "dep" + depNum);
                                } else {
                                    properties.setProperty("dependencies", property + ",dep" + depNum);
                                }
                                // following data are mandatory, help to
                                // reproduce
                                // the archive content as it was
                                properties.setProperty("dep" + depNum + ".position", String.valueOf(position));
                                properties.setProperty("dep" + depNum + ".name", nextEntry.getName());
                                properties.setProperty("dep" + depNum + ".externalAttributes", String.valueOf(nextEntry.getExternalAttributes()));
                                properties.setProperty("dep" + depNum + ".time", String.valueOf(nextEntry.getTime()));
                                properties.setProperty("dep" + depNum + ".url", repackagedJar.getUrl());
                                properties.setProperty("dep" + depNum + ".repackage.positions", repackagedJar.getPositions());
                                properties.setProperty("dep" + depNum + ".repackage.times", repackagedJar.getTimes());
                                properties.setProperty("dep" + depNum + ".repackage.externalAttributesList", repackagedJar.getExternalAttributesList());
                                properties.setProperty("dep" + depNum + ".repackage.files", fileNames.toString());
                                StringBuilder excludedURLsSB = new StringBuilder();
                                boolean firstExcludedURL = true;
                                List<String> excludedURLs = repackagedJar.getExcludedURLs();
                                if (excludedURLs != null && excludedURLs.size() != 0) {
                                    for (String excludedURL : excludedURLs) {
                                        int indexOf = urlList.indexOf(excludedURL) + 1;
                                        if (indexOf == 0) {
                                            urlList.add(excludedURL);
                                            indexOf = urlList.size();
                                            properties.setProperty("url" + indexOf, excludedURL);
                                        }
                                        if (firstExcludedURL) {
                                            firstExcludedURL = false;
                                        } else {
                                            excludedURLsSB.append(",");
                                        }
                                        excludedURLsSB.append("url");
                                        excludedURLsSB.append(indexOf);
                                    }
                                    properties.setProperty("dep" + depNum + ".repackage.excludedURLs", excludedURLsSB.toString());
                                }

                                if (nextEntry.getMethod() == ZipEntry.STORED) {
                                    // we can try to recreate the skinny war /
                                    // ejb

                                    File tmpFile = new File("tmp.data");
                                    try {
                                        try (InputStream is = new SkipAndLimitFilterInputStream(new FileInputStream(jarWithDependencies), nextEntry.getDataOffset(),
                                                nextEntry.getCompressedSize())) {
                                            try (FileOutputStream output = new FileOutputStream(tmpFile)) {
                                                IOUtils.copy(is, output);
                                            }
                                        }
                                        GeneratedZipFileMeta gfm = new GeneratedZipFileMeta(nextEntry, sha512,
                                                subFile(subMetas, tmpFile, repackagedJar, localRepository, compressedStore));
                                        if (!IOUtils.contentEquals(gfm.getInputStream(0, -1), new FileInputStream(tmpFile))) {
                                            try (FileOutputStream output = new FileOutputStream(tmpFile)) {
                                                IOUtils.copy(gfm.getInputStream(0, -1), output);
                                            }
                                            throw new IOException("GeneratedZipFileMeta");
                                        }
                                        fileMetas.add(gfm);
                                    } finally {
                                        tmpFile.delete();
                                    }
                                } else {
                                    // if the skinny war is not kept as STORED
                                    // we will have to keep this skinny war in
                                    // CompressedStoreFileMeta which is a lost
                                    // of space
                                    // maven-ear-plugin should compress the
                                    // skinny war with best compression (9) and
                                    // add it to the ear as STORED

                                    String compressedSha512;
                                    try (InputStream is = new SkipAndLimitFilterInputStream(new FileInputStream(jarWithDependencies), nextEntry.getDataOffset(),
                                            nextEntry.getCompressedSize())) {
                                        compressedSha512 = compressedStore.store(sha512, nextEntry.getSize(), nextEntry.getCrc(), nextEntry.getCompressedSize(), is);
                                    }
                                    // copy compressed entry in a store so it
                                    // can be
                                    // reused when same compression is used
                                    // (future
                                    // space gain)
                                    fileMetas.add(new CompressedStoreFileMeta(nextEntry, sha512, compressedSha512, compressedStore));
                                }

                            }
                        }
                    }
                    if (!found) {
                        // not a dependency, copy as it is (no space gain)
                        zos.addRawArchiveEntry(nextEntry,
                                new SkipAndLimitFilterInputStream(new FileInputStream(jarWithDependencies), nextEntry.getDataOffset(), nextEntry.getCompressedSize()));
                        zos.flush();
                        fileMetas.add(new ExplodedJarFileMeta(nextEntry, sha512, explodedJarFile, explodedJarFile.length() - nextEntry.getCompressedSize()));
                    }

                    nextEntry = zis.getNextZipEntry();
                }
            }

            // add our property file (space lost !!!)
            File explodedAssemblyFile = new File("exploded-assembly.properties");
            properties.store(new FileOutputStream(explodedAssemblyFile), null);
            zos.putArchiveEntry(new ZipArchiveEntry(explodedAssemblyFile, "META-INF/exploded-assembly.properties"));
            IOUtils.copy(new FileInputStream(explodedAssemblyFile), zos);
            zos.closeArchiveEntry();
            explodedAssemblyFile.delete();

            for (AssemblyFile assemblyFile : assemblyFiles) {
                zos.putArchiveEntry(new ZipArchiveEntry(assemblyFile.name));
                position = 0;
                try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new FileInputStream(jarWithDependencies))) {
                    ZipArchiveEntry nextEntry = zis.getNextZipEntry();
                    while (nextEntry != null) {
                        position++;
                        if (position == assemblyFile.position) {
                            int subPosition = 0;
                            ZipArchiveInputStream subZis = new ZipArchiveInputStream(zis);
                            ZipArchiveEntry nextSubEntry = subZis.getNextZipEntry();
                            while (nextSubEntry != null) {
                                subPosition++;
                                if (subPosition == assemblyFile.subPosition) {
                                    IOUtils.copy(subZis, zos);
                                    break;
                                }
                                nextSubEntry = subZis.getNextZipEntry();
                            }
                            break;
                        }
                        nextEntry = zis.getNextZipEntry();
                    }
                }

                zos.closeArchiveEntry();
            }
        }
        LOGGER.info("exploded-assembly.zip created");

        {
            // an application which downloads exploded-assembly.zip is able to
            // create reconstructedFile (see below)
            // it will not match the sha512 of the zip but the content after
            // unziping will be the same

            LOGGER.info("Creating assembly-reconstructed.zip (could be used by tomcat-vestige or tomee-vestige)");
            File reconstructedFile = new File("assembly-reconstructed.zip");
            ContructFromExplodedAssembly.construct(explodedJarFile, reconstructedFile);
            LOGGER.info("assembly-reconstructed.zip created");
            try {
                CompareContent.compare(reconstructedFile, jarWithDependencies);
                LOGGER.info("Reconstructed file has same content than original one");
            } catch (IOException e) {
                LOGGER.error("Reconstructed file has different content than original one", e);
            }

        }

        {
            // the repository need to be able to recreate exactly the same jar
            // so that if there is checksum (sha1, asc) attached it will be
            // validated
            // if the code fails to recreate the jar then simply keep the jar

            // FileMeta data should be in DB and reconstructed
            File recreatedFile = new File("assembly-recreated.zip");
            LOGGER.info("Creating assembly-recreated.zip (identical file, should be used be repository manager [nexus, artifactory ...])");
            boolean zip64 = false;
            long size64 = DynamicZip.calculateZipMeta(fileMetas, true).getSize();
            if (jarWithDependencies.length() == size64) {
                zip64 = true;
            } else {
                long size32 = DynamicZip.calculateZipMeta(fileMetas, false).getSize();
                if (jarWithDependencies.length() != size32) {
                    throw new IOException("Unable to match size expected:\n" + jarWithDependencies.length() + "\n32bits:\n" + size32 + " \n64bits:\n" + size64);
                }
            }

            try (FileOutputStream output = new FileOutputStream(recreatedFile)) {
                IOUtils.copy(DynamicZip.getInputStream(fileMetas, 0, -1, zip64), output);
            }
            LOGGER.info("assembly-recreated.zip created");
            // verify
            if (FileUtils.contentEquals(recreatedFile, jarWithDependencies)) {
                LOGGER.info("Recreated file is identical to the original one, we can delete the original one");
                LOGGER.info("mvn install:install-file -Dfile=exploded-assembly.zip -DgroupId=" + groupId + " -DartifactId=" + artifactId + " -Dversion=" + version + " -Dpackaging="
                        + extension + " -Dclassifier=exploded-assembly");
            } else {
                LOGGER.error(
                        "Recreated file is different from the original one, we cannot delete the original one. Need to check why they are different, maybe add new DynamicZip options.");
            }
        }

    }

}
