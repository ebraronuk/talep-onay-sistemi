package tr.ebrar.talep.service.olay;

import tr.ebrar.talep.domain.TalepDurumu;

/**
 * Talep durumu degistiginde yayinlanir.
 *
 * <p>Icinde varlik degil <b>id</b> tasiyor. Sebep: dinleyici commit'ten sonra
 * calisiyor, o noktada varliklar kalicilik baglamindan ayrilmis oluyor.
 * Varligi tasisaydik dinleyicide LazyInitializationException yerdik.
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
