// Arka uctaki DTO'larin TypeScript karsiligi.
// Elle tutuluyor: OpenAPI'den kod uretmek de mumkun ama bu boyuttaki bir API icin
// uretilen kodu incelemek elle yazmaktan uzun surer. Uc sayisi ikiye katlanirsa
// openapi-typescript'e gecmek mantikli olur.

export type Rol = 'PERSONEL' | 'AMIR' | 'YONETICI';

export type TalepDurumu =
  | 'TASLAK'
  | 'BEKLEMEDE'
  | 'YONETICI_ONAYINDA'
  | 'ONAYLANDI'
  | 'REDDEDILDI';

export type TalepTuru = 'IZIN' | 'SATIN_ALMA' | 'DONANIM' | 'EGITIM' | 'DIGER';

export interface KullaniciOzeti {
  id: number;
  kullaniciAdi: string;
  adSoyad: string;
  rol: Rol;
  birimKodu: string;
}

export interface GirisYaniti {
  token: string;
  tip: string;
  gecerlilikSaniye: number;
  kullanici: KullaniciOzeti;
}

export interface TalepOzeti {
  id: number;
  baslik: string;
  tur: TalepTuru;
  durum: TalepDurumu;
  // Arka uc BigDecimal donuyor, JSON'da sayi olarak geliyor. Bos birakilabilir:
  // izin talebinin parasal karsiligi yok.
  tutar: number | null;
  talepEdenAdSoyad: string;
  birimKodu: string;
  olusturmaTarihi: string;
}

export interface OnayKaydi {
  id: number;
  oncekiDurum: TalepDurumu | null;
  yeniDurum: TalepDurumu;
  islemYapanAdSoyad: string;
  aciklama: string | null;
  islemTarihi: string;
}

export interface TalepDetayi {
  id: number;
  baslik: string;
  aciklama: string;
  tur: TalepTuru;
  durum: TalepDurumu;
  tutar: number | null;
  talepEden: KullaniciOzeti;
  birimKodu: string;
  birimAdi: string;
  olusturmaTarihi: string;
  guncellemeTarihi: string;
  gecmis: OnayKaydi[];
}

export interface SayfaYaniti<T> {
  icerik: T[];
  sayfaNo: number;
  sayfaBoyutu: number;
  toplamKayit: number;
  toplamSayfa: number;
  sonSayfaMi: boolean;
}

export interface DurumDagilimi {
  durum: TalepDurumu;
  adet: number;
}

export interface Rapor {
  birimId: number | null;
  toplamTalep: number;
  bekleyenTalep: number;
  durumDagilimi: DurumDagilimi[];
}

export interface Bildirim {
  id: number;
  mesaj: string;
  okundu: boolean;
  talepId: number;
  olusturmaTarihi: string;
}

/** Arka ucun hata sozlesmesi: { kod, mesaj, detaylar, zaman } */
export interface HataYaniti {
  kod: string;
  mesaj: string;
  detaylar: { alan: string; mesaj: string }[];
  zaman: string;
}

export const DURUM_ETIKETLERI: Record<TalepDurumu, string> = {
  TASLAK: 'Taslak',
  BEKLEMEDE: 'Amir onayında',
  YONETICI_ONAYINDA: 'Yönetici onayında',
  ONAYLANDI: 'Onaylandı',
  REDDEDILDI: 'Reddedildi',
};

/** Tutari kurumsal bicimde gosterir. Bos tutar tire ile gosteriliyor. */
export function tutarBicimle(tutar: number | null): string {
  if (tutar === null || tutar === undefined) {
    return '-';
  }
  return new Intl.NumberFormat('tr-TR', {
    style: 'currency',
    currency: 'TRY',
    maximumFractionDigits: 2,
  }).format(tutar);
}

export const TUR_ETIKETLERI: Record<TalepTuru, string> = {
  IZIN: 'İzin',
  SATIN_ALMA: 'Satın alma',
  DONANIM: 'Donanım',
  EGITIM: 'Eğitim',
  DIGER: 'Diğer',
};
