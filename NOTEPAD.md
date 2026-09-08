# DejaView Çalışma Notları

Bu dosya, Claude ile yapılan çalışmanın durumunu takip etmek için tutuluyor. Amaç: "ne yapıldı, ne yapılacak, şu an aklımda ne var" sorusuna her seferinde baştan anlatmadan cevap verebilmek.

Son güncelleme: 2026-09-08

---

## 1. ÇÖZÜLDÜ: main vs feature branch karışıklığı

~~Daha önce burada "main, feature/user-profile-and-service-name'den 7 commit geride, merge edilmemiş" diye yazılmıştı — bu YANLIŞTI.~~ Gerçek durum: `feature/user-profile-and-service-name` GitHub'da **PR #2 ile zaten main'e merge edilmişti** (`bc7a8a1`), sadece benim local `main`'im hiç `git pull` yapılmadığı için geride kalmış ve yanlış teşhis etmişime yol açmıştı.

Yapılan: local `main`'de o sırada duran, GitHub'daki yeni haliyle çakışan tek bir yeni commit vardı (bağlantı havuzu ayarları, bkz. madde 3) — `git pull --rebase origin main` ile onun üstüne taşındı, derlendi, push edildi. Şu an local ve `origin/main` birebir aynı, HEAD: `8eb0618`.

Diskte `AuthControllerTest.java`, `GeminiServiceLiveTest.java` ve `README.md` var.

