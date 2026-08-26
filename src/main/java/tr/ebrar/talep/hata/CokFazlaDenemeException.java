package tr.ebrar.talep.hata;

/** Kisa surede cok fazla basarisiz giris denemesi. HTTP 429. */
public class CokFazlaDenemeException extends IsKuraliException {

    public CokFazlaDenemeException(long kalanSaniye) {
        super(
                "COK_FAZLA_DENEME",
                "Cok fazla basarisiz giris denemesi yapildi. "
                        + kalanSaniye
                        + " saniye sonra tekrar deneyin.");
    }
}
