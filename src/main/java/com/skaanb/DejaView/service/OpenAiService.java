package com.skaanb.DejaView.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skaanb.DejaView.dto.AIAnalysisResponse;
import com.skaanb.DejaView.exception.AiSummarizationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;

/**
 * OpenAI'ın {@code /v1/chat/completions} protokolünü konuşan AI sağlayıcısı.
 *
 * Nasıl çalışır: adres ve model adı yapılandırmadan geldiği için bu sınıf AYNI
 * ZAMANDA yerel modeller için de kullanılır — LM Studio (ve Ollama, vLLM,
 * llama.cpp server) tam olarak bu protokolü sunuyor. Dolayısıyla
 * {@code openai.api.url}'i {@code http://localhost:1234/v1/chat/completions}
 * yapmak yerel bir modele bağlanmak için yeterli; ayrı bir yerel servis sınıfı
 * yazmaya gerek yok. Yerel kurulum için bkz.
 * {@code application-local-ai.properties}.
 *
 * Hangi sağlayıcının aktif olacağı {@code dejaview.ai.provider} ile seçilir;
 * bu sayede aynı anda yalnızca tek bir {@link AiSummarizationService} bean'i
 * vardır ve {@code @Primary}'ye ihtiyaç kalmaz.
 *
 * Sınıfın belirleyici tasarım kararı: modelden JSON RİCA EDİLMEZ, JSON şeması
 * DAYATILIR (bkz. {@link #analyze(String, boolean)}). Yapısal çıktının doğrudan
 * sonucu olarak ayrıştırma hataları büyük ölçüde ortadan kalkar.
 */
