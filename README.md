# DejaView

Hata/ticket takip sistemi. Backend'de yakalanan hatalar veya kullanıcıların elle
açtığı kayıtlar Elasticsearch'e indeksleniyor, bu makinede çalışan bir yapay zekâ
modeliyle otomatik özetleniyor,
aynı hata tekrar oluştuğunda ayrı bir kayıt açmak yerine mevcut kayda birleştiriliyor
("bu hatayı daha önce gördük mü?" — isim buradan geliyor).

## Mimari

| Bileşen | Ne için | Nerede |
|---|---|---|
| **H2** (in-memory) | Kullanıcılar (`User`), JPA | Uygulamayla aynı süreçte, **restart'ta veri kaybolur** |
| **Elasticsearch** | Ticket'lar (`TicketDocument`), arama | Ayrı sunucu (home-pi), kalıcı |
| **RabbitMQ** | Ticket oluşturulduğunda AI analizini asenkron kuyruğa almak | Ayrı sunucu (home-pi) |
| **Yerel AI modeli** | Hata açıklamasını özetleyip çözüm önerisi ve etiket üretmek | LM Studio, `localhost:1234` (OpenAI uyumlu uç) |

Ticket oluşturma akışı: istek gelir → kayıt `PENDING` durumuyla anında döner →
RabbitMQ üzerinden arka planda yerel modele gönderilir → sonuç `solutions` listesine
eklenir (üzerine yazılmaz, birikir) → durum `COMPLETED`/`FAILED` olur.

Aynı başlıkla (büyük/küçük harf duyarsız) tekrar gelen kayıtlar yeni bir doküman
açmaz; başlıktan türetilen deterministik bir ID (SHA-256) sayesinde aynı kayda
yönlenir, `occurrenceCount` artar, tag'ler birleştirilir. Concurrent güncellemeler
Elasticsearch'in optimistic locking'i (`@Version`) ve retry ile güvenli şekilde
yönetiliyor (bkz. `TicketMutationExecutor`).

## Gereksinimler

- **JDK 17** — proje `java.version=17` hedefliyor. Sistemde başka bir JDK
  varsayılansa (`java -version` ile kontrol edin) derleme/annotation processing
  (Lombok) sorun çıkarabilir; `JAVA_HOME`'u JDK 17'ye işaret edecek şekilde
  ayarlayıp çalıştırın.
- **Maven**
- Çalışan bir **Elasticsearch** ve **RabbitMQ** — aşağıya bakın.

## Elasticsearch / RabbitMQ bağlantısı

Varsayılan olarak `wdmch.server` host adına bağlanıyor. Bu isim projeye özel
bir DNS değil — makinenizin `/etc/hosts` dosyasında tanımlanması gerekiyor:

```
100.93.71.107   wdmch.server
```

