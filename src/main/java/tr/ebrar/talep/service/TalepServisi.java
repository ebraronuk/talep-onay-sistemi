package tr.ebrar.talep.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.OnayKaydi;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.hata.GecersizIslemException;
import tr.ebrar.talep.hata.KayitBulunamadiException;
import tr.ebrar.talep.hata.YetkisizIslemException;
import tr.ebrar.talep.repository.KullaniciRepository;
import tr.ebrar.talep.repository.OnayKaydiRepository;
import tr.ebrar.talep.repository.TalepAramaKriteri;
import tr.ebrar.talep.repository.TalepRepository;
import tr.ebrar.talep.service.dto.TalepDetayDto;
import tr.ebrar.talep.service.dto.TalepOzetDto;
import tr.ebrar.talep.service.komut.Karar;
import tr.ebrar.talep.service.komut.OnayKarariKomutu;
import tr.ebrar.talep.service.komut.TalepFiltresi;
import tr.ebrar.talep.service.komut.TalepGuncelleKomutu;
import tr.ebrar.talep.service.komut.TalepOlusturKomutu;
import tr.ebrar.talep.service.olay.TalepDurumuDegistiOlayi;

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
    private final TalepOlcumleri olcumler;
    private final OnayAyarlari onayAyarlari;

    public TalepServisi(TalepRepository talepRepository,
                        OnayKaydiRepository onayKaydiRepository,
                        KullaniciRepository kullaniciRepository,
                        ApplicationEventPublisher olaylar,
                        TalepOlcumleri olcumler,
                        OnayAyarlari onayAyarlari) {
        this.talepRepository = talepRepository;
        this.onayKaydiRepository = onayKaydiRepository;
        this.kullaniciRepository = kullaniciRepository;
        this.olaylar = olaylar;
        this.olcumler = olcumler;
        this.onayAyarlari = onayAyarlari;
    }

    @Transactional
    public TalepDetayDto olustur(TalepOlusturKomutu komut, String kullaniciAdi) {
        Kullanici sahip = kullaniciBul(kullaniciAdi);

        Talep talep = new Talep(komut.baslik(), komut.aciklama(), komut.tur(), sahip, komut.tutar());
        talepRepository.save(talep);

        olcumler.olusturuldu(talep.getTur());
        log.info(
                "Talep olusturuldu: id={}, tur={}, tutar={}, kullanici={}",
                talep.getId(),
                talep.getTur(),
                talep.getTutar(),
                kullaniciAdi);
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
        talep.setTutar(komut.tutar());
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
     * Onay veya ret karari. Iki kademeli:
     *
     * <ol>
     *   <li>Birim amiri, kendi birimindeki BEKLEMEDE talebe karar verir.
     *   <li>Tutar limiti asiyorsa talep ONAYLANDI'ya degil YONETICI_ONAYINDA'ya
     *       gecer ve ikinci kademede yonetici karar verir.
     * </ol>
     *
     * <p>Kim hangi kademede karar verebilir sorusu {@link #kararVerebilirMi} icinde.
     * Rolun tek basina yetmemesi onemli: AMIR rolu olan biri ikinci kademeye
     * karisamaz, YONETICI rolu olan biri de birinci kademeye.
     */
    @Transactional
    public TalepDetayDto karar(Long talepId, OnayKarariKomutu komut, String kullaniciAdi) {
        Kullanici islemYapan = kullaniciBul(kullaniciAdi);
        Talep talep = talepBul(talepId);

        kararVerebilirMi(talep, islemYapan);

        if (talep.sahibiMi(islemYapan.getId())) {
            throw new GecersizIslemException("Kendi talebinizi onaylayamaz veya reddedemezsiniz");
        }
        if (komut.karar() == Karar.REDDET && (komut.aciklama() == null || komut.aciklama().isBlank())) {
            throw new GecersizIslemException("Ret islemi icin gerekce yazmak zorunlu");
        }

        durumDegistir(talep, hedefDurum(talep, komut.karar()), islemYapan, komut.aciklama());
        return detayDto(talep);
    }

    /**
     * Karar sonrasi hangi duruma gidilecegini belirler.
     *
     * <p>Ret her kademede dogrudan REDDEDILDI. Onay ise yalnizca birinci kademede
     * ve tutar limiti asildiginda ikinci kademeye gidiyor; diger tum durumlarda
     * dogrudan ONAYLANDI.
     */
    private TalepDurumu hedefDurum(Talep talep, Karar karar) {
        if (karar == Karar.REDDET) {
            return TalepDurumu.REDDEDILDI;
        }
        boolean birinciKademe = talep.getDurum() == TalepDurumu.BEKLEMEDE;
        if (birinciKademe && talep.tutarLimitiAsiyorMu(onayAyarlari.yoneticiLimiti())) {
            return TalepDurumu.YONETICI_ONAYINDA;
        }
        return TalepDurumu.ONAYLANDI;
    }

    /**
     * Kayit bazli yetki: rol, kapsam ve kademe.
     *
     * <p>Burada yalnizca "bu kisi bu kademede yetkili mi" sorusu var. Gecisin
     * kendisinin gecerli olup olmadigina durum makinesi karar veriyor ve gecersizse
     * 409 donuyor; o kontrolu burada tekrarlamiyoruz.
     *
     * <p>Acikca engellenen tek sey, dogru role sahip birinin <b>digerinin
     * kademesine</b> karismasi. Bunu durum makinesi yakalayamaz, cunku
     * YONETICI_ONAYINDA -> ONAYLANDI gecisi kendi basina gecerli bir gecis;
     * gecersiz olan, o gecisi birim amirinin yapmasi.
     */
    private void kararVerebilirMi(Talep talep, Kullanici islemYapan) {
        switch (islemYapan.getRol()) {
            case PERSONEL ->
                throw new YetkisizIslemException("Onay islemi yalnizca birim amiri veya yonetici tarafindan yapilabilir");

            case AMIR -> {
                if (!talep.getBirim().getId().equals(islemYapan.getBirim().getId())) {
                    throw new YetkisizIslemException("Baska bir birimin talebi uzerinde islem yapamazsiniz");
                }
                if (talep.getDurum() == TalepDurumu.YONETICI_ONAYINDA) {
                    throw new YetkisizIslemException(
                            "Bu talep tutar limiti nedeniyle yonetici onayinda; bu asamada karar veremezsiniz");
                }
            }

            case YONETICI -> {
                if (talep.getDurum() == TalepDurumu.BEKLEMEDE) {
                    throw new YetkisizIslemException(
                            "Bu talep once birim amirinin onayindan gecmeli");
                }
            }
        }
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

        TalepAramaKriteri kriter = switch (aktif.getRol()) {
            case PERSONEL -> TalepAramaKriteri.kendiTalepleri(
                    aktif.getId(), filtre.durum(), filtre.tur(), filtre.baslik());
            case AMIR -> TalepAramaKriteri.birimTalepleri(
                    aktif.getBirim().getId(), filtre.durum(), filtre.tur(), filtre.baslik());
            case YONETICI -> TalepAramaKriteri.tumTalepler(
                    filtre.birimId(), filtre.durum(), filtre.tur(), filtre.baslik());
        };

        return talepRepository.ara(kriter, sayfaIstegi).map(TalepDonusturucu::ozet);
    }

    // --- ic yardimcilar -------------------------------------------------

    private void durumDegistir(Talep talep, TalepDurumu hedef, Kullanici islemYapan, String aciklama) {
        TalepDurumu onceki = talep.getDurum();

        // Gecis kontrolu varligin icinde; gecersizse GecersizDurumGecisiException
        // firlatiyor ve HataYakalayici bunu 409'a ceviriyor. Burada try/catch yok:
        // exception zaten alan diline ait, cevirmeye gerek kalmiyor.
        OnayKaydi kayit = talep.durumDegistir(hedef, islemYapan, aciklama);

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
