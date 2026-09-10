package com.skaanb.DejaView.model;

/**
 * Kullanıcı yetki seviyesi.
 *
 * Nasıl çalışır: {@link User#role} alanında {@code EnumType.STRING} olarak
 * saklanır ve {@link com.skaanb.DejaView.service.CustomUserDetailsService}
 * tarafından {@code "ROLE_" + name()} biçimine çevrilerek Spring Security'ye
 * verilir. {@code SecurityConfig} içindeki {@code hasRole("ADMIN")}
 * kısıtlamaları bu değerle eşleşir.
 *
 * Rol istemciden asla alınmaz; kayıt sırasında sunucu tarafından atanır
 * (bkz. {@link com.skaanb.DejaView.service.UserService#createUser}).
 */
public enum Role {

    /** Tüm kayıtlar üzerinde tam yetki: silme, yeniden analiz, park kuyruğu yönetimi. */
    ADMIN,

    /** Standart kullanıcı: yalnızca kendi açtığı kayıtlar üzerinde yetkili. */
    USER
}
