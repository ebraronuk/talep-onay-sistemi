package tr.ebrar.talep.hata;

import java.time.Instant;
import java.util.List;

/**
 * Tum hatalarin tek bicimi: { kod, mesaj, detaylar, zaman }.
 *
 * <p>web paketinde degil burada duruyor cunku iki ayri yer uretiyor: normal
 * akista {@code HataYakalayici}, guvenlik filtre zincirinde ise
 * {@code GuvenlikYapilandirmasi} (oradaki hatalar controller'a hic ulasmadigi
 * icin advice'a ugramiyor). web paketinde kalsaydi security -> web -> service
 * -> security seklinde bir dongu olusuyordu.
 *
 * <p>Istemci tarafinda tek bir hata isleyici yazabilmek icin sozlesme her ucta ayni.
 * "detaylar" yalnizca alan bazli dogrulama hatalarinda dolu geliyor, digerlerinde null.
 */
public record HataYaniti(
        String kod,
        String mesaj,
        List<AlanHatasi> detaylar,
        Instant zaman
) {

    public static HataYaniti of(String kod, String mesaj) {
        // detaylar null degil bos liste: sozlesme her yanitta ayni dursun,
        // istemci "alan var mi" diye kontrol etmek zorunda kalmasin.
        return new HataYaniti(kod, mesaj, List.of(), Instant.now());
    }

    public static HataYaniti of(String kod, String mesaj, List<AlanHatasi> detaylar) {
        return new HataYaniti(kod, mesaj, detaylar, Instant.now());
    }

    public record AlanHatasi(String alan, String mesaj) {
    }
}
