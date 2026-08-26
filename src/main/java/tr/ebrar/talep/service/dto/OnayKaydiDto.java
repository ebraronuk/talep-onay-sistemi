package tr.ebrar.talep.service.dto;

import tr.ebrar.talep.domain.TalepDurumu;

import java.time.Instant;

public record OnayKaydiDto(
        Long id,
        TalepDurumu oncekiDurum,
        TalepDurumu yeniDurum,
        String islemYapanAdSoyad,
        String aciklama,
        Instant islemTarihi
) {
}
