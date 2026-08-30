# DejaView

DejaView, uygulama hatalarını ve destek kayıtlarını tek bir yerde toplayan,
yapay zeka ile otomatik özetleyen bir hata takip sistemi. Aynı hata tekrar
oluştuğunda yeni bir kayıt açmak yerine mevcut kayda ekleniyor — "bu hatayı
daha önce gördük mü?" sorusuna cevap vermesi amaçlanıyor, ismi de buradan geliyor.

Spring Boot ile yazılmış bir REST API'dir. Kullanıcılar kimlik doğrulamalı
(JWT) olarak hata kaydı oluşturabilir, arayabilir ve yönetebilir; adminler
ayrıca kullanıcı ekleyebilir ve kayıtları AI'a yeniden özetletebilir.

## Nasıl çalışır

1. Bir kayıt oluşturulduğunda (elle veya arka planda yakalanan bir exception'dan)
   Elasticsearch'e kaydedilir.
2. Aynı başlıkla daha önce açılmış bir kayıt varsa, yeni bir kayıt açılmaz —
   mevcut kayda "tekrar görüldü" olarak işlenir (sayaç artar, etiketler birleşir).
3. Özetleme işi RabbitMQ üzerinden arka planda yapılır: kayıt anında oluşturulur,
   Gemini'den gelen özet birkaç saniye içinde kayda eklenir.
4. Kayıtlar `GET /api/tickets/search` ile aranabilir.

## Gereksinimler

- JDK 17
- Maven
- Docker (Elasticsearch, RabbitMQ; canlı ortamda PostgreSQL için)
- Bir Gemini API anahtarı ([aistudio.google.com/apikey](https://aistudio.google.com/apikey))

## Kurulum

**1. Elasticsearch ve RabbitMQ'yu ayağa kaldır:**

```bash
docker compose up -d
```

Bu, `docker-compose.yml` üzerinden Elasticsearch'ü `localhost:9200`'de ve
RabbitMQ'yu `localhost:5672`'de (yönetim paneli `localhost:15672`, guest/guest)
başlatır. Servislerin ayakta olduğunu kontrol etmek için:

```bash
docker compose ps
```

**2. Gemini API anahtarını set et:**

```bash
export GEMINI_API_KEY=xxxxx
```

Bu adım atlanırsa uygulama açılır ama AI özetleme her zaman başarısız olur
(kayıtlar `FAILED` durumunda kalır).

**3. Uygulamayı başlat:**

```bash
mvn spring-boot:run
```

Varsayılan profil (`dev`): kimlik doğrulama olmadan tüm endpoint'lere erişilebilir,
H2 konsolu `/h2-console`'da, Swagger UI `/swagger-ui.html`'de açık.

İlk açılışta otomatik bir admin kullanıcısı oluşturulur:

- **E-posta:** `admin@dejaview.com`
- **Şifre:** `admin`

## Elasticsearch/RabbitMQ farklı bir yerde çalışıyorsa

`docker-compose up`'ı kullanmıyorsanız (ör. uzak bir sunucudaki servislere
bağlanıyorsanız) şu ortam değişkenlerini set edin:

| Değişken | Varsayılan |
|---|---|
| `ELASTICSEARCH_HOST` | `localhost` |
| `RABBITMQ_HOST` | `localhost` |
| `RABBITMQ_PORT` | `5672` |
| `RABBITMQ_USERNAME` | `guest` |
| `RABBITMQ_PASSWORD` | `guest` |

## Canlı ortam (prod profili)

Varsayılan `dev` profili kullanıcı verisini H2'de bellek içi tutar — sadece
geliştirme/test için, restart'ta silinir. Gerçek kullanımda `prod` profiliyle
PostgreSQL'e geçin:

```bash
export SPRING_PROFILES_ACTIVE=prod
export DB_HOST=localhost   # docker-compose'daki postgres servisini kullanıyorsanız
export DB_PASSWORD=dejaview
mvn spring-boot:run
```

`docker-compose.yml` PostgreSQL'i de içeriyor (`docker compose up -d` zaten
başlatır). Farklı bir veritabanı kullanıyorsanız `DB_HOST`, `DB_PORT`, `DB_NAME`,
`DB_USERNAME`, `DB_PASSWORD` ile yönlendirin.

## Roller

- **USER** — kendi kayıtlarını oluşturur/siler, profilini günceller.
- **ADMIN** — `POST /api/auth/register` ile yeni kullanıcı ekleyebilir (prod
  profilinde register herkese açık değildir, sadece ADMIN), herhangi bir kaydı
  sahiplik kontrolüne takılmadan silebilir, `POST /api/tickets/{id}/resummarize`
  ile AI analizini yeniden tetikleyebilir.

## API özeti

| Metod & Yol | Açıklama |
|---|---|
| `POST /api/auth/register` | Yeni kullanıcı (prod'da ADMIN gerekir) |
| `POST /api/auth/login` | `{email, password}` → JWT |
| `GET /api/users/me` | Kendi profilim |
| `PUT /api/users/me` | Profil güncelle (`email`, `phoneNumber`) |
| `GET /api/tickets` | Tüm kayıtlar |
| `GET /api/tickets/{id}` | Tekil kayıt |
| `POST /api/tickets` | Yeni kayıt: `{title, description, tags, serviceName}` |
| `DELETE /api/tickets/{id}` | Kayıt sil (sahibi veya ADMIN) |
| `POST /api/tickets/{id}/resummarize` | AI analizini yeniden tetikle (ADMIN) |
| `GET /api/tickets/search?q=...` | Arama |

## Test

```bash
mvn test
```

Testlerin tamamı mock'larla çalışır, Elasticsearch/RabbitMQ/Gemini'ye ihtiyaç
duymaz — `docker compose up` yapmadan da çalıştırılabilir. Tek istisna:
`GeminiServiceLiveTest`, `GEMINI_API_KEY` set değilse otomatik atlanır; gerçek
API'ye karşı elle doğrulamak isterseniz:

```bash
GEMINI_API_KEY=xxxxx mvn test -Dtest=GeminiServiceLiveTest
```

## Bilinmesi gerekenler

- `dev` profilinde kullanıcı verisi H2'de bellek içi tutulur — uygulama her
  yeniden başladığında silinir (seed edilen `admin` hariç). `prod` profilinde
  PostgreSQL kalıcıdır. Kayıtlar (Elasticsearch'te) her iki profilde de kalıcıdır.
- Elasticsearch veya RabbitMQ'ya erişilemezse uygulama çökmez; kayıtlar
  `PENDING`'de bekler, bağlantı kurulana kadar arka planda tekrar dener.
