package com.skaanb.DejaView.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ticketController {

    @GetMapping("/tickets/{id}")
    String getTickets(@PathVariable String id){
        return null;
    }

}


/*
POST /tickets — Ticket oluştur (AI özeti burada üretilebilir)
GET /tickets/{id} — Detay
GET /tickets/search?q=... — Elasticsearch ile arama
GET /tickets — Tüm ticket'ları listele
DELETE /tickets/{id} — Sil
*/