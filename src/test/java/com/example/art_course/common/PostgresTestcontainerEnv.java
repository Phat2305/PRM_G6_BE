package com.example.art_course.common;

import com.example.art_course.contracts.IEnrollmentContract;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.service.OrderService;
import com.example.art_course.modules.payment.gateway.PayOSPaymentGateway;
import com.example.art_course.modules.payment.gateway.PaymentGatewayFactory;
import com.example.art_course.modules.payment.service.EnrollmentGrantService;
import com.example.art_course.modules.payment.service.PaymentService;
import com.example.art_course.modules.finance.service.LedgerService;
import com.example.art_course.modules.finance.service.EscrowReleaseService;
import com.example.art_course.modules.refund.service.RefundService;
import com.example.art_course.modules.withdrawal.service.WithdrawalService;
import com.example.art_course.modules.refund.repository.RefundRepository;
import com.example.art_course.modules.refund.repository.RefundItemRepository;
import com.example.art_course.modules.order.repository.OrderRepository;
import com.example.art_course.modules.order.repository.OrderDetailRepository;
import com.example.art_course.modules.payment.repository.PaymentRepository;
import com.example.art_course.modules.payment.repository.MoneyLedgerRepository;
import com.example.art_course.modules.withdrawal.repository.WithdrawalRequestRepository;
import com.example.art_course.supply.repository.SupplyRepository;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.testcontainers.DockerClientFactory;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.mockito.Mockito.mock;

/**
 * Môi trường Testcontainers PostgreSQL (postgres:16-alpine) cô lập hoàn toàn.
 * Có Safety Guard từ chối mọi JDBC URL không phải của chính container đang chạy.
 */
public final class PostgresTestcontainerEnv {

    private static PostgreSQLContainer<?> container;
    private static AnnotationConfigApplicationContext context;

    private PostgresTestcontainerEnv() {}

