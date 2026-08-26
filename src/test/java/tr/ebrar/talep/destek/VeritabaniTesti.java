package tr.ebrar.talep.destek;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import tr.ebrar.talep.config.JpaDenetimYapilandirmasi;

/**
 * Repository testleri icin ortak kurulum.
 *
 * <ul>
 *   <li>{@code replace = NONE}: Spring'in gomulu veritabanini devreye sokmasi engellenir,
 *       boylece {@link VeritabaniTestTemeli} icindeki gercek PostgreSQL konteyneri kullanilir.</li>
 *   <li>Denetim yapilandirmasi acikca import edilir; {@code @DataJpaTest} kendiliginden
 *       {@code @Configuration} siniflarini yuklemez ve denetim alanlari NOT NULL oldugu icin
 *       import edilmezse her insert patlar.</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaDenetimYapilandirmasi.class)
public @interface VeritabaniTesti {
}
