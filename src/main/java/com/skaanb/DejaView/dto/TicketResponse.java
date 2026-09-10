package com.skaanb.DejaView.dto;

import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.model.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Bir kaydın istemciye dönen hâli.
 *
 * Nasıl çalışır: {@link TicketDocument}'tan {@link #fromTicket(TicketDocument)}
 * ile üretilir. Doküman doğrudan döndürülmez çünkü içindeki {@code version} ve
 * {@code titleNormalized} gibi alanlar yalnızca depolama detayıdır; istemcinin
 * bunları görmesi hem gereksiz hem de ileride şema değişince kırılgan olurdu.
 *
 * Lombok'un {@code @Data} anotasyonu erişimcileri, {@code equals}/{@code hashCode}
 * ve {@code toString}'i derleme sırasında üretir; bu yüzden sınıfta elle yazılmış
 * erişimci yoktur.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {

    /** Elasticsearch doküman kimliği; ilişkisel kimlik olmadığı için {@code String}. */
    private String id;

    /** Kaydın görünen başlığı. */
    private String title;

    /** Hatanın metni. */
    private String errorMessage;

    /** Hatanın yığın izi. */
    private String stackTrace;

    /** Hatanın geldiği servis adı. */
    private String serviceName;

    /** AI'ın ürettiği ayrıntılı açıklama: "ne oldu". */
    private String aiGeneratedDescription;

    /** AI'ın öne sürdüğü kök neden: "neden oldu". */
    private String aiRootCause;

    /** Etiketler; kullanıcının verdikleri ve AI'ın ürettikleri birleşik hâlde. */
    private List<String> aiTags;

    /** Çözüm önerileri; her yeni analizde birikir. */
    private List<String> solutions;

    /** Kaydın ilk oluşturulma zamanı; model sınıfıyla aynı tip ({@link Instant}). */
    private Instant createdAt;

    /** Aynı hatanın en son görülme zamanı. */
    private Instant lastOccurrenceAt;

    /** Kaydı ilk açan kullanıcı adı. */
    private String createdBy;

    /** Bu başlıkla kaç kez kayıt açılmaya çalışıldığı. */
    private int occurrenceCount;

    /** Kaydın AI analiz sürecindeki durumu. */
    private TicketStatus status;

    /**
     * Elasticsearch dokümanından yanıt DTO'su üretir.
     *
     * Nasıl çalışır: alanlar tek tek kopyalanır; kopyalanmayan depolama
     * detayları ({@code version}, {@code titleNormalized}) dışarı çıkmaz.
     *
     * @param ticketDocument kaynak doküman; {@code null} verilebilir
     * @return dolu DTO; kaynak {@code null} ise {@code null}
     */
    public static TicketResponse fromTicket(TicketDocument ticketDocument) {
        if (ticketDocument == null) {
            return null;
        }
        TicketResponse response = new TicketResponse();
        response.setId(ticketDocument.getId());
        response.setTitle(ticketDocument.getTitle());
        response.setErrorMessage(ticketDocument.getErrorMessage());
        response.setStackTrace(ticketDocument.getStackTrace());
        response.setServiceName(ticketDocument.getServiceName());
        response.setAiGeneratedDescription(ticketDocument.getAiGeneratedDescription());
        response.setAiRootCause(ticketDocument.getAiRootCause());
        response.setAiTags(ticketDocument.getAiTags());
        response.setSolutions(ticketDocument.getSolutions());
        response.setCreatedAt(ticketDocument.getCreatedAt());
        response.setLastOccurrenceAt(ticketDocument.getLastOccurrenceAt());
        response.setCreatedBy(ticketDocument.getCreatedBy());
        response.setOccurrenceCount(ticketDocument.getOccurrenceCount());
        response.setStatus(ticketDocument.getStatus());
        return response;
    }
}
