package tr.ebrar.talep.web;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tr.ebrar.talep.service.RaporServisi;
import tr.ebrar.talep.service.dto.RaporDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/raporlar")
@Tag(name = "Raporlar", description = "Yonetici ozet raporlari")
public class RaporController {

    private final RaporServisi raporServisi;

    public RaporController(RaporServisi raporServisi) {
        this.raporServisi = raporServisi;
    }

    @GetMapping("/ozet")
    @PreAuthorize("hasRole('YONETICI')")
    @Operation(summary = "Durum bazinda talep dagilimi. birimId verilmezse tum kurum.")
    public RaporDto ozet(@RequestParam(required = false) Long birimId) {
        return raporServisi.ozet(birimId);
    }
}
