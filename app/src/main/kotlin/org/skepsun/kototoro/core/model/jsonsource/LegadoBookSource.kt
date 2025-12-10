package org.skepsun.kototoro.core.model.jsonsource

import kotlinx.serialization.Serializable

/**
 * Legado book source configuration model
 * Represents a complete book source configuration in Legado format
 */
@Serializable
data class LegadoBookSource(
	val bookSourceName: String,
	val bookSourceUrl: String,
	val bookSourceType: Int = 0,       // 0=文字, 1=音频, 2=图片
	val bookSourceGroup: String? = null,
	val enabled: Boolean = true,
	val searchUrl: String? = null,
	val exploreUrl: String? = null,
	val header: String? = null,        // 请求头配置（JSON格式字符串）
	val ruleExplore: SearchRule? = null,  // 浏览规则（与搜索规则格式相同）
	val ruleSearch: SearchRule? = null,
	val ruleBookInfo: BookInfoRule? = null,
	val ruleToc: TocRule? = null,
	val ruleContent: ContentRule? = null,
)

/**
 * Search rule for parsing search results
 * Note: All fields are nullable to support sources with incomplete configurations
 */
@Serializable
data class SearchRule(
	val bookList: String? = null,      // 列表选择器
	val name: String? = null,          // 名称规则
	val author: String? = null,        // 作者规则
	val coverUrl: String? = null,      // 封面规则
	val bookUrl: String? = null,       // 链接规则
	val intro: String? = null,         // 简介规则
	val lastChapter: String? = null,   // 最新章节规则
	val kind: String? = null,          // 分类规则
	val init: String? = null,          // 初始化脚本
)

/**
 * Book info rule for parsing book details
 */
@Serializable
data class BookInfoRule(
	val name: String? = null,
	val author: String? = null,
	val coverUrl: String? = null,
	val intro: String? = null,
	val kind: String? = null,          // 分类/标签
	val lastChapter: String? = null,
	val init: String? = null,          // 初始化脚本
)

/**
 * Table of contents rule for parsing chapter list
 */
@Serializable
data class TocRule(
	val chapterList: String,           // 章节列表选择器
	val chapterName: String,           // 章节名称规则
	val chapterUrl: String,            // 章节链接规则
)

/**
 * Content rule for parsing chapter content
 */
@Serializable
data class ContentRule(
	val content: String,               // 内容规则
	val init: String? = null,          // 初始化脚本
)
