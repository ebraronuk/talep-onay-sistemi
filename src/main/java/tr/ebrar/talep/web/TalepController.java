package tr.ebrar.talep.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;
import tr.ebrar.talep.service.TalepServisi;
import tr.ebrar.talep.service.dto.TalepDetayDto;
import tr.ebrar.talep.service.dto.TalepOzetDto;
import tr.ebrar.talep.service.komut.OnayKarariKomutu;
import tr.ebrar.talep.service.komut.TalepFiltresi;
import tr.ebrar.talep.service.komut.TalepGuncelleKomutu;
import tr.ebrar.talep.service.komut.TalepOlusturKomutu;

import java.net.URI;

/**
 * Talep uclari.
 *
 * <p>Controller'da is mantigi yok; gorevi HTTP ile servis arasinda ceviri yapmak.
 * Yetki de iki kademeli: buradaki {@code @PreAuthorize} rolu kontrol ediyor,
 * kaydin sahibi kim sorusuna serviste bakiliyor.
 */
@RestController
@RequestMapping("/api/talepler")
@Tag(name = "Talepler", description = "Talep olusturma, listeleme ve onay akisi")
public class TalepController {

    private final TalepServisi talepServisi;

    public TalepController(TalepServisi talepServisi) {
        this.talepServisi = talepServisi;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PERSONEL')")
    @Operation(summary = "Yeni talep olusturur (taslak olarak baslar)")
    public ResponseEntity<TalepDetayDto> olustur(@Valid @RequestBody TalepOlusturKomutu komut,
                                                 Authentication kimlik,
                                                 UriComponentsBuilder uriKurucu) {
        TalepDetayDto olusan = talepServisi.olustur(komut, kimlik.getName());

        URI konum = uriKurucu.path("/api/talepler/{id}").buildAndExpand(olusan.id()).toUri();
        return ResponseEntity.created(konum).body(olusan);
    }

    @GetMapping
    @Operation(summary = "Talepleri listeler. Kapsam role gore daralir.")
    public SayfaYaniti<TalepOzetDto> listele(
            @RequestParam(required = false) TalepDurumu durum,
            @RequestParam(required = false) TalepTuru tur,
            @RequestParam(required = false) Long birimId,
            @RequestParam(required = false) String baslik,
            @PageableDefault(size = 20, sort = "olusturmaTarihi", direction = Sort.Direction.DESC) Pageable sayfaIstegi,
            Authentication kimlik) {

        TalepFiltresi filtre = new TalepFiltresi(durum, tur, birimId, baslik);
        return SayfaYaniti.of(talepServisi.listele(filtre, sayfaIstegi, kimlik.getName()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Talep detayi ve onay gecmisi")
    public TalepDetayDto detay(@PathVariable Long id, Authentication kimlik) {
        return talepServisi.detay(id, kimlik.getName());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('PERSONEL')")
    @Operation(summary = "Taslak talebi gunceller")
    public TalepDetayDto guncelle(@PathVariable Long id,
                                  @Valid @RequestBody TalepGuncelleKomutu komut,
                                  Authentication kimlik) {
        return talepServisi.guncelle(id, komut, kimlik.getName());
    }

    @PostMapping("/{id}/onaya-gonder")
    @PreAuthorize("hasRole('PERSONEL')")
    @Operation(summary = "Taslak talebi amirin onayina gonderir")
    public TalepDetayDto onayaGonder(@PathVariable Long id, Authentication kimlik) {
        return talepServisi.onayaGonder(id, kimlik.getName());
    }

    @PostMapping("/{id}/karar")
    @PreAuthorize("hasRole('AMIR')")
    @Operation(summary = "Amirin onay veya ret karari")
    public TalepDetayDto karar(@PathVariable Long id,
                               @Valid @RequestBody OnayKarariKomutu komut,
                               Authentication kimlik) {
        return talepServisi.karar(id, komut, kimlik.getName());
    }
}
