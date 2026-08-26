package tr.ebrar.talep.service.komut;

import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;

/**
 * Liste ekraninin filtre kutulari. Hepsi opsiyonel, null gelenler yok sayilir.
 *
 * <p>Dikkat: buradaki birimId yalnizca YONETICI icin anlamli. Personel ve amir
 * icin kapsam zaten rolden geliyor, filtreden gelen deger yok sayiliyor.
 * Yoksa personel birimId gondererek baskasinin taleplerini listeleyebilirdi.
 */
public record TalepFiltresi(
        TalepDurumu durum,
        TalepTuru tur,
        Long birimId,
        String baslik
) {
}
