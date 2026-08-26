package tr.ebrar.talep.security;

import java.nio.charset.StandardCharsets;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * talep.jwt.* ayarlari.
 *
 * <p>Anahtar uzunlugu acilista kontrol ediliyor. HS256 icin 256 bit (32 bayt)
 * alt sinir; kisa anahtarla jjwt zaten hata veriyor ama o hata ilk giris
 * denemesinde, yani uretimde ortaya cikardi. Acilista patlamasi daha iyi.
 */
@ConfigurationProperties(prefix = "talep.jwt")
public record JwtAyarlari(String gizliAnahtar, int gecerlilikSuresiDakika) {

    private static final int ASGARI_ANAHTAR_BAYTI = 32;

    public JwtAyarlari {
        if (gizliAnahtar == null || gizliAnahtar.getBytes(StandardCharsets.UTF_8).length < ASGARI_ANAHTAR_BAYTI) {
            throw new IllegalStateException(
                    "talep.jwt.gizli-anahtar en az " + ASGARI_ANAHTAR_BAYTI + " bayt olmali. "
                            + "Uretimde JWT_SECRET ortam degiskenini tanimlayin.");
        }
        if (gecerlilikSuresiDakika <= 0) {
            throw new IllegalStateException("talep.jwt.gecerlilik-suresi-dakika pozitif olmali");
        }
    }
}
