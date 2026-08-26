package tr.ebrar.talep.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import tr.ebrar.talep.domain.GecersizDurumGecisiException;
import tr.ebrar.talep.hata.GecersizIslemException;
import tr.ebrar.talep.hata.HataYaniti;
import tr.ebrar.talep.hata.IsKuraliException;
import tr.ebrar.talep.hata.KayitBulunamadiException;
import tr.ebrar.talep.hata.YetkisizIslemException;

/**
 * Tek merkezden hata yonetimi.
 *
 * <p>Controller'larda try/catch yok. Servis alan diline ait exception firlatiyor,
 * HTTP karsiligina cevrilme isi burada bir kez yapiliyor.
 *
 * <p><b>Neden {@link ResponseEntityExceptionHandler} genisletiliyor:</b> once bunu
 * yapmadan yalnizca {@code @ExceptionHandler(Exception.class)} yazmistim ve gercek
 * bir hata cikti. Spring'in {@code ExceptionHandlerExceptionResolver}'i, cercevenin
 * kendi {@code DefaultHandlerExceptionResolver}'indan ONCE calisiyor. Yani genel
 * Exception yakalayicisi, Spring MVC'nin dogru status kodunu bildigi hatalari da
 * (bilinmeyen yol, desteklenmeyen metot, eksik parametre) yutup 500 donduruyordu.
 * Bu sinifi genisletince o hatalar dogru kodlarina (404, 405, 400, 415) donuyor;
 * biz de govdeyi kendi sozlesmemize cevirmek icin
 * {@link #handleExceptionInternal} metodunu eziyoruz.
 *
 * <p>Kural: 500 disindaki hatalarda stack trace loglanmiyor. Kullanici hatasi
 * (yanlis id, yetkisiz erisim) log'u kirletmemeli, gercek problemi gozden kacirirsin.
 */
