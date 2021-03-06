/*
 * (C) Copyright 2011 Nuxeo SA (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Sun Seng David TAN
 *     Florent Guillaume
 *     Benoit Delbosc
 */
package org.nuxeo.functionaltests;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.nuxeo.functionaltests.pages.AbstractPage;
import org.nuxeo.functionaltests.pages.DocumentBasePage;
import org.nuxeo.functionaltests.pages.DocumentBasePage.UserNotConnectedException;
import org.nuxeo.functionaltests.pages.LoginPage;
import org.openqa.selenium.By;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.NotFoundException;
import org.openqa.selenium.Proxy;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxProfile;
import org.openqa.selenium.internal.WrapsElement;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.Clock;
import org.openqa.selenium.support.ui.SystemClock;

import org.browsermob.proxy.ProxyServer;

/**
 * Base functions for all pages.
 */
public abstract class AbstractTest {

    public static final String NUXEO_URL = System.getProperty("nuxeoURL", "http://localhost:8080/nuxeo").replaceAll("/$","");

    private static final String FIREBUG_XPI = "firebug-1.6.2-fx.xpi";

    private static final String FIREBUG_VERSION = "1.6.2";

    private static final String FIREBUG_M2 = "firebug/firebug/1.6.2-fx";

    private static final String M2_REPO = "/.m2/repository/";

    private static final int LOAD_TIMEOUT_SECONDS = 30;

    private static final int AJAX_TIMEOUT_SECONDS = 10;

    private static final int PROXY_PORT = 4444;

    private static final String HAR_NAME = "http-headers.json";

    protected static RemoteWebDriver driver;

    protected static File tmp_firebug_xpi;

    protected static ProxyServer proxyServer = null;

    @BeforeClass
    public static void initDriver() throws Exception {
        String browser = System.getProperty("browser", "firefox");
        // Use the same strings as command-line Selenium
        if (browser.equals("chrome") || browser.equals("firefox")) {
            initFirefoxDriver();
        } else if (browser.equals("googlechrome")) {
            initChromeDriver();
        } else {
            throw new RuntimeException("Browser not supported: " + browser);
        }
    }

    protected static void initFirefoxDriver() throws Exception {
        DesiredCapabilities dc = DesiredCapabilities.firefox();
        FirefoxProfile profile = new FirefoxProfile();
        // Disable native events (makes things break on Windows)
        profile.setEnableNativeEvents(false);
        // Set English as default language
        profile.setPreference("general.useragent.locale", "en");
        profile.setPreference("intl.accept_languages", "en");
        addFireBug(profile);
        Proxy proxy = startProxy();
        if (proxy != null) {
            // Does not work, but leave code for when it does
            // Workaround: use 127.0.0.2
            proxy.setNoProxy("");
            profile.setProxyPreferences(proxy);
        }
        dc.setCapability(FirefoxDriver.PROFILE, profile);
        driver = new FirefoxDriver(dc);
    }

    protected static void initChromeDriver() throws Exception {
        DesiredCapabilities dc = DesiredCapabilities.chrome();
        ChromeOptions options = new ChromeOptions();
        options.addArguments(Arrays.asList("--ignore-certificate-errors"));
        Proxy proxy = startProxy();
        if (proxy != null) {
            proxy.setNoProxy("");
            dc.setCapability(CapabilityType.PROXY, proxy);
        }
        dc.setCapability(ChromeOptions.CAPABILITY, options);
        driver = new ChromeDriver(dc);
    }

    @AfterClass
    public static void quitDriver() throws InterruptedException {
        // Temporary code to take snapshots of the last page
        // TODO: snapshots only test on failure, prefix using the test name
        if (driver instanceof FirefoxDriver) {
            Thread.sleep(250);
            ((FirefoxDriver)driver).getScreenshotAs(new ScreenShotFileOutput("screenshot1-lastpage"));
            Thread.sleep(250);
            ((FirefoxDriver)driver).getScreenshotAs(new ScreenShotFileOutput("screenshot2-lastpage"));
        } else {
            // Not implemented for other drivers
        }

        if (driver != null) {
            driver.close();
            driver = null;
        }

        removeFireBug();

        try {
            stopProxy();
        } catch (Exception e) {
            System.err.println("Could not stop proxy: " + e.getMessage());
        }
    }

