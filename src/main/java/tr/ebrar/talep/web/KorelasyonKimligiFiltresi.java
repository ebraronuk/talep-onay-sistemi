package tr.ebrar.talep.web;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Her istege bir korelasyon kimligi takar.
 *
 * <p>Bir kullanici "saat 14:32'de hata aldim" dedi diyelim. Korelasyon kimligi
 * olmadan o dakikadaki butun log satirlarini elle ayiklamak gerekir. Kimlik
 * varsa tek bir grep o istegin tum satirlarini getirir.
 *
 * <p>Istemci kendi kimligini gonderebilir (X-Korelasyon-Kimligi). Boylece on yuz
 * ile arka ucun loglari ayni kimlik uzerinden eslesir. Gelen deger uzunluk
 * sinirina takilirsa yok sayilir: log dosyasina kontrolsuz uzunlukta veri
 * yazdirmak istemiyoruz.
 *
 * <p>Filtre en basta calisiyor (HIGHEST_PRECEDENCE + 1): guvenlik zincirinde
 * uretilen hata satirlarinin da kimligi olsun.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class KorelasyonKimligiFiltresi extends OncePerRequestFilter {

    public static final String BASLIK = "X-Korelasyon-Kimligi";
    static final String MDC_ANAHTARI = "korelasyonKimligi";
    private static final int AZAMI_UZUNLUK = 64;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest istek,
                                    @NonNull HttpServletResponse yanit,
                                    @NonNull FilterChain zincir) throws ServletException, IOException {

        String kimlik = kimlikBelirle(istek);
        MDC.put(MDC_ANAHTARI, kimlik);
        yanit.setHeader(BASLIK, kimlik);

        try {
            zincir.doFilter(istek, yanit);
        } finally {
            // MDC bir ThreadLocal. Temizlenmezse havuzdan gelen bir sonraki istek
            // onceki istegin kimligiyle loglanir; bu da hata ayiklarken yanlis ize
            // dusurur. finally sart.
            MDC.remove(MDC_ANAHTARI);
        }
    }

    private String kimlikBelirle(HttpServletRequest istek) {
        String gelen = istek.getHeader(BASLIK);
        if (gelen != null && !gelen.isBlank() && gelen.length() <= AZAMI_UZUNLUK) {
            return gelen;
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
