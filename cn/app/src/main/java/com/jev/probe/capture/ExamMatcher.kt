package com.jev.probe.capture

import android.content.Context
import org.json.JSONArray
import java.io.BufferedReader
import java.io.InputStreamReader

data class ExamQuestion(
    val id: Int,
    val title: String,
    val status: String,
    val answer: List<String>,
    val options: List<String>
)

object ExamMatcher {
    private var questions: List<ExamQuestion> = emptyList()

    fun init(ctx: Context) {
        if (questions.isNotEmpty()) return
        try {
            ctx.assets.open("zhiniao-bank.json").use { stream ->
                val reader = BufferedReader(InputStreamReader(stream, "UTF-8"))
                val text = reader.readText()
                val array = JSONArray(text)
                val list = ArrayList<ExamQuestion>()
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val id = obj.optInt("id", i + 1)
                    val title = obj.optString("title", "")
                    val status = obj.optString("status", "")
                    val ansArr = obj.optJSONArray("answer")
                    val answers = ArrayList<String>()
                    if (ansArr != null) {
                        for (j in 0 until ansArr.length()) answers.add(ansArr.getString(j))
                    }
                    val optArr = obj.optJSONArray("options")
                    val opts = ArrayList<String>()
                    if (optArr != null) {
                        for (j in 0 until optArr.length()) opts.add(optArr.getString(j))
                    }
                    list.add(ExamQuestion(id, title, status, answers, opts))
                }
                questions = list
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 针对屏幕识别出的全部文字行进行题库匹配
     * 返回匹配度最高的一道题及得分 (0~100)
     */
    fun match(screenText: String): Pair<ExamQuestion, Int>? {
        if (questions.isEmpty() || screenText.isBlank()) return null
        val cleanScreen = screenText.replace(Regex("""[^\w\u4e00-\u9fa5]"""), "")
        if (cleanScreen.length < 4) return null

        var bestQ: ExamQuestion? = null
        var bestScore = 0

        for (q in questions) {
            val cleanTitle = q.title.replace(Regex("""[^\w\u4e00-\u9fa5]"""), "")
            if (cleanTitle.isEmpty()) continue

            // 1. 题干前10~15个汉字直接完全包含
            val headLen = minOf(12, cleanTitle.length)
            val head = cleanTitle.substring(0, headLen)
            if (cleanScreen.contains(head)) {
                return Pair(q, 100)
            }

            // 2. 连续子串重合度评分
            var hits = 0
            val step = 3
            var total = 0
            for (i in 0 until cleanTitle.length - step + 1 step 2) {
                total++
                val gram = cleanTitle.substring(i, i + step)
                if (cleanScreen.contains(gram)) hits++
            }
            if (total > 0) {
                val score = (hits * 100) / total
                if (score > bestScore) {
                    bestScore = score
                    bestQ = q
                }
            }
        }

        return if (bestScore >= 45 && bestQ != null) Pair(bestQ, bestScore) else null
    }
}