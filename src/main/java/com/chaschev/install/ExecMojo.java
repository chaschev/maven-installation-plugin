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

import com.google.common.base.Preconditions;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyResult;

import java.util.List;

@Mojo(name = "exec", requiresProject = false, threadSafe = true)
public class ExecMojo extends AbstractExecMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        Preconditions.checkNotNull(className, "you need to set class name with -Dclass=your.ClassName");

        try {
            initialize();

            Artifact artifact = new DefaultArtifact(artifactName);

            DependencyResult dependencyResult = resolveArtifact(artifact);



            List<ArtifactResult> artifacts = dependencyResult.getArtifactResults();

//            List<ArtifactResult> artifactResults = getDependencies(artifact);

            new ExecObject(getLog(),
                artifact, artifacts, className,
                parseArgs(),
                systemProperties
            ).execute();
        } catch (Exception e) {
            if(e instanceof RuntimeException){
                throw (RuntimeException)e;
            }else{
                getLog().error(e.toString(), e);
                throw new MojoExecutionException(e.toString());
            }
        }
    }

}
