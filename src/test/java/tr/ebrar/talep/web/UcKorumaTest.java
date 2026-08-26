package tr.ebrar.talep.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tr.ebrar.talep.destek.VeritabaniTestTemeli;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * "Bir ucu yazip guvenlik kuralini koymayi unuttum" senaryosuna karsi bekci.
 *
 * <p>Elle yazilan yetki testleri var olan uclari kontrol eder; bu test ise
 * uygulamanin kendi ucu listesini okuyup her birini tokensiz deniyor. Yarin
 * yeni bir controller metodu eklenir ve kural yazilmazsa burasi kirmizi yanar.
 *
 * <p>Kasitli acik uclar listede acikca yaziyor. Bir ucu buraya eklemek bilincli
 * bir hareket olmali; listeye eklerken insan iki kere dusunur.
 */
@SpringBootTest
@AutoConfigureMockMvc
class UcKorumaTest extends VeritabaniTestTemeli {

    /** Kimlik dogrulamasi olmadan erisilebilen uclar. Buraya ekleme yaparken dikkat. */
    private static final Set<String> KASITLI_ACIK_UCLAR = Set.of(
            "POST /api/kimlik/giris"
    );

    @Autowired
    private MockMvc mockMvc;

    // Actuator kendi eslestiricisini de kaydediyor (controllerEndpointHandlerMapping),
    // o yuzden isimle secmek gerekiyor.
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping ucEslestirici;

    @Test
    @DisplayName("Tum /api uclari tokensiz istekte 401 doner")
    void hicbirUcKorumasizDegil() {
        List<String> korumasizUclar = new ArrayList<>();

        ucEslestirici.getHandlerMethods().forEach((bilgi, metot) -> {
            for (String desen : desenler(bilgi)) {
                if (!desen.startsWith("/api")) {
                    continue;
                }
                for (HttpMethod httpMetodu : httpMetotlari(bilgi)) {
                    String imza = httpMetodu.name() + " " + desen;
                    if (KASITLI_ACIK_UCLAR.contains(imza)) {
                        continue;
                    }
                    if (!tokensizReddediliyorMu(httpMetodu, desen)) {
                        korumasizUclar.add(imza);
                    }
                }
            }
        });

        assertThat(korumasizUclar)
                .as("bu uclar tokensiz erisime acik kalmis")
                .isEmpty();
    }

    @Test
    @DisplayName("Test, uc listesini gercekten okuyabiliyor")
    void ucListesiBos_degil() {
        // Yukaridaki test, uc listesi bos gelirse de yesil yanardi. Bu, o yanlis
        // guvenin onune geciyor.
        long apiUcSayisi = ucEslestirici.getHandlerMethods().keySet().stream()
                .flatMap(bilgi -> desenler(bilgi).stream())
                .filter(desen -> desen.startsWith("/api"))
                .count();

        assertThat(apiUcSayisi).isGreaterThanOrEqualTo(10);
    }

    private boolean tokensizReddediliyorMu(HttpMethod httpMetodu, String desen) {
        String yol = desen.replaceAll("\\{[^}]+}", "1");
        try {
            int durum = mockMvc.perform(MockMvcRequestBuilders.request(httpMetodu, yol))
                    .andReturn().getResponse().getStatus();
            return durum == 401;
        } catch (Exception e) {
            throw new IllegalStateException("Uc denenemedi: " + httpMetodu + " " + yol, e);
        }
    }

    private Set<String> desenler(RequestMappingInfo bilgi) {
        return bilgi.getPathPatternsCondition() == null
                ? Set.of()
                : bilgi.getPathPatternsCondition().getPatternValues();
    }

    private Set<HttpMethod> httpMetotlari(RequestMappingInfo bilgi) {
        Set<org.springframework.web.bind.annotation.RequestMethod> metotlar =
                bilgi.getMethodsCondition().getMethods();
        if (metotlar.isEmpty()) {
            return Set.of(HttpMethod.GET);
        }
        return metotlar.stream().map(m -> HttpMethod.valueOf(m.name())).collect(java.util.stream.Collectors.toSet());
    }
}
