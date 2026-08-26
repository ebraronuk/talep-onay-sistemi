package tr.ebrar.talep.destek;

import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

/**
 * Hibernate'in calistirdigi JDBC deyim sayisini olcer.
 *
 * <p>N+1 problemini "sanirim duzeldi" yerine sayiyla kanitlamak icin var.
 * {@code hibernate.generate_statistics=true} gerektirir (test profilinde acik).
 */
public final class SorguSayaci {

    private final Statistics istatistik;
    private long baslangic;

    private SorguSayaci(Statistics istatistik) {
        this.istatistik = istatistik;
        this.istatistik.setStatisticsEnabled(true);
    }

    public static SorguSayaci ac(EntityManager entityManager) {
        SessionFactory oturumFabrikasi = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        return new SorguSayaci(oturumFabrikasi.getStatistics());
    }

    /** Sayaci sifirlar; bundan sonraki deyimler sayilir. */
    public SorguSayaci sifirla() {
        this.baslangic = istatistik.getPrepareStatementCount();
        return this;
    }

    /** Son sifirlamadan bu yana calisan JDBC deyim sayisi. */
    public long sayi() {
        return istatistik.getPrepareStatementCount() - baslangic;
    }
}
