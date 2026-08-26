package tr.ebrar.talep.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Talep durum makinesi.
 *
 * <pre>
 *   TASLAK ---> BEKLEMEDE ---> ONAYLANDI
 *                         \--> REDDEDILDI
 * </pre>
 *
 * <p>ONAYLANDI ve REDDEDILDI nihai durumlardir; bu durumlardan cikis yoktur.
 * Izin verilen gecisler burada, tek bir yerde tanimlidir. Servis katmani gecis
 * kontrolunu {@link #gecebilirMi(TalepDurumu)} uzerinden yapar, kendi kurallarini
 * yeniden yazmaz (bkz. docs/decisions.md K-008).
 */
public enum TalepDurumu {

    TASLAK,
    BEKLEMEDE,
    ONAYLANDI,
    REDDEDILDI;

    private static final Map<TalepDurumu, Set<TalepDurumu>> IZINLI_GECISLER = Map.of(
            TASLAK, EnumSet.of(BEKLEMEDE),
            BEKLEMEDE, EnumSet.of(ONAYLANDI, REDDEDILDI),
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
