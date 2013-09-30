package com.chaschev;

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
import com.google.common.base.Optional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.DefaultMaven;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Mojo(name = "install", requiresProject = false, threadSafe = true)
public class InstallMojo extends AbstractExecMojo2 {
    public static final class MatchingPath implements Comparable<MatchingPath>{
        boolean isJDK;

        String path;

        public MatchingPath(boolean JDK, String path) {
            isJDK = JDK;
            this.path = path;
        }

        @Override
        public int compareTo(MatchingPath o) {
            if(isJDK && !o.isJDK) return 1;
            if(!isJDK && o.isJDK) return -1;

            return path.compareTo(o.path);
        }
    }

    @Parameter(property = "installTo")
    private String installTo;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            initialize();

            Artifact artifact = resolveArtifact(artifactName);

            if(className != null){
                List<ArtifactResult> artifactResults = getDependencies(artifact);
                new ExecObject2(getLog(),
                    artifact, artifactResults, className,
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

            for (Object[] entry : entries) {
                String shortCut = (String) entry[0];
                String className = ((Class)entry[1]).getName();


                File file;
                if(SystemUtils.IS_OS_WINDOWS){

                    FileUtils.writeStringToFile(
                        file = new File(installToDir, shortCut + ".bat"),
                        String.format("@mvn -q installation:exec -Dartifact=%s:%s -Dversion=%s -Dclass=%s -Dargs=\"%s\"",
                            artifact.getGroupId(),
                            artifact.getArtifactId(),
                            artifact.getBaseVersion(),
                            className,
                            "%*")
                    );
                }else{
                    FileUtils.writeStringToFile(
                        file = new File(installToDir, shortCut + ".sh"),
                        String.format("mvn -q installation:exec -Dartifact=%s:%s -Dversion=%s -Dclass=%s -Dargs=\"%s\"",
                            artifact.getGroupId(),
                            artifact.getArtifactId(),
                            artifact.getBaseVersion(),
                            className,
                            "$*")
                    );
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

    private String findPath() throws MojoFailureException {
        String path = Optional.fromNullable(System.getenv("path")).or(System.getenv("PATH"));

        String[] pathEntries = path == null ? new String[0] : path.split(File.pathSeparator);

        String javaHomeAbsPath = SystemUtils.getJavaHome().getParentFile().getAbsolutePath();

        File mavenHome = new File(DefaultMaven.class.getProtectionDomain().getCodeSource().getLocation().getFile()).getParentFile().getParentFile();
        String mavenHomeAbsPath = mavenHome.getAbsolutePath();

        List<MatchingPath> matchingPaths = new ArrayList<MatchingPath>();

        for (String pathEntry : pathEntries) {
            File entryFile = new File(pathEntry);
            String absPath = entryFile.getAbsolutePath();

            boolean writable = isWritable(entryFile);

            if(absPath.startsWith(javaHomeAbsPath)){
                if(!writable){
                    getLog().warn(absPath + " is not writable");
                }else{
                    matchingPaths.add(new MatchingPath(true, absPath));
                }
            } else
            if(absPath.startsWith(mavenHomeAbsPath)){
                if(!writable){
                    getLog().warn(absPath + " is not writable");
                }else{
                    matchingPaths.add(new MatchingPath(false, absPath));
                }
            }
        }

        Collections.sort(matchingPaths);

        if(matchingPaths.isEmpty()){
            throw new MojoFailureException("could not find a bin folder to write to");
        }

        return matchingPaths.get(0).path;
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
