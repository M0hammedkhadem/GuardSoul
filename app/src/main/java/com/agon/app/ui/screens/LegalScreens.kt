package com.agon.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.agon.app.R
import com.agon.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalScreenScaffold(
        title = stringResource(R.string.privacy_title),
        onBack = onBack
    ) {
        LegalSection(
            title = stringResource(R.string.privacy_section1_title),
            body = stringResource(R.string.privacy_section1_body)
        )
        LegalSection(
            title = stringResource(R.string.privacy_section2_title),
            body = stringResource(R.string.privacy_section2_body)
        )
        LegalSection(
            title = stringResource(R.string.privacy_section3_title),
            body = stringResource(R.string.privacy_section3_body)
        )
        LegalSection(
            title = stringResource(R.string.privacy_section4_title),
            body = stringResource(R.string.privacy_section4_body)
        )
        LegalSection(
            title = stringResource(R.string.privacy_section5_title),
            body = stringResource(R.string.privacy_section5_body)
        )
        LegalSection(
            title = stringResource(R.string.privacy_contact_title),
            body = stringResource(R.string.privacy_contact_body)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    LegalScreenScaffold(
        title = stringResource(R.string.terms_title),
        onBack = onBack
    ) {
        LegalSection(
            title = stringResource(R.string.terms_section1_title),
            body = stringResource(R.string.terms_section1_body)
        )
        LegalSection(
            title = stringResource(R.string.terms_section2_title),
            body = stringResource(R.string.terms_section2_body)
        )
        LegalSection(
            title = stringResource(R.string.terms_section3_title),
            body = stringResource(R.string.terms_section3_body)
        )
        LegalSection(
            title = stringResource(R.string.terms_section4_title),
            body = stringResource(R.string.terms_section4_body)
        )
        LegalSection(
            title = stringResource(R.string.terms_section5_title),
            body = stringResource(R.string.terms_section5_body)
        )
        LegalSection(
            title = stringResource(R.string.terms_section6_title),
            body = stringResource(R.string.terms_section6_body)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LegalScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    titleContentColor = text
                )
            )
        },
        containerColor = background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            content()
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.legal_effective_date),
                fontSize = 11.sp,
                color = textMuted
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LegalSection(title: String, body: String) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = text
        )
        Spacer(Modifier.height(6.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = card)
        ) {
            Text(
                text = body,
                fontSize = 13.sp,
                color = textSecondary,
                modifier = Modifier.padding(14.dp)
            )
        }
    }
}
