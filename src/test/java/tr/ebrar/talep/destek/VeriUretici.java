package tr.ebrar.talep.destek;

import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepTuru;

/**
 * Testler icin ornek veri uretir. Amac, her test sinifinda ayni yapicilari
 * elle yazmamak ve testin okunurlugunu kurulum gurultusune bogmamak.
 */
public final class VeriUretici {

    /** Tum test kullanicilari icin ayni BCrypt ozeti; duz metin karsiligi "Parola123!". */
    public static final String ORNEK_SIFRE_OZETI = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private VeriUretici() {
    }

    public static Birim birim(String kod) {
        return new Birim(kod, kod + " Mudurlugu");
    }

    public static Kullanici kullanici(String kullaniciAdi, Rol rol, Birim birim) {
        return new Kullanici(
                kullaniciAdi,
                kullaniciAdi.toUpperCase() + " Test",
                kullaniciAdi + "@ornek.gov.tr",
                ORNEK_SIFRE_OZETI,
                rol,
                birim);
    }

    public static Talep talep(String baslik, Kullanici talepEden) {
        return new Talep(baslik, baslik + " icin aciklama metni.", TalepTuru.DIGER, talepEden);
    }

    /**
     * Varliklara elle id verir.
     *
     * <p>Sadece Mockito testleri icin. Orada veritabani yok, dolayisiyla id de
     * uretilmiyor; ama servis kodu id uzerinden karsilastirma yapiyor
     * (sahibiMi, birim esitligi). Uretim kodunda id setter'i bilerek yok,
     * bu yuzden yansima (reflection) ile veriyoruz.
     */
    public static <T> T kimlikVer(T varlik, Long id) {
        org.springframework.test.util.ReflectionTestUtils.setField(varlik, "id", id);
        return varlik;
    }
}
