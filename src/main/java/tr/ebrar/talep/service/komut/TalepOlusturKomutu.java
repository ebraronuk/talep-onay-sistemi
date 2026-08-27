package tr.ebrar.talep.service.komut;

import java.math.BigDecimal;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import tr.ebrar.talep.domain.TalepTuru;

/**
 * Yeni talep girdisi.
 *
 * <p>Size sinirlari veritabanindaki kolon uzunluklariyla birebir ayni. Farkli
 * olsalardi dogrulama gecen bir kayit insert sirasinda patlardi ve kullanici
 * 400 yerine 500 gorurdu.
 */
public record TalepOlusturKomutu(

        @NotBlank(message = "Baslik zorunludur")
        @Size(max = 200, message = "Baslik en fazla 200 karakter olabilir")
        String baslik,

        @NotBlank(message = "Aciklama zorunludur")
        @Size(max = 4000, message = "Aciklama en fazla 4000 karakter olabilir")
        String aciklama,

        @NotNull(message = "Talep turu zorunludur")
        TalepTuru tur,

        // Bos birakilabilir: izin talebinin parasal karsiligi yok. Dolu oldugunda
        // onay kademesini belirliyor; limitin ustundeki talepler yonetici onayina
        // duser. Digits siniri veritabani kolonuyla ayni: NUMERIC(12,2). Farkli
        // olsaydi dogrulamadan gecen bir kayit insert sirasinda patlardi.
        @PositiveOrZero(message = "Tutar negatif olamaz")
        @Digits(integer = 10, fraction = 2, message = "Tutar en fazla 10 basamak ve 2 ondalik olabilir")
        BigDecimal tutar
) {
}
