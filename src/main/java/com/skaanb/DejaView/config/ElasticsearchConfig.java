package com.skaanb.DejaView.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;

/**
 * Elasticsearch istemci zincirinin elle kurulduğu yapılandırma.
 *
 * Nasıl çalışır: istemci dört katman hâlinde, aşağıdan yukarı kurulur —
 * düşük seviyeli {@link RestClient} (HTTP), onun üzerinde tip güvenli
 * {@link ElasticsearchClient}, en üstte Spring Data'nın kullandığı
 * {@link ElasticsearchTemplate} ve nesne/JSON eşlemesini yapan
 * {@link ElasticsearchConverter}. Her katman bir öncekini bean olarak alır.
 *
 * Otomatik yapılandırma yerine elle kurulmasının sebebi bağlantı havuzu ve
 * kimlik bilgisi ayarlarına doğrudan müdahale gereksinimi (aşağıya bkz.).
 *
 * Adres ve kimlik bilgileri {@code spring.elasticsearch.rest.*} ayarlarından
 * gelir; farklı bir makinede çalıştırırken {@code ELASTICSEARCH_HOST} ortam
 * değişkeni yeterlidir.
 */
@Configuration
public class ElasticsearchConfig {

    /** Elasticsearch adresi; {@code http://host:9200} biçiminde tek URI. */
    @Value("${spring.elasticsearch.rest.uris}")
    private String esUris;

    /** Kullanıcı adı; boş bırakılırsa kimlik doğrulama hiç kurulmaz. */
    @Value("${spring.elasticsearch.rest.username}")
    private String esUsername;

    /** Şifre; yalnızca kullanıcı adı doluysa kullanılır. */
    @Value("${spring.elasticsearch.rest.password}")
    private String esPassword;

    /**
     * Düşük seviyeli HTTP istemcisini kurar.
     *
     * Nasıl çalışır: kullanıcı adı verilmişse temel kimlik doğrulama eklenir,
     * verilmemişse kimlik bilgisi hiç tanımlanmaz — güvenliği kapalı yerel bir
     * kurulumda boş kullanıcı adıyla istek göndermemek için.
     *
     * Bağlantı havuzu açıkça büyütülüyor: kütüphanenin varsayılan havuzu küçük,
     * oysa kayıt listeleme/arama/oluşturma en sık kullanılan uçlar ve hepsi
     * buradan geçiyor. Çok sayıda eşzamanlı kullanıcıda (örn. 100+) havuz dolup
     * istekler kuyrukta beklerdi.
     *
     * @return yapılandırılmış düşük seviyeli REST istemcisi
     */
    @Bean
    public RestClient restClient() {
        var credentialsProvider = new BasicCredentialsProvider();
        if (esUsername != null && !esUsername.isBlank()) {
            credentialsProvider.setCredentials(
                    AuthScope.ANY,
                    new UsernamePasswordCredentials(esUsername, esPassword)
            );
        }

        return RestClient.builder(HttpHost.create(esUris))
                .setHttpClientConfigCallback(httpClientBuilder ->
                        httpClientBuilder
                                .setDefaultCredentialsProvider(credentialsProvider)
                                .setMaxConnTotal(100)
                                .setMaxConnPerRoute(100)
                )
                .build();
    }

    /**
     * Spring Boot 3.x uyumlu, tip güvenli Elasticsearch istemcisini kurar.
     *
     * Nasıl çalışır: düşük seviyeli istemciyi bir taşıma katmanına sarar ve
     * JSON dönüşümü için Jackson eşleyicisini kullanır.
     *
     * @param restClient düşük seviyeli HTTP istemcisi (yukarıdaki bean)
     * @return sorguların yazıldığı tip güvenli istemci
     */
    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient restClient) {
        ElasticsearchTransport transport = new RestClientTransport(
                restClient,
                new JacksonJsonpMapper()
        );
        return new ElasticsearchClient(transport);
    }

    /**
     * Spring Data'nın kullandığı şablon nesnesini kurar.
     *
     * Nasıl çalışır: {@code TicketService} içindeki elle yazılmış sorgular
     * ({@code NativeQuery}) bu şablon üzerinden çalışır; repository arayüzleri de
     * arka planda aynı şablonu kullanır. Kaldırılan eski
     * {@code ElasticsearchRestTemplate}'in yerini alır.
     *
     * @param client    tip güvenli Elasticsearch istemcisi
     * @param converter nesne/JSON eşlemesini yapan dönüştürücü
     * @return sorgu çalıştırmakta kullanılan şablon
     */
    @Bean
    public ElasticsearchTemplate elasticsearchTemplate(
            ElasticsearchClient client,
            ElasticsearchConverter converter
    ) {
        return new ElasticsearchTemplate(client, converter);
    }

    /**
     * Java nesneleri ile Elasticsearch dokümanları arasındaki dönüştürücüyü kurar.
     *
     * Nasıl çalışır: eşleme bilgisini {@code @Document} ve {@code @Field}
     * anotasyonlarından okur (bkz. {@link com.skaanb.DejaView.model.TicketDocument}).
     *
     * @return alan eşlemesini yürüten dönüştürücü
     */
    @Bean
    public ElasticsearchConverter elasticsearchConverter() {
        return new MappingElasticsearchConverter(
                new SimpleElasticsearchMappingContext()
        );
    }
}
