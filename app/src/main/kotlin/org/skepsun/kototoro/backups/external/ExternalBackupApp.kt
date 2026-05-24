package org.skepsun.kototoro.backups.external

enum class ExternalBackupApp {
    MIHON,
    KOMIKKU,
    VENERA,
    ANIYOMI,
    ANIKKU,
    ANIMIRU,

    ;

    val family: ExternalBackupFamily
        get() = when (this) {
            MIHON, KOMIKKU, VENERA -> ExternalBackupFamily.MANGA
            ANIYOMI, ANIKKU, ANIMIRU -> ExternalBackupFamily.ANIME
        }
}

enum class ExternalBackupFamily {
    MANGA,
    ANIME,
}
