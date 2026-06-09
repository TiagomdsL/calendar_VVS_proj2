package com.example.meetings.discover;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the 3rd-party provider adapters.
 * Uses WireMock to simulate the external APIs without real network calls.
 */
class ProviderIntegrationTest {

    static WireMockServer wireMock;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void resetStubs() {
        wireMock.resetAll();
    }

    // ============================================================ TicketmasterProvider

    /**
     * Verifies that a well-formed Ticketmaster response is correctly parsed into
     * a {@link DiscoveredEvent} with the expected title, venue, and source label.
     * Also confirms that the {@code keyword} query parameter carries the search term
     * and that {@code apikey} and {@code countryCode} are sent to the API.
     */
    @Test
    void ticketmaster_parsesEventList() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/discovery/v2/events.json"))
                .withQueryParam("keyword",     equalTo("rock"))
                .withQueryParam("apikey",      equalTo("test-key"))
                .withQueryParam("countryCode", equalTo("PT"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "_embedded": {
                                    "events": [{
                                      "id": "TM1",
                                      "name": "Rock Concert",
                                      "url": "http://ticketmaster.com/TM1",
                                      "dates": { "start": { "dateTime": "2025-11-01T20:00:00Z" } },
                                      "_embedded": { "venues": [{"name": "Altice Arena"}] }
                                    }]
                                  }
                                }""")));

        TicketmasterProvider provider = new TicketmasterProvider("test-key", "PT");
        injectBaseUrl(provider, "http", wireMock.port());

        List<DiscoveredEvent> results = provider.search("rock");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Rock Concert");
        assertThat(results.get(0).venue()).isEqualTo("Altice Arena");
        assertThat(results.get(0).source()).isEqualTo("Ticketmaster");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/discovery/v2/events.json"))
                .withQueryParam("keyword", equalTo("rock")));
    }

    /**
     * Verifies that an empty list is returned when the Ticketmaster response
     * contains no {@code _embedded} block (i.e. no events matched the query).
     * Confirms the request is still sent with the correct {@code keyword} param.
     */
    @Test
    void ticketmaster_returnsEmptyWhenNoEmbedded() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/discovery/v2/events.json"))
                .withQueryParam("keyword", equalTo("nothing"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("{}")));

        TicketmasterProvider provider = new TicketmasterProvider("test-key", "PT");
        injectBaseUrl(provider, "http", wireMock.port());

        assertThat(provider.search("nothing")).isEmpty();
        wireMock.verify(getRequestedFor(urlPathEqualTo("/discovery/v2/events.json"))
                .withQueryParam("keyword", equalTo("nothing")));
    }

    /**
     * Verifies that a 500 error from the Ticketmaster API is swallowed gracefully
     * and results in an empty list rather than a thrown exception.
     */
    @Test
    void ticketmaster_returnsEmptyOnServerError() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/discovery/v2/events.json"))
                .withQueryParam("keyword", equalTo("anything"))
                .willReturn(aResponse().withStatus(500)));

        TicketmasterProvider provider = new TicketmasterProvider("test-key", "PT");
        injectBaseUrl(provider, "http", wireMock.port());

        assertThat(provider.search("anything")).isEmpty();
    }

    /**
     * Verifies that an event whose {@code dates.start} object lacks a
     * {@code dateTime} field is silently skipped and not included in results.
     */
    @Test
    void ticketmaster_skipsEventWithNoDateTime() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/discovery/v2/events.json"))
                .withQueryParam("keyword", equalTo("tba"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "_embedded": {
                                    "events": [{
                                      "id": "TM2",
                                      "name": "TBA Event",
                                      "dates": { "start": {} }
                                    }]
                                  }
                                }""")));

        TicketmasterProvider provider = new TicketmasterProvider("test-key", "PT");
        injectBaseUrl(provider, "http", wireMock.port());

        assertThat(provider.search("tba")).isEmpty();
    }

    /**
     * Verifies that a provider constructed with an empty API key reports itself
     * as not configured and returns an empty list without making any HTTP call.
     */
    @Test
    void ticketmaster_notConfiguredWhenNoApiKey() {
        TicketmasterProvider provider = new TicketmasterProvider("", "PT");
        assertThat(provider.isConfigured()).isFalse();
        assertThat(provider.search("q")).isEmpty();
        wireMock.verify(0, anyRequestedFor(anyUrl()));
    }

    // ============================================================ SeatGeekProvider

    /**
     * Verifies that a well-formed SeatGeek response is correctly parsed into
     * a {@link DiscoveredEvent} with the expected title, venue, and source label.
     * Also confirms that the search term is sent as the {@code q} parameter and
     * that {@code client_id} is included in the request.
     */
    @Test
    void seatgeek_parsesEventList() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
                .withQueryParam("q",         equalTo("jazz"))
                .withQueryParam("client_id", equalTo("my-client-id"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "events": [{
                                    "id": 99,
                                    "title": "Jazz Night",
                                    "short_title": "Jazz",
                                    "datetime_utc": "2025-12-01T21:00:00",
                                    "url": "http://seatgeek.com/99",
                                    "venue": { "name": "Blue Note" }
                                  }]
                                }""")));

        SeatGeekProvider provider = new SeatGeekProvider("my-client-id");
        injectSeatGeekBaseUrl(provider, "http", wireMock.port());

        List<DiscoveredEvent> results = provider.search("jazz");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Jazz Night");
        assertThat(results.get(0).venue()).isEqualTo("Blue Note");
        assertThat(results.get(0).source()).isEqualTo("SeatGeek");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/2/events"))
                .withQueryParam("q", equalTo("jazz")));
    }

    /**
     * Verifies that a SeatGeek event missing the {@code datetime_utc} field
     * is silently skipped and not included in the returned list.
     * Confirms the {@code q} parameter still carries the search term.
     */
    @Test
    void seatgeek_returnsEmptyWhenNullDatetime() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
                .withQueryParam("q", equalTo("event"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                  "events": [{
                                    "id": 100,
                                    "title": "Event"
                                  }]
                                }""")));

        SeatGeekProvider provider = new SeatGeekProvider("my-client-id");
        injectSeatGeekBaseUrl(provider, "http", wireMock.port());

        assertThat(provider.search("event")).isEmpty();
        wireMock.verify(getRequestedFor(urlPathEqualTo("/2/events"))
                .withQueryParam("q", equalTo("event")));
    }

    /**
     * Verifies that a provider constructed with an empty client ID reports itself
     * as not configured and returns an empty list without making any HTTP call.
     */
    @Test
    void seatgeek_notConfiguredWhenNoClientId() {
        SeatGeekProvider provider = new SeatGeekProvider("");
        assertThat(provider.isConfigured()).isFalse();
        assertThat(provider.search("q")).isEmpty();
        wireMock.verify(0, anyRequestedFor(anyUrl()));
    }

    /**
     * Verifies that a 503 error from the SeatGeek API is swallowed gracefully
     * and results in an empty list rather than a thrown exception.
     */
    @Test
    void seatgeek_returnsEmptyOnServerError() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/2/events"))
                .withQueryParam("q", equalTo("anything"))
                .willReturn(aResponse().withStatus(503)));

        SeatGeekProvider provider = new SeatGeekProvider("my-client-id");
        injectSeatGeekBaseUrl(provider, "http", wireMock.port());

        assertThat(provider.search("anything")).isEmpty();
    }

    // ============================================================ AgendaLxProvider

    /**
     * Verifies that {@code AgendaLxProvider} always reports itself as configured
     * since it requires no API key.
     */
    @Test
    void agendaLx_isAlwaysConfigured() {
        AgendaLxProvider provider = new AgendaLxProvider();
        assertThat(provider.isConfigured()).isTrue();
    }

    /**
     * Verifies that a 500 error from the AgendaLx API is swallowed gracefully
     * and results in an empty list rather than a thrown exception.
     */
    @Test
    void agendaLx_returnsEmptyOnServerError() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .withQueryParam("search", equalTo("fado"))
                .willReturn(aResponse().withStatus(500)));

        AgendaLxProvider provider = new AgendaLxProvider();
        injectAgendaLxBaseUrl(provider, "http", wireMock.port());

        assertThat(provider.search("fado")).isEmpty();
    }

    /**
     * Verifies that an event with at least one future occurrence date is parsed
     * correctly and included in the results with the right title and venue.
     * Also confirms the search term is forwarded as the {@code search} query parameter
     * and that {@code per_page} is included in the request.
     */
    @Test
    void agendaLx_parsesEventWithFutureDate() throws Exception {
        String futureDate = java.time.LocalDate.now().plusDays(10).toString();
        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .withQueryParam("search",   equalTo("fado"))
                .withQueryParam("per_page", equalTo("20"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format("""
                                [{
                                  "id": 1,
                                  "title": { "rendered": "Fado Show" },
                                  "occurences": ["%s"],
                                  "string_times": "21h30",
                                  "link": "http://agendalx.pt/1",
                                  "venue": { "1": { "name": "Casa de Fado" } }
                                }]""", futureDate))));

        AgendaLxProvider provider = new AgendaLxProvider();
        injectAgendaLxBaseUrl(provider, "http", wireMock.port());

        List<DiscoveredEvent> results = provider.search("fado");

        assertThat(results).hasSize(1);
        assertThat(results.get(0).title()).isEqualTo("Fado Show");
        assertThat(results.get(0).venue()).isEqualTo("Casa de Fado");
        wireMock.verify(getRequestedFor(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .withQueryParam("search", equalTo("fado")));
    }

    /**
     * Verifies that an event whose only occurrence dates are in the past
     * is silently skipped and not included in the returned results.
     */
    @Test
    void agendaLx_skipsEventWithOnlyPastDates() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/wp-json/agendalx/v1/events"))
                .withQueryParam("search", equalTo("old"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [{
                                  "id": 2,
                                  "title": { "rendered": "Old Show" },
                                  "occurences": ["2000-01-01"],
                                  "string_times": "20h00",
                                  "link": "http://agendalx.pt/2"
                                }]""")));

        AgendaLxProvider provider = new AgendaLxProvider();
        injectAgendaLxBaseUrl(provider, "http", wireMock.port());

        assertThat(provider.search("old")).isEmpty();
    }

    // ============================================================ Reflection helpers
    // We inject the WireMock base URL into the RestClient via reflection so we don't
    // need to change the providers' constructors for testing.

    private void injectBaseUrl(TicketmasterProvider provider, String scheme, int port) throws Exception {
        Field f = TicketmasterProvider.class.getDeclaredField("http");
        f.setAccessible(true);
        var client = org.springframework.web.client.RestClient.builder()
                .baseUrl(scheme + "://localhost:" + port + "/discovery/v2")
                .build();
        f.set(provider, client);
    }

    private void injectSeatGeekBaseUrl(SeatGeekProvider provider, String scheme, int port) throws Exception {
        Field f = SeatGeekProvider.class.getDeclaredField("http");
        f.setAccessible(true);
        var client = org.springframework.web.client.RestClient.builder()
                .baseUrl(scheme + "://localhost:" + port + "/2")
                .build();
        f.set(provider, client);
    }

    private void injectAgendaLxBaseUrl(AgendaLxProvider provider, String scheme, int port) throws Exception {
        Field f = AgendaLxProvider.class.getDeclaredField("http");
        f.setAccessible(true);
        var client = org.springframework.web.client.RestClient.builder()
                .baseUrl(scheme + "://localhost:" + port + "/wp-json/agendalx/v1")
                .defaultHeader("User-Agent", "test-agent")
                .build();
        f.set(provider, client);
    }
}