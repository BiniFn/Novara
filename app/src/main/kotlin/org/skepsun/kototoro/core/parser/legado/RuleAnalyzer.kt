package org.skepsun.kototoro.core.parser.legado

// 通用的规则切分处理（移植自 legado-with-MD3）
class RuleAnalyzer(data: String, private val isCodeMode: Boolean = false) {

    private var queue: String = data //被处理字符串
    private var pos = 0 //当前处理到的位置
    private var start = 0 //当前处理字段的开始
    private var startX = 0 //当前规则的开始

    private var rule = ArrayList<String>()  //分割出的规则列表
    private var step: Int = 0 //分割字符的长度
    var elementsType = "" //当前分割字符串

    fun trim() { // 修剪当前规则之前的"@"或者空白符
        if (queue[pos] == '@' || queue[pos] < '!') { //在while里重复设置start和startX会拖慢执行速度，所以先来个判断是否存在需要修剪的字段，最后再一次性设置start和startX
            pos++
            while (queue[pos] == '@' || queue[pos] < '!') pos++
            start = pos //开始点推移
            startX = pos //规则起始点推移
        }
    }

    //将pos重置为0，方便复用
    fun reSetPos() {
        pos = 0
        startX = 0
    }

    /**
     * 从剩余字串中拉出一个字符串，直到但不包括匹配序列
     * @param seq 查找的字符串 **区分大小写**
     * @return 是否找到相应字段。
     */
    private fun consumeTo(seq: String): Boolean {
        start = pos //将处理到的位置设置为规则起点
        val offset = queue.indexOf(seq, pos)
        return if (offset != -1) {
            pos = offset
            true
        } else false
    }

    /**
     * 从剩余字串中拉出一个字符串，直到但不包括匹配序列（匹配参数列表中一项即为匹配），或剩余字串用完。
     * @param seq 匹配字符串序列
     * @return 成功返回true并设置间隔，失败则直接返回fasle
     */
    private fun consumeToAny(vararg seq: String): Boolean {

        var pos = pos //声明新变量记录匹配位置，不更改类本身的位置

        while (pos != queue.length) {

            for (s in seq) {
                if (queue.regionMatches(pos, s, 0, s.length)) {
                    step = s.length //间隔数
                    this.pos = pos //匹配成功, 同步处理位置到类
                    return true //匹配就返回 true
                }
            }

            pos++ //逐个试探
        }
        return false
    }

    /**
     * 从剩余字串中拉出一个字符串，直到但不包括匹配序列（匹配参数列表中一项即为匹配），或剩余字串用完。
     * @param seq 匹配字符序列
     * @return 返回匹配位置
     */
    private fun findToAny(vararg seq: Char): Int {

        var pos = pos //声明新变量记录匹配位置，不更改类本身的位置

        while (pos != queue.length) {

            for (s in seq) if (queue[pos] == s) return pos //匹配则返回位置

            pos++ //逐个试探

        }

        return -1
    }

    /**
     * 拉出一个非内嵌代码平衡组，存在转义文本
     */
    private fun chompCodeBalanced(open: Char, close: Char): Boolean {

        var pos = pos //声明临时变量记录匹配位置，匹配成功后才同步到类的pos

        var depth = 0 //嵌套深度
        var otherDepth = 0 //其他对称符合嵌套深度

        var inSingleQuote = false //单引号
        var inDoubleQuote = false //双引号

        do {
            if (pos == queue.length) break
            val c = queue[pos++]
            if (c != ESC) { //非转义字符
                if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote //匹配具有语法功能的单引号
                else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote //匹配具有语法功能的双引号

                if (inSingleQuote || inDoubleQuote) continue //语法单元未匹配结束，直接进入下个循环

                if (c == '[') depth++ //开始嵌套一层
                else if (c == ']') depth-- //闭合一层嵌套
                else if (depth == 0) {
                    //处于默认嵌套中的非默认字符不需要平衡，仅depth为0时默认嵌套全部闭合，此字符才进行嵌套
                    if (c == open) otherDepth++
                    else if (c == close) otherDepth--
                }

            } else pos++

        } while (depth > 0 || otherDepth > 0) //拉出一个平衡字串

        return if (depth > 0 || otherDepth > 0) false else {
            this.pos = pos //同步位置
            true
        }
    }

