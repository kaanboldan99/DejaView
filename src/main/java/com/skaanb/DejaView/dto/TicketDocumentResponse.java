package com.skaanb.DejaView.dto;

import com.skaanb.DejaView.model.TicketDocument;
import com.skaanb.DejaView.model.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.List;

/**
 * Bir kaydın istemciye dönen hâlinin, son görülme zamanını taşımayan sürümü.
 *
 * Nasıl çalışır: {@link TicketResponse} ile aynı işi yapar; tek farkı
 * {@code lastOccurrenceAt} alanının burada olmamasıdır. İki DTO paralel
 * durduğu için ikisi de aynı dönüştürme sözleşmesini sunar —
 * {@link #fromTicket(TicketDocument)} çağıran kod hangi DTO'yu kullandığını
 * bilmek zorunda kalmaz.
 *
 * Lombok'un {@code @Data} anotasyonu erişimcileri derleme sırasında üretir.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TicketDocumentResponse {

    /** Elasticsearch doküman kimliği. */
    private String id;

    /** Kaydın görünen başlığı. */
    private String title;

    /** Hatanın metni. */
    private String errorMessage;

    /** Hatanın yığın izi. */
    private String stackTrace;

    /** Hatanın geldiği servis adı. */
    private String serviceName;

    /** AI'ın ürettiği ayrıntılı açıklama. */
    private String aiGeneratedDescription;

    /** AI'ın öne sürdüğü kök neden. */
    private String aiRootCause;

    /** Etiketler. */
    private List<String> aiTags;

    /** Çözüm önerileri. */
    private List<String> solutions;

    /** Kaydın ilk oluşturulma zamanı. */
    private Instant createdAt;

    /** Kaydı ilk açan kullanıcı adı. */
    private String createdBy;

    /** Bu başlıkla kaç kez kayıt açılmaya çalışıldığı. */
    private int occurrenceCount;

    /** Kaydın AI analiz sürecindeki durumu. */
    private TicketStatus status;

    /**
     * Elasticsearch dokümanından yanıt DTO'su üretir.
     *
     * @param doc kaynak doküman; {@code null} verilebilir
     * @return dolu DTO; kaynak {@code null} ise {@code null}
     */
    public static TicketDocumentResponse fromDocument(TicketDocument doc) {
        if (doc == null) {
            return null;
        }
        TicketDocumentResponse response = new TicketDocumentResponse();
        response.setId(doc.getId());
        response.setTitle(doc.getTitle());
        response.setErrorMessage(doc.getErrorMessage());
        response.setStackTrace(doc.getStackTrace());
        response.setServiceName(doc.getServiceName());
        response.setAiGeneratedDescription(doc.getAiGeneratedDescription());
        response.setAiRootCause(doc.getAiRootCause());
        response.setAiTags(doc.getAiTags());
        response.setSolutions(doc.getSolutions());
        response.setCreatedAt(doc.getCreatedAt());
        response.setCreatedBy(doc.getCreatedBy());
        response.setOccurrenceCount(doc.getOccurrenceCount());
        response.setStatus(doc.getStatus());
        return response;
    }

    /**
     * {@link TicketResponse} ile aynı adı taşıyan dönüştürücü.
     *
     * Nasıl çalışır: gövdesi yok, doğrudan {@link #fromDocument(TicketDocument)}
     * çağrılır. İki DTO'nun aynı metot adını sunması, çağıran kodun hangi
     * DTO'ya dönüştürdüğünü bilmeden aynı ifadeyi yazabilmesini sağlıyor.
     *
     * @param doc kaynak doküman; {@code null} verilebilir
     * @return dolu DTO; kaynak {@code null} ise {@code null}
     */
    public static TicketDocumentResponse fromTicket(TicketDocument doc) {
        return fromDocument(doc);
    }
}
