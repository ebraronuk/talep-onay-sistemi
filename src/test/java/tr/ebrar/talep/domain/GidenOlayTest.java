package tr.ebrar.talep.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tr.ebrar.talep.destek.VeriUretici;

/** Giden olayin yasam dongusu. Veritabani yok; zaman disaridan veriliyor. */
class GidenOlayTest {

    private static final Instant SIMDI = Instant.parse("2026-11-25T09:00:00Z");

    private final GidenOlay olay = VeriUretici.gidenOlay(1L, SIMDI);

    @Test
    @DisplayName("Yeni olay bekliyor durumunda ve hemen islenebilir")
    void yeniOlayHemenIslenebilir() {
        assertThat(olay.getDurum()).isEqualTo(GidenOlayDurumu.BEKLIYOR);
        assertThat(olay.getSonrakiDeneme()).isEqualTo(SIMDI);
        assertThat(olay.getDenemeSayisi()).isZero();
        assertThat(olay.getIslenmeTarihi()).isNull();
        assertThat(olay.getSonHata()).isNull();
    }

    @Test
    @DisplayName("Islenen olay islenme zamaniyla birlikte kapanir")
    void islenenOlayKapanir() {
        olay.islendi(SIMDI.plusSeconds(3));

        assertThat(olay.getDurum()).isEqualTo(GidenOlayDurumu.ISLENDI);
        assertThat(olay.getIslenmeTarihi()).isEqualTo(SIMDI.plusSeconds(3));
    }

    @Test
    @DisplayName("Basarisiz denemede olay kaybolmaz, artan araliklarla yeniden denenir")
    void basarisizDenemeArtanAraliklaTekrarlanir() {
        olay.denemeBasarisiz("ilk hata", SIMDI);

        assertThat(olay.getDurum()).isEqualTo(GidenOlayDurumu.BEKLIYOR);
        assertThat(olay.getDenemeSayisi()).isEqualTo(1);
        assertThat(olay.getSonHata()).isEqualTo("ilk hata");
        assertThat(olay.getSonrakiDeneme()).isEqualTo(SIMDI.plus(GidenOlay.ILK_BEKLEME));

        olay.denemeBasarisiz("ikinci hata", SIMDI);
        assertThat(olay.getSonrakiDeneme()).isEqualTo(SIMDI.plus(GidenOlay.ILK_BEKLEME.multipliedBy(2)));

        olay.denemeBasarisiz("ucuncu hata", SIMDI);
        assertThat(olay.getSonrakiDeneme()).isEqualTo(SIMDI.plus(GidenOlay.ILK_BEKLEME.multipliedBy(4)));
        assertThat(olay.getSonHata()).isEqualTo("ucuncu hata");
    }

    @Test
    @DisplayName("Azami denemeden sonra olay basarisiz sayilir, oncesinde sayilmaz")
    void azamiDenemedenSonraVazgecilir() {
        for (int i = 1; i < GidenOlay.AZAMI_DENEME; i++) {
            olay.denemeBasarisiz("hata", SIMDI);
        }
        assertThat(olay.getDurum())
                .as("son hak kullanilmadan vazgecilmemeli")
                .isEqualTo(GidenOlayDurumu.BEKLIYOR);

        Instant sonPlanlananDeneme = olay.getSonrakiDeneme();
        olay.denemeBasarisiz("son hata", SIMDI.plus(Duration.ofDays(1)));

        assertThat(olay.getDurum()).isEqualTo(GidenOlayDurumu.BASARISIZ);
        assertThat(olay.getDenemeSayisi()).isEqualTo(GidenOlay.AZAMI_DENEME);
        assertThat(olay.getSonHata()).isEqualTo("son hata");
        assertThat(olay.getSonrakiDeneme())
                .as("vazgecilen olaya yeni deneme planlanmamali")
                .isEqualTo(sonPlanlananDeneme);
    }

    @Test
    @DisplayName("Uzun hata metni kolona sigacak kadar kisaltilir")
    void uzunHataKisaltilir() {
        olay.denemeBasarisiz("x".repeat(GidenOlay.AZAMI_HATA_UZUNLUGU + 1), SIMDI);

        assertThat(olay.getSonHata()).hasSize(GidenOlay.AZAMI_HATA_UZUNLUGU);

        String tamSigan = "y".repeat(GidenOlay.AZAMI_HATA_UZUNLUGU);
        olay.denemeBasarisiz(tamSigan, SIMDI);

        assertThat(olay.getSonHata()).isEqualTo(tamSigan);
    }
}
