package com.utility.pdf.pdfcompressor

import android.app.Activity
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
//import com.google.android.gms.ads.rewarded.RewardedAdCallback
import com.google.android.gms.ads.rewarded.RewardItem

object RewardedAdManager {

    private var rewardedAd: RewardedAd? = null

    // ⭐ Track reward credits
    private var rewardCredits = 0

    // Test id
//    private const val AD_UNIT = "ca-app-pub-3940256099942544/5224354917"

    //Real id
    private const val AD_UNIT = "ca-app-pub-9300270005866808/5177370984"

    fun load(activity: Activity) {
        val request = AdRequest.Builder().build()

        RewardedAd.load(
            activity,
            AD_UNIT,
            request,
            object : RewardedAdLoadCallback() {

                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                }
            }
        )
    }

    // ⭐ NEW
    fun hasCredit(): Boolean = rewardCredits > 0

    // ⭐ NEW
    fun consumeCredit() {
        if (rewardCredits > 0) rewardCredits--
    }

    fun show(
        activity: Activity,
        onReward: () -> Unit,
        onClosed: () -> Unit = {}
    ) {
        val ad = rewardedAd ?: run {
            onClosed()
            return
        }

        ad.fullScreenContentCallback =
            object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    load(activity)
                    onClosed()
                }
            }

        ad.show(activity) {
            rewardCredits++   // ⭐ reward granted
            onReward()
        }
    }
}
