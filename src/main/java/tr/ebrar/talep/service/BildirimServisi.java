package tr.ebrar.talep.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import tr.ebrar.talep.domain.Bildirim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.hata.KayitBulunamadiException;
import tr.ebrar.talep.hata.YetkisizIslemException;
import tr.ebrar.talep.repository.BildirimRepository;
import tr.ebrar.talep.repository.KullaniciRepository;
import tr.ebrar.talep.repository.TalepRepository;
import tr.ebrar.talep.service.dto.BildirimDto;
import tr.ebrar.talep.service.olay.TalepDurumuDegistiOlayi;

/**
 * Uygulama ici bildirim.
 *
 * <p>Neden olay (event) ile? Onay islemi ile bildirim yazmanin ayni transaction'da
 * olmasi gerekmiyor. Bildirim tablosuna yazim herhangi bir sebeple patlarsa onayin
 * geri sarmasini istemeyiz; onay is acisindan asil olan, bildirim yan etki.
 * Bu yuzden dinleyici AFTER_COMMIT'te ve kendi transaction'inda calisiyor.
 *
 * <p>Karsi taraf: bildirim yazilmazsa kimse haberdar olmaz ve bunu fark etmek zor.
 * Onun icin hata yutulmuyor, log'a ERROR olarak dusuyor.
 */
@Service
@Transactional(readOnly = true)
public class BildirimServisi {

    private static final Logger log = LoggerFactory.getLogger(BildirimServisi.class);

    private final BildirimRepository bildirimRepository;
    private final KullaniciRepository kullaniciRepository;
    private final TalepRepository talepRepository;

    public BildirimServisi(BildirimRepository bildirimRepository,
                           KullaniciRepository kullaniciRepository,
                           TalepRepository talepRepository) {
        this.bildirimRepository = bildirimRepository;
        this.kullaniciRepository = kullaniciRepository;
        this.talepRepository = talepRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void talepDurumuDegisti(TalepDurumuDegistiOlayi olay) {
        try {
            Talep talep = talepRepository.findById(olay.talepId())
                    .orElseThrow(() -> new KayitBulunamadiException("Talep", olay.talepId()));

            List<Kullanici> alicilar = aliciBelirle(olay);
            String mesaj = mesajUret(olay);

            alicilar.forEach(alici -> bildirimRepository.save(new Bildirim(alici, talep, mesaj)));

            log.debug("Bildirim yazildi: talep={}, alici sayisi={}", olay.talepId(), alicilar.size());
        } catch (RuntimeException e) {
            // Bilincli olarak yutuyoruz: onay zaten commit oldu, geri donusu yok.
            // Ama sessizce degil, iz birakarak.
            log.error("Bildirim yazilamadi. talepId={}, gecis={}->{}",
                    olay.talepId(), olay.oncekiDurum(), olay.yeniDurum(), e);
        }
    }

    public Page<BildirimDto> bildirimlerim(String kullaniciAdi, Pageable sayfaIstegi) {
        Kullanici aktif = kullaniciBul(kullaniciAdi);
        return bildirimRepository.findByAliciIdOrderByOlusturmaTarihiDesc(aktif.getId(), sayfaIstegi)
                .map(TalepDonusturucu::bildirim);
    }

    public long okunmamisSayisi(String kullaniciAdi) {
        return bildirimRepository.countByAliciIdAndOkunduFalse(kullaniciBul(kullaniciAdi).getId());
    }

    @Transactional
    public void okunduIsaretle(Long bildirimId, String kullaniciAdi) {
        Kullanici aktif = kullaniciBul(kullaniciAdi);
        Bildirim bildirim = bildirimRepository.findById(bildirimId)
                .orElseThrow(() -> new KayitBulunamadiException("Bildirim", bildirimId));

        if (!bildirim.getAlici().getId().equals(aktif.getId())) {
            throw new YetkisizIslemException("Bu bildirim uzerinde islem yapamazsiniz");
        }
        bildirim.okunduIsaretle();
    }

    private List<Kullanici> aliciBelirle(TalepDurumuDegistiOlayi olay) {
        if (olay.yeniDurum() == TalepDurumu.BEKLEMEDE) {
            // Talep onaya dustu: birimdeki amirlerin haberi olsun.
            return kullaniciRepository.findByBirimIdAndRolAndAktifTrue(olay.birimId(), Rol.AMIR);
        }
        // Karar verildi: talebi acan kisiye haber gidiyor.
        return kullaniciRepository.findById(olay.talepSahibiId()).map(List::of).orElseGet(List::of);
    }

    private String mesajUret(TalepDurumuDegistiOlayi olay) {
        return switch (olay.yeniDurum()) {
            case BEKLEMEDE -> "\"%s\" talebi onayinizi bekliyor.".formatted(olay.talepBasligi());
            case ONAYLANDI -> "\"%s\" talebiniz onaylandi.".formatted(olay.talepBasligi());
            case REDDEDILDI -> "\"%s\" talebiniz reddedildi.".formatted(olay.talepBasligi());
            case TASLAK -> "\"%s\" talebi taslaga alindi.".formatted(olay.talepBasligi());
        };
    }

    private Kullanici kullaniciBul(String kullaniciAdi) {
        return kullaniciRepository.findByKullaniciAdi(kullaniciAdi)
                .orElseThrow(() -> new KayitBulunamadiException("Kullanici", kullaniciAdi));
    }
}
