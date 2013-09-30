package com.chaschev.install;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 * A Plugin for executing external programs.
 *
 * @author Jerome Lacoste <jerome@coffeebreaks.org>
 * @version $Id: ExecMojo.java 14727 2011-09-15 19:58:00Z rfscholte $
 */
public class ExecObject {
    private Log log ;

    /**
     * Defines the scope of the classpath passed to the plugin. Set to compile,test,runtime or system depending on your
     * needs. Since 1.1.2, the default value is 'runtime' instead of 'compile'.
     *
     * @parameter expression="${exec.classpathScope}" default-value="runtime"
     */
    @Parameter(defaultValue = "runtime")
    protected String classpathScope;

    private Artifact artifactToExec;

    ArtifactResolutionResult artifactResolutionResult;


    /**
     * The main class to execute.
     *
     * @parameter expression="${exec.mainClass}"
     * @required
     * @since 1.0
     */
    @Parameter
    private String mainClass;

    /**
     * The class arguments.
     *
     * @parameter expression="${exec.arguments}"
     * @since 1.0
     */
    @Parameter
    private String[] arguments;

    /**
     * A list of system properties to be passed. Note: as the execution is not forked, some system properties
     * required by the JVM cannot be passed here. Use MAVEN_OPTS or the exec:exec instead. See the user guide for
     * more information.
     *
     * @parameter
     * @since 1.0
     */
    @Parameter
    private Property[] systemProperties;

    /**
     * Indicates if mojo should be kept running after the mainclass terminates.
     * Usefull for serverlike apps with deamonthreads.
     *
     * @parameter expression="${exec.keepAlive}" default-value="false"
     * @since 1.0
     * @deprecated since 1.1-alpha-1
     */
    @Parameter(defaultValue = "false")
    private boolean keepAlive = false;

    /**
     * Wether to interrupt/join and possibly stop the daemon threads upon quitting. <br/> If this is <code>false</code>,
     * maven does nothing about the daemon threads.  When maven has no more work to do, the VM will normally terminate
     * any remaining daemon threads.
     * <p>
     * In certain cases (in particular if maven is embedded),
     * you might need to keep this enabled to make sure threads are properly cleaned up to ensure they don't interfere
     * with subsequent activity.
     * In that case, see {@link #daemonThreadJoinTimeout} and
     * {@link #stopUnresponsiveDaemonThreads} for further tuning.
     * </p>
     *
     * @parameter expression="${exec.cleanupDaemonThreads} default-value="true"
     * @since 1.1-beta-1
     */
    @Parameter(defaultValue = "true")
    private boolean cleanupDaemonThreads = true;

    /**
     * This defines the number of milliseconds to wait for daemon threads to quit following their interruption.<br/>
     * This is only taken into account if {@link #cleanupDaemonThreads} is <code>true</code>.
     * A value &lt;=0 means to not timeout (i.e. wait indefinitely for threads to finish). Following a timeout, a
     * warning will be logged.
     * <p>Note: properly coded threads <i>should</i> terminate upon interruption but some threads may prove
     * problematic:  as the VM does interrupt daemon threads, some code may not have been written to handle
     * interruption properly. For example java.util.Timer is known to not handle interruptions in JDK &lt;= 1.6.
     * So it is not possible for us to infinitely wait by default otherwise maven could hang. A  sensible default
     * value has been chosen, but this default value <i>may change</i> in the future based on user feedback.</p>
     *
     * @parameter expression="${exec.daemonThreadJoinTimeout}" default-value="15000"
     * @since 1.1-beta-1
     */
    @Parameter(defaultValue = "15000")
    private long daemonThreadJoinTimeout = 15000;

    /**
     * Wether to call {@link Thread#stop()} following a timing out of waiting for an interrupted thread to finish.
     * This is only taken into account if {@link #cleanupDaemonThreads} is <code>true</code>
     * and the {@link #daemonThreadJoinTimeout} threshold has been reached for an uncooperative thread.
     * If this is <code>false</code>, or if {@link Thread#stop()} fails to get the thread to stop, then
     * a warning is logged and Maven will continue on while the affected threads (and related objects in memory)
     * linger on.  Consider setting this to <code>true</code> if you are invoking problematic code that you can't fix.
     * An example is {@link java.util.Timer} which doesn't respond to interruption.  To have <code>Timer</code>
     * fixed, vote for <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6336543">this bug</a>.
     *
     * @parameter expression="${exec.stopUnresponsiveDaemonThreads} default-value="false"
     * @since 1.1-beta-1
     */

    @Parameter(defaultValue = "false")
    private boolean stopUnresponsiveDaemonThreads = false;

    private Properties originalSystemProperties;

