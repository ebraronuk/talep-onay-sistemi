package tr.ebrar.talep.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Zamanlanmis islerin acilip kapanmasi.
 *
 * <p>Ayar yalnizca testler icin var: arka planda calisan bir isleyici, testin kurdugu
 * ara durumu testten once degistirir. Ayar hic verilmezse zamanlama <b>acik</b>; unutulan
 * bir satir yuzunden bildirimlerin sessizce durmasini istemiyoruz.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "talep.giden-olay.zamanlama-etkin", havingValue = "true", matchIfMissing = true)
public class ZamanlamaYapilandirmasi {
}
