package tr.ebrar.talep.service;

import java.time.Instant;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tr.ebrar.talep.domain.GidenOlay;
import tr.ebrar.talep.repository.GidenOlayRepository;
import tr.ebrar.talep.service.olay.TalepDurumuDegistiOlayi;

/**
 * Olay kutusu (transactional outbox). Gerekce: docs/decisions.md K-023.
 */
@Service
@Transactional(readOnly = true)
public class GidenOlayServisi {

    private final GidenOlayRepository gidenOlayRepository;

    public GidenOlayServisi(GidenOlayRepository gidenOlayRepository) {
        this.gidenOlayRepository = gidenOlayRepository;
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
}
