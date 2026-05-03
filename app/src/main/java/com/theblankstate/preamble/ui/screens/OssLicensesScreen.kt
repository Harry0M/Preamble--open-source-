package com.theblankstate.preamble.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class OssEntry(
    val name: String,
    val copyright: String,
    val license: String,
    val url: String,
    val licenseText: String,
)

private const val APACHE_2 = """Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."""

private const val MIT = """Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."""

private const val ISC = """Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted, provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE."""

private val OSS_ENTRIES = listOf(
    OssEntry("Jetpack Compose", "Copyright (c) The Android Open Source Project", "Apache 2.0", "https://developer.android.com/jetpack/compose", APACHE_2),
    OssEntry("AndroidX (Core, Lifecycle, Activity, Navigation, Room, Work, Glance, Credentials, ProfileInstaller)", "Copyright (c) The Android Open Source Project", "Apache 2.0", "https://developer.android.com/jetpack/androidx", APACHE_2),
    OssEntry("Material Components for Android & Material 3", "Copyright (c) Google LLC", "Apache 2.0", "https://github.com/material-components/material-components-android", APACHE_2),
    OssEntry("Material Symbols / Icons", "Copyright (c) Google LLC", "Apache 2.0", "https://fonts.google.com/icons", APACHE_2),
    OssEntry("Kotlin & kotlinx-coroutines", "Copyright (c) JetBrains s.r.o.", "Apache 2.0", "https://github.com/JetBrains/kotlin", APACHE_2),
    OssEntry("Firebase Android SDK (Auth, Firestore, Messaging, Storage, Remote Config)", "Copyright (c) Google LLC", "Apache 2.0", "https://firebase.google.com/", APACHE_2),
    OssEntry("Google Play Services (Auth, Ads, Identity)", "Copyright (c) Google LLC", "Apache 2.0", "https://developers.google.com/android/", APACHE_2),
    OssEntry("Google API Client for Java & Calendar / Tasks API services", "Copyright (c) Google LLC", "Apache 2.0", "https://github.com/googleapis/google-api-java-client", APACHE_2),
    OssEntry("OkHttp", "Copyright (c) Square, Inc.", "Apache 2.0", "https://github.com/square/okhttp", APACHE_2),
    OssEntry("Gson", "Copyright (c) Google LLC", "Apache 2.0", "https://github.com/google/gson", APACHE_2),
    OssEntry("Coil (compose, svg)", "Copyright (c) Coil Contributors", "Apache 2.0", "https://github.com/coil-kt/coil", APACHE_2),
    OssEntry("Vico Charts", "Copyright (c) Patryk Goworowski and contributors", "Apache 2.0", "https://github.com/patrykandpatrick/vico", APACHE_2),
    OssEntry("AndroidX Graphics Shapes", "Copyright (c) The Android Open Source Project", "Apache 2.0", "https://developer.android.com/jetpack/androidx/releases/graphics", APACHE_2),
    OssEntry("PostHog Android", "Copyright (c) PostHog Inc.", "MIT", "https://github.com/PostHog/posthog-android", MIT),
    OssEntry("Konfetti", "Copyright (c) Dion Segijn", "ISC", "https://github.com/DanielMartinus/Konfetti", ISC),
    OssEntry("gRPC for Java", "Copyright (c) The gRPC Authors", "Apache 2.0", "https://github.com/grpc/grpc-java", APACHE_2),
    OssEntry("JUnit 4", "Copyright (c) JUnit", "EPL 1.0", "https://junit.org/junit4/", "Eclipse Public License - v 1.0. Full text: https://www.eclipse.org/legal/epl-v10.html"),
    OssEntry("Espresso", "Copyright (c) The Android Open Source Project", "Apache 2.0", "https://developer.android.com/training/testing/espresso", APACHE_2),
    OssEntry("Streamline HQ Illustrations", "Free illustrations by Streamline", "CC BY 4.0", "https://streamlinehq.com", "Creative Commons Attribution 4.0 International (CC BY 4.0).\n\nYou are free to share and adapt, as long as appropriate credit is given.\n\nFull text: https://creativecommons.org/licenses/by/4.0/"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OssLicensesSubscreen(modifier: Modifier = Modifier) {
    var detail by remember { mutableStateOf<OssEntry?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item {
            Text(
                "Preamble bundles open-source software. Listed below are the libraries we ship and their licenses. Tap any entry for the full license text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            )
            HorizontalDivider()
        }
        items(OSS_ENTRIES) { entry ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { detail = entry }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(entry.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(entry.copyright, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("License: ${entry.license}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
        }
    }

    detail?.let { entry ->
        ModalBottomSheet(
            onDismissRequest = { detail = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(entry.copyright, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
                Text(entry.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                Text(entry.licenseText, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
