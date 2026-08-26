package tr.ebrar.talep.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.repository.DurumOzeti;
import tr.ebrar.talep.repository.TalepRepository;
import tr.ebrar.talep.service.dto.DurumDagilimDto;
import tr.ebrar.talep.service.dto.RaporDto;

/**
 * Yonetici ozet raporu.
 *
 * <p>Gruplama veritabaninda yapiliyor (bkz. TalepRepository.durumOzetiGetir).
 * Butun talepleri cekip Java tarafinda saymak 50 kayitta calisir, 500.000 kayitta
 * uygulamayi dusurur.
 */
@Service
@Transactional(readOnly = true)
public class RaporServisi {

    private final TalepRepository talepRepository;

    public RaporServisi(TalepRepository talepRepository) {
        this.talepRepository = talepRepository;
    }

    public RaporDto ozet(Long birimId) {
        List<DurumOzeti> satirlar = talepRepository.durumOzetiGetir(birimId);

        long toplam = satirlar.stream().mapToLong(DurumOzeti::adet).sum();
        long bekleyen = satirlar.stream()
                .filter(satir -> satir.durum() == TalepDurumu.BEKLEMEDE)
                .mapToLong(DurumOzeti::adet)
                .findFirst()
                .orElse(0L);

        List<DurumDagilimDto> dagilim = satirlar.stream()
                .map(satir -> new DurumDagilimDto(satir.durum(), satir.adet()))
                .toList();

        return new RaporDto(birimId, toplam, bekleyen, dagilim);
    }
}
