/*
 * Copyright (c) 2008-2018 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.uberjar;

import org.eclipse.jetty.plus.webapp.EnvConfiguration;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.webapp.WebXmlConfiguration;
import org.eclipse.jetty.xml.XmlConfiguration;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static com.haulmont.uberjar.CubaJettyUtils.*;

public class CubaJettyServer {
    protected int port;
    protected int stopPort;
    protected String stopKey;
    protected String contextPath;
    protected String portalContextPath;
    protected String frontContextPath;
    protected URL jettyEnvPathUrl;
    protected URL jettyConfUrl;

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getFrontContextPath() {
        return frontContextPath;
    }

    public void setFrontContextPath(String frontContextPath) {
        this.frontContextPath = frontContextPath;
    }

    public String getPortalContextPath() {
        return portalContextPath;
    }

    public void setPortalContextPath(String portalContextPath) {
        this.portalContextPath = portalContextPath;
    }

    public URL getJettyEnvPathUrl() {
        return jettyEnvPathUrl;
    }

    public void setJettyEnvPathUrl(URL jettyEnvPathUrl) {
        this.jettyEnvPathUrl = jettyEnvPathUrl;
    }

    public URL getJettyConfUrl() {
        return jettyConfUrl;
    }

    public void setJettyConfUrl(URL jettyConfUrl) {
        this.jettyConfUrl = jettyConfUrl;
    }

    public int getStopPort() {
        return stopPort;
    }

    public void setStopPort(int stopPort) {
        this.stopPort = stopPort;
    }

    public String getStopKey() {
        return stopKey;
    }

    public void setStopKey(String stopKey) {
        this.stopKey = stopKey;
    }

    public void start() {
        String appHome = System.getProperty("app.home");
        if (appHome == null || appHome.length() == 0) {
            System.setProperty("app.home", System.getProperty("user.dir"));
        }
        if ("/".equals(System.getProperty("app.home"))) {
            System.setProperty("app.home", "");
        }
        if (stopPort > 0) {
            System.setProperty("STOP.PORT", Integer.toString(stopPort));
            System.setProperty("STOP.KEY", stopKey);
            System.setProperty("STOP.HOST", "127.0.0.1");
        }
        try {
            Server server = createServer();
            server.start();
            server.join();
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    protected Server createServer() throws Exception {
        ClassLoader serverClassLoader = Thread.currentThread().getContextClassLoader();
        ClassLoader sharedClassLoader = new UberJarURLClassLoader("Shared",
                pathsToURLs(serverClassLoader, SHARED_CLASS_PATH_IN_JAR), serverClassLoader);
        Server server;
        if (jettyConfUrl != null) {
            XmlConfiguration xmlConfiguration = new XmlConfiguration(jettyConfUrl);
            server = (Server) xmlConfiguration.configure();
        } else {
            server = new Server(port);
        }
        server.setStopAtShutdown(true);
        List<Handler> handlers = new ArrayList<>();
        if (CubaJettyUtils.hasCoreApp(serverClassLoader)) {
            String coreContextPath = contextPath;
            if (isSingleJar(serverClassLoader)) {
                if (PATH_DELIMITER.equals(contextPath)) {
                    coreContextPath = PATH_DELIMITER + "app-core";
                } else {
                    coreContextPath = contextPath + "-core";
                }
            }
            handlers.add(createAppContext("Core", serverClassLoader, sharedClassLoader, CORE_PATH_IN_JAR, coreContextPath));
        }
        if (hasWebApp(serverClassLoader)) {
            handlers.add(createAppContext("Web", serverClassLoader, sharedClassLoader, WEB_PATH_IN_JAR, contextPath));
        }
        if (hasPortalApp(serverClassLoader)) {
            String portalContextPath = contextPath;
            if (isSingleJar(serverClassLoader)) {
                portalContextPath = this.portalContextPath;
            }
            handlers.add(createAppContext("Portal", serverClassLoader, sharedClassLoader, PORTAL_PATH_IN_JAR, portalContextPath));
        }
        if (hasFrontApp(serverClassLoader)) {
            handlers.add(createFrontAppContext(serverClassLoader, sharedClassLoader));
        }

        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.setHandlers(handlers.toArray(new Handler[0]));
        server.setHandler(handlerCollection);

        return server;
    }

    protected WebAppContext createAppContext(String name, ClassLoader serverClassLoader, ClassLoader sharedClassLoader,
                                             String appPathInJar, String contextPath) throws URISyntaxException {
        ClassLoader appClassLoader = new UberJarURLClassLoader(name,
                pathsToURLs(serverClassLoader, getAppClassesPath(appPathInJar)), sharedClassLoader);

        WebAppContext appContext = new WebAppContext();
        appContext.setConfigurations(new Configuration[]{new WebXmlConfiguration(), createEnvConfiguration()});
        appContext.setContextPath(contextPath);
        appContext.setClassLoader(appClassLoader);

        setResourceBase(serverClassLoader, appContext, appPathInJar);

        return appContext;
    }

    protected WebAppContext createFrontAppContext(ClassLoader serverClassLoader, ClassLoader sharedClassLoader) throws URISyntaxException {
        ClassLoader frontClassLoader = new UberJarURLClassLoader("Front",
                pathsToURLs(serverClassLoader, getAppClassesPath(FRONT_PATH_IN_JAR)), sharedClassLoader);

        WebAppContext frontContext = new WebAppContext();
        frontContext.setConfigurations(new Configuration[]{new WebXmlConfiguration()});
        frontContext.setContextPath(frontContextPath);
        frontContext.setClassLoader(frontClassLoader);

        setResourceBase(serverClassLoader, frontContext, FRONT_PATH_IN_JAR);

        System.setProperty("cuba.front.baseUrl", PATH_DELIMITER.equals(frontContextPath) ? frontContextPath :
                frontContextPath + PATH_DELIMITER);
        System.setProperty("cuba.front.apiUrl", PATH_DELIMITER.equals(contextPath) ? "/rest/" :
                contextPath + PATH_DELIMITER + "rest" + PATH_DELIMITER);

        return frontContext;
    }

    protected void setResourceBase(ClassLoader serverClassLoader, WebAppContext appContext, String appPath) throws URISyntaxException {
        URL resourceBaseUrl = serverClassLoader.getResource(appPath);
        if (resourceBaseUrl != null) {
            appContext.setResourceBase(resourceBaseUrl.toURI().toString());
        }
    }

    protected EnvConfiguration createEnvConfiguration() {
        EnvConfiguration envConfiguration = new EnvConfiguration();
        if (jettyEnvPathUrl != null) {
            envConfiguration.setJettyEnvXml(jettyEnvPathUrl);
        }
        return envConfiguration;
    }
}
