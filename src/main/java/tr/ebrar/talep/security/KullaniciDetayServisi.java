package tr.ebrar.talep.security;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.repository.KullaniciRepository;

import java.util.List;

/**
 * Giris sirasinda kullaniciyi yukler.
 *
 * <p>Yalnizca giris akisinda cagriliyor; sonraki isteklerde kimlik tokendan
 * geliyor ve buraya ugranmiyor.
 */
@Service
public class KullaniciDetayServisi implements UserDetailsService {

    private final KullaniciRepository kullaniciRepository;

    public KullaniciDetayServisi(KullaniciRepository kullaniciRepository) {
        this.kullaniciRepository = kullaniciRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String kullaniciAdi) throws UsernameNotFoundException {
        Kullanici kullanici = kullaniciRepository.findByKullaniciAdi(kullaniciAdi)
                // Mesajda "kullanici yok" demiyoruz. Hangi kullanici adlarinin
                // sistemde kayitli oldugunu disariya sizdirmanin alemi yok.
                .orElseThrow(() -> new UsernameNotFoundException("Kimlik dogrulanamadi"));

        return User.withUsername(kullanici.getKullaniciAdi())
                .password(kullanici.getSifreOzeti())
                .authorities(List.of(new SimpleGrantedAuthority(kullanici.getRol().yetkiAdi())))
                .disabled(!kullanici.isAktif())
                .build();
    }
}