    /**
     * Introspects the classpath and returns the list of files in it.
     *
     * @return
     * @throws Exception
     */
    protected static List<String> getClassLoaderFiles() throws Exception {
        ClassLoader cl = AbstractTest.class.getClassLoader();
        URL[] urls = null;
        if (cl instanceof URLClassLoader) {
            urls = ((URLClassLoader) cl).getURLs();
        } else if (cl.getClass().getName().equals(
                "org.apache.tools.ant.AntClassLoader")) {
            Method method = cl.getClass().getMethod("getClasspath");
            String cp = (String) method.invoke(cl);
            String[] paths = cp.split(File.pathSeparator);
            urls = new URL[paths.length];
            for (int i = 0; i < paths.length; i++) {
                urls[i] = new URL("file:" + paths[i]);
            }
        } else {
            System.err.println("Unknow classloader type: "
                    + cl.getClass().getName());
            return null;
        }
        // special case for maven surefire with useManifestOnlyJar
        if (urls.length == 1) {
            try {
                URI uri = urls[0].toURI();
                if (uri.getScheme().equals("file")
                        && uri.getPath().contains("surefirebooter")) {
                    JarFile jar = new JarFile(new File(uri));
                    try {
                        String cp = jar.getManifest().getMainAttributes().getValue(
                                Attributes.Name.CLASS_PATH);
                        if (cp != null) {
                            String[] cpe = cp.split(" ");
                            URL[] newUrls = new URL[cpe.length];
                            for (int i = 0; i < cpe.length; i++) {
                                // Don't need to add 'file:' with maven
                                // surefire >= 2.4.2
                                String newUrl = cpe[i].startsWith("file:") ? cpe[i]
                                        : "file:" + cpe[i];
                                newUrls[i] = new URL(newUrl);
                            }
                            urls = newUrls;
                        }
                    } finally {
                        jar.close();
                    }
                }
            } catch (Exception e) {
                // skip
            }
        }
        // turn into files
        List<String> files = new ArrayList<String>(urls.length);
        for (URL url : urls) {
            files.add(url.toURI().getPath());
        }
        return files;
    }

    protected static void addFireBug(FirefoxProfile profile) throws Exception {
        File xpi = null;
        List<String> clf = getClassLoaderFiles();
        for (String f : clf) {
            if (f.endsWith("/" + FIREBUG_XPI)) {
                xpi = new File(f);
            }
        }
        if (xpi == null) {
            String customM2Repo = System.getProperty("M2_REPO", M2_REPO);
            // try to guess the location in the M2 repo
            for (String f : clf) {
                if (f.contains(customM2Repo)) {
                    String m2 = f.substring(0,
                            f.indexOf(customM2Repo) + customM2Repo.length());
                    xpi = new File(m2 + FIREBUG_M2 + "/" + FIREBUG_XPI);
                    break;
                }
            }
        }
        if (xpi == null || !xpi.exists()) {
            throw new RuntimeException(FIREBUG_XPI
                    + " not found in classloader or local M2 repository");
        }
        profile.addExtension(xpi);
        // avoid "first run" page
        profile.setPreference("extensions.firebug.currentVersion",
                FIREBUG_VERSION);
    }

    protected static void removeFireBug() {
        if (tmp_firebug_xpi != null) {
            tmp_firebug_xpi.delete();
            tmp_firebug_xpi.getParentFile().delete();
        }
    }

