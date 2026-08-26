package tr.ebrar.talep.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tr.ebrar.talep.domain.Rol;

import io.jsonwebtoken.Claims;

/**
 * Authorization basligindaki tokeni okuyup guvenlik baglamina kimlik yerlestirir.
 *
 * <p>Token yoksa ya da gecersizse hicbir sey yapmiyor: istek kimliksiz devam eder
 * ve korumali bir uca gidiyorsa Spring Security zaten 401 dondurur. Filtrenin
 * burada hata firlatmasi, permitAll uclarini da bozardi.
 */
@Component
public class JwtKimlikFiltresi extends OncePerRequestFilter {

    private static final String BASLIK = "Authorization";
    private static final String ONEK = "Bearer ";

    private final JwtUretici jwtUretici;

    public JwtKimlikFiltresi(JwtUretici jwtUretici) {
        this.jwtUretici = jwtUretici;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest istek,
                                    @NonNull HttpServletResponse yanit,
                                    @NonNull FilterChain zincir) throws ServletException, IOException {

        tokenOku(istek)
                .flatMap(jwtUretici::coz)
                .ifPresent(istemler -> kimlikYerlestir(istemler, istek));

        zincir.doFilter(istek, yanit);
    }

    private void kimlikYerlestir(Claims istemler, HttpServletRequest istek) {
        // Zaten kimlik varsa dokunma: baska bir filtre koymus olabilir.
        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            return;
        }

        Rol rol = jwtUretici.rolOku(istemler);
        var kimlik = new UsernamePasswordAuthenticationToken(
                istemler.getSubject(),
                null,
                List.of(new SimpleGrantedAuthority(rol.yetkiAdi())));
        kimlik.setDetails(new WebAuthenticationDetailsSource().buildDetails(istek));

        SecurityContextHolder.getContext().setAuthentication(kimlik);
    }

    private java.util.Optional<String> tokenOku(HttpServletRequest istek) {
        String deger = istek.getHeader(BASLIK);
        if (deger == null || !deger.startsWith(ONEK)) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(deger.substring(ONEK.length()).trim());
    }
}
