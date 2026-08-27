-- V6: Tutar alani ve ikinci kademe onay.
--
-- Denetimde cikan en agir bulgu, alan modelinin sigligiydi: is akisi tek kademeli
-- onaydan ibaretti ve gercek bir kurumsal talep sisteminde olmayan bir sadelikteydi.
-- Gercek kurumlarda onay yetkisi tutara bagli: birim amiri belli bir limite kadar
-- onaylayabilir, ustunu yonetici onaylar.
--
-- Yeni durum: YONETICI_ONAYINDA. Durum makinesi artik su hale geliyor:
--   TASLAK -> BEKLEMEDE -> ONAYLANDI                       (limit alti)
--   TASLAK -> BEKLEMEDE -> YONETICI_ONAYINDA -> ONAYLANDI  (limit ustu)
--   her iki kademeden de REDDEDILDI'ye gecis mumkun.

ALTER TABLE talep ADD COLUMN tutar NUMERIC(12, 2);

COMMENT ON COLUMN talep.tutar IS
    'Talebin parasal tutari (TL). Bos birakilabilir; dolu ise onay kademesini belirler.';

-- Negatif tutar is acisindan anlamsiz. Uygulama tarafinda Bean Validation da var
-- ama tek dogrulama noktasi olarak ona guvenmek, veriye baska bir yoldan giren
-- kaydin kontrolsuz kalmasi demek.
ALTER TABLE talep ADD CONSTRAINT ck_talep_tutar CHECK (tutar IS NULL OR tutar >= 0);

-- Durum kisiti yeni degeri kapsayacak sekilde yeniden tanimlaniyor.
ALTER TABLE talep DROP CONSTRAINT ck_talep_durum;
ALTER TABLE talep ADD CONSTRAINT ck_talep_durum
    CHECK (durum IN ('TASLAK', 'BEKLEMEDE', 'YONETICI_ONAYINDA', 'ONAYLANDI', 'REDDEDILDI'));

ALTER TABLE onay_kaydi DROP CONSTRAINT ck_onay_kaydi_onceki;
ALTER TABLE onay_kaydi ADD CONSTRAINT ck_onay_kaydi_onceki
    CHECK (onceki_durum IS NULL OR onceki_durum IN
        ('TASLAK', 'BEKLEMEDE', 'YONETICI_ONAYINDA', 'ONAYLANDI', 'REDDEDILDI'));

ALTER TABLE onay_kaydi DROP CONSTRAINT ck_onay_kaydi_yeni;
ALTER TABLE onay_kaydi ADD CONSTRAINT ck_onay_kaydi_yeni
    CHECK (yeni_durum IN ('TASLAK', 'BEKLEMEDE', 'YONETICI_ONAYINDA', 'ONAYLANDI', 'REDDEDILDI'));
