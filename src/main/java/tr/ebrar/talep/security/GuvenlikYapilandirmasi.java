package tr.ebrar.talep.security;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.fasterxml.jackson.databind.ObjectMapper;

import tr.ebrar.talep.hata.HataYaniti;

/**
 * Guvenlik yapilandirmasi.
 *
 * <p>Temel karar: stateless. Sunucu tarafinda oturum yok, her istek kendi tokenini
 * getiriyor. Bunun dogal sonucu olarak CSRF korumasi kapali: CSRF saldirisi
 * tarayicinin cerezi otomatik gondermesine dayanir, biz cerez kullanmiyoruz.
 *
 * <p>Uclarin yetki tablosu icin bkz. docs/guvenlik.md
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(JwtAyarlari.class)
public class GuvenlikYapilandirmasi {

    private final JwtKimlikFiltresi jwtKimlikFiltresi;
    private final ObjectMapper jsonYazici;

    public GuvenlikYapilandirmasi(JwtKimlikFiltresi jwtKimlikFiltresi, ObjectMapper jsonYazici) {
        this.jwtKimlikFiltresi = jwtKimlikFiltresi;
        this.jsonYazici = jsonYazici;
    }

    @Bean
    SecurityFilterChain guvenlikZinciri(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsAyarlari()))
                .sessionManagement(oturum -> oturum.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(istekler -> istekler
                        // Giris ucu acik olmak zorunda, yoksa kimse token alamaz.
                        .requestMatchers(HttpMethod.POST, "/api/kimlik/giris").permitAll()
                        // Konteyner ve yuk dengeleyici saglik kontrolu icin acik.
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                        // API dokumani gelistirme kolayligi icin acik; kapatilmak istenirse
                        // tek satir. Uretimde kapatmak makul, burada bilerek acik.
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Geri kalan actuator uclari operasyon bilgisi sizdirir.
                        .requestMatchers("/actuator/**").hasRole("YONETICI")
                        .anyRequest().authenticated())
                .exceptionHandling(hata -> hata
                        .authenticationEntryPoint((istek, yanit, e) ->
                                hataYaz(yanit, HttpServletResponse.SC_UNAUTHORIZED,
                                        "KIMLIK_DOGRULANAMADI", "Gecerli bir token gondermelisiniz"))
                        .accessDeniedHandler((istek, yanit, e) ->
                                hataYaz(yanit, HttpServletResponse.SC_FORBIDDEN,
                                        "YETKISIZ_ISLEM", "Bu islem icin yetkiniz yok")))
                .addFilterBefore(jwtKimlikFiltresi, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * BCrypt gucu 10 (varsayilan). Her ozette rastgele tuz (salt) uretilir,
     * bu yuzden ayni sifrenin iki kullanicidaki ozeti farkli cikar ve
     * gokkusagi tablosu (rainbow table) ise yaramaz.
     */
    @Bean
    PasswordEncoder sifreKodlayici() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager kimlikYoneticisi(KullaniciDetayServisi kullaniciDetayServisi,
                                           PasswordEncoder sifreKodlayici) {
        DaoAuthenticationProvider saglayici = new DaoAuthenticationProvider(kullaniciDetayServisi);
        saglayici.setPasswordEncoder(sifreKodlayici);
        // Kullanici bulunamadiginda da sifre kontrolu yapilmis gibi zaman harcansin.
        // Aksi halde yanit suresinden "bu kullanici adi var mi" bilgisi sizar.
        saglayici.setHideUserNotFoundExceptions(true);
        return new org.springframework.security.authentication.ProviderManager(saglayici);
    }

    @Bean
    CorsConfigurationSource corsAyarlari() {
        CorsConfiguration ayar = new CorsConfiguration();
        // Vite gelistirme sunucusu. Uretimde ters vekil (reverse proxy) arkasinda
        // ayni kaynak kullanilacagi icin bu listenin bosaltilmasi gerekir.
        ayar.setAllowedOrigins(List.of("http://localhost:5173", "http://localhost:4173"));
        ayar.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        ayar.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        ayar.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource kaynak = new UrlBasedCorsConfigurationSource();
        kaynak.registerCorsConfiguration("/api/**", ayar);
        return kaynak;
    }

    private void hataYaz(HttpServletResponse yanit, int durum, String kod, String mesaj) throws java.io.IOException {
        // Guvenlik filtresi zincirinde firlatilan hatalar @RestControllerAdvice'a ugramaz;
        // bu yuzden ayni sozlesmeyi burada elle uretmek zorundayiz.
        yanit.setStatus(durum);
        yanit.setContentType(MediaType.APPLICATION_JSON_VALUE);
        yanit.setCharacterEncoding("UTF-8");
        jsonYazici.writeValue(yanit.getWriter(), HataYaniti.of(kod, mesaj));
    }
}
