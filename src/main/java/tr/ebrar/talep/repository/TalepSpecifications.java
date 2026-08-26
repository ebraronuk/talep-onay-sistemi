package tr.ebrar.talep.repository;

import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;

import java.time.Instant;

/**
 * Talep listeleme filtreleri.
 *
 * <p>Her filtre kendi basina null-guvenli: parametre yoksa {@code null} donerek
 * {@link Specification#allOf} zincirinden sessizce dusulur. Bu sayede servis
 * katmaninda if merdiveni kurmaya gerek kalmiyor.
 */
public final class TalepSpecifications {

    private TalepSpecifications() {
    }

    public static Specification<Talep> durumu(TalepDurumu durum) {
        return durum == null ? null : (kok, sorgu, kb) -> kb.equal(kok.get("durum"), durum);
    }

    public static Specification<Talep> turu(TalepTuru tur) {
        return tur == null ? null : (kok, sorgu, kb) -> kb.equal(kok.get("tur"), tur);
    }

    public static Specification<Talep> birimi(Long birimId) {
        return birimId == null ? null : (kok, sorgu, kb) -> kb.equal(kok.get("birim").get("id"), birimId);
    }

    public static Specification<Talep> talepEdeni(Long kullaniciId) {
        return kullaniciId == null ? null : (kok, sorgu, kb) -> kb.equal(kok.get("talepEden").get("id"), kullaniciId);
    }

    /** Baslikta buyuk/kucuk harf duyarsiz arama. */
    public static Specification<Talep> baslikIcerir(String parca) {
        if (parca == null || parca.isBlank()) {
            return null;
        }
        String desen = "%" + parca.toLowerCase() + "%";
        return (kok, sorgu, kb) -> kb.like(kb.lower(kok.get("baslik")), desen);
    }

    public static Specification<Talep> olusturmaTarihiSonrasi(Instant baslangic) {
        return baslangic == null ? null : (kok, sorgu, kb) -> kb.greaterThanOrEqualTo(kok.get("olusturmaTarihi"), baslangic);
    }

    public static Specification<Talep> olusturmaTarihiOncesi(Instant bitis) {
        return bitis == null ? null : (kok, sorgu, kb) -> kb.lessThanOrEqualTo(kok.get("olusturmaTarihi"), bitis);
    }

    /**
     * Iliskileri ayni sorguda getirir (N+1 onlemi).
     *
     * <p>Sayfalama sorgusunda Spring Data ayrica bir {@code count} sorgusu calistirir.
     * O sorgunun sonuc tipi {@code Long} olur ve fetch join iceremez; bu yuzden
     * sonuc tipi kontrol ediliyor. Kontrol kaldirilirsa sayfali sorgular
     * "query specified join fetching, but the owner of the fetched association was not present"
     * hatasi verir.
     */
    public static Specification<Talep> iliskileriGetir() {
        return (kok, sorgu, kb) -> {
            Class<?> sonucTipi = sorgu.getResultType();
            if (sonucTipi != Long.class && sonucTipi != long.class) {
                kok.fetch("talepEden", JoinType.LEFT);
                kok.fetch("birim", JoinType.LEFT);
            }
            return kb.conjunction();
        };
    }
}
