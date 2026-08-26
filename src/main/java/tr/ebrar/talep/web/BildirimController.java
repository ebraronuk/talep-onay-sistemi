package tr.ebrar.talep.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tr.ebrar.talep.service.BildirimServisi;
import tr.ebrar.talep.service.dto.BildirimDto;

import java.util.Map;

@RestController
@RequestMapping("/api/bildirimler")
@Tag(name = "Bildirimler", description = "Uygulama ici bildirimler")
public class BildirimController {

    private final BildirimServisi bildirimServisi;

    public BildirimController(BildirimServisi bildirimServisi) {
        this.bildirimServisi = bildirimServisi;
    }

    @GetMapping
    @Operation(summary = "Giris yapan kullanicinin bildirimleri")
    public SayfaYaniti<BildirimDto> bildirimlerim(
            @PageableDefault(size = 20) Pageable sayfaIstegi, Authentication kimlik) {
        return SayfaYaniti.of(bildirimServisi.bildirimlerim(kimlik.getName(), sayfaIstegi));
    }

    @GetMapping("/okunmamis-sayisi")
    @Operation(summary = "Menudeki rozet icin okunmamis bildirim sayisi")
    public Map<String, Long> okunmamisSayisi(Authentication kimlik) {
        // Cikplak bir sayi yerine nesne donuyoruz: ileride "sonGuncelleme" gibi
        // bir alan eklemek gerekirse istemci sozlesmesi kirilmasin.
        return Map.of("adet", bildirimServisi.okunmamisSayisi(kimlik.getName()));
    }

    @PostMapping("/{id}/okundu")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Bildirimi okundu olarak isaretler")
    public void okunduIsaretle(@PathVariable Long id, Authentication kimlik) {
        bildirimServisi.okunduIsaretle(id, kimlik.getName());
    }
}
