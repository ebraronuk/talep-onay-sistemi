package tr.ebrar.talep.domain;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Talep ile ayni transaction'da yazilip sonra islenen olay (outbox satiri).
 *
 * <p>Talep, kullanici ve birime iliski degil <b>id</b> tutuyor. Olay, yazildigi andaki
 * durumun fotografi; iliski kurulsaydi isleyici olayi degil, talebin o anki halini
 * okumaya kayardi.
 */
@Entity
@Table(name = "giden_olay")
public class GidenOlay extends DenetimAlanlari {

    /** Artan araliklarla yaklasik on dakikaya yayiliyor: kisa kesintiyi atlatir, sonsuza dek ugrasmaz. */
    static final int AZAMI_DENEME = 8;
    static final Duration ILK_BEKLEME = Duration.ofSeconds(5);
    static final int AZAMI_HATA_UZUNLUGU = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "talep_id", nullable = false, updatable = false)
    private Long talepId;

    @Column(name = "talep_basligi", nullable = false, updatable = false, length = 200)
    private String talepBasligi;

    @Enumerated(EnumType.STRING)
    @Column(name = "onceki_durum", nullable = false, updatable = false, length = 20)
    private TalepDurumu oncekiDurum;

    @Enumerated(EnumType.STRING)
    @Column(name = "yeni_durum", nullable = false, updatable = false, length = 20)
    private TalepDurumu yeniDurum;

    @Column(name = "islem_yapan_id", nullable = false, updatable = false)
    private Long islemYapanId;

    @Column(name = "talep_sahibi_id", nullable = false, updatable = false)
    private Long talepSahibiId;

    @Column(name = "birim_id", nullable = false, updatable = false)
    private Long birimId;

    @Enumerated(EnumType.STRING)
    @Column(name = "durum", nullable = false, length = 20)
    private GidenOlayDurumu durum;

    @Column(name = "deneme_sayisi", nullable = false)
    private int denemeSayisi;

    @Column(name = "sonraki_deneme", nullable = false)
    private Instant sonrakiDeneme;

    @Column(name = "islenme_tarihi")
    private Instant islenmeTarihi;

    @Column(name = "son_hata", length = AZAMI_HATA_UZUNLUGU)
    private String sonHata;

    protected GidenOlay() {
        // JPA icin
    }

    public GidenOlay(Long talepId,
                     String talepBasligi,
                     TalepDurumu oncekiDurum,
                     TalepDurumu yeniDurum,
                     Long islemYapanId,
                     Long talepSahibiId,
                     Long birimId,
                     Instant simdi) {
        this.talepId = talepId;
        this.talepBasligi = talepBasligi;
        this.oncekiDurum = oncekiDurum;
        this.yeniDurum = yeniDurum;
        this.islemYapanId = islemYapanId;
        this.talepSahibiId = talepSahibiId;
        this.birimId = birimId;
        this.durum = GidenOlayDurumu.BEKLIYOR;
        this.sonrakiDeneme = simdi;
    }

    public void islendi(Instant simdi) {
        this.durum = GidenOlayDurumu.ISLENDI;
        this.islenmeTarihi = simdi;
    }

    /** Hatali olay digerlerini tikamasin diye zamani ileri alinir, hakki bitince vazgecilir. */
    public void denemeBasarisiz(String hata, Instant simdi) {
        this.denemeSayisi++;
        this.sonHata = hata.length() > AZAMI_HATA_UZUNLUGU ? hata.substring(0, AZAMI_HATA_UZUNLUGU) : hata;

        if (denemeSayisi >= AZAMI_DENEME) {
            this.durum = GidenOlayDurumu.BASARISIZ;
            return;
        }
        this.sonrakiDeneme = simdi.plus(ILK_BEKLEME.multipliedBy(1L << (denemeSayisi - 1)));
    }

    public Long getId() {
        return id;
    }

    public Long getTalepId() {
        return talepId;
    }

    public String getTalepBasligi() {
        return talepBasligi;
    }

    public TalepDurumu getOncekiDurum() {
        return oncekiDurum;
    }

    public TalepDurumu getYeniDurum() {
        return yeniDurum;
    }

    public Long getIslemYapanId() {
        return islemYapanId;
    }

    public Long getTalepSahibiId() {
        return talepSahibiId;
    }

    public Long getBirimId() {
        return birimId;
    }

    public GidenOlayDurumu getDurum() {
        return durum;
    }

    public int getDenemeSayisi() {
        return denemeSayisi;
    }

    public Instant getSonrakiDeneme() {
        return sonrakiDeneme;
    }

    public Instant getIslenmeTarihi() {
        return islenmeTarihi;
    }

    public String getSonHata() {
        return sonHata;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GidenOlay diger)) {
            return false;
        }
        return id != null && id.equals(diger.id);
    }

    /** Sabit deger; Hibernate vekili ile gercek nesnenin hashCode'u ayni olmali. */
    @Override
    public int hashCode() {
        return GidenOlay.class.hashCode();
    }
}