**DÜZELTME (2026-09-08):** Yukarıdaki cümlede `docker-compose.yml` ve `application-prod.properties`
da sayılıyordu — bu YANLIŞ. `git branch --contains` ile doğrulandı: o iki dosyayı ekleyen commit'ler
(`6273f31`, `9859a75`) yalnızca `origin/feature/user-profile-and-service-name` üzerinde; PR #2
branch'in tamamını değil, o commit'lerden öncesini merge etmiş. Yani **PostgreSQL prod profili ve
compose dosyası yazıldı ama main'e hiç girmedi.** Geri getirmek için o iki commit cherry-pick
edilebilir (branch'teki `.DS_Store` dosyalarını almadan).

(`application-dev.properties` 2026-09-08'de ayrıca ve sıfırdan yazıldı, o branch'tekiyle ilgisi yok.)

**Ders çıkar:** Bir sonraki oturumda branch/commit durumu hakkında konuşmadan önce önce `git fetch && git log main..origin/main` ile gerçekten kontrol et, local geçmişe güvenme.

---

## 2. GÜVENLİK DENETİMİ — TAMAMLANDI, KRİTİK BEŞLİ KAPATILDI (2026-09-08)

Git geçmişinde sızmış sırlar arandı (pickaxe + blob tarama ile). En kritik bulgu:

- **JWT imzalama anahtarı hardcoded**: `security/JwtUtil.java:20`
  `Keys.hmacShaKeyFor("mySuperSecretKeyForJwtGeneration123456789012345".getBytes())`
  Commit `41c2f82`'de girmiş, **public GitHub repo'da yayında**.
  → **DÜZELTİLDİ (2026-09-08):** anahtar `jwt.secret` / `JWT_SECRET` üzerinden geliyor, eksik veya
  32 byte'tan kısaysa uygulama açılmıyor. `JwtUtilTest` eski sızmış anahtarın artık hiçbir token'ı
  doğrulayamadığını kanıtlıyor. **Sunucuda yapılacak:** `JWT_SECRET`'i `openssl rand -base64 48`
  ile üretip set et — eski token'lar geçersiz olacak, kullanıcılar yeniden login olmalı.

- Geçmişi temizlemek istenirse `git-filter-repo` komutları verildi ama **henüz çalıştırılmadı** (repo'yu yeniden yazacağı için onay bekliyor).

---

## 3. TAMAMLANDI: Yüksek eşzamanlı kullanıcı yükü (100+ kullanıcı senaryosu)

Kullanıcı sordu: "100 kişi bağlanırsa ne olur, threading/semafor güçlendirdin mi?" Cevap: HTTP katmanı için hayır, o zamana kadar yapılan threading işi (RabbitMQ listener concurrency, `TicketMutationExecutor` retry) farklı bir sorunu çözüyordu — arka planda AI analizi ve aynı kayda eşzamanlı yazma, "100 kullanıcı REST'e istek atıyor" senaryosuyla ilgili değildi.

Kod tarandı (`synchronized`/`Semaphore`/`Lock`/`ExecutorService` — hiçbiri yok, suni bir darboğaz yaratılmamış ama bilinçli koruma da yoktu). Gerçek darboğazlar bulundu ve düzeltildi:

- **HikariCP (DB bağlantı havuzu)**: hiç ayarlanmamıştı, varsayılan 10 bağlantı. `application.properties`'e eklendi: `maximum-pool-size=30`, `minimum-idle=10`, `connection-timeout=20000`.
- **Elasticsearch RestClient bağlantı havuzu**: `ElasticsearchConfig.java`'da hiç override edilmemişti (kütüphane varsayılanıyla çalışıyordu). Ticket listeleme/arama/oluşturma en sık kullanılan uçlar olduğu için `setMaxConnTotal(100)` / `setMaxConnPerRoute(100)` eklendi.
- Tomcat thread pool'a dokunulmadı — Spring Boot varsayılanı (max 200 thread) 100 eşzamanlı kullanıcı için zaten yeterli.

Derlendi, commit'lendi, main'e push edildi (commit `80cfc86`).

**Not:** Bu arada `main` local'de geriden geliyordu (bkz. madde 1), o yüzden push `.DS_Store` takip sorunuyla birlikte rebase gerektirdi — `.DS_Store` dosyaları artık `.gitignore`'da ve takipten çıkarıldı (commit `8eb0618`).

---

## 4. TAMAMLANDI: Sadece arayüzde yapılan yetki kontrolleri (bu proje frontend'siz, backend trust-boundary denetimi olarak yorumlandı)

İstenen: UI'da gizlenip backend'de aynı kontrolün olmadığı yerleri bul, sonra tek bir ortak yardımcı fonksiyonda topla.

Bu repo'da frontend olmadığı için görev şuna dönüştü: **backend endpoint'lerinin client'tan gelen kimlik/rol bilgisine güvendiği, sunucu tarafında doğrulamadığı yerler.**

### Bulunan somut açık (2026-09-08'de DÜZELTİLDİ — aşağıdaki DURUM bölümüne bakın):

**`AuthController.register` — Mass Assignment / Privilege Escalation**
- Dosya: `controller/AuthController.java:42` — `register(@RequestBody User user)` ham JPA entity'sini client JSON'undan direkt bind ediyor.
- `model/User.java` — `id` (public setter) ve `role` (default `USER` ama settable) alanları client tarafından serbestçe set edilebiliyor.
- `service/UserService.java:29` `createUser()` — `id` ve `role` alanlarını temizlemeden direkt `userRepository.save(user)` çağırıyor.
- **Etki:**
  1. `dev` profilinde (`SecurityConfig.java` — varsayılan aktif profil) `/api/auth/register` tamamen `permitAll`, JWT filtresi de yok. İstek gövdesine `"role":"ADMIN"` eklemek yeterli → kimlik doğrulama olmadan admin hesabı açılabilir.
  2. `prod` profilinde register `hasRole("ADMIN")` ile korunuyor (bunu sadece zaten admin olan biri çağırabilir) ama **admin bile olsa** register isteğine `"role":"ADMIN"` koyup yeni kullanıcıyı doğrudan admin yapabilir — bu normal, ama register body'sinde `id` de kabul ediliyor: var olan bir kullanıcı ID'si gönderilirse `userRepository.save()` INSERT değil UPDATE (merge) yapar → **var olan kullanıcının satırının üzerine yazılması ihtimali var** (henüz test yazıp doğrulanmadı).

### DURUM: 1-2-4 yapıldı, 3 ve 5 açık (2026-09-08)

Kritik beşlinin tamamı kapatıldı. Sıra: önce açığı kanıtlayan test yazıldı (üçü de kırmızı
çıktı — rol ADMIN oldu, mevcut kullanıcı ezildi, BCrypt hash'i yanıtta döndü), sonra düzeltme,
sonra testlerin yeşile döndüğü gösterildi. Tam paket: 165 test, 0 hata.

| Açık | Düzeltme | Kanıt |
|---|---|---|
| JWT anahtarı sabit | `JwtUtil` anahtarı config'ten alıyor, yoksa fail-fast | `JwtUtilTest` (6 test) |
| Mass assignment (`role`) | `RegisterRequest` DTO'su, rol her zaman sunucuda `USER` | `testRegister_GovdedeRolADMINGonderilse...` |
| Mass assignment (`id`) | DTO'da `id` yok; `createUser` sıfırdan `User` kuruyor | `testRegister_GovdedeMevcutIdGonderilse...` |
| Şifre hash'i yanıtta | Yanıt `UserProfileResponse`; ayrıca `User.password` `@JsonIgnore` | `testRegister_YanitSifreHashiniIcermez` |
| `active=dev` sabit | Satır kaldırıldı; profil verilmezse korumalı zincir. `application-dev.properties` eklendi | Profilsiz açılış `JWT_SECRET` yok diye durdu (elle doğrulandı) |
| `admin`/`admin` her profilde | Şifre `ADMIN_PASSWORD`'dan; boşsa hesap açılmıyor, şifre loglanmıyor | dev açılışında log temiz (elle doğrulandı) |

H2 konsolu da prod zincirinden çıkarıldı (profilsiz deploy'da veritabanı konsolunu
herkese açık bırakıyordu). README çalıştırma bölümü yeni akışa göre yazıldı.

### Kalan yapılacaklar (bu görevden):
1. **Rol değiştirme ucu açılmadı.** Admin'in başka birini ADMIN yapması için ayrı,
   `hasRole("ADMIN")` korumalı bir endpoint (`PUT /api/users/{id}/role`) gerekiyor. Şu an
   register rolü hep `USER` yaptığı için **yeni admin oluşturmanın uygulama içi yolu yok**;
   ilk admin yalnızca `ADMIN_PASSWORD` ile açılıyor.
2. **Ortak yetki yardımcısı yazılmadı.** Ownership/role kontrolü hâlâ `TicketService`
   içinde satır satır. `AuthorizationHelper` / `OwnershipGuard` gibi tek bir sınıfa çıkarılıp
   hem ticket silme hem de yukarıdaki rol değiştirme ucu oradan geçsin.

---

## 5. Daha önce bu oturumda çözülen/yapılan işler (referans için özet)

- Log4j yerine SLF4J/Logback proje geneline yayıldı.
- Admin/user rolleri + RabbitMQ'lu async AI özetleme pipeline'ı (duplicate birleştirme: başlık güncellenmiyor, tag'ler birleşiyor, yeni çözüm ekleniyor) kuruldu.
- `TicketMutationExecutor` — optimistic locking retry helper — merkezi hale getirildi, gerçek race condition bug'ı bulunup düzeltildi (Elasticsearch external versioning'de version'ı elle artırmak gerekiyor).
- RabbitMQ listener concurrency ayarlandı + `AiSummarizationService` arayüzü (SOLID/DIP) ile Gemini/OpenAI değiştirilebilir hale getirildi.
- Gemini entegrasyonundaki gerçek bug bulundu: `gemini-pro` modeli deprecated/404, `gemini.api.url` config'i hiç okunmuyordu — düzeltildi, canlı curl + test ile doğrulandı.
- `/etc/hosts`'taki `wdmch.server` çift IP belirsizliği teşhis edildi, kullanıcıya düzeltme komutu verildi (henüz çalıştırıldığı teyit edilmedi).
- H2 sadece dev/test için; prod için PostgreSQL profili eklendi (`application-prod.properties`, `docker-compose.yml`) — **artık main'de.**
- README sadeleştirildi, kişisel altyapı bilgileri (wdmch, IP'ler) çıkarıldı, genel docker-compose talimatı eklendi — **artık main'de.**
- SQL injection / XSS / adversarial input testleri `UserServiceTest`, `TicketServiceTest`'e eklendi.
- Git geçmişi sır taraması yapıldı (madde 2).
- HikariCP + Elasticsearch bağlantı havuzları büyütüldü, `.DS_Store` takipten çıkarıldı (madde 3).

## 6. Sabit kurallar / kullanıcı tercihleri (unutma)

- **Docker asla bu sandbox'ta çalıştırılmayacak** — sadece kullanıcının kendi sunucusunda (home-pi / wdmch) çalışır. `docker compose up` gibi komutlar burada asla denenmeyecek.
- Değişiklikler doğrudan commit + push edilecek, her seferinde izin sorulmayacak (zor geri alınabilir / dışa dönük aksiyonlar hariç).
- DB: Elasticsearch home-pi'de Docker'da çalışıyor (bkz. hafıza: `dejaview-elasticsearch-config.md`).
