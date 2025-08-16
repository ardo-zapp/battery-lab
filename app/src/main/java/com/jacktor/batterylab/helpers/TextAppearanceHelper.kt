package com.jacktor.batterylab.helpers

import android.content.Context
import android.graphics.Typeface
import android.util.SparseArray
import android.util.TypedValue
import androidx.appcompat.widget.AppCompatTextView
import androidx.core.content.res.ResourcesCompat
import com.jacktor.batterylab.R
import com.jacktor.premium.Premium

object TextAppearanceHelper {


    private val fontCache = SparseArray<Typeface?>()
    private const val DEFAULT_FONT_INDEX = 6
    private const val MAX_FONT_INDEX = 39
    private const val MAX_STYLE_INDEX = 2
    private const val MAX_SIZE_INDEX = 4

    fun setTextAppearanceArray(
        context: Context,
        textViewArrayList: ArrayList<AppCompatTextView>,
        textStylePref: String?,
        textFontPref: String?,
        textSizePref: String?
    ) {
        val isPremium = Premium.isPremium().value
        val sizeIdx = parseIndex(textSizePref, 2, MAX_SIZE_INDEX)
        val fontIdx = if (isPremium) parseIndex(textFontPref, DEFAULT_FONT_INDEX, MAX_FONT_INDEX)
        else DEFAULT_FONT_INDEX
        val styleIdx = parseIndex(textStylePref, 0, MAX_STYLE_INDEX)

        val typeface = resolveTypeface(context, fontIdx, styleIdx)

        textViewArrayList.forEach { tv ->
            setTextSize(context, tv, sizeIdx, subTitle = false)
            tv.typeface = typeface
        }
    }

    fun setTextAppearance(
        context: Context,
        textView: AppCompatTextView,
        textStylePref: String?,
        textFontPref: String?,
        textSizePref: String?,
        subTitle: Boolean
    ) {
        val isPremium = Premium.isPremium().value

        val sizeIdx = parseIndex(textSizePref, if (subTitle) 2 else 2, MAX_SIZE_INDEX)
        val fontIdx = if (isPremium) parseIndex(textFontPref, DEFAULT_FONT_INDEX, MAX_FONT_INDEX)
        else DEFAULT_FONT_INDEX
        val styleIdx = parseIndex(textStylePref, 0, MAX_STYLE_INDEX)

        setTextSize(context, textView, sizeIdx, subTitle)
        textView.typeface = resolveTypeface(textView.context, fontIdx, styleIdx)
    }

    private fun parseIndex(raw: String?, def: Int, max: Int): Int {
        val v = raw?.toIntOrNull() ?: def
        return v.coerceIn(0, max)
    }

    private fun resolveTypeface(context: Context, fontIndex: Int, styleIndex: Int): Typeface {
        val base = getFontByIndex(context, fontIndex) ?: Typeface.DEFAULT
        val style = when (styleIndex) {
            1 -> Typeface.BOLD
            2 -> Typeface.ITALIC
            else -> Typeface.NORMAL
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
            6 -> resFont(context, R.font.google_sans)
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

    private fun setTextSize(
        context: Context,
        textView: AppCompatTextView,
        sizeIndex: Int,
        subTitle: Boolean
    ) {
        if (!subTitle) {
            when (sizeIndex) {
                0 -> textView.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    context.resources.getDimension(R.dimen.very_small_text_size)
                )

                1 -> textView.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    context.resources.getDimension(R.dimen.small_text_size)
                )

                2 -> textView.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    context.resources.getDimension(R.dimen.medium_text_size)
                )

                3 -> textView.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    context.resources.getDimension(R.dimen.large_text_size)
                )

                4 -> textView.setTextSize(
                    TypedValue.COMPLEX_UNIT_PX,
                    context.resources.getDimension(R.dimen.very_large_text_size)
                )
            }
        } else {
            when (sizeIndex) {
                0 -> textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 6f)
                1 -> textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 8f)
                2 -> textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                3 -> textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                4 -> textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            }
        }
    }
}
