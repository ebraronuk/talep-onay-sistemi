package tr.ebrar.talep.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Durum makinesinin tamami. Veritabani yok, Spring yok; saf mantik testi,
 * milisaniyeler icinde kosuyor.
 *
 * <p>Izinli gecisleri tek tek yazmak yerine "izinli olanlar disindaki her sey
 * yasak" seklinde de yazilabilirdi. Boyle daha uzun ama okurken hangi gecise
 * izin verildigi tabloya bakar gibi goruluyor.
 */
class TalepDurumuTest {

    @ParameterizedTest(name = "{0} -> {1} izinli")
    @CsvSource({
            "TASLAK,    BEKLEMEDE",
            "BEKLEMEDE, ONAYLANDI",
            "BEKLEMEDE, REDDEDILDI"
    })
    @DisplayName("Izinli gecisler kabul edilir")
    void izinliGecisler(TalepDurumu kaynak, TalepDurumu hedef) {
        assertThat(kaynak.gecebilirMi(hedef)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} yasak")
    @CsvSource({
            // Onaya gonderilmemis talep karara baglanamaz
            "TASLAK,     ONAYLANDI",
            "TASLAK,     REDDEDILDI",
            "TASLAK,     TASLAK",
            // Beklemedeki talep taslaga geri cekilemez: geri cekme ozelligi kapsam disi
            "BEKLEMEDE,  TASLAK",
            "BEKLEMEDE,  BEKLEMEDE",
            // Nihai durumlardan cikis yok
            "ONAYLANDI,  REDDEDILDI",
            "ONAYLANDI,  TASLAK",
            "ONAYLANDI,  BEKLEMEDE",
            "ONAYLANDI,  ONAYLANDI",
            "REDDEDILDI, ONAYLANDI",
            "REDDEDILDI, TASLAK",
            "REDDEDILDI, BEKLEMEDE",
            "REDDEDILDI, REDDEDILDI"
    })
    @DisplayName("Yasak gecisler reddedilir")
    void yasakGecisler(TalepDurumu kaynak, TalepDurumu hedef) {
        assertThat(kaynak.gecebilirMi(hedef)).isFalse();
    }

    @Test
    @DisplayName("ONAYLANDI ve REDDEDILDI nihai durum")
    void nihaiDurumlar() {
        assertThat(TalepDurumu.ONAYLANDI.nihaiMi()).isTrue();
        assertThat(TalepDurumu.REDDEDILDI.nihaiMi()).isTrue();
        assertThat(TalepDurumu.TASLAK.nihaiMi()).isFalse();
        assertThat(TalepDurumu.BEKLEMEDE.nihaiMi()).isFalse();
    }

    @Test
    @DisplayName("Izinli hedefler hata mesajinda kullanilmak uzere listelenebiliyor")
    void izinliHedeflerListelenir() {
        assertThat(TalepDurumu.BEKLEMEDE.izinliHedefler())
                .containsExactlyInAnyOrder(TalepDurumu.ONAYLANDI, TalepDurumu.REDDEDILDI);
        assertThat(TalepDurumu.ONAYLANDI.izinliHedefler()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(TalepDurumu.class)
    @DisplayName("Her durum icin gecis tablosu tanimli, hicbiri NPE atmiyor")
    void herDurumTanimli(TalepDurumu durum) {
        // Enum'a yeni bir durum eklenip haritaya eklenmezse burada NullPointerException gelir.
        // Kucuk ama isini goren bir bekci.
        assertThat(durum.izinliHedefler()).isNotNull();
    }
}