@Service
@ConditionalOnProperty(name = "dejaview.ai.provider", havingValue = "openai")
public class OpenAiService implements AiSummarizationService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAiService.class);

    /**
     * Normal analizde kullanılan sıcaklık.
     *
     * Nasıl çalışır: etiketleme bir sınıflandırma işi — aynı hataya her
     * seferinde aynı etiketlerin gelmesi yaratıcılıktan daha değerli, o yüzden
     * sıcaklık düşük tutulur.
     */
    private static final double DEFAULT_TEMPERATURE = 0.2;

    /**
     * "Yeniden üret" isteğinde kullanılan sıcaklık.
     *
     * Nasıl çalışır: 0.2 ile aynı girdi neredeyse aynı çıktıyı verir ve buton
     * hiçbir işe yaramazdı — yeniden üretmenin tek anlamı FARKLI bir bakış
     * açısı görmek olduğu için sıcaklık yükseltilir.
     */
    private static final double REGENERATE_TEMPERATURE = 0.8;

    /** Zaman aşımı ayarlanmış HTTP istemcisi. */
    private final RestTemplate restTemplate;

    /** Model yanıtındaki JSON'u nesnelere çeviren eşleyici. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** API anahtarı; yerel sunucularda boş bırakılır. */
    private final String apiKey;

    /** Sohbet tamamlama uç adresi; yerel ya da bulut olabilir. */
    private final String apiUrl;

    /** Kullanılacak model adı. */
    private final String model;

    /** Hedeflenen en az etiket sayısı. */
    private final int minTags;

    /** Kabul edilen en fazla etiket sayısı. */
    private final int maxTags;

    /** Hedeflenen en az çözüm sayısı. */
    private final int minSolutions;

    /** Kabul edilen en fazla çözüm sayısı. */
    private final int maxSolutions;

    /**
     * Şemadaki {@code minItems}/{@code maxItems}'ı sağlayıcının kabul edip etmediği.
     *
     * Nasıl çalışır: bulut OpenAI ve güncel LM Studio kabul ediyor, bazı yerel
     * sunucular ise bu anahtarları içeren şemayı 400 ile reddediyor.
     * Reddedilirse bir kez kısıtsız şemayla tekrar denenir ve bu durum
     * HATIRLANIR — her istekte aynı 400'ü yemek için sebep yok. O durumda sayı
     * hedefi yalnızca prompt ve {@link #topUpTags} ile sağlanır.
     *
     * {@code volatile}: birden fazla dinleyici thread'i bu alanı okuyup
     * yazabilir; güncellemenin diğer thread'lerce görülmesi gerekir.
     */
    private volatile boolean countConstraintsSupported = true;

    /**
     * Sağlayıcı ayarlarını okur, sınırları tutarlı hâle getirir ve HTTP istemcisini kurar.
     *
     * Nasıl çalışır: min/max çiftleri düzeltilerek alınır — yanlış yapılandırılmış
     * bir çift (örn. min=10, max=5) şemayı sağlanamaz hâle getirip her isteği
     * patlatırdı; sınırları burada tutarlı hâle getirmek, yapılandırma hatasını
     * sessiz bir üretim arızasına çevirmiyor.
     *
     * Zaman aşımı AÇIKÇA veriliyor: {@link RestTemplate}'in varsayılanı "sonsuz
     * bekle"dir ve yerel model kullanırken bu gerçek bir risk — makine takılırsa
     * RabbitMQ dinleyici thread'i süresiz bloke olur ve kuyruk birikir. Yerel
     * modeller bulut API'lerinden belirgin şekilde yavaş olduğu için süre
     * yapılandırmadan okunur.
     *
     * @param apiKey         {@code openai.api.key}; yerel sunucularda boş bırakılır
     * @param apiUrl         {@code openai.api.url}; sohbet tamamlama uç adresi
     * @param model          {@code openai.api.model}; model tanımlayıcısı
     * @param timeoutSeconds {@code openai.api.timeout-seconds}; okuma zaman aşımı
     * @param minTags        hedeflenen en az etiket sayısı; en az 1'e yükseltilir
     * @param maxTags        en fazla etiket sayısı; {@code minTags}'ten küçükse ona eşitlenir
     * @param minSolutions   hedeflenen en az çözüm sayısı; en az 1'e yükseltilir
     * @param maxSolutions   en fazla çözüm sayısı; {@code minSolutions}'tan küçükse ona eşitlenir
     */
    public OpenAiService(@Value("${openai.api.key:}") String apiKey,
                         @Value("${openai.api.url}") String apiUrl,
                         @Value("${openai.api.model}") String model,
                         @Value("${openai.api.timeout-seconds:60}") int timeoutSeconds,
                         @Value("${dejaview.ai.min-tags:10}") int minTags,
                         @Value("${dejaview.ai.max-tags:14}") int maxTags,
                         @Value("${dejaview.ai.min-solutions:3}") int minSolutions,
                         @Value("${dejaview.ai.max-solutions:5}") int maxSolutions) {
        this.apiKey = apiKey;
        this.apiUrl = apiUrl;
        this.model = model;
        this.minTags = Math.max(1, minTags);
        this.maxTags = Math.max(this.minTags, maxTags);
        this.minSolutions = Math.max(1, minSolutions);
        this.maxSolutions = Math.max(this.minSolutions, maxSolutions);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));
        this.restTemplate = new RestTemplate(requestFactory);

        logger.info("OpenAI uyumlu sağlayıcı hazır. url={}, model={}, timeout={}s, etiket={}-{}, çözüm={}-{}",
                apiUrl, model, timeoutSeconds, this.minTags, this.maxTags, this.minSolutions, this.maxSolutions);
    }

    /**
     * Metni sade bir sohbet çağrısıyla özetler.
     *
     * Nasıl çalışır: yapısal çıktı İSTENMEZ ({@code responseFormat} boş
     * geçilir), yani model serbest metin döner. Bu metot arayüzün zorunlu
     * kıldığı en basit yetenektir; asıl kullanılan yol
     * {@link #analyze(String, boolean)}'dir.
     *
     * @param text özetlenecek metin
     * @return modelin ürettiği özet
     * @throws AiSummarizationException çağrı başarısızsa ya da yanıt boşsa
     */
    @Override
    public String summarize(String text) {
        return chat(
                List.of(
                        Map.of("role", "system", "content", "Aşağıdaki metni kısa bir şekilde özetle."),
                        Map.of("role", "user", "content", text)
                ),
                null,
                DEFAULT_TEMPERATURE
        );
    }

    /**
     * Normal (yeniden üretim olmayan) analiz yapar.
     *
     * @param description analiz edilecek hata açıklaması
     * @return dolu analiz nesnesi
     * @throws AiSummarizationException çağrı ya da ayrıştırma başarısızsa
     */
    @Override
    public AIAnalysisResponse analyze(String description) {
        return analyze(description, false);
    }

    /**
     * Açıklama, kök neden, çözüm önerileri ve etiketleri TEK çağrıda üretir.
     *
     * Nasıl çalışır: modelden "lütfen JSON dön" diye RİCA EDİLMEZ;
     * {@code response_format} ile bir JSON şeması DAYATILIR. Fark önemli: şema
     * verildiğinde sunucu çözümleme aşamasında yalnızca şemaya uyan token'lara
     * izin verir, yani "JSON'un etrafına açıklama yazdı" ya da "etiketleri
     * metin olarak döndürdü" gibi klasik ayrıştırma hataları yapısal olarak
     * imkânsız hâle gelir. Yerel 8B sınıfı modellerde etiketlemeyi güvenilir
     * kılan asıl şey budur — model boyutundan daha belirleyici.
     *
     * Yanıt alındıktan sonra iki temizlik adımı işler: çözümler ve etiketler
     * normalize edilir; etiket sayısı hedefin altında kaldıysa eksik kalanlar
     * için ikinci bir çağrı yapılır.
     *
     * @param description analiz edilecek hata açıklaması
     * @param regenerate  {@code true} ise sıcaklık yükseltilir ve prompt farklı
     *                    bir bakış açısı ister
     * @return normalize edilmiş, dolu analiz nesnesi
     * @throws AiSummarizationException çağrı başarısızsa ya da yanıt JSON olarak
     *                                  ayrıştırılamazsa
     */
    @Override
    public AIAnalysisResponse analyze(String description, boolean regenerate) {
        String content = chatStructured(
                List.of(
                        Map.of("role", "system", "content", analysisSystemPrompt(regenerate)),
                        Map.of("role", "user", "content", description)
                ),
                "ticket_analysis",
                this::analysisSchema,
                regenerate ? REGENERATE_TEMPERATURE : DEFAULT_TEMPERATURE
        );

        AIAnalysisResponse analysis;
        try {
            analysis = objectMapper.readValue(content, AIAnalysisResponse.class);
        } catch (Exception e) {
            logger.error("AI yanıtı JSON olarak ayrıştırılamadı. model={}, yanıt={}", model, content, e);
            throw new AiSummarizationException("AI analiz yanıtı ayrıştırılamadı.", e);
        }

        analysis.setSolutions(normalizeSolutions(analysis.getSolutions()));

        List<String> tags = normalizeTags(analysis.getTags());
        /*
         * Şema minItems'ı desteklenmiyorsa ya da model sayıyı tutturamadıysa etiket
         * hedefinin altında kalınabiliyor; eksik kalanı ikinci bir çağrıyla tamamlıyoruz.
         */
        if (tags.size() < minTags) {
            tags = topUpTags(description, tags, regenerate);
        }
        analysis.setTags(tags);

        return analysis;
    }

    /**
     * Analiz çağrısının sistem yönergesini kurar.
     *
     * Nasıl çalışır: her alan için ne beklendiği ayrı ayrı ve TÜRKÇE yanıt
     * istenerek yazılır; etiket ve çözüm sayıları yapılandırmadan gelen
     * sınırlarla metne gömülür. Şema sayıyı zorlasa bile prompt'ta tekrar
     * edilmesi bilinçli: şema kısıtlarının reddedildiği sağlayıcılarda sayı
     * hedefini ayakta tutan tek şey prompt oluyor.
     *
     * Yeniden üretim isteğinde sonuna ek bir yönerge eklenir: kullanıcı ilk
     * sonucu görüp beğenmemiştir, aynı cümleleri tekrarlamak yerine başka bir
     * açıdan bakması istenir.
     *
     * @param regenerate {@code true} ise farklı bakış açısı yönergesi eklenir
     * @return modele gönderilecek sistem yönergesi
     */
    private String analysisSystemPrompt(boolean regenerate) {
        StringBuilder prompt = new StringBuilder()
                .append("Sen bir hata takip sistemi asistanısın. Verilen hata kaydını analiz et ve TÜRKÇE yanıtla.\n")
                .append("- description: hatanın ne olduğunu 2-4 cümleyle, teknik ama anlaşılır biçimde anlat.\n")
                .append("- rootCause: en olası kök nedeni bir paragrafta açıkla. Emin olamıyorsan 'bilinmiyor' deme, ")
                .append("en güçlü hipotezini gerekçesiyle yaz.\n")
                .append("- solutions: birbirinden FARKLI ").append(minSolutions).append("-").append(maxSolutions)
                .append(" çözüm önerisi ver. Hızlı geçici çözümden kalıcı düzeltmeye doğru sırala; her madde tek ")
                .append("başına okunabilir, uygulanabilir bir adım olsun ve başına numara koyma.\n")
                .append("- tags: en az ").append(minTags).append(", en fazla ").append(maxTags)
                .append(" adet kısa, tekil, küçük harfli teknik etiket üret (ör. 'database', 'timeout', 'auth'). ")
                .append("Hatayı farklı açılardan etiketle: teknoloji, katman, hata tipi, etkilenen alan. ")
                .append("Port numarası, sürüm, tarih gibi yalnızca bu kayda özel değerleri etiket yapma ve ")
                .append("aynı etiketi iki kez verme.");

        if (regenerate) {
            prompt.append("\nBu kayıt için daha önce bir analiz üretildi ve kullanıcı YENİDEN üretilmesini istedi. ")
                  .append("Aynı cümleleri tekrarlama; farklı bir bakış açısı, farklı olasılıklar ve ")
                  .append("alternatif çözüm yolları sun.");
        }
        return prompt.toString();
    }

    /**
     * Tam analiz yanıtının JSON şemasını kurar.
     *
     * Nasıl çalışır: dört alan da {@code required} listesine konur ve
     * {@code additionalProperties=false} verilir, yani model şema dışı bir alan
     * ekleyemez. Sayı kısıtları isteğe bağlıdır — sağlayıcı bunları
     * reddettiğinde aynı şema kısıtsız hâliyle yeniden kurulur.
     *
     * @param withCounts {@code true} ise dizilere {@code minItems}/{@code maxItems} eklenir
     * @return {@code response_format} içine konacak şema
     */
    private Map<String, Object> analysisSchema(boolean withCounts) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("description", Map.of("type", "string"));
        properties.put("rootCause", Map.of("type", "string"));
        properties.put("solutions", arraySchema(withCounts, minSolutions, maxSolutions));
        properties.put("tags", arraySchema(withCounts, minTags, maxTags));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("description", "rootCause", "solutions", "tags"));
        schema.put("additionalProperties", false);
        return schema;
    }

    /**
     * Metin dizisi için şema parçası üretir.
     *
     * @param withCounts {@code true} ise sayı kısıtları eklenir
     * @param min        en az eleman sayısı
     * @param max        en fazla eleman sayısı
     * @return dizi şeması
     */
    private Map<String, Object> arraySchema(boolean withCounts, int min, int max) {
        Map<String, Object> array = new LinkedHashMap<>();
        array.put("type", "array");
        array.put("items", Map.of("type", "string"));
        if (withCounts) {
            array.put("minItems", min);
            array.put("maxItems", max);
        }
        return array;
    }

    /**
     * Etiket sayısı hedefin altında kaldığında eksik kalanlar için ikinci bir çağrı yapar.
     *
     * Nasıl çalışır: zaten üretilmiş etiketler prompt'a KONUR ki model aynılarını
     * tekrar önermesin; yalnızca eksik kalan sayı kadar yeni etiket istenir.
     * Gelen etiketler mevcutlarla birleştirilip yeniden normalize edilir.
     *
     * Bu çağrının başarısızlığı analizi başarısız SAYMAZ: özet ve çözümler
     * elde, etiket sayısının hedefin altında kalması kaydı kullanışsız yapmaz.
     * Bu yüzden hata yutulur ve mevcut etiketlerle devam edilir.
     *
     * @param description analiz edilen hata açıklaması
     * @param existing    ilk çağrıda üretilmiş, normalize edilmiş etiketler
     * @param regenerate  {@code true} ise yüksek sıcaklık kullanılır
     * @return tamamlanmış etiket listesi; ek çağrı başarısızsa {@code existing}
     */
    private List<String> topUpTags(String description, List<String> existing, boolean regenerate) {
        int missing = minTags - existing.size();
        int room = maxTags - existing.size();
        logger.info("Model {} etiket üretti, hedef en az {}. Eksik {} etiket için ek çağrı yapılıyor.",
                existing.size(), minTags, missing);

        String prompt = "Sen bir hata takip sistemi asistanısın. Aşağıdaki hata kaydı için şu etiketler ZATEN "
                + "üretildi: " + String.join(", ", existing) + ". Bunlardan FARKLI, en az " + missing
                + " yeni etiket üret. Etiketler kısa, tekil, küçük harfli teknik terimler olsun; hatayı henüz "
                + "etiketlenmemiş açılardan (teknoloji, katman, hata tipi, etkilenen alan) tanımlasın. "
                + "Port numarası, sürüm, tarih gibi kayda özel değerleri etiket yapma.";

        try {
            String content = chatStructured(
                    List.of(
                            Map.of("role", "system", "content", prompt),
                            Map.of("role", "user", "content", description)
                    ),
                    "ticket_tags",
                    withCounts -> tagsOnlySchema(withCounts, missing, Math.max(missing, room)),
                    regenerate ? REGENERATE_TEMPERATURE : DEFAULT_TEMPERATURE
            );

            JsonNode tagsNode = objectMapper.readTree(content).path("tags");
            List<String> extra = new ArrayList<>();
            for (JsonNode node : tagsNode) {
                extra.add(node.asText());
            }

            List<String> merged = new ArrayList<>(existing);
            merged.addAll(extra);
            List<String> result = normalizeTags(merged);
            logger.info("Ek etiket çağrısı sonrası etiket sayısı: {}", result.size());
            return result;
        } catch (Exception e) {
            logger.warn("Ek etiket çağrısı başarısız oldu, mevcut {} etiketle devam ediliyor. hata={}",
                    existing.size(), e.getMessage());
            return existing;
        }
    }

    /**
     * Yalnızca etiket dizisi içeren şemayı kurar.
     *
     * Nasıl çalışır: ek etiket çağrısında tam analiz şeması kullanılmaz; model
     * yalnızca eksik etiketleri üretmeli, açıklama ve çözümleri baştan
     * yazmamalı.
     *
     * @param withCounts {@code true} ise sayı kısıtları eklenir
     * @param min        en az yeni etiket sayısı
     * @param max        en fazla yeni etiket sayısı
     * @return yalnızca {@code tags} alanı olan şema
     */
    private Map<String, Object> tagsOnlySchema(boolean withCounts, int min, int max) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("tags", arraySchema(withCounts, min, max)));
        schema.put("required", List.of("tags"));
        schema.put("additionalProperties", false);
        return schema;
    }

    /**
     * Yapısal çıktı isteyen çağrıları tek noktadan geçirir ve şema reddini yönetir.
     *
     * Nasıl çalışır: önce sayı kısıtlı şemayla denenir. Sağlayıcı bunu
     * reddederse (bkz. {@link #countConstraintsSupported}) bir kez kısıtsız
     * şemayla tekrar denenir ve kısıtlar kalıcı olarak kapatılır.
     *
     * Geri düşüş YALNIZCA 4xx'te yapılır: zaman aşımı ya da bağlantı hatası
     * şemayla ilgili değildir ve o durumda kısıtları kalıcı olarak kapatmak
     * yanlış olurdu. Ayrım, hatanın sarmalanmış asıl sebebine bakılarak yapılır
     * — bu yüzden {@link #chat} sebebi koruyor.
     *
     * @param messages      modele gönderilecek mesajlar
     * @param schemaName    şemanın adı; sağlayıcı yanıtı bu adla etiketler
     * @param schemaBuilder sayı kısıtlarının açık/kapalı olmasına göre şema
     *                      üreten fonksiyon
     * @param temperature   örnekleme sıcaklığı
     * @return modelin döndürdüğü ham JSON metni
     * @throws AiSummarizationException çağrı başarısızsa (şema reddi dışındaki
     *                                  hatalarda doğrudan yeniden fırlatılır)
     */
    private String chatStructured(List<Map<String, String>> messages,
                                  String schemaName,
                                  Function<Boolean, Map<String, Object>> schemaBuilder,
                                  double temperature) {
        boolean withCounts = countConstraintsSupported;
        try {
            return chat(messages, responseFormat(schemaName, schemaBuilder.apply(withCounts)), temperature);
        } catch (AiSummarizationException e) {
            if (!withCounts || !(e.getCause() instanceof HttpClientErrorException)) {
                throw e;
            }
            logger.warn("Sağlayıcı minItems/maxItems içeren şemayı reddetti; kısıtsız şemaya dönülüyor. "
                    + "Etiket/çözüm sayısı bundan sonra yalnızca prompt ile hedefleniyor.");
            countConstraintsSupported = false;
            return chat(messages, responseFormat(schemaName, schemaBuilder.apply(false)), temperature);
        }
    }

    /**
     * Şemayı, isteğin gövdesine konacak {@code response_format} yapısına sarar.
     *
     * Nasıl çalışır: {@code strict: true} verilir, yani sağlayıcı şemaya
     * uymayan çıktı üretemez.
     *
     * @param schemaName şemanın adı
     * @param schema     dayatılacak JSON şeması
     * @return istek gövdesine eklenecek {@code response_format} yapısı
     */
    private Map<String, Object> responseFormat(String schemaName, Map<String, Object> schema) {
        return Map.of(
                "type", "json_schema",
                "json_schema", Map.of(
                        "name", schemaName,
                        "strict", true,
                        "schema", schema
                )
        );
    }

    /**
     * Etiketleri karşılaştırılabilir tek bir biçime indirger.
     *
     * Nasıl çalışır: etiketler doğrudan modelden geldiği için normalize edilir —
     * Elasticsearch tarafında {@code aiTags} Keyword tipindedir (bkz.
     * {@link com.skaanb.DejaView.model.TicketDocument}), yani {@code "Database"}
     * ile {@code "database"} AYRI iki etiket olarak indekslenir ve filtreleme
     * bozulur. Küçük harfe çevirip tekrarları eleyerek bu kaynağında engellenir.
     *
     * Küçültme {@code Locale.ROOT} ile yapılır: etiketler İngilizce teknik
     * terimler ve Türkçe yerel ayarıyla küçültmek bunları bozar
     * ({@code "TIMEOUT" -> "tımeout"}, noktasız ı). Kayıtların dili Türkçe olsa
     * da etiket alfabesi değil.
     *
     * Baştaki {@code #} işaretleri kırpılır: model bazen {@code "#timeout"}
     * yazıyor, ama {@code #} etiketin parçası değil, gösterim biçimi.
     *
     * @param rawTags modelden gelen ham etiketler; {@code null} olabilir
     * @return küçük harfli, tekrarsız, en fazla {@link #maxTags} elemanlı liste
     */
    private List<String> normalizeTags(List<String> rawTags) {
        if (rawTags == null) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String tag : rawTags) {
            if (tag == null || tag.isBlank()) {
                continue;
            }
            String cleaned = tag.trim().toLowerCase(Locale.ROOT);
            while (cleaned.startsWith("#")) {
                cleaned = cleaned.substring(1).trim();
            }
            if (cleaned.isEmpty()) {
                continue;
            }
            normalized.add(cleaned);
            if (normalized.size() == maxTags) {
                break;
            }
        }
        return new ArrayList<>(normalized);
    }

    /**
     * Çözüm önerilerini temizler ve tekrarları eler.
     *
     * Nasıl çalışır: çözümler kayıtta ayrı ayrı gösterildiği için her madde
     * kendi başına anlamlı olmalı — boşlar atılır, modelin arada eklediği
     * {@code "1."} / {@code "- "} gibi liste işaretleri kırpılır (numaralandırmayı
     * arayüz zaten kendisi yapıyor) ve tekrarlar elenir.
     *
     * Tekrar kontrolünde anahtar küçük harflidir ama listede METNİN ORİJİNALİ
     * saklanır: model aynı öneriyi farklı büyük/küçük harfle iki kez yazdığında
     * listede iki madde gibi görünmesin, ama gösterilen metin de bozulmasın.
     *
     * @param rawSolutions modelden gelen ham çözümler; {@code null} olabilir
     * @return temizlenmiş, tekrarsız, en fazla {@link #maxSolutions} elemanlı liste
     */
    private List<String> normalizeSolutions(List<String> rawSolutions) {
        if (rawSolutions == null) {
            return List.of();
        }
        Map<String, String> unique = new LinkedHashMap<>();
        for (String solution : rawSolutions) {
            if (solution == null || solution.isBlank()) {
                continue;
            }
            String cleaned = solution.trim().replaceFirst("^\\s*(?:[-*•]|\\d+[.)])\\s*", "").trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            unique.putIfAbsent(cleaned.toLowerCase(Locale.ROOT), cleaned);
            if (unique.size() == maxSolutions) {
                break;
            }
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * Sağlayıcıya asıl HTTP çağrısını yapar ve yanıttaki metni çıkarır.
     *
     * Nasıl çalışır: başlıklar kurulur, istek gövdesi ({@code model},
     * {@code messages}, {@code temperature} ve varsa {@code response_format})
     * hazırlanır, çağrı yapılır ve yanıttan {@code choices[0].message.content}
     * okunur.
     *
     * Authorization başlığı yalnızca anahtar DOLUYSA eklenir: yerel sunucular
     * (LM Studio, Ollama) anahtar istemiyor ve boşken {@code "Bearer "} gibi
     * anlamsız bir değer göndermenin anlamı yok.
     *
     * Boş yanıt da başarısızlık sayılır — HTTP 200 dönmüş olması modelin bir
     * şey ürettiği anlamına gelmiyor.
     *
     * Hata sarmalanırken SEBEP KORUNUR: çağıran taraf 4xx (şema reddi) ile
     * zaman aşımını bu sayede ayırt ediyor (bkz. {@link #chatStructured}).
     *
     * @param messages       modele gönderilecek mesajlar (rol/içerik çiftleri)
     * @param responseFormat dayatılacak yapısal çıktı biçimi; serbest metin için
     *                       {@code null}
     * @param temperature    örnekleme sıcaklığı
     * @return modelin ürettiği içerik metni
     * @throws AiSummarizationException çağrı başarısızsa, yanıt beklenen biçimde
     *                                  değilse ya da içerik boşsa
     */
    @SuppressWarnings("unchecked")
    private String chat(List<Map<String, String>> messages, Map<String, Object> responseFormat, double temperature) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.setBearerAuth(apiKey);
        }

        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", temperature);
        if (responseFormat != null) {
            body.put("response_format", responseFormat);
        }

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    apiUrl, new HttpEntity<>(body, headers), Map.class);

            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.getBody().get("choices");
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String content = (String) message.get("content");

            if (content == null || content.isBlank()) {
                throw new AiSummarizationException("AI sağlayıcısı boş yanıt döndü.", null);
            }
            return content;

        } catch (AiSummarizationException e) {
            throw e;
        } catch (Exception e) {
            logger.error("AI çağrısı başarısız oldu. url={}, model={}, hata={}", apiUrl, model, e.getMessage(), e);
            throw new AiSummarizationException("AI yanıtı alınamadı.", e);
        }
    }
}