    public static synchronized AnnotationConfigApplicationContext getContext() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker required for isolated PostgreSQL tests");
        if (context == null) {
            if (container == null) {
                container = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("test_art_course_dev")
                        .withUsername("test_agent")
                        .withPassword("test_secret_pw");
                container.start();

                String jdbcUrl = container.getJdbcUrl();
                assertIsolatedContainerUrl(jdbcUrl);

            }

            context = new AnnotationConfigApplicationContext(TestJpaConfig.class);
            prepareTestSchema();
        }
        return context;
    }

    public static synchronized PostgreSQLContainer<?> getContainer() {
        getContext();
        return container;
    }

    public static java.util.Map<String, String> payosWebhook(long code, String amount, String reference) {
        var data = new java.util.TreeMap<String, String>();
        data.put("amount", amount);
        data.put("code", "00");
        data.put("orderCode", Long.toString(code));
        data.put("reference", reference);
        String raw = data.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(java.util.stream.Collectors.joining("&"));
        try {
            var mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec("test-checksum-key".getBytes(java.nio.charset.StandardCharsets.UTF_8), "HmacSHA256"));
            var params = new java.util.HashMap<String, String>();
            data.forEach((key, value) -> params.put("data." + key, value));
            params.put("success", "true");
            params.put("signature", java.util.HexFormat.of().formatHex(mac.doFinal(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
            return params;
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Guard bắt buộc: từ chối mọi JDBC URL không phải của chính container Testcontainers đang chạy.
     */
    public static void assertIsolatedContainerUrl(String jdbcUrl) {
        if (container == null || !container.isRunning()) {
            throw new SecurityException("SAFETY GUARD: Container Testcontainers chưa chạy!");
        }
        String expectedUrl = container.getJdbcUrl();
        int mappedPort = container.getMappedPort(5432);
        if (jdbcUrl == null || !jdbcUrl.equals(expectedUrl)) {
            throw new SecurityException("SAFETY GUARD: Từ chối JDBC URL không khớp container cô lập: " + jdbcUrl);
        }
        if (mappedPort == 5432 || !jdbcUrl.contains(":" + mappedPort + "/test_art_course_dev")) {
            throw new SecurityException("SAFETY GUARD: Cổng hoặc tên DB không phải của Testcontainers cô lập: " + jdbcUrl);
        }
    }

    // Isolated service tests use current entities; legacy pg_dump is not the application schema.
    private static void prepareTestSchema() {
        assertIsolatedContainerUrl(container.getJdbcUrl());
        JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
        // Profile lookup uses native SQL rather than JPA entities.
        jdbc.execute("CREATE TABLE teacher_profiles (user_id uuid PRIMARY KEY, full_name varchar(255), shipping_fee bigint)");
        jdbc.execute("CREATE TABLE parent_profiles (user_id uuid PRIMARY KEY, full_name varchar(255), shipping_address text, phone text)");
        jdbc.query("SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_schema = 'public'", rs -> {
            String column = rs.getString("column_name");
            String value = null;
            if (column.equals("id") && rs.getString("data_type").equals("uuid")) value = "gen_random_uuid()";
            if (List.of("created_at", "updated_at").contains(column)) value = "CURRENT_TIMESTAMP";
            if (List.of("commission_rate", "commission_amount", "net_amount", "shipping_fee", "reserved_quantity", "sold_quantity", "rejection_count").contains(column)) value = "0";
            if (value != null) jdbc.execute("ALTER TABLE public.\"" + rs.getString("table_name") + "\" ALTER COLUMN \"" + column + "\" SET DEFAULT " + value);
        });
        try {
            // Exercise the actual migration and append-only triggers, never a copied money algorithm.
            jdbc.execute(Files.readString(Path.of("src/main/resources/db/migration/V11__commerce_safety.sql")));
            String original = Files.readString(Path.of("docs/migrations/01_be2_migration_up.sql"));
            int from = original.indexOf("CREATE OR REPLACE FUNCTION public.fn_prevent_money_ledger_modification()");
            int to = original.indexOf("-- =====================================================================", from);
            jdbc.execute(original.substring(from, to));
            jdbc.execute("CREATE TRIGGER trg_prevent_money_ledger_truncate BEFORE TRUNCATE ON public.money_ledger FOR EACH STATEMENT EXECUTE FUNCTION public.fn_prevent_money_ledger_modification()");
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot prepare isolated test schema", ex);
        }
    }

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackages = "com.example.art_course")
    public static class TestJpaConfig {

        @Bean
        public DataSource dataSource() {
            String url = container.getJdbcUrl();
            assertIsolatedContainerUrl(url);
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(container.getUsername());
            config.setPassword(container.getPassword());
            config.setMaximumPoolSize(15);
            return new HikariDataSource(config);
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
            em.setDataSource(dataSource);
            em.setPackagesToScan("com.example.art_course");
            em.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            Properties props = new Properties();
            props.setProperty("hibernate.hbm2ddl.auto", "create-drop");
            props.setProperty("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
            em.setJpaProperties(props);
            return em;
        }

        @Bean
        public PlatformTransactionManager transactionManager(EntityManagerFactory emf) {
            return new JpaTransactionManager(emf);
        }

        @Bean
        public ConfigService configService(com.example.art_course.modules.systemconfig.repository.SystemConfigRepository repo) {
            return new ConfigService(repo);
        }

        @Bean
        public OrderService orderService(
                com.example.art_course.modules.order.repository.OrderRepository orderRepo,
                com.example.art_course.modules.order.repository.OrderDetailRepository orderDetailRepo,
                com.example.art_course.modules.cart.repository.CartRepository cartRepo,
                com.example.art_course.modules.cart.repository.CartItemRepository cartItemRepo,
                com.example.art_course.course.repository.CourseRepository courseRepo,
                com.example.art_course.supply.repository.SupplyRepository supplyRepo,
                EntityManagerFactory emf,
                ConfigService configService,
                EnrollmentGrantService enrollmentGrantService,
                com.example.art_course.child.repository.ChildRepository childRepository
        ) {
            jakarta.persistence.EntityManager sharedEm = org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(emf);
            return new OrderService(orderRepo, orderDetailRepo, cartRepo, cartItemRepo, courseRepo, supplyRepo, sharedEm,
                    new com.example.art_course.common.TeacherNames(sharedEm),
                    childRepository,
                    enrollmentGrantService, configService);
        }

        @Bean
        public IEnrollmentContract enrollmentContract() {
            return mock(IEnrollmentContract.class);
        }

        @Bean
        public EnrollmentGrantService enrollmentGrantService(IEnrollmentContract enrollmentContract) {
            return new EnrollmentGrantService(enrollmentContract);
        }

        @Bean
        public NotificationService notificationService(com.example.art_course.modules.notification.repository.NotificationRepository repo) {
            return new NotificationService(repo);
        }

        @Bean
        public PaymentGatewayFactory paymentGatewayFactory() {
            PayOSPaymentGateway gateway = new PayOSPaymentGateway(mock(JdbcTemplate.class));
            org.springframework.test.util.ReflectionTestUtils.setField(gateway, "checksumKey", "test-checksum-key");
            return new PaymentGatewayFactory(List.of(gateway));
        }

        @Bean
        public PaymentService paymentService(
                com.example.art_course.modules.order.repository.OrderRepository orderRepo,
                com.example.art_course.modules.order.repository.OrderDetailRepository orderDetailRepo,
                com.example.art_course.modules.payment.repository.PaymentRepository paymentRepo,
                com.example.art_course.modules.payment.repository.WebhookLogRepository webhookLogRepo,
                com.example.art_course.modules.payment.repository.MoneyLedgerRepository moneyLedgerRepo,
                com.example.art_course.supply.repository.SupplyRepository supplyRepo,
                PaymentGatewayFactory gatewayFactory,
                EnrollmentGrantService grantService,
                NotificationService notifService,
                ConfigService configService,
                com.example.art_course.modules.refund.repository.RefundRepository refunds,
                com.example.art_course.modules.refund.repository.RefundItemRepository refundItems,
                com.example.art_course.enrollment.repository.EnrollmentRepository enrollments,
                PlatformTransactionManager transactionManager
        ) {
            return new PaymentService(orderRepo, orderDetailRepo, paymentRepo, webhookLogRepo, moneyLedgerRepo,
                    supplyRepo, gatewayFactory, grantService, notifService, configService, refunds, refundItems, enrollments, transactionManager);
        }

        @Bean
        public LedgerService ledgerService(MoneyLedgerRepository ledger, PaymentRepository payments,
                                           WithdrawalRequestRepository withdrawals, RefundRepository refunds) {
            return org.mockito.Mockito.spy(new LedgerService(ledger, payments, withdrawals, refunds));
        }

        @Bean
        public RefundService refundService(RefundRepository refunds, RefundItemRepository items,
                OrderRepository orders, OrderDetailRepository details, PaymentRepository payments,
                SupplyRepository supplies, IEnrollmentContract enrollment, LedgerService ledger,
                NotificationService notifications,
                com.example.art_course.customize_request.repository.CustomizeRequestRepository requests,
                com.example.art_course.course.repository.CourseRepository courses) {
            return new RefundService(refunds, items, orders, details, payments, supplies, enrollment, ledger,
                    notifications, mock(com.example.art_course.modules.moderation.service.ModerationService.class), requests, courses, org.mockito.Mockito.mock(com.example.art_course.video.service.R2StorageService.class));
        }

        @Bean
        public EscrowReleaseService escrowReleaseService(PaymentRepository payments, OrderRepository orders,
                OrderDetailRepository details, com.example.art_course.modules.course.repository.CourseReviewRepository reviews,
                RefundRepository refunds, LedgerService ledger, NotificationService notifications, ConfigService config) {
            return new EscrowReleaseService(payments, orders, details, reviews, refunds, ledger, notifications, config);
        }

        @Bean
        public WithdrawalService withdrawalService(WithdrawalRequestRepository withdrawals, LedgerService ledger,
                MoneyLedgerRepository entries, NotificationService notifications) {
            return new WithdrawalService(withdrawals, entries, ledger, notifications, org.mockito.Mockito.mock(com.example.art_course.video.service.R2StorageService.class));
        }
    }
}
