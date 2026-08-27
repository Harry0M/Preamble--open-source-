package com.theblankstate.preamble.ui.components

import android.app.Activity
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.revenuecat.purchases.Package
import com.revenuecat.purchases.PackageType
import com.theblankstate.preamble.billing.RevenueCatManager
import com.theblankstate.preamble.data.PremiumFeature

/**
 * Preamble Pro Expressive Paywall Bottom Sheet.
 * Displays value propositions, subscription packages, and launches Google Play billing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PremiumUpsellSheet(
    feature: PremiumFeature,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val offerings by RevenueCatManager.currentOfferings.collectAsState()
    val availablePackages = offerings?.current?.availablePackages.orEmpty()

    var selectedPackageType by remember { mutableStateOf(PackageType.ANNUAL) }
    var isPurchasing by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Crown Badge
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Unlock Preamble Pro",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${featureDisplayName(feature)} is an exclusive Pro feature. Elevate your productivity with uninterrupted power.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Value Propositions Grid
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FeatureRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "5x Daily AI Capacity",
                    subtitle = "50 daily AI chat & planning messages powered by Mistral"
                )
                FeatureRow(
                    icon = Icons.Default.Group,
                    title = "Circles & Social Collaboration",
                    subtitle = "Shared goals, friend accountability & task assignment"
                )
                FeatureRow(
                    icon = Icons.Default.Insights,
                    title = "Lifetime Analytics & Wrapped",
                    subtitle = "Deep habit trends, yearly recaps & comprehensive stats"
                )
                FeatureRow(
                    icon = Icons.Default.Star,
                    title = "100% Ad-Free Experience",
                    subtitle = "Clean, distraction-free productivity without interruptions"
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Plan Options (Yearly, Monthly, Lifetime)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val monthlyPkg = availablePackages.find { it.packageType == PackageType.MONTHLY }
                val annualPkg = availablePackages.find { it.packageType == PackageType.ANNUAL }
                val lifetimePkg = availablePackages.find { it.packageType == PackageType.LIFETIME }

                // Monthly Card
                PlanCard(
                    modifier = Modifier.weight(1f),
                    title = "Monthly",
                    price = monthlyPkg?.product?.price?.formatted ?: "₹99 / mo",
                    badge = null,
                    isSelected = selectedPackageType == PackageType.MONTHLY,
                    onClick = { selectedPackageType = PackageType.MONTHLY }
                )

                // Annual Card (Highlighted Best Value)
                PlanCard(
                    modifier = Modifier.weight(1.1f),
                    title = "Annual",
                    price = annualPkg?.product?.price?.formatted ?: "₹799 / yr",
                    badge = "SAVE 33%",
                    isSelected = selectedPackageType == PackageType.ANNUAL,
                    onClick = { selectedPackageType = PackageType.ANNUAL }
                )

                // Lifetime Card
                PlanCard(
                    modifier = Modifier.weight(1f),
                    title = "Lifetime",
                    price = lifetimePkg?.product?.price?.formatted ?: "₹1,499",
                    badge = "FOREVER",
                    isSelected = selectedPackageType == PackageType.LIFETIME,
                    onClick = { selectedPackageType = PackageType.LIFETIME }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Purchase Action CTA Button
            Button(
                onClick = {
                    if (activity == null) return@Button
                    val targetPkg = when (selectedPackageType) {
                        PackageType.MONTHLY -> availablePackages.find { it.packageType == PackageType.MONTHLY }
                        PackageType.LIFETIME -> availablePackages.find { it.packageType == PackageType.LIFETIME }
                        else -> availablePackages.find { it.packageType == PackageType.ANNUAL }
                    }

                    if (targetPkg != null) {
                        isPurchasing = true
                        RevenueCatManager.purchase(
                            activity = activity,
                            packageToPurchase = targetPkg,
                            onSuccess = {
                                isPurchasing = false
                                Toast.makeText(context, "Welcome to Preamble Pro! 🎉", Toast.LENGTH_LONG).show()
                                onDismissRequest()
                            },
                            onError = { error, userCancelled ->
                                isPurchasing = false
                                if (!userCancelled) {
                                    Toast.makeText(context, "Purchase failed: ${error.message}", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        Toast.makeText(context, "Ready for purchase. Linking to Google Play Store...", Toast.LENGTH_SHORT).show()
                        onDismissRequest()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                enabled = !isPurchasing
            ) {
                if (isPurchasing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.5.dp
                    )
                } else {
                    Text(
                        text = "Upgrade to Pro",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Restore Purchases & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        isPurchasing = true
                        RevenueCatManager.restorePurchases(
                            context = context,
                            onSuccess = { customerInfo ->
                                isPurchasing = false
                                val isPro = customerInfo.entitlements[RevenueCatManager.PRO_ENTITLEMENT_ID]?.isActive == true
                                if (isPro) {
                                    Toast.makeText(context, "Pro subscription restored! 🎉", Toast.LENGTH_SHORT).show()
                                    onDismissRequest()
                                } else {
                                    Toast.makeText(context, "No active subscription found to restore.", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onError = { error ->
                                isPurchasing = false
                                Toast.makeText(context, "Restore failed: ${error.message}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                ) {
                    Text(
                        text = "Restore Purchases",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                TextButton(onClick = onDismissRequest) {
                    Text(
                        text = "Maybe Later",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlanCard(
    modifier: Modifier = Modifier,
    title: String,
    price: String,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (badge != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = badge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = price,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Human-readable name for each gated [PremiumFeature], shown in the upsell (Req 11.3). */
private fun featureDisplayName(feature: PremiumFeature): String = when (feature) {
    PremiumFeature.WRAPPED -> "Wrapped"
    PremiumFeature.AI_AUTO_SUBTASKS -> "AI auto subtasks"
    PremiumFeature.AI_EDIT_FROM_NOTIFICATION -> "Edit from notification"
    PremiumFeature.EXPRESSIVE_APPEARANCE -> "Expressive appearance"
    PremiumFeature.STATS_EXTENDED_RANGE -> "Extended stats range"
    PremiumFeature.STATS_DEDICATED_SCREEN -> "Advanced statistics"
    PremiumFeature.AI_AUTO_PLANNING -> "Plan my day"
    PremiumFeature.UNLIMITED_AI_CREDITS -> "Unlimited AI credits"
    PremiumFeature.CIRCLES_COLLABORATION -> "Circles & Collaboration"
    PremiumFeature.TASK_SHARING -> "Task Sharing & Friends"
}
