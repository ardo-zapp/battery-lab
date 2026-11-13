package com.jacktor.batterylab.helpers

import android.app.Activity
import android.content.Context
import android.graphics.Typeface
import android.util.SparseArray
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.jacktor.batterylab.R
import com.jacktor.batterylab.utilities.preferences.PreferencesKeys

object TextAppearanceHelper {

    private val fontCache = SparseArray<Typeface?>()
    private const val DEFAULT_FONT_INDEX = 6
    private const val MAX_FONT_INDEX = 40
    private const val MAX_STYLE_INDEX = 2
    private const val MAX_SIZE_INDEX = 4

    enum class ViewRole {
        TOOLBAR_TITLE,
        CATEGORY_HEADER,
        PREFERENCE_TITLE,
        PREFERENCE_SUMMARY,
        CARD_TITLE,
        CARD_SUBTITLE,
        BOTTOM_NAV,
        HERO_PERCENT,
        HERO_METRIC,
        BUTTON,
        DIALOG_TITLE,
        PREMIUM_TITLE,
        PREMIUM_BODY,
        GENERAL
    }

    fun setTextAppearanceArray(
        context: Context,
        textViewArrayList: ArrayList<out TextView>,
        textStylePref: String?,
        textFontPref: String?,
        textSizePref: String?
    ) {
        val sizeIdx = parseIndex(textSizePref, 2, MAX_SIZE_INDEX)
        val fontIdx = parseIndex(textFontPref, DEFAULT_FONT_INDEX, MAX_FONT_INDEX)
        val styleIdx = parseIndex(textStylePref, 0, MAX_STYLE_INDEX)

        val regular = resolveTypeface(context, fontIdx, styleIdx, subTitle = true)
        val bold = resolveTypeface(context, fontIdx, styleIdx, subTitle = false)

        textViewArrayList.forEachIndexed { index, tv ->
            val isSub = index > 0
            val role = if (isSub) ViewRole.CARD_SUBTITLE else ViewRole.CARD_TITLE
            tv.typeface = if (isSub) regular else bold
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, getSpSizeForRole(role, sizeIdx))
        }
    }

    fun setTextAppearance(
        context: Context,
        textView: TextView,
        textStylePref: String?,
        textFontPref: String?,
        textSizePref: String?,
        subTitle: Boolean
    ) {
        val sizeIdx = parseIndex(textSizePref, 2, MAX_SIZE_INDEX)
        val fontIdx = parseIndex(textFontPref, DEFAULT_FONT_INDEX, MAX_FONT_INDEX)
        val styleIdx = parseIndex(textStylePref, 0, MAX_STYLE_INDEX)

        val role = if (subTitle) ViewRole.CARD_SUBTITLE else ViewRole.CARD_TITLE
        textView.typeface = resolveTypeface(textView.context, fontIdx, styleIdx, subTitle)
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, getSpSizeForRole(role, sizeIdx))
    }

    fun getGlobalTypeface(context: Context, isBold: Boolean = false): Typeface {
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val fontPref = pref.getString(PreferencesKeys.TEXT_FONT, "6")
        val fontIdx = parseIndex(fontPref, DEFAULT_FONT_INDEX, MAX_FONT_INDEX)
        val stylePref = pref.getString(PreferencesKeys.TEXT_STYLE, "0")
        val styleIdx = parseIndex(stylePref, 0, MAX_STYLE_INDEX)
        return resolveTypeface(context, fontIdx, styleIdx, subTitle = !isBold)
    }

    fun applyToView(view: View) {
        val context = view.context
        val pref = PreferenceManager.getDefaultSharedPreferences(context)
        val fontPref = pref.getString(PreferencesKeys.TEXT_FONT, "6")
        val fontIdx = parseIndex(fontPref, DEFAULT_FONT_INDEX, MAX_FONT_INDEX)
        val stylePref = pref.getString(PreferencesKeys.TEXT_STYLE, "0")
        val styleIdx = parseIndex(stylePref, 0, MAX_STYLE_INDEX)
        val sizePref = pref.getString(PreferencesKeys.TEXT_SIZE, "2")
        val sizeIdx = parseIndex(sizePref, 2, MAX_SIZE_INDEX)

        val regular = resolveTypeface(context, fontIdx, styleIdx, subTitle = true)
        val bold = resolveTypeface(context, fontIdx, styleIdx, subTitle = false)

        val styleKey = (fontIdx shl 16) or (styleIdx shl 8) or sizeIdx
        applyTypefaceRecursive(view, regular, bold, sizeIdx, styleKey)
    }

    fun applyToActivity(activity: Activity) {
        val decorView = try {
            activity.window?.decorView
        } catch (_: Throwable) {
            null
        } ?: return
        applyToView(decorView)
    }

    private fun applyTypefaceRecursive(
        v: View,
        regular: Typeface,
        bold: Typeface,
        sizeIdx: Int,
        styleKey: Int
    ) {
        if (v is TextView) {
            val applied = v.getTag(R.id.tag_typeface_applied) as? Int
            if (applied != styleKey) {
                val role = getViewRole(v)
                val isBold = isBoldRole(role, v)
                v.typeface = if (isBold) bold else regular
                val targetSp = getSpSizeForRole(role, sizeIdx)
                v.setTextSize(TypedValue.COMPLEX_UNIT_SP, targetSp)
                v.setTag(R.id.tag_typeface_applied, styleKey)
            }
        } else if (v is Toolbar) {
            for (i in 0 until v.childCount) {
                applyTypefaceRecursive(v.getChildAt(i), regular, bold, sizeIdx, styleKey)
            }
        }

        if (v is RecyclerView) {
            v.setTag(R.id.tag_typeface_applied, styleKey)
            if (v.getTag(R.id.tag_typeface_listener) == null) {
                v.setTag(R.id.tag_typeface_listener, true)
                v.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
                    override fun onChildViewAttachedToWindow(child: View) {
                        val currentKey = (v.getTag(R.id.tag_typeface_applied) as? Int) ?: styleKey
                        val childApplied = child.getTag(R.id.tag_typeface_applied) as? Int
                        if (childApplied != currentKey) {
                            applyTypefaceRecursive(child, regular, bold, sizeIdx, currentKey)
                            child.setTag(R.id.tag_typeface_applied, currentKey)
                        }
                    }
                    override fun onChildViewDetachedFromWindow(child: View) {}
                })
            }
        }

        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                applyTypefaceRecursive(v.getChildAt(i), regular, bold, sizeIdx, styleKey)
            }
        }
    }

    private fun getViewRole(tv: TextView): ViewRole {
        val id = tv.id

        // Hero percentage inside circular battery gauge
        if (id == R.id.tvHeroBatteryPercent) return ViewRole.HERO_PERCENT

        // Hero metric chips
        if (id == R.id.tvHeroTemp || id == R.id.tvHeroVoltage || id == R.id.tvHeroTime) {
            return ViewRole.HERO_METRIC
        }

        // Toolbar title
        if (tv.parent is Toolbar || id == R.id.toolbar) return ViewRole.TOOLBAR_TITLE

        val entryName = try {
            if (id != View.NO_ID) tv.resources.getResourceEntryName(id) else null
        } catch (_: Throwable) {
            null
        }

        // Bottom navigation bar labels
        if (entryName != null && entryName.contains("navigation_bar_item")) {
            return ViewRole.BOTTOM_NAV
        }

        // Dialog title
        if (id == androidx.appcompat.R.id.alertTitle) return ViewRole.DIALOG_TITLE

        // Card titles & Metric Values
        if (id == R.id.tvTitle || id == R.id.tvSectionTitle ||
            id == R.id.tvNumberOfCycles || id == R.id.tvNumberOfCyclesAndroid ||
            id == R.id.tvNumberOfCharges || id == R.id.tvNumberOfFullCharges) {
            return ViewRole.CARD_TITLE
        }

        // Summary / Subtitle / Secondary rows / Time rows
        if (id == android.R.id.summary || id == R.id.tvPrimary ||
            id == R.id.tvHeroBatteryStatus ||
            id == R.id.tvSecondary1 || id == R.id.tvSecondary2 ||
            id == R.id.tvSecondary3 || id == R.id.tvSecondary4 ||
            id == R.id.tvSecondary5 ||
            id == R.id.tvChargingTime || id == R.id.tvChargingTimeRemaining ||
            id == R.id.tvRemainingBatteryTime || id == R.id.tvScreenTime ||
            id == R.id.tvLastChargeTime) {
            return ViewRole.CARD_SUBTITLE
        }

        // Preference title / Category Header
        if (id == android.R.id.title) {
            val parent = tv.parent as? ViewGroup
            val hasSummaryOrWidget = parent?.let { p ->
                (0 until p.childCount).any { i ->
                    val cId = p.getChildAt(i).id
                    cId == android.R.id.summary || cId == android.R.id.widget_frame
                }
            } ?: false

            return if (!hasSummaryOrWidget && isCategoryView(tv)) {
                ViewRole.CATEGORY_HEADER
            } else {
                ViewRole.PREFERENCE_TITLE
            }
        }

        if (entryName != null) {
            if (entryName == "product_name" || entryName == "product_price" ||
                entryName == "tvPremiumBenefitsTitle" || entryName.contains("premiumTitle", ignoreCase = true)) {
                return ViewRole.PREMIUM_TITLE
            }
            if (entryName == "headerPremiumMsg" || entryName.startsWith("tvBenefit") ||
                entryName.contains("benefit", ignoreCase = true)) {
                return ViewRole.PREMIUM_BODY
            }
            if (entryName == "highlightChip") return ViewRole.BUTTON
            if (entryName.contains("title", ignoreCase = true)) return ViewRole.CARD_TITLE
            if (entryName.contains("summary", ignoreCase = true) ||
                entryName.startsWith("tvSecondary", ignoreCase = true)) {
                return ViewRole.CARD_SUBTITLE
            }
        }

        if (tv is com.google.android.material.button.MaterialButton) return ViewRole.BUTTON

        val tf = tv.typeface
        if (tf != null && tf.isBold) return ViewRole.CARD_TITLE

        return ViewRole.GENERAL
    }

    private fun isCategoryView(tv: TextView): Boolean {
        val parent = tv.parent as? View ?: return false
        val grandParent = parent.parent as? View
        val pName = runCatching { tv.resources.getResourceEntryName(parent.id) }.getOrNull() ?: ""
        val gpName = runCatching { grandParent?.id?.let { tv.resources.getResourceEntryName(it) } }.getOrNull() ?: ""
        return pName.contains("category", ignoreCase = true) || gpName.contains("category", ignoreCase = true)
    }

    private fun isBoldRole(role: ViewRole, tv: TextView): Boolean {
        return when (role) {
            ViewRole.TOOLBAR_TITLE -> true
            ViewRole.CATEGORY_HEADER -> true
            ViewRole.PREFERENCE_TITLE -> true
            ViewRole.CARD_TITLE -> true
            ViewRole.DIALOG_TITLE -> true
            ViewRole.HERO_PERCENT -> true
            ViewRole.BUTTON -> true
            ViewRole.PREMIUM_TITLE -> true
            ViewRole.PREMIUM_BODY -> false
            ViewRole.PREFERENCE_SUMMARY -> false
            ViewRole.CARD_SUBTITLE -> false
            ViewRole.BOTTOM_NAV -> false
            ViewRole.HERO_METRIC -> false
            ViewRole.GENERAL -> {
                val tf = tv.typeface
                tf != null && tf.isBold
            }
        }
    }

    fun getSpSizeForRole(role: ViewRole, sizeIdx: Int): Float {
        // Delta from sizeIdx (0: -2.5, 1: -1.25, 2: 0, 3: +1.5, 4: +3.0)
        val delta = when (sizeIdx) {
            0 -> -2.5f
            1 -> -1.25f
            2 -> 0.0f
            3 -> 1.5f
            4 -> 3.0f
            else -> 0.0f
        }

        return when (role) {
            ViewRole.TOOLBAR_TITLE -> (18.0f + delta * 1.0f).coerceAtLeast(14.0f)
            ViewRole.CATEGORY_HEADER -> (12.5f + delta * 0.8f).coerceAtLeast(10.0f)
            ViewRole.PREFERENCE_TITLE -> (14.0f + delta * 1.0f).coerceAtLeast(11.0f)
            ViewRole.PREFERENCE_SUMMARY -> (12.0f + delta * 0.9f).coerceAtLeast(9.5f)
            ViewRole.CARD_TITLE -> (14.0f + delta * 1.0f).coerceAtLeast(11.0f)
            ViewRole.CARD_SUBTITLE -> (12.0f + delta * 0.9f).coerceAtLeast(9.5f)
            ViewRole.BOTTOM_NAV -> (11.5f + delta * 0.6f).coerceAtLeast(9.5f)
            ViewRole.HERO_PERCENT -> (28.0f + delta * 2.0f).coerceAtLeast(22.0f)
            ViewRole.HERO_METRIC -> (13.5f + delta * 1.0f).coerceAtLeast(10.5f)
            ViewRole.BUTTON -> (14.0f + delta * 1.0f).coerceAtLeast(11.0f)
            ViewRole.DIALOG_TITLE -> (17.0f + delta * 1.0f).coerceAtLeast(14.0f)
            ViewRole.PREMIUM_TITLE -> (16.0f + delta * 1.0f).coerceAtLeast(13.0f)
            ViewRole.PREMIUM_BODY -> (14.0f + delta * 1.0f).coerceAtLeast(11.0f)
            ViewRole.GENERAL -> (12.0f + delta * 0.9f).coerceAtLeast(9.5f)
        }
    }

    private fun parseIndex(raw: String?, def: Int, max: Int): Int {
        val v = raw?.toIntOrNull() ?: def
        return v.coerceIn(0, max)
    }

    private fun resolveTypeface(
        context: Context,
        fontIndex: Int,
        styleIndex: Int,
        subTitle: Boolean
    ): Typeface {
        val base = getFontByIndex(context, fontIndex) ?: Typeface.DEFAULT
        val style = if (!subTitle) {
            if (styleIndex == 2) Typeface.BOLD_ITALIC else Typeface.BOLD
        } else {
            when (styleIndex) {
                1 -> Typeface.BOLD
                2 -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
        }
        return Typeface.create(base, style)
    }

    private fun getFontByIndex(context: Context, idx: Int): Typeface? {
        fontCache[idx]?.let { return it }

        val tf: Typeface? = when (idx) {
            0 -> Typeface.DEFAULT
            1 -> resFont(context, R.font.roboto)
            2 -> Typeface.SERIF
            3 -> Typeface.SANS_SERIF
            4 -> Typeface.MONOSPACE
            5 -> resFont(context, R.font.inter)
            6 -> resFont(context, R.font.geist)
            7 -> resFont(context, R.font.times_new_roman)
            8 -> resFont(context, R.font.ubuntu)
            9 -> resFont(context, R.font.lora)
            10 -> resFont(context, R.font.oswald)
            11 -> resFont(context, R.font.pt_sans)
            12 -> resFont(context, R.font.pt_serif)
            13 -> resFont(context, R.font.open_sans)
            14 -> resFont(context, R.font.noto_sans)
            15 -> resFont(context, R.font.nunito_sans)
            16 -> resFont(context, R.font.work_sans)
            17 -> resFont(context, R.font.merriweather_sans)
            18 -> resFont(context, R.font.sf_pro)
            19 -> resFont(context, R.font.lobster)
            20 -> resFont(context, R.font.moon_dance)
            21 -> resFont(context, R.font.rubik)
            22 -> resFont(context, R.font.playfair_display)
            23 -> resFont(context, R.font.rowdies)
            24 -> resFont(context, R.font.raleway)
            25 -> resFont(context, R.font.montserrat)
            26 -> resFont(context, R.font.sono)
            27 -> resFont(context, R.font.rubik_iso)
            28 -> resFont(context, R.font.roboto_condensed)
            29 -> resFont(context, R.font.poppins)
            30 -> resFont(context, R.font.kanit)
            31 -> resFont(context, R.font.playfair)
            32 -> resFont(context, R.font.mukta)
            33 -> resFont(context, R.font.mooli)
            34 -> resFont(context, R.font.inclusive_sans)
            35 -> resFont(context, R.font.borel)
            36 -> resFont(context, R.font.handjet)
            37 -> resFont(context, R.font.ysabeau_sc)
            38 -> resFont(context, R.font.ysabeau_office)
            39 -> resFont(context, R.font.ysabeau_infant)
            40 -> resFont(context, R.font.google_sans)
            else -> null
        }

        fontCache.put(idx, tf)
        return tf
    }

    private fun resFont(context: Context, resId: Int): Typeface? =
        try {
            ResourcesCompat.getFont(context, resId)
        } catch (_: Throwable) {
            null
        }
}
