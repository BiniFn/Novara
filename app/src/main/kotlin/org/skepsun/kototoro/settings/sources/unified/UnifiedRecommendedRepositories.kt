package org.skepsun.kototoro.settings.sources.unified

import org.skepsun.kototoro.extensions.repo.ExternalExtensionType

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

	private val cloudstreamRepoCapabilities = setOf(
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
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "CloudStream Providers",
				url = "https://raw.githubusercontent.com/recloudstream/extensions/master/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "Mega Repository",
				url = "https://raw.githubusercontent.com/self-similarity/MegaRepo/builds/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
				note = "CloudStream megarepo aggregator",
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "Phisher Repo",
				url = "https://raw.githubusercontent.com/phisher98/cloudstream-extensions-phisher/refs/heads/builds/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "CXXX",
				url = "https://raw.githubusercontent.com/phisher98/CXXX/builds/CXXX.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
				note = "Maintained 18+ repository",
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "IndoStream Repo",
				url = "https://raw.githubusercontent.com/TeKuma25/IndoStream/builds/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "CloudX Repository",
				url = "https://raw.githubusercontent.com/Asm0d3usX/CloudX/builds/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "CakesTwix Providers Repository",
				url = "https://codeberg.org/CakesTwix/cloudstream-extensions-uk/raw/branch/master/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "Italian providers repository",
				url = "https://raw.githubusercontent.com/Gian-Fr/ItalianProvider/builds/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "Multilingual providers repository",
				url = "https://codeberg.org/cloudstream/cloudstream-extensions-multilingual/raw/branch/builds/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "Hexated providers repository",
				url = "https://codeberg.org/cloudstream/cloudstream-extensions-hexated/raw/branch/builds/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "LikDev-256 Providers Repository",
				url = "https://raw.githubusercontent.com/LikDev-256/likdev256-tamil-providers/builds/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "NSFW Providers",
				url = "https://codeberg.org/cloudstream/cs3xxx-repo/raw/branch/dev/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "Arabic providers repository",
				url = "https://raw.githubusercontent.com/yoyzo/arab/builds/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "SkillShare-Repo",
				url = "https://raw.githubusercontent.com/techtanic/SkillShare-Repo/builds/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "Horis providers repository",
				url = "https://codeberg.org/cloudstream/cloudstream-extensions-horis/raw/branch/master/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "CSX (Hindi & English)",
				url = "https://raw.githubusercontent.com/SaurabhKaperwan/CSX/builds/CS.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "CuxPlug",
				url = "https://raw.githubusercontent.com/ycngmn/CuxPlug/refs/heads/main/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "Turkish Providers Repository | @KekikAkademi",
				url = "https://raw.githubusercontent.com/keyiflerolsun/Kekik-cloudstream/master/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "FStream (Repo: Francais)",
				url = "https://git.disroot.org/ayza/FStream/raw/branch/main/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "Luna712's CloudStream extension repository",
				url = "https://raw.githubusercontent.com/Luna712/Luna712-CloudStream-Extensions/master/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "NetMirror",
				url = "https://raw.githubusercontent.com/Sushan64/NetMirror-Extension/refs/heads/builds/Netflix.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "Redowan's BDIX repository",
				url = "https://raw.githubusercontent.com/redowan99/Redowan-CloudStream/master/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "Vietnamese Extension",
				url = "https://gitlab.com/tearrs/cloudstream-vietnamese/-/raw/main/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "zzikozz",
				url = "https://codeberg.org/zzikozz/frencharchive/raw/branch/Release/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "Kraptor'un CloudStream Reposu | @kraptor123",
				url = "https://raw.githubusercontent.com/Kraptor123/cs-kraptor/refs/heads/master/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
			),
			UnifiedRecommendedRepository(
				kind = UnifiedSourceKind.CLOUDSTREAM,
				name = "doGior's Had Enough",
				url = "https://raw.githubusercontent.com/doGior/doGiorsHadEnough/refs/heads/builds/repo.json",
				locationType = UnifiedRepositoryLocationType.REMOTE_URL,
				capabilities = cloudstreamRepoCapabilities,
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
			url = "https://cdn.jsdmirror.com/gh/XIU2/Yuedu/shuyuan",
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

	fun byExternalType(type: ExternalExtensionType): List<UnifiedRecommendedRepository> {
		return byKind(
			when (type) {
				ExternalExtensionType.MIHON -> UnifiedSourceKind.MIHON
				ExternalExtensionType.ANIYOMI -> UnifiedSourceKind.ANIYOMI
				ExternalExtensionType.IREADER -> UnifiedSourceKind.IREADER
				ExternalExtensionType.JAR -> UnifiedSourceKind.JAR
				ExternalExtensionType.CLOUDSTREAM -> UnifiedSourceKind.CLOUDSTREAM
			},
		)
	}
}
