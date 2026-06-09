package com.example.meetings.repository;

import com.example.meetings.model.InviteStatus;
import com.example.meetings.model.Meeting;
import com.example.meetings.model.MeetingParticipant;
import com.example.meetings.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests against the concrete H2 in-memory database.
 * Uses {@code @DataJpaTest} which loads only JPA slices, not the full application context.
 */
@DataJpaTest
@ActiveProfiles("test")
class MeetingRepositoryIntegrationTest {

    @Autowired MeetingRepository meetingRepository;
    @Autowired MeetingParticipantRepository participantRepository;
    @Autowired UserRepository userRepository;

    private User alice;
    private User bob;
    private Instant t0;

    @BeforeEach
    void setUp() {
        alice = userRepository.save(new User("alice", "alice@example.com", "hash"));
        bob   = userRepository.save(new User("bob",   "bob@example.com",   "hash"));
        t0    = Instant.parse("2025-09-01T10:00:00Z");
    }

    // ------------------------------------------------------------------ findCalendarMeetings

    /**
     * Verifies that a meeting organised by the user appears in their calendar,
     * even when they are also listed as a participant with {@code ACCEPTED} status.
     */
    @Test
    void findCalendarMeetings_returnsOrganizerMeeting() {
        Meeting m = meetingRepository.save(new Meeting("Standup", null, t0, t0.plus(1, ChronoUnit.HOURS), alice));
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        meetingRepository.save(m);

        List<Meeting> result = meetingRepository.findCalendarMeetings(alice);
        assertThat(result).extracting(Meeting::getTitle).contains("Standup");
    }

