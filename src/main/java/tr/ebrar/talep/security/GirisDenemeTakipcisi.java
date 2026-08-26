package tr.ebrar.talep.security;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import tr.ebrar.talep.hata.CokFazlaDenemeException;

/**
 * Kaba kuvvet denemelerine karsi basit sayac.
 *
 * <p>Bu, denetimde cikan bir acigin karsiligi. Once giris ucunda hicbir sinir yoktu:
 * saniyede yuzlerce sifre denemesi yapmak mumkundu. BCrypt her denemeyi yaklasik
 * 100 ms'e cikardigi icin saldiri yavas, ama imkansiz degildi; ayrica bu, ucuz bir
 * hizmet disi birakma (DoS) yoluydu, cunku her deneme bir CPU cekirdegini mesgul ediyor.
 *
 * <p><b>Bilinen sinir:</b> sayac bu uygulama ornegine ait, bellekte. Uygulama birden
 * fazla kopya halinde calistirilirsa saldirgan kopyalar arasinda gezinerek limiti
 * carpar. Dogru cozum yuk dengeleyici veya API gateway seviyesinde hiz sinirlama,
 * ya da paylasimli bir sayac (Redis). Tek konteynerli bu dagitim icin bu yeterli ve
 * "hic yok"tan cok daha iyi.
 *
 * <p>Anahtar olarak kullanici adi kullaniliyor, IP degil. Sebep: kurumsal aglarda
 * yuzlerce kullanici tek bir NAT adresinin arkasindan cikiyor; IP bazli sayim tum
 * kurumu birlikte kilitler. Bunun karsiligi, saldirganin farkli kullanici adlariyla
 * denemeye devam edebilmesi; onu durduran sey BCrypt'in maliyeti.
 */
@Component
public class GirisDenemeTakipcisi {

    private static final Logger log = LoggerFactory.getLogger(GirisDenemeTakipcisi.class);

    static final int AZAMI_DENEME = 5;
    static final Duration KILIT_SURESI = Duration.ofMinutes(15);

    private final Cache<String, AtomicInteger> denemeler;

    public GirisDenemeTakipcisi() {
        this.denemeler = Caffeine.newBuilder()
                .expireAfterWrite(KILIT_SURESI)
                // Ust sinir: saldirgan rastgele kullanici adlariyla bellegi sisiremesin.
                .maximumSize(10_000)
                .build();
    }

    /** Giris denemesinden ONCE cagrilir. Limit asilmissa exception firlatir. */
    public void kontrolEt(String kullaniciAdi) {
        AtomicInteger sayac = denemeler.getIfPresent(anahtar(kullaniciAdi));
        if (sayac != null && sayac.get() >= AZAMI_DENEME) {
            throw new CokFazlaDenemeException(KILIT_SURESI.toSeconds());
        }
    }

    public void basarisizDeneme(String kullaniciAdi) {
        int guncel = denemeler
                .get(anahtar(kullaniciAdi), k -> new AtomicInteger(0))
                .incrementAndGet();

        if (guncel == AZAMI_DENEME) {
            log.warn(
                    "Giris denemesi limiti asildi, kullanici {} dakika kilitlendi: {}",
                    KILIT_SURESI.toMinutes(),
                    kullaniciAdi);
        }
    }

    public void basariliGiris(String kullaniciAdi) {
        denemeler.invalidate(anahtar(kullaniciAdi));
    }

    /**
     * Kullanici adi buyuk/kucuk harf duyarsiz normallestiriliyor; aksi halde
     * "Ayse" ve "ayse" ayri sayaclar olur ve limit iki katina cikar.
     */
    private String anahtar(String kullaniciAdi) {
        return kullaniciAdi == null ? "" : kullaniciAdi.toLowerCase(java.util.Locale.ROOT);
    }
}
