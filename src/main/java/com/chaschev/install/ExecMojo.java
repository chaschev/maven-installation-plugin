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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.repository.ArtifactRepositoryPolicy;
import org.apache.maven.artifact.repository.layout.ArtifactRepositoryLayout;
import org.apache.maven.artifact.resolver.AbstractArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mojo( name = "execLegacy", requiresProject = false, threadSafe = true )
public class ExecMojo extends AbstractMojo {
    /**
     * Artifact to execute. I.e. com.chaschev.cap4j:1.0:your-class
     */
    @Parameter(property = "artifact", required = true)
    private String artifactString;

    /**
     * Repositories in the format id::[layout]::url or just url, separated by comma.
     * ie. central::default::http://repo1.maven.apache.org/maven2,myrepo::::http://repo.acme.com,http://repo.acme2.com
     */
    @Parameter(property = "remoteRepositories")
    private String remoteRepositories;

    /**
     * Arguments for the executed program
     */
    @Parameter(property = "args")
    private String commandlineArgs;

    @Parameter
    private Property[] systemProperties;

    /**
     *
     */
    @Component
    private org.apache.maven.artifact.factory.ArtifactFactory artifactFactory;

    /**
     *
     */
    @Component
    private ArtifactResolver artifactResolver;

    /**
     *
     */
    @Component
    private ArtifactRepositoryFactory artifactRepositoryFactory;

    /**
     * Map that contains the layouts.
     */
    @Component( role = ArtifactRepositoryLayout.class )
    private java.util.Map<String, ArtifactRepositoryLayout> repositoryLayouts;

    /**
     *
     */
    @Component
    private org.apache.maven.artifact.metadata.ArtifactMetadataSource source;

    /**
     *
     */
    @Parameter( defaultValue = "${localRepository}", readonly = true )
    private org.apache.maven.artifact.repository.ArtifactRepository localRepository;

    @Component
    private PluginManager pluginManager;

    //Plan:


    //link: com.chaschev:cap4:1.0-SNAPSHOT:com.chaschev.cap4.main.Cap4j
    //execute com.chaschev:cap4:1.0-SNAPSHOT:com.chaschev.cap4.main.Cap4j

    public void execute() throws MojoExecutionException, MojoFailureException {
//        System.out.println(this);

        if(artifactString != null){
            getLog().info("executing artifact " + artifactString);

            String[] strings = artifactString.split(":");

//            System.out.println(Arrays.asList(strings));

            boolean hasClassifier = strings.length == 5;

            if(strings.length != 4 && strings.length != 5){
                throw new MojoExecutionException("error: expected format is groupId:artifactId:version:[classifier:]className");
            }

//            System.out.println(11);

            String groupId = strings[0];
            String artifactId = strings[1];
            String version = strings[2];
            String className;
            String classifier;

            if(hasClassifier){
//                System.out.println(12);
                classifier = strings[3];
                className = strings[4];
            }else{
//                System.out.println(13);
                classifier = null;
                className = strings[3];
            }

            String remoteRepositories =
                this.remoteRepositories != null ? this.remoteRepositories :
                    "central::default::http://repo1.maven.apache.org/maven2," +
                        "oss-snapshots::default::https://oss.sonatype.org/content/repositories/snapshots";

            List<ArtifactRepository> repositoryList = new RepositoryParser(artifactRepositoryFactory, repositoryLayouts)
                .parseRepositories(remoteRepositories);

            Artifact artifact = newArtifact(groupId, artifactId, version, classifier, artifactFactory);
            ArtifactResolutionResult resolutionResult = downloadArtifact(repositoryList, artifact);

            new ExecObject(getLog(),
                artifact, resolutionResult, className,
                parseArgs(),
                systemProperties
            ).execute();
        }
    }

    private String[] parseArgs() throws MojoExecutionException {
        if(commandlineArgs == null) return null;

        try {
            return CommandLineUtils.translateCommandline(commandlineArgs);
        } catch (Exception e) {
            throw new MojoExecutionException(e.toString());
        }
    }

