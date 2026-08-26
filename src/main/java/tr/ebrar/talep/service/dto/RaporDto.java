package tr.ebrar.talep.service.dto;

import java.util.List;

// Yonetici ekranindaki ozet kutulari bunu kullaniyor.
public record RaporDto(
        Long birimId,
        long toplamTalep,
        long bekleyenTalep,
        List<DurumDagilimDto> durumDagilimi
) {
}
