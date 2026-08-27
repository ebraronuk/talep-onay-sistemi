package tr.ebrar.talep.service;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Onay akisi ayarlari.
 *
 * <p>Limit koda gomulmedi cunku bu bir is kurali parametresi, teknik bir sabit degil.
 * Kurumun onay limiti degistiginde kod degismemeli, yeniden derlenmemeli ve
 * yeniden test edilmemeli; ortam degiskeni yetmeli.
 *
 * @param yoneticiLimiti bu tutari <b>asan</b> talepler, birim amiri onayladiktan
 *     sonra yonetici onayina duser. Esit olan talepler tek kademede biter.
 */
@ConfigurationProperties(prefix = "talep.onay")
public record OnayAyarlari(BigDecimal yoneticiLimiti) {

    public OnayAyarlari {
        if (yoneticiLimiti == null || yoneticiLimiti.signum() < 0) {
            throw new IllegalStateException("talep.onay.yonetici-limiti sifir veya pozitif olmali");
        }
    }
}
