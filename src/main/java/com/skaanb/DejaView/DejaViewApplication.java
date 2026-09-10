package com.skaanb.DejaView;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Uygulamanın giriş noktası.
 *
 * Nasıl çalışır: {@code @SpringBootApplication} üç şeyi birden açar —
 * otomatik yapılandırma (Elasticsearch, RabbitMQ, Security gibi bağımlılıklar
 * classpath'te görülünce kendiliğinden kurulur), bileşen taraması (bu sınıfın
 * paketi ve altındaki tüm {@code @Component} / {@code @Service} /
 * {@code @RestController} / {@code @Configuration} sınıfları bean olarak
 * kaydedilir) ve yapılandırma sınıfı olma özelliği.
 *
 * Bu yüzden sınıfın kök pakette ({@code com.skaanb.DejaView}) durması şart:
 * alt paketlere taşınırsa tarama kapsamı daralır ve bean'lerin bir kısmı
 * hiç oluşturulmaz.
 */
@SpringBootApplication
public class DejaViewApplication {

	/**
	 * Spring uygulama bağlamını kurar ve gömülü web sunucusunu başlatır.
	 *
	 * Nasıl çalışır: bağlam ayağa kalktıktan sonra {@code CommandLineRunner}
	 * uygulayan bean'ler çalıştırılır — bu projede başlangıç admin hesabını
	 * oluşturan {@link com.skaanb.DejaView.config.DataInitializer} bu yolla
	 * devreye girer.
	 *
	 * @param args komut satırı argümanları; Spring bunları ayar kaynağı olarak
	 *             da okur ({@code --spring.profiles.active=dev} gibi)
	 */
	public static void main(String[] args) {
		SpringApplication.run(DejaViewApplication.class, args);
	}

}
