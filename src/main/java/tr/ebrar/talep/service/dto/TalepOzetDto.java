package tr.ebrar.talep.service.dto;

import java.math.BigDecimal;
import java.time.Instant;

import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;

// Liste ekraninin ihtiyaci kadar alan. Aciklama alani kasitli olarak yok:
// 4000 karakterlik metni 20 satirlik listede tasimanin anlami yok.
public record TalepOzetDto(
        Long id,
        String baslik,
        TalepTuru tur,
        TalepDurumu durum,
        BigDecimal tutar,
        String talepEdenAdSoyad,
        String birimKodu,
        Instant olusturmaTarihi
) {
}
