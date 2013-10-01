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

import com.chaschev.install.ConsoleRepositoryListener;
import com.chaschev.install.ConsoleTransferListener;
import com.chaschev.install.guice.GuiceRepositorySystemFactory;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;

/**
 * A helper to boot the repository system and a repository system session.
 */
public class Booter
{

    public static RepositorySystem newRepositorySystem()
    {
//        return ManualRepositorySystemFactory.newRepositorySystem();
        return GuiceRepositorySystemFactory.newRepositorySystem();
//        return PlexusRepositorySystemFactory.newRepositorySystem();
//        return new DefaultRepositorySystem();
    }

    public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system){
        return newRepositorySystemSession(system, new LocalRepository("target/repo4"));
    }

    public static DefaultRepositorySystemSession newRepositorySystemSession(RepositorySystem system, LocalRepository localRepo)
    {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        session.setLocalRepositoryManager( system.newLocalRepositoryManager( session, localRepo) );

        session.setTransferListener( new ConsoleTransferListener() );
        session.setRepositoryListener( new ConsoleRepositoryListener() );

        // uncomment to generate dirty trees
        // session.setDependencyGraphTransformer( null );

        return session;
    }

    public static RemoteRepository newCentralRepository()
    {
        return new RemoteRepository.Builder( "central", "default", "http://repo1.maven.org/maven2/" ).build();
    }

    public static RemoteRepository newSonatypeRepository()
    {
        return new RemoteRepository.Builder( "sonatype-snapshots", "default", "https://oss.sonatype.org/content/repositories/snapshots/" ).build();
    }

}
