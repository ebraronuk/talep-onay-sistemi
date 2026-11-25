package tr.ebrar.talep.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tr.ebrar.talep.domain.GidenOlay;
import tr.ebrar.talep.domain.GidenOlayDurumu;
import tr.ebrar.talep.repository.GidenOlayRepository;
import tr.ebrar.talep.service.olay.TalepDurumuDegistiOlayi;

/**
 * Olay kutusu (transactional outbox). Gerekce: docs/decisions.md K-023.
 */
@Service
@Transactional(readOnly = true)
public class GidenOlayServisi {

    private static final Logger log = LoggerFactory.getLogger(GidenOlayServisi.class);

    private final GidenOlayRepository gidenOlayRepository;
    private final BildirimServisi bildirimServisi;

    public GidenOlayServisi(GidenOlayRepository gidenOlayRepository, BildirimServisi bildirimServisi) {
        this.gidenOlayRepository = gidenOlayRepository;
        this.bildirimServisi = bildirimServisi;
    }

    /**
     * Olayi, yayinlayanin transaction'i icinde yazar.
     *
     * <p>Duz {@code @EventListener} cunku eszamanli calisiyor: yazim patlarsa hata
     * yayinlayana cikiyor ve durum degisikligi de geri sariyor. MANDATORY ise
     * transaction disinda yayinlanan olayi reddediyor. {@code @TransactionalEventListener}
     * olsaydi o olay sessizce yok sayilirdi; kaybolmasin diye kurulan yerde bu olmaz.
     */
    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void kaydet(TalepDurumuDegistiOlayi olay) {
        gidenOlayRepository.save(new GidenOlay(
                olay.talepId(),
                olay.talepBasligi(),
                olay.oncekiDurum(),
                olay.yeniDurum(),
                olay.islemYapanId(),
                olay.talepSahibiId(),
                olay.birimId(),
                Instant.now()));
    }

    public List<Long> islenecekler(int azami) {
        return gidenOlayRepository.islenecekKimlikler(Instant.now(), Limit.of(azami));
    }

    /**
     * Bildirimi yazar ve olayi ayni transaction'da kapatir.
     *
     * <p>Ikisi birlikte commit oldugu icin arada kapanan uygulama ne bildirimi iki kez
     * uretir ne de olayi islenmis gosterip bildirimsiz birakir. Kilitlenecek satir yoksa
     * olay baska bir isleyicide ya da coktan islenmis demektir.
     */
    @Transactional
    public void isle(Long olayId) {
        gidenOlayRepository.islemekIcinKilitle(olayId).ifPresent(olay -> {
            bildirimServisi.durumDegisikliginiBildir(olayaCevir(olay));
            olay.islendi(Instant.now());
        });
    }

    /** Isleme transaction'i geri sardigi icin hata ayri bir transaction'da kaydediliyor. */
    @Transactional
    public void basarisizligiKaydet(Long olayId, RuntimeException hata) {
        gidenOlayRepository.islemekIcinKilitle(olayId).ifPresent(olay -> {
            olay.denemeBasarisiz(hata.toString(), Instant.now());

            if (olay.getDurum() == GidenOlayDurumu.BASARISIZ) {
                log.error("Bildirim uretilemedi, denemekten vazgecildi: olayId={}, talepId={}, gecis={}->{}, deneme={}",
                        olayId, olay.getTalepId(), olay.getOncekiDurum(), olay.getYeniDurum(),
                        olay.getDenemeSayisi(), hata);
            } else {
                log.warn("Bildirim uretilemedi, yeniden denenecek: olayId={}, talepId={}, deneme={}, sonraki={}",
                        olayId, olay.getTalepId(), olay.getDenemeSayisi(), olay.getSonrakiDeneme(), hata);
            }
        });
    }

    private TalepDurumuDegistiOlayi olayaCevir(GidenOlay olay) {
        return new TalepDurumuDegistiOlayi(
                olay.getTalepId(),
                olay.getTalepBasligi(),
                olay.getOncekiDurum(),
                olay.getYeniDurum(),
                olay.getIslemYapanId(),
                olay.getTalepSahibiId(),
                olay.getBirimId());
    }
}
