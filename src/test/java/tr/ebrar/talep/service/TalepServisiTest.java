package tr.ebrar.talep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import tr.ebrar.talep.destek.VeriUretici;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.GecersizDurumGecisiException;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.OnayKaydi;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;
import tr.ebrar.talep.hata.GecersizIslemException;
import tr.ebrar.talep.hata.KayitBulunamadiException;
import tr.ebrar.talep.hata.YetkisizIslemException;
import tr.ebrar.talep.repository.KullaniciRepository;
import tr.ebrar.talep.repository.OnayKaydiRepository;
import tr.ebrar.talep.repository.TalepRepository;
import tr.ebrar.talep.service.dto.TalepDetayDto;
import tr.ebrar.talep.service.komut.Karar;
import tr.ebrar.talep.service.komut.OnayKarariKomutu;
import tr.ebrar.talep.service.komut.TalepGuncelleKomutu;
import tr.ebrar.talep.service.komut.TalepOlusturKomutu;
import tr.ebrar.talep.service.olay.TalepDurumuDegistiOlayi;

/**
 * Servis katmani birim testleri. Repository'ler mock, veritabani yok.
 *
 * <p>Burada is kurallarini test ediyoruz: kim ne yapabilir, hangi gecis serbest,
 * hangi durumda hata firlar. Verinin gercekten yazildigi entegrasyon testleri ayri
 * (bkz. TalepServisiTransactionTest).
 */
@ExtendWith(MockitoExtension.class)
class TalepServisiTest {

    @Mock
    private TalepRepository talepRepository;

    @Mock
    private OnayKaydiRepository onayKaydiRepository;

    @Mock
    private KullaniciRepository kullaniciRepository;

    @Mock
    private ApplicationEventPublisher olaylar;

    @InjectMocks
    private TalepServisi servis;

    private Birim btgm;
    private Birim muhasebe;
    private Kullanici personel;
    private Kullanici digerPersonel;
    private Kullanici amir;
    private Kullanici baskaBirimAmiri;
    private Kullanici yonetici;

    @BeforeEach
    void hazirla() {
        btgm = VeriUretici.kimlikVer(VeriUretici.birim("BTGM"), 1L);
        muhasebe = VeriUretici.kimlikVer(VeriUretici.birim("MUH"), 2L);

        personel = VeriUretici.kimlikVer(VeriUretici.kullanici("ayse", Rol.PERSONEL, btgm), 10L);
        digerPersonel = VeriUretici.kimlikVer(VeriUretici.kullanici("mehmet", Rol.PERSONEL, btgm), 11L);
        amir = VeriUretici.kimlikVer(VeriUretici.kullanici("veli", Rol.AMIR, btgm), 12L);
        baskaBirimAmiri = VeriUretici.kimlikVer(VeriUretici.kullanici("fatma", Rol.AMIR, muhasebe), 13L);
        yonetici = VeriUretici.kimlikVer(VeriUretici.kullanici("hakan", Rol.YONETICI, btgm), 14L);
    }

    // --- yardimcilar ----------------------------------------------------

    private Talep talepHazirla(Kullanici sahip, TalepDurumu hedefDurum) {
        Talep talep = VeriUretici.kimlikVer(VeriUretici.talep("Dizustu bilgisayar talebi", sahip), 100L);
        if (hedefDurum == TalepDurumu.BEKLEMEDE || hedefDurum.nihaiMi()) {
            talep.durumDegistir(TalepDurumu.BEKLEMEDE, sahip, null);
        }
        if (hedefDurum.nihaiMi()) {
            talep.durumDegistir(hedefDurum, amir, "karar");
        }
        return talep;
    }

    private void kullaniciVar(Kullanici kullanici) {
        when(kullaniciRepository.findByKullaniciAdi(kullanici.getKullaniciAdi())).thenReturn(Optional.of(kullanici));
    }

    private void talepVar(Talep talep) {
        when(talepRepository.findWithIliskilerById(talep.getId())).thenReturn(Optional.of(talep));
    }

    private void gecmisVar(Talep talep) {
        when(onayKaydiRepository.findByTalepIdOrderByOlusturmaTarihiAsc(talep.getId()))
                .thenReturn(List.copyOf(talep.getOnayKayitlari()));
    }

