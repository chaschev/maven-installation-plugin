package com.chaschev.install;

import com.chaschev.install.jcabi.Aether;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.SystemUtils;
import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.resolution.VersionRangeResult;
import org.sonatype.aether.version.Version;
import org.sonatype.aether.util.artifact.DefaultArtifact;

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

//        Preconditions.checkArgument(repositoryFile.exists(), "could not find local repo at: %s", repositoryFile.getAbsolutePath());

        System.out.println( "------------------------------------------------------------" );
        System.out.println( FindAvailableVersions.class.getSimpleName() );

        Artifact artifact = new DefaultArtifact( "com.chaschev:chutils:[0,)" );

        VersionRangeResult rangeResult = new Aether(Lists.newArrayList(
            Booter.newCentralRepository(), Booter.newSonatypeRepository()
        ), repositoryFile).getVersions(artifact);

        List<Version> versions = rangeResult.getVersions();

        System.out.println( "Available versions " + versions );
    }



}