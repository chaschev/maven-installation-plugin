package com.chaschev.install;


/*******************************************************************************
 * Copyright (c) 2010, 2013 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.internal.impl.DefaultRepositorySystem;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

import java.io.File;

/**
 * A helper to boot the repository system and a repository system session.
 */
public class Booter {

    public static RepositorySystem newRepositorySystem() {
//        return ManualRepositorySystemFactory.newRepositorySystem();

        return new DefaultRepositorySystem();
    }


    public static RemoteRepository newCentralRepository() {
        return new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build();
//        return new RemoteRepository( "central", "default", "http://repo1.maven.org/maven2/" );
    }

    public static RemoteRepository newSonatypeRepository() {
        return new RemoteRepository.Builder("sonatype-snapshots", "default", "https://oss.sonatype.org/content/repositories/snapshots/").build();
//        return new RemoteRepository("sonatype-snapshots", "default", "https://oss.sonatype.org/content/repositories/snapshots/");
    }

    public static DefaultRepositorySystemSession newSession(RepositorySystem system, File repositoryDir)
    {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepository localRepo = new LocalRepository(repositoryDir);

        session.setLocalRepositoryManager( system.newLocalRepositoryManager( session, localRepo ) );

//        session.setTransferListener( new ConsoleTransferListener() );
//        session.setRepositoryListener( new ConsoleRepositoryListener() );

        // uncomment to generate dirty trees
        // session.setDependencyGraphTransformer( null );

        return session;
    }
}