    /**
     * Verifies that a meeting appears in the calendar of an invited participant
     * whose invite status is {@code ACCEPTED}.
     */
    @Test
    void findCalendarMeetings_includesAcceptedParticipant() {
        Meeting m = meetingRepository.save(new Meeting("Team Sync", null, t0, t0.plus(1, ChronoUnit.HOURS), alice));
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob,   InviteStatus.ACCEPTED));
        meetingRepository.save(m);

        List<Meeting> result = meetingRepository.findCalendarMeetings(bob);
        assertThat(result).hasSize(1);
    }

    /**
     * Verifies that a meeting appears in the calendar of an invited participant
     * whose invite is still {@code PENDING} (not yet responded to).
     */
    @Test
    void findCalendarMeetings_includesPendingParticipant() {
        Meeting m = meetingRepository.save(new Meeting("Pending Meeting", null, t0, t0.plus(1, ChronoUnit.HOURS), alice));
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob,   InviteStatus.PENDING));
        meetingRepository.save(m);

        List<Meeting> result = meetingRepository.findCalendarMeetings(bob);
        assertThat(result).hasSize(1);
    }

    /**
     * Verifies that a meeting is hidden from the calendar of a participant
     * who has {@code DECLINED} the invite.
     */
    @Test
    void findCalendarMeetings_excludesDeclinedParticipant() {
        Meeting m = meetingRepository.save(new Meeting("Declined Meeting", null, t0, t0.plus(1, ChronoUnit.HOURS), alice));
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob,   InviteStatus.DECLINED));
        meetingRepository.save(m);

        List<Meeting> result = meetingRepository.findCalendarMeetings(bob);
        assertThat(result).isEmpty();
    }

    /**
     * Verifies that a meeting is not duplicated in the organiser's calendar
     * when the organiser is also stored as an {@code ACCEPTED} participant.
     */
    @Test
    void findCalendarMeetings_noDuplicatesWhenOrganizerAlsoParticipant() {
        Meeting m = meetingRepository.save(new Meeting("Solo", null, t0, t0.plus(1, ChronoUnit.HOURS), alice));
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        meetingRepository.save(m);

        List<Meeting> result = meetingRepository.findCalendarMeetings(alice);
        assertThat(result).hasSize(1);
    }

    /**
     * Verifies that calendar meetings are returned ordered by start time ascending,
     * so earlier meetings appear before later ones.
     */
    @Test
    void findCalendarMeetings_isSortedByStartTimeAscending() {
        Instant t1 = t0.plus(2, ChronoUnit.HOURS);
        Instant t2 = t0.plus(4, ChronoUnit.HOURS);
        Meeting later  = meetingRepository.save(new Meeting("Later",  null, t1, t1.plus(1, ChronoUnit.HOURS), alice));
        Meeting earlier = meetingRepository.save(new Meeting("Earlier", null, t0, t0.plus(1, ChronoUnit.HOURS), alice));
        later.addParticipant(new MeetingParticipant(later,   alice, InviteStatus.ACCEPTED));
        earlier.addParticipant(new MeetingParticipant(earlier, alice, InviteStatus.ACCEPTED));
        meetingRepository.save(later);
        meetingRepository.save(earlier);

        List<Meeting> result = meetingRepository.findCalendarMeetings(alice);
        assertThat(result.get(0).getTitle()).isEqualTo("Earlier");
        assertThat(result.get(1).getTitle()).isEqualTo("Later");
    }

    // ------------------------------------------------------------------ findOverlapping

    /**
     * Verifies that an existing meeting is detected as a conflict when a new
     * proposed window exactly matches its start and end times.
     */
    @Test
    void findOverlapping_detectsFullOverlap() {
        Meeting m = meetingRepository.save(new Meeting("Conflict", null, t0, t0.plus(2, ChronoUnit.HOURS), alice));
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        meetingRepository.save(m);

        // New window: same as existing
        List<Meeting> result = meetingRepository.findOverlapping(alice, t0, t0.plus(2, ChronoUnit.HOURS));
        assertThat(result).hasSize(1);
    }

    /**
     * Verifies that an overlap is detected when the proposed window starts before
     * an existing meeting but ends inside it (head overlap).
     */
    @Test
    void findOverlapping_detectsPartialOverlapAtStart() {
        Meeting m = meetingRepository.save(new Meeting("Conflict", null, t0, t0.plus(2, ChronoUnit.HOURS), alice));
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        meetingRepository.save(m);

        // Query window starts before and ends in the middle
        List<Meeting> result = meetingRepository.findOverlapping(
                alice,
                t0.minus(30, ChronoUnit.MINUTES),
                t0.plus(30, ChronoUnit.MINUTES));
        assertThat(result).hasSize(1);
    }

    /**
     * Verifies that an overlap is detected when the proposed window starts inside
     * an existing meeting and ends after it (tail overlap).
     */
    @Test
    void findOverlapping_detectsPartialOverlapAtEnd() {
        Meeting m = meetingRepository.save(new Meeting("Conflict", null, t0, t0.plus(2, ChronoUnit.HOURS), alice));
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        meetingRepository.save(m);

        List<Meeting> result = meetingRepository.findOverlapping(
                alice,
                t0.plus(90, ChronoUnit.MINUTES),
                t0.plus(3, ChronoUnit.HOURS));
        assertThat(result).hasSize(1);
    }

    /**
     * Verifies that two back-to-back meetings are not considered overlapping:
     * a new meeting that starts exactly when an existing one ends is allowed.
     */
    @Test
    void findOverlapping_noConflictWhenAdjacentAfter() {
        Instant end = t0.plus(1, ChronoUnit.HOURS);
        Meeting m = meetingRepository.save(new Meeting("Meeting", null, t0, end, alice));
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        meetingRepository.save(m);

        // New meeting starts exactly when old one ends — should not conflict
        List<Meeting> result = meetingRepository.findOverlapping(alice, end, end.plus(1, ChronoUnit.HOURS));
        assertThat(result).isEmpty();
    }

    /**
     * Verifies that a meeting a user has {@code DECLINED} is not counted as a
     * scheduling conflict when checking their availability.
     */
    @Test
    void findOverlapping_excludesDeclinedMeetings() {
        Meeting m = meetingRepository.save(new Meeting("Declined", null, t0, t0.plus(1, ChronoUnit.HOURS), alice));
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob,   InviteStatus.DECLINED));
        meetingRepository.save(m);

        List<Meeting> result = meetingRepository.findOverlapping(bob, t0, t0.plus(1, ChronoUnit.HOURS));
        assertThat(result).isEmpty();
    }

    // ------------------------------------------------------------------ UserRepository

    /**
     * Verifies that {@code findByUsername} returns the matching user with
     * the correct email when the username exists in the database.
     */
    @Test
    void findByUsername_returnsUser() {
        Optional<User> found = userRepository.findByUsername("alice");
        assertThat(found).isPresent().get().extracting(User::getEmail).isEqualTo("alice@example.com");
    }

    /**
     * Verifies that {@code findByUsername} returns an empty {@code Optional}
     * when no user with the given username exists.
     */
    @Test
    void findByUsername_emptyWhenNotFound() {
        assertThat(userRepository.findByUsername("nobody")).isEmpty();
    }

    /**
     * Verifies that a user can be looked up by their unique iCal token,
     * confirming the token-to-user mapping is persisted correctly.
     */
    @Test
    void findByIcalToken_returnsUser() {
        String token = alice.getIcalToken();
        Optional<User> found = userRepository.findByIcalToken(token);
        assertThat(found).isPresent().get().extracting(User::getUsername).isEqualTo("alice");
    }

    /**
     * Verifies that {@code existsByUsername} returns {@code true}
     * for a username that is present in the database.
     */
    @Test
    void existsByUsername_trueWhenExists() {
        assertThat(userRepository.existsByUsername("alice")).isTrue();
    }

    /**
     * Verifies that {@code existsByUsername} returns {@code false}
     * for a username that does not exist in the database.
     */
    @Test
    void existsByUsername_falseWhenNotExists() {
        assertThat(userRepository.existsByUsername("ghost")).isFalse();
    }

    // ------------------------------------------------------------------ MeetingParticipantRepository

    /**
     * Verifies that {@code findByUserAndStatus} returns only the participant
     * records matching the given user and {@code PENDING} status.
     */
    @Test
    void findByUserAndStatus_returnsPendingInvites() {
        Meeting m = meetingRepository.save(new Meeting("T", null, t0, t0.plus(1, ChronoUnit.HOURS), alice));
        m.addParticipant(new MeetingParticipant(m, alice, InviteStatus.ACCEPTED));
        m.addParticipant(new MeetingParticipant(m, bob,   InviteStatus.PENDING));
        meetingRepository.save(m);

        List<MeetingParticipant> pending = participantRepository.findByUserAndStatus(bob, InviteStatus.PENDING);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getUser().getUsername()).isEqualTo("bob");
    }

    /**
     * Verifies that a specific participant record can be retrieved by the
     * combination of meeting ID and user ID, and that the status is correct.
     */
    @Test
    void findByMeetingIdAndUserId_returnsParticipant() {
        Meeting m = meetingRepository.save(new Meeting("T", null, t0, t0.plus(1, ChronoUnit.HOURS), alice));
        m.addParticipant(new MeetingParticipant(m, bob, InviteStatus.PENDING));
        meetingRepository.save(m);

        Optional<MeetingParticipant> p = participantRepository.findByMeetingIdAndUserId(m.getId(), bob.getId());
        assertThat(p).isPresent();
        assertThat(p.get().getStatus()).isEqualTo(InviteStatus.PENDING);
    }
}