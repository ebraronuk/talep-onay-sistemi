package tr.ebrar.talep.service.dto;

import tr.ebrar.talep.domain.TalepDurumu;

public record DurumDagilimDto(TalepDurumu durum, long adet) {
}
