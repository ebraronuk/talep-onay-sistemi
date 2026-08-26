package tr.ebrar.talep.hata;

/**
 * Alan diline ait tum hatalarin ortak atasi.
 *
 * <p>Her hatanin makine tarafindan okunabilir bir {@code kod}u vardir. Istemci
 * mesaj metnine degil bu koda bakar; boylece mesaj metni degistiginde istemci
 * kirilmaz. HTTP karsiliklari tek yerde, {@code HataYakalayici} icinde belirlenir.
 *
 * <p>RuntimeException turevi olmasi bilincli: Spring varsayilan olarak yalnizca
 * denetlenmeyen (unchecked) exception'larda rollback yapar.
 */
public abstract class IsKuraliException extends RuntimeException {

    private final String kod;

    protected IsKuraliException(String kod, String mesaj) {
        super(mesaj);
        this.kod = kod;
    }

    public String getKod() {
        return kod;
    }
}
