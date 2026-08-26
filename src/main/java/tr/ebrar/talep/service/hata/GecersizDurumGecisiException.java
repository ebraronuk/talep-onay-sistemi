package tr.ebrar.talep.service.hata;

import tr.ebrar.talep.domain.TalepDurumu;

/**
 * Durum makinesinde tanimli olmayan bir gecis denendi. HTTP 409 (cakisma).
 *
 * <p>400 degil 409 secildi: istek bicimsel olarak gecerli, sorun kaynagin
 * o anki durumuyla cakismasi. Ayni istek talep BEKLEMEDE durumundayken
 * basarili olurdu.
 */
public class GecersizDurumGecisiException extends IsKuraliException {

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
