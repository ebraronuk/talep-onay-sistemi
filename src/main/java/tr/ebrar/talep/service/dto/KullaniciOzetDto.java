package tr.ebrar.talep.service.dto;

import tr.ebrar.talep.domain.Rol;

// Kullaniciyi disariya verirken sadece bunlar gidiyor. Eposta ve sifre ozeti burada
// bilerek yok; talep listesinde kimsenin epostasini gostermeye gerek yok.
public record KullaniciOzetDto(
        Long id,
        String kullaniciAdi,
        String adSoyad,
        Rol rol,
        String birimKodu
) {
}
