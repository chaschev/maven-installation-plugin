package com.chaschev.install;

import com.chaschev.install.Booter;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.SystemUtils;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.resolution.VersionRangeRequest;
import org.eclipse.aether.resolution.VersionRangeResult;
import org.eclipse.aether.version.Version;

import java.io.File;
import java.util.List;

public class FindAvailableVersions
{

    public static void main( String[] args )
        throws Exception
    {
        String localRepo = null;
        File repositoryFile = localRepo == null ? (new File(SystemUtils.getUserHome(),
            ".m2/repository")) : new File(localRepo);

        Preconditions.checkArgument(repositoryFile.exists(), "could not find local repo at: %s", repositoryFile.getAbsolutePath());

        LocalRepository localRepository = new LocalRepository(repositoryFile);

        System.out.println( "------------------------------------------------------------" );
        System.out.println( FindAvailableVersions.class.getSimpleName() );

        RepositorySystem system = Booter.newRepositorySystem();

        RepositorySystemSession session = Booter.newRepositorySystemSession( system, new LocalRepository("target/repo3"));
//        RepositorySystemSession session = Booter.newRepositorySystemSession( system, localRepository);

//        Artifact artifact = new DefaultArtifact( "org.eclipse.aether:aether-util:[0,)" );
        Artifact artifact = new DefaultArtifact( "com.chaschev:chutils:[0,)" );

        VersionRangeRequest rangeRequest = new VersionRangeRequest();
        rangeRequest.setArtifact( artifact );

        rangeRequest.addRepository(Booter.newCentralRepository());
        rangeRequest.addRepository( Booter.newSonatypeRepository() );

        VersionRangeResult rangeResult = system.resolveVersionRange( session, rangeRequest );

        List<Version> versions = rangeResult.getVersions();

        System.out.println( "Available versions " + versions );
    }

}