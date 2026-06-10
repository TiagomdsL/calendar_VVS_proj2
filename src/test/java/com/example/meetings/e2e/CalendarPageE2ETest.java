package com.example.meetings.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for the Calendar page itself and the iCal subscription section.
 *
 * <p>Scenarios covered:
 * <ul>
 *   <li>Empty-state message shown when no meetings exist.</li>
 *   <li>Multiple meetings are all listed on the page.</li>
 *   <li>Organiser is displayed next to each meeting.</li>
 *   <li>iCal subscription section is visible.</li>
 *   <li>iCal URLs contain the correct scheme (webcal / https).</li>
 *   <li>The "Discover" link navigates to /discover.</li>
 * </ul>
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CalendarPageE2ETest extends SeleniumTestBase {

    // -----------------------------------------------------------------------
    // Empty state
    // -----------------------------------------------------------------------

    /**
     * A new user with no meetings should see a friendly empty-state message
     * and an invitation to propose their first meeting.
     */
    @Test
    void calendarPage_noMeetings_showsEmptyStateMessage() {
        registerAndLogin("cal_empty", "pass");

        assertThat(pageContains("No meetings yet")).isTrue();
    }

    /**
     * The empty-state message contains a link to the proposal form.
     */
    @Test
    void calendarPage_emptyState_linkToProposeMeeting() {
        registerAndLogin("cal_empty2", "pass");

        WebElement link = driver.findElement(By.linkText("Propose one"));
        assertThat(link.getAttribute("href")).contains("/meetings/new");
    }

    // -----------------------------------------------------------------------
    // Meetings listed
    // -----------------------------------------------------------------------

    /**
     * After proposing two meetings, both titles appear on the calendar page.
     */
    @Test
    void calendarPage_multipleMeetings_allTitlesVisible() { ///ERROR
        registerAndLogin("cal_multi", "pass");

        proposeMeeting("Morning standup", "",  "2030-06-01T09:00", "2030-06-01T09:15", "");
        proposeMeeting("Weekly all-hands", "", "2030-06-01T10:00", "2030-06-01T11:00", "");

        assertThat(pageContains("Morning standup")).isTrue();
        assertThat(pageContains("Weekly all-hands")).isTrue();
    }

    /**
     * The organiser's username appears next to each meeting entry.
     */
    @Test
    void calendarPage_meetingEntry_showsOrganiserUsername() { //ERROR
        registerAndLogin("cal_org", "pass");

        proposeMeeting("Budget review","",  "2030-07-01T14:00", "2030-07-01T15:00", "");

        assertThat(pageContains("cal_org")).isTrue();
    }

    /**
     * The meeting description appears on the calendar page when one is given.
     */
    @Test
    void calendarPage_meetingWithDescription_descriptionIsVisible() { ///ERROR
        registerAndLogin("cal_desc", "pass");

        proposeMeeting("Sprint review", "We review the sprint deliverables", "2030-08-01T15:00", "2030-08-01T16:00", ""); 
        
        assertThat(pageContains("We review the sprint deliverables")).isTrue();
    }

    // -----------------------------------------------------------------------
    // iCal section
    // -----------------------------------------------------------------------

    /**
     * The "Subscribe (iCal)" section is always present on the calendar page.
     */
    @Test
    void calendarPage_icalSectionIsPresent() {
        registerAndLogin("cal_ical1", "pass");

        assertThat(pageContains("Subscribe (iCal)")).isTrue();
    }

    /**
     * The webcal URL shown in the iCal section starts with {@code webcal://}.
     */
    @Test
    void calendarPage_icalWebcalUrlStartsWithWebcalScheme() {
        registerAndLogin("cal_ical2", "pass");

        // There are two <code class="url"> elements; the first is the webcal one.
        WebElement webcalCode = driver.findElements(By.cssSelector("code.url")).get(0);
        assertThat(webcalCode.getText()).startsWith("webcal://");
    }

    /**
     * The https URL shown in the iCal section starts with {@code https://} or
     * {@code http://} (in the test environment the base URL is http).
     */
    @Test
    void calendarPage_icalHttpsUrlContainsIcalPath() {
        registerAndLogin("cal_ical3", "pass");

        WebElement httpsCode = driver.findElements(By.cssSelector("code.url")).get(1);
        assertThat(httpsCode.getText()).contains("/ical/");
    }

    /**
     * The "Download .ics" button is present and its href points to the iCal
     * endpoint.
     */
    @Test
    void calendarPage_downloadIcsButton_hrefPointsToIcalEndpoint() {
        registerAndLogin("cal_ical4", "pass");

        WebElement downloadLink = driver.findElement(By.linkText("Download .ics"));
        assertThat(downloadLink.getAttribute("href")).contains("/ical/");
    }

    // -----------------------------------------------------------------------
    // Navigation bar
    // -----------------------------------------------------------------------

    /**
     * The "Discover" navigation link leads to /discover.
     */
    @Test
    void calendarPage_discoverNavLink_navigatesToDiscoverPage() {
        registerAndLogin("cal_nav1", "pass");

        driver.findElement(By.linkText("Discover")).click();

        assertThat(driver.getCurrentUrl()).contains("/discover");
    }

    /**
     * The logged-in username is visible in the navigation bar.
     */
    @Test
    void calendarPage_navBar_showsLoggedInUsername() {
        registerAndLogin("cal_nav2", "pass");

        String navText = driver.findElement(By.tagName("nav")).getText();
        assertThat(navText).contains("cal_nav2");
    }
}
