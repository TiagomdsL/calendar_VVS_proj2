package com.example.meetings.service;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ICalServiceTest {

    private ICalService icalService;
    private User owner;
    private Instant start;
    private Instant end;

    @BeforeEach
    void setUp() {
        icalService = new ICalService();
        owner = new User("alice", "alice@example.com", "hash");
        start = Instant.parse("2025-09-01T18:00:00Z");
        end   = start.plus(2, ChronoUnit.HOURS);
    }

    @Test
    void render_containsRequiredVCalendarHeaders() {
        String output = icalService.render(owner, List.of());

        assertThat(output)
                .contains("BEGIN:VCALENDAR")
                .contains("VERSION:2.0")
                .contains("PRODID:-//meetings-app//EN")
                .contains("CALSCALE:GREGORIAN")
                .contains("END:VCALENDAR");
    }

    @Test
    void render_usesCRLFLineEndings() {
        String output = icalService.render(owner, List.of());
        // Per RFC 5545 all lines must end with CRLF
        assertThat(output).contains("\r\n");
        // No bare LF (without preceding CR) should remain
        String withoutCRLF = output.replace("\r\n", "");
        assertThat(withoutCRLF).doesNotContain("\n");
    }

    @Test
    void render_emitsVEventForEachMeeting() {
        Meeting m1 = buildMeeting("Meeting A");
        Meeting m2 = buildMeeting("Meeting B");

        String output = icalService.render(owner, List.of(m1, m2));

        long count = output.lines().filter("BEGIN:VEVENT"::equals).count();
        assertThat(count).isEqualTo(2);
    }

    @Test
    void render_includesDtStartAndDtEnd() {
        Meeting m = buildMeeting("Test");

        String output = icalService.render(owner, List.of(m));

        assertThat(output)
                .contains("DTSTART:20250901T180000Z")
                .contains("DTEND:20250901T200000Z");
    }

    @Test
    void render_confirmedMeetingHasStatusConfirmed() {
        Meeting m = buildMeeting("Confirmed");
        // only organizer participant → all ACCEPTED → isConfirmed() == true
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("STATUS:CONFIRMED");
    }

    @Test
    void render_tentativeMeetingHasStatusTentative() {
        User other = new User("bob", "bob@example.com", "hash");
        Meeting m = buildMeeting("Tentative");
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, other, InviteStatus.PENDING));

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("STATUS:TENTATIVE");
    }

    @Test
    void render_includesOrganizerLine() {
        Meeting m = buildMeeting("Test");

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("ORGANIZER;CN=alice");
    }

    @Test
    void render_includesAttendeeForParticipant() {
        User bob = new User("bob", "bob@example.com", "hash");
        Meeting m = buildMeeting("Test");
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob, InviteStatus.PENDING));

        String output = icalService.render(owner, List.of(m));

        assertThat(output)
                .contains("ATTENDEE;CN=bob")
                .contains("PARTSTAT=NEEDS-ACTION");
    }

    @Test
    void render_attendeeDeclinedMapsToDeclined() {
        User bob = new User("bob", "bob@example.com", "hash");
        Meeting m = buildMeeting("Test");
        m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob, InviteStatus.DECLINED));

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("PARTSTAT=DECLINED");
    }

    @Test
    void render_escapesSemicolonInTitle() {
        Meeting m = buildMeetingWithTitle("Title;With;Semicolons");

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("SUMMARY:Title\\;With\\;Semicolons");
    }

    @Test
    void render_escapesCommaInTitle() {
        Meeting m = buildMeetingWithTitle("Title,With,Commas");

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("SUMMARY:Title\\,With\\,Commas");
    }

    @Test
    void render_escapesBackslashInTitle() {
        Meeting m = buildMeetingWithTitle("Path\\to\\file");

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("SUMMARY:Path\\\\to\\\\file");
    }

    @Test
    void render_omitsDescriptionWhenBlank() {
        Meeting m = buildMeeting("No desc");
        // description is null by default in buildMeeting

        String output = icalService.render(owner, List.of(m));

        assertThat(output).doesNotContain("DESCRIPTION:");
    }

    @Test
    void render_includesDescriptionWhenPresent() {
        Meeting m = new Meeting("Title", "Some description", start, end, owner);

        String output = icalService.render(owner, List.of(m));

        assertThat(output).contains("DESCRIPTION:Some description");
    }

    @Test
    void render_emptyMeetingListProducesEmptyCalendar() {
        String output = icalService.render(owner, List.of());

        assertThat(output)
                .contains("BEGIN:VCALENDAR")
                .contains("END:VCALENDAR")
                .doesNotContain("BEGIN:VEVENT");
    }

    // ------------------------------------------------------------------ helpers

    private Meeting buildMeeting(String title) {
        return new Meeting(title, null, start, end, owner);
    }

    private Meeting buildMeetingWithTitle(String title) {
        return new Meeting(title, null, start, end, owner);
    }
}
