package tr.ebrar.talep.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import tr.ebrar.talep.security.JwtAyarlari;

/**
 * Uretim profilinde acilista yapilan sagduyu kontrolleri.
 *
 * <p>Amac, gelistirme kolayliklarinin uretime sizmasini fark edilir kilmak.
 * En tehlikelisi gizli anahtar: application.yml icindeki varsayilan deger
 * depoda duruyor ve herkes tarafindan okunabiliyor. Bu degerle uretime cikmak,
 * imza anahtari yokmus gibi bir sey.
 *
 * <p>Uyari degil hata firlatiyoruz: uyari log'da kaybolur, hata dagitimi durdurur.
 * Yanlis yapilandirmayla ayakta duran bir sistem, ayakta olmayan sistemden kotudur.
 */
@Component
@Profile("prod")
public class UretimYapilandirmaDenetimi {

    private static final Logger log = LoggerFactory.getLogger(UretimYapilandirmaDenetimi.class);

    /** application.yml icindeki yerel gelistirme varsayilani. */
    private static final String GELISTIRME_ANAHTARI =
            "yerel-gelistirme-icin-en-az-256-bit-uzunlugunda-gecici-anahtar-degeri";

    private final JwtAyarlari jwtAyarlari;

    public UretimYapilandirmaDenetimi(JwtAyarlari jwtAyarlari) {
        this.jwtAyarlari = jwtAyarlari;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void denetle() {
        if (GELISTIRME_ANAHTARI.equals(jwtAyarlari.gizliAnahtar())) {
            throw new IllegalStateException(
                    "Uretim profilinde depodaki varsayilan JWT anahtari kullanilamaz. "
                            + "JWT_SECRET ortam degiskenini gizli yonetim sisteminden verin.");
        }
        log.info("Uretim yapilandirma denetimi gecti.");
    }
}
