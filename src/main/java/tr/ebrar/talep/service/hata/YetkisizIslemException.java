package tr.ebrar.talep.service.hata;

/**
 * Kullanici kimlik dogrulamasindan gecti ama bu kayit uzerinde yetkisi yok. HTTP 403.
 *
 * <p>Mesajda kaydin varligina dair bilgi sizdirilmaz; "bu talep sizin degil" demek,
 * o id'de bir talep oldugunu dogrulamak olur.
 */
public class YetkisizIslemException extends IsKuraliException {

    public YetkisizIslemException(String mesaj) {
        super("YETKISIZ_ISLEM", mesaj);
    }
}
