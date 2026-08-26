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

@Entity
@Table(name = "kullanici")
public class Kullanici extends DenetimAlanlari {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kullanici_adi", nullable = false, length = 60, unique = true)
    private String kullaniciAdi;

    @Column(name = "ad_soyad", nullable = false, length = 150)
    private String adSoyad;

    @Column(name = "eposta", nullable = false, length = 180, unique = true)
    private String eposta;

    /** BCrypt ozeti. Duz metin sifre hicbir alanda tutulmaz. */
    @Column(name = "sifre_ozeti", nullable = false, length = 100)
    private String sifreOzeti;

    @Enumerated(EnumType.STRING)
    @Column(name = "rol", nullable = false, length = 20)
    private Rol rol;

    @Column(name = "aktif", nullable = false)
    private boolean aktif = true;

    /**
     * Varsayilan LAZY. Kullanici listelenirken birim genelde gerekmiyor;
     * gerektigi yerde @EntityGraph veya join fetch ile acikca isteniyor.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "birim_id", nullable = false)
    private Birim birim;

    protected Kullanici() {
        // JPA icin
    }

    public Kullanici(String kullaniciAdi, String adSoyad, String eposta, String sifreOzeti, Rol rol, Birim birim) {
        this.kullaniciAdi = kullaniciAdi;
        this.adSoyad = adSoyad;
        this.eposta = eposta;
        this.sifreOzeti = sifreOzeti;
        this.rol = rol;
        this.birim = birim;
        this.aktif = true;
    }

    public Long getId() {
        return id;
    }

    public String getKullaniciAdi() {
        return kullaniciAdi;
    }

    public String getAdSoyad() {
        return adSoyad;
    }

    public void setAdSoyad(String adSoyad) {
        this.adSoyad = adSoyad;
    }

    public String getEposta() {
        return eposta;
    }

    public void setEposta(String eposta) {
        this.eposta = eposta;
    }

    public String getSifreOzeti() {
        return sifreOzeti;
    }

    public void setSifreOzeti(String sifreOzeti) {
        this.sifreOzeti = sifreOzeti;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public boolean isAktif() {
        return aktif;
    }

    public void setAktif(boolean aktif) {
        this.aktif = aktif;
    }

    public Birim getBirim() {
        return birim;
    }

    public void setBirim(Birim birim) {
        this.birim = birim;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Kullanici diger)) {
            return false;
        }
        return id != null && id.equals(diger.id);
    }

    /** Sabit deger; Hibernate vekili ile gercek nesnenin hashCode'u ayni olmali. */
    @Override
    public int hashCode() {
        return Kullanici.class.hashCode();
    }

    /** Sifre ozeti kasitli olarak disarida birakildi; log satirlarina sizmasin. */
    @Override
    public String toString() {
        return "Kullanici{id=" + id + ", kullaniciAdi='" + kullaniciAdi + "', rol=" + rol + "}";
    }
}
