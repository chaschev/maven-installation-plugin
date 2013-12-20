package com.chaschev.install;

import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.resolution.ArtifactResult;

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
    private Log log;

    /**
     * Defines the scope of the classpath passed to the plugin. Set to compile,test,runtime or system depending on your
     * needs. Since 1.1.2, the default value is 'runtime' instead of 'compile'.
     *
     * @parameter expression="${exec.classpathScope}" default-value="runtime"
     */
    @Parameter(defaultValue = "runtime")
    protected String classpathScope;

    private Artifact artifactToExec;

    List<ArtifactResult> dependencies;


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

    public ExecObject(Log log, Artifact artifactToExec, List<ArtifactResult> dependencies, String mainClass, String[] arguments, Property[] systemProperties) {
        this.log = log;
        this.artifactToExec = artifactToExec;
        this.dependencies = dependencies;
        this.mainClass = mainClass;
        this.arguments = arguments;
        this.systemProperties = systemProperties;
    }

    public void execute() {
        new ClassRunner(this.mainClass, createClassPathURLs(), artifactToExec.toString(), this.arguments, this.cleanupDaemonThreads, System.getProperties(), this.daemonThreadJoinTimeout, this.stopUnresponsiveDaemonThreads, this.systemProperties, this.log).invoke();
    }

    /**
     * a ThreadGroup to isolate execution and collect exceptions.
     */
    static class IsolatedThreadGroup extends ThreadGroup {
        private Throwable uncaughtException; // synchronize access to this
        private Log log;

        public IsolatedThreadGroup(String name, Log log) {
            super(name);
            this.log = log;
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

            if (log != null) {
                log.warn(throwable);
            } else {
                System.err.println(throwable);
            }
        }
    }


    private List<URL> createClassPathURLs() {
        List<URL> classpathURLs = new ArrayList<URL>();
        this.addRelevantProjectDependenciesToClasspath(classpathURLs);
        return classpathURLs;
    }


    /**
     * Add any relevant project dependencies to the classpath.
     * Takes includeProjectDependencies into consideration.
     *
     * @param path classpath of {@link java.net.URL} objects
     */
    private void addRelevantProjectDependenciesToClasspath(List<URL> path) {
        try {
            log.debug("Project Dependencies will be included.");

            for (ArtifactResult it : dependencies) {
                Artifact artifact = it.getArtifact();
                log.debug("adding artifact to classpath : " + artifact.getArtifactId() + " from repo " + it.getRepository().getId());
                path.add(artifact.getFile().toURI().toURL());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Error during setting up classpath", e);
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

    public static class ClassRunner {
        private final String mainClass;
        private final Log log;
        private List<URL> classPathURLs;
        private String jarPath;
        private String[] arguments;
        private boolean cleanupDaemonThreads;
        private Properties originalSystemProperties;
        private long daemonThreadJoinTimeout;
        private boolean stopUnresponsiveDaemonThreads;
        private Property[] systemProperties;

        public ClassRunner(String mainClass, List<URL> classPathURLs, String jarPath, String[] arguments, boolean cleanupDaemonThreads, Properties originalSystemProperties, long daemonThreadJoinTimeout, boolean stopUnresponsiveDaemonThreads, Property[] systemProperties, Log log) {
            this.mainClass = mainClass;
            this.classPathURLs = classPathURLs;
            this.jarPath = jarPath;
            this.arguments = arguments;
            this.cleanupDaemonThreads = cleanupDaemonThreads;
            this.originalSystemProperties = originalSystemProperties;
            this.daemonThreadJoinTimeout = daemonThreadJoinTimeout;
            this.stopUnresponsiveDaemonThreads = stopUnresponsiveDaemonThreads;
            this.systemProperties = systemProperties;
            this.log = log;
        }

        public void invoke() {
            info("executing class " + mainClass + " in " + jarPath);

            if (null == arguments) {
                arguments = new String[0];
            }

            StringBuilder msg = new StringBuilder("Invoking : ");
            msg.append(mainClass);
            msg.append(".main(");
            for (int i = 0; i < arguments.length; i++) {
                if (i > 0) {
                    msg.append(", ");
                }
                msg.append(arguments[i]);
            }
            msg.append(")");
            debug(msg.toString());

            IsolatedThreadGroup threadGroup = new IsolatedThreadGroup(mainClass, log /*name*/);
            final String[] finalArguments = arguments;
            Thread bootstrapThread = new Thread(threadGroup, new Runnable() {
                public void run() {
                    try {
                        Method main = Thread.currentThread().getContextClassLoader().loadClass(mainClass)
                            .getMethod("main", new Class[]{String[].class});
                        if (!main.isAccessible()) {
                            debug("Setting accessibility to true in order to invoke main().");
                            main.setAccessible(true);
                        }
                        if (!Modifier.isStatic(main.getModifiers())) {
                            throw new RuntimeException(
                                "Can't call main(String[])-method because it is not static.");
                        }
                        main.invoke(null, new Object[]{finalArguments});
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

            bootstrapThread.setContextClassLoader(getClassLoader(classPathURLs));
            setSystemProperties(systemProperties);

            bootstrapThread.start();
            joinNonDaemonThreads(threadGroup);

            // It's plausible that spontaneously a non-daemon thread might be created as we try and shut down,
            // but it's too late since the termination condition (only daemon threads) has been triggered.


            if (cleanupDaemonThreads) {
                terminateThreads(threadGroup, daemonThreadJoinTimeout, stopUnresponsiveDaemonThreads);

                try {
                    threadGroup.destroy();
                } catch (IllegalThreadStateException e) {
                    warn("Couldn't destroy threadgroup " + threadGroup, e);
                }
            }

            if (originalSystemProperties != null) {
                System.setProperties(originalSystemProperties);
            }

            synchronized (threadGroup) {
                if (threadGroup.uncaughtException != null) {
                    throw new RuntimeException("An exception occured while executing the Java class. "
                        + threadGroup.uncaughtException.getMessage(),
                        threadGroup.uncaughtException);
                }
            }
        }


        /**
         * Set up a classloader for the execution of the main class.
         *
         * @param classpathURLs
         * @return the classloader
         */
        private ClassLoader getClassLoader(List<URL> classpathURLs) {

            return new URLClassLoader(classpathURLs.toArray(new URL[classpathURLs.size()]));
        }

        /**
         * Pass any given system properties to the java system properties.
         *
         * @param systemProperties
         */
        private void setSystemProperties(Property[] systemProperties) {
            if (systemProperties != null) {
                for (int i = 0; i < systemProperties.length; i++) {
                    Property systemProperty = systemProperties[i];
                    String value = systemProperty.getValue();
                    System.setProperty(systemProperty.getKey(), value == null ? "" : value);
                }
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
                String s = "joining on thread " + thread;
                debug(s);
                thread.join(timeoutMsecs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();   // good practice if don't throw
                warn("interrupted while joining against thread " + thread, e);   // not expected!
            }
            if (thread.isAlive()) //generally abnormal
            {
                warn("thread " + thread + " was interrupted but is still alive after waiting at least "
                    + timeoutMsecs + "msecs");
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

        private void terminateThreads(ThreadGroup threadGroup, long daemonThreadJoinTimeout1, boolean stopUnresponsiveDaemonThreads) {
            long startTime = System.currentTimeMillis();
            Set uncooperativeThreads = new HashSet(); // these were not responsive to interruption
            for (Collection threads = getActiveThreads(threadGroup); !threads.isEmpty();
                 threads = getActiveThreads(threadGroup), threads.removeAll(uncooperativeThreads)) {
                // Interrupt all threads we know about as of this instant (harmless if spuriously went dead (! isAlive())
                //   or if something else interrupted it ( isInterrupted() ).
                for (Iterator iter = threads.iterator(); iter.hasNext(); ) {
                    Thread thread = (Thread) iter.next();
                    debug("interrupting thread " + thread);
                    thread.interrupt();
                }
                // Now join with a timeout and call stop() (assuming flags are set right)
                for (Iterator iter = threads.iterator(); iter.hasNext(); ) {
                    Thread thread = (Thread) iter.next();
                    if (!thread.isAlive()) {
                        continue; //and, presumably it won't show up in getActiveThreads() next iteration
                    }
                    if (daemonThreadJoinTimeout1 <= 0) {
                        joinThread(thread, 0); //waits until not alive; no timeout
                        continue;
                    }
                    long timeout = daemonThreadJoinTimeout1
                        - (System.currentTimeMillis() - startTime);
                    if (timeout > 0) {
                        joinThread(thread, timeout);
                    }
                    if (!thread.isAlive()) {
                        continue;
                    }
                    uncooperativeThreads.add(thread); // ensure we don't process again
                    if (stopUnresponsiveDaemonThreads) {
                        warn("thread " + thread + " will be Thread.stop()'ed");
                        thread.stop();
                    } else {
                        warn("thread " + thread + " will linger despite being asked to die via interruption");
                    }
                }
            }

            if (!uncooperativeThreads.isEmpty()) {
                warn("NOTE: " + uncooperativeThreads.size() + " thread(s) did not finish despite being asked to "
                    + " via interruption. This is not a problem with exec:java, it is a problem with the running code."
                    + " Although not serious, it should be remedied.");
            } else {
                int activeCount = threadGroup.activeCount();
                if (activeCount != 0) {
                    // TODO this may be nothing; continue on anyway; perhaps don't even log in future
                    Thread[] threadsArray = new Thread[1];
                    threadGroup.enumerate(threadsArray);
                    debug("strange; " + activeCount
                        + " thread(s) still active in the group " + threadGroup + " such as " + threadsArray[0]);
                }
            }
        }

        private void debug(String s) {
            if (log != null)
                log.debug(s);
        }

        private void info(String s) {
            if (log != null)
                log.info(s);
        }

        private void warn(String s, Exception e) {
            if (log != null)
                log.warn(s, e);
        }

        private void warn(String s) {
            if (log != null)
                log.warn(s);
        }
    }
}