    /**
     * 拉出一个规则平衡组，经过仔细测试xpath和jsoup中，引号内转义字符无效。
     */
    private fun chompRuleBalanced(open: Char, close: Char): Boolean {

        var pos = pos //声明临时变量记录匹配位置，匹配成功后才同步到类的pos
        var depth = 0 //嵌套深度
        var inSingleQuote = false //单引号
        var inDoubleQuote = false //双引号

        do {
            if (pos == queue.length) break
            val c = queue[pos++]
            if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote //匹配具有语法功能的单引号
            else if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote //匹配具有语法功能的双引号

            if (inSingleQuote || inDoubleQuote) continue //语法单元未匹配结束，直接进入下个循环
            else if (c == '\\') { //不在引号中的转义字符才将下个字符转义
                pos++
                continue
            }

            if (c == open) depth++ //开始嵌套一层
            else if (c == close) depth-- //闭合一层嵌套

        } while (depth > 0) //拉出一个平衡字串

        return if (depth > 0) false else {
            this.pos = pos //同步位置
            true
        }
    }

    /**
     * 不用正则,不到最后不切片也不用中间变量存储,只在序列中标记当前查找字段的开头结尾,到返回时才切片,高效快速准确切割规则
     * 解决jsonPath自带的"&&"和"||"与阅读的规则冲突,以及规则正则或字符串中包含"&&"、"||"、"%%"、"@"导致的冲突
     */
    fun splitRule(vararg split: String): ArrayList<String> {
        if (split.isEmpty()) {
            rule.add(queue.substring(startX))
            return rule
        }

        while (pos < queue.length) {
            // 检查是否遇到分隔符
            var stepLen = 0
            var foundType = ""
            var match = false
            for (s in split) {
                if (queue.regionMatches(pos, s, 0, s.length)) {
                    stepLen = s.length
                    foundType = s
                    match = true
                    break
                }
            }

            if (match) {
                // 找到了一个候选分隔符
                val segment = queue.substring(startX, pos)
                // 检查这个片段是否平衡
                val testAnalyzer = RuleAnalyzer(segment, isCodeMode)
                if (testAnalyzer.isBalanced()) {
                    // 平衡，确实是分隔符
                    rule.add(segment)
                    elementsType = foundType
                    pos += stepLen
                    startX = pos
                    start = pos
                    // 继续循环查找下一个
                    continue
                } else {
                    // 不平衡，跳过分隔符的第一个字符继续找
                    pos++
                }
            } else {
                // 处理括号跳过，避免在括号内匹配分隔符
                val c = queue[pos]
                if (c == '[' || c == '(' || c == '{') {
                    val next = when(c) {
                        '[' -> ']'
                        '(' -> ')'
                        '{' -> '}'
                        else -> ' '
                    }
                    if (!chompBalanced(c, next)) {
                        // 如果不平衡，强行推进一位
                        pos++
                    }
                } else {
                    pos++
                }
            }
        }

        // 添加最后一段
        if (startX < queue.length) {
            rule.add(queue.substring(startX))
        } else if (startX == queue.length && rule.isEmpty()) {
            // 处理空字符串或刚好结束的情况
            rule.add("")
        }
        return rule
    }

    /**
     * 检查当前 queue 是否平衡
     */
    private fun isBalanced(): Boolean {
        pos = 0
        while (pos < queue.length) {
            val c = queue[pos]
            val next = when (c) {
                '[' -> ']'
                '(' -> ')'
                '{' -> '}'
                else -> {
                    pos++
                    continue
                }
            }
            // 尝试拉出一个平衡组，如果失败说明不平衡
            if (!chompBalanced(c, next)) return false
        }
        return true
    }

    /**
     * 截取到指定匹配符左边所有字符，保留匹配符和右侧字符，常用于查找匹配字符左边的必要字符串或简单的截取动作
     */
    fun consumeToAnyLeft(vararg seq: String): String {
        val ps = pos //获取当前匹配位置
        return if (consumeToAny(*seq)) {
            val rl = queue.substring(start, pos)
            pos = ps
            rl
        } else queue.substring(pos)
    }

    /**
     * 截取到指定匹配符左边的所有字符，丢弃匹配符，但保留右侧字符，常用于简单的截取动作
     */
    fun consumeToAnyLeftRt(vararg seq: String): String {
        val ps = pos //获取当前匹配位置
        return if (consumeToAny(*seq)) {
            val rl = queue.substring(start, pos)
            pos += step //跳过匹配字段
            rl
        } else queue.substring(pos)
    }

    /**
     * 截取到指定匹配符右边所有字符，匹配字符做为返回结果首部，常用于查找匹配字符右边的内容
     */
    fun consumeToAnyRight(vararg seq: String): String? {
        val ps = pos //获取当前匹配位置
        return if (consumeToAny(*seq)) {
            val rl = queue.substring(pos)
            pos = ps
            rl
        } else null
    }

