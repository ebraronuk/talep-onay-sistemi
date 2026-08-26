package tr.ebrar.talep.service.komut;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OnayKarariKomutu(

        @NotNull(message = "Karar zorunludur")
        Karar karar,

        // Ret icin gerekce sart, onay icin degil. Bu kural serviste kontrol ediliyor;
        // Bean Validation ile ifade etmek icin sinif seviyesinde ozel bir dogrulayici
        // yazmak gerekirdi, tek kural icin degmez.
        @Size(max = 1000, message = "Aciklama en fazla 1000 karakter olabilir")
        String aciklama
) {
}
