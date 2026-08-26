package tr.ebrar.talep.web;

import jakarta.validation.Valid;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tr.ebrar.talep.service.KimlikServisi;
import tr.ebrar.talep.service.dto.GirisYanitiDto;
import tr.ebrar.talep.service.dto.KullaniciOzetDto;
import tr.ebrar.talep.service.komut.GirisKomutu;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/kimlik")
@Tag(name = "Kimlik", description = "Giris ve aktif kullanici bilgisi")
public class KimlikController {

    private final KimlikServisi kimlikServisi;

    public KimlikController(KimlikServisi kimlikServisi) {
        this.kimlikServisi = kimlikServisi;
    }

    @PostMapping("/giris")
    @Operation(summary = "Kullanici adi ve sifre ile token alir")
    public GirisYanitiDto giris(@Valid @RequestBody GirisKomutu komut) {
        return kimlikServisi.giris(komut);
    }

    @GetMapping("/ben")
    @Operation(summary = "Token sahibinin bilgileri. On yuz menuyu buna gore kuruyor.")
    public KullaniciOzetDto ben(Authentication kimlik) {
        return kimlikServisi.aktifKullanici(kimlik.getName());
    }
}