    public ExecObject(Log log, Artifact artifactToExec, ArtifactResolutionResult artifactResolutionResult, String mainClass, String[] arguments, Property[] systemProperties) {
        this.log = log;
        this.artifactToExec = artifactToExec;
        this.artifactResolutionResult = artifactResolutionResult;
        this.mainClass = mainClass;
        this.arguments = arguments;
        this.systemProperties = systemProperties;
    }

    /**
     * Execute goal.
     *
     * @throws MojoExecutionException execution of the main class or one of the threads it generated failed.
     * @throws org.apache.maven.plugin.MojoFailureException
     *                                something bad happened...
     */
    public void execute()
        throws MojoExecutionException, MojoFailureException {

        if (null == arguments) {
            arguments = new String[0];
        }

        if (log.isDebugEnabled()) {
            StringBuffer msg = new StringBuffer("Invoking : ");
            msg.append(mainClass);
            msg.append(".main(");
            for (int i = 0; i < arguments.length; i++) {
                if (i > 0) {
                    msg.append(", ");
                }
                msg.append(arguments[i]);
            }
            msg.append(")");
            log.debug(msg);
        }

        IsolatedThreadGroup threadGroup = new IsolatedThreadGroup(mainClass /*name*/);
        Thread bootstrapThread = new Thread(threadGroup, new Runnable() {
            public void run() {
                try {
                    Method main = Thread.currentThread().getContextClassLoader().loadClass(mainClass)
                        .getMethod("main", new Class[]{String[].class});
                    if (!main.isAccessible()) {
                        log.debug("Setting accessibility to true in order to invoke main().");
                        main.setAccessible(true);
                    }
                    if (!Modifier.isStatic(main.getModifiers())) {
                        throw new MojoExecutionException(
                            "Can't call main(String[])-method because it is not static.");
                    }
                    main.invoke(null, new Object[]{arguments});
                } catch (NoSuchMethodException e) {   // just pass it on
                    Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(),
                        new Exception(
                            "The specified mainClass doesn't contain a main method with appropriate signature.", e
                        )
                    );
                } catch (Exception e) {   // just pass it on
                    Thread.currentThread().getThreadGroup().uncaughtException(Thread.currentThread(), e);
                }
            }
        }, mainClass + ".main()");
        bootstrapThread.setContextClassLoader(getClassLoader());
        setSystemProperties();

        bootstrapThread.start();
        joinNonDaemonThreads(threadGroup);
        // It's plausible that spontaneously a non-daemon thread might be created as we try and shut down,
        // but it's too late since the termination condition (only daemon threads) has been triggered.
        if (keepAlive) {
            log.warn(
                "Warning: keepAlive is now deprecated and obsolete. Do you need it? Please comment on MEXEC-6.");
            waitFor(0);
        }

        if (cleanupDaemonThreads) {

            terminateThreads(threadGroup);

            try {
                threadGroup.destroy();
            } catch (IllegalThreadStateException e) {
                log.warn("Couldn't destroy threadgroup " + threadGroup, e);
            }
        }


        if (originalSystemProperties != null) {
            System.setProperties(originalSystemProperties);
        }

