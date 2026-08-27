package tr.ebrar.talep.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
import jakarta.persistence.Version;

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

    /**
     * Iyimser kilitleme surumu.
     *
     * <p>Iki amir ayni talebi ayni anda actigini dusunelim: ikisi de BEKLEMEDE
     * goruyor, biri onayliyor, digeri reddediyor. Bu kolon olmadan ikinci yazim
     * birincinin uzerine sessizce geciyor ve denetim izinde iki celiskili kayit
     * kaliyor. Hibernate her UPDATE'e "where surum = ?" ekliyor; eskimis surumle
     * gelen ikinci islem sifir satir gunceller ve exception firlatir.
     *
     * <p>Kotumser kilit (SELECT FOR UPDATE) yerine iyimser secildi: cakisma nadir,
     * kilit tutmanin maliyeti ise her istekte odeniyor.
     */
    @Version
    @Column(name = "surum", nullable = false)
    private Long surum;

    @Column(name = "baslik", nullable = false, length = 200)
    private String baslik;

    @Column(name = "aciklama", nullable = false, length = 4000)
    private String aciklama;

    /**
     * Talebin parasal tutari. Bos birakilabilir (izin talebinin tutari yok).
     *
     * <p>Dolu oldugunda onay kademesini belirliyor: belli bir limitin ustundeki
     * talepler birim amirinin onayindan sonra yonetici onayina dusuyor.
     * Limit degeri yapilandirmadan geliyor (talep.onay.yonetici-limiti), burada
     * sabit degil; kurumun limiti degistiginde kod degismesin.
     *
     * <p>double degil BigDecimal: para hesabinda kayan nokta kullanmak, 0.1 + 0.2
     * gibi ifadelerin beklenmedik sonuc vermesi demek. Kolon da NUMERIC(12,2).
     */
    @Column(name = "tutar", precision = 12, scale = 2)
    private BigDecimal tutar;

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
        this(baslik, aciklama, tur, talepEden, null);
    }

    public Talep(String baslik, String aciklama, TalepTuru tur, Kullanici talepEden, BigDecimal tutar) {
        this.tutar = tutar;
        this.baslik = baslik;
        this.aciklama = aciklama;
        this.tur = tur;
        this.talepEden = talepEden;
        this.birim = talepEden.getBirim();
        this.durum = TalepDurumu.TASLAK;

        // Olusturmak da bir degisiklik. Denetim izinin ilk satirini burada yaziyoruz ki
        // "bu talep ne zaman, kim tarafindan acilmis" sorusunun cevabi tek yerde dursun.
        // oncekiDurum null: oncesi yok.
        this.onayKayitlari.add(new OnayKaydi(this, null, TalepDurumu.TASLAK, talepEden, "Talep olusturuldu"));
    }

    /**
     * Durumu hedefe tasir ve denetim kaydini ekler.
     *
     * <p>Kural burada, varligin icinde duruyor. Servis katmaninda olsaydi ikinci
     * bir cagiran (demo veri yukleyici, ileride bir toplu is) kurali atlayabilirdi.
     * Varlik kendi tutarliligindan kendisi sorumlu.
     *
     * @throws GecersizDurumGecisiException gecis durum makinesinde tanimli degilse
     */
    public OnayKaydi durumDegistir(TalepDurumu hedef, Kullanici islemYapan, String aciklama) {
        if (!durum.gecebilirMi(hedef)) {
            throw new GecersizDurumGecisiException(durum, hedef);
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

    public Long getSurum() {
        return surum;
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

    public BigDecimal getTutar() {
        return tutar;
    }

    public void setTutar(BigDecimal tutar) {
        this.tutar = tutar;
    }

    /**
     * Tutar verilen limiti asiyor mu.
     *
     * <p>Tutari olmayan talep hicbir zaman limiti asmaz; izin talebi gibi parasal
     * karsiligi olmayan talepler tek kademede sonuclanir.
     */
    public boolean tutarLimitiAsiyorMu(BigDecimal limit) {
        return tutar != null && limit != null && tutar.compareTo(limit) > 0;
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

    /**
     * Sabit deger. getClass() kullanilmiyor: tembel yuklenmis bir Talep aslinda
     * Hibernate vekili ve onun getClass()'i farkli bir sinif donuyor. Vekil ile
     * gercek nesne ayni HashSet'e girerse iki ayri kayit gibi gorunurlerdi.
     */
    @Override
    public int hashCode() {
        return Talep.class.hashCode();
    }

    @Override
    public String toString() {
        return "Talep{id=" + id + ", durum=" + durum + ", tur=" + tur + "}";
    }
}