@RestControllerAdvice
public class HataYakalayici extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(HataYakalayici.class);

    // --- alan diline ait hatalar ----------------------------------------

    @ExceptionHandler(KayitBulunamadiException.class)
    public ResponseEntity<HataYaniti> kayitBulunamadi(KayitBulunamadiException e) {
        return yanit(HttpStatus.NOT_FOUND, e);
    }

    @ExceptionHandler(YetkisizIslemException.class)
    public ResponseEntity<HataYaniti> yetkisiz(YetkisizIslemException e, HttpServletRequest istek) {
        // Yetki ihlalleri iz birakmali; birileri deniyor olabilir.
        log.warn("Yetkisiz erisim denemesi: {} {} - {}", istek.getMethod(), istek.getRequestURI(), e.getMessage());
        return yanit(HttpStatus.FORBIDDEN, e);
    }

    @ExceptionHandler(GecersizDurumGecisiException.class)
    public ResponseEntity<HataYaniti> gecersizGecis(GecersizDurumGecisiException e) {
        // 409: istek gecerli ama kaynagin o anki durumuyla cakisiyor.
        return yanit(HttpStatus.CONFLICT, e);
    }

    @ExceptionHandler(GecersizIslemException.class)
    public ResponseEntity<HataYaniti> gecersizIslem(GecersizIslemException e) {
        return yanit(HttpStatus.BAD_REQUEST, e);
    }

    // --- kalicilik ve guvenlik kaynakli hatalar -------------------------

    /**
     * Iki amir ayni talebe ayni anda karar verdiginde ikincisi buraya duser.
     * Talep varliginda {@code @Version} kolonu var; Hibernate ikinci guncellemeyi
     * reddediyor. Istemciye 409 donuyoruz: "kayit senin okudugundan beri degisti".
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<HataYaniti> esZamanliDegisiklik(OptimisticLockingFailureException e) {
        log.warn("Es zamanli degisiklik cakismasi: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(HataYaniti.of(
                "ES_ZAMANLI_DEGISIKLIK",
                "Bu kayit siz goruntulerken baskasi tarafindan degistirildi. Sayfayi yenileyip tekrar deneyin."));
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
     * o yuzden tam stack trace ile loglaniyor. Kullaniciya sadece genel bir mesaj gidiyor.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<HataYaniti> beklenmeyen(Exception e, HttpServletRequest istek) {
        log.error("Beklenmeyen hata: {} {}", istek.getMethod(), istek.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(HataYaniti.of("SUNUCU_HATASI", "Beklenmeyen bir hata olustu"));
    }

    // --- Spring MVC'nin kendi hatalari ----------------------------------

    /** Alan bazli dogrulama hatalari; detaylar listesi yalnizca burada doluyor. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
                                                                  HttpHeaders basliklar,
                                                                  HttpStatusCode durum,
                                                                  WebRequest istek) {
        List<HataYaniti.AlanHatasi> detaylar = e.getBindingResult().getFieldErrors().stream()
                .map(hata -> new HataYaniti.AlanHatasi(hata.getField(), mesaj(hata)))
                .toList();

        return ResponseEntity.badRequest()
                .body(HataYaniti.of("DOGRULAMA_HATASI", "Gonderilen veri gecerli degil", detaylar));
    }

    /**
     * Cercevenin urettigi diger tum hatalar (bilinmeyen yol, desteklenmeyen metot,
     * okunamayan govde, eksik parametre...) buradan geciyor ve govdeleri bizim
     * sozlesmemize cevriliyor. Boylece istemci tarafinda tek bir hata isleyici yetiyor.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e,
                                                             @Nullable Object govde,
                                                             HttpHeaders basliklar,
                                                             HttpStatusCode durum,
                                                             WebRequest istek) {
        if (durum.is5xxServerError()) {
            log.error("Cerceve kaynakli sunucu hatasi: {}", yol(istek), e);
        }

        HataYaniti hata = HataYaniti.of(kod(e, durum), kullaniciyaGosterilecekMesaj(e, durum));
        return super.handleExceptionInternal(e, hata, basliklar, durum, istek);
    }

    // --- yardimcilar ----------------------------------------------------

    private ResponseEntity<HataYaniti> yanit(HttpStatus durum, IsKuraliException e) {
        return ResponseEntity.status(durum).body(HataYaniti.of(e.getKod(), e.getMessage()));
    }

    /**
     * Once hata tipine, olmadi durum koduna bakiyoruz. Tipe bakmak istemciye daha
     * kullanisli bir kod veriyor: "GECERSIZ_PARAMETRE" ile "GECERSIZ_GOVDE" ikisi de
     * 400 ama istemci tarafinda farkli sey yapmayi gerektiriyor.
     */
    private String kod(Exception e, HttpStatusCode durum) {
        if (e instanceof HttpMessageNotReadableException) {
            return "GECERSIZ_GOVDE";
        }
        if (e instanceof MethodArgumentTypeMismatchException || e instanceof TypeMismatchException) {
            return "GECERSIZ_PARAMETRE";
        }
        if (e instanceof MissingServletRequestParameterException) {
            return "EKSIK_PARAMETRE";
        }
        return switch (HttpStatus.valueOf(durum.value())) {
            case NOT_FOUND -> "KAYNAK_BULUNAMADI";
            case METHOD_NOT_ALLOWED -> "DESTEKLENMEYEN_METOT";
            case UNSUPPORTED_MEDIA_TYPE -> "DESTEKLENMEYEN_ICERIK_TIPI";
            default -> durum.is5xxServerError() ? "SUNUCU_HATASI" : "GECERSIZ_ISTEK";
        };
    }

    /**
     * Cerceve mesajlari paket ici sinif adlarini ve Jackson ayrintilarini
     * icerebiliyor; disariya yalnizca duruma karsilik gelen genel bir cumle gidiyor.
     */
    private String kullaniciyaGosterilecekMesaj(Exception e, HttpStatusCode durum) {
        if (e instanceof HttpMessageNotReadableException) {
            return "Istek govdesi okunamadi";
        }
        if (e instanceof MethodArgumentTypeMismatchException uyusmazlik) {
            return "'%s' parametresi beklenen tipte degil".formatted(uyusmazlik.getName());
        }
        if (e instanceof MissingServletRequestParameterException eksik) {
            return "'%s' parametresi zorunlu".formatted(eksik.getParameterName());
        }
        return switch (HttpStatus.valueOf(durum.value())) {
            case NOT_FOUND -> "Istenen kaynak bulunamadi";
            case METHOD_NOT_ALLOWED -> "Bu uc bu HTTP metodunu desteklemiyor";
            case UNSUPPORTED_MEDIA_TYPE -> "Desteklenmeyen icerik tipi";
            case BAD_REQUEST -> "Istek gecerli degil";
            default -> durum.is5xxServerError() ? "Beklenmeyen bir hata olustu" : "Istek islenemedi";
        };
    }

    private String yol(WebRequest istek) {
        return istek instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : "-";
    }

    private String mesaj(FieldError hata) {
        return hata.getDefaultMessage() == null ? "Gecersiz deger" : hata.getDefaultMessage();
    }
}
