package tr.ebrar.talep.domain;

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
import jakarta.persistence.Table;

/**
 * Denetim izi. Bir kez yazilir, guncellenmez, silinmez.
 *
 * <p>Setter yok: kayit yalnizca yapici uzerinden olusur. Bu, "denetim kaydi
 * degistirilemez" kuralini derleme zamaninda garanti eder.
 */
@Entity
@Table(name = "onay_kaydi")
public class OnayKaydi extends DenetimAlanlari {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "talep_id", nullable = false)
    private Talep talep;

    /** Talep ilk kez olusturuldugunda null olur. */
    @Enumerated(EnumType.STRING)
    @Column(name = "onceki_durum", length = 20)
    private TalepDurumu oncekiDurum;

    @Enumerated(EnumType.STRING)
    @Column(name = "yeni_durum", nullable = false, length = 20)
    private TalepDurumu yeniDurum;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "islem_yapan_id", nullable = false)
    private Kullanici islemYapan;

    @Column(name = "aciklama", length = 1000)
    private String aciklama;

    protected OnayKaydi() {
        // JPA icin
    }

    public OnayKaydi(Talep talep, TalepDurumu oncekiDurum, TalepDurumu yeniDurum, Kullanici islemYapan, String aciklama) {
        this.talep = talep;
        this.oncekiDurum = oncekiDurum;
        this.yeniDurum = yeniDurum;
        this.islemYapan = islemYapan;
        this.aciklama = aciklama;
    }

    public Long getId() {
        return id;
    }

    public Talep getTalep() {
        return talep;
    }

    public TalepDurumu getOncekiDurum() {
        return oncekiDurum;
    }

    public TalepDurumu getYeniDurum() {
        return yeniDurum;
    }

    public Kullanici getIslemYapan() {
        return islemYapan;
    }

    public String getAciklama() {
        return aciklama;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OnayKaydi diger)) {
            return false;
        }
        return id != null && id.equals(diger.id);
    }

    /** Sabit deger; Hibernate vekili ile gercek nesnenin hashCode'u ayni olmali. */
    @Override
    public int hashCode() {
        return OnayKaydi.class.hashCode();
    }

    @Override
    public String toString() {
        return "OnayKaydi{id=" + id + ", " + oncekiDurum + " -> " + yeniDurum + "}";
    }
}
