package com.skaanb.DejaView.controller;

import com.skaanb.DejaView.dto.CreateTicketRequest;
import com.skaanb.DejaView.dto.TicketResponse;
import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.service.TicketAnalysisParkingService;
import com.skaanb.DejaView.service.TicketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Kayıt uçları: listeleme, oluşturma, arama, silme ve yönetimsel işlemler.
 *
 * Nasıl çalışır: {@code /api/tickets} altında toplanır. Sınıf iş kuralı
 * İÇERMEZ — her uç isteği doğrulayıp kimliği çıkarır ve işi
 * {@link TicketService} ya da {@link TicketAnalysisParkingService}'e devreder.
 *
 * Yetkilendirme burada değil {@link com.skaanb.DejaView.config.SecurityConfig}
 * içinde tanımlıdır: yeniden özetleme ve park kuyruğu uçları ADMIN'e kısıtlıdır.
 * Tek istisna silme: sahiplik kontrolü kayıt bazında yapıldığı için rol bilgisi
 * servise parametre olarak geçilir.
 */
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    /** Kayıt iş kurallarını yürüten servis. */
    private final TicketService ticketService;

    /** Park kuyruğunu görüntüleyen ve geri oynatan servis. */
    private final TicketAnalysisParkingService ticketAnalysisParkingService;

    /**
     * @param ticketService                kayıt iş kurallarını yürüten servis
     * @param ticketAnalysisParkingService park kuyruğu işlemlerini yürüten servis
     */
    @Autowired
    public TicketController(TicketService ticketService,
                            TicketAnalysisParkingService ticketAnalysisParkingService) {
        this.ticketService = ticketService;
        this.ticketAnalysisParkingService = ticketAnalysisParkingService;
    }

    /**
     * Tüm kayıtları döner.
     *
     * Nasıl çalışır: indeksin tamamı okunur; sayfalama yoktur, bu yüzden
     * indeks büyüdüğünde bu ucun yerini aramanın alması beklenir.
     *
     * @return Elasticsearch'teki tüm kayıt dokümanları
     */
    @GetMapping
    public List<TicketDocument> getAllTickets() {
        return ticketService.getAllTickets();
    }

    /**
     * Tek bir kaydı kimliğiyle döner.
     *
     * Nasıl çalışır: kimlik {@code String}'tir çünkü Elasticsearch doküman
     * kimliği ilişkisel bir sayı değil, başlıktan türetilen özettir.
     *
     * @param id kaydın doküman kimliği
     * @return 200 ve kayıt
     * @throws RuntimeException kayıt bulunamazsa (500'e çevrilir ve hata kaydı oluşturulur)
     */
    @GetMapping("/{id}")
    public ResponseEntity<TicketDocument> getTicketById(@PathVariable String id) {
        TicketDocument ticket = ticketService.getTicketById(id)
                .orElseThrow(() -> new RuntimeException("Ticket bulunamadı"));
        return ResponseEntity.ok(ticket);
    }

    /**
     * Yeni kayıt açar ya da aynı başlıklı kaydın tekrar sayacını artırır.
     *
     * Nasıl çalışır: kimlik JWT'den okunur; token gönderilmediğinde
     * (dev profilinde mümkün) {@code anonymous_user} kullanılır, böylece
     * yerel denemelerde {@code NullPointerException} oluşup 500 dönmez.
     * Karar — yeni kayıt mı, mevcut kayda görülme mi — servise aittir.
     *
     * @param request   kayıt gövdesi: başlık, açıklama, etiketler, servis adı
     * @param principal JWT'den gelen kimlik; {@code null} olabilir
     * @return 200 ve oluşturulan ya da güncellenen kaydın yanıt hâli
     */
    @PostMapping
    public ResponseEntity<TicketResponse> createTicket(@RequestBody CreateTicketRequest request, Principal principal) {
        String username = (principal != null) ? principal.getName() : "anonymous_user";

        TicketResponse created = ticketService.createTicket(request, username);
        return ResponseEntity.ok(created);
    }

    /**
     * Kaydı siler.
     *
     * Nasıl çalışır: sahiplik kontrolü servise bırakılır ama gereken iki bilgi
     * burada hazırlanır — istek sahibinin kullanıcı adı ve ADMIN olup olmadığı.
     * ADMIN rolündeki kullanıcılar sahiplik kontrolüne takılmadan her kaydı
     * silebilir; diğerleri yalnızca kendi açtıklarını silebilir.
     *
     * @param id             silinecek kaydın doküman kimliği
     * @param authentication Spring Security kimliği; yetkiler buradan okunur,
     *                       {@code null} olabilir
     * @return içerik taşımayan 204 No Content
     * @throws RuntimeException kayıt yoksa ya da silme yetkisi yoksa
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTicket(@PathVariable String id, Authentication authentication) {
        String username = (authentication != null) ? authentication.getName() : "anonymous_user";
        boolean isAdmin = authentication != null && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        ticketService.deleteTicket(id, username, isAdmin);
        return ResponseEntity.noContent().build();
    }

    /**
     * ADMIN: mevcut bir kayıt için AI analizini yeniden tetikler.
     *
     * Nasıl çalışır: istek "yeniden üret" olarak kuyruğa girer, yani gelen
     * çözümler mevcut listeye eklenmek yerine onun yerine geçer. Uç
     * {@link com.skaanb.DejaView.config.SecurityConfig} içinde ADMIN'e
     * kısıtlıdır.
     *
     * @param id yeniden analiz edilecek kaydın doküman kimliği
     * @return 200 ve kaydın kuyruğa alınmış (PENDING) hâli
     */
    @PostMapping("/{id}/resummarize")
    public ResponseEntity<TicketResponse> resummarizeTicket(@PathVariable String id) {
        return ResponseEntity.ok(ticketService.resummarizeTicket(id));
    }

    /**
     * Kayıtlarda arama yapar.
     *
     * Nasıl çalışır: eşleştirmeyi Elasticsearch yapar; sorgu boş bırakılırsa
     * tüm kayıtlar döner.
     *
     * @param query aranacak metin; hata mesajı, servis adı ve etiketlerde
     *              büyük/küçük harf duyarsız aranır
     * @return eşleşen kayıtların yanıt hâli
     */
    @GetMapping("/search")
    public List<TicketResponse> searchTickets(@RequestParam("q") String query) {
        return ticketService.searchTickets(query);
    }

    /**
     * ADMIN: park kuyruğunda bekleyen analiz isteği sayısını döner.
     *
     * Nasıl çalışır: bu sayı 0'dan büyükse, AI tarafındaki bir arıza yüzünden
     * tüm denemeleri tükenmiş ama KAYBOLMAMIŞ analizler var demektir. Uç
     * ADMIN'e kısıtlıdır; kuyruk durumu operasyonel bir bilgidir.
     *
     * @return 200 ve {@code {"parked": n}} biçiminde sayı
     */
    @GetMapping("/analysis/parked")
    public ResponseEntity<Map<String, Integer>> parkedAnalysisCount() {
        return ResponseEntity.ok(Map.of("parked", ticketAnalysisParkingService.parkedCount()));
    }

    /**
     * ADMIN: park kuyruğundaki analiz isteklerini yeniden analiz kuyruğuna koyar.
     *
     * Nasıl çalışır: arıza giderildikten sonra (örn. yerel model yeniden
     * açıldığında) çağrılır. Yanıt iki sayı taşır: geri oynatılan mesaj sayısı
     * ve gövdesi çözülemediği için atlanan mesaj sayısı — atlananlar da
     * silinmez, kuyruğa geri konur.
     *
     * @return 200 ve {@code {"replayed": n, "skipped": m}}
     */
    @PostMapping("/analysis/parked/replay")
    public ResponseEntity<Map<String, Integer>> replayParkedAnalysis() {
        TicketAnalysisParkingService.ReplayResult result = ticketAnalysisParkingService.replayAll();
        return ResponseEntity.ok(Map.of(
                "replayed", result.replayed(),
                "skipped", result.skipped()
        ));
    }
}
