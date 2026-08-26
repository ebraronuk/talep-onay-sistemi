package tr.ebrar.talep.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * Uygulama ici bildirim. E-posta veya SMS gonderimi kapsam disidir.
 */
@Entity
@Table(name = "bildirim")
public class Bildirim extends DenetimAlanlari {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "alici_id", nullable = false)
    private Kullanici alici;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "talep_id", nullable = false)
    private Talep talep;

    @Column(name = "mesaj", nullable = false, length = 500)
    private String mesaj;

    @Column(name = "okundu", nullable = false)
    private boolean okundu;

    protected Bildirim() {
        // JPA icin
    }

    public Bildirim(Kullanici alici, Talep talep, String mesaj) {
        this.alici = alici;
        this.talep = talep;
        this.mesaj = mesaj;
        this.okundu = false;
    }

    public void okunduIsaretle() {
        this.okundu = true;
    }

    public Long getId() {
        return id;
    }

    public Kullanici getAlici() {
        return alici;
    }

    public Talep getTalep() {
        return talep;
    }

    public String getMesaj() {
        return mesaj;
    }

    public boolean isOkundu() {
        return okundu;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Bildirim diger)) {
            return false;
        }
        return id != null && id.equals(diger.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(getClass());
    }
}
