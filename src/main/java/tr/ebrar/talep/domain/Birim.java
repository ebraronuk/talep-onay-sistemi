package tr.ebrar.talep.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

@Entity
@Table(name = "birim")
public class Birim extends DenetimAlanlari {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "kod", nullable = false, length = 20, unique = true)
    private String kod;

    @Column(name = "ad", nullable = false, length = 150)
    private String ad;

    protected Birim() {
        // JPA icin
    }

    public Birim(String kod, String ad) {
        this.kod = kod;
        this.ad = ad;
    }

    public Long getId() {
        return id;
    }

    public String getKod() {
        return kod;
    }

    public void setKod(String kod) {
        this.kod = kod;
    }

    public String getAd() {
        return ad;
    }

    public void setAd(String ad) {
        this.ad = ad;
    }

    /**
     * Kimlik karsilastirmasi yalnizca id uzerinden yapilir. Kalicilastirilmamis
     * (id'si null) iki varlik esit sayilmaz; boylece Set icinde beklenmedik
     * birlesme olmaz.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Birim diger)) {
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
        return "Birim{id=" + id + ", kod='" + kod + "'}";
    }
}
