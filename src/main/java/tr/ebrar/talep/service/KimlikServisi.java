package tr.ebrar.talep.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.hata.KayitBulunamadiException;
import tr.ebrar.talep.repository.KullaniciRepository;
import tr.ebrar.talep.security.JwtUretici;
import tr.ebrar.talep.service.dto.GirisYanitiDto;
import tr.ebrar.talep.service.dto.KullaniciOzetDto;
import tr.ebrar.talep.service.komut.GirisKomutu;

@Service
@Transactional(readOnly = true)
public class KimlikServisi {

    private static final Logger log = LoggerFactory.getLogger(KimlikServisi.class);

    private final AuthenticationManager kimlikYoneticisi;
    private final KullaniciRepository kullaniciRepository;
    private final JwtUretici jwtUretici;

    public KimlikServisi(AuthenticationManager kimlikYoneticisi,
                         KullaniciRepository kullaniciRepository,
                         JwtUretici jwtUretici) {
        this.kimlikYoneticisi = kimlikYoneticisi;
        this.kullaniciRepository = kullaniciRepository;
        this.jwtUretici = jwtUretici;
    }

    public GirisYanitiDto giris(GirisKomutu komut) {
        try {
            kimlikYoneticisi.authenticate(
                    new UsernamePasswordAuthenticationToken(komut.kullaniciAdi(), komut.sifre()));
        } catch (AuthenticationException e) {
            // Basarisiz girisleri iz birakacak sekilde logluyoruz ama sifreyi degil,
            // yalnizca kullanici adini yaziyoruz.
            log.warn("Basarisiz giris denemesi: kullanici={}", komut.kullaniciAdi());
            throw e;
        }

        Kullanici kullanici = kullaniciRepository.findByKullaniciAdi(komut.kullaniciAdi())
                .orElseThrow(() -> new KayitBulunamadiException("Kullanici", komut.kullaniciAdi()));

        String token = jwtUretici.uret(kullanici.getKullaniciAdi(), kullanici.getRol());
        log.info("Giris basarili: kullanici={}, rol={}", kullanici.getKullaniciAdi(), kullanici.getRol());

        return GirisYanitiDto.bearer(token, jwtUretici.gecerlilikSuresiSaniye(), ozet(kullanici));
    }

    public KullaniciOzetDto aktifKullanici(String kullaniciAdi) {
        return ozet(kullaniciRepository.findByKullaniciAdi(kullaniciAdi)
                .orElseThrow(() -> new KayitBulunamadiException("Kullanici", kullaniciAdi)));
    }

    private KullaniciOzetDto ozet(Kullanici kullanici) {
        return new KullaniciOzetDto(
                kullanici.getId(),
                kullanici.getKullaniciAdi(),
                kullanici.getAdSoyad(),
                kullanici.getRol(),
                kullanici.getBirim().getKod());
    }
}
