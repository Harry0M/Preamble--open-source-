package com.theblankstate.preamble.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.PurchasesError
import com.revenuecat.purchases.interfaces.LogInCallback
import com.revenuecat.purchases.interfaces.PurchaseCallback
import com.revenuecat.purchases.interfaces.ReceiveCustomerInfoCallback
import com.revenuecat.purchases.interfaces.ReceiveOfferingsCallback
import com.revenuecat.purchases.interfaces.UpdatedCustomerInfoListener
import com.revenuecat.purchases.models.StoreTransaction
import com.theblankstate.preamble.BuildConfig
import com.theblankstate.preamble.data.Entitlement
import com.theblankstate.preamble.data.EntitlementStore
import com.theblankstate.preamble.data.EntitlementTier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for Google Play In-App Purchases and Subscriptions via RevenueCat.
 */
object RevenueCatManager {
    private const val TAG = "RevenueCatManager"

    // Default Entitlement identifier configured in RevenueCat dashboard
    const val PRO_ENTITLEMENT_ID = "pro"

    // RevenueCat Google API Key (Replace with your live key from RevenueCat dashboard)
    // Keys start with "goog_"
    const val REVENUECAT_API_KEY = "goog_placeholder_key"

    private val _isProFlow = MutableStateFlow(false)
    val isProFlow: StateFlow<Boolean> = _isProFlow.asStateFlow()

    private val _currentOfferings = MutableStateFlow<Offerings?>(null)
    val currentOfferings: StateFlow<Offerings?> = _currentOfferings.asStateFlow()

    /**
     * Initializes RevenueCat in Application.onCreate()
     */
    fun initialize(context: Context) {
        try {
            Purchases.logLevel = if (BuildConfig.DEBUG) LogLevel.DEBUG else LogLevel.INFO

            val currentUid = FirebaseAuth.getInstance().currentUser?.uid

            val builder = PurchasesConfiguration.Builder(context, REVENUECAT_API_KEY)
            if (!currentUid.isNullOrBlank()) {
                builder.appUserID(currentUid)
            }

            Purchases.configure(builder.build())

            // Listen for real-time subscription changes
            Purchases.sharedInstance.updatedCustomerInfoListener = UpdatedCustomerInfoListener { customerInfo ->
                handleCustomerInfo(context, customerInfo)
            }

            // Initial fetch
            Purchases.sharedInstance.getCustomerInfo(object : ReceiveCustomerInfoCallback {
                override fun onReceived(customerInfo: CustomerInfo) {
                    handleCustomerInfo(context, customerInfo)
                }

                override fun onError(error: PurchasesError) {
                    Log.w(TAG, "Failed to fetch initial CustomerInfo: ${error.message}")
                }
            })

            FirebaseAuth.getInstance().addAuthStateListener { auth ->
                val user = auth.currentUser
                if (user != null) {
                    logIn(context, user.uid)
                }
            }

            fetchOfferings()
        } catch (e: Exception) {
            Log.e(TAG, "RevenueCat initialization failed", e)
        }
    }

    /**
     * Link RevenueCat user identity with Firebase Auth UID on sign-in
     */
    fun logIn(context: Context, uid: String) {
        if (!Purchases.isConfigured) return
        Purchases.sharedInstance.logIn(uid, object : LogInCallback {
            override fun onReceived(customerInfo: CustomerInfo, created: Boolean) {
                Log.i(TAG, "RevenueCat logIn success for $uid, isNewUser=$created")
                handleCustomerInfo(context, customerInfo)
            }

            override fun onError(error: PurchasesError) {
                Log.w(TAG, "RevenueCat logIn error: ${error.message}")
            }
        })
    }

    /**
     * Reset RevenueCat identity on user logout
     */
    fun logOut(context: Context) {
        if (!Purchases.isConfigured) return
        Purchases.sharedInstance.logOut(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                handleCustomerInfo(context, customerInfo)
            }

            override fun onError(error: PurchasesError) {
                Log.w(TAG, "RevenueCat logOut error: ${error.message}")
            }
        })
    }

    /**
     * Fetch available subscription and lifetime packages from Google Play
     */
    fun fetchOfferings(onComplete: ((Offerings?) -> Unit)? = null) {
        if (!Purchases.isConfigured) {
            onComplete?.invoke(null)
            return
        }

        Purchases.sharedInstance.getOfferings(object : ReceiveOfferingsCallback {
            override fun onReceived(offerings: Offerings) {
                _currentOfferings.value = offerings
                onComplete?.invoke(offerings)
            }

            override fun onError(error: PurchasesError) {
                Log.w(TAG, "Error fetching offerings: ${error.message}")
                onComplete?.invoke(null)
            }
        })
    }

    /**
     * Launch Google Play native purchase sheet for a package
     */
    fun purchase(
        activity: Activity,
        packageToPurchase: Package,
        onSuccess: (CustomerInfo) -> Unit,
        onError: (PurchasesError, Boolean) -> Unit
    ) {
        if (!Purchases.isConfigured) {
            onError(PurchasesError(com.revenuecat.purchases.PurchasesErrorCode.UnknownError, "RevenueCat is not configured"), false)
            return
        }

        val params = PurchaseParams.Builder(activity, packageToPurchase).build()
        Purchases.sharedInstance.purchase(params, object : PurchaseCallback {
            override fun onCompleted(storeTransaction: StoreTransaction, customerInfo: CustomerInfo) {
                handleCustomerInfo(activity, customerInfo)
                onSuccess(customerInfo)
            }

            override fun onError(error: PurchasesError, userCancelled: Boolean) {
                onError(error, userCancelled)
            }
        })
    }

    /**
     * Restore previous purchases
     */
    fun restorePurchases(
        context: Context,
        onSuccess: (CustomerInfo) -> Unit,
        onError: (PurchasesError) -> Unit
    ) {
        if (!Purchases.isConfigured) {
            onError(PurchasesError(com.revenuecat.purchases.PurchasesErrorCode.UnknownError, "RevenueCat is not configured"))
            return
        }

        Purchases.sharedInstance.restorePurchases(object : ReceiveCustomerInfoCallback {
            override fun onReceived(customerInfo: CustomerInfo) {
                handleCustomerInfo(context, customerInfo)
                onSuccess(customerInfo)
            }

            override fun onError(error: PurchasesError) {
                onError(error)
            }
        })
    }

    /**
     * Processes CustomerInfo and synchronizes with local EntitlementStore
     */
    private fun handleCustomerInfo(context: Context, customerInfo: CustomerInfo) {
        val proEntitlement = customerInfo.entitlements[PRO_ENTITLEMENT_ID]
        val isPro = proEntitlement?.isActive == true
        _isProFlow.value = isPro

        if (isPro) {
            val expDate = proEntitlement?.expirationDate?.time ?: 0L
            EntitlementStore.save(
                context,
                Entitlement(
                    tier = EntitlementTier.PREMIUM,
                    expiresAtMs = expDate,
                    activatedAtMs = System.currentTimeMillis(),
                    lastSyncedAtMs = System.currentTimeMillis()
                )
            )
        } else {
            val current = EntitlementStore.load(context)
            if (current.tier == EntitlementTier.PREMIUM) {
                EntitlementStore.save(
                    context,
                    current.copy(
                        tier = EntitlementTier.FREE_TIER,
                        lastSyncedAtMs = System.currentTimeMillis()
                    )
                )
            }
        }
        Log.i(TAG, "CustomerInfo updated: isPro=$isPro")
    }
}
