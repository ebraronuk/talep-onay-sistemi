package tr.ebrar.talep.mimari;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Katman kurallari yorum satiri degil, test.
 *
 * <p>README'de "controller'da is mantigi yok" yazmak kolay; alti ay sonra hala
 * dogru oldugunu garanti eden sey bu testler. Bir kural bozuldugunda derleme
 * degil test kirmizi yanar ve hata mesaji nedenini soyler.
 */
class MimariKurallariTest {

    private static final String KOK = "tr.ebrar.talep";

    private static JavaClasses siniflar;

    @BeforeAll
    static void yukle() {
        siniflar = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(KOK);
    }

    @Test
    @DisplayName("domain katmani hicbir ust katmani tanimaz")
    void domainBagimsizKalir() {
        ArchRule kural = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("..service..", "..web..", "..repository..", "..security..", "..config..")
                .because("alan modeli, kendisini kullanan katmanlari tanimamali; "
                        + "aksi halde is kurallarini test etmek icin cerceve ayaga kaldirmak gerekir");

        kural.check(siniflar);
    }

    @Test
    @DisplayName("domain katmani Spring Web'e bagimli degil")
    void domainSpringWebTanimaz() {
        ArchRule kural = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "org.springframework.http..",
                        "org.springframework.security..")
                .because("alan modeli HTTP'den ve guvenlik cercevesinden bagimsiz olmali");

        kural.check(siniflar);
    }

    @Test
    @DisplayName("web katmani repository'ye dogrudan erisemez")
    void webRepositoryyeDokunmaz() {
        ArchRule kural = noClasses()
                .that().resideInAPackage("..web..")
                .should().dependOnClassesThat().resideInAPackage("..repository..")
                .because("controller veriye servis uzerinden ulasmali; dogrudan erisim "
                        + "transaction sinirini ve yetki kontrolunu atlar");

        kural.check(siniflar);
    }

    @Test
    @DisplayName("repository katmani servis ve web'i tanimaz")
    void repositoryYukariBakmaz() {
        ArchRule kural = noClasses()
                .that().resideInAPackage("..repository..")
                .should().dependOnClassesThat().resideInAnyPackage("..service..", "..web..")
                .because("bagimlilik yonu tek yonlu olmali: web -> service -> repository");

        kural.check(siniflar);
    }

    @Test
    @DisplayName("servis katmani web'i tanimaz")
    void servisWebiTanimaz() {
        ArchRule kural = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat().resideInAPackage("..web..")
                .because("is kurallari HTTP'den bagimsiz olmali");

        kural.check(siniflar);
    }

    @Test
    @DisplayName("servis katmani kalicilik tiplerini disariya sizdirmaz")
    void servisSpecificationKullanmaz() {
        ArchRule kural = noClasses()
                .that().resideInAPackage("..service..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.data.jpa.domain..", "jakarta.persistence.criteria..")
                .because("Specification ve Criteria API kalicilik detayi; sorgu kurma isi "
                        + "repository katmaninda kalmali (bkz. TalepRepository.ara)");

        kural.check(siniflar);
    }

    @Test
    @DisplayName("controller metotlari entity dondurmez")
    void controllerEntityDondurmez() {
        ArchRule kural = noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage("..web..")
                .and().arePublic()
                .should().haveRawReturnType(com.tngtech.archunit.base.DescribedPredicate.describe(
                        "bir entity", tip -> tip.getPackageName().equals(KOK + ".domain")
                                && tip.isAnnotatedWith("jakarta.persistence.Entity")))
                .because("entity dondurmek kalicilik semasini API sozlesmesine cevirir; "
                        + "ayrica tembel iliskiler serilestirme aninda patlar");

        kural.check(siniflar);
    }

    @Test
    @DisplayName("paketler arasinda dongusel bagimlilik yok")
    void dongusuzBagimlilik() {
        ArchRule kural = slices()
                .matching(KOK + ".(*)..")
                .should().beFreeOfCycles()
                .because("dongu, katman ayrimin kagit uzerinde kaldiginin en net isareti");

        kural.check(siniflar);
    }

    @Test
    @DisplayName("uretim kodunda System.out kullanilmaz")
    void loglamaSlf4jUzerinden() {
        ArchRule kural = noClasses()
                .that().resideInAPackage(KOK + "..")
                .should().accessField(System.class, "out")
                .orShould().accessField(System.class, "err")
                .because("loglama SLF4J uzerinden yapilmali; System.out seviyesi, "
                        + "bicimi ve korelasyon kimligi olmayan bir cikti uretir");

        kural.check(siniflar);
    }

    @Test
    @DisplayName("repository arayuzleri repository paketinde durur")
    void repositoryyeYerlesim() {
        ArchRule kural = classes()
                .that().areAssignableTo(org.springframework.data.repository.Repository.class)
                .should().resideInAPackage("..repository..")
                .because("veri erisimi tek bir yerde toplanmali");

        kural.check(siniflar);
    }
}
