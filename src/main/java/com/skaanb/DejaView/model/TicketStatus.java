package com.skaanb.DejaView.model;

/**
 * Bir kaydın AI analiz sürecindeki durumu.
 *
 * Nasıl çalışır: durum {@link TicketDocument#status} alanında tutulur ve
 * yalnızca analiz akışı tarafından değiştirilir — kayıt oluşturulurken
 * {@code PENDING} yazılır, kuyruktan alındığında {@code PROCESSING},
 * sonuç yazıldığında {@code COMPLETED} olur
 * (bkz. {@link com.skaanb.DejaView.service.TicketAnalysisListener}).
 *
 * {@code FAILED} ile {@code PENDING} arasındaki ayrım bilinçli: yeniden
 * denenecek bir analiz {@code PENDING} kalır, yalnızca tüm deneme hakkı
 * tükendiğinde {@code FAILED} yazılır. Aksi halde arayüzde "bitti, olmadı"
 * görünen bir kayıt aslında arka planda hâlâ kuyrukta bekliyor olurdu.
 */
public enum TicketStatus {

    /** AI analizi kuyruğa alındı, henüz işlenmedi (yeniden deneme bekleyenler dâhil). */
    PENDING,

    /** AI analizi şu an işleniyor. */
    PROCESSING,

    /** AI analizi tamamlandı, sonuç kayda eklendi. */
    COMPLETED,

    /** AI analizi tüm deneme hakları tükendiği hâlde başarısız oldu. */
    FAILED
}
