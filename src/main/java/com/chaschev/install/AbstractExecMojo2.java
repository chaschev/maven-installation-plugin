package com.chaschev.install;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
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

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
public abstract class AbstractExecMojo2 extends AbstractMojo {
    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile("(.+)::(.*)::(.+)");

    /**
     * Artifact to execute. I.e. com.chaschev:cap4j:1.0 or com.chaschev.
     */
    @Parameter(property = "artifact", required = true)
    protected String artifactName;

    @Parameter(property = "class")
    protected String className;

    @Parameter(property = "version", defaultValue = "LATEST")
    protected String artifactVersion;

    /**
     * Include snapshots releases when version is not set.
     */
    @Parameter(property = "snapshots", defaultValue = "false")
    protected boolean snapshots;

    /**
     * Repositories in the format id::[layout]::url or just url, separated by comma.
     * ie. central::default::http://repo1.maven.apache.org/maven2,myrepo::::http://repo.acme.com,http://repo.acme2.com
     */
    @Parameter(property = "remoteRepositories", defaultValue =
        "http://repo1.maven.apache.org/maven2," +
            "https://oss.sonatype.org/content/repositories/snapshots")
    protected String remoteRepositories;

    /**
     * Arguments for the executed program
     */
    @Parameter(property = "args")
    protected String commandlineArgs;
    @Parameter
    protected Property[] systemProperties;

    @Parameter(property = "forceDownload", defaultValue = "false")
    private boolean forceDownload;

    @Parameter(property = "localRepo")
    protected String localRepo;

    protected List<RemoteRepository> repositories;
    protected RepositorySystem system;
    protected RepositorySystemSession session;

    protected void initialize() throws VersionRangeResolutionException, MojoFailureException {
        File repositoryFile = localRepo == null ? (new File(SystemUtils.getUserHome(),
              ".m2/repository")) : new File(localRepo);

        Preconditions.checkArgument(repositoryFile.exists(), "could not find local repo at: %s", repositoryFile.getAbsolutePath());

        LocalRepository localRepository = new LocalRepository(repositoryFile);

        repositories = new RepositoryParser().parse(remoteRepositories);

        system = Booter.newRepositorySystem();
        session = Booter.newRepositorySystemSession(system, localRepository);

        if ("LATEST".equals(artifactVersion)) {
            Artifact artifact = new DefaultArtifact(artifactName + ":[0,)");

            VersionRangeRequest rangeRequest = new VersionRangeRequest();

            rangeRequest.setArtifact( artifact );
            rangeRequest.setRepositories(repositories);


            VersionRangeResult rangeResult = system.resolveVersionRange(session, rangeRequest);

            List<Version> versions = Lists.reverse(rangeResult.getVersions());

//            Version matchedVersion = rangeRequest.;
            Version matchedVersion = null;

            if(!snapshots) {
                for (int i = 0; i < versions.size(); i++) {
                    Version version = versions.get(i);
                    if (!version.toString().toUpperCase().contains("SNAPSHOT")) {
                        matchedVersion = version;
                        break;
                    }
                }
                if(matchedVersion == null){
                    matchedVersion = rangeResult.getHighestVersion();
                    if(matchedVersion != null){
                        getLog().warn("didn't find non-snapshot version for " + artifactName +
                            ", using snapshot version: " + matchedVersion);
                    }
                }
            }else{
                matchedVersion = rangeResult.getHighestVersion();
            }

            if(matchedVersion == null){
                throw new MojoFailureException("didn't find matching version for " + artifactName);
            }

            artifactVersion = matchedVersion.toString();
        }

        artifactName += ":" + artifactVersion;
    }

    protected Artifact resolveArtifact(String artifactToResolve) throws ArtifactResolutionException {
        getLog().info("resolving artifact " + artifactToResolve);

        Artifact artifact = new DefaultArtifact(artifactToResolve);

        ArtifactRequest artifactRequest = new ArtifactRequest();
        artifactRequest.setArtifact(artifact);

        artifactRequest.setRepositories(repositories);

        ArtifactResult artifactResult = system.resolveArtifact(session, artifactRequest);

        return artifactResult.getArtifact();
    }

    protected List<ArtifactResult> getDependencies(Artifact artifact) throws DependencyResolutionException {
        DependencyFilter dependencyFilter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot( new Dependency( artifact, JavaScopes.COMPILE ) );
        collectRequest.setRepositories(repositories);

        DependencyRequest dependencyRequest = new DependencyRequest( collectRequest, dependencyFilter );

        return system.resolveDependencies(session, dependencyRequest).getArtifactResults();
    }

    protected String[] parseArgs() throws MojoExecutionException {
        if (commandlineArgs == null) return null;

        try {
            return CommandLineUtils.translateCommandline(commandlineArgs);
        } catch (Exception e) {
            throw new MojoExecutionException(e.toString());
        }
    }

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
