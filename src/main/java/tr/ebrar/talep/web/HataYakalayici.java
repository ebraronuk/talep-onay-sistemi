package tr.ebrar.talep.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tr.ebrar.talep.service.hata.GecersizDurumGecisiException;
import tr.ebrar.talep.service.hata.GecersizIslemException;
import tr.ebrar.talep.service.hata.KayitBulunamadiException;
import tr.ebrar.talep.service.hata.YetkisizIslemException;

import java.util.List;

/**
 * Tek merkezden hata yonetimi.
 *
 * <p>Controller'larda try/catch yok. Servis alan diline ait exception firlatiyor,
 * HTTP karsiligina cevrilme isi burada bir kez yapiliyor. Yeni bir hata tipi
 * eklendiginde tek dokunulacak dosya burasi.
 *
 * <p>Kural: 500 disindaki hatalarda stack trace loglanmiyor. Kullanici hatasi
 * (yanlis id, yetkisiz erisim) log'u kirletmemeli; gercek problemi gozden kacirirsin.
 */
@RestControllerAdvice
public class HataYakalayici {

    private static final Logger log = LoggerFactory.getLogger(HataYakalayici.class);

    @ExceptionHandler(KayitBulunamadiException.class)
    public ResponseEntity<HataYaniti> kayitBulunamadi(KayitBulunamadiException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(HataYaniti.of(e.getKod(), e.getMessage()));
    }

    @ExceptionHandler(YetkisizIslemException.class)
    public ResponseEntity<HataYaniti> yetkisiz(YetkisizIslemException e, HttpServletRequest istek) {
        // Yetki ihlalleri iz birakmali; birileri deniyor olabilir.
        log.warn("Yetkisiz erisim denemesi: {} {} - {}", istek.getMethod(), istek.getRequestURI(), e.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(HataYaniti.of(e.getKod(), e.getMessage()));
    }

    @ExceptionHandler(GecersizDurumGecisiException.class)
    public ResponseEntity<HataYaniti> gecersizGecis(GecersizDurumGecisiException e) {
        // 409: istek gecerli ama kaynagin o anki durumuyla cakisiyor.
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(HataYaniti.of(e.getKod(), e.getMessage()));
    }

    @ExceptionHandler(GecersizIslemException.class)
    public ResponseEntity<HataYaniti> gecersizIslem(GecersizIslemException e) {
        return ResponseEntity.badRequest().body(HataYaniti.of(e.getKod(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<HataYaniti> dogrulama(MethodArgumentNotValidException e) {
        List<HataYaniti.AlanHatasi> detaylar = e.getBindingResult().getFieldErrors().stream()
                .map(hata -> new HataYaniti.AlanHatasi(hata.getField(), mesaj(hata)))
                .toList();

        return ResponseEntity.badRequest()
                .body(HataYaniti.of("DOGRULAMA_HATASI", "Gonderilen veri gecerli degil", detaylar));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<HataYaniti> okunamayanGovde(HttpMessageNotReadableException e) {
        // Genelde bozuk JSON ya da enum'a uymayan bir deger. Icerideki mesaji
        // disariya vermiyoruz, paket ici sinif adlari sizabiliyor.
        return ResponseEntity.badRequest()
                .body(HataYaniti.of("GECERSIZ_GOVDE", "Istek govdesi okunamadi"));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<HataYaniti> tipUyusmazligi(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest()
                .body(HataYaniti.of("GECERSIZ_PARAMETRE",
                        "'%s' parametresi beklenen tipte degil".formatted(e.getName())));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<HataYaniti> butunlukIhlali(DataIntegrityViolationException e) {
        // Veritabani kisitindan gelen mesaj (kolon adi, kisit adi) disariya cikmamali.
        log.warn("Veri butunlugu ihlali: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(HataYaniti.of("VERI_CAKISMASI", "Islem mevcut kayitlarla cakisiyor"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<HataYaniti> erisimReddedildi(AccessDeniedException e, HttpServletRequest istek) {
        log.warn("Rol yetkisi yetersiz: {} {}", istek.getMethod(), istek.getRequestURI());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(HataYaniti.of("YETKISIZ_ISLEM", "Bu islem icin yetkiniz yok"));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<HataYaniti> kimlikDogrulanamadi(AuthenticationException e) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(HataYaniti.of("KIMLIK_DOGRULANAMADI", "Kullanici adi veya sifre hatali"));
    }

    /**
     * Beklenmeyen her sey. Buraya dusen bir hata bizim hatamiz demektir,
     * o yuzden tam stack trace ile loglaniyor. Kullaniciya ise sadece
     * genel bir mesaj gidiyor.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<HataYaniti> beklenmeyen(Exception e, HttpServletRequest istek) {
        log.error("Beklenmeyen hata: {} {}", istek.getMethod(), istek.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(HataYaniti.of("SUNUCU_HATASI", "Beklenmeyen bir hata olustu"));
    }

    private String mesaj(FieldError hata) {
        return hata.getDefaultMessage() == null ? "Gecersiz deger" : hata.getDefaultMessage();
    }
}
