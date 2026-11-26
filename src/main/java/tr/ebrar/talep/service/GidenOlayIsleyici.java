package tr.ebrar.talep.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Kutudaki olaylari duzenli araliklarla isler.
 *
 * <p>Transaction burada degil {@link GidenOlayServisi} icinde: her olay kendi
 * transaction'inda islenmeli ki biri patlayinca ayni turdaki digerleri de geri sarmasin.
 */
@Component
public class GidenOlayIsleyici {

    private static final int PARTI_BOYUTU = 50;

    private final GidenOlayServisi gidenOlayServisi;

    public GidenOlayIsleyici(GidenOlayServisi gidenOlayServisi) {
        this.gidenOlayServisi = gidenOlayServisi;
    }

    @Scheduled(fixedDelayString = "${talep.giden-olay.aralik}")
    public void bekleyenleriIsle() {
        for (Long olayId : gidenOlayServisi.islenecekler(PARTI_BOYUTU)) {
            try {
                gidenOlayServisi.isle(olayId);
            } catch (RuntimeException e) {
                // Yutulmuyor: olay kutuda kaliyor ve hata satirina yaziliyor. Donguyu
                // kesmiyoruz, cunku bozuk bir olay arkasindakileri bekletmemeli.
                gidenOlayServisi.basarisizligiKaydet(olayId, e);
            }
        }
    }
}
