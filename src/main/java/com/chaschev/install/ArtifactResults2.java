package com.chaschev.install;

import org.sonatype.aether.artifact.Artifact;
import org.sonatype.aether.resolution.ArtifactResult;
import org.sonatype.aether.resolution.DependencyResult;

import java.util.List;

public class ArtifactResults2 {
    public final Artifact artifact;
    public final List<ArtifactResult> dependencies;
    public final DependencyResult dependencyResult;

    public ArtifactResults2(Artifact artifact, List<ArtifactResult> dependencies, DependencyResult dependencyResult) {
        this.artifact = artifact;
        this.dependencies = dependencies;
        this.dependencyResult = dependencyResult;
    }


    public List<ArtifactResult> getDependencies() {
        return dependencies;
    }
}
