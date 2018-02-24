package io.jenkins.jenkinsfile.runner;

import jenkins.slaves.DeprecatedAgentProtocolMonitor;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.ThreadPoolImpl;
import org.jvnet.hudson.test.WarExploder;

import javax.servlet.ServletContext;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Set up of Jenkins environment for executing a single Jenkinsfile.
 *
 * @author Kohsuke Kawaguchi
 */
public class JenkinsfileRunnerRule extends JenkinsRule {

    private Logger l2;

    /**
     * Sets up Jetty without any actual TCP port serving HTTP.
     */
    @Override
    protected ServletContext createWebServer() throws Exception {
        server = new Server(new ThreadPoolImpl(new ThreadPoolExecutor(10, 10, 10L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(),new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("Jetty Thread Pool");
                return t;
            }
        })));

        WebAppContext context = new WebAppContext(WarExploder.getExplodedDir().getPath(), contextPath);
        context.setClassLoader(getClass().getClassLoader());
        context.setConfigurations(new Configuration[]{new WebXmlConfiguration()});
        context.addBean(new NoListenerConfiguration(context));
        server.setHandler(context);
        context.getSecurityHandler().setLoginService(configureUserRealm());
        context.setResourceBase(WarExploder.getExplodedDir().getPath());

        server.start();

        localPort = -1;

        return context.getServletContext();
    }

    /**
     * Supply a dummy {@link LoginService} that allows nobody.
     */
    @Override
    protected LoginService configureUserRealm() {
        return new HashLoginService();
    }

    @Override
    public void before() throws Throwable {
        setLogLevels();
        super.before();
    }

    /**
     * We don't want to clutter console with log messages, so kill of any unimportant ones.
     */
    private void setLogLevels() {
        Logger.getLogger("").setLevel(Level.WARNING);
        l2 = Logger.getLogger(DeprecatedAgentProtocolMonitor.class.getName());
        l2.setLevel(Level.OFF);
    }

    /**
     * Skips the clean up.
     *
     * This was initially motivated by SLF4J leaving gnarly messages.
     * The whole JVM is going to die anyway, so we don't really care about cleaning up anything nicely.
     */
    @Override
    public void after() throws Exception {
        jenkins = null;
        super.after();
    }

    // Doesn't work as intended
//    @Initializer(before= InitMilestone.PLUGINS_LISTED)
//    public static void init() {
//        // no external connectivity needed
//        Jenkins.getInstance().setAgentProtocols(Collections.emptySet());
//    }
}