    // --- olusturma ------------------------------------------------------

    @Nested
    @DisplayName("Talep olusturma")
    class Olusturma {

        @Test
        @DisplayName("Yeni talep TASLAK durumunda baslar ve olusturma kaydi yazilir")
        void yeniTalepTaslakBaslar() {
            kullaniciVar(personel);
            when(talepRepository.save(any(Talep.class))).thenAnswer(cagri -> {
                Talep gelen = cagri.getArgument(0);
                return VeriUretici.kimlikVer(gelen, 100L);
            });
            when(onayKaydiRepository.findByTalepIdOrderByOlusturmaTarihiAsc(100L))
                    .thenAnswer(cagri -> List.of());

            TalepDetayDto sonuc = servis.olustur(
                    new TalepOlusturKomutu("Yeni monitor", "Ikinci ekran gerekiyor", TalepTuru.DONANIM),
                    "ayse");

            assertThat(sonuc.durum()).isEqualTo(TalepDurumu.TASLAK);
            assertThat(sonuc.talepEden().kullaniciAdi()).isEqualTo("ayse");
            assertThat(sonuc.birimKodu()).isEqualTo("BTGM");

            ArgumentCaptor<Talep> yakalanan = ArgumentCaptor.forClass(Talep.class);
            verify(talepRepository).save(yakalanan.capture());
            assertThat(yakalanan.getValue().getOnayKayitlari())
                    .as("olusturma da denetim izine yaziliyor")
                    .hasSize(1)
                    .allSatisfy(kayit -> {
                        assertThat(kayit.getOncekiDurum()).isNull();
                        assertThat(kayit.getYeniDurum()).isEqualTo(TalepDurumu.TASLAK);
                    });
        }

        @Test
        @DisplayName("Olmayan kullanici adiyla talep acilamaz")
        void olmayanKullanici() {
            when(kullaniciRepository.findByKullaniciAdi("hayalet")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servis.olustur(
                    new TalepOlusturKomutu("Baslik", "Aciklama", TalepTuru.DIGER), "hayalet"))
                    .isInstanceOf(KayitBulunamadiException.class);

            verify(talepRepository, never()).save(any());
        }
    }

    // --- durum gecisleri ------------------------------------------------

    @Nested
    @DisplayName("Durum gecisleri")
    class Gecisler {

