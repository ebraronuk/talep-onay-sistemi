package tr.ebrar.talep.service.komut;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tr.ebrar.talep.domain.TalepTuru;

// Guncelleme yalnizca TASLAK durumunda serbest. Kontrol serviste.
public record TalepGuncelleKomutu(

        @NotBlank(message = "Baslik zorunludur")
        @Size(max = 200, message = "Baslik en fazla 200 karakter olabilir")
        String baslik,

        @NotBlank(message = "Aciklama zorunludur")
        @Size(max = 4000, message = "Aciklama en fazla 4000 karakter olabilir")
        String aciklama,

        @NotNull(message = "Talep turu zorunludur")
        TalepTuru tur
) {
}
