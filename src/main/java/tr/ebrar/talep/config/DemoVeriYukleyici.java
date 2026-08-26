package tr.ebrar.talep.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tr.ebrar.talep.domain.Birim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.Rol;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.domain.TalepDurumu;
import tr.ebrar.talep.domain.TalepTuru;
import tr.ebrar.talep.repository.BirimRepository;
import tr.ebrar.talep.repository.KullaniciRepository;
import tr.ebrar.talep.repository.TalepRepository;

import java.util.List;

/**
 * Demo verisi. Yalnizca "demo" profilinde calisir (docker-compose bu profili aciyor).
 *
 * <p>Neden Flyway migration degil? Migration klasoru semanin tanimi ve uretimde de
 * calisiyor; oraya test kullanicisi koymak uretim veritabanina test kullanicisi
 * yazmak demek olurdu. Ayrinti icin docs/decisions.md K-010.
 *
 * <p>Idempotent: veri zaten varsa hicbir sey yapmaz. Konteyner yeniden basladiginda
 * ayni kayitlarin ikinci kez yazilmasini istemiyoruz.
 */
@Component
@Profile("demo")
public class DemoVeriYukleyici implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoVeriYukleyici.class);
    private static final String DEMO_SIFRESI = "Parola123!";

    private final BirimRepository birimRepository;
    private final KullaniciRepository kullaniciRepository;
    private final TalepRepository talepRepository;
    private final PasswordEncoder sifreKodlayici;

    public DemoVeriYukleyici(BirimRepository birimRepository,
                             KullaniciRepository kullaniciRepository,
                             TalepRepository talepRepository,
                             PasswordEncoder sifreKodlayici) {
        this.birimRepository = birimRepository;
        this.kullaniciRepository = kullaniciRepository;
        this.talepRepository = talepRepository;
        this.sifreKodlayici = sifreKodlayici;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (birimRepository.count() > 0) {
            log.info("Demo verisi zaten yuklu, atlaniyor.");
            return;
        }

        Birim btgm = birimRepository.save(new Birim("BTGM", "Bilgi Teknolojileri Genel Mudurlugu"));
        Birim muhasebe = birimRepository.save(new Birim("MUH", "Muhasebe Mudurlugu"));
        Birim ik = birimRepository.save(new Birim("IK", "Insan Kaynaklari Mudurlugu"));

        Kullanici ayse = kullanici("ayse.yilmaz", "Ayse Yilmaz", Rol.PERSONEL, btgm);
        Kullanici mehmet = kullanici("mehmet.demir", "Mehmet Demir", Rol.PERSONEL, btgm);
        Kullanici zeynep = kullanici("zeynep.kaya", "Zeynep Kaya", Rol.PERSONEL, muhasebe);
        Kullanici ali = kullanici("ali.vural", "Ali Vural", Rol.AMIR, btgm);
        kullanici("fatma.sahin", "Fatma Sahin", Rol.AMIR, muhasebe);
        kullanici("hakan.ozturk", "Hakan Ozturk", Rol.YONETICI, ik);

        // Farkli durumlarda ornek talepler: ekran acildiginda bos liste gorunmesin.
        talepRepository.save(new Talep("Dizustu bilgisayar talebi",
                "Mevcut cihaz derleme islerinde yetersiz kaliyor, 32 GB bellekli bir cihaz gerekiyor.",
                TalepTuru.DONANIM, ayse));

        Talep beklemede = new Talep("Yillik izin talebi",
                "15-22 Eylul tarihleri arasinda yillik izin kullanmak istiyorum.",
                TalepTuru.IZIN, ayse);
        beklemede.durumDegistir(TalepDurumu.BEKLEMEDE, ayse, "Onaya gonderildi");
        talepRepository.save(beklemede);

        Talep ikinciBeklemede = new Talep("Lisans yenileme",
                "IDE lisanslarinin suresi ay sonunda doluyor, yenilenmesi gerekiyor.",
                TalepTuru.SATIN_ALMA, mehmet);
        ikinciBeklemede.durumDegistir(TalepDurumu.BEKLEMEDE, mehmet, null);
        talepRepository.save(ikinciBeklemede);

        Talep onaylanmis = new Talep("Spring Boot egitimi",
                "Ekip icin uc gunluk Spring Boot egitimi talebi.",
                TalepTuru.EGITIM, mehmet);
        onaylanmis.durumDegistir(TalepDurumu.BEKLEMEDE, mehmet, null);
        onaylanmis.durumDegistir(TalepDurumu.ONAYLANDI, ali, "Butcede yeri var, uygundur.");
        talepRepository.save(onaylanmis);

        Talep reddedilmis = new Talep("Ikinci monitor",
                "Evden calisma icin ikinci monitor talebi.",
                TalepTuru.DONANIM, mehmet);
        reddedilmis.durumDegistir(TalepDurumu.BEKLEMEDE, mehmet, null);
        reddedilmis.durumDegistir(TalepDurumu.REDDEDILDI, ali, "Bu donem donanim butcesi kapandi.");
        talepRepository.save(reddedilmis);

        Talep muhasebeTalebi = new Talep("Yazici toneri",
                "Muhasebe katindaki yazicinin toneri bitmek uzere.",
                TalepTuru.SATIN_ALMA, zeynep);
        muhasebeTalebi.durumDegistir(TalepDurumu.BEKLEMEDE, zeynep, null);
        talepRepository.save(muhasebeTalebi);

        log.info("Demo verisi yuklendi: {} birim, {} kullanici, {} talep. Ortak sifre: {}",
                birimRepository.count(), kullaniciRepository.count(), talepRepository.count(), DEMO_SIFRESI);
    }

    private Kullanici kullanici(String kullaniciAdi, String adSoyad, Rol rol, Birim birim) {
        return kullaniciRepository.save(new Kullanici(
                kullaniciAdi,
                adSoyad,
                kullaniciAdi + "@ornek.gov.tr",
                sifreKodlayici.encode(DEMO_SIFRESI),
                rol,
                birim));
    }
}
