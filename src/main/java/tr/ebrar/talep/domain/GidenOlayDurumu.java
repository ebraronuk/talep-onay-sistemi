package tr.ebrar.talep.domain;

public enum GidenOlayDurumu {

    /** Henuz islenmedi ya da son denemesi basarisiz oldu, yeniden denenecek. */
    BEKLIYOR,
    ISLENDI,
    /** Deneme hakki bitti. Kendiliginden tekrar denenmez, birinin bakmasi gerekir. */
    BASARISIZ
}
