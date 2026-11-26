package tr.ebrar.talep.service.olay;

import tr.ebrar.talep.domain.TalepDurumu;

/**
 * Talep durumu degistiginde yayinlanir.
 *
 * <p>Icinde varlik degil <b>id</b> tasiyor. Sebep: olay kutuya yazilip cok sonra, baska
 * bir transaction'da, hatta uygulama yeniden acildiktan sonra okunabiliyor. O noktada
 * yayinlayanin varliklari ortada olmuyor.
 */
public record TalepDurumuDegistiOlayi(
        Long talepId,
        String talepBasligi,
        TalepDurumu oncekiDurum,
        TalepDurumu yeniDurum,
        Long islemYapanId,
        Long talepSahibiId,
        Long birimId
) {
}
