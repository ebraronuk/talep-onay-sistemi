package tr.ebrar.talep.service.dto;

import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;

import java.time.Instant;
import java.util.List;

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
