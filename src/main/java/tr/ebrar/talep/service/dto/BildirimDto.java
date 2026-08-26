package tr.ebrar.talep.service.dto;

import java.time.Instant;

public record BildirimDto(
        Long id,
        String mesaj,
        boolean okundu,
        Long talepId,
        Instant olusturmaTarihi
) {
}
