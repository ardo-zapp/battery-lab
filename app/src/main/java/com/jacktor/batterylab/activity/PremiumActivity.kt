package com.jacktor.batterylab.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jacktor.batterylab.MainActivity
import com.jacktor.batterylab.MainApp
import com.jacktor.batterylab.R
import com.jacktor.batterylab.databinding.ActivityPremiumBinding
import com.jacktor.batterylab.interfaces.RecyclerPremiumInterface
import com.jacktor.batterylab.utilities.preferences.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import kotlin.time.Duration.Companion.seconds


class PremiumActivity() : AppCompatActivity(), RecyclerPremiumInterface {

    private var activity: Activity? = null
    private var pref: Prefs? = null
    private var handler: Handler? = null

    private lateinit var binding: ActivityPremiumBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPremiumBinding.inflate(layoutInflater)
        setContentView(binding.root)

        activity = this
        handler = Handler(Looper.getMainLooper())
        pref = Prefs(this)

        binding.restoreFab.hide()

        //Top App Bar
        val topAppBar = findViewById<View>(R.id.topAppBar) as MaterialToolbar
        setSupportActionBar(topAppBar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.jacktorMsg.text = getString(R.string.jacktor_msg_purchased)
        binding.jacktorImg.setImageResource(R.mipmap.jacktor_pose_2)
        binding.loadProducts.visibility = View.GONE

        //Tombol back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                startActivity(Intent(activity, MainActivity::class.java))
                finish()
            }
        })


        //restore purchases
        binding.restoreFab.setOnClickListener {
            reloadScreen()
        }

        binding.showAllFeatures.setOnClickListener {
            MaterialAlertDialogBuilder(this).apply {
                setIcon(R.drawable.ic_premium_24)
                setTitle(getString(R.string.premium))
                setMessage(getString(R.string.premium_dialog))
                setPositiveButton(R.string.dialog_button_close) { d, _ ->
                    d.dismiss()
                }

                setCancelable(false)
                show()
            }
        }
    }

    @Suppress("DEPRECATION")
    fun reloadScreen() {
        //Reload the screen
        finish()

        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else overridePendingTransition(0, 0)

        startActivity(intent)

        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
        } else overridePendingTransition(0, 0)
    }

    fun showSnackbar(view: View?, message: String?, duration: Int) {
        Snackbar.make(view!!, message!!, duration).show()
    }

    override fun onItemClick(pos: Int) {
        showSnackbar(
            binding.restoreFab, getString(R.string.already_purchased_sb), Snackbar.LENGTH_SHORT
        )
    }
}
