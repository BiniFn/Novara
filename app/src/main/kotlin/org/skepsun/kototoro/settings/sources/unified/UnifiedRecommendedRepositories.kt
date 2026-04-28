package org.skepsun.kototoro.settings.sources.unified

object UnifiedRecommendedRepositories {

	private val extensionRepoCapabilities = setOf(
		UnifiedRepositoryCapability.REFRESH,
		UnifiedRepositoryCapability.VERSIONED_INDEX,
		UnifiedRepositoryCapability.INSTALL_PACKAGE,
		UnifiedRepositoryCapability.TRUST_FINGERPRINT,
	)

	private val jarRepoCapabilities = setOf(
		UnifiedRepositoryCapability.REFRESH,
		UnifiedRepositoryCapability.VERSIONED_INDEX,
		UnifiedRepositoryCapability.INSTALL_PACKAGE,
	)

	private val jsonRepoCapabilities = setOf(
		UnifiedRepositoryCapability.REFRESH,
		UnifiedRepositoryCapability.IMPORT_JSON_LIST,
	)

	val all: List<UnifiedRecommendedRepository> = listOf(
		UnifiedRecommendedRepository(
			kind = UnifiedSourceKind.JAR,
			name = "Kototoro Parsers",
			url = "https://raw.githubusercontent.com/skepsun/kototoro-parsers/repo/index.min.json",
			locationType = UnifiedRepositoryLocationType.REMOTE_URL,
			capabilities = jarRepoCapabilities,
		),
		UnifiedRecommendedRepository(
			kind = UnifiedSourceKind.MIHON,
			name = "Keiyoushi",
			url = "https://raw.githubusercontent.com/keiyoushi/extensions/repo/index.min.json",
			locationType = UnifiedRepositoryLocationType.REMOTE_URL,
			capabilities = extensionRepoCapabilities,
		),
		UnifiedRecommendedRepository(
			kind = UnifiedSourceKind.MIHON,
			name = "Yuzono Manga Repo",
			url = "https://raw.githubusercontent.com/yuzono/manga-repo/repo/index.min.json",
			locationType = UnifiedRepositoryLocationType.REMOTE_URL,
			capabilities = extensionRepoCapabilities,
		),
		UnifiedRecommendedRepository(
			kind = UnifiedSourceKind.MIHON,
			name = "CopyManga Copy20",
			url = "https://raw.githubusercontent.com/LittleSurvival/copymanga-copy20/repo/index.min.json",
			locationType = UnifiedRepositoryLocationType.REMOTE_URL,
			capabilities = extensionRepoCapabilities,
			note = "Chinese-site coverage",
		),
		UnifiedRecommendedRepository(
			kind = UnifiedSourceKind.ANIYOMI,
			name = "Aniyomi Official",
			url = "https://raw.githubusercontent.com/aniyomiorg/aniyomi-extensions/repo/index.min.json",
			locationType = UnifiedRepositoryLocationType.REMOTE_URL,
			capabilities = extensionRepoCapabilities,
		),
		UnifiedRecommendedRepository(
			kind = UnifiedSourceKind.ANIYOMI,
			name = "Yuzono Anime Repo",
			url = "https://raw.githubusercontent.com/yuzono/anime-repo/repo/index.min.json",
			locationType = UnifiedRepositoryLocationType.REMOTE_URL,
			capabilities = extensionRepoCapabilities,
		),
		UnifiedRecommendedRepository(
			kind = UnifiedSourceKind.ANIYOMI,
			name = "KudoAni",
			url = "https://raw.githubusercontent.com/KudoAni/aniyomi-extensions/repo/index.min.json",
			locationType = UnifiedRepositoryLocationType.REMOTE_URL,
			capabilities = extensionRepoCapabilities,
		),
		UnifiedRecommendedRepository(
			kind = UnifiedSourceKind.IREADER,
			name = "IReader Official",
			url = "https://raw.githubusercontent.com/IReaderorg/IReader-extensions/repov2/index.min.json",
			locationType = UnifiedRepositoryLocationType.REMOTE_URL,
			capabilities = extensionRepoCapabilities,
		),
		UnifiedRecommendedRepository(
			kind = UnifiedSourceKind.LNREADER,
			name = "LNReader Official",
			url = org.skepsun.kototoro.core.lnreader.LNReaderRepository.OFFICIAL_REPO_URL,
			locationType = UnifiedRepositoryLocationType.REMOTE_URL,
			capabilities = jsonRepoCapabilities + UnifiedRepositoryCapability.VERSIONED_INDEX,
		),
		UnifiedRecommendedRepository(
			kind = UnifiedSourceKind.LEGADO,
			name = "XIU2 Yuedu",
			url = "https://github.com/XIU2/Yuedu",
			locationType = UnifiedRepositoryLocationType.REMOTE_URL,
			capabilities = jsonRepoCapabilities,
			note = "Legado sources usually cannot expose reliable per-source versions",
		),
		UnifiedRecommendedRepository(
			kind = UnifiedSourceKind.TVBOX,
			name = "Qiqi TVBox",
			url = "http://z.qiqiv.cn/123.txt",
			locationType = UnifiedRepositoryLocationType.REMOTE_URL,
			capabilities = jsonRepoCapabilities,
			note = "TVBox lists usually cannot expose reliable per-source versions",
		),
	)

	fun byKind(kind: UnifiedSourceKind): List<UnifiedRecommendedRepository> {
		return all.filter { it.kind == kind }
	}
}