    protected static Proxy startProxy() throws Exception {
        if (Boolean.valueOf(System.getProperty("useProxy", "true"))) {
            proxyServer = new ProxyServer(PROXY_PORT);
            proxyServer.start();
            proxyServer.setCaptureHeaders(true);
            // Block access to tracking sites
            proxyServer.blacklistRequests("https?://www\\.nuxeo\\.com/embedded/wizard.*", 410);
            proxyServer.blacklistRequests("https?://.*\\.mktoresp\\.com/.*", 410);
            proxyServer.blacklistRequests(".*_mchId.*", 410);
            proxyServer.blacklistRequests("https?://.*\\.google-analytics\\.com/.*", 410);
            proxyServer.newHar("webdriver-test");
            Proxy proxy = proxyServer.seleniumProxy();
            return proxy;
        } else {
            return null;
        }
    }

    protected static void stopProxy() throws Exception {
        if (proxyServer != null) {
            String target = System.getProperty("nuxeo.log.dir");
            File harFile;
            if (target == null) {
                harFile = new File(HAR_NAME);
            } else {
                harFile = new File(target, HAR_NAME);
            }
            proxyServer.getHar().writeTo(harFile);
            proxyServer.stop();
        }
    }

    public static <T> T get(String url, Class<T> pageClassToProxy) {
        driver.get(url);
        return asPage(pageClassToProxy);
    }

