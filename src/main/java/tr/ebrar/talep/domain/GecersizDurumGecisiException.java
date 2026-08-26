package tr.ebrar.talep.domain;


/**
 * Durum makinesinde tanimli olmayan bir gecis denendi. HTTP 409 (cakisma).
 *
 * <p>Bu istisna neden hata paketinde degil de domain paketinde: icinde
 * {@link TalepDurumu} tasiyor. Hata paketinde dursaydi domain ile hata paketleri
 * birbirini isaret eder, yani dongusel bagimlilik olusurdu. ArchUnit testi
 * (MimariKurallariTest.dongusuzBagimlilik) bunu yakaladi ve tasidik.
 *
 * <p>400 degil 409 secildi: istek bicimsel olarak gecerli, sorun kaynagin
 * o anki durumuyla cakismasi. Ayni istek talep BEKLEMEDE durumundayken
 * basarili olurdu.
 */
public class GecersizDurumGecisiException extends tr.ebrar.talep.hata.IsKuraliException {

    private final TalepDurumu mevcutDurum;
    private final TalepDurumu hedefDurum;

    public GecersizDurumGecisiException(TalepDurumu mevcutDurum, TalepDurumu hedefDurum) {
        super("GECERSIZ_DURUM_GECISI",
                "Talep '%s' durumundan '%s' durumuna gecemez. Izinli hedefler: %s"
                        .formatted(mevcutDurum, hedefDurum, mevcutDurum.izinliHedefler()));
        this.mevcutDurum = mevcutDurum;
        this.hedefDurum = hedefDurum;
    }

    public TalepDurumu getMevcutDurum() {
        return mevcutDurum;
    }

    public TalepDurumu getHedefDurum() {
        return hedefDurum;
    }
}
