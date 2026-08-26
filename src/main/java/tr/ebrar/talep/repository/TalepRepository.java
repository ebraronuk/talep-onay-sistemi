package tr.ebrar.talep.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;

import java.util.List;
import java.util.Optional;

public interface TalepRepository extends JpaRepository<Talep, Long>, JpaSpecificationExecutor<Talep> {

    /**
     * Amir ekrani: bir birimin belirli durumdaki talepleri.
     *
     * <p>{@code @EntityGraph} olmadan bu sorgu N+1 uretir: bir sayfa sorgusu, sonra
     * her satirin talepEden ve birim iliskisi icin ayri SELECT. Olcumu icin
     * bkz. docs/performans.md bolum 2.
     */
    @EntityGraph(attributePaths = {"talepEden", "birim"})
    Page<Talep> findByDurumAndBirimId(TalepDurumu durum, Long birimId, Pageable pageable);

    /**
     * Ayni sorgunun liste (sayfasiz) hali. N+1 olcumunde sayfa sorgusunun ek
     * count deyimi sayimi bulandirmasin diye ayri tutuldu.
     */
    @EntityGraph(attributePaths = {"talepEden", "birim"})
    List<Talep> findByDurumOrderByIdAsc(TalepDurumu durum);

    /** Personel ekrani: kendi taleplerim. */
    @EntityGraph(attributePaths = {"talepEden", "birim"})
    Page<Talep> findByTalepEdenId(Long talepEdenId, Pageable pageable);

    /** Iliskileri onceden yuklenmis tekil talep; liste ekranindan detaya gecerken. */
    @EntityGraph(attributePaths = {"talepEden", "birim"})
    Optional<Talep> findWithIliskilerById(Long id);

    /**
     * Detay ekrani: talep, sahibi, birimi ve tum onay gecmisi tek sorguda.
     *
     * <p>Tek bir koleksiyon fetch edildigi icin Kartezyen carpim riski yok.
     * Ikinci bir koleksiyon eklenirse (ornegin bildirimler) Hibernate
     * MultipleBagFetchException firlatir; o durumda ikinci koleksiyon ayri
     * sorguya alinmali.
     */
    @Query("""
            select t from Talep t
            left join fetch t.talepEden te
            left join fetch t.birim
            left join fetch t.onayKayitlari ok
            left join fetch ok.islemYapan
            where t.id = :id
            """)
    Optional<Talep> detayGetir(@Param("id") Long id);

    /**
     * Yonetici raporu: durum basina adet. Butun talepleri belege cekip Java'da
     * saymak yerine gruplama veritabaninda yapilir.
     */
    @Query("""
            select new tr.ebrar.talep.repository.DurumOzeti(t.durum, count(t))
            from Talep t
            where (:birimId is null or t.birim.id = :birimId)
            group by t.durum
            order by t.durum
            """)
    List<DurumOzeti> durumOzetiGetir(@Param("birimId") Long birimId);

    long countByDurumAndBirimId(TalepDurumu durum, Long birimId);
}
