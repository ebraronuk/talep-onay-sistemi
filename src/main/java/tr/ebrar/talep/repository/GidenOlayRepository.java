package tr.ebrar.talep.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import tr.ebrar.talep.domain.GidenOlay;

public interface GidenOlayRepository extends JpaRepository<GidenOlay, Long> {

    /**
     * Durum parametre degil sabit. Parametre olsaydi PostgreSQL genel plana (generic plan)
     * gectiginde kosulu kismi indeksle eslestiremez, tum tabloyu tarardi.
     */
    @Query("""
            select o.id from GidenOlay o
            where o.durum = tr.ebrar.talep.domain.GidenOlayDurumu.BEKLIYOR and o.sonrakiDeneme <= :simdi
            order by o.id""")
    List<Long> islenecekKimlikler(@Param("simdi") Instant simdi, Limit limit);

    /**
     * Bekleyen olayi kilitler; baska bir islem tutuyorsa <b>beklemez</b>, bos doner.
     *
     * <p>Kilit ve durum kosulu ayni sorguda. Ayri olsalardi iki isleyici ayni satiri
     * bekliyor gorup ikisi de bildirim uretebilirdi.
     */
    @Query(value = "select * from giden_olay where id = :id and durum = 'BEKLIYOR' for update skip locked",
            nativeQuery = true)
    Optional<GidenOlay> islemekIcinKilitle(@Param("id") Long id);
}
