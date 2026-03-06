package org.skepsun.kototoro.reader.translate.data

data class NcnnOfficialModel(
	val id: String,
	val title: String,
	val version: String,
	val detParamUrl: String,
	val detBinUrl: String,
	val recParamUrl: String,
	val recBinUrl: String,
)

object NcnnOfficialModelCatalog {

	private const val BASE_URL = "https://raw.githubusercontent.com/equationl/ncnn-android-ppocrv5/master/app/src/main/assets/"

	val models = listOf(
		NcnnOfficialModel(
			id = "ppocrv5_mobile",
			title = "PP-OCRv5 Mobile (Recommended)",
			version = "v5_mobile",
			detParamUrl = BASE_URL + "PP_OCRv5_mobile_det.ncnn.param",
			detBinUrl = BASE_URL + "PP_OCRv5_mobile_det.ncnn.bin",
			recParamUrl = BASE_URL + "PP_OCRv5_mobile_rec.ncnn.param",
			recBinUrl = BASE_URL + "PP_OCRv5_mobile_rec.ncnn.bin",
		),
		NcnnOfficialModel(
			id = "ppocrv5_server",
			title = "PP-OCRv5 Server",
			version = "v5_server",
			detParamUrl = BASE_URL + "PP_OCRv5_server_det.ncnn.param",
			detBinUrl = BASE_URL + "PP_OCRv5_server_det.ncnn.bin",
			recParamUrl = BASE_URL + "PP_OCRv5_server_rec.ncnn.param",
			recBinUrl = BASE_URL + "PP_OCRv5_server_rec.ncnn.bin",
		),
	)

	fun findById(id: String?): NcnnOfficialModel? {
		return models.firstOrNull { it.id == id }
	}
}
