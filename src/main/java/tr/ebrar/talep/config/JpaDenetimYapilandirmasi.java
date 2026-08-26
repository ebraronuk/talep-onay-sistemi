package tr.ebrar.talep.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Denetim alanlarinin otomatik doldurulmasi.
 *
 * <p>{@code olusturan_kullanici} degeri guvenlik baglamindan okunur. Kimlik yoksa
 * (arka plan isi, migration sonrasi ilk yukleme, test) "sistem" yazilir; boylece
 * kolon hicbir zaman bos kalmaz ve NOT NULL kisiti korunur.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "denetimKullanicisi")
public class JpaDenetimYapilandirmasi {

    static final String SISTEM_KULLANICISI = "sistem";

    @Bean
    AuditorAware<String> denetimKullanicisi() {
        return () -> {
            Authentication kimlik = SecurityContextHolder.getContext().getAuthentication();
            if (kimlik == null || !kimlik.isAuthenticated() || "anonymousUser".equals(kimlik.getPrincipal())) {
                return Optional.of(SISTEM_KULLANICISI);
            }
            return Optional.of(kimlik.getName());
        };
    }
}
