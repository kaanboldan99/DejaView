package com.skaanb.DejaView.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * AI'ın tek çağrıda ürettiği yapısal analiz.
 *
 * Nasıl çalışır: alanlar, modele dayatılan JSON şemasıyla birebir eşleşir
 * (bkz. {@link com.skaanb.DejaView.service.OpenAiService}). Model "lütfen JSON
 * dön" diye rica edilerek değil, şema zorunlu kılınarak yanıt verdiği için
 * alanların gelmesi yapısal olarak garantidir.
 *
 * Eskiden yalnızca "bir cümle özet + tek çözüm" vardı; artık kayıt başına daha
 * fazla içerik üretiliyor: ayrıntılı açıklama, olası kök neden ve BİRDEN FAZLA
 * çözüm önerisi. Sebep pratik — tek cümlelik bir özet, hatayı ilk kez gören
 * birine kaydı açmadan önce karar verdirmiyordu; kök neden ve alternatif
 * çözümler ise doğrudan aksiyona çeviriyor.
 *
 * {@code ignoreUnknown}: model şemada olmayan fazladan bir alan döndürdüğünde
 * (yapısal çıktıyı tam desteklemeyen sağlayıcılarda olabiliyor) ayrıştırma
 * tümden patlamasın diye.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AIAnalysisResponse {

    /** Hatanın ne olduğunu anlatan, 2-4 cümlelik ayrıntılı açıklama. */
    private String description;

    /**
     * Hatanın en olası kök nedeni.
     *
     * Nasıl çalışır: model emin olamasa bile en güçlü hipotezini yazar;
     * "bilinmiyor" demek yerine bir yön göstermesi, kaydı inceleyen için
     * daha değerli.
     */
    private String rootCause;

    /**
     * Çözüm önerileri; bilinçli olarak ÇOĞUL.
     *
     * Nasıl çalışır: aynı hatanın genelde birden fazla makul çözümü var
     * (hızlı geçici çözüm / kalıcı düzeltme gibi) ve hangisinin uygun olduğuna
     * kaydı inceleyen karar vermeli. Liste hızlıdan kalıcıya doğru sıralanır.
     */
    private List<String> solutions = new ArrayList<>();

    /** Kısa, tekil, küçük harfli teknik etiketler. */
    private List<String> tags = new ArrayList<>();

    /**
     * @return hatanın ayrıntılı açıklaması
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description modelin ürettiği ayrıntılı açıklama
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return hatanın en olası kök nedeni; sağlayıcı üretmediyse {@code null}
     */
    public String getRootCause() {
        return rootCause;
    }

    /**
     * @param rootCause modelin öne sürdüğü kök neden
     */
    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    /**
     * @return çözüm önerileri; hiç yoksa boş liste ({@code null} dönmez)
     */
    public List<String> getSolutions() {
        return solutions;
    }

    /**
     * Çözüm listesini ayarlar.
     *
     * Nasıl çalışır: {@code null} gelen liste boş listeye çevrilir, böylece
     * çağıran taraf (bkz. {@link com.skaanb.DejaView.service.TicketAnalysisListener})
     * her seferinde {@code null} kontrolü yapmak zorunda kalmaz. Gelen liste
     * kopyalanır; dışarıdaki referans üzerinden sonradan değiştirilemez.
     *
     * @param solutions çözüm önerileri; {@code null} verilebilir
     */
    public void setSolutions(List<String> solutions) {
        this.solutions = solutions != null ? new ArrayList<>(solutions) : new ArrayList<>();
    }

    /**
     * @return etiketler; hiç yoksa boş liste ({@code null} dönmez)
     */
    public List<String> getTags() {
        return tags;
    }

    /**
     * Etiket listesini ayarlar.
     *
     * Nasıl çalışır: {@link #setSolutions} ile aynı sözleşme — {@code null}
     * boş listeye çevrilir ve gelen liste kopyalanır.
     *
     * @param tags etiketler; {@code null} verilebilir
     */
    public void setTags(List<String> tags) {
        this.tags = tags != null ? new ArrayList<>(tags) : new ArrayList<>();
    }
}