        synchronized (threadGroup) {
            if (threadGroup.uncaughtException != null) {
                throw new MojoExecutionException("An exception occured while executing the Java class. "
                    + threadGroup.uncaughtException.getMessage(),
                    threadGroup.uncaughtException);
            }
        }
    }

    /**
     * a ThreadGroup to isolate execution and collect exceptions.
     */
    class IsolatedThreadGroup extends ThreadGroup {
        private Throwable uncaughtException; // synchronize access to this

        public IsolatedThreadGroup(String name) {
            super(name);
        }

        public void uncaughtException(Thread thread, Throwable throwable) {
            if (throwable instanceof ThreadDeath) {
                return; //harmless
            }
            synchronized (this) {
                if (uncaughtException == null) // only remember the first one
                {
                    uncaughtException = throwable; // will be reported eventually
                }
            }
            log.warn(throwable);
        }
    }

    private void joinNonDaemonThreads(ThreadGroup threadGroup) {
        boolean foundNonDaemon;
        do {
            foundNonDaemon = false;
            Collection threads = getActiveThreads(threadGroup);
            for (Iterator iter = threads.iterator(); iter.hasNext(); ) {
                Thread thread = (Thread) iter.next();
                if (thread.isDaemon()) {
                    continue;
                }
                foundNonDaemon = true;   //try again; maybe more threads were created while we were busy
                joinThread(thread, 0);
            }
        } while (foundNonDaemon);
    }

    private void joinThread(Thread thread, long timeoutMsecs) {
        try {
            log.debug("joining on thread " + thread);
            thread.join(timeoutMsecs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // good practice if don't throw
            log.warn("interrupted while joining against thread " + thread, e);   // not expected!
        }
        if (thread.isAlive()) //generally abnormal
        {
            log.warn("thread " + thread + " was interrupted but is still alive after waiting at least "
                + timeoutMsecs + "msecs");
        }
    }

    private void terminateThreads(ThreadGroup threadGroup) {
        long startTime = System.currentTimeMillis();
        Set uncooperativeThreads = new HashSet(); // these were not responsive to interruption
        for (Collection threads = getActiveThreads(threadGroup); !threads.isEmpty();
             threads = getActiveThreads(threadGroup), threads.removeAll(uncooperativeThreads)) {
            // Interrupt all threads we know about as of this instant (harmless if spuriously went dead (! isAlive())
            //   or if something else interrupted it ( isInterrupted() ).
            for (Iterator iter = threads.iterator(); iter.hasNext(); ) {
                Thread thread = (Thread) iter.next();
                log.debug("interrupting thread " + thread);
                thread.interrupt();
            }
            // Now join with a timeout and call stop() (assuming flags are set right)
            for (Iterator iter = threads.iterator(); iter.hasNext(); ) {
                Thread thread = (Thread) iter.next();
                if (!thread.isAlive()) {
                    continue; //and, presumably it won't show up in getActiveThreads() next iteration
                }
                if (daemonThreadJoinTimeout <= 0) {
                    joinThread(thread, 0); //waits until not alive; no timeout
                    continue;
                }
                long timeout = daemonThreadJoinTimeout
                    - (System.currentTimeMillis() - startTime);
                if (timeout > 0) {
                    joinThread(thread, timeout);
                }
                if (!thread.isAlive()) {
                    continue;
                }
                uncooperativeThreads.add(thread); // ensure we don't process again
                if (stopUnresponsiveDaemonThreads) {
                    log.warn("thread " + thread + " will be Thread.stop()'ed");
                    thread.stop();
                } else {
                    log.warn("thread " + thread + " will linger despite being asked to die via interruption");
                }
            }
        }
        if (!uncooperativeThreads.isEmpty()) {
            log.warn("NOTE: " + uncooperativeThreads.size() + " thread(s) did not finish despite being asked to "
                + " via interruption. This is not a problem with exec:java, it is a problem with the running code."
                + " Although not serious, it should be remedied.");
        } else {
            int activeCount = threadGroup.activeCount();
            if (activeCount != 0) {
                // TODO this may be nothing; continue on anyway; perhaps don't even log in future
                Thread[] threadsArray = new Thread[1];
                threadGroup.enumerate(threadsArray);
                log.debug("strange; " + activeCount
                    + " thread(s) still active in the group " + threadGroup + " such as " + threadsArray[0]);
            }
        }
    }

    private Collection getActiveThreads(ThreadGroup threadGroup) {
        Thread[] threads = new Thread[threadGroup.activeCount()];
        int numThreads = threadGroup.enumerate(threads);
        Collection result = new ArrayList(numThreads);
        for (int i = 0; i < threads.length && threads[i] != null; i++) {
            result.add(threads[i]);
        }
        return result; //note: result should be modifiable
    }

    /**
     * Pass any given system properties to the java system properties.
     */
    private void setSystemProperties() {
        if (systemProperties != null) {
            originalSystemProperties = System.getProperties();
            for (int i = 0; i < systemProperties.length; i++) {
                Property systemProperty = systemProperties[i];
                String value = systemProperty.getValue();
                System.setProperty(systemProperty.getKey(), value == null ? "" : value);
            }
        }
    }

    /**
     * Set up a classloader for the execution of the main class.
     *
     * @return the classloader
     * @throws MojoExecutionException if a problem happens
     */
    private ClassLoader getClassLoader() throws MojoExecutionException {
        List<URL> classpathURLs = new ArrayList<URL>();
        this.addRelevantProjectDependenciesToClasspath(classpathURLs);

        return new URLClassLoader(classpathURLs.toArray(new URL[classpathURLs.size()]));
    }



    /**
     * Add any relevant project dependencies to the classpath.
     * Takes includeProjectDependencies into consideration.
     *
     * @param path classpath of {@link java.net.URL} objects
     * @throws MojoExecutionException if a problem happens
     */
    private void addRelevantProjectDependenciesToClasspath(List<URL> path)throws MojoExecutionException {
        try {
            log.debug("Project Dependencies will be included.");

            Set<Artifact> artifacts = artifactResolutionResult.getArtifacts();

            for (Artifact it : artifacts) {
                log.debug("Adding artifact to classpath : " + it.getArtifactId());
                path.add(it.getFile().toURI().toURL());
            }
        } catch (MalformedURLException e) {
            throw new MojoExecutionException("Error during setting up classpath", e);
        }

    }




    /**
     * Stop program execution for nn millis.
     *
     * @param millis the number of millis-seconds to wait for,
     *               <code>0</code> stops program forever.
     */
    private void waitFor(long millis) {
        Object lock = new Object();
        synchronized (lock) {
            try {
                lock.wait(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // good practice if don't throw
                log.warn("Spuriously interrupted while waiting for " + millis + "ms", e);
            }
        }
    }

}
