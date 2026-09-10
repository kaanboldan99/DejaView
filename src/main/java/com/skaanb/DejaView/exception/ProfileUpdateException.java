package com.skaanb.DejaView.exception;

/**
 * Profil güncellemesinin kullanıcı hatasından kaynaklandığı durumlar
 * (çakışan e-posta/telefon, geçersiz numara biçimi, oturum yokluğu).
 *
 * Nasıl çalışır: {@link GlobalExceptionHandler} içindeki catch-all
 * {@code Exception} handler'ı her hatayı 500 kabul edip Elasticsearch'e bir
 * hata kaydı yazıyor. Bu tip ayrı bir handler ile ele alındığı için kullanıcı
 * hatası 400 döner ve sistem hatası kaydı oluşturulmaz.
 */
public class ProfileUpdateException extends RuntimeException {

    /**
     * @param message kullanıcıya doğrudan gösterilecek hata metni; yanıt
     *                gövdesindeki {@code message} alanına yazılır
     */
    public ProfileUpdateException(String message) {
        super(message);
    }
}
