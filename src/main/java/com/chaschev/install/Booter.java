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

import org.sonatype.aether.RepositorySystem;
import org.sonatype.aether.repository.RemoteRepository;

/**
 * A helper to boot the repository system and a repository system session.
 */
public class Booter
{

    public static RepositorySystem newRepositorySystem()
    {
        return ManualRepositorySystemFactory.newRepositorySystem();
    }


    public static RemoteRepository newCentralRepository()
    {
        return new RemoteRepository( "central", "default", "http://repo1.maven.org/maven2/" );
    }

    public static RemoteRepository newSonatypeRepository()
    {
        return new RemoteRepository( "sonatype-snapshots", "default", "https://oss.sonatype.org/content/repositories/snapshots/" );
    }

}
