package tr.ebrar.talep.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * Her varlikta bulunan denetim alanlari.
 *
 * <p>Degerleri Spring Data JPA denetimi (auditing) doldurur; uygulama kodu bu alanlara
 * elle yazmaz. Kim yazdi bilgisi {@code JpaDenetimYapilandirmasi} icindeki
 * {@code AuditorAware} tarafindan guvenlik baglamindan okunur.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class DenetimAlanlari {

    @CreatedDate
    @Column(name = "olusturma_tarihi", nullable = false, updatable = false)
    private Instant olusturmaTarihi;

    @LastModifiedDate
    @Column(name = "guncelleme_tarihi", nullable = false)
    private Instant guncellemeTarihi;

    @CreatedBy
    @Column(name = "olusturan_kullanici", nullable = false, updatable = false, length = 60)
    private String olusturanKullanici;

    public Instant getOlusturmaTarihi() {
        return olusturmaTarihi;
    }

    public Instant getGuncellemeTarihi() {
        return guncellemeTarihi;
    }

    public String getOlusturanKullanici() {
        return olusturanKullanici;
    }
}
