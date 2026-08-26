package tr.ebrar.talep.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Is akisinin ana varligi.
 *
 * <p>Durum degisikligi yalnizca {@link #durumDegistir} uzerinden yapilir; boylece
 * gecersiz gecis kontrolu ve denetim kaydi yazimi atlanamaz. Alan dogrudan
 * disaridan set edilemesin diye {@code setDurum} bilincli olarak yok.
 */
@Entity
@Table(name = "talep")
public class Talep extends DenetimAlanlari {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "baslik", nullable = false, length = 200)
    private String baslik;

    @Column(name = "aciklama", nullable = false, length = 4000)
    private String aciklama;

    @Enumerated(EnumType.STRING)
    @Column(name = "tur", nullable = false, length = 30)
    private TalepTuru tur;

    @Enumerated(EnumType.STRING)
    @Column(name = "durum", nullable = false, length = 20)
    private TalepDurumu durum;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "talep_eden_id", nullable = false)
    private Kullanici talepEden;

    /**
     * Talebin dustugu birim. Talep eden kullanicinin birimi ile ayni baslar; kullanici
     * daha sonra birim degistirse bile talep acildigi birimde kalir. Bu, denetim
     * gecmisinin tutarli olmasi icin bilincli bir kopyalamadir.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "birim_id", nullable = false)
    private Birim birim;

    @OneToMany(mappedBy = "talep", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @OrderBy("olusturmaTarihi ASC")
    private List<OnayKaydi> onayKayitlari = new ArrayList<>();

    protected Talep() {
        // JPA icin
    }

    public Talep(String baslik, String aciklama, TalepTuru tur, Kullanici talepEden) {
        this.baslik = baslik;
        this.aciklama = aciklama;
        this.tur = tur;
        this.talepEden = talepEden;
        this.birim = talepEden.getBirim();
        this.durum = TalepDurumu.TASLAK;
    }

    /**
     * Durumu hedefe tasir ve denetim kaydini ekler.
     *
     * @throws IllegalStateException gecis izinli degilse. Servis katmani bunu
     *         alan diline ait ozel bir exception'a cevirir.
     */
    public OnayKaydi durumDegistir(TalepDurumu hedef, Kullanici islemYapan, String aciklama) {
        if (!durum.gecebilirMi(hedef)) {
            throw new IllegalStateException(
                    "Gecersiz durum gecisi: " + durum + " -> " + hedef + ". Izinli hedefler: " + durum.izinliHedefler());
        }
        TalepDurumu onceki = this.durum;
        this.durum = hedef;

        OnayKaydi kayit = new OnayKaydi(this, onceki, hedef, islemYapan, aciklama);
        this.onayKayitlari.add(kayit);
        return kayit;
    }

    /** Talebi acan kisi mi. Yetki kontrolu icin servis katmani kullanir. */
    public boolean sahibiMi(Long kullaniciId) {
        return talepEden != null && talepEden.getId() != null && talepEden.getId().equals(kullaniciId);
    }

    public boolean duzenlenebilirMi() {
        return durum == TalepDurumu.TASLAK;
    }

    public Long getId() {
        return id;
    }

    public String getBaslik() {
        return baslik;
    }

    public void setBaslik(String baslik) {
        this.baslik = baslik;
    }

    public String getAciklama() {
        return aciklama;
    }

    public void setAciklama(String aciklama) {
        this.aciklama = aciklama;
    }

    public TalepTuru getTur() {
        return tur;
    }

    public void setTur(TalepTuru tur) {
        this.tur = tur;
    }

    public TalepDurumu getDurum() {
        return durum;
    }

    public Kullanici getTalepEden() {
        return talepEden;
    }

    public Birim getBirim() {
        return birim;
    }

    public List<OnayKaydi> getOnayKayitlari() {
        return Collections.unmodifiableList(onayKayitlari);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Talep diger)) {
            return false;
        }
        return id != null && id.equals(diger.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getClass());
    }

    @Override
    public String toString() {
        return "Talep{id=" + id + ", durum=" + durum + ", tur=" + tur + "}";
    }
}
