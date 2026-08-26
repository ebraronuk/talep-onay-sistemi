package tr.ebrar.talep.service.dto;

import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;

import java.time.Instant;

// Liste ekraninin ihtiyaci kadar alan. Aciklama alani kasitli olarak yok:
// 4000 karakterlik metni 20 satirlik listede tasimanin anlami yok.
public record TalepOzetDto(
        Long id,
        String baslik,
        TalepTuru tur,
        TalepDurumu durum,
        String talepEdenAdSoyad,
        String birimKodu,
        Instant olusturmaTarihi
) {
}
