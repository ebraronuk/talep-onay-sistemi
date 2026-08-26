package tr.ebrar.talep.service.dto;

import java.time.Instant;

import tr.ebrar.talep.domain.TalepDurumu;

public record OnayKaydiDto(
        Long id,
        TalepDurumu oncekiDurum,
        TalepDurumu yeniDurum,
        String islemYapanAdSoyad,
        String aciklama,
        Instant islemTarihi
) {
}
