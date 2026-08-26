package tr.ebrar.talep.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Is metrikleri.
 *
 * <p>Actuator kutudan JVM ve HTTP metrikleri veriyor: bellek, gecikme, hata orani.
 * Bunlar "sistem ayakta mi" sorusuna cevap veriyor ama "sistem ise yariyor mu"
 * sorusuna vermiyor. Bir sabah gelip "dun kac talep onaylandi, onay ne kadar
 * surdu" diye sorulunca cevap verebilmek icin bu sinif var.
 *
 * <p>Etiketler (tag) bilincli olarak enum degerleriyle sinirli. Etiket olarak
 * kullanici adi ya da talep basligi konsaydi her yeni deger yeni bir zaman serisi
 * uretirdi; metrik veritabanini sisiren klasik hata bu.
 */
@Component
public class TalepOlcumleri {

    private static final String OLUSTURULDU = "talep.olusturuldu";
    private static final String DURUM_DEGISTI = "talep.durum.degisti";
    private static final String KARAR_SURESI = "talep.karar.suresi";

    private final MeterRegistry kayitci;

    public TalepOlcumleri(MeterRegistry kayitci) {
        this.kayitci = kayitci;
    }

    public void olusturuldu(TalepTuru tur) {
        kayitci.counter(OLUSTURULDU, "tur", tur.name()).increment();
    }

    public void durumDegisti(TalepDurumu onceki, TalepDurumu yeni) {
        kayitci.counter(DURUM_DEGISTI, "onceki", onceki.name(), "yeni", yeni.name()).increment();
    }

    /**
     * Talebin acilisindan nihai karara kadar gecen sure.
     *
     * <p>Bu, sistemin is degerini olcen tek sayi: kullanicilar icin "talebim ne kadar
     * surede sonuclanir" demek. Teknik gecikme (p95 64 ms) bunun yaninda onemsiz.
     */
    public void kararVerildi(Instant talepAcilisi, TalepDurumu sonuc) {
        Timer.builder(KARAR_SURESI)
                .description("Talebin acilisindan nihai karara kadar gecen sure")
                .tag("sonuc", sonuc.name())
                .register(kayitci)
                .record(Duration.between(talepAcilisi, Instant.now()));
    }
}
