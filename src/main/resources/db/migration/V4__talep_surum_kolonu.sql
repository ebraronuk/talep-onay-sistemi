-- V4: Iyimser kilitleme.
--
-- Ayni talebe ayni anda karar veren iki amir senaryosu: bu kolon olmadan ikinci
-- yazim birincinin uzerine sessizce geciyor. Hibernate @Version ile her UPDATE'e
-- "where surum = ?" ekliyor, eskimis surumle gelen islem sifir satir gunceller
-- ve OptimisticLockingFailureException firlatir. API tarafi bunu 409'a ceviriyor.
--
-- Mevcut satirlar icin varsayilan 0; NOT NULL kisitini bozmadan geriye donuk uyumlu.

ALTER TABLE talep ADD COLUMN surum BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN talep.surum IS 'Iyimser kilitleme surumu; Hibernate @Version yonetiyor';