    public static <T> T asPage(Class<T> pageClassToProxy) {
        T page = instantiatePage(driver, pageClassToProxy);
        PageFactory.initElements(new VariableElementLocatorFactory(driver,
                AJAX_TIMEOUT_SECONDS), page);
        // check all required WebElements on the page and wait for their
        // loading
        List<String> fieldNames = new ArrayList<String>();
        List<WrapsElement> elements = new ArrayList<WrapsElement>();
        for (Field field : pageClassToProxy.getDeclaredFields()) {
            if (field.getAnnotation(Required.class) != null) {
                try {
                    field.setAccessible(true);
                    fieldNames.add(field.getName());
                    elements.add((WrapsElement) field.get(page));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        Clock clock = new SystemClock();
        long end = clock.laterBy(SECONDS.toMillis(LOAD_TIMEOUT_SECONDS));
        String notLoaded = null;
        while (clock.isNowBefore(end)) {
            notLoaded = anyElementNotLoaded(elements, fieldNames);
            if (notLoaded == null) {
                return page;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        throw new NoSuchElementException("Timeout loading page "
                + pageClassToProxy.getSimpleName() + " missing element "
                + notLoaded);
    }

    protected static String anyElementNotLoaded(List<WrapsElement> proxies,
            List<String> fieldNames) {
        for (int i = 0; i < proxies.size(); i++) {
            WrapsElement proxy = proxies.get(i);
            try {
                // method implemented in LocatingElementHandler
                proxy.getWrappedElement();
            } catch (NoSuchElementException e) {
                return fieldNames.get(i);
            }
        }
        return null;
    }

    // private in PageFactory...
    protected static <T> T instantiatePage(WebDriver driver,
            Class<T> pageClassToProxy) {
        try {
            try {
                Constructor<T> constructor = pageClassToProxy.getConstructor(WebDriver.class);
                return constructor.newInstance(driver);
            } catch (NoSuchMethodException e) {
                return pageClassToProxy.newInstance();
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Finds the first {@link WebElement} using the given method, with a
     * timeout.
     *
     * @param by the locating mechanism
     * @param timeout the timeout in milliseconds
     * @return the first matching element on the current page, if found
     * @throws NoSuchElementException when not found
     */
    public static WebElement findElementWithTimeout(By by, int timeout)
            throws NoSuchElementException {
        return findElementWithTimeout(by, timeout, null);
    }

    /**
     * Finds the first {@link WebElement} using the given method, with a
     * timeout.
     *
     * @param by the locating mechanism
     * @param timeout the timeout in milliseconds
     * @param parentElement find from the element
     * @return the first matching element on the current page, if found
     * @throws NoSuchElementException when not found
     */
    public static WebElement findElementWithTimeout(By by, int timeout,
            WebElement parentElement) throws NoSuchElementException {
        Clock clock = new SystemClock();
        long end = clock.laterBy(timeout);
        NoSuchElementException lastException = null;
        while (clock.isNowBefore(end)) {
            try {
                WebElement element;
                if (parentElement == null) {
                    element = driver.findElement(by);
                } else {
                    element = parentElement.findElement(by);
                }
                if (element != null) {
                    return element;
                }
            } catch (NoSuchElementException e) {
                lastException = e;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        throw new NoSuchElementException(String.format(
                "Couldn't find element '%s' after timeout", by), lastException);
    }

    /**
     * Finds the first {@link WebElement} using the given method, with a
     * timeout.
     *
     * @param by the locating mechanism
     * @param timeout the timeout in milliseconds
     * @return the first matching element on the current page, if found
     * @throws NoSuchElementException when not found
     */
    public static WebElement findElementWithTimeout(By by)
            throws NoSuchElementException {
        return findElementWithTimeout(by, LOAD_TIMEOUT_SECONDS * 1000);
    }

    /**
     * Finds the first {@link WebElement} using the given method, with a
     * timeout.
     *
     * @param by the locating mechanism
     * @param timeout the timeout in milliseconds
     * @param parentElement find from the element
     * @return the first matching element on the current page, if found
     * @throws NoSuchElementException when not found
     */
    public static WebElement findElementWithTimeout(By by,
            WebElement parentElement) throws NoSuchElementException {
        return findElementWithTimeout(by, LOAD_TIMEOUT_SECONDS * 1000,
                parentElement);
    }

    /**
     * Waits until an element is enabled, with a timeout.
     *
     * @param element the element
     * @param timeout the timeout in milliseconds
     */
    public static void waitUntilEnabled(final WebElement element, int timeout)
            throws NotFoundException {
        Clock clock = new SystemClock();
        long end = clock.laterBy(timeout);
        while (clock.isNowBefore(end)) {
            if (element.isEnabled()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // ignore
            }
        }
        throw new NotFoundException("Element not enabled after timeout: "
                + element);
    }

    /**
     * Waits until an element is enabled, with a timeout.
     *
     * @param element the element
     */
    public static void waitUntilEnabled(WebElement element)
            throws NotFoundException {
        waitUntilEnabled(element, AJAX_TIMEOUT_SECONDS * 1000);
    }

    public LoginPage getLoginPage() {
        return get(NUXEO_URL + "/logout", LoginPage.class);
    }

    /**
     * navigate to a link text. wait until the link is available and click on
     * it.
     */
    public <T extends AbstractPage> T nav(Class<T> pageClass, String linkText) {
        WebElement link = findElementWithTimeout(By.linkText(linkText));
        if (link == null) {
            return null;
        }
        link.click();
        return asPage(pageClass);
    }

    /**
     * Navigate to a specified url
     *
     * @param urlString url
     * @throws MalformedURLException
     */
    public void navToUrl(String urlString) throws MalformedURLException {
        URL url = new URL(urlString);
        driver.navigate().to(url);
    }

    /**
     * Login as Administrator
     *
     * @return the Document base page (by default returned by nuxeo dm)
     * @throws UserNotConnectedException
     */
    public DocumentBasePage login() throws UserNotConnectedException {
        return login("Administrator", "Administrator");
    }

    public DocumentBasePage login(String username, String password)
            throws UserNotConnectedException {
        DocumentBasePage documentBasePage = getLoginPage().login(username,
                password, DocumentBasePage.class);
        documentBasePage.checkUserConnected(username);
        return documentBasePage;
    }

    /**
     * Login using an invalid credential.
     *
     * @param username
     * @param password
     * @return
     */
    public LoginPage loginInvalid(String username, String password) {
        LoginPage loginPage = getLoginPage().login(username, password,
                LoginPage.class);
        return loginPage;
    }

}
