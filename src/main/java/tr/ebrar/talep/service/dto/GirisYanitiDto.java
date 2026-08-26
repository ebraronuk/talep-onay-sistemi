package tr.ebrar.talep.service.dto;

public record GirisYanitiDto(
        String token,
        String tip,
        long gecerlilikSaniye,
        KullaniciOzetDto kullanici
) {

    public static GirisYanitiDto bearer(String token, long gecerlilikSaniye, KullaniciOzetDto kullanici) {
        return new GirisYanitiDto(token, "Bearer", gecerlilikSaniye, kullanici);
    }
}
