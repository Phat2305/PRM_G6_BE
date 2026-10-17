package com.example.art_course.common;

import com.example.art_course.contracts.IEnrollmentContract;
import com.example.art_course.modules.notification.NotificationService;
import com.example.art_course.modules.order.service.OrderService;
import com.example.art_course.modules.payment.gateway.MockPaymentGateway;
import com.example.art_course.modules.payment.gateway.PaymentGatewayFactory;
import com.example.art_course.modules.payment.service.EnrollmentGrantService;
import com.example.art_course.modules.payment.service.PaymentService;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

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
        if (context == null) {
            if (container == null) {
                container = new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("test_art_course_dev")
                        .withUsername("test_agent")
                        .withPassword("test_secret_pw");
                container.start();

                String jdbcUrl = container.getJdbcUrl();
                assertIsolatedContainerUrl(jdbcUrl);

                loadBaseSchemaAndMigrate();
            }

            context = new AnnotationConfigApplicationContext(TestJpaConfig.class);
        }
        return context;
    }

    public static synchronized PostgreSQLContainer<?> getContainer() {
        getContext();
        return container;
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

    private static void loadBaseSchemaAndMigrate() {
        try {
            Path baseSqlPath = Path.of("docs", "ART_COUSE.sql");
            String filteredSql = Files.readAllLines(baseSqlPath, StandardCharsets.UTF_8).stream()
                    .filter(line -> !line.startsWith("\\restrict"))
                    .filter(line -> !line.startsWith("\\unrestrict"))
                    .filter(line -> !line.startsWith("SET transaction_timeout"))
                    .filter(line -> !line.contains("OWNER TO"))
                    .filter(line -> !line.startsWith("COPY public."))
                    .filter(line -> !line.equals("\\."))
                    .collect(Collectors.joining("\n"));

            try (Connection conn = container.createConnection("");
                 Statement stmt = conn.createStatement()) {
                assertIsolatedContainerUrl(conn.getMetaData().getURL());
                stmt.execute(filteredSql);
            }

            Flyway flyway = Flyway.configure()
                    .dataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword())
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .load();
            flyway.migrate();
        } catch (Exception e) {
            throw new RuntimeException("Lỗi khởi tạo schema nền và Flyway trên Testcontainers postgres:16-alpine", e);
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
            props.setProperty("hibernate.hbm2ddl.auto", "validate");
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
                com.example.art_course.modules.course.repository.CourseRepository courseRepo,
                com.example.art_course.modules.product.repository.SupplyRepository supplyRepo,
                EntityManagerFactory emf,
                ConfigService configService
        ) {
            jakarta.persistence.EntityManager sharedEm = org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(emf);
            return new OrderService(orderRepo, orderDetailRepo, cartRepo, cartItemRepo, courseRepo, supplyRepo, sharedEm, configService);
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
            return new PaymentGatewayFactory(List.of(new MockPaymentGateway()));
        }

        @Bean
        public PaymentService paymentService(
                com.example.art_course.modules.order.repository.OrderRepository orderRepo,
                com.example.art_course.modules.order.repository.OrderDetailRepository orderDetailRepo,
                com.example.art_course.modules.payment.repository.PaymentRepository paymentRepo,
                com.example.art_course.modules.payment.repository.WebhookLogRepository webhookLogRepo,
                com.example.art_course.modules.payment.repository.MoneyLedgerRepository moneyLedgerRepo,
                com.example.art_course.modules.product.repository.SupplyRepository supplyRepo,
                PaymentGatewayFactory gatewayFactory,
                EnrollmentGrantService grantService,
                NotificationService notifService,
                ConfigService configService
        ) {
            return new PaymentService(orderRepo, orderDetailRepo, paymentRepo, webhookLogRepo, moneyLedgerRepo,
                    supplyRepo, gatewayFactory, grantService, notifService, configService);
        }
    }
}
