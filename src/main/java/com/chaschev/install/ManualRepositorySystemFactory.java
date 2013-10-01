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

/**
 * A factory for repository system instances that employs Aether's built-in service locator infrastructure to wire up
 * the system's components.
 */
public class ManualRepositorySystemFactory {

    public static RepositorySystem newRepositorySystem() {
        /*
         * Aether's components implement org.sonatype.aether.spi.locator.Service to ease manual wiring and using the
         * prepopulated DefaultServiceLocator, we only need to register the repository connector and transporter
         * factories.
         */
//        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
//
//        locator.addService(RepositoryConnectorFactory.class, WagonRepositoryConnectorFactory.class);
//        locator.addService(WagonProvider.class, PlexusWagonProvider.class);
//        locator.addService(PlexusContainer.class, DefaultPlexusContainer.class);
////        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
////        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
////        locator.addService(TransporterProvider.class, DefaultTransporterProvider.class);
////        locator.addService(RepositoryLayoutProvider.class, DefaultRepositoryLayoutProvider.class);
////        locator.addService(RepositoryLayoutFactory.class, Maven2RepositoryLayoutFactory.class);
//
//        locator.setErrorHandler(new DefaultServiceLocator.ErrorHandler() {
//            @Override
//            public void serviceCreationFailed(Class<?> type, Class<?> impl, Throwable exception) {
//                exception.printStackTrace();
//            }
//        });
//
//        return locator.getService(RepositorySystem.class);


        return null;
    }

}
