package tr.ebrar.talep.destek;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional olmayan entegrasyon testleri icin tablo temizligi.
 *
 * <p>Neden {@code deleteAllInBatch} degil de TRUNCATE: V5 migration'i ile
 * {@code onay_kaydi} tablosuna UPDATE ve DELETE'i reddeden bir trigger eklendi
 * (denetim izi degistirilemez olmali). Testlerin temizlik icin DELETE atmasi
 * artik o trigger'a takiliyor. TRUNCATE, satir seviyesindeki trigger'lari
 * tetiklemedigi icin dogru arac; ayrica cok daha hizli.
 *
 * <p>RESTART IDENTITY: id sayaclari sifirlaniyor, boylece testler arasinda
 * id degerleri ongorulebilir kaliyor.
 */
@Component
public class VeritabaniTemizleyici {

    private final EntityManager entityManager;

    public VeritabaniTemizleyici(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void hepsiniTemizle() {
        entityManager
                .createNativeQuery("TRUNCATE TABLE bildirim, onay_kaydi, talep, kullanici, birim RESTART IDENTITY CASCADE")
                .executeUpdate();
    }
}
