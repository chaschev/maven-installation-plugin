package com.chaschev.install;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.chaschev.chutils.util.OpenBean2;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.DefaultMaven;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.util.artifact.DefaultArtifact;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.MessageFormat;
import java.util.*;

import static com.google.common.collect.Lists.newArrayList;
import static com.google.common.collect.Lists.transform;
import static org.apache.commons.lang3.SystemUtils.IS_OS_UNIX;

@Mojo(name = "install", requiresProject = false, threadSafe = true)
public class InstallMojo extends AbstractExecMojo2 {

    public static final Function<String,File> PATH_TO_FILE = new Function<String, File>() {
        public File apply(String s) {
            return new File(s);
        }
    };

    public static final class MatchingPath implements Comparable<MatchingPath>{

        int type;

        String path;

        public MatchingPath(int type, String path) {
            this.type = type;
            this.path = path;
        }

        @Override
        public int compareTo(MatchingPath o) {
            return type - o.type;
        }
    }

    @Parameter(property = "installTo")
    private String installTo;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
//            FindAvailableVersions.main(null);
            initialize();

            Artifact artifact = new DefaultArtifact(artifactName);
            ArtifactResults2 artifactResults = resolveArtifact(artifact);

            artifact = artifactResults.artifact;

            List<ArtifactResult> dependencies = artifactResults.getDependencies();

            if(className != null){
                new ExecObject2(getLog(),
                    artifact, dependencies, className,
                    parseArgs(),
                    systemProperties
                ).execute();
            }

            Class<?> installation = new URLClassLoader(new URL[]{artifact.getFile().toURI().toURL()}).loadClass("Installation");

            List<Object[]> entries = (List<Object[]>) OpenBean2.getStaticFieldValue(installation, "shortcuts");

            if(installTo == null){
               installTo = findPath();
            }

            File installToDir = new File(installTo);

            File classPathFile = writeClasspath(artifact, dependencies, installToDir);

            for (Object[] entry : entries) {
                String shortCut = (String) entry[0];
                String className = ((Class)entry[1]).getName();

                File file;
                if(SystemUtils.IS_OS_WINDOWS){
                    FileUtils.writeStringToFile(
                        file = new File(installToDir, shortCut + ".bat"),
                        "@" + createLaunchString(className, classPathFile) + " %*");
                }else{
                    file = new File(installToDir, shortCut);
                    FileUtils.writeStringToFile(
                        file,
                        createLaunchString(className, classPathFile) + " $*");
                    try {
                        file.setExecutable(true, false);
                    } catch (Exception e) {
                        getLog().warn("could not make '" + file.getAbsolutePath() + "' executable: " + e.toString());
                    }
                }

                getLog().info("created shortcut: " + shortCut + " -> " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            if(e instanceof RuntimeException){
                throw (RuntimeException)e;
            }else{
                getLog().error(e.toString(), e);
                throw new MojoExecutionException(e.toString());
            }
        }
    }

    private static String createLaunchString(String className, File classPathFile) {
        String jarPath = getJarByClass(Runner.class).getAbsolutePath();

        if(IS_OS_UNIX){
            String installerUserHome = getInstallerHomeDir(jarPath);

            jarPath = jarPath.replace(installerUserHome, "$HOME");
        }

        return MessageFormat.format("{0} -cp \"{1}\" {2} {3} {4} ",
            javaExePath(), jarPath, Runner.class.getName(), classPathFile.getAbsolutePath(), className);
    }

    private static String getInstallerHomeDir(String jarPath) {
        return new File(StringUtils.substringBefore(jarPath, "/com/chaschev")).getParentFile().getParentFile().getAbsolutePath();
    }

    private static File javaExePath() {
        return new File(SystemUtils.getJavaHome(), "bin/" + (IS_OS_UNIX ? "java" : "java.exe"));
    }