    /**
     * 截取到指定匹配符右边的所有字符，丢弃匹配符，常用于简单的截取动作
     */
    fun consumeToAnyRightRt(vararg seq: String): String? {
        return if (consumeToAny(*seq)) {
            val rl = queue.substring(pos + step) //跳过匹配字段
            rl
        } else null
    }

    /**
     * 获取字段中符合的平衡组, 提取、分离js代码功能强大
     * @param open 左符号
     * @param close 右符号
     * @return 成功则返回相关的字段，失败返回空串
     */
    fun chompBalancedGroup(open: Char, close: Char): String? {
        val st = StringBuilder()
        while (true) {
            val go = consumeToAny(open.toString(), close.toString())
            st.append(queue.substring(start, pos))
            if (!go) return null // 未找到 open/close

            val current = queue[pos]
            pos += step // 跳过匹配字符

            if (current == open) {
                // 进入嵌套，手动计数避免递归
                var depth = 1
                while (pos < queue.length && depth > 0) {
                    val c = queue[pos++]
                    if (c == open) depth++ else if (c == close) depth--
                }
                if (depth != 0) return null // 未匹配成功
                // 继续循环，寻找最外层关闭
                continue
            } else if (current == close) {
                // 最外层闭合
                return st.toString()
            }
        }
    }

    /**
     * 截取字段到指定字符右边为止，丢弃匹配字符
     */
    fun chompTo(c: Char): String {
        val start = pos //记录开始位置
        while (true) {
            if (pos == queue.length) break
            if (queue[pos++] == c) break
        }
        return queue.substring(start, pos)
    }

    // 移除旧的 splitRuleNext 逻辑，统一由 splitRule 处理

    /**
     * 替换内嵌规则
     * @param inner 起始标志,如{$.
     * @param startStep 不属于规则部分的前置字符长度，如{$.中{不属于规则的组成部分，故startStep为1
     * @param endStep 不属于规则部分的后置字符长度
     * @param fr 查找到内嵌规则时，用于解析的函数
     *
     * */
    fun innerRule(
        inner: String,
        startStep: Int = 1,
        endStep: Int = 1,
        fr: (String) -> String?
    ): String {
        val st = StringBuilder()

        while (consumeTo(inner)) { //拉取成功返回true，ruleAnalyzes里的字符序列索引变量pos后移相应位置，否则返回false,且isEmpty为true
            val posPre = pos //记录consumeTo匹配位置
            if (chompCodeBalanced('{', '}')) {
                val frv = fr(queue.substring(posPre + startStep, pos - endStep))
                if (!frv.isNullOrEmpty()) {
                    st.append(queue.substring(startX, posPre) + frv) //压入内嵌规则前的内容，及内嵌规则解析得到的字符串
                    startX = pos //记录下次规则起点
                    continue //获取内容成功，继续选择下个内嵌规则
                }
            }
            pos += inner.length //拉出字段不平衡，inner只是个普通字串，跳到此inner后继续匹配
        }

        return if (startX == 0) "" else st.apply {
            append(queue.substring(startX))
        }.toString()
    }

    /**
     * 替换内嵌规则
     * @param fr 查找到内嵌规则时，用于解析的函数
     *
     * */
    fun innerRule(
        startStr: String,
        endStr: String,
        fr: (String) -> String?
    ): String {

        val st = StringBuilder()
        while (consumeTo(startStr)) { //拉取成功返回true，ruleAnalyzes里的字符序列索引变量pos后移相应位置，否则返回false,且isEmpty为true
            pos += startStr.length //跳过开始字符串
            val posPre = pos //记录consumeTo匹配位置
            if (consumeTo(endStr)) {
                val frv = fr(queue.substring(posPre, pos))
                st.append(
                    queue.substring(
                        startX,
                        posPre - startStr.length
                    ) + frv
                ) //压入内嵌规则前的内容，及内嵌规则解析得到的字符串
                pos += endStr.length //跳过结束字符串
                startX = pos //记录下次规则起点
            }
        }

        return if (startX == 0) queue else st.apply {
            append(queue.substring(startX))
        }.toString()
    }

    /**
     * 设置平衡组函数，json或JavaScript时设置成chompCodeBalanced，否则为chompRuleBalanced
     */
    val chompBalanced = if (isCodeMode) ::chompCodeBalanced else ::chompRuleBalanced

    companion object {

        /**
         * 转义字符
         */
        private const val ESC = '\\'

    }
}
