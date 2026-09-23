package com.bryan.fluxgate.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.bryan.fluxgate.model.principal.ApiKeyPrincipal;
import com.bryan.fluxgate.repository.ApiKeyRepository;
import com.bryan.fluxgate.support.PostgresIntegrationTest;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.core.PostgresDatabase;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ApiKeyAuthService.class, ApiKeyHashingService.class, ApiKeyAuthServiceTest.FixedTime.class})
// Do not let a test transaction hide a missing service transaction or uncommitted write.
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ApiKeyAuthServiceTest extends PostgresIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
    private static final OffsetDateTime USED_AT = NOW.atOffset(ZoneOffset.UTC);

    @Autowired ApiKeyAuthService service;
    @Autowired ApiKeyHashingService hashing;
    @Autowired ApiKeyRepository keys;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactions;

    private UUID accountId;
    private UUID keyId;
    private String rawKey;

    @BeforeEach
    void createCredentialUsingDatabaseDefaults() {
        accountId = UUID.randomUUID();
        keyId = UUID.randomUUID();
        rawKey = "test-" + UUID.randomUUID();
        jdbc.update("insert into accounts (id, name) values (?, ?)", accountId, accountId.toString());
        jdbc.update("insert into api_keys (id, account_id, key_hash, key_prefix) values (?, ?, ?, ?)",
                keyId, accountId, hashing.hash(rawKey), "test");
    }

    @Test
    void databaseDefaultsAuthenticateAndLastUseIsCommitted() {
        assertEquals("ACTIVE", jdbc.queryForObject("select status from accounts where id = ?",
                String.class, accountId));
        assertEquals("ACTIVE", jdbc.queryForObject("select status from api_keys where id = ?",
                String.class, keyId));

        assertEquals(new ApiKeyPrincipal(accountId, keyId), service.verifyApiKey(rawKey));
        assertEquals(USED_AT, lastUsedAt());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "unknown-key"})
    void rejectsMissingAndUnknownCredentials(String credential) {
        assertThrows(BadCredentialsException.class, () -> service.verifyApiKey(credential));
        assertNull(lastUsedAt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-01T11:59:59Z", "2026-01-01T12:00:00Z"})
    void rejectsExpiredCredentialsIncludingExactBoundary(String expiresAt) {
        jdbc.update("update api_keys set expires_at = ? where id = ?", OffsetDateTime.parse(expiresAt), keyId);
        assertRejectedWithoutUpdatingLastUse();
    }

    @Test
    void acceptsACredentialThatHasNotYetExpired() {
        jdbc.update("update api_keys set expires_at = ? where id = ?", USED_AT.plusSeconds(1), keyId);
        assertEquals(keyId, service.verifyApiKey(rawKey).apiKeyId());
        assertEquals(USED_AT, lastUsedAt());
    }

    @Test
    void rejectsRevokedStatusEvenWithoutRevokedTimestamp() {
        jdbc.update("update api_keys set status = 'REVOKED' where id = ?", keyId);
        assertRejectedWithoutUpdatingLastUse();
    }

    @Test
    void rejectsRevokedTimestampEvenWithActiveStatus() {
        jdbc.update("update api_keys set revoked_at = ? where id = ?", USED_AT.minusSeconds(1), keyId);
        assertRejectedWithoutUpdatingLastUse();
    }

    @Test
    void rejectsInactiveAccounts() {
        jdbc.update("update accounts set status = 'REVOKED' where id = ?", accountId);
        assertRejectedWithoutUpdatingLastUse();
    }

    @Test
    void earlierAuthenticationCannotMoveLastUseBackward() {
        jdbc.update("update api_keys set last_used_at = ? where id = ?", USED_AT.plusSeconds(1), keyId);
        service.verifyApiKey(rawKey);
        assertEquals(USED_AT.plusSeconds(1), lastUsedAt());
    }

    @Test
    void metadataUpdateDoesNotOverwriteRevocation() {
        jdbc.update("update api_keys set status = 'REVOKED', revoked_at = ? where id = ?", USED_AT, keyId);
        new TransactionTemplate(transactions).executeWithoutResult(status ->
                keys.recordSuccessfulAuthentication(keyId, USED_AT));

        assertEquals("REVOKED", jdbc.queryForObject("select status from api_keys where id = ?", String.class, keyId));
        assertEquals(USED_AT, jdbc.queryForObject("select revoked_at from api_keys where id = ?",
                OffsetDateTime.class, keyId));
        assertEquals(USED_AT, lastUsedAt());
    }

    @Test
    void databaseRejectsUnknownStatusValues() {
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update api_keys set status = 'unknown' where id = ?", keyId));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update accounts set status = 'unknown' where id = ?", accountId));
    }

    @Test
    void migrationUpgradesExistingLowercaseStatuses() throws Exception {
        // Exercise an actual v1 -> current upgrade separately from the fresh-schema tests.
        String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("create schema " + schema);
        try (Connection connection = dataSource.getConnection()) {
            connection.setSchema(schema);
            PostgresDatabase database = new PostgresDatabase();
            database.setConnection(new JdbcConnection(connection));
            database.setDefaultSchemaName(schema);
            database.setLiquibaseSchemaName(schema);
            var resources = new ClassLoaderResourceAccessor();
            new Liquibase("db/changelog/changes/v1/changelog.yaml", resources, database)
                    .update(new Contexts(), new LabelExpression());

            try (var statement = connection.createStatement()) {
                statement.executeUpdate("insert into accounts (name, status) values ('old-active', 'active'), ('old-revoked', 'revoked')");
                statement.executeUpdate("""
                        insert into api_keys (account_id, key_hash, key_prefix, status)
                        select id, name, 'old', status from accounts
                        """);
            }

            new Liquibase("db/changelog/changelog-master.yaml", resources, database)
                    .update(new Contexts(), new LabelExpression());

            try (var statement = connection.createStatement();
                    var rows = statement.executeQuery("""
                            select a.name, a.status, k.status from accounts a
                            join api_keys k on k.account_id = a.id order by a.name
                            """)) {
                org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                assertEquals("ACTIVE", rows.getString(2));
                assertEquals("ACTIVE", rows.getString(3));
                org.junit.jupiter.api.Assertions.assertTrue(rows.next());
                assertEquals("REVOKED", rows.getString(2));
                assertEquals("REVOKED", rows.getString(3));
            }
        }
    }

    private void assertRejectedWithoutUpdatingLastUse() {
        // Rejection must preserve an existing timestamp as well as avoid new writes.
        OffsetDateTime previous = USED_AT.minusDays(1);
        jdbc.update("update api_keys set last_used_at = ? where id = ?", previous, keyId);
        assertThrows(BadCredentialsException.class, () -> service.verifyApiKey(rawKey));
        assertEquals(previous, lastUsedAt());
    }

    private OffsetDateTime lastUsedAt() {
        return jdbc.queryForObject("select last_used_at from api_keys where id = ?", OffsetDateTime.class, keyId);
    }

    @TestConfiguration
    static class FixedTime {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
