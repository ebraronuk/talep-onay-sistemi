package tr.ebrar.talep.service.dto;

import java.time.Instant;
import java.util.List;

import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;

public record TalepDetayDto(
        Long id,
        String baslik,
        String aciklama,
        TalepTuru tur,
        TalepDurumu durum,
        KullaniciOzetDto talepEden,
        String birimKodu,
        String birimAdi,
        Instant olusturmaTarihi,
        Instant guncellemeTarihi,
        List<OnayKaydiDto> gecmis
) {
}
