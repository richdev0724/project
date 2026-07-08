package com.marketmono.config;

import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.ResourceAccessor;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LiquibaseDeployChangelogTest {

    private static final String MASTER_CHANGELOG = "db/changelog/db.changelog-master.yaml";

    @Test
    void masterChangelogIncludesOptionTradesHotWindowRemediation() throws Exception {
        DatabaseChangeLog changeLog = parseChangelog(MASTER_CHANGELOG);

        assertThat(changeLog.getChangeSets())
            .extracting(changeSet -> changeSet.getId())
            .contains("option-trades-016-hot-window-index-and-compression-policy");
    }

    @Test
    void masterChangelogIncludesCurrentProductionDeltaChangesets() throws Exception {
        DatabaseChangeLog changeLog = parseChangelog(MASTER_CHANGELOG);

        assertThat(changeLog.getChangeSets())
            .extracting(changeSet -> changeSet.getId())
            .contains(
                "seed-106-waitlist-confirmation-template",
                "107-customer-invite-user-fields",
                "107-economic-event-admin-fields",
                "108-seed-economic-events-from-temporary-json",
                "seed-109-customer-invite-template",
                "111-admin-uw-api-call-log",
                "112-runtime-grants-admin-cross-service-tables",
                "113-support-ticket-types",
                "113-support-ticket-tables",
                "113-support-ticket-notification-status-in-progress",
                "113-seed-support-ticket-created-email-template",
                "113-seed-support-ticket-user-reply-email-template",
                "113-seed-support-ticket-staff-reply-email-template",
                "114-create-user-beta-access",
                "114-seed-beta-waitlist-invite-template",
                "115-runtime-grants-user-beta-access",
                "116-update-beta-waitlist-invite-template-no-temp-password",
                "117-user-email-lifecycle-controls",
                "118-admin-hard-delete-challenges",
                "119-runtime-grants-admin-hard-delete-challenges",
                "120-whop-provider-billing-columns",
                "120-whop-seed-monthly-product-mappings",
                "120-whop-billing-webhook-events",
                "122-whop-product-visibility",
                "123-beta-waitlist-invite-direct-whop-link",
                "124-whop-membership-correlations",
                "125-runtime-grants-whop-checkout-attempts",
                "128-spot-gex-strike-expiry-stream-buckets",
                "129-stock-price-source-neutralization",
                "spot-gex-130-stream-snapshot-lookup-index",
                "spot-gex-131-current-snapshot-table",
                "144-honor-program-schema",
                "145-runtime-grants-honor-codes"
            );
    }

    @Test
    void spotGexStreamBucketChangelogCreatesDedicatedSevenDayBucketTable() throws Exception {
        String changelog = Files.readString(Path.of(
            "src/main/resources/db/changelog/128-spot-gex-strike-expiry-stream-buckets.yaml"
        ));

        assertThat(changelog)
            .contains("CREATE TABLE IF NOT EXISTS spot_gex_strike_expiry_1m")
            .contains("PRIMARY KEY (ticker, expiry, strike_price_cents, bucket_start)")
            .contains("idx_spot_gex_1m_ticker_bucket")
            .contains("idx_spot_gex_1m_ticker_expiry_bucket")
            .contains("INTERVAL '7 days'")
            .doesNotContain("spot-gex-strike-expiry.raw")
            .doesNotContain("spot-gex-strike-expiry.1m");
    }

    @Test
    void spotGexCurrentSnapshotChangelogCreatesMaintainedCurrentTable() throws Exception {
        String changelog = Files.readString(Path.of(
            "src/main/resources/db/changelog/131-spot-gex-current-snapshot.yaml"
        ));

        assertThat(changelog)
            .contains("CREATE TABLE IF NOT EXISTS spot_gex_strike_expiry_current")
            .contains("PRIMARY KEY (ticker, expiry, strike_price_cents)")
            .contains("idx_spot_gex_current_ticker_bucket")
            .contains("SELECT DISTINCT ON (ticker, expiry, strike_price_cents)")
            .contains("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.spot_gex_strike_expiry_current TO marketmono_app");
    }

    @Test
    void whopProviderBillingChangelogSeedsKnownProductMappings() throws Exception {
        String changelog = Files.readString(Path.of(
            "src/main/resources/db/changelog/120-whop-provider-billing.yaml"
        ));

        assertThat(changelog)
            .contains("id: 120-whop-seed-monthly-product-mappings")
            .contains("prod_VYg1i2OBMF2No")
            .contains("plan_psgRuvs8nuYvo")
            .contains("https://whop.com/checkout/plan_psgRuvs8nuYvo")
            .contains("prod_xKyE6lcOIYmw7")
            .contains("plan_qCFdPvGNk9iRb")
            .contains("https://whop.com/checkout/plan_qCFdPvGNk9iRb")
            .contains("ON CONFLICT (product_id, billing_period) DO UPDATE")
            .contains("whop_plan_id = EXCLUDED.whop_plan_id");
    }

    @Test
    void supportTicketChangelogCreatesTextOnlyTablesWithoutAttachmentTable() throws Exception {
        String changelog = Files.readString(Path.of(
            "src/main/resources/db/changelog/113-support-ticket-system.yaml"
        ));

        assertThat(changelog)
            .contains("support_tickets")
            .contains("support_ticket_messages")
            .contains("support_ticket_notification_events")
            .contains("support_ticket_number_seq")
            .doesNotContain("support_ticket_attachments");
    }

    @Test
    void supportTicketEmailTemplateSeedsArePartialStateSafePerSlug() throws Exception {
        String changelog = Files.readString(Path.of(
            "src/main/resources/db/changelog/113-support-ticket-system.yaml"
        ));

        assertThat(changelog)
            .contains("id: 113-seed-support-ticket-created-email-template")
            .contains("sql: SELECT COUNT(*) FROM email_templates WHERE slug = 'support-ticket-created'")
            .contains("id: 113-seed-support-ticket-user-reply-email-template")
            .contains("sql: SELECT COUNT(*) FROM email_templates WHERE slug = 'support-ticket-user-reply'")
            .contains("id: 113-seed-support-ticket-staff-reply-email-template")
            .contains("sql: SELECT COUNT(*) FROM email_templates WHERE slug = 'support-ticket-staff-reply'")
            .doesNotContain(
                "SELECT COUNT(*) FROM email_templates WHERE slug IN "
                    + "('support-ticket-created', 'support-ticket-user-reply', 'support-ticket-staff-reply')"
            );
    }

    @Test
    void productionDeployWorkflowUsesCanonicalMasterChangelog() throws Exception {
        String workflow = Files.readString(Path.of(".github/workflows/deploy-backend.yml"));

        assertThat(workflow)
            .contains("SPRING_LIQUIBASE_CHANGE_LOG=classpath:db/changelog/db.changelog-master.yaml")
            .doesNotContain("db.changelog-waitlist-deploy.yaml");
    }

    @Test
    void runtimeGrantChangelogGrantsAdminVisibleTablesToRuntimeRole() throws Exception {
        String changelog = Files.readString(Path.of(
            "src/main/resources/db/changelog/112-runtime-grants-admin-cross-service-tables.yaml"
        ));

        assertThat(changelog)
            .contains("GRANT SELECT ON TABLE public.subscriber_cap_reservations TO marketmono_app")
            .contains("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.uw_api_call_logs TO marketmono_app")
            .contains("GRANT USAGE, SELECT ON SEQUENCE public.uw_api_call_logs_id_seq TO marketmono_app");
    }

    @Test
    void runtimeGrantChangelogGrantsUserBetaAccessToRuntimeRole() throws Exception {
        String changelog = Files.readString(Path.of(
            "src/main/resources/db/changelog/115-runtime-grants-user-beta-access.yaml"
        ));

        assertThat(changelog)
            .contains("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.user_beta_access TO marketmono_app");
    }

    @Test
    void whopMembershipCorrelationChangelogGrantsRuntimeRole() throws Exception {
        String changelog = Files.readString(Path.of(
            "src/main/resources/db/changelog/124-whop-membership-correlations.yaml"
        ));

        assertThat(changelog)
            .contains("tableName: whop_membership_correlations")
            .contains("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.whop_membership_correlations TO marketmono_app");
    }

    @Test
    void whopCheckoutAttemptGrantChangelogGrantsRuntimeRole() throws Exception {
        String changelog = Files.readString(Path.of(
            "src/main/resources/db/changelog/125-runtime-grants-whop-checkout-attempts.yaml"
        ));

        assertThat(changelog)
            .contains("id: 125-runtime-grants-whop-checkout-attempts")
            .contains("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.whop_checkout_attempts TO marketmono_app")
            .contains("REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLE public.whop_checkout_attempts FROM marketmono_app");
    }

    @Test
    void hardDeleteChallengeChangelogGrantsRuntimeRole() throws Exception {
        String changelog = Files.readString(Path.of(
            "src/main/resources/db/changelog/119-runtime-grants-admin-hard-delete-challenges.yaml"
        ));

        assertThat(changelog)
            .contains("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.admin_hard_delete_challenges TO marketmono_app")
            .contains("REVOKE SELECT, INSERT, UPDATE, DELETE ON TABLE public.admin_hard_delete_challenges FROM marketmono_app");
    }

    @Test
    void masterChangelogPreservesProductionLogicalFilePaths() throws Exception {
        DatabaseChangeLog changeLog = parseChangelog(MASTER_CHANGELOG);

        assertThat(filePathFor(changeLog, "baseline-001-market-reference-and-cache"))
            .isEqualTo("src/main/resources/db/changelog/001-market-reference-and-cache.yaml");
        assertThat(filePathFor(changeLog, "feature-011-option-trade-aggregates"))
            .isEqualTo("src/main/resources/db/changelog/011-option-trade-aggregates.yaml");
        assertThat(filePathFor(changeLog, "changelog-105-create-table"))
            .isEqualTo("src/main/resources/db/changelog/105-changelog-entries.yaml");
        assertThat(filePathFor(changeLog, "015-user-registration-lifecycle-waitlist"))
            .isEqualTo("db/changelog/015-user-registration-lifecycle-waitlist.yaml");
        assertThat(filePathFor(changeLog, "107-customer-invite-user-fields"))
            .isEqualTo("db/changelog/107-customer-invites-and-economic-event-admin.yaml");
        assertThat(filePathFor(changeLog, "seed-109-customer-invite-template"))
            .isEqualTo("db/changelog/109-seed-customer-invite-template.yaml");
        assertThat(filePathFor(changeLog, "111-admin-uw-api-call-log"))
            .isEqualTo("db/changelog/111-admin-uw-api-call-log.yaml");
        assertThat(filePathFor(changeLog, "112-runtime-grants-admin-cross-service-tables"))
            .isEqualTo("db/changelog/112-runtime-grants-admin-cross-service-tables.yaml");
        assertThat(filePathFor(changeLog, "113-support-ticket-tables"))
            .isEqualTo("db/changelog/113-support-ticket-system.yaml");
        assertThat(filePathFor(changeLog, "114-create-user-beta-access"))
            .isEqualTo("db/changelog/114-beta-waitlist-invite-flow.yaml");
        assertThat(filePathFor(changeLog, "115-runtime-grants-user-beta-access"))
            .isEqualTo("db/changelog/115-runtime-grants-user-beta-access.yaml");
        assertThat(filePathFor(changeLog, "116-update-beta-waitlist-invite-template-no-temp-password"))
            .isEqualTo("db/changelog/116-update-beta-waitlist-invite-template-no-temp-password.yaml");
        assertThat(filePathFor(changeLog, "117-user-email-lifecycle-controls"))
            .isEqualTo("src/main/resources/db/changelog/117-user-email-lifecycle-controls.yaml");
        assertThat(filePathFor(changeLog, "118-admin-hard-delete-challenges"))
            .isEqualTo("src/main/resources/db/changelog/118-admin-hard-delete-challenges.yaml");
        assertThat(filePathFor(changeLog, "119-runtime-grants-admin-hard-delete-challenges"))
            .isEqualTo("db/changelog/119-runtime-grants-admin-hard-delete-challenges.yaml");
        assertThat(filePathFor(changeLog, "129-stock-price-source-neutralization"))
            .isEqualTo("db/changelog/129-stock-price-source-neutralization.yaml");
    }

    @Test
    void betaWaitlistChangelogUsesSeparateAccessTableInsteadOfUserColumns() throws Exception {
        String changelog = Files.readString(Path.of(
            "src/main/resources/db/changelog/114-beta-waitlist-invite-flow.yaml"
        ));

        assertThat(changelog)
            .contains("id: 114-create-user-beta-access")
            .contains("tableName: user_beta_access")
            .contains("baseTableName: user_beta_access")
            .contains("referencedTableName: users")
            .doesNotContain("id: 114-beta-waitlist-user-fields")
            .doesNotContain("addColumn:\n            tableName: users");
    }

    private static DatabaseChangeLog parseChangelog(String path) throws Exception {
        ResourceAccessor resourceAccessor = new ClassLoaderResourceAccessor();
        return ChangeLogParserFactory.getInstance()
            .getParser(path, resourceAccessor)
            .parse(path, new ChangeLogParameters(), resourceAccessor);
    }

    private static String filePathFor(DatabaseChangeLog changeLog, String id) {
        return changeLog.getChangeSets().stream()
            .filter(changeSet -> id.equals(changeSet.getId()))
            .findFirst()
            .orElseThrow()
            .getFilePath();
    }
}
