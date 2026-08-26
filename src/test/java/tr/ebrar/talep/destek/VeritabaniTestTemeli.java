package tr.ebrar.talep.destek;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testler gercek PostgreSQL uzerinde calisir (bkz. docs/decisions.md K-006).
 *
 * <p>Konteyner <b>tekil</b>dir: statik blokta bir kez baslar ve JVM kapanana kadar
 * tum test siniflari tarafindan paylasilir. JUnit'in {@code @Container} anotasyonu
 * bilincli olarak kullanilmadi; o, sinif basina yeni konteyner acar ve test suresini
 * konteyner sayisi kadar katlar. Kapatma isini Ryuk (Testcontainers'in temizlik
 * konteyneri) ustlenir.
 */
@ActiveProfiles("test")
public abstract class VeritabaniTestTemeli {

    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"))
                    .withDatabaseName("talep_onay_test")
                    .withUsername("talep")
                    .withPassword("talep")
                    .withReuse(false);

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void veritabaniAyarlari(DynamicPropertyRegistry kayit) {
        kayit.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        kayit.add("spring.datasource.username", POSTGRES::getUsername);
        kayit.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
