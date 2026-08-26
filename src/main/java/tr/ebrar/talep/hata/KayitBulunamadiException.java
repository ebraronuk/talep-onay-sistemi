package tr.ebrar.talep.hata;

/** Istenen kayit yok. HTTP 404. */
public class KayitBulunamadiException extends IsKuraliException {

    public KayitBulunamadiException(String varlikAdi, Object kimlik) {
        super("KAYIT_BULUNAMADI", varlikAdi + " bulunamadi: " + kimlik);
    }
}
