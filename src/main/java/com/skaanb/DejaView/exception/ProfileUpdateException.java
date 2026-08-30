package com.skaanb.DejaView.exception;

/**
 * Profil güncellemesinin kullanıcı hatasından kaynaklandığı durumlar
 * (çakışan e-posta/telefon, geçersiz numara biçimi).
 *
 * GlobalExceptionHandler'daki catch-all Exception handler'ı her hatayı 500
 * olarak Elasticsearch'e ticket yazdığı için bu tip ayrı ele alınır: kullanıcı
 * hatası 400 döner ve hata kaydı oluşturulmaz.
 */
public class ProfileUpdateException extends RuntimeException {
    public ProfileUpdateException(String message) {
        super(message);
    }
}
