package com.skaanb.DejaView.repository;

import com.skaanb.DejaView.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Kullanıcı tablosuna erişim.
 *
 * Nasıl çalışır: {@link JpaRepository} temel CRUD işlemlerini hazır verir;
 * aşağıdaki metotların gövdesi yoktur — Spring Data metot ADINDAN sorguyu
 * kendisi türetir ({@code findByEmail} -> {@code where email = ?}). Bu yüzden
 * metot adlarındaki alan isimleri {@link User} alanlarıyla birebir aynı olmak
 * zorundadır; alan adı değişirse sorgu uygulama açılışında patlar.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Kullanıcıyı kullanıcı adına göre bulur.
     *
     * Nasıl çalışır: JWT'nin subject alanı kullanıcı adı olduğu için kimlik
     * doğrulamanın ana giriş noktası burasıdır
     * (bkz. {@link com.skaanb.DejaView.service.CustomUserDetailsService}).
     *
     * @param username aranan kullanıcı adı (birebir eşleşme, benzersiz sütun)
     * @return kullanıcı; kayıt yoksa boş {@link Optional}
     */
    Optional<User> findByUsername(String username);

    /**
     * Kullanıcıyı e-posta adresine göre bulur.
     *
     * Nasıl çalışır: giriş akışı e-posta ile çalıştığı için login bu metodu
     * kullanır; profil güncellemede de e-posta çakışması bununla kontrol edilir.
     *
     * @param email aranan e-posta (birebir eşleşme, benzersiz sütun)
     * @return kullanıcı; kayıt yoksa boş {@link Optional}
     */
    Optional<User> findByEmail(String email);

    /**
     * Kullanıcıyı telefon numarasına göre bulur.
     *
     * Nasıl çalışır: telefon sütunu benzersiz olduğu için profil güncellemede
     * numaranın başka bir hesapta kayıtlı olup olmadığı önden bununla
     * denetlenir; aksi halde kayıt anında veritabanı kısıt hatası alınırdı.
     *
     * @param phoneNumber normalize edilmiş telefon numarası
     *                    (bkz. {@link com.skaanb.DejaView.service.UserService})
     * @return kullanıcı; kayıt yoksa boş {@link Optional}
     */
    Optional<User> findByPhoneNumber(String phoneNumber);

    /**
     * Bu kullanıcı adının alınmış olup olmadığını söyler.
     *
     * Nasıl çalışır: yalnızca varlık sorgusu çalıştırır, kaydı belleğe
     * getirmez; kayıt öncesi ve başlangıç admin hesabı kontrolünde kullanılır.
     *
     * @param username kontrol edilecek kullanıcı adı
     * @return kayıt varsa {@code true}
     */
    boolean existsByUsername(String username);

    /**
     * Bu e-postanın kayıtlı olup olmadığını söyler.
     *
     * @param email kontrol edilecek e-posta
     * @return kayıt varsa {@code true}
     */
    boolean existsByEmail(String email);
}
