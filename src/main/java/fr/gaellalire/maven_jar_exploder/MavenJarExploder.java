package fr.gaellalire.maven_jar_exploder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
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

import fr.gaellalire.maven_jar_exploder.DynamicZip.FileMeta;
import fr.gaellalire.maven_jar_exploder.utils.CompareContent;
import fr.gaellalire.maven_jar_exploder.utils.ContructFromExplodedAssembly;

public class MavenJarExploder {

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

    public static void main(String[] args) throws Exception {
        String groupId = args[0];
        String artifactId = args[1];
        String version = args[2];
        String extension = args[3];

        boolean repackageExpected = "ear".equals(extension);
        repackageExpected = false;
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
        Map<String, List<MetaAndSha512>> subMetaBySha512 = new HashMap<String, List<MetaAndSha512>>();

        // calculate sha512 in dependencies
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
            try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new DigestInputStream(new FileInputStream(dependency), mainDigest))) {
                ZipArchiveEntry nextEntry = zis.getNextZipEntry();
                List<MetaAndSha512> subMetas = new ArrayList<MetaAndSha512>();
                while (nextEntry != null) {
                    position++;
                    if (nextEntry.isDirectory() || nextEntry.getSize() == 0) {
                        nextEntry = zis.getNextZipEntry();
                        continue;
                    }

                    if (repackageExpected) {
                        MessageDigest subDigest = MessageDigest.getInstance("SHA-512");
                        DigestInputStream digestInputStream = new DigestInputStream(zis, subDigest);
                        while (digestInputStream.read() != -1)
                            ;
                        MetaAndSha512 metaAndSha512 = new MetaAndSha512(toHexString(subDigest.digest()), position, nextEntry.getName(), nextEntry.getSize(), nextEntry.getCrc(),
                                nextEntry.getTime(), nextEntry.getCompressedSize(), url);

                        subMetas.add(metaAndSha512);
                        subMetaBySha512.put(metaAndSha512.getSha512(), subMetas);
                    } else {
                        while (zis.read() != -1)
                            ;

                    }

                    nextEntry = zis.getNextZipEntry();
                }

            }
            MetaAndSha512 metaAndSha512 = new MetaAndSha512(toHexString(mainDigest.digest()), -1, dependency.getName(), dependency.length(), -1, dependency.lastModified(), -1,
                    url);
            metaBySha512.put(metaAndSha512.getSha512(), metaAndSha512);
        }

        // compare with sha512 in assembly and create properties and fileMetas
        Properties properties = new Properties();
        List<FileMeta> fileMetas = new ArrayList<FileMeta>();
        CompressedStore compressedStore = new CompressedStore();
        // explodedJarFile should be attach to the original artifact with
        // exploded-assembly classifier and keep same extension than original
        // artifact
        File explodedJarFile = new File("exploded-assembly.zip");
        try (ZipArchiveOutputStream zos = new ZipArchiveOutputStream(new FileOutputStream(explodedJarFile))) {
            int position = 0;
            int depNum = 0;
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
                                @SuppressWarnings("unused") // will be used
                                                            // later
                                MetaAndSha512 metaAndSha512 = new MetaAndSha512(toHexString(subDigest.digest()), subPosition, subNextEntry.getName(), subNextEntry.getSize(),
                                        subNextEntry.getCrc(), subNextEntry.getTime(), subNextEntry.getCompressedSize(), null);
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
                                fileMetas.add(new MavenRepositoryFileMeta(nextEntry, sha512, localRepository));
                            } else {
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
        }

        {
            // an application which downloads exploded-assembly.zip is able to
            // create reconstructedFile (see below)
            // it will not match the sha512 of the zip but the content after
            // unziping will be the same
            
            File reconstructedFile = new File("assembly-reconstructed.zip");
            ContructFromExplodedAssembly.construct(explodedJarFile, reconstructedFile);
            if (CompareContent.compare(reconstructedFile, jarWithDependencies)) {
                System.out.println("Reconstructed file has same content than original one");
            } else {
                System.out.println("Reconstructed file has different content than original one");
            }

        }

        {
            // the repository need to be able to recreate exactly the same jar so that if there is checksum (sha1, asc) attached it will be validated
            // if the code fails to recreate the jar then simply keep the jar
            
            // FileMeta data should be in DB and reconstructed
            File recreatedFile = new File("assembly-recreated.zip");
            IOUtils.copy(DynamicZip.getInputStream(fileMetas, 0, -1, false), new FileOutputStream(recreatedFile));
            // verify
            if (FileUtils.contentEquals(recreatedFile, jarWithDependencies)) {
                System.out.println("Recreated file has same content than original one, we can delete the original one");
            } else {
                System.out.println(
                        "Recreated file has different content than original one, we cannot delete the original one. Need to check why they are different, maybe add new DynamicZip options.");
            }
        }

    }

}
