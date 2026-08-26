package tr.ebrar.talep.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tr.ebrar.talep.destek.VeriUretici;

/**
 * Varliklarda kimlik karsilastirmasinin sozlesmesi.
 *
 * <p>Bu testler mutasyon testi sonrasinda yazildi: {@code equals} ve
 * {@code hashCode} metotlarinin icini bozdugumda hicbir test kirmiyordu.
 * Yani "kimlik karsilastirmasi id uzerinden yapilir" iddiasinin karsiligi yoktu.
 *
 * <p>JPA varliklarinda bu metotlar goründügünden daha kritik: yanlis yazildiginda
 * belirti kod hatasi degil, koleksiyonlarda kaybolan kayit oluyor ve teshisi zor.
 */
class VarlikKimlikTest {

    /**
     * Kimlik sozlesmesi bes varlikta da ayni. Ayni iddialari bes kez yazmak yerine
     * uretici fonksiyonlari parametre olarak veriyoruz; yeni bir varlik eklendiginde
     * buraya tek satir eklemek yetiyor.
     */
    static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> tumVarliklar() {
        return java.util.stream.Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        "Birim", (java.util.function.Supplier<Object>) () -> VeriUretici.birim("AAA")),
                org.junit.jupiter.params.provider.Arguments.of(
                        "Kullanici", (java.util.function.Supplier<Object>) VarlikKimlikTest::yeniKullanici),
                org.junit.jupiter.params.provider.Arguments.of(
                        "Talep", (java.util.function.Supplier<Object>) VarlikKimlikTest::yeniTalep),
                org.junit.jupiter.params.provider.Arguments.of(
                        "OnayKaydi", (java.util.function.Supplier<Object>) VarlikKimlikTest::yeniOnayKaydi),
                org.junit.jupiter.params.provider.Arguments.of(
                        "Bildirim", (java.util.function.Supplier<Object>) VarlikKimlikTest::yeniBildirim));
    }

    static Kullanici yeniKullanici() {
        return VeriUretici.kullanici("k1", Rol.PERSONEL, VeriUretici.kimlikVer(VeriUretici.birim("AAA"), 1L));
    }

    static Talep yeniTalep() {
        return VeriUretici.talep("Baslik", VeriUretici.kimlikVer(yeniKullanici(), 2L));
    }

    static OnayKaydi yeniOnayKaydi() {
        Kullanici sahip = VeriUretici.kimlikVer(yeniKullanici(), 2L);
        return new OnayKaydi(
                VeriUretici.kimlikVer(VeriUretici.talep("T", sahip), 3L),
                TalepDurumu.TASLAK,
                TalepDurumu.BEKLEMEDE,
                sahip,
                "aciklama");
    }

    static Bildirim yeniBildirim() {
        Kullanici sahip = VeriUretici.kimlikVer(yeniKullanici(), 2L);
        return new Bildirim(sahip, VeriUretici.kimlikVer(VeriUretici.talep("T", sahip), 3L), "mesaj");
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.MethodSource("tumVarliklar")
    @DisplayName("Kimlik sozlesmesi tum varliklarda ayni")
    void kimlikSozlesmesi(String ad, java.util.function.Supplier<Object> uretici) {
        Object idsizBir = uretici.get();
        Object idsizIki = uretici.get();

        // Kalicilastirilmamis iki varlik esit degil: esit sayilsalardi ayni Set'e
        // eklendiklerinde biri sessizce kaybolurdu.
        assertThat(idsizBir).isNotEqualTo(idsizIki);
        assertThat(idsizBir).isEqualTo(idsizBir);

        // Farkli tip ve null ile karsilastirma patlamamali.
        assertThat(idsizBir).isNotEqualTo("bir metin");
        assertThat(idsizBir).isNotEqualTo(null);

        // hashCode id atanmadan once ve sonra ayni kalmali (HashSet sozlesmesi).
        int oncekiHash = idsizBir.hashCode();
        VeriUretici.kimlikVer(idsizBir, 42L);
        assertThat(idsizBir.hashCode()).isEqualTo(oncekiHash);

        // Ayni id'yi tasiyan iki ayri nesne ayni satiri temsil eder: esit olmali.
        Object ayniSatir = VeriUretici.kimlikVer(uretici.get(), 42L);
        assertThat(idsizBir).isEqualTo(ayniSatir);
        assertThat(ayniSatir).isEqualTo(idsizBir);

        // Farkli id esit degil.
        Object baskaSatir = VeriUretici.kimlikVer(uretici.get(), 43L);
        assertThat(idsizBir).isNotEqualTo(baskaSatir);

        // Set icinde ayni satir tek kayit.
        HashSet<Object> kume = new HashSet<>();
        kume.add(idsizBir);
        kume.add(ayniSatir);
        assertThat(kume).as(ad + " icin ayni satir Set'te tek kayit olmali").hasSize(1);
    }

    @Test
    @DisplayName("Ayni id'ye sahip iki varlik esittir")
    void ayniIdEsit() {
        Birim birim = VeriUretici.kimlikVer(VeriUretici.birim("AAA"), 1L);
        Birim ayniKayit = VeriUretici.kimlikVer(VeriUretici.birim("BBB"), 1L);

        // Kod alanlari farkli ama ayni satiri temsil ediyorlar: esit sayilmalilar.
        assertThat(birim).isEqualTo(ayniKayit);
        assertThat(ayniKayit).isEqualTo(birim);
    }

    @Test
    @DisplayName("Farkli id'ler esit degildir")
    void farkliIdEsitDegil() {
        Birim birinci = VeriUretici.kimlikVer(VeriUretici.birim("AAA"), 1L);
        Birim ikinci = VeriUretici.kimlikVer(VeriUretici.birim("AAA"), 2L);

        assertThat(birinci).isNotEqualTo(ikinci);
    }

    @Test
    @DisplayName("Kalicilastirilmamis iki varlik esit degildir")
    void idsizVarliklarEsitDegil() {
        Birim birinci = VeriUretici.birim("AAA");
        Birim ikinci = VeriUretici.birim("AAA");

        // Ikisi de henuz veritabaninda degil. Esit saysaydik, ayni Set'e
        // eklendiklerinde biri sessizce kaybolurdu.
        assertThat(birinci).isNotEqualTo(ikinci);
        assertThat(birinci).isEqualTo(birinci);
    }

    @Test
    @DisplayName("Farkli tipteki nesneyle karsilastirma false doner, patlamaz")
    void farkliTipEsitDegil() {
        Birim birim = VeriUretici.kimlikVer(VeriUretici.birim("AAA"), 1L);

        assertThat(birim).isNotEqualTo("bir metin");
        assertThat(birim).isNotEqualTo(null);
    }

    @Test
    @DisplayName("hashCode sabit: id atandiktan sonra degismiyor")
    void hashCodeSabitKaliyor() {
        Birim birim = VeriUretici.birim("AAA");
        int oncesi = birim.hashCode();

        VeriUretici.kimlikVer(birim, 5L);

        // Bu, HashSet sozlesmesinin geregi. Kalicilastirma sirasinda hashCode
        // degisseydi, Set'e id'siz eklenen varlik id aldiktan sonra bulunamazdi.
        assertThat(birim.hashCode()).isEqualTo(oncesi);
    }

    @Test
    @DisplayName("Kalicilastirilan varlik Set icinde bulunabiliyor")
    void setIcindeKaybolmuyor() {
        Talep talep = VeriUretici.talep(
                "Set testi", VeriUretici.kimlikVer(VeriUretici.kullanici("k1", Rol.PERSONEL, VeriUretici.kimlikVer(VeriUretici.birim("AAA"), 1L)), 2L));

        HashSet<Talep> kume = new HashSet<>();
        kume.add(talep);

        VeriUretici.kimlikVer(talep, 9L);

        assertThat(kume).contains(talep);
        assertThat(kume.add(talep)).as("ayni nesne ikinci kez eklenemez").isFalse();
    }

    @Test
    @DisplayName("Ayni satiri temsil eden iki nesne Set'te tek kayit")
    void ayniKayitSettePayIcinTek() {
        Talep birinci = VeriUretici.kimlikVer(
                VeriUretici.talep("Ilk", kullaniciUret()), 7L);
        Talep ikinci = VeriUretici.kimlikVer(
                VeriUretici.talep("Ayni satir farkli nesne", kullaniciUret()), 7L);

        HashSet<Talep> kume = new HashSet<>();
        kume.add(birinci);
        kume.add(ikinci);

        assertThat(kume).hasSize(1);
    }

    private Kullanici kullaniciUret() {
        return VeriUretici.kimlikVer(
                VeriUretici.kullanici("k1", Rol.PERSONEL, VeriUretici.kimlikVer(VeriUretici.birim("AAA"), 1L)), 2L);
    }
}
