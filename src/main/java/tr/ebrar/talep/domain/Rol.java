package tr.ebrar.talep.domain;

/**
 * Kullanici rolleri. Yetki kurallari icin bkz. docs/guvenlik.md
 */
public enum Rol {

    /** Kendi talebini olusturur ve goruntuler. */
    PERSONEL,

    /** Kendi biriminin bekleyen taleplerini onaylar veya reddeder. */
    AMIR,

    /**
     * Tum birimleri goruntuler ve rapor alir. Tutar limitini asan taleplerde
     * (bkz. OnayAyarlari) ikinci kademe onayi verir; birinci kademeye karisamaz.
     */
    YONETICI;

    /** Spring Security yetki adi bicimi: ROLE_ oneki ile. */
    public String yetkiAdi() {
        return "ROLE_" + name();
    }
}
