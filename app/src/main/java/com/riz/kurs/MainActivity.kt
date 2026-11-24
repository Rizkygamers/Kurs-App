package com.riz.kurs

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.roundToInt

private const val API_URL = "https://open.er-api.com/v6/latest/USD"
private const val FRESH_MS = 6 * 60 * 60 * 1000L // rate API update harian; segarkan tiap 6 jam

private val BG_1 = Color.parseColor("#0b1020")
private val TEXT = Color.parseColor("#f4f6fb")
private val MUTED = Color.argb(150, 244, 246, 251)
private val ERR = Color.parseColor("#ff9aa7")

internal data class Mode(
    val label: String,
    val inCur: String,
    val outCur: String,
    val calc: (rate: Double, amount: Double) -> Double,
)

internal val MODES = listOf(
    Mode("USD→IDR", "USD", "IDR") { r, a -> a * r },
    Mode("IDR→USD", "IDR", "USD") { r, a -> a / r },
    Mode("IDR→USC", "IDR", "USC") { r, a -> a * 100 / r },
    Mode("USC→IDR", "USC", "IDR") { r, a -> a * r / 100 },
)

class MainActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private lateinit var prefs: SharedPreferences

    private lateinit var segment: SegmentBar
    private lateinit var amountLabel: TextView
    private lateinit var amountInput: EditText
    private lateinit var resultLabel: TextView
    private lateinit var resultView: TextView
    private lateinit var rateView: TextView
    private lateinit var statusView: TextView

    private var modeIdx = 0
    private var rate = 0.0
    private var rateTime = 0L
    private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("kurs", Context.MODE_PRIVATE)
        rate = prefs.getString("rate", null)?.toDoubleOrNull() ?: 0.0
        rateTime = prefs.getLong("time", 0L)
        modeIdx = prefs.getInt("mode", 0).coerceIn(0, MODES.lastIndex)

        window.statusBarColor = BG_1
        window.navigationBarColor = BG_1
        setContentView(buildUi())

        if (rate > 0) statusView.text = "Rate tersimpan " + stamp(rateTime)
        applyMode(modeIdx, animate = false)
        if (rate <= 0.0 || System.currentTimeMillis() - rateTime > FRESH_MS) refresh()
    }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(26), dp(16), dp(36))
            addView(buildCard(), lp())
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(page, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        return FrameLayout(this).apply {
            addView(GlassBackground(this@MainActivity), FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(scroll, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }
    }

    private fun buildCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassBg(28, 0.10f)
            elevation = dp(14).toFloat()
            setPadding(dp(20), dp(24), dp(20), dp(22))
        }

        card.addView(TextView(this).apply {
            text = "Kurs"
            textSize = 26f
            letterSpacing = -0.02f
            setTextColor(TEXT)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        card.addView(TextView(this).apply {
            text = "USD / IDR · tetap jalan offline"
            textSize = 12f
            setTextColor(MUTED)
            setPadding(0, dp(3), 0, dp(18))
        })

        segment = SegmentBar(this).apply { onPick = { applyMode(it, animate = true) } }
        card.addView(segment, LinearLayout.LayoutParams(MATCH_PARENT, dp(44)))

        amountLabel = TextView(this).apply {
            textSize = 11f
            letterSpacing = 0.06f
            setTextColor(MUTED)
            setPadding(dp(2), dp(20), 0, 0)
        }
        card.addView(amountLabel)

        amountInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            setTextColor(TEXT)
            textSize = 34f
            letterSpacing = -0.02f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = null
            includeFontPadding = false
            setPadding(dp(2), dp(6), dp(2), dp(8))
            setText(prefs.getString("amount", "1"))
            setSelection(text.length)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    prefs.edit().putString("amount", s?.toString() ?: "").apply()
                    render()
                }
            })
        }
        card.addView(amountInput, lp())

        card.addView(
            View(this).apply { setBackgroundColor(Color.argb(28, 255, 255, 255)) },
            LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply { topMargin = dp(6) },
        )

        val well = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glassBg(20, 0.05f, 0.12f)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        resultLabel = TextView(this).apply {
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(MUTED)
        }
        well.addView(resultLabel)
        resultView = TextView(this).apply {
            textSize = 30f
            letterSpacing = -0.02f
            setTextColor(TEXT)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(4), 0, 0)
            setTextIsSelectable(true)
        }
        well.addView(resultView)
        card.addView(well, lp().apply { topMargin = dp(18) })

        rateView = TextView(this).apply {
            textSize = 12f
            setTextColor(MUTED)
            setPadding(dp(2), dp(12), 0, 0)
        }
        card.addView(rateView)

        statusView = TextView(this).apply {
            textSize = 12f
            setTextColor(MUTED)
            setPadding(dp(2), dp(4), dp(2), 0)
        }
        card.addView(statusView)

        card.addView(TextView(this).apply {
            text = "Refresh kurs"
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.argb(235, 99, 102, 241), Color.argb(200, 79, 70, 229)),
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), Color.argb(90, 255, 255, 255))
            }
            setPadding(dp(18), dp(13), dp(18), dp(13))
            isClickable = true
            setOnClickListener { refresh() }
        }, lp().apply { topMargin = dp(18) })

        return card
    }

    private fun applyMode(i: Int, animate: Boolean) {
        modeIdx = i
        prefs.edit().putInt("mode", i).apply()
        segment.setIndex(i, animate)
        amountLabel.text = "JUMLAH · ${MODES[i].inCur}"
        render()
    }

    private fun render() {
        val mode = MODES[modeIdx]
        resultLabel.text = "HASIL · ${mode.outCur}"
        if (rate <= 0.0) {
            resultView.text = "—"
            rateView.text = ""
            return
        }
        val amount = parseAmount(amountInput.text.toString())
        resultView.text = fmt(mode.calc(rate, amount))
        rateView.text = "1 USD = ${fmt(rate)} IDR"
    }

    private fun refresh() {
        if (busy) return
        busy = true
        statusView.setTextColor(MUTED)
        statusView.text = "Mengambil kurs…"
        io.execute {
            val res = runCatching { fetchRate() }
            runOnUiThread {
                busy = false
                res.onSuccess { fresh ->
                    rate = fresh
                    rateTime = System.currentTimeMillis()
                    prefs.edit().putString("rate", fresh.toString()).putLong("time", rateTime).apply()
                    statusView.setTextColor(MUTED)
                    statusView.text = "Diperbarui " + stamp(rateTime)
                    render()
                }.onFailure {
                    statusView.setTextColor(ERR)
                    statusView.text = if (rate > 0) {
                        "Offline · pakai rate " + stamp(rateTime)
                    } else {
                        "Gagal ambil kurs. Cek koneksi lalu Refresh."
                    }
                }
            }
        }
    }

    private fun fetchRate(): Double {
        val conn = URL(API_URL).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optString("result") != "success") error("api error")
            return json.getJSONObject("rates").getDouble("IDR")
        } finally {
            conn.disconnect()
        }
    }

    // shortcut: tanpa pemisah ribuan; desimal boleh "." atau ","
    private fun parseAmount(s: String): Double =
        s.trim().replace(",", ".").toDoubleOrNull() ?: 0.0

    private fun fmt(v: Double): String {
        val nf = NumberFormat.getInstance(Locale.forLanguageTag("id-ID"))
        nf.maximumFractionDigits = if (abs(v) >= 1000) 2 else 4
        return nf.format(v)
    }

    private fun stamp(t: Long): String =
        SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("id-ID")).format(Date(t))

    private fun glassBg(radius: Int, alpha: Float, strokeAlpha: Float = 0.16f) = GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(
            Color.argb((255 * alpha * 1.7f).roundToInt().coerceAtMost(255), 255, 255, 255),
            Color.argb((255 * alpha * 0.45f).roundToInt(), 255, 255, 255),
        ),
    ).apply {
        cornerRadius = dp(radius).toFloat()
        setStroke(dp(1), Color.argb((255 * strokeAlpha).roundToInt(), 255, 255, 255))
    }

    private fun lp() = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    /** Segmented control gaya iOS: pill yang meluncur di dalam panel kaca. */
    private inner class SegmentBar(context: Context) : FrameLayout(context) {
        var onPick: (Int) -> Unit = {}
        private val labels = mutableListOf<TextView>()
        private val pill = View(context)
        private var pos = 0f

        init {
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = glassBg(16, 0.04f, 0.12f)
            pill.background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.argb(52, 255, 255, 255))
                setStroke(dp(1), Color.argb(46, 255, 255, 255))
            }
            addView(pill, LayoutParams(0, MATCH_PARENT))

            val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            MODES.forEachIndexed { i, m ->
                val tab = TextView(context).apply {
                    text = m.label
                    textSize = 11f
                    gravity = Gravity.CENTER
                    isSingleLine = true
                    setTextColor(Color.argb(150, 244, 246, 251))
                    setOnClickListener { onPick(i) }
                }
                labels.add(tab)
                row.addView(tab, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
            }
            addView(row, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        }

        fun setIndex(i: Int, animate: Boolean) {
            labels.forEachIndexed { k, tv ->
                tv.setTextColor(if (k == i) Color.WHITE else Color.argb(150, 244, 246, 251))
            }
            if (!animate) {
                pos = i.toFloat()
                requestLayout()
                return
            }
            ValueAnimator.ofFloat(pos, i.toFloat()).apply {
                duration = 220
                interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
                addUpdateListener {
                    pos = it.animatedValue as Float
                    requestLayout()
                }
                start()
            }
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            val w = (width - paddingLeft - paddingRight).toFloat() / MODES.size
            val x = paddingLeft + (w * pos).roundToInt()
            pill.layout(x, paddingTop, x + w.roundToInt(), height - paddingBottom)
        }
    }
}
