package tr.ebrar.talep.service.komut;

/**
 * Amirin veya yoneticinin verdigi karar.
 *
 * <p>Istemciden dogrudan hedef durum ("ONAYLANDI") almak yerine karar aliyoruz.
 * Sebep: istemci durum makinesinin ic isleyisini bilmek zorunda kalmasin.
 * Yarin araya bir durum girerse istemci kodu degismez.
 *
 * <p>Karardan hedef duruma cevrim burada degil, {@code TalepServisi.hedefDurum}
 * icinde: ONAYLA'nin hedefi artik sabit degil, hangi kademede oldugumuza ve
 * tutar limitine bagli (bkz. iki kademeli onay).
 */
public enum Karar {
    ONAYLA,
    REDDET
}
