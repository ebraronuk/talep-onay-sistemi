-- V5: Denetim izini veritabani seviyesinde kilitle.
--
-- Bu migration bir denetim bulgusunun karsiligi. Dokumanda "onay kaydi silinemez
-- ve degistirilemez" yaziyordu ama bu yalnizca Java tarafinda dogruydu: OnayKaydi
-- sinifinda setter yok. Veritabanina baglanan herhangi biri (baska bir uygulama,
-- elle acilan bir psql oturumu, ileride yazilacak bir toplu is) satiri guncelleyip
-- silebiliyordu. Denetim izinin tum degeri degistirilemez olmasindan geliyor;
-- iddia ile gercek arasindaki bu fark kapatildi.
--
-- Trigger secildi, kolon seviyesinde GRANT degil: GRANT rol bazli calisir ve
-- uygulama rolunun degismesiyle sessizce devre disi kalabilir. Trigger, hangi
-- rolle baglanildigindan bagimsiz olarak calisir.

CREATE OR REPLACE FUNCTION onay_kaydi_degistirilemez()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION
        'onay_kaydi degistirilemez bir denetim tablosudur (islem: %, kayit: %)',
        TG_OP, COALESCE(OLD.id, NEW.id)
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION onay_kaydi_degistirilemez() IS
    'Denetim izini korur: onay_kaydi uzerinde UPDATE ve DELETE reddedilir';

CREATE TRIGGER trg_onay_kaydi_guncellenemez
    BEFORE UPDATE ON onay_kaydi
    FOR EACH ROW
    EXECUTE FUNCTION onay_kaydi_degistirilemez();

CREATE TRIGGER trg_onay_kaydi_silinemez
    BEFORE DELETE ON onay_kaydi
    FOR EACH ROW
    EXECUTE FUNCTION onay_kaydi_degistirilemez();
