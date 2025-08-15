package com.jacktor.batterylab.helpers

import com.jacktor.batterylab.R

object StatusBarHelper {

    fun stat(level: Int?): Int {
        if (level == null || level !in 0..100) {
            return R.drawable.ic_battery_stat_24
        }

        val resourceName = when (level) {
            100 -> "hundred"
            in 0..9 -> toWord(level)
            else -> {
                val tens = toWord(level / 10)
                val ones = toWord(level % 10)
                "${tens}_${ones}"
            }
        }

        return getResId(resourceName) ?: R.drawable.ic_battery_stat_24
    }

    private fun toWord(digit: Int): String = when (digit) {
        0 -> "zero"
        1 -> "one"
        2 -> "two"
        3 -> "three"
        4 -> "four"
        5 -> "five"
        6 -> "six"
        7 -> "seven"
        8 -> "eight"
        9 -> "nine"
        else -> throw IllegalArgumentException("Invalid digit: $digit")
    }

    private fun getResId(name: String): Int? = when (name) {
        "zero" -> R.mipmap.zero
        "one" -> R.mipmap.one
        "two" -> R.mipmap.two
        "three" -> R.mipmap.three
        "four" -> R.mipmap.four
        "five" -> R.mipmap.five
        "six" -> R.mipmap.six
        "seven" -> R.mipmap.seven
        "eight" -> R.mipmap.eight
        "nine" -> R.mipmap.nine
        "hundred" -> R.mipmap.hundred

        // Combination of 2 numbers
        "one_zero" -> R.mipmap.one_zero
        "one_one" -> R.mipmap.one_one
        "one_two" -> R.mipmap.one_two
        "one_three" -> R.mipmap.one_three
        "one_four" -> R.mipmap.one_four
        "one_five" -> R.mipmap.one_five
        "one_six" -> R.mipmap.one_six
        "one_seven" -> R.mipmap.one_seven
        "one_eight" -> R.mipmap.one_eight
        "one_nine" -> R.mipmap.one_nine
        "two_zero" -> R.mipmap.two_zero
        "two_one" -> R.mipmap.two_one
        "two_two" -> R.mipmap.two_two
        "two_three" -> R.mipmap.two_three
        "two_four" -> R.mipmap.two_four
        "two_five" -> R.mipmap.two_five
        "two_six" -> R.mipmap.two_six
        "two_seven" -> R.mipmap.two_seven
        "two_eight" -> R.mipmap.two_eight
        "two_nine" -> R.mipmap.two_nine
        "three_zero" -> R.mipmap.three_zero
        "three_one" -> R.mipmap.three_one
        "three_two" -> R.mipmap.three_two
        "three_three" -> R.mipmap.three_three
        "three_four" -> R.mipmap.three_four
        "three_five" -> R.mipmap.three_five
        "three_six" -> R.mipmap.three_six
        "three_seven" -> R.mipmap.three_seven
        "three_eight" -> R.mipmap.three_eight
        "three_nine" -> R.mipmap.three_nine
        "four_zero" -> R.mipmap.four_zero
        "four_one" -> R.mipmap.four_one
        "four_two" -> R.mipmap.four_two
        "four_three" -> R.mipmap.four_three
        "four_four" -> R.mipmap.four_four
        "four_five" -> R.mipmap.four_five
        "four_six" -> R.mipmap.four_six
        "four_seven" -> R.mipmap.four_seven
        "four_eight" -> R.mipmap.four_eight
        "four_nine" -> R.mipmap.four_nine
        "five_zero" -> R.mipmap.five_zero
        "five_one" -> R.mipmap.five_one
        "five_two" -> R.mipmap.five_two
        "five_three" -> R.mipmap.five_three
        "five_four" -> R.mipmap.five_four
        "five_five" -> R.mipmap.five_five
        "five_six" -> R.mipmap.five_six
        "five_seven" -> R.mipmap.five_seven
        "five_eight" -> R.mipmap.five_eight
        "five_nine" -> R.mipmap.five_nine
        "six_zero" -> R.mipmap.six_zero
        "six_one" -> R.mipmap.six_one
        "six_two" -> R.mipmap.six_two
        "six_three" -> R.mipmap.six_three
        "six_four" -> R.mipmap.six_four
        "six_five" -> R.mipmap.six_five
        "six_six" -> R.mipmap.six_six
        "six_seven" -> R.mipmap.six_seven
        "six_eight" -> R.mipmap.six_eight
        "six_nine" -> R.mipmap.six_nine
        "seven_zero" -> R.mipmap.seven_zero
        "seven_one" -> R.mipmap.seven_one
        "seven_two" -> R.mipmap.seven_two
        "seven_three" -> R.mipmap.seven_three
        "seven_four" -> R.mipmap.seven_four
        "seven_five" -> R.mipmap.seven_five
        "seven_six" -> R.mipmap.seven_six
        "seven_seven" -> R.mipmap.seven_seven
        "seven_eight" -> R.mipmap.seven_eight
        "seven_nine" -> R.mipmap.seven_nine
        "eight_zero" -> R.mipmap.eight_zero
        "eight_one" -> R.mipmap.eight_one
        "eight_two" -> R.mipmap.eight_two
        "eight_three" -> R.mipmap.eight_three
        "eight_four" -> R.mipmap.eight_four
        "eight_five" -> R.mipmap.eight_five
        "eight_six" -> R.mipmap.eight_six
        "eight_seven" -> R.mipmap.eight_seven
        "eight_eight" -> R.mipmap.eight_eight
        "eight_nine" -> R.mipmap.eight_nine
        "nine_zero" -> R.mipmap.nine_zero
        "nine_one" -> R.mipmap.nine_one
        "nine_two" -> R.mipmap.nine_two
        "nine_three" -> R.mipmap.nine_three
        "nine_four" -> R.mipmap.nine_four
        "nine_five" -> R.mipmap.nine_five
        "nine_six" -> R.mipmap.nine_six
        "nine_seven" -> R.mipmap.nine_seven
        "nine_eight" -> R.mipmap.nine_eight
        "nine_nine" -> R.mipmap.nine_nine
        else -> null
    }
}
