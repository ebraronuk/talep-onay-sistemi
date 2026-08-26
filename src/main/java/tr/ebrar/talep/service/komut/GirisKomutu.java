package tr.ebrar.talep.service.komut;

import jakarta.validation.constraints.NotBlank;

public record GirisKomutu(

        @NotBlank(message = "Kullanici adi zorunludur")
        String kullaniciAdi,

        @NotBlank(message = "Sifre zorunludur")
        String sifre
) {

    // Sifrenin log'a ya da hata mesajina dusmemesi icin toString ezildi.
    // Record'un varsayilan toString'i butun alanlari basar, o yuzden bu sart.
    @Override
    public String toString() {
        return "GirisKomutu{kullaniciAdi='" + kullaniciAdi + "'}";
    }
}
