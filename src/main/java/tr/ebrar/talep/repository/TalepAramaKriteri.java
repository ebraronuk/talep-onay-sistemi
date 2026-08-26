package tr.ebrar.talep.repository;

import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;

/**
 * Talep aramasinin girdisi.
 *
 * <p>Servis katmani artik {@code Specification} kurmuyor; onun yerine bu duz kaydi
 * dolduruyor. Sebep: {@code Specification} bir kalicilik (persistence) tipi ve is
 * kurallarinin oldugu katmanda gorunmesi katman sizintisi. Bugun JPA, yarin baska
 * bir sey; is kurali "personel yalnizca kendi taleplerini gorur" degismez.
 *
 * <p>Kapsam alanlari ({@code talepEdenId}, {@code birimId}) guvenligin kalbi:
 * degerleri istemciden degil oturumdan geliyor. Fabrika metotlari bunu acikca
 * gosteriyor.
 */
public record TalepAramaKriteri(
        Long talepEdenId,
        Long birimId,
        TalepDurumu durum,
        TalepTuru tur,
        String baslik
) {

    /** Personel kapsami: yalnizca kendi actigi talepler. */
    public static TalepAramaKriteri kendiTalepleri(Long kullaniciId, TalepDurumu durum, TalepTuru tur, String baslik) {
        return new TalepAramaKriteri(kullaniciId, null, durum, tur, baslik);
    }

    /** Amir kapsami: kendi birimindeki tum talepler. */
    public static TalepAramaKriteri birimTalepleri(Long birimId, TalepDurumu durum, TalepTuru tur, String baslik) {
        return new TalepAramaKriteri(null, birimId, durum, tur, baslik);
    }

    /** Yonetici kapsami: tum birimler; birim daraltmasi istege bagli. */
    public static TalepAramaKriteri tumTalepler(Long birimId, TalepDurumu durum, TalepTuru tur, String baslik) {
        return new TalepAramaKriteri(null, birimId, durum, tur, baslik);
    }
}
