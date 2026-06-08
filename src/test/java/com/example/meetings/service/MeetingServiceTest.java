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

    // ------------------------------------------------------------------ propose()

    @Test
    void propose_savesAndReturns_meeting() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting m = meetingService.propose(organizer, "Standup", null, start, end, List.of());

        assertThat(m.getTitle()).isEqualTo("Standup");
        assertThat(m.getOrganizer()).isEqualTo(organizer);
        verify(meetingRepository).save(any(Meeting.class));
    }

    @Test
    void propose_organizerIsAutoAccepted() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting m = meetingService.propose(organizer, "T", null, start, end, List.of());

        assertThat(m.getParticipants())
                .anyMatch(p -> p.getUser().equals(organizer) && p.getStatus() == InviteStatus.ACCEPTED);
    }

    @Test
    void propose_inviteeReceivesPendingInvite() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(invitee));
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting m = meetingService.propose(organizer, "T", null, start, end, List.of("bob"));

        assertThat(m.getParticipants())
                .anyMatch(p -> p.getUser().equals(invitee) && p.getStatus() == InviteStatus.PENDING);
    }

    @Test
    void propose_rejectsEndBeforeStart() {
        assertThatThrownBy(() ->
                meetingService.propose(organizer, "T", null, end, start, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("End time must be after start time");
    }

    @Test
    void propose_rejectsEndEqualToStart() {
        assertThatThrownBy(() ->
                meetingService.propose(organizer, "T", null, start, start, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void propose_deduplicatesInvitees() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(invitee));
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting m = meetingService.propose(organizer, "T", null, start, end, List.of("bob", "bob"));

        long bobCount = m.getParticipants().stream()
                .filter(p -> p.getUser().equals(invitee)).count();
        assertThat(bobCount).isEqualTo(1);
    }

    @Test
    void propose_organizerNotAddedTwiceIfListedAsInvitee() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting m = meetingService.propose(organizer, "T", null, start, end, List.of("alice"));

        assertThat(m.getParticipants()).hasSize(1);
    }

    @Test
    void propose_throwsForUnknownInvitee() {
        when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                meetingService.propose(organizer, "T", null, start, end, List.of("unknown")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown invitee");
    }

    @Test
    void propose_skipsBlankInviteeNames() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Meeting m = meetingService.propose(organizer, "T", null, start, end, List.of("  ", ""));

        // Only organizer should be in participants (no unknown-user lookups)
        verify(userRepository, never()).findByUsername(any());
        assertThat(m.getParticipants()).hasSize(1);
    }

    // ------------------------------------------------------------------ respond()

    @Test
    void respond_setsParticipantToAccepted() {
        Meeting meeting = new Meeting("T", null, start, end, organizer);
        MeetingParticipant participant = new MeetingParticipant(meeting, invitee, InviteStatus.PENDING);
        when(participantRepository.findByMeetingIdAndUserId(1L, invitee.getId()))
                .thenReturn(Optional.of(participant));

        meetingService.respond(1L, invitee, InviteStatus.ACCEPTED);

        assertThat(participant.getStatus()).isEqualTo(InviteStatus.ACCEPTED);
    }

    @Test
    void respond_setsParticipantToDeclined() {
        Meeting meeting = new Meeting("T", null, start, end, organizer);
        MeetingParticipant participant = new MeetingParticipant(meeting, invitee, InviteStatus.PENDING);
        when(participantRepository.findByMeetingIdAndUserId(1L, invitee.getId()))
                .thenReturn(Optional.of(participant));

        meetingService.respond(1L, invitee, InviteStatus.DECLINED);

        assertThat(participant.getStatus()).isEqualTo(InviteStatus.DECLINED);
    }

    @Test
    void respond_rejectsPendingStatus() {
        assertThatThrownBy(() ->
                meetingService.respond(1L, invitee, InviteStatus.PENDING))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ACCEPTED or DECLINED");
    }

    @Test
    void respond_throwsWhenNoInviteFound() {
        when(participantRepository.findByMeetingIdAndUserId(99L, invitee.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                meetingService.respond(99L, invitee, InviteStatus.ACCEPTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No invite found");
    }

    // ------------------------------------------------------------------ copyFromDiscovered()

    @Test
    void copyFromDiscovered_defaultsEndToTwoHoursWhenNull() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DiscoveredEvent event = new DiscoveredEvent(
                "TestSource", "42", "Concert", null, start, null, null, "Venue");

        Meeting m = meetingService.copyFromDiscovered(organizer, event);

        assertThat(m.getEndTime()).isEqualTo(start.plus(2, ChronoUnit.HOURS));
    }

    @Test
    void copyFromDiscovered_usesProvidedEndTime() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DiscoveredEvent event = new DiscoveredEvent(
                "TestSource", "42", "Concert", null, start, end, null, "Venue");

        Meeting m = meetingService.copyFromDiscovered(organizer, event);

        assertThat(m.getEndTime()).isEqualTo(end);
    }

    @Test
    void copyFromDiscovered_ownerIsAutoAccepted() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DiscoveredEvent event = new DiscoveredEvent(
                "TestSource", "42", "Concert", null, start, end, null, null);

        Meeting m = meetingService.copyFromDiscovered(organizer, event);

        assertThat(m.isConfirmed()).isTrue();
        assertThat(m.getParticipants())
                .anyMatch(p -> p.getUser().equals(organizer) && p.getStatus() == InviteStatus.ACCEPTED);
    }

    @Test
    void copyFromDiscovered_descriptionIncludesSourceAndUrl() {
        when(meetingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        DiscoveredEvent event = new DiscoveredEvent(
                "MySource", "1", "Show", "A great show.", start, end, "http://example.com", "Big Arena");

        Meeting m = meetingService.copyFromDiscovered(organizer, event);

        assertThat(m.getDescription())
                .contains("MySource")
                .contains("http://example.com")
                .contains("Big Arena");
    }

    // ------------------------------------------------------------------ calendarForIcalToken()

    @Test
    void calendarForIcalToken_throwsForInvalidToken() {
        when(userRepository.findByIcalToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                meetingService.calendarForIcalToken("bad-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid iCal token");
    }
}