(Tailscale IP'si — hem evde LAN üzerinden hem dışarıda Tailscale açıkken çalışır.
Farklı bir Elasticsearch/RabbitMQ kullanıyorsanız aşağıdaki ortam değişkenleriyle
override edin, `/etc/hosts`'a dokunmanıza gerek kalmaz.)

RabbitMQ yerelde yoksa:
```bash
docker run -d --name dejaview-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

## Ortam değişkenleri

| Değişken | Varsayılan | Ne için |
|---|---|---|
| `JWT_SECRET` | *(boş)* | **Zorunlu (dev dışında)** — token imzalama anahtarı. Set edilmezse uygulama açılmaz. En az 32 byte: `openssl rand -base64 48` |
| `ADMIN_PASSWORD` | *(boş)* | Başlangıç admin hesabının şifresi. Boşsa hesap **hiç oluşturulmaz**. `ADMIN_USERNAME` / `ADMIN_EMAIL` ile hesap adı değiştirilebilir. |
| `GEMINI_API_KEY` | *(boş)* | **Zorunlu** — set edilmezse AI analizi her zaman başarısız olur (ticket'lar `FAILED`'da kalır). [Google AI Studio](https://aistudio.google.com/apikey)'dan alınır. |
| `ELASTICSEARCH_HOST` | `wdmch.server` | Elasticsearch'in çalıştığı host |
| `RABBITMQ_HOST` | `wdmch.server` | RabbitMQ'nun çalıştığı host |
| `RABBITMQ_PORT` | `5672` | |
| `RABBITMQ_USERNAME` | `guest` | |
| `RABBITMQ_PASSWORD` | `guest` | |

## Çalıştırma

**Profil artık `application.properties` içinde sabit değil.** Profil verilmezse
korumalı (`prod`/`default`) güvenlik zinciri devreye girer — yani bir deploy'da
profili vermeyi unutmak uygulamayı açık bırakmaz, tam tersine `JWT_SECRET`
eksikse hiç açılmamasına yol açar.

### Yerel geliştirme

```bash
export GEMINI_API_KEY=xxxxx
JAVA_HOME=/path/to/jdk-17 mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

`dev` profilinde (`application-dev.properties`) tüm endpoint'ler kimlik doğrulama
olmadan erişilebilir, H2 konsolu `/h2-console`'da açık, Swagger UI
`/swagger-ui.html`'de. Sabit bir dev anahtarı ve `admin` / `admin` hesabı bu
profille birlikte gelir — **yalnızca yerel geliştirme içindir.**

### Üretim

```bash
export JWT_SECRET=$(openssl rand -base64 48)
export ADMIN_PASSWORD=...            # yalnızca ilk kurulumda gerekli
export GEMINI_API_KEY=xxxxx
JAVA_HOME=/path/to/jdk-17 mvn spring-boot:run
```

`JWT_SECRET` tanımlı değilse uygulama açılmaz ve nedenini söyleyerek durur.
`ADMIN_PASSWORD` boşsa başlangıç admin hesabı oluşturulmaz (uyarı loglanır),
böylece kurulumu unutulmuş bir sunucuda tahmin edilebilir bir admin kalmaz.
H2 konsolu bu profilde kapalıdır.

## Roller

- **USER**: kendi ticket'larını oluşturabilir/silebilir, profilini günceller.
- **ADMIN**: `POST /api/auth/register` ile yeni kullanıcı ekleyebilir (prod
  profilinde register herkese açık değil, sadece ADMIN), herhangi bir ticket'ı
  sahiplik kontrolüne takılmadan silebilir, `POST /api/tickets/{id}/resummarize`
  ile AI analizini yeniden tetikleyebilir.

## API özeti

| Metod & Yol | Açıklama |
|---|---|
| `POST /api/auth/register` | Yeni kullanıcı (prod'da ADMIN gerekir) |
| `POST /api/auth/login` | `{email, password}` → JWT |
| `GET /api/users/me` | Kendi profilim |
| `PUT /api/users/me` | Profil güncelle (`email`, `phoneNumber`) |
| `GET /api/tickets` | Tüm ticket'lar |
| `GET /api/tickets/{id}` | Tekil ticket |
| `POST /api/tickets` | Yeni ticket: `{title, description, tags, serviceName}` |
| `DELETE /api/tickets/{id}` | Ticket sil (sahibi veya ADMIN) |
| `POST /api/tickets/{id}/resummarize` | AI analizini yeniden tetikle (ADMIN) |
| `GET /api/tickets/search?q=...` | Elasticsearch tabanlı arama |

## Test

```bash
JAVA_HOME=/path/to/jdk-17 mvn test
```

`GeminiServiceLiveTest` hariç tüm testler mock'larla çalışır, dış servis
gerektirmez. `GeminiServiceLiveTest`, `GEMINI_API_KEY` set değilse otomatik
atlanır; gerçek API'ye karşı elle doğrulamak için:

```bash
GEMINI_API_KEY=xxxxx mvn test -Dtest=GeminiServiceLiveTest
```

## Bilinmesi gerekenler

- **H2 in-memory**: backend her yeniden başladığında kullanıcı verisi silinir
  (seed edilen `admin` hariç). Ticket'lar (Elasticsearch'te) kalıcıdır.
- **RabbitMQ dinleyicisi 3-10 paralel thread**'le çalışır
  (`spring.rabbitmq.listener.simple.concurrency`).
- Elasticsearch veya RabbitMQ'ya erişilemezse uygulama çökmez; ticket'lar
  `PENDING`'de bekler, bağlantı kurulana kadar arka planda tekrar dener.
