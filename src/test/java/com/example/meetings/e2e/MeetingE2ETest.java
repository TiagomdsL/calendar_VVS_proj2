package com.example.meetings.e2e;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-End tests for the meeting proposal and invite-response flows.
 *
 * <p>Scenarios covered:
 * <ul>
 *   <li>Organiser proposes a meeting without invitees → appears on calendar as confirmed.</li>
 *   <li>Organiser proposes a meeting with a valid invitee → appears as tentative.</li>
 *   <li>Invitee accepts → both users see the meeting as confirmed.</li>
 *   <li>Invitee declines → meeting stays tentative on organiser's calendar.</li>
 *   <li>End time before start time shows a validation error.</li>
 *   <li>Unknown invitee username shows a validation error.</li>
 *   <li>Proposal form preserves field values after a validation error.</li>
 * </ul>
 * </p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class MeetingE2ETest extends SeleniumTestBase {

    // -----------------------------------------------------------------------
    // Propose without invitees
    // -----------------------------------------------------------------------

    /**
     * When the organiser creates a meeting with no invitees, they are the only
     * participant and auto-accept, so {@code isConfirmed()} is true immediately.
     * The calendar page must show a "confirmed" badge.
     */
    @Test
    void proposeMeeting_noInvitees_appearsAsConfirmedOnCalendar() {
        registerAndLogin("org1", "pass");

        proposeMeeting("Solo standup", "", "2030-07-01T09:00", "2030-07-01T09:30", "");

        assertThat(pageContains("Solo standup")).isTrue();
        assertThat(pageContains("confirmed")).isTrue();
    }

    /**
     * The meeting title shows on the calendar page after the proposal redirect.
     */
    @Test
    void proposeMeeting_titleAppearsOnCalendarPage() {
        registerAndLogin("org2", "pass");

        proposeMeeting("Team retrospective", "", "2030-08-01T14:00", "2030-08-01T15:00", "");

        assertThat(pageContains("Team retrospective")).isTrue();
    }

    // -----------------------------------------------------------------------
    // Propose with a valid invitee
    // -----------------------------------------------------------------------

    /**
     * When an invitee is named, the meeting is "tentative" (not yet confirmed)
     * on the organiser's calendar until the invitee responds.
     */
    @Test
    void proposeMeeting_withInvitee_appearsAsTentativeOnOrganizerCalendar() {
        // Register both users first
        register("inv_user1", "inv_user1@test.com", "pass");
        registerAndLogin("organizer1", "pass");

        proposeMeeting("Sync", "", "2030-09-01T10:00", "2030-09-01T11:00", "inv_user1");

        assertThat(pageContains("Sync")).isTrue();
        assertThat(pageContains("tentative")).isTrue();
    }

    /**
     * The invitee sees the meeting in their "Pending invites" section.
     */
    @Test
    void proposeMeeting_inviteeSeesItAsPendingInvite() {
        register("inv_user2", "inv_user2@test.com", "pass");
        registerAndLogin("organizer2", "pass");
        proposeMeeting("Planning", "", "2030-10-01T10:00", "2030-10-01T11:00", "inv_user2");

        // Sign out as organizer and log in as invitee
        driver.findElement(By.cssSelector("nav button[type=submit]")).click();
        loginAs("inv_user2", "pass");

        assertThat(pageContains("Pending invites")).isTrue();
        assertThat(pageContains("Planning")).isTrue();
    }

    // -----------------------------------------------------------------------
    // Accept invite
    // -----------------------------------------------------------------------

    /**
     * After the invitee accepts, the meeting is marked "confirmed" on
     * the invitee's calendar page.
     */
    @Test
    void acceptInvite_meetingBecomesConfirmedOnInviteeCalendar() {
        register("inv_user3", "inv_user3@test.com", "pass");
        registerAndLogin("organizer3", "pass");
        proposeMeeting("Design review", "", "2030-11-01T10:00", "2030-11-01T11:00", "inv_user3");

        // Switch to invitee
        driver.findElement(By.cssSelector("nav button[type=submit]")).click();
        loginAs("inv_user3", "pass");

        // Accept the invite
        List<WebElement> acceptForms = driver.findElements(
                By.cssSelector("input[name=action][value=accept]"));
        assertThat(acceptForms).isNotEmpty();
        acceptForms.get(0).findElement(By.xpath("..")).submit();

        wait.until(ExpectedConditions.urlContains("/calendar"));

        assertThat(pageContains("Design review")).isTrue();
        assertThat(pageContains("confirmed")).isTrue();
    }

    /**
     * After the only invitee accepts, the organiser also sees the meeting
     * as confirmed.
     */
    @Test
    void acceptInvite_organizerAlsoSeesConfirmed() {
        register("inv_user4", "inv_user4@test.com", "pass");
        registerAndLogin("organizer4", "pass");
        proposeMeeting("Kick-off", "", "2030-12-01T10:00", "2030-12-01T11:00", "inv_user4");

        // Switch to invitee and accept
        driver.findElement(By.cssSelector("nav button[type=submit]")).click();
        loginAs("inv_user4", "pass");
        driver.findElement(By.cssSelector("input[name=action][value=accept]"))
              .findElement(By.xpath("..")).submit();

        // Switch back to organizer
        driver.findElement(By.cssSelector("nav button[type=submit]")).click();
        loginAs("organizer4", "pass");

        assertThat(pageContains("Kick-off")).isTrue();
        assertThat(pageContains("confirmed")).isTrue();
    }

    // -----------------------------------------------------------------------
    // Decline invite
    // -----------------------------------------------------------------------

    /**
     * After the invitee declines, the meeting stays "tentative" on the
     * organiser's calendar (not everyone has accepted).
     */
    @Test
    void declineInvite_meetingStaysTentativeForOrganizer() {
        register("inv_user5", "inv_user5@test.com", "pass");
        registerAndLogin("organizer5", "pass");
        proposeMeeting("Workshop", "", "2031-01-15T09:00", "2031-01-15T10:00", "inv_user5");

        // Switch to invitee and decline
        driver.findElement(By.cssSelector("nav button[type=submit]")).click();
        loginAs("inv_user5", "pass");
        driver.findElement(By.cssSelector("input[name=action][value=decline]"))
              .findElement(By.xpath("..")).submit();

        // Switch back to organizer
        driver.findElement(By.cssSelector("nav button[type=submit]")).click();
        loginAs("organizer5", "pass");

        assertThat(pageContains("Workshop")).isTrue();
        assertThat(pageContains("tentative")).isTrue();
    }

    /**
     * After declining, the invite no longer appears in the invitee's
     * "Pending invites" section.
     */
    @Test
    void declineInvite_removedFromPendingInvitesList() {
        register("inv_user6", "inv_user6@test.com", "pass");
        registerAndLogin("organizer6", "pass");
        proposeMeeting("Hackathon", "", "2031-02-01T09:00", "2031-02-01T17:00", "inv_user6");

        driver.findElement(By.cssSelector("nav button[type=submit]")).click();
        loginAs("inv_user6", "pass");
        driver.findElement(By.cssSelector("input[name=action][value=decline]"))
              .findElement(By.xpath("..")).submit();

        // Pending invites section should be gone (no pending invites left)
        assertThat(pageContains("No meetings yet")).isTrue();
    }

    // -----------------------------------------------------------------------
    // Validation errors
    // -----------------------------------------------------------------------

    /**
     * Submitting a proposal where end is before start should keep the user on
     * the proposal page and display an error message.
     */
    @Test
    void proposeMeeting_endBeforeStart_showsValidationError() {
        registerAndLogin("org_val1", "pass");

        get("/meetings/new");
        driver.findElement(By.id("title")).sendKeys("Bad times");
        setDateTimeLocal("start", "2030-06-01T11:00");
        setDateTimeLocal("end",   "2030-06-01T10:00");   // end before start
        driver.findElement(By.cssSelector("button[type=submit]:not(.secondary)")).click();

        // Must stay on the propose page
        assertThat(driver.getCurrentUrl()).contains("/meetings/new");
        assertThat(driver.findElement(By.cssSelector(".error")).isDisplayed()).isTrue();
    }

    /**
     * Proposing a meeting with an invitee username that does not exist shows
     * an error on the propose page.
     */
    @Test
    void proposeMeeting_unknownInvitee_showsErrorOnProposePage() {
        registerAndLogin("org_val2", "pass");

        get("/meetings/new");
        driver.findElement(By.id("title")).sendKeys("Ghost meeting");
        setDateTimeLocal("start", "2030-06-01T10:00");
        setDateTimeLocal("end",   "2030-06-01T11:00");
        driver.findElement(By.id("invitees")).sendKeys("nobody_exists");
        driver.findElement(By.cssSelector("button[type=submit]:not(.secondary)")).click();
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.cssSelector(".error")));

        assertThat(driver.getCurrentUrl()).contains("/meetings/new");
        assertThat(driver.findElement(By.cssSelector(".error")).isDisplayed()).isTrue();
    }

    /**
     * After a validation error the proposal form keeps the title and invitee
     * values the user had typed, avoiding the need to retype them.
     */
    @Test
    void proposeMeeting_validationError_preservesFormValues() {
        registerAndLogin("org_val3", "pass");

        get("/meetings/new");
        driver.findElement(By.id("title")).sendKeys("My Meeting");
        setDateTimeLocal("start", "2030-06-01T11:00");
        setDateTimeLocal("end",   "2030-06-01T10:00");   // invalid
        driver.findElement(By.id("invitees")).sendKeys("ghost");
        driver.findElement(By.cssSelector("button[type=submit]:not(.secondary)")).click();
        
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("title")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("invitees")));

        assertThat(driver.findElement(By.id("title")).getAttribute("value"))
                .isEqualTo("My Meeting");
        assertThat(driver.findElement(By.id("invitees")).getAttribute("value"))
                .contains("ghost");
    }

    // -----------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------

    /**
     * The "Propose a meeting" link in the navigation bar takes the user to
     * the correct form page.
     */
    @Test
    void navBar_proposeMeetingLink_navigatesToProposeForm() {
        registerAndLogin("nav_user1", "pass");

        driver.findElement(By.linkText("Propose a meeting")).click();

        assertThat(driver.getCurrentUrl()).contains("/meetings/new");
        assertThat(driver.findElement(By.id("title"))).isNotNull();
    }

    /**
     * The "Cancel" link on the proposal form returns the user to the calendar.
     */
    @Test
    void proposeForm_cancelLink_returnsToCalendar() {
        registerAndLogin("nav_user2", "pass");

        get("/meetings/new");
        driver.findElement(By.linkText("Cancel")).click();

        assertThat(driver.getCurrentUrl()).contains("/calendar");
    }
}
