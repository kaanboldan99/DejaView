package com.skaanb.DejaView.model;

public enum TicketStatus {
    PENDING,     // AI analizi kuyruğa alındı, henüz işlenmedi
    PROCESSING,  // AI analizi şu an işleniyor
    COMPLETED,   // AI analizi tamamlandı, sonuç kayda eklendi
    FAILED       // AI analizi başarısız oldu
}