    public ArtifactResolutionResult downloadArtifact(List<ArtifactRepository> repositoryList, Artifact toDownload) throws MojoExecutionException {
        try {

            Artifact dummyOriginatingArtifact =
                artifactFactory.createBuildArtifact( "org.apache.maven.plugins", "maven-downloader-plugin", "1.0", "jar" );

            System.out.println(44);

            getLog().info( "Resolving " + toDownload + " with transitive dependencies" );

            return artifactResolver.resolveTransitively(
                Collections.singleton(toDownload), dummyOriginatingArtifact,
                repositoryList, localRepository, source);
        }
        catch ( AbstractArtifactResolutionException e )
        {
            throw new MojoExecutionException( "Couldn't resolve artifact: " + e.getMessage(), e );
        }
    }

    public static Artifact newArtifact(String groupId, String artifactId, String version, String classifier, ArtifactFactory factory) {
        return classifier == null
                    ? factory.createBuildArtifact(groupId, artifactId, version, "jar")
                    : factory.createArtifactWithClassifier(groupId, artifactId, version, "jar", classifier);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("ExecMojo{");
        sb.append(", artifactString='").append(artifactString).append('\'');
        sb.append(", remoteRepositories='").append(remoteRepositories).append('\'');
        sb.append(", artifactFactory=").append(artifactFactory);
        sb.append(", artifactResolver=").append(artifactResolver);
        sb.append(", artifactRepositoryFactory=").append(artifactRepositoryFactory);
        sb.append(", repositoryLayouts=").append(repositoryLayouts);
        sb.append(", source=").append(source);
        sb.append(", localRepository=").append(localRepository);
        sb.append(", pluginManager=").append(pluginManager);
        sb.append('}');
        return sb.toString();
    }

    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile( "(.+)::(.*)::(.+)" );

    public static class RepositoryParser{
        ArtifactRepositoryFactory artifactRepositoryFactory;
        java.util.Map<String, ArtifactRepositoryLayout> repositoryLayouts;

        static final ArtifactRepositoryPolicy ALWAYS =
            new ArtifactRepositoryPolicy( true, ArtifactRepositoryPolicy.UPDATE_POLICY_ALWAYS,
                ArtifactRepositoryPolicy.CHECKSUM_POLICY_WARN );


        public RepositoryParser(ArtifactRepositoryFactory artifactRepositoryFactory, Map<String, ArtifactRepositoryLayout> repositoryLayouts) {
            this.artifactRepositoryFactory = artifactRepositoryFactory;
            this.repositoryLayouts = repositoryLayouts;
        }

        public List<ArtifactRepository> parseRepositories(String repos){
            try {
                List<ArtifactRepository> repoList = new ArrayList<ArtifactRepository>();

                for (String repo : repos.split(",")) {
                    repoList.add(parseRepository(repo, ALWAYS));
                }

                return repoList;
            } catch (MojoFailureException e) {
                throw new RuntimeException(e);
            }
        }
        public ArtifactRepository parseRepository( String repo, ArtifactRepositoryPolicy policy )
            throws MojoFailureException
        {
            // if it's a simple url
            String id = null;
            ArtifactRepositoryLayout layout = getLayout( "default" );
            String url = repo;

            // if it's an extended repo URL of the form id::layout::url
            if (repo.contains("::"))
            {
                Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher( repo );
                if ( !matcher.matches() )
                {
                    throw new MojoFailureException( repo, "Invalid syntax for repository: " + repo,
                        "Invalid syntax for repository. Use \"id::layout::url\" or \"URL\"." );
                }

                id = matcher.group( 1 ).trim();
                if ( !StringUtils.isEmpty(matcher.group(2)) )
                {
                    layout = getLayout( matcher.group( 2 ).trim() );
                }
                url = matcher.group( 3 ).trim();
            }
            return artifactRepositoryFactory.createArtifactRepository( id, url, layout, policy, policy );
        }

        private ArtifactRepositoryLayout getLayout( String id )
            throws MojoFailureException
        {
            ArtifactRepositoryLayout layout = repositoryLayouts.get( id );

            if ( layout == null )
            {
                throw new MojoFailureException( id, "Invalid repository layout", "Invalid repository layout: " + id );
            }

            return layout;
        }
    }


}
