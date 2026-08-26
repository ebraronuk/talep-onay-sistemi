package tr.ebrar.talep.service.komut;

import tr.ebrar.talep.domain.TalepDurumu;

/**
 * Amirin verdigi karar.
 *
 * <p>Istemciden dogrudan hedef durum ("ONAYLANDI") almak yerine karar aliyoruz.
 * Sebep: istemci durum makinesinin ic isleyisini bilmek zorunda kalmasin.
 * Yarin araya bir durum girerse istemci kodu degismez.
 */
public enum Karar {

    ONAYLA(TalepDurumu.ONAYLANDI),
    REDDET(TalepDurumu.REDDEDILDI);

    private final TalepDurumu hedefDurum;

    Karar(TalepDurumu hedefDurum) {
        this.hedefDurum = hedefDurum;
    }

    public TalepDurumu hedefDurum() {
        return hedefDurum;
    }
}
