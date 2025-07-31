package com.skaanb.DejaView.config;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.convert.ElasticsearchConverter;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;

@Configuration
public class ElasticsearchConfig {

    @Value("${spring.elasticsearch.rest.uris}")
    private String esUris;

    @Value("${spring.elasticsearch.rest.username}")
    private String esUsername;

    @Value("${spring.elasticsearch.rest.password}")
    private String esPassword;

    // Tek HLL client bean’imizi tanımlıyoruz
    @Bean
    public RestHighLevelClient elasticsearchClient() {
        var creds = new BasicCredentialsProvider();
        creds.setCredentials(AuthScope.ANY,
                new UsernamePasswordCredentials(esUsername, esPassword));

        HttpHost host = HttpHost.create(esUris);
        RestClientBuilder builder = RestClient.builder(host)
                .setHttpClientConfigCallback(http ->
                        http.setDefaultCredentialsProvider(creds)
                );
        return new RestHighLevelClient(builder);
    }

    // Burada hem client’i hem converter’ı alıyoruz
    @Bean
    public ElasticsearchRestTemplate elasticsearchRestTemplate(
            RestHighLevelClient client,
            ElasticsearchConverter converter
    ) {
        return new ElasticsearchRestTemplate(client, converter);
    }

    @Bean
    public ElasticsearchConverter elasticsearchConverter() {
        return new MappingElasticsearchConverter(
                new SimpleElasticsearchMappingContext()
        );
    }
}