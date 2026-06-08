package com.example.meetings.api;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import com.example.meetings.service.MeetingService;
import com.example.meetings.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REST/HTTP-level integration tests for the Meetings Calendar application.
 *
 * <p>Uses {@link SpringBootTest} with the full application context and an
 * in-memory H2 database (activated via the "test" profile). Each test class
 * is {@link Transactional} so that database state is rolled back after every
 * test method, keeping tests fully isolated.</p>
 *
 * <p>Spring Security's {@code user()} post-processor is used to inject an
 * authenticated principal into MockMvc requests without going through the
 * login form, keeping the auth tests focused on the auth endpoints themselves.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RestApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired UserService userService;
    @Autowired MeetingService meetingService;
    @Autowired UserRepository userRepository;

    @BeforeEach
    void cleanSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // -------------------------------------------------------------------------
    // Shared test fixtures
    // -------------------------------------------------------------------------

    /** Creates a real persisted user via the service (password is encoded). */
    private User createUser(String username) {
        return userService.register(username, username + "@test.com", "password123");
    }

    // =========================================================================
    // 1. AUTHENTICATION ENDPOINTS
    // =========================================================================

    @Nested
    @DisplayName("GET /login")
    class GetLogin {

        @Test
        @DisplayName("returns 200 and renders login form for unauthenticated user")
        void loginPage_isPublic() throws Exception {
            mvc.perform(get("/login"))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        }
    }

    @Nested
    @DisplayName("GET /register")
    class GetRegister {

        @Test
        @DisplayName("returns 200 and renders registration form")
        void registerPage_isPublic() throws Exception {
            mvc.perform(get("/register"))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        }
    }

    @Nested
    @DisplayName("POST /register")
    class PostRegister {

        @Test
        @DisplayName("redirects to /login?registered on successful registration")
        void register_success_redirectsToLogin() throws Exception {
            mvc.perform(post("/register")
                    .with(csrf())
                    .param("username", "alice")
                    .param("email",    "alice@example.com")
                    .param("password", "securePass1"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/login?registered"));
        }

        @Test
        @DisplayName("returns 200 with error message when username is already taken")
        void register_duplicateUsername_showsError() throws Exception {
            createUser("bob");

            mvc.perform(post("/register")
                    .with(csrf())
                    .param("username", "bob")
                    .param("email",    "bob2@example.com")
                    .param("password", "securePass1"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Username already taken")));
        }

        @Test
        @DisplayName("re-renders form preserving submitted username and email on error")
        void register_duplicateUsername_preservesFormValues() throws Exception {
            createUser("carol");

            mvc.perform(post("/register")
                    .with(csrf())
                    .param("username", "carol")
                    .param("email",    "carol2@example.com")
                    .param("password", "pass"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("carol")))
               .andExpect(content().string(containsString("carol2@example.com")));
        }
    }

    // =========================================================================
    // 2. REDIRECT / ROOT ENDPOINT
    // =========================================================================

    @Nested
    @DisplayName("GET /")
    class GetRoot {
    	
    	@Test
    	@WithAnonymousUser
    	@DisplayName("/calendar unauthenticated redirects to /login")
    	void calendar_unauthenticated_redirectsToLogin() throws Exception {
    	    mvc.perform(get("/calendar"))
    	            .andExpect(status().is3xxRedirection())
    	            .andExpect(redirectedUrlPattern("**/login"));
    	}
    	
        @Test
        @DisplayName("redirects authenticated users to /calendar")
        void root_authenticated_redirectsToCalendar() throws Exception {
            User u = createUser("dave");

            mvc.perform(get("/")
                    .with(user(u.getUsername()).password("password123").roles("USER")))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/calendar"));
        }
    }

    // =========================================================================
    // 3. CALENDAR ENDPOINT
    // =========================================================================

    @Nested
    @DisplayName("GET /calendar")
    class GetCalendar {

        @Test
        @DisplayName("returns 302 → /login for unauthenticated requests")
        void calendar_requiresAuthentication() throws Exception {
            mvc.perform(get("/calendar"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrlPattern("**/login"));
        }

        @Test
        @DisplayName("returns 200 for authenticated user")
        void calendar_authenticated_returns200() throws Exception {
            User u = createUser("eve");

            mvc.perform(get("/calendar")
                    .with(user(u.getUsername()).password("x").roles("USER")))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        }

        @Test
        @DisplayName("calendar page contains the user's iCal token URL")
        void calendar_containsIcalLink() throws Exception {
            User u = createUser("frank");

            mvc.perform(get("/calendar")
                    .with(user(u.getUsername()).password("x").roles("USER")))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString(u.getIcalToken())));
        }
    }

    // =========================================================================
    // 4. MEETING PROPOSAL ENDPOINTS
    // =========================================================================

    @Nested
    @DisplayName("GET /meetings/new")
    class GetMeetingsNew {

        @Test
        @DisplayName("returns 302 → /login for unauthenticated requests")
        void proposeForm_requiresAuthentication() throws Exception {
            mvc.perform(get("/meetings/new"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrlPattern("**/login"));
        }

        @Test
        @DisplayName("returns 200 and propose form for authenticated user")
        void proposeForm_authenticated_returns200() throws Exception {
            User u = createUser("grace");

            mvc.perform(get("/meetings/new")
                    .with(user(u.getUsername()).password("x").roles("USER")))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
        }
    }

    @Nested
    @DisplayName("POST /meetings/new")
    class PostMeetingsNew {

        @Test
        @DisplayName("returns 302 → /calendar on successful meeting proposal (no invitees)")
        void propose_noInvitees_redirectsToCalendar() throws Exception {
            User organizer = createUser("henry");

            mvc.perform(post("/meetings/new")
                    .with(csrf())
                    .with(user(organizer.getUsername()).password("x").roles("USER"))
                    .param("title", "Team Sync")
                    .param("start", "2030-09-01T10:00")
                    .param("end",   "2030-09-01T11:00"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/calendar"));
        }

        @Test
        @DisplayName("returns 302 → /calendar when inviting an existing user")
        void propose_withValidInvitee_redirectsToCalendar() throws Exception {
            User organizer = createUser("iris");
            User invitee   = createUser("jack");

            mvc.perform(post("/meetings/new")
                    .with(csrf())
                    .with(user(organizer.getUsername()).password("x").roles("USER"))
                    .param("title",    "Design Review")
                    .param("start",    "2030-10-05T14:00")
                    .param("end",      "2030-10-05T15:00")
                    .param("invitees", invitee.getUsername()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/calendar"));
        }

        @Test
        @DisplayName("returns 200 with error when inviting an unknown username")
        void propose_unknownInvitee_showsError() throws Exception {
            User organizer = createUser("kate");

            mvc.perform(post("/meetings/new")
                    .with(csrf())
                    .with(user(organizer.getUsername()).password("x").roles("USER"))
                    .param("title",    "Meeting with Ghost")
                    .param("start",    "2030-10-01T09:00")
                    .param("end",      "2030-10-01T10:00")
                    .param("invitees", "ghost_user_xyz"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Unknown invitee")));
        }

        @Test
        @DisplayName("returns 200 with error when end time is before start time")
        void propose_endBeforeStart_showsError() throws Exception {
            User organizer = createUser("leo");

            mvc.perform(post("/meetings/new")
                    .with(csrf())
                    .with(user(organizer.getUsername()).password("x").roles("USER"))
                    .param("title", "Bad Times")
                    .param("start", "2030-11-01T10:00")
                    .param("end",   "2030-11-01T09:00"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("End time must be after start time")));
        }

        @Test
        @DisplayName("re-renders form with submitted values preserved on error")
        void propose_onError_preservesFormValues() throws Exception {
            User organizer = createUser("mia");

            mvc.perform(post("/meetings/new")
                    .with(csrf())
                    .with(user(organizer.getUsername()).password("x").roles("USER"))
                    .param("title",       "Preserved Title")
                    .param("description", "Preserved Description")
                    .param("start",       "2030-11-01T10:00")
                    .param("end",         "2030-11-01T09:00"))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Preserved Title")))
               .andExpect(content().string(containsString("Preserved Description")));
        }

        @Test
        @DisplayName("returns 302 → /calendar for a meeting with multiple comma-separated invitees")
        void propose_multipleInvitees_succeeds() throws Exception {
            User organizer = createUser("nina");
            User inv1      = createUser("oscar");
            User inv2      = createUser("petra");

            mvc.perform(post("/meetings/new")
                    .with(csrf())
                    .with(user(organizer.getUsername()).password("x").roles("USER"))
                    .param("title",    "All Hands")
                    .param("start",    "2030-12-01T09:00")
                    .param("end",      "2030-12-01T10:00")
                    .param("invitees", inv1.getUsername() + "," + inv2.getUsername()))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/calendar"));
        }

        @Test
        @DisplayName("unauthenticated POST to /meetings/new is redirected to login")
        void propose_unauthenticated_redirectsToLogin() throws Exception {
            mvc.perform(post("/meetings/new")
                    .with(csrf())
                    .param("title", "Sneaky")
                    .param("start", "2030-09-01T10:00")
                    .param("end",   "2030-09-01T11:00"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrlPattern("**/login"));
        }
    }

    // =========================================================================
    // 5. MEETING RESPONSE ENDPOINT
    // =========================================================================

    @Nested
    @DisplayName("POST /meetings/{id}/respond")
    class PostMeetingRespond {

        /**
         * Helper: organizer proposes a meeting, returns meeting ID by querying the
         * service directly. We store the invitee as the user whose response we test.
         */
        private long proposeMeeting(User organizer, User invitee) {
            Instant start = Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
            Instant end   = start.plus(1, ChronoUnit.HOURS);
            return meetingService
                    .propose(organizer, "Test Meeting", null, start, end,
                             java.util.List.of(invitee.getUsername()))
                    .getId();
        }

        @Test
        @DisplayName("invitee can accept their invite — redirects to /calendar")
        void respond_accept_redirectsToCalendar() throws Exception {
            User organizer = createUser("quinn");
            User invitee   = createUser("rachel");
            long meetingId = proposeMeeting(organizer, invitee);

            mvc.perform(post("/meetings/{id}/respond", meetingId)
                    .with(csrf())
                    .with(user(invitee.getUsername()).password("x").roles("USER"))
                    .param("action", "accept"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/calendar"));
        }

        @Test
        @DisplayName("invitee can decline their invite — redirects to /calendar")
        void respond_decline_redirectsToCalendar() throws Exception {
            User organizer = createUser("sam");
            User invitee   = createUser("tina");
            long meetingId = proposeMeeting(organizer, invitee);

            mvc.perform(post("/meetings/{id}/respond", meetingId)
                    .with(csrf())
                    .with(user(invitee.getUsername()).password("x").roles("USER"))
                    .param("action", "decline"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/calendar"));
        }

        @Test
        @DisplayName("unauthenticated respond request is redirected to /login")
        void respond_unauthenticated_redirectsToLogin() throws Exception {
            User organizer = createUser("uma");
            User invitee   = createUser("victor");
            long meetingId = proposeMeeting(organizer, invitee);

            mvc.perform(post("/meetings/{id}/respond", meetingId)
                    .with(csrf())
                    .param("action", "accept"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrlPattern("**/login"));
        }

        @Test
        @DisplayName("user who is not an invitee gets an error (no invite found)")
        void respond_nonInvitee_throwsError() {
            User organizer  = createUser("wendy");
            User invitee    = createUser("xander");
            User stranger   = createUser("yasmine");
            long meetingId  = proposeMeeting(organizer, invitee);
 
            // The controller lets IllegalArgumentException propagate — Spring wraps it
            // in a ServletException and MockMvc rethrows it instead of returning a
            // response object. We catch that, unwrap it, and verify the root cause.
            Exception thrown = assertThrows(Exception.class, () ->
                mvc.perform(post("/meetings/{id}/respond", meetingId)
                        .with(csrf())
                        .with(user(stranger.getUsername()).password("x").roles("USER"))
                        .param("action", "accept"))
            );
 
            Throwable root = thrown;
            while (root.getCause() != null) root = root.getCause();
            assertInstanceOf(IllegalArgumentException.class, root);
            assertThat(root.getMessage()).contains("No invite found");
        }
    }

    // =========================================================================
    // 6. ICAL FEED ENDPOINT
    // =========================================================================

    @Nested
    @DisplayName("GET /ical/{token}.ics")
    class GetIcal {

        @Test
        @DisplayName("returns 200 with text/calendar content-type for a valid token")
        void ical_validToken_returns200WithCalendarContentType() throws Exception {
            User u = createUser("zara");

            mvc.perform(get("/ical/{token}.ics", u.getIcalToken()))
               .andExpect(status().isOk())
               .andExpect(content().contentTypeCompatibleWith("text/calendar"));
        }

        @Test
        @DisplayName("response body starts with BEGIN:VCALENDAR")
        void ical_validToken_bodyStartsWithVCalendar() throws Exception {
            User u = createUser("aaron");

            mvc.perform(get("/ical/{token}.ics", u.getIcalToken()))
               .andExpect(status().isOk())
               .andExpect(content().string(startsWith("BEGIN:VCALENDAR")));
        }

        @Test
        @DisplayName("response body ends with END:VCALENDAR")
        void ical_validToken_bodyEndsWithVCalendar() throws Exception {
            User u = createUser("brenda");

            mvc.perform(get("/ical/{token}.ics", u.getIcalToken()))
               .andExpect(status().isOk())
               .andExpect(content().string(endsWith("END:VCALENDAR\r\n")));
        }

        @Test
        @DisplayName("returns 404 for an unknown token")
        void ical_unknownToken_returns404() throws Exception {
            mvc.perform(get("/ical/{token}.ics", "totally-invalid-token-000"))
               .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("ical feed is accessible without authentication (public endpoint)")
        void ical_noAuthRequired() throws Exception {
            // No .with(user(...)) — purely unauthenticated.
            User u = createUser("carlos");

            mvc.perform(get("/ical/{token}.ics", u.getIcalToken()))
               .andExpect(status().isOk());
        }

        @Test
        @DisplayName("ical feed includes a meeting the user organised")
        void ical_includesOrganisedMeeting() throws Exception {
            User u = createUser("diana");
            Instant start = Instant.parse("2031-03-10T14:00:00Z");
            Instant end   = Instant.parse("2031-03-10T15:00:00Z");
            meetingService.propose(u, "Sprint Planning", null, start, end, java.util.List.of());

            mvc.perform(get("/ical/{token}.ics", u.getIcalToken()))
               .andExpect(status().isOk())
               .andExpect(content().string(containsString("Sprint Planning")))
               .andExpect(content().string(containsString("VEVENT")));
        }

        @Test
        @DisplayName("Content-Disposition header suggests inline .ics filename")
        void ical_contentDispositionHeader() throws Exception {
            User u = createUser("ethan");

            mvc.perform(get("/ical/{token}.ics", u.getIcalToken()))
               .andExpect(status().isOk())
               .andExpect(header().string("Content-Disposition",
                       containsString("filename=\"meetings.ics\"")));
        }
    }

    // =========================================================================
    // 7. SECURITY — CSRF PROTECTION
    // =========================================================================

    @Nested
    @DisplayName("CSRF protection")
    class CsrfProtection {

        @Test
        @DisplayName("POST /register without CSRF token returns 403")
        void register_missingCsrf_returns403() throws Exception {
            // No .with(csrf()) — token deliberately omitted.
            mvc.perform(post("/register")
                    .param("username", "frank2")
                    .param("email",    "frank2@example.com")
                    .param("password", "pass"))
               .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("POST /meetings/new without CSRF token returns 403")
        void proposeMeeting_missingCsrf_returns403() throws Exception {
            User u = createUser("gina");

            mvc.perform(post("/meetings/new")
                    .with(user(u.getUsername()).password("x").roles("USER"))
                    .param("title", "No CSRF Meeting")
                    .param("start", "2030-09-01T10:00")
                    .param("end",   "2030-09-01T11:00"))
               .andExpect(status().isForbidden());
        }
    }

    // =========================================================================
    // 8. STATIC & UNKNOWN ROUTES
    // =========================================================================

    @Nested
    @DisplayName("Static assets and unknown routes")
    class StaticAndUnknown {

        @Test
        @DisplayName("CSS static asset is publicly accessible")
        void cssAsset_isPublic() throws Exception {
            mvc.perform(get("/css/app.css"))
               .andExpect(status().isOk());
        }

        @Test
        @DisplayName("unknown route for authenticated user returns 404 or error, not login redirect")
        void unknownRoute_authenticated_isNotLoginRedirect() throws Exception {
            User u = createUser("hank");

            mvc.perform(get("/this/route/does/not/exist")
                    .with(user(u.getUsername()).password("x").roles("USER")))
               .andExpect(status().isNotFound());
        }
    }
}
