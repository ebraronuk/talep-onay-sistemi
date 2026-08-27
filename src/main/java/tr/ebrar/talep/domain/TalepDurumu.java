package tr.ebrar.talep.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Talep durum makinesi.
 *
 * <pre>
 *   TASLAK ---> BEKLEMEDE ---> ONAYLANDI              (tutar limit altinda)
 *                     |
 *                     +------> YONETICI_ONAYINDA ---> ONAYLANDI   (tutar limit ustunde)
 *                     |                          \--> REDDEDILDI
 *                     \------> REDDEDILDI
 * </pre>
 *
 * <p>Hangi dala gidilecegini tutar belirliyor: birim amiri belli bir limite kadar
 * tek basina onaylayabilir, ustunu yonetici onayina gonderir. Kural servis
 * katmaninda (bkz. TalepServisi.karar), burada yalnizca gecisin izinli olup
 * olmadigi tanimli.
 *
 * <p>ONAYLANDI ve REDDEDILDI nihai durumlardir; bu durumlardan cikis yoktur.
 * Izin verilen gecisler burada, tek bir yerde tanimlidir. Servis katmani gecis
 * kontrolunu {@link #gecebilirMi(TalepDurumu)} uzerinden yapar, kendi kurallarini
 * yeniden yazmaz (bkz. docs/decisions.md K-008).
 */
public enum TalepDurumu {

    TASLAK,
    BEKLEMEDE,
    /** Birim amiri onayladi ama tutar limiti astigi icin yonetici onayi bekliyor. */
    YONETICI_ONAYINDA,
    ONAYLANDI,
    REDDEDILDI;

    private static final Map<TalepDurumu, Set<TalepDurumu>> IZINLI_GECISLER = Map.of(
            TASLAK, EnumSet.of(BEKLEMEDE),
            BEKLEMEDE, EnumSet.of(YONETICI_ONAYINDA, ONAYLANDI, REDDEDILDI),
            YONETICI_ONAYINDA, EnumSet.of(ONAYLANDI, REDDEDILDI),
            ONAYLANDI, EnumSet.noneOf(TalepDurumu.class),
            REDDEDILDI, EnumSet.noneOf(TalepDurumu.class)
    );

    /** Bu durumdan hedefe gecmeye izin var mi. */
    public boolean gecebilirMi(TalepDurumu hedef) {
        return IZINLI_GECISLER.get(this).contains(hedef);
    }

    /** Bu durumdan cikis yoksa true. */
    public boolean nihaiMi() {
        return IZINLI_GECISLER.get(this).isEmpty();
    }

    /** Bu durumdan gidilebilecek durumlar; hata mesajlarinda kullanilir. */
    public Set<TalepDurumu> izinliHedefler() {
        return Collections.unmodifiableSet(IZINLI_GECISLER.get(this));
    }
}