    private static File writeClasspath(Artifact artifact, List<ArtifactResult> dependencies, File installToDir) throws IOException {
        final String jarPath = getJarByClass(Runner.class).getAbsolutePath();

        final String installerUserHome = getInstallerHomeDir(jarPath);

        ArrayList<File> classPathFiles = newArrayList(transform(dependencies, new Function<ArtifactResult, File>() {
            @Override
            public File apply(ArtifactResult artifactResult) {
                return artifactResult.getArtifact().getFile();
            }
        }));

        classPathFiles.add(getJarByClass(Runner.class));

        File file = new File(installToDir, artifact.getGroupId() + "." + artifact.getArtifactId());
        FileUtils.writeLines(file, transform(classPathFiles, new Function<File, String>() {
            @Override
            public String apply(File file) {
                if(IS_OS_UNIX){
                    return file.getAbsolutePath().replace(installerUserHome, "$HOME");
                }else{
                    return file.getAbsolutePath();
                }
            }
        }));

        return file;
    }

    private String findPath() throws MojoFailureException {
        String path = Optional.fromNullable(System.getenv("path")).or(System.getenv("PATH"));

        ArrayList<String> pathEntries = newArrayList(path == null ? new String[0] : path.split(File.pathSeparator));

        String javaHomeAbsPath = SystemUtils.getJavaHome().getParentFile().getAbsolutePath();

        String mavenHomeAbsPath = getMavenHomeByClass(DefaultMaven.class).getAbsolutePath();

        List<MatchingPath> matchingPaths = new ArrayList<MatchingPath>();

        final LinkedHashSet<File> knownBinFolders = Sets.newLinkedHashSet(
            Lists.transform(Arrays.asList("/usr/local/bin", "/usr/local/sbin"), PATH_TO_FILE)
        );

        for (String pathEntry : pathEntries) {
            File entryFile = new File(pathEntry);
            String absPath = entryFile.getAbsolutePath();

            boolean writable = isWritable(entryFile);

            getLog().debug("testing " + entryFile.getAbsolutePath() + ": " + (writable ? "writable": "not writable"));

            if(absPath.startsWith(javaHomeAbsPath)){
                addMatching(matchingPaths, absPath, writable, 1);
            } else
            if(absPath.startsWith(mavenHomeAbsPath)){
                addMatching(matchingPaths, absPath, writable, 2);
            }
        }

        if(IS_OS_UNIX && matchingPaths.isEmpty()){
            getLog().warn("didn't find maven/jdk writable roots available on path, trying common unix paths: " + knownBinFolders);

            final LinkedHashSet<File> pathEntriesSet = Sets.newLinkedHashSet(
                Lists.transform(pathEntries, PATH_TO_FILE)
            );

            for (File knownBinFolder : knownBinFolders) {
                if(pathEntriesSet.contains(knownBinFolder)){
                    addMatching(matchingPaths, knownBinFolder.getAbsolutePath(), isWritable(knownBinFolder), 3);
                }
            }
        }

        Collections.sort(matchingPaths);

        if(matchingPaths.isEmpty()){
            throw new MojoFailureException("Could not find a bin folder to write to. Tried: \n" + Joiner.on("\n")
                .join(mavenHomeAbsPath, javaHomeAbsPath) + "\n" +
                (IS_OS_UNIX ? knownBinFolders + "\n" : "") +
                    " but they don't appear on the path or are not writable. You may try running as administrator or specifying -DinstallTo=your-bin-dir-path parameter");
        }

        return matchingPaths.get(0).path;
    }

    private void addMatching(List<MatchingPath> matchingPaths, String matchingPath, boolean writable, int type) {
        if(writable){
            getLog().info(matchingPath + " matches");

            matchingPaths.add(new MatchingPath(type, matchingPath));
        }else{
            getLog().warn(matchingPath + " matches, but is not writable");

        }
    }

    private static File getMavenHomeByClass(Class<?> aClass) {
        return getJarByClass(aClass).getParentFile().getParentFile();
    }

    public static File getJarByClass(Class<?> aClass) {
        return new File(aClass.getProtectionDomain().getCodeSource().getLocation().getFile());
    }

    private static boolean isWritable(File dir) {
        try {
            File tempFile = File.createTempFile("testWrite", "txt", dir);
            if(tempFile.exists()) {
                tempFile.delete();
                return true;
            }
        } catch (IOException e) {
            return false;
        }

        return dir.canWrite();
    }

}
