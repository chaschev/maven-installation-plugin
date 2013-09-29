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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;

/**
 * Goal which touches a timestamp file.
 *
 * @goal install
 * @phase install
 */
public class InstallMojo extends AbstractMojo {
    /**
     * Plugin to execute.
     *
     * @parameter
     */
    private String pluginString;

    /**
     * Plugin goal to execute.
     *
     * @parameter
     */
    private String goal;

    /**
     * Plugin configuration to use in the execution.
     *
     * @parameter
     */
    private XmlPlexusConfiguration configuration;

    /**
     * The project currently being build.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject mavenProject;

    /**
     * The current Maven session.
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    private MavenSession mavenSession;

    /**
     * The Maven BuildPluginManager component.
     *
     * @component
     * @required
     */
    private BuildPluginManager pluginManager;


    //Plan:


    //link: com.chaschev:cap4:1.0-SNAPSHOT:com.chaschev.cap4.main.Cap4j
    //execute com.chaschev:cap4:1.0-SNAPSHOT:com.chaschev.cap4.main.Cap4j

    public void execute() throws MojoExecutionException {
    }
}
