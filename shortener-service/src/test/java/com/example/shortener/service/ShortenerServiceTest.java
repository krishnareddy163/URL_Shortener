package com.example.shortener.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.example.shortener.domain.ShortLink;
import com.example.shortener.storage.LinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShortenerServiceTest {
    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");

    private LinkRepository repository;
    private CodeGenerator codeGenerator;
    private ClickRecorder clickRecorder;
    private ShortenerService service;

    @BeforeEach
    void setUp() {
        repository = mock(LinkRepository.class);
        codeGenerator = mock(CodeGenerator.class);
        clickRecorder = mock(ClickRecorder.class);
        service = new ShortenerService(repository, new UrlValidator(), new AliasValidator(), codeGenerator,
                clickRecorder, Clock.fixed(NOW, ZoneOffset.UTC));
        when(repository.findByUrlHash(anyString())).thenReturn(Optional.empty());
        when(repository.findByCode(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void retriesGeneratedCodeAfterCollision() {
        when(codeGenerator.generate()).thenReturn("aaaaaaa", "bbbbbbb");
        doThrow(new DuplicateKeyException("code exists")).doNothing().when(repository).insert(any());

        ShortenerService.CreateResult result = service.create("https://example.com/a", null);

        assertThat(result.created()).isTrue();
        assertThat(result.link().code()).isEqualTo("bbbbbbb");
        assertThat(result.link().createdAt()).isEqualTo(NOW);
        verify(codeGenerator, times(2)).generate();
    }

    @Test
    void failsWithCodeGenerationFailedAfterFiveCollisions() {
        when(codeGenerator.generate()).thenReturn("aaaaaaa");
        doThrow(new DuplicateKeyException("code exists")).when(repository).insert(any());

        assertThatThrownBy(() -> service.create("https://example.com/a", null))
                .isInstanceOf(ShortenerException.class)
                .extracting(error -> ((ShortenerException) error).errorCode())
                .isEqualTo(ErrorCode.CODE_GENERATION_FAILED);
        verify(codeGenerator, times(ShortenerService.MAX_CODE_ATTEMPTS)).generate();
    }

    @Test
    void returnsExistingLinkForSameNormalizedUrl() {
        ShortLink existing = new ShortLink("abc1234", "https://example.com/a",
                ShortenerService.sha256("https://example.com/a"), NOW);
        when(repository.findByUrlHash(existing.urlHash())).thenReturn(Optional.of(existing));

        ShortenerService.CreateResult result = service.create("HTTPS://EXAMPLE.com:443/a", null);

        assertThat(result.created()).isFalse();
        assertThat(result.link()).isEqualTo(existing);
        verify(repository, never()).insert(any());
    }

    @Test
    void concurrentInsertOfSameUrlReturnsTheWinner() {
        String urlHash = ShortenerService.sha256("https://example.com/race");
        ShortLink winner = new ShortLink("winner1", "https://example.com/race", urlHash, NOW);
        when(codeGenerator.generate()).thenReturn("loser01");
        when(repository.findByUrlHash(urlHash)).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));
        doThrow(new DuplicateKeyException("url exists")).when(repository).insert(any());

        ShortenerService.CreateResult result = service.create("https://example.com/race", null);

        assertThat(result.created()).isFalse();
        assertThat(result.link()).isEqualTo(winner);
    }

    @Test
    void createsLinkWithCustomAlias() {
        doNothing().when(repository).insert(any());

        ShortenerService.CreateResult result = service.create("https://example.com/a", "my-alias");

        assertThat(result.link().code()).isEqualTo("my-alias");
        verify(codeGenerator, never()).generate();
    }

    @Test
    void rejectsAliasThatIsAlreadyTaken() {
        when(repository.findByCode("my-alias")).thenReturn(Optional.of(
                new ShortLink("my-alias", "https://other.example/", "hash", NOW)));

        assertError(() -> service.create("https://example.com/a", "my-alias"), ErrorCode.ALIAS_TAKEN);
    }

    @Test
    void rejectsAliasForUrlAlreadyShortenedUnderAnotherCode() {
        String urlHash = ShortenerService.sha256("https://example.com/a");
        when(repository.findByUrlHash(urlHash)).thenReturn(Optional.of(
                new ShortLink("abc1234", "https://example.com/a", urlHash, NOW)));

        assertError(() -> service.create("https://example.com/a", "my-alias"), ErrorCode.URL_ALREADY_SHORTENED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ab", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "has space", "semi;colon", "api", "API", "actuator", "health", "stats"})
    void rejectsInvalidOrReservedAliases(String alias) {
        assertError(() -> service.create("https://example.com/a", alias), ErrorCode.INVALID_ALIAS);
    }

    @Test
    void resolveEnqueuesClickWithoutWaiting() {
        ShortLink link = new ShortLink("abc1234", "https://example.com/a", "hash", NOW);
        when(repository.findByCode("abc1234")).thenReturn(Optional.of(link));

        assertThat(service.resolve("abc1234")).isEqualTo(link);
        verify(clickRecorder).enqueue("abc1234");
    }

    @Test
    void resolveOfUnknownCodeIsNotFoundAndRecordsNothing() {
        assertError(() -> service.resolve("missing"), ErrorCode.LINK_NOT_FOUND);
        verify(clickRecorder, never()).enqueue(anyString());
    }

    private static void assertError(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(ShortenerException.class)
                .extracting(error -> ((ShortenerException) error).errorCode())
                .isEqualTo(expected);
    }

    @Test
    void recreatingWithTheSameAliasReturnsTheExistingLink() {
        ShortLink existing = new ShortLink("my-alias", "https://example.com/same", ShortenerService.sha256("https://example.com/same"), NOW);
        when(repository.findByUrlHash(existing.urlHash())).thenReturn(Optional.of(existing));

        ShortenerService.CreateResult result = service.create("https://example.com/same", "my-alias");

        assertThat(result.created()).isFalse();
        assertThat(result.link()).isEqualTo(existing);
    }

    @Test
    void anAliasRaceWonByTheSameUrlReturnsTheWinner() {
        String hash = ShortenerService.sha256("https://example.com/race");
        ShortLink winner = new ShortLink("race-alias", "https://example.com/race", hash, NOW);
        doThrow(new DuplicateKeyException("url exists")).when(repository).insert(any());
        when(repository.findByUrlHash(hash)).thenReturn(Optional.empty()).thenReturn(Optional.of(winner));

        ShortenerService.CreateResult result = service.create("https://example.com/race", "race-alias");

        assertThat(result.created()).isFalse();
        assertThat(result.link()).isEqualTo(winner);
    }

    @Test
    void anAliasRaceLostToAnotherUrlIsAliasTaken() {
        doThrow(new DuplicateKeyException("alias exists")).when(repository).insert(any());

        assertThatThrownBy(() -> service.create("https://example.com/lost", "lost-alias"))
                .isInstanceOf(ShortenerException.class)
                .extracting(exception -> ((ShortenerException) exception).errorCode())
                .isEqualTo(ErrorCode.ALIAS_TAKEN);
    }

    @Test
    void creationWorksWithInfoLoggingSwitchedOff() {
        Logger logger = (Logger) LoggerFactory.getLogger(ShortenerService.class);
        Level previous = logger.getLevel();
        logger.setLevel(Level.WARN);
        try {
            when(codeGenerator.generate()).thenReturn("quiet01");

            assertThat(service.create("https://example.com/quiet", null).created()).isTrue();
            assertThat(service.create("https://example.com/quiet-alias", "quiet-alias").created()).isTrue();
        } finally {
            logger.setLevel(previous);
        }
    }
}
