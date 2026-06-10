package com.example.meetings.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for the authentication flow (registration, login, logout).
 *
 * <p>Each test gets a fresh in-memory H2 database via the {@code test} profile
 * and a real (headless) Chrome browser via {@link SeleniumTestBase}.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class AuthE2ETest extends SeleniumTestBase {

    // Driver setup and teardown are fully handled by SeleniumTestBase —
    // no @BeforeEach / @AfterEach overrides needed here.

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    /**
     * A new visitor can fill the registration form and, on success, is
     * redirected to the login page where a confirmation message appears.
     */
    @Test
    void registerNewUser_redirectsToLoginWithSuccessMessage() {
        get("/register");

        driver.findElement(By.id("username")).sendKeys("alice");
        driver.findElement(By.id("email")).sendKeys("alice@test.com");
        driver.findElement(By.id("password")).sendKeys("password123");
        driver.findElement(By.cssSelector("button[type=submit]")).click();

        // Wait for the redirect to complete before asserting the URL.
        wait.until(ExpectedConditions.urlContains("/login"));

        assertThat(driver.getCurrentUrl()).contains("/login");
        assertThat(pageContains("Account created")).isTrue();
    }

    /**
     * Trying to register with a username that already exists shows an inline
     * error on the register page and does NOT redirect.
     */
    @Test
    void registerDuplicateUsername_showsErrorOnSamePage() {
        register("bob", "bob@test.com", "password123");

        get("/register");
        driver.findElement(By.id("username")).sendKeys("bob");
        driver.findElement(By.id("email")).sendKeys("bob2@test.com");
        driver.findElement(By.id("password")).sendKeys("password123");
        driver.findElement(By.cssSelector("button[type=submit]")).click();

        // Wait for the error element to appear (page re-renders in place).
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".error")));

        assertThat(driver.getCurrentUrl()).contains("/register");
        assertThat(driver.findElement(By.cssSelector(".error")).isDisplayed()).isTrue();
    }

    /**
     * The register form preserves the username and email when it re-renders
     * after a validation error, so the user does not have to retype everything.
     */
    @Test
    void registerError_preservesUsernameAndEmailFields() {
        register("carol", "carol@test.com", "pass");

        get("/register");
        driver.findElement(By.id("username")).sendKeys("carol");        // duplicate
        driver.findElement(By.id("email")).sendKeys("carol2@test.com");
        driver.findElement(By.id("password")).sendKeys("pass");
        driver.findElement(By.cssSelector("button[type=submit]")).click();

        // Wait for the form to re-render with the preserved values.
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("username")));

        assertThat(driver.findElement(By.id("username")).getAttribute("value"))
                .isEqualTo("carol");
        assertThat(driver.findElement(By.id("email")).getAttribute("value"))
                .isEqualTo("carol2@test.com");
    }

    // -----------------------------------------------------------------------
    // Login
    // -----------------------------------------------------------------------

    /**
     * A registered user can log in and is redirected to the calendar page.
     */
    @Test
    void login_validCredentials_redirectsToCalendar() {
        register("dave", "dave@test.com", "secret");
        loginAs("dave", "secret");

        // loginAs already waits for /calendar — assert is just a safety check.
        assertThat(driver.getCurrentUrl()).contains("/calendar");
    }

    /**
     * Wrong password shows an "Invalid username or password" message on the
     * login page.
     */
    @Test
    void login_wrongPassword_showsErrorMessage() {
        register("eve", "eve@test.com", "correct");

        get("/login");
        driver.findElement(By.id("username")).sendKeys("eve");
        driver.findElement(By.id("password")).sendKeys("wrong");
        driver.findElement(By.cssSelector("button[type=submit]")).click();

        // Wait for the error to appear before asserting.
        wait.until(ExpectedConditions.urlContains("/login?error"));

        assertThat(driver.getCurrentUrl()).contains("/login");
        assertThat(pageContains("Invalid username or password")).isTrue();
    }

    /**
     * An unauthenticated user who tries to access a protected page is
     * redirected to /login.
     */
    @Test
    void accessProtectedPage_unauthenticated_redirectsToLogin() {
        get("/calendar");

        // Wait for Spring Security's redirect to complete.
        wait.until(ExpectedConditions.urlContains("/login"));

        assertThat(driver.getCurrentUrl()).contains("/login");
    }

    // -----------------------------------------------------------------------
    // Logout
    // -----------------------------------------------------------------------

    /**
     * A logged-in user can sign out and is redirected to the login page with a
     * confirmation message.
     */
    @Test
    void logout_redirectsToLoginWithLogoutMessage() {
        registerAndLogin("frank", "pass123");

        driver.findElement(By.cssSelector("nav button[type=submit]")).click();

        // Wait for the logout redirect before asserting.
        wait.until(ExpectedConditions.urlContains("/login"));

        assertThat(driver.getCurrentUrl()).contains("/login");
        assertThat(pageContains("signed out")).isTrue();
    }

    /**
     * After signing out the user cannot access protected pages without
     * re-authenticating.
     */
    @Test
    void afterLogout_calendarPageRequiresLogin() {
        registerAndLogin("grace", "pass123");

        driver.findElement(By.cssSelector("nav button[type=submit]")).click();
        wait.until(ExpectedConditions.urlContains("/login"));

        get("/calendar");
        wait.until(ExpectedConditions.urlContains("/login"));

        assertThat(driver.getCurrentUrl()).contains("/login");
    }
}