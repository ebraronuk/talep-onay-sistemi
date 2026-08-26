package tr.ebrar.talep.repository;

import tr.ebrar.talep.domain.TalepDurumu;

/**
 * Yonetici raporundaki tek satir: durum basina talep adedi.
 * JPQL yapici ifadesi (constructor expression) ile dogrudan doldurulur.
 */
public record DurumOzeti(TalepDurumu durum, long adet) {
}
