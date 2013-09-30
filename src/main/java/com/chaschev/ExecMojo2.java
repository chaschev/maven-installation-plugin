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

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.version.Version;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.apache.commons.lang3.StringUtils.substringBeforeLast;

@Mojo(name = "exec", requiresProject = false, threadSafe = true)
public class ExecMojo2 extends AbstractMojo {
    /**
     * Artifact to execute. I.e. com.chaschev.cap4j:1.0:your-class
     */
    @Parameter(property = "artifact", required = true)
    private String artifactString;

    @Parameter(property = "version", defaultValue = "LATEST")
    private String artifactVersion;

    /**
     * Repositories in the format id::[layout]::url or just url, separated by comma.
     * ie. central::default::http://repo1.maven.apache.org/maven2,myrepo::::http://repo.acme.com,http://repo.acme2.com
     */
    @Parameter(property = "remoteRepositories", defaultValue =
        "http://repo1.maven.apache.org/maven2," +
            "https://oss.sonatype.org/content/repositories/snapshots")
    private String remoteRepositories;

    /**
     * Arguments for the executed program
     */
    @Parameter(property = "args")
    private String commandlineArgs;

    @Parameter(property = "forceDownload", defaultValue = "false")
    private boolean forceDownload;

    @Parameter
    private Property[] systemProperties;

    @Parameter(property = "localRepo")
    private String localRepo;

    @Component
    private PluginManager pluginManager;

    //Plan:

    public void execute() throws MojoExecutionException, MojoFailureException {
//        System.out.println(this);

        try {

            File repositoryFile =
                localRepo == null ? (new File(SystemUtils.getUserHome(),
                  ".m2/repository")) : new File(localRepo);

            Preconditions.checkArgument(repositoryFile.exists(), "could not find local repo at: %s", repositoryFile.getAbsolutePath());

            LocalRepository localRepository = new LocalRepository(repositoryFile);

            List<RemoteRepository> repositories = new RepositoryParser().parse(remoteRepositories);

            RepositorySystem system = Booter.newRepositorySystem();
            RepositorySystemSession session = Booter.newRepositorySystemSession(system, localRepository);

            if (artifactString != null) {
                String artifactName = substringBeforeLast(artifactString, ":");
                String className = substringAfterLast(artifactString, ":");

                if ("LATEST".equals(artifactVersion)) {
                    Artifact artifact = new DefaultArtifact(artifactName + ":[0,)");

                    VersionRangeRequest rangeRequest = new VersionRangeRequest();

                    rangeRequest.setArtifact( artifact );
                    rangeRequest.setRepositories(repositories);

                    VersionRangeResult rangeResult = system.resolveVersionRange( session, rangeRequest );

                    Version newestVersion = rangeResult.getHighestVersion();

                    artifactVersion = newestVersion.toString();
                }

                artifactName += ":" + artifactVersion;

                getLog().info("resolving artifact " + artifactName);

                Artifact artifact = new DefaultArtifact(artifactName);

                ArtifactRequest artifactRequest = new ArtifactRequest();
                artifactRequest.setArtifact(artifact);

                artifactRequest.setRepositories(repositories);

                ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);

                DependencyFilter classpathFlter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);

                CollectRequest collectRequest = new CollectRequest();
                collectRequest.setRoot( new Dependency( artifact, JavaScopes.COMPILE ) );
                collectRequest.setRepositories(repositories);

                DependencyRequest dependencyRequest = new DependencyRequest( collectRequest, classpathFlter );

                List<ArtifactResult> artifactResults =
                    system.resolveDependencies( session, dependencyRequest ).getArtifactResults();


                new ExecObject2(getLog(),
                    artifact, artifactResults, className,
                    parseArgs(),
                    systemProperties
                ).execute();
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

    private String[] parseArgs() throws MojoExecutionException {
        if (commandlineArgs == null) return null;

        try {
            return CommandLineUtils.translateCommandline(commandlineArgs);
        } catch (Exception e) {
            throw new MojoExecutionException(e.toString());
        }
    }

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.*)::(.+)");

    public static class RepositoryParser {

        public RepositoryParser() {
        }

        public List<RemoteRepository> parse(String repos) {
            try {
                List<RemoteRepository> repoList = new ArrayList<RemoteRepository>();

                for (String repo : repos.split(",")) {
                    repoList.add(parseRepository(repo));
                }

                return repoList;
            } catch (MojoFailureException e) {
                throw new RuntimeException(e);
            }
        }

        public RemoteRepository parseRepository(String repo)  throws MojoFailureException {
            // if it's a simple url
            String id = null;
            String layout = "default";
            String url = repo;

            // if it's an extended repo URL of the form id::layout::url
            if (repo.contains("::")) {
                Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher(repo);

                if (!matcher.matches()) {
                    throw new MojoFailureException(repo, "Invalid syntax for repository: " + repo,
                        "Invalid syntax for repository. Use \"id::layout::url\" or just \"URL\".");
                }

                id = matcher.group(1).trim();

                if (!StringUtils.isEmpty(matcher.group(2))) {
                    layout = matcher.group(2).trim();
                }

                url = matcher.group(3).trim();
            } else {
                url = repo;
            }

            RemoteRepository.Builder builder = new RemoteRepository.Builder(id, layout, url);

            if(url.contains("snapshot")){
                builder.setSnapshotPolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS,
                    RepositoryPolicy.CHECKSUM_POLICY_WARN));
            }

            return builder.build();
        }
    }
}
