package com.projectkaka.inventory.ui.reports

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.projectkaka.inventory.data.local.entity.AccountEntity
import com.projectkaka.inventory.model.Money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementsScreen(
    onNavigateBack: () -> Unit,
    initialTab: Int = 0,
    viewModel: StatementsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(initialTab) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Financial Statements") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("Balance Sheet") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("Income Statement") }
                )
            }

            when (selectedTabIndex) {
                0 -> BalanceSheetTab(uiState)
                1 -> IncomeStatementTab(uiState)
            }
        }
    }
}

@Composable
fun BalanceSheetTab(uiState: StatementsUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Assets",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        items(uiState.assets) { account ->
            AccountRow(account)
        }
        item {
            TotalRow("Total Assets", uiState.totalAssets)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                "Liabilities",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
        items(uiState.liabilities) { account ->
            AccountRow(account)
        }
        item {
            TotalRow("Total Liabilities", uiState.totalLiabilities)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                "Equity",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        items(uiState.equity) { account ->
            AccountRow(account)
        }
        item {
            TotalRow("Retained Earnings (Net Income)", uiState.netIncome)
        }
        item {
            TotalRow("Total Equity", uiState.totalEquity)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        item {
            TotalRow(
                "Total Liabilities & Equity",
                uiState.totalLiabilities + uiState.totalEquity,
                isFinal = true
            )
        }
    }
}

@Composable
fun IncomeStatementTab(uiState: StatementsUiState) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "Revenue",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        items(uiState.revenues) { account ->
            AccountRow(account)
        }
        item {
            TotalRow("Total Revenue", uiState.totalRevenue)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        item {
            Text(
                "Expenses",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
        items(uiState.expenses) { account ->
            AccountRow(account)
        }
        item {
            TotalRow("Total Expenses", uiState.totalExpense)
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
        }

        item {
            TotalRow("Net Income", uiState.netIncome, isFinal = true)
        }
    }
}

@Composable
fun AccountRow(account: AccountEntity) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(account.name, style = MaterialTheme.typography.bodyLarge)
        Text(account.balance.format(), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun TotalRow(label: String, amountMinor: Long, isFinal: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = if (isFinal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            Money(amountMinor).format(),
            style = if (isFinal) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