        @Test
        @DisplayName("Taslak talep onaya gonderilince BEKLEMEDE olur ve olay yayinlanir")
        void onayaGonderme() {
            Talep talep = talepHazirla(personel, TalepDurumu.TASLAK);
            kullaniciVar(personel);
            talepVar(talep);
            gecmisVar(talep);

            TalepDetayDto sonuc = servis.onayaGonder(100L, "ayse");

            assertThat(sonuc.durum()).isEqualTo(TalepDurumu.BEKLEMEDE);
            verify(onayKaydiRepository).save(any(OnayKaydi.class));

            ArgumentCaptor<TalepDurumuDegistiOlayi> olay = ArgumentCaptor.forClass(TalepDurumuDegistiOlayi.class);
            verify(olaylar).publishEvent(olay.capture());
            assertThat(olay.getValue().oncekiDurum()).isEqualTo(TalepDurumu.TASLAK);
            assertThat(olay.getValue().yeniDurum()).isEqualTo(TalepDurumu.BEKLEMEDE);
            assertThat(olay.getValue().birimId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("Taslak talep dogrudan onaylanamaz, GecersizDurumGecisiException gelir")
        void taslakDogrudanOnaylanamaz() {
            Talep talep = talepHazirla(personel, TalepDurumu.TASLAK);
            kullaniciVar(amir);
            talepVar(talep);

            assertThatThrownBy(() -> servis.karar(100L, new OnayKarariKomutu(Karar.ONAYLA, "Uygundur"), "veli"))
                    .isInstanceOf(GecersizDurumGecisiException.class)
                    .hasMessageContaining("TASLAK");

            assertThat(talep.getDurum()).as("hata durumunda durum degismemeli").isEqualTo(TalepDurumu.TASLAK);
            verify(olaylar, never()).publishEvent(any(TalepDurumuDegistiOlayi.class));
        }

        @Test
        @DisplayName("Onaylanmis talep bir daha reddedilemez")
        void nihaiDurumdanCikisYok() {
            Talep talep = talepHazirla(personel, TalepDurumu.ONAYLANDI);
            kullaniciVar(amir);
            talepVar(talep);

            assertThatThrownBy(() -> servis.karar(100L, new OnayKarariKomutu(Karar.REDDET, "Vazgectik"), "veli"))
                    .isInstanceOf(GecersizDurumGecisiException.class);

            assertThat(talep.getDurum()).isEqualTo(TalepDurumu.ONAYLANDI);
        }

        @Test
        @DisplayName("Beklemedeki talep onaylanabilir")
        void onaylama() {
            Talep talep = talepHazirla(personel, TalepDurumu.BEKLEMEDE);
            kullaniciVar(amir);
            talepVar(talep);
            gecmisVar(talep);

            TalepDetayDto sonuc = servis.karar(100L, new OnayKarariKomutu(Karar.ONAYLA, "Uygundur"), "veli");

            assertThat(sonuc.durum()).isEqualTo(TalepDurumu.ONAYLANDI);
        }

        @Test
        @DisplayName("Ret icin gerekce zorunlu")
        void retGerekcesiZorunlu() {
            Talep talep = talepHazirla(personel, TalepDurumu.BEKLEMEDE);
            kullaniciVar(amir);
            talepVar(talep);

            assertThatThrownBy(() -> servis.karar(100L, new OnayKarariKomutu(Karar.REDDET, "  "), "veli"))
                    .isInstanceOf(GecersizIslemException.class)
                    .hasMessageContaining("gerekce");

            assertThat(talep.getDurum()).isEqualTo(TalepDurumu.BEKLEMEDE);
        }

        @Test
        @DisplayName("Onay icin gerekce zorunlu degil")
        void onaydaGerekceOpsiyonel() {
            Talep talep = talepHazirla(personel, TalepDurumu.BEKLEMEDE);
            kullaniciVar(amir);
            talepVar(talep);
            gecmisVar(talep);

            assertThat(servis.karar(100L, new OnayKarariKomutu(Karar.ONAYLA, null), "veli").durum())
                    .isEqualTo(TalepDurumu.ONAYLANDI);
        }
    }

    // --- yetki ----------------------------------------------------------

    @Nested
    @DisplayName("Yetki kurallari")
    class Yetki {

        @Test
        @DisplayName("Personel baskasinin talebini goremez")
        void personelBaskasininTalebiniGoremez() {
            Talep talep = talepHazirla(digerPersonel, TalepDurumu.BEKLEMEDE);
            kullaniciVar(personel);
            when(talepRepository.detayGetir(100L)).thenReturn(Optional.of(talep));

            assertThatThrownBy(() -> servis.detay(100L, "ayse"))
                    .isInstanceOf(YetkisizIslemException.class);
        }

        @Test
        @DisplayName("Personel kendi talebini gorebilir")
        void personelKendiTalebiniGorur() {
            Talep talep = talepHazirla(personel, TalepDurumu.BEKLEMEDE);
            kullaniciVar(personel);
            when(talepRepository.detayGetir(100L)).thenReturn(Optional.of(talep));

            assertThat(servis.detay(100L, "ayse").id()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Amir kendi birimindeki talebi gorebilir")
        void amirKendiBirimindekiTalebiGorur() {
            Talep talep = talepHazirla(personel, TalepDurumu.BEKLEMEDE);
            kullaniciVar(amir);
            when(talepRepository.detayGetir(100L)).thenReturn(Optional.of(talep));

            assertThat(servis.detay(100L, "veli").id()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Yonetici her talebi gorebilir")
        void yoneticiHepsiniGorur() {
            Talep talep = talepHazirla(personel, TalepDurumu.BEKLEMEDE);
            kullaniciVar(yonetici);
            when(talepRepository.detayGetir(100L)).thenReturn(Optional.of(talep));

            assertThat(servis.detay(100L, "hakan").id()).isEqualTo(100L);
        }

        @Test
        @DisplayName("Amir baska birimin talebine karar veremez")
        void baskaBirimAmiriKararVeremez() {
            Talep talep = talepHazirla(personel, TalepDurumu.BEKLEMEDE);
            kullaniciVar(baskaBirimAmiri);
            talepVar(talep);

            assertThatThrownBy(() -> servis.karar(100L, new OnayKarariKomutu(Karar.ONAYLA, null), "fatma"))
                    .isInstanceOf(YetkisizIslemException.class);

            assertThat(talep.getDurum()).isEqualTo(TalepDurumu.BEKLEMEDE);
        }

        @Test
        @DisplayName("Personel rolu onay veremez")
        void personelOnayVeremez() {
            Talep talep = talepHazirla(digerPersonel, TalepDurumu.BEKLEMEDE);
            kullaniciVar(personel);
            talepVar(talep);

            assertThatThrownBy(() -> servis.karar(100L, new OnayKarariKomutu(Karar.ONAYLA, null), "ayse"))
                    .isInstanceOf(YetkisizIslemException.class);
        }

        @Test
        @DisplayName("Amir kendi actigi talebi onaylayamaz")
        void kendiTalebiniOnaylayamaz() {
            Talep talep = talepHazirla(amir, TalepDurumu.BEKLEMEDE);
            kullaniciVar(amir);
            talepVar(talep);

            assertThatThrownBy(() -> servis.karar(100L, new OnayKarariKomutu(Karar.ONAYLA, null), "veli"))
                    .isInstanceOf(GecersizIslemException.class)
                    .hasMessageContaining("Kendi talebinizi");
        }

        @Test
        @DisplayName("Baskasinin talebi onaya gonderilemez")
        void baskasininTalebiOnayaGonderilemez() {
            Talep talep = talepHazirla(digerPersonel, TalepDurumu.TASLAK);
            kullaniciVar(personel);
            talepVar(talep);

            assertThatThrownBy(() -> servis.onayaGonder(100L, "ayse"))
                    .isInstanceOf(YetkisizIslemException.class);
        }
    }

    // --- guncelleme -----------------------------------------------------

    @Nested
    @DisplayName("Guncelleme")
    class Guncelleme {

        @Test
        @DisplayName("Taslak talep guncellenebilir")
        void taslakGuncellenir() {
            Talep talep = talepHazirla(personel, TalepDurumu.TASLAK);
            kullaniciVar(personel);
            talepVar(talep);
            gecmisVar(talep);

            TalepDetayDto sonuc = servis.guncelle(100L,
                    new TalepGuncelleKomutu("Guncel baslik", "Guncel aciklama", TalepTuru.EGITIM), "ayse");

            assertThat(sonuc.baslik()).isEqualTo("Guncel baslik");
            // aciklama da dogrulaniyor: mutasyon testi bu satirin eksikligini yakaladi.
            // setAciklama cagrisini silince testler yesil kaliyordu, yani "guncelleme
            // calisiyor" iddiasi aslinda iki alan icin kanitlanmis, ucuncusu icin degildi.
            assertThat(sonuc.aciklama()).isEqualTo("Guncel aciklama");
            assertThat(sonuc.tur()).isEqualTo(TalepTuru.EGITIM);
            // save cagrilmiyor: kirli kontrol devrede
            verify(talepRepository, never()).save(any());
        }

        @Test
        @DisplayName("Onaya gonderilmis talep artik duzenlenemez")
        void beklemedekiTalepGuncellenemez() {
            Talep talep = talepHazirla(personel, TalepDurumu.BEKLEMEDE);
            kullaniciVar(personel);
            talepVar(talep);

            assertThatThrownBy(() -> servis.guncelle(100L,
                    new TalepGuncelleKomutu("Yeni", "Yeni aciklama", TalepTuru.DIGER), "ayse"))
                    .isInstanceOf(GecersizIslemException.class)
                    .hasMessageContaining("taslak");
        }

        @Test
        @DisplayName("Olmayan talep guncellenemez")
        void olmayanTalep() {
            kullaniciVar(personel);
            when(talepRepository.findWithIliskilerById(999L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> servis.guncelle(999L,
                    new TalepGuncelleKomutu("A", "B", TalepTuru.DIGER), "ayse"))
                    .isInstanceOf(KayitBulunamadiException.class);
        }
    }
}
