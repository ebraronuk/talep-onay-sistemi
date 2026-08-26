package tr.ebrar.talep.destek;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import tr.ebrar.talep.domain.Rol;

import java.util.List;

/**
 * Controller testlerinde istege kimlik takmak icin.
 *
 * <p>Burada @WithMockUser yerine dogrudan principal veriyoruz. Sebep: bu testlerde
 * guvenlik filtreleri kapali (rol kontrolu ayri bir entegrasyon testinde),
 * filtreler kapaliyken @WithMockUser'in kurdugu baglam istege yansimiyor.
 */
public final class SahteKimlik {

    private SahteKimlik() {
    }

    public static Authentication olarak(String kullaniciAdi, Rol rol) {
        return new UsernamePasswordAuthenticationToken(
                kullaniciAdi, null, List.of(new SimpleGrantedAuthority(rol.yetkiAdi())));
    }
}
