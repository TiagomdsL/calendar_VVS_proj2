package com.example.meetings.service;

import com.example.meetings.model.User;
import com.example.meetings.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService and AppUserDetailsService.
 * All collaborators are mocked — no Spring context, no database.
 */
class UserServiceTest {

    // =========================================================================
    // UserService
    // =========================================================================

    @Nested
    @DisplayName("UserService")
    @ExtendWith(MockitoExtension.class)
    class UserServiceTests {

        @Mock UserRepository userRepository;
        @Mock PasswordEncoder passwordEncoder;

        @InjectMocks UserService userService;

        // No @BeforeEach stub — MockitoExtension strict mode fails if a stub is
        // declared but never invoked. Each test sets up only what it actually needs.

        // ── register() ──────────────────────────────────────────────────────

        @Nested
        @DisplayName("register()")
        class Register {

            @Test
            @DisplayName("saves user with encoded password")
            void savesUserWithHashedPassword() {
                when(passwordEncoder.encode("secret")).thenReturn("hashed_secret");
                when(userRepository.existsByUsername("alice")).thenReturn(false);
                when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                User u = userService.register("alice", "alice@example.com", "secret");

                assertThat(u.getPasswordHash()).isEqualTo("hashed_secret");
                assertThat(u.getUsername()).isEqualTo("alice");
                assertThat(u.getEmail()).isEqualTo("alice@example.com");
            }

            @Test
            @DisplayName("raw password is never stored directly — PasswordEncoder.encode() is always called")
            void doesNotStoreRawPassword() {
                when(passwordEncoder.encode("myRawPassword")).thenReturn("$2a$irrelevant");
                when(userRepository.existsByUsername("carol")).thenReturn(false);
                when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                userService.register("carol", "carol@example.com", "myRawPassword");

                // Verifying encode() was called is the correct way to assert the password
                // was not stored raw — checking that the hash "doesn't contain" the raw
                // value is meaningless when the mock controls what encode() returns.
                verify(passwordEncoder).encode("myRawPassword");
            }

            @Test
            @DisplayName("assigns a non-blank iCal token to every new user")
            void assignsIcalToken() {
                when(passwordEncoder.encode(anyString())).thenReturn("hash");
                when(userRepository.existsByUsername("bob")).thenReturn(false);
                when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                User u = userService.register("bob", "bob@example.com", "pw");

                assertThat(u.getIcalToken()).isNotNull().isNotBlank();
            }

            @Test
            @DisplayName("each new user gets a unique iCal token")
            void uniqueIcalTokenPerUser() {
                when(passwordEncoder.encode(anyString())).thenReturn("hash");
                when(userRepository.existsByUsername(anyString())).thenReturn(false);
                when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                User u1 = userService.register("u1", "u1@example.com", "pw");
                User u2 = userService.register("u2", "u2@example.com", "pw");

                assertThat(u1.getIcalToken()).isNotEqualTo(u2.getIcalToken());
            }

            @Test
            @DisplayName("throws IllegalArgumentException when username is already taken")
            void throwsWhenUsernameAlreadyTaken() {
                // encode() is never reached — no stub needed for it
                when(userRepository.existsByUsername("alice")).thenReturn(true);

                assertThatThrownBy(() ->
                        userService.register("alice", "alice@example.com", "pw"))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Username already taken");
            }

            @Test
            @DisplayName("save() is never called when username is duplicate")
            void doesNotSaveWhenDuplicate() {
                when(userRepository.existsByUsername("alice")).thenReturn(true);

                assertThatThrownBy(() ->
                        userService.register("alice", "alice@example.com", "pw"))
                        .isInstanceOf(IllegalArgumentException.class);

                verify(userRepository, never()).save(any());
            }

            @Test
            @DisplayName("PasswordEncoder.encode() is called exactly once per registration")
            void encoderCalledOnce() {
                when(passwordEncoder.encode("pw")).thenReturn("hash");
                when(userRepository.existsByUsername("dave")).thenReturn(false);
                when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

                userService.register("dave", "dave@example.com", "pw");

                verify(passwordEncoder, times(1)).encode("pw");
            }
        }

        // ── requireByUsername() ─────────────────────────────────────────────

        @Nested
        @DisplayName("requireByUsername()")
        class RequireByUsername {

            @Test
            @DisplayName("returns the user when the username exists")
            void returnsUserWhenFound() {
                User alice = new User("alice", "alice@example.com", "hash");
                when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

                User found = userService.requireByUsername("alice");

                assertThat(found).isEqualTo(alice);
            }

            @Test
            @DisplayName("throws IllegalArgumentException when the username does not exist")
            void throwsWhenNotFound() {
                when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> userService.requireByUsername("ghost"))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Unknown user");
            }

            @Test
            @DisplayName("error message includes the unknown username")
            void errorMessageIncludesUsername() {
                when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

                assertThatThrownBy(() -> userService.requireByUsername("ghost"))
                        .hasMessageContaining("ghost");
            }
        }
    }

    // =========================================================================
    // AppUserDetailsService
    // =========================================================================

    @Nested
    @DisplayName("AppUserDetailsService")
    @ExtendWith(MockitoExtension.class)
    class AppUserDetailsServiceTests {

        @Mock UserRepository userRepository;

        @InjectMocks AppUserDetailsService detailsService;

        @Test
        @DisplayName("loadUserByUsername returns UserDetails with correct username")
        void returnsUserDetailsWithCorrectUsername() {
            User alice = new User("alice", "alice@example.com", "encodedHash");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

            UserDetails details = detailsService.loadUserByUsername("alice");

            assertThat(details.getUsername()).isEqualTo("alice");
        }

        @Test
        @DisplayName("loadUserByUsername returns the stored encoded password hash")
        void returnsEncodedPasswordHash() {
            User alice = new User("alice", "alice@example.com", "encodedHash");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

            UserDetails details = detailsService.loadUserByUsername("alice");

            assertThat(details.getPassword()).isEqualTo("encodedHash");
        }

        @Test
        @DisplayName("loadUserByUsername grants ROLE_USER authority")
        void grantsRoleUser() {
            User alice = new User("alice", "alice@example.com", "hash");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

            UserDetails details = detailsService.loadUserByUsername("alice");

            assertThat(details.getAuthorities())
                    .extracting(a -> a.getAuthority())
                    .containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("loadUserByUsername throws UsernameNotFoundException for unknown user")
        void throwsUsernameNotFoundForUnknownUser() {
            when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> detailsService.loadUserByUsername("ghost"))
                    .isInstanceOf(UsernameNotFoundException.class)
                    .hasMessageContaining("ghost");
        }

        @Test
        @DisplayName("loadUserByUsername does not return null")
        void neverReturnsNull() {
            User alice = new User("alice", "alice@example.com", "hash");
            when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

            assertThat(detailsService.loadUserByUsername("alice")).isNotNull();
        }
    }
}