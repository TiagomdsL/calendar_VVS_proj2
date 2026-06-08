package com.example.meetings.service;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
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

    // =========================================================================
    // Calendar-level structure
    // =========================================================================

    @Nested
    @DisplayName("VCALENDAR structure")
    class VCalendarStructure {

        @Test
        @DisplayName("output contains all required VCALENDAR headers")
        void containsRequiredVCalendarHeaders() {
            String output = icalService.render(owner, List.of());

            assertThat(output)
                    .contains("BEGIN:VCALENDAR")
                    .contains("VERSION:2.0")
                    .contains("PRODID:-//meetings-app//EN")
                    .contains("CALSCALE:GREGORIAN")
                    .contains("END:VCALENDAR");
        }

        @Test
        @DisplayName("all lines use CRLF endings per RFC 5545")
        void usesCRLFLineEndings() {
            String output = icalService.render(owner, List.of());

            // Must contain CRLF
            assertThat(output).contains("\r\n");
            // No bare LF (CR stripped leaves only the content, no stray \n)
            assertThat(output.replace("\r\n", "")).doesNotContain("\n");
        }

        @Test
        @DisplayName("empty meeting list produces a valid calendar with no VEVENT blocks")
        void emptyMeetingListProducesEmptyCalendar() {
            String output = icalService.render(owner, List.of());

            assertThat(output)
                    .contains("BEGIN:VCALENDAR")
                    .contains("END:VCALENDAR")
                    .doesNotContain("BEGIN:VEVENT");
        }

        @Test
        @DisplayName("output ends with END:VCALENDAR followed by CRLF")
        void endsWithVCalendarCRLF() {
            String output = icalService.render(owner, List.of());

            assertThat(output).endsWith("END:VCALENDAR\r\n");
        }
    }

    // =========================================================================
    // VEVENT count and timestamps
    // =========================================================================

    @Nested
    @DisplayName("VEVENT blocks")
    class VEventBlocks {

        @Test
        @DisplayName("emits exactly one VEVENT per meeting")
        void emitsOneVEventPerMeeting() {
            Meeting m1 = buildMeeting("Meeting A");
            Meeting m2 = buildMeeting("Meeting B");

            String output = icalService.render(owner, List.of(m1, m2));

            // Use CRLF-aware split instead of lines() to avoid double-counting
            long count = Arrays.stream(output.split("\r\n"))
                    .filter("BEGIN:VEVENT"::equals)
                    .count();
            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("DTSTART and DTEND are formatted as UTC in yyyyMMdd'T'HHmmss'Z' format")
        void includesDtStartAndDtEnd() {
            Meeting m = buildMeeting("Test");

            String output = icalService.render(owner, List.of(m));

            assertThat(output)
                    .contains("DTSTART:20250901T180000Z")
                    .contains("DTEND:20250901T200000Z");
        }

        @Test
        @DisplayName("each VEVENT contains a UID field")
        void includesUidInEachEvent() {
            Meeting m = buildMeeting("Test");

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("UID:");
        }

        @Test
        @DisplayName("each VEVENT contains a DTSTAMP field")
        void includesDtStamp() {
            Meeting m = buildMeeting("Test");

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("DTSTAMP:");
        }
    }

    // =========================================================================
    // STATUS mapping
    // =========================================================================

    @Nested
    @DisplayName("STATUS field")
    class StatusField {

        @Test
        @DisplayName("STATUS:CONFIRMED when all participants have accepted")
        void confirmedMeetingHasStatusConfirmed() {
            Meeting m = buildMeeting("Confirmed");
            m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("STATUS:CONFIRMED");
        }

        @Test
        @DisplayName("STATUS:TENTATIVE when at least one participant is PENDING")
        void tentativeMeetingHasStatusTentative() {
            User other = new User("bob", "bob@example.com", "hash");
            Meeting m = buildMeeting("Tentative");
            m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));
            m.addParticipant(new MeetingParticipant(m, other, InviteStatus.PENDING));

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("STATUS:TENTATIVE");
        }

        @Test
        @DisplayName("STATUS:TENTATIVE when a participant has declined (not all accepted)")
        void meetingWithDeclinedParticipantIsTentative() {
            User other = new User("bob", "bob@example.com", "hash");
            Meeting m = buildMeeting("Declined");
            m.addParticipant(new MeetingParticipant(m, owner,  InviteStatus.ACCEPTED));
            m.addParticipant(new MeetingParticipant(m, other, InviteStatus.DECLINED));

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("STATUS:TENTATIVE");
        }
    }

    // =========================================================================
    // ORGANIZER and ATTENDEE lines
    // =========================================================================

    @Nested
    @DisplayName("ORGANIZER and ATTENDEE fields")
    class OrganizerAndAttendee {

        @Test
        @DisplayName("ORGANIZER line contains the organizer's username as CN")
        void includesOrganizerLine() {
            Meeting m = buildMeeting("Test");

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("ORGANIZER;CN=alice");
        }

        @Test
        @DisplayName("ORGANIZER line contains the organizer's email as mailto")
        void organizerLineContainsEmail() {
            Meeting m = buildMeeting("Test");

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("mailto:alice@example.com");
        }

        @Test
        @DisplayName("PENDING participant maps to PARTSTAT=NEEDS-ACTION")
        void pendingParticipantMapsToNeedsAction() {
            User bob = new User("bob", "bob@example.com", "hash");
            Meeting m = buildMeeting("Test");
            m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));
            m.addParticipant(new MeetingParticipant(m, bob,   InviteStatus.PENDING));

            String output = icalService.render(owner, List.of(m));

            assertThat(output)
                    .contains("ATTENDEE;CN=bob")
                    .contains("PARTSTAT=NEEDS-ACTION");
        }

        @Test
        @DisplayName("ACCEPTED participant maps to PARTSTAT=ACCEPTED")
        void acceptedParticipantMapsToAccepted() {
            Meeting m = buildMeeting("Test");
            m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("PARTSTAT=ACCEPTED");
        }

        @Test
        @DisplayName("DECLINED participant maps to PARTSTAT=DECLINED")
        void declinedParticipantMapsToDeclined() {
            User bob = new User("bob", "bob@example.com", "hash");
            Meeting m = buildMeeting("Test");
            m.addParticipant(new MeetingParticipant(m, owner, InviteStatus.ACCEPTED));
            m.addParticipant(new MeetingParticipant(m, bob,   InviteStatus.DECLINED));

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("PARTSTAT=DECLINED");
        }
    }

    // =========================================================================
    // RFC 5545 text escaping (§3.3.11)
    // =========================================================================

    @Nested
    @DisplayName("RFC 5545 text escaping in SUMMARY")
    class Escaping {

        @Test
        @DisplayName("semicolons in title are escaped as \\;")
        void escapesSemicolon() {
            Meeting m = buildMeetingWithTitle("Title;With;Semicolons");

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("SUMMARY:Title\\;With\\;Semicolons");
        }

        @Test
        @DisplayName("commas in title are escaped as \\,")
        void escapesComma() {
            Meeting m = buildMeetingWithTitle("Title,With,Commas");

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("SUMMARY:Title\\,With\\,Commas");
        }

        @Test
        @DisplayName("backslashes in title are escaped as \\\\")
        void escapesBackslash() {
            Meeting m = buildMeetingWithTitle("Path\\to\\file");

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("SUMMARY:Path\\\\to\\\\file");
        }

        @Test
        @DisplayName("newlines in title are escaped as \\n")
        void escapesNewlineInTitle() {
            Meeting m = buildMeetingWithTitle("Line1\nLine2");

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("SUMMARY:Line1\\nLine2");
        }
    }

    // =========================================================================
    // DESCRIPTION field
    // =========================================================================

    @Nested
    @DisplayName("DESCRIPTION field")
    class DescriptionField {

        @Test
        @DisplayName("DESCRIPTION line is absent when description is null")
        void omitsDescriptionWhenNull() {
            Meeting m = buildMeeting("No desc");  // description = null

            String output = icalService.render(owner, List.of(m));

            assertThat(output).doesNotContain("DESCRIPTION:");
        }

        @Test
        @DisplayName("DESCRIPTION line is absent when description is blank")
        void omitsDescriptionWhenBlank() {
            Meeting m = new Meeting("Title", "   ", start, end, owner);

            String output = icalService.render(owner, List.of(m));

            assertThat(output).doesNotContain("DESCRIPTION:");
        }

        @Test
        @DisplayName("DESCRIPTION line is present and contains the value when non-blank")
        void includesDescriptionWhenPresent() {
            Meeting m = new Meeting("Title", "Some description", start, end, owner);

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("DESCRIPTION:Some description");
        }

        @Test
        @DisplayName("newlines in description are escaped as \\n per RFC 5545")
        void escapesNewlineInDescription() {
            Meeting m = new Meeting("Title", "Line1\nLine2", start, end, owner);

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("DESCRIPTION:Line1\\nLine2");
        }

        @Test
        @DisplayName("semicolons in description are escaped")
        void escapesSemicolonInDescription() {
            Meeting m = new Meeting("Title", "Venue;Details", start, end, owner);

            String output = icalService.render(owner, List.of(m));

            assertThat(output).contains("DESCRIPTION:Venue\\;Details");
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private Meeting buildMeeting(String title) {
        return new Meeting(title, null, start, end, owner);
    }

    private Meeting buildMeetingWithTitle(String title) {
        return new Meeting(title, null, start, end, owner);
    }
}
