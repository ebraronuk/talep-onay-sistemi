package tr.ebrar.talep.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.OnayKaydi;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.repository.KullaniciRepository;
import tr.ebrar.talep.repository.OnayKaydiRepository;
import tr.ebrar.talep.repository.TalepRepository;
import tr.ebrar.talep.repository.TalepSpecifications;
import tr.ebrar.talep.service.dto.TalepDetayDto;
import tr.ebrar.talep.service.dto.TalepOzetDto;
import tr.ebrar.talep.service.hata.GecersizDurumGecisiException;
import tr.ebrar.talep.service.hata.GecersizIslemException;
import tr.ebrar.talep.service.hata.KayitBulunamadiException;
import tr.ebrar.talep.service.hata.YetkisizIslemException;
import tr.ebrar.talep.service.komut.OnayKarariKomutu;
import tr.ebrar.talep.service.komut.TalepFiltresi;
import tr.ebrar.talep.service.komut.TalepGuncelleKomutu;
import tr.ebrar.talep.service.komut.TalepOlusturKomutu;
import tr.ebrar.talep.service.olay.TalepDurumuDegistiOlayi;

import java.util.List;

/**
 * Talep is akisinin tamami burada.
 *
 * <p>Sinif seviyesinde {@code readOnly = true}, yazan metotlarda ayrica
 * {@code @Transactional}. Bu sekilde yeni bir okuma metodu eklendiginde
 * kimse bir sey yazmayi unutmuyor; yazma metodu eklendiginde ise anotasyonu
 * koymayi unutursan test hemen kirmizi yaniyor.
 *
 * <p>Yetki kontrolu iki katmanda: controller'da rol bazli (@PreAuthorize),
 * burada kayit bazli (bu talep senin mi, senin biriminde mi). Ikisi ayni sey
 * degil; rolu AMIR olan biri baska birimin talebine karisamamali.
 */
@Service
@Transactional(readOnly = true)
public class TalepServisi {

    private static final Logger log = LoggerFactory.getLogger(TalepServisi.class);

    private final TalepRepository talepRepository;
    private final OnayKaydiRepository onayKaydiRepository;
    private final KullaniciRepository kullaniciRepository;
    private final ApplicationEventPublisher olaylar;

    public TalepServisi(TalepRepository talepRepository,
                        OnayKaydiRepository onayKaydiRepository,
                        KullaniciRepository kullaniciRepository,
                        ApplicationEventPublisher olaylar) {
        this.talepRepository = talepRepository;
        this.onayKaydiRepository = onayKaydiRepository;
        this.kullaniciRepository = kullaniciRepository;
        this.olaylar = olaylar;
    }

    @Transactional
    public TalepDetayDto olustur(TalepOlusturKomutu komut, String kullaniciAdi) {
        Kullanici sahip = kullaniciBul(kullaniciAdi);

        Talep talep = new Talep(komut.baslik(), komut.aciklama(), komut.tur(), sahip);
        talepRepository.save(talep);

        log.info("Talep olusturuldu: id={}, tur={}, kullanici={}", talep.getId(), talep.getTur(), kullaniciAdi);
        return detayDto(talep);
    }

    @Transactional
    public TalepDetayDto guncelle(Long talepId, TalepGuncelleKomutu komut, String kullaniciAdi) {
        Kullanici aktif = kullaniciBul(kullaniciAdi);
        Talep talep = talepBul(talepId);

        if (!talep.sahibiMi(aktif.getId())) {
            throw new YetkisizIslemException("Bu talep uzerinde islem yapma yetkiniz yok");
        }
        if (!talep.duzenlenebilirMi()) {
            throw new GecersizIslemException(
                    "Yalnizca taslak durumundaki talepler duzenlenebilir. Mevcut durum: " + talep.getDurum());
        }

        talep.setBaslik(komut.baslik());
        talep.setAciklama(komut.aciklama());
        talep.setTur(komut.tur());
        // save cagirmiyoruz: talep yonetilen bir varlik, kirli kontrol (dirty checking)
        // degisikligi commit aninda kendisi UPDATE'e cevirir.

        return detayDto(talep);
    }

    @Transactional
    public TalepDetayDto onayaGonder(Long talepId, String kullaniciAdi) {
        Kullanici aktif = kullaniciBul(kullaniciAdi);
        Talep talep = talepBul(talepId);

        if (!talep.sahibiMi(aktif.getId())) {
            throw new YetkisizIslemException("Bu talep uzerinde islem yapma yetkiniz yok");
        }

        durumDegistir(talep, TalepDurumu.BEKLEMEDE, aktif, "Onaya gonderildi");
        return detayDto(talep);
    }

    /**
     * Amirin onay veya ret karari.
     *
     * <p>Uc ayri kontrol var ve ucu de birbirinden farkli sey soruyor:
     * rol dogru mu, birim dogru mu, kendi talebi mi. Ilk ikisi olmadan
     * yetki acigi olur, ucuncusu olmadan da kimse kendi iznini kendi onaylar.
     */
    @Transactional
    public TalepDetayDto karar(Long talepId, OnayKarariKomutu komut, String kullaniciAdi) {
        Kullanici amir = kullaniciBul(kullaniciAdi);
        Talep talep = talepBul(talepId);

        if (amir.getRol() != Rol.AMIR) {
            throw new YetkisizIslemException("Onay islemi yalnizca birim amiri tarafindan yapilabilir");
        }
        if (!talep.getBirim().getId().equals(amir.getBirim().getId())) {
            throw new YetkisizIslemException("Baska bir birimin talebi uzerinde islem yapamazsiniz");
        }
        if (talep.sahibiMi(amir.getId())) {
            throw new GecersizIslemException("Kendi talebinizi onaylayamaz veya reddedemezsiniz");
        }
        if (komut.karar() == tr.ebrar.talep.service.komut.Karar.REDDET
                && (komut.aciklama() == null || komut.aciklama().isBlank())) {
            throw new GecersizIslemException("Ret islemi icin gerekce yazmak zorunlu");
        }

        durumDegistir(talep, komut.karar().hedefDurum(), amir, komut.aciklama());
        return detayDto(talep);
    }

