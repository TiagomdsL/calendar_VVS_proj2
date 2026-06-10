package com.example.meetings.e2e;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.time.Duration;

/**
 * Base class for all Selenium / End-to-End tests.
 *
 * <p>Starts a headless Chrome instance before every test and tears it
 * down afterwards.  Subclasses call {@link #loginAs(String, String)} and
 * the other small helpers so that test methods stay readable.</p>
 */
public abstract class SeleniumTestBase {

    /** Injected by Spring Boot's random-port test slice. */
    @LocalServerPort
    protected int port;

    protected WebDriver driver;
    protected WebDriverWait wait;

    // Stored by loginAs() so that proposeMeeting() can re-authenticate if the
    // session is invalidated between navigations (Spring Security session fixation).
    private String lastUsername;
    private String lastPassword;

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    /**
     * Downloads / sets up ChromeDriver once per JVM run using WebDriverManager.
     * This avoids hitting the network on every test class.
     */
    @BeforeAll
    static void setUpDriverBinary() {
        WebDriverManager.chromedriver().setup();
    }

    @BeforeEach
    void setUpDriver() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",   // new headless mode (Chrome 112+)
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--disable-gpu",
                "--window-size=1280,900"
        );
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(10));
    }

    @AfterEach
    void tearDownDriver() {
        if (driver != null) {
            driver.quit();
        }
    }

    // -----------------------------------------------------------------------
    // Navigation helpers
    // -----------------------------------------------------------------------

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }

    protected void get(String path) {
        driver.get(url(path));
    }

    // -----------------------------------------------------------------------
    // Auth helpers
    // -----------------------------------------------------------------------

    /**
     * Navigates to {@code /register} and fills the registration form.
     * Redirects to login on success.
     */
    protected void register(String username, String email, String password) {
        get("/register");
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("email")).sendKeys(email);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type=submit]")).click();
        // Wait for redirect to /login?registered
        wait.until(ExpectedConditions.urlContains("/login"));
    }

    /**
     * Navigates to {@code /login} and submits the login form.
     * Stores the credentials so that {@link #proposeMeeting} can re-authenticate
     * automatically if the session is lost between navigations.
     * Waits until the browser has left the login page (redirect to /calendar).
     */
    protected void loginAs(String username, String password) {
        this.lastUsername = username;
        this.lastPassword = password;
        get("/login");
        driver.findElement(By.id("username")).sendKeys(username);
        driver.findElement(By.id("password")).sendKeys(password);
        driver.findElement(By.cssSelector("button[type=submit]")).click();
        wait.until(ExpectedConditions.urlContains("/calendar"));
    }

    /**
     * Registers a fresh user and immediately logs in as that user.
     * Uses {@code username + "@test.com"} as the e-mail address.
     */
    protected void registerAndLogin(String username, String password) {
        register(username, username + "@test.com", password);
        loginAs(username, password);
    }

    // -----------------------------------------------------------------------
    // Form helpers
    // -----------------------------------------------------------------------

    /**
     * Proposes a meeting from the /meetings/new form.
     *
     * <p>Navigates to {@code /meetings/new}, fills the form, and submits it.
     * If Spring Security invalidates the session between navigations and redirects
     * to {@code /login}, the method re-authenticates automatically and retries the
     * proposal once — keeping test methods free of boilerplate re-login logic.</p>
     *
     * @param title     	meeting title
     * @param description   meeting description (optional, may be empty)
     * @param start     	datetime-local value, e.g. {@code "2030-06-15T10:00"}
     * @param end       	datetime-local value, e.g. {@code "2030-06-15T11:00"}
     * @param invitees  	comma-separated usernames (may be empty)
     */
    protected void proposeMeeting(String title, String description, String start, String end, String invitees) {
        get("/meetings/new");

        // Wait until either the form is ready or we were kicked to /login.
        wait.until(ExpectedConditions.or(
                ExpectedConditions.visibilityOfElementLocated(By.id("title")),
                ExpectedConditions.urlContains("/login")
        ));

        // If Spring Security killed the session, re-login and retry once.
        if (driver.getCurrentUrl().contains("/login")) {
            loginAs(lastUsername, lastPassword);
            get("/meetings/new");
            wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("title")));
        }

        driver.findElement(By.id("title")).sendKeys(title);
        if (description != null && !description.isBlank()) {
			driver.findElement(By.id("description")).sendKeys(description);
		}

        // datetime-local inputs need JS because WebDriver sendKeys is unreliable
        setDateTimeLocal("start", start);
        setDateTimeLocal("end",   end);

        if (invitees != null && !invitees.isBlank()) {
            driver.findElement(By.id("invitees")).sendKeys(invitees);
        }
        driver.findElement(By.cssSelector("button[type=submit]:not(.secondary)")).click();

        // Wait for /calendar, but also catch accidental logout to give a clear
        // failure message instead of a generic TimeoutException.
        wait.until(ExpectedConditions.or(
                ExpectedConditions.urlContains("/calendar"),
                ExpectedConditions.urlContains("/login")
        ));
        if (!driver.getCurrentUrl().contains("/calendar")) {
            throw new AssertionError(
                    "proposeMeeting: expected redirect to /calendar but landed at: "
                    + driver.getCurrentUrl());
        }
    }

    // -----------------------------------------------------------------------
    // Low-level helpers
    // -----------------------------------------------------------------------

    /**
     * Sets a {@code datetime-local} input via JavaScript because Selenium's
     * {@code sendKeys} is browser/OS dependent for these inputs.
     */
    protected void setDateTimeLocal(String inputId, String value) {
        WebElement el = driver.findElement(By.id(inputId));
        ((org.openqa.selenium.JavascriptExecutor) driver)
                .executeScript("arguments[0].value = arguments[1];", el, value);
    }

    /**
     * Waits for an element matching the given CSS selector to be visible,
     * then returns it.
     */
    protected WebElement waitFor(String cssSelector) {
        return wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.cssSelector(cssSelector)));
    }

    /**
     * Returns {@code true} if the current page body contains the given text.
     */
    protected boolean pageContains(String text) {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

        return wait.until(ExpectedConditions.textToBePresentInElementLocated(
            By.tagName("body"),
            text
        ));
    }
}