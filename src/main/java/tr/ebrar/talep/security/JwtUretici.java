package tr.ebrar.talep.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import tr.ebrar.talep.domain.Rol;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT uretme ve dogrulama.
 *
 * <p>Token icinde kullanici adi (subject) ve rol tasiniyor. Rolu tokena koymak
 * her istekte kullanici tablosuna gitmemizi engelliyor.
 *
 * <p>Bedeli su: kullanici pasife alinsa bile elindeki token suresi bitene kadar
 * gecerli kalir. 60 dakikalik omur ile bunu kabul ediyoruz. Aninda iptal
 * gerekseydi kara liste tablosu tutmak gerekirdi, o da her istekte bir sorgu
 * demek olurdu; yani stateless olmaktan vazgecmek.
 */
@Component
public class JwtUretici {

    private static final Logger log = LoggerFactory.getLogger(JwtUretici.class);
    private static final String ROL_ISTEMI = "rol";

    private final SecretKey anahtar;
    private final Duration gecerlilikSuresi;

    public JwtUretici(JwtAyarlari ayarlar) {
        this.anahtar = Keys.hmacShaKeyFor(ayarlar.gizliAnahtar().getBytes(StandardCharsets.UTF_8));
        this.gecerlilikSuresi = Duration.ofMinutes(ayarlar.gecerlilikSuresiDakika());
    }

    public String uret(String kullaniciAdi, Rol rol) {
        Instant simdi = Instant.now();
        return Jwts.builder()
                .subject(kullaniciAdi)
                .claim(ROL_ISTEMI, rol.name())
                .issuedAt(Date.from(simdi))
                .expiration(Date.from(simdi.plus(gecerlilikSuresi)))
                .signWith(anahtar)
                .compact();
    }

    /**
     * Tokeni dogrular. Gecersizse bos Optional doner, exception firlatmaz:
     * cagiran taraf (filtre) zaten "gecerli degilse kimlik atama" diyecek.
     */
    public Optional<Claims> coz(String token) {
        try {
            Claims istemler = Jwts.parser()
                    .verifyWith(anahtar)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(istemler);
        } catch (JwtException | IllegalArgumentException e) {
            // Suresi dolmus, imzasi bozuk ya da hic token olmayan bir metin.
            // Detayi debug seviyesinde birakiyoruz; her hatali istekte WARN basmak
            // log'u kirletir ve gercek problemleri gizler.
            log.debug("Gecersiz JWT: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public Rol rolOku(Claims istemler) {
        return Rol.valueOf(istemler.get(ROL_ISTEMI, String.class));
    }

    public long gecerlilikSuresiSaniye() {
        return gecerlilikSuresi.toSeconds();
    }
}