    public TalepDetayDto detay(Long talepId, String kullaniciAdi) {
        Kullanici aktif = kullaniciBul(kullaniciAdi);
        Talep talep = talepRepository.detayGetir(talepId)
                .orElseThrow(() -> new KayitBulunamadiException("Talep", talepId));

        gorebilirMi(talep, aktif);

        return TalepDonusturucu.detay(talep, talep.getOnayKayitlari());
    }

    /**
     * Rol bazli kapsam + istege bagli filtreler.
     *
     * <p>Buradaki switch guvenligin kalbi. Personel ne yaparsa yapsin kendi
     * taleplerinin disina cikamiyor, cunku talepEdeni kriteri filtreden degil
     * oturumdan geliyor. Istemciden gelen birimId'yi personel ve amir icin
     * bilerek yok sayiyoruz.
     */
    public Page<TalepOzetDto> listele(TalepFiltresi filtre, Pageable sayfaIstegi, String kullaniciAdi) {
        Kullanici aktif = kullaniciBul(kullaniciAdi);

        Specification<Talep> kapsam = switch (aktif.getRol()) {
            case PERSONEL -> TalepSpecifications.talepEdeni(aktif.getId());
            case AMIR -> TalepSpecifications.birimi(aktif.getBirim().getId());
            case YONETICI -> TalepSpecifications.birimi(filtre.birimId());
        };

        Specification<Talep> kriter = Specification.allOf(
                kapsam,
                TalepSpecifications.durumu(filtre.durum()),
                TalepSpecifications.turu(filtre.tur()),
                TalepSpecifications.baslikIcerir(filtre.baslik()),
                TalepSpecifications.iliskileriGetir());

        return talepRepository.findAll(kriter, sayfaIstegi).map(TalepDonusturucu::ozet);
    }

    // --- ic yardimcilar -------------------------------------------------

    private void durumDegistir(Talep talep, TalepDurumu hedef, Kullanici islemYapan, String aciklama) {
        TalepDurumu onceki = talep.getDurum();

        OnayKaydi kayit;
        try {
            kayit = talep.durumDegistir(hedef, islemYapan, aciklama);
        } catch (IllegalStateException e) {
            // Varlik teknik bir exception firlatiyor; disariya alan diline ait olani cikiyor.
            throw new GecersizDurumGecisiException(onceki, hedef);
        }

        // Denetim kaydi ayni transaction icinde yaziliyor. Burasi patlarsa talebin
        // durumu da geri sariyor: yarim denetim izi, hic olmamasindan kotu.
        onayKaydiRepository.save(kayit);

        olaylar.publishEvent(new TalepDurumuDegistiOlayi(
                talep.getId(),
                talep.getBaslik(),
                onceki,
                hedef,
                islemYapan.getId(),
                talep.getTalepEden().getId(),
                talep.getBirim().getId()));

        log.info("Talep durumu degisti: id={}, {} -> {}, islem yapan={}",
                talep.getId(), onceki, hedef, islemYapan.getKullaniciAdi());
    }

    private void gorebilirMi(Talep talep, Kullanici aktif) {
        boolean izin = switch (aktif.getRol()) {
            case PERSONEL -> talep.sahibiMi(aktif.getId());
            case AMIR -> talep.getBirim().getId().equals(aktif.getBirim().getId());
            case YONETICI -> true;
        };
        if (!izin) {
            // Mesajda "boyle bir talep var ama senin degil" demiyoruz; o da bilgi sizdirmak olur.
            throw new YetkisizIslemException("Bu talebi goruntuleme yetkiniz yok");
        }
    }

    private Talep talepBul(Long talepId) {
        return talepRepository.findWithIliskilerById(talepId)
                .orElseThrow(() -> new KayitBulunamadiException("Talep", talepId));
    }

    private Kullanici kullaniciBul(String kullaniciAdi) {
        return kullaniciRepository.findByKullaniciAdi(kullaniciAdi)
                .orElseThrow(() -> new KayitBulunamadiException("Kullanici", kullaniciAdi));
    }

    /**
     * Detay DTO'sunu gecmisiyle birlikte kurar.
     *
     * <p>Gecmisi repository'den okuyoruz, talep.getOnayKayitlari() uzerinden degil.
     * Sebep: yeni eklenen kayit henuz flush edilmemis olabilir ve id'si null gelir.
     * Repository sorgusu once otomatik flush tetikliyor, boylece id'ler dolu geliyor.
     */
    private TalepDetayDto detayDto(Talep talep) {
        List<OnayKaydi> gecmis = onayKaydiRepository.findByTalepIdOrderByOlusturmaTarihiAsc(talep.getId());
        return TalepDonusturucu.detay(talep, gecmis);
    }
}
