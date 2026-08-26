package tr.ebrar.talep.service.hata;

/** Is kurali geregi yapilamayan islem (ornegin taslak olmayan talebin duzenlenmesi). HTTP 400. */
public class GecersizIslemException extends IsKuraliException {

    public GecersizIslemException(String mesaj) {
        super("GECERSIZ_ISLEM", mesaj);
    }
}
