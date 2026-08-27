package tr.ebrar.talep.service;

import java.util.List;

import tr.ebrar.talep.domain.Bildirim;
import tr.ebrar.talep.domain.Kullanici;
import tr.ebrar.talep.domain.OnayKaydi;
import tr.ebrar.talep.domain.Talep;
import tr.ebrar.talep.service.dto.BildirimDto;
import tr.ebrar.talep.service.dto.KullaniciOzetDto;
import tr.ebrar.talep.service.dto.OnayKaydiDto;
import tr.ebrar.talep.service.dto.TalepDetayDto;
import tr.ebrar.talep.service.dto.TalepOzetDto;

/**
 * Varlik -> DTO cevrimi.
 *
 * <p>MapStruct gibi bir kutuphane eklemedim: bes tane DTO icin uretilmis kod
 * okumak, elle yazilmis yirmi satiri okumaktan zor. Cevrim sayisi otuza cikarsa
 * karar yeniden gozden gecirilir.
 *
 * <p>Onemli: buradaki metotlar tembel iliskilere dokunuyor. Bu yuzden mutlaka
 * transaction acikken, yani servis katmaninda cagrilmali. Controller'da
 * cagrilirsa LazyInitializationException gelir (open-in-view kapali).
 */
final class TalepDonusturucu {

    private TalepDonusturucu() {
    }

    static KullaniciOzetDto kullaniciOzeti(Kullanici kullanici) {
        return new KullaniciOzetDto(
                kullanici.getId(),
                kullanici.getKullaniciAdi(),
                kullanici.getAdSoyad(),
                kullanici.getRol(),
                kullanici.getBirim().getKod());
    }

    static TalepOzetDto ozet(Talep talep) {
        return new TalepOzetDto(
                talep.getId(),
                talep.getBaslik(),
                talep.getTur(),
                talep.getDurum(),
                talep.getTutar(),
                talep.getTalepEden().getAdSoyad(),
                talep.getBirim().getKod(),
                talep.getOlusturmaTarihi());
    }

    static TalepDetayDto detay(Talep talep, List<OnayKaydi> gecmis) {
        return new TalepDetayDto(
                talep.getId(),
                talep.getBaslik(),
                talep.getAciklama(),
                talep.getTur(),
                talep.getDurum(),
                talep.getTutar(),
                kullaniciOzeti(talep.getTalepEden()),
                talep.getBirim().getKod(),
                talep.getBirim().getAd(),
                talep.getOlusturmaTarihi(),
                talep.getGuncellemeTarihi(),
                gecmis.stream().map(TalepDonusturucu::onayKaydi).toList());
    }

    static OnayKaydiDto onayKaydi(OnayKaydi kayit) {
        return new OnayKaydiDto(
                kayit.getId(),
                kayit.getOncekiDurum(),
                kayit.getYeniDurum(),
                kayit.getIslemYapan().getAdSoyad(),
                kayit.getAciklama(),
                kayit.getOlusturmaTarihi());
    }

    static BildirimDto bildirim(Bildirim bildirim) {
        return new BildirimDto(
                bildirim.getId(),
                bildirim.getMesaj(),
                bildirim.isOkundu(),
                bildirim.getTalep().getId(),
                bildirim.getOlusturmaTarihi());
    }
}
