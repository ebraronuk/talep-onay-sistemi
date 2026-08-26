package tr.ebrar.talep.web;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Sayfali yanit sozlesmesi.
 *
 * <p>Spring'in {@code Page} nesnesini dogrudan JSON'a cevirmiyoruz. Sebep:
 * o siniftan uretilen JSON, Spring surumune bagli olarak degisebiliyor
 * (pageable, sort, first/last alanlari...). Istemci sozlesmesinin cerceve
 * surumune bagli olmasi istemedigimiz bir bagimlilik. Burada ne donecegini
 * biz belirliyoruz.
 */
public record SayfaYaniti<T>(
        List<T> icerik,
        int sayfaNo,
        int sayfaBoyutu,
        long toplamKayit,
        int toplamSayfa,
        boolean sonSayfaMi
) {

    public static <T> SayfaYaniti<T> of(Page<T> sayfa) {
        return new SayfaYaniti<>(
                sayfa.getContent(),
                sayfa.getNumber(),
                sayfa.getSize(),
                sayfa.getTotalElements(),
                sayfa.getTotalPages(),
                sayfa.isLast());
    }
}
