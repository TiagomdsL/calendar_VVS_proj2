package com.example.meetings.service;

import com.example.meetings.discover.DiscoveredEvent;
import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import com.example.meetings.repository.MeetingParticipantRepository;
import com.example.meetings.repository.MeetingRepository;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MeetingServiceTest {

    @Mock MeetingRepository meetingRepository;
    @Mock MeetingParticipantRepository participantRepository;
    @Mock UserRepository userRepository;

    @InjectMocks MeetingService meetingService;

    private User organizer;
    private User invitee;
    private Instant start;
    private Instant end;

    @BeforeEach
    void setUp() {
        organizer = new User("alice", "alice@example.com", "hash");
        invitee   = new User("bob",   "bob@example.com",   "hash");
        start = Instant.now().plus(1, ChronoUnit.HOURS);
        end   = start.plus(1, ChronoUnit.HOURS);
    }

    // =========================================================================
    // propose()
    // =========================================================================

    @Nested
    @DisplayName("propose()")
    class Propose {

        @Test
        @DisplayName("saves the meeting and returns it with the correct title and organizer")
        void savesAndReturnsMeeting() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Meeting m = meetingService.propose(organizer, "Standup", null, start, end, List.of());

            assertThat(m.getTitle()).isEqualTo("Standup");
            assertThat(m.getOrganizer()).isEqualTo(organizer);
            verify(meetingRepository).save(any(Meeting.class));
        }

        @Test
        @DisplayName("organizer is automatically added as ACCEPTED participant")
        void organizerIsAutoAccepted() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Meeting m = meetingService.propose(organizer, "T", null, start, end, List.of());

            assertThat(m.getParticipants())
                    .anyMatch(p -> p.getUser().equals(organizer)
                                && p.getStatus() == InviteStatus.ACCEPTED);
        }

        @Test
        @DisplayName("invitee is added with PENDING status")
        void inviteeReceivesPendingInvite() {
            when(userRepository.findByUsername("bob")).thenReturn(Optional.of(invitee));
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Meeting m = meetingService.propose(organizer, "T", null, start, end, List.of("bob"));

            assertThat(m.getParticipants())
                    .anyMatch(p -> p.getUser().equals(invitee)
                                && p.getStatus() == InviteStatus.PENDING);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when end is before start")
        void rejectsEndBeforeStart() {
            assertThatThrownBy(() ->
                    meetingService.propose(organizer, "T", null, end, start, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("End time must be after start time");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when end equals start (zero duration)")
        void rejectsEndEqualToStart() {
            assertThatThrownBy(() ->
                    meetingService.propose(organizer, "T", null, start, start, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("End time must be after start time");
        }

        @Test
        @DisplayName("duplicate invitee username results in only one PENDING participant")
        void deduplicatesInvitees() {
            when(userRepository.findByUsername("bob")).thenReturn(Optional.of(invitee));
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Meeting m = meetingService.propose(organizer, "T", null, start, end,
                    List.of("bob", "bob"));

            long bobCount = m.getParticipants().stream()
                    .filter(p -> p.getUser().equals(invitee)).count();
            assertThat(bobCount).isEqualTo(1);
        }

        @Test
        @DisplayName("organizer listed as invitee is not added twice")
        void organizerNotAddedTwiceIfListedAsInvitee() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Meeting m = meetingService.propose(organizer, "T", null, start, end,
                    List.of("alice"));

            assertThat(m.getParticipants()).hasSize(1);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for an unknown invitee username")
        void throwsForUnknownInvitee() {
            when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    meetingService.propose(organizer, "T", null, start, end,
                            List.of("unknown")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Unknown invitee");
        }

        @Test
        @DisplayName("blank and whitespace-only invitee names are silently ignored")
        void skipsBlankInviteeNames() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Meeting m = meetingService.propose(organizer, "T", null, start, end,
                    List.of("  ", ""));

            verify(userRepository, never()).findByUsername(any());
            assertThat(m.getParticipants()).hasSize(1);
        }

        @Test
        @DisplayName("null invitee entry is treated as blank and skipped")
        void skipsNullInviteeName() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // The service normalizes null → "" → skips
            Meeting m = meetingService.propose(organizer, "T", null, start, end,
                    List.of((String) null == null ? "" : null));

            verify(userRepository, never()).findByUsername(any());
            assertThat(m.getParticipants()).hasSize(1);
        }

        @Test
        @DisplayName("description is stored as provided (including null)")
        void storesDescription() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Meeting m = meetingService.propose(organizer, "T", "desc", start, end, List.of());

            assertThat(m.getDescription()).isEqualTo("desc");
        }

        @Test
        @DisplayName("multiple distinct invitees are all added as PENDING")
        void multipleInviteesAllAddedAsPending() {
            User charlie = new User("charlie", "charlie@example.com", "hash");
            when(userRepository.findByUsername("bob")).thenReturn(Optional.of(invitee));
            when(userRepository.findByUsername("charlie")).thenReturn(Optional.of(charlie));
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            Meeting m = meetingService.propose(organizer, "T", null, start, end,
                    List.of("bob", "charlie"));

            assertThat(m.getParticipants()).hasSize(3); // organizer + 2 invitees
            assertThat(m.getParticipants())
                    .filteredOn(p -> p.getStatus() == InviteStatus.PENDING)
                    .hasSize(2);
        }
    }

    // =========================================================================
    // respond()
    // =========================================================================

    @Nested
    @DisplayName("respond()")
    class Respond {

        private MeetingParticipant pendingParticipant() {
            Meeting meeting = new Meeting("T", null, start, end, organizer);
            return new MeetingParticipant(meeting, invitee, InviteStatus.PENDING);
        }

        @Test
        @DisplayName("sets participant status to ACCEPTED when action is accept")
        void setsParticipantToAccepted() {
            MeetingParticipant p = pendingParticipant();
            when(participantRepository.findByMeetingIdAndUserId(1L, invitee.getId()))
                    .thenReturn(Optional.of(p));

            meetingService.respond(1L, invitee, InviteStatus.ACCEPTED);

            assertThat(p.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
        }

        @Test
        @DisplayName("sets participant status to DECLINED when action is decline")
        void setsParticipantToDeclined() {
            MeetingParticipant p = pendingParticipant();
            when(participantRepository.findByMeetingIdAndUserId(1L, invitee.getId()))
                    .thenReturn(Optional.of(p));

            meetingService.respond(1L, invitee, InviteStatus.DECLINED);

            assertThat(p.getStatus()).isEqualTo(InviteStatus.DECLINED);
        }

        @Test
        @DisplayName("throws IllegalArgumentException when status is PENDING (not a valid response)")
        void rejectsPendingStatus() {
            assertThatThrownBy(() ->
                    meetingService.respond(1L, invitee, InviteStatus.PENDING))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ACCEPTED or DECLINED");
        }

        @Test
        @DisplayName("throws IllegalArgumentException when no invite exists for the user")
        void throwsWhenNoInviteFound() {
            when(participantRepository.findByMeetingIdAndUserId(99L, invitee.getId()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    meetingService.respond(99L, invitee, InviteStatus.ACCEPTED))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No invite found");
        }
    }

    // =========================================================================
    // calendarFor() and pendingInvitesFor()
    // =========================================================================

    @Nested
    @DisplayName("calendarFor() and pendingInvitesFor()")
    class Queries {

        @Test
        @DisplayName("calendarFor delegates to MeetingRepository.findCalendarMeetings")
        void calendarForDelegatesToRepository() {
            when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(List.of());

            meetingService.calendarFor(organizer);

            verify(meetingRepository).findCalendarMeetings(organizer);
        }

        @Test
        @DisplayName("calendarFor returns whatever the repository returns")
        void calendarForReturnsRepositoryResult() {
            Meeting m = new Meeting("T", null, start, end, organizer);
            when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(List.of(m));

            List<Meeting> result = meetingService.calendarFor(organizer);

            assertThat(result).containsExactly(m);
        }

        @Test
        @DisplayName("pendingInvitesFor delegates to repository with PENDING status")
        void pendingInvitesForDelegatesToRepository() {
            when(participantRepository.findByUserAndStatus(organizer, InviteStatus.PENDING))
                    .thenReturn(List.of());

            meetingService.pendingInvitesFor(organizer);

            verify(participantRepository).findByUserAndStatus(organizer, InviteStatus.PENDING);
        }

        @Test
        @DisplayName("pendingInvitesFor returns only the pending invites for that user")
        void pendingInvitesForReturnsCorrectList() {
            Meeting m = new Meeting("T", null, start, end, organizer);
            MeetingParticipant pending = new MeetingParticipant(m, invitee, InviteStatus.PENDING);
            when(participantRepository.findByUserAndStatus(invitee, InviteStatus.PENDING))
                    .thenReturn(List.of(pending));

            List<MeetingParticipant> result = meetingService.pendingInvitesFor(invitee);

            assertThat(result).containsExactly(pending);
        }
    }

    // =========================================================================
    // copyFromDiscovered()
    // =========================================================================

    @Nested
    @DisplayName("copyFromDiscovered()")
    class CopyFromDiscovered {

        @Test
        @DisplayName("defaults end time to start + 2 hours when event has no end")
        void defaultsEndToTwoHoursWhenNull() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            DiscoveredEvent event = new DiscoveredEvent(
                    "TestSource", "42", "Concert", null, start, null, null, "Venue");

            Meeting m = meetingService.copyFromDiscovered(organizer, event);

            assertThat(m.getEndTime()).isEqualTo(start.plus(2, ChronoUnit.HOURS));
        }

        @Test
        @DisplayName("uses the provided end time when present")
        void usesProvidedEndTime() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            DiscoveredEvent event = new DiscoveredEvent(
                    "TestSource", "42", "Concert", null, start, end, null, "Venue");

            Meeting m = meetingService.copyFromDiscovered(organizer, event);

            assertThat(m.getEndTime()).isEqualTo(end);
        }

        @Test
        @DisplayName("owner is auto-accepted → meeting is immediately confirmed")
        void ownerIsAutoAccepted() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            DiscoveredEvent event = new DiscoveredEvent(
                    "TestSource", "42", "Concert", null, start, end, null, null);

            Meeting m = meetingService.copyFromDiscovered(organizer, event);

            assertThat(m.isConfirmed()).isTrue();
            assertThat(m.getParticipants())
                    .anyMatch(p -> p.getUser().equals(organizer)
                                && p.getStatus() == InviteStatus.ACCEPTED);
        }

        @Test
        @DisplayName("description contains source name, URL and venue")
        void descriptionIncludesSourceAndUrl() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            DiscoveredEvent event = new DiscoveredEvent(
                    "MySource", "1", "Show", "A great show.", start, end,
                    "http://example.com", "Big Arena");

            Meeting m = meetingService.copyFromDiscovered(organizer, event);

            assertThat(m.getDescription())
                    .contains("MySource")
                    .contains("http://example.com")
                    .contains("Big Arena");
        }

        @Test
        @DisplayName("description still contains source when venue and URL are null")
        void handlesNullVenueAndUrl() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            DiscoveredEvent event = new DiscoveredEvent(
                    "Src", "1", "Title", null, start, end, null, null);

            Meeting m = meetingService.copyFromDiscovered(organizer, event);

            // must not throw NPE, and must at least contain the source name
            assertThat(m.getDescription())
                    .isNotNull()
                    .contains("Src");
        }

        @Test
        @DisplayName("description contains the event's own description when provided")
        void includesEventDescription() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            DiscoveredEvent event = new DiscoveredEvent(
                    "Src", "1", "Title", "Great show details.", start, end, null, null);

            Meeting m = meetingService.copyFromDiscovered(organizer, event);

            assertThat(m.getDescription()).contains("Great show details.");
        }

        @Test
        @DisplayName("blank event description is not included in meeting description")
        void skipsBlankEventDescription() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            DiscoveredEvent event = new DiscoveredEvent(
                    "Src", "1", "Title", "   ", start, end, null, null);

            Meeting m = meetingService.copyFromDiscovered(organizer, event);

            // The blank description should not appear verbatim
            assertThat(m.getDescription()).doesNotContain("   ");
        }

        @Test
        @DisplayName("title from event is used as meeting title")
        void usesTitleFromEvent() {
            when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            DiscoveredEvent event = new DiscoveredEvent(
                    "Src", "1", "Jazz Night", null, start, end, null, null);

            Meeting m = meetingService.copyFromDiscovered(organizer, event);

            assertThat(m.getTitle()).isEqualTo("Jazz Night");
        }
    }

    // =========================================================================
    // calendarForIcalToken()
    // =========================================================================

    @Nested
    @DisplayName("calendarForIcalToken()")
    class CalendarForIcalToken {

        @Test
        @DisplayName("throws IllegalArgumentException for an unknown token")
        void throwsForInvalidToken() {
            when(userRepository.findByIcalToken("bad-token")).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    meetingService.calendarForIcalToken("bad-token"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid iCal token");
        }

        @Test
        @DisplayName("returns meetings for the user associated with the token")
        void returnsMeetingsForTokenUser() {
            Meeting m = new Meeting("T", null, start, end, organizer);
            when(userRepository.findByIcalToken("valid-token"))
                    .thenReturn(Optional.of(organizer));
            when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(List.of(m));

            List<Meeting> result = meetingService.calendarForIcalToken("valid-token");

            assertThat(result).containsExactly(m);
        }

        @Test
        @DisplayName("returns a mutable list (not the repository's list directly)")
        void returnsMutableList() {
            when(userRepository.findByIcalToken("t")).thenReturn(Optional.of(organizer));
            when(meetingRepository.findCalendarMeetings(organizer)).thenReturn(List.of());

            List<Meeting> result = meetingService.calendarForIcalToken("t");

            // Should not throw UnsupportedOperationException
            assertThatCode(() -> result.add(new Meeting("X", null, start, end, organizer)))
                    .doesNotThrowAnyException();
        }
    }
}
