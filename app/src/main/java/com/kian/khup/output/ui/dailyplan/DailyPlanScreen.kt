package com.kian.khup.output.ui.dailyplan

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kian.khup.core.data.db.entities.DailyPlan
import com.kian.khup.output.ui.dailyplan.components.AddPlanInline
import com.kian.khup.output.ui.dailyplan.components.EditPlanDialog
import com.kian.khup.output.ui.dailyplan.components.PlanRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyPlanScreen(
    onBack: () -> Unit,
    viewModel: DailyPlanViewModel = hiltViewModel(),
) {
    val plans by viewModel.plans.collectAsStateWithLifecycle()
    val progress by viewModel.progress.collectAsStateWithLifecycle()
    var editingPlan by remember { mutableStateOf<DailyPlan?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("今日计划") },
                actions = {
                    val (done, total) = progress
                    if (total > 0) {
                        Text(
                            text = "$done / $total 已完成",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 16.dp),
        ) {
            items(plans, key = { it.id }) { plan ->
                PlanRow(
                    plan = plan,
                    onToggleDone = { viewModel.toggleDone(plan.id) },
                    onEdit = { editingPlan = plan },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                AddPlanInline(
                    onAdd = viewModel::add,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    editingPlan?.let { plan ->
        EditPlanDialog(
            plan = plan,
            onDismiss = { editingPlan = null },
            onSave = { title, note ->
                viewModel.updateContent(plan.id, title, note)
                editingPlan = null
            },
            onDelete = {
                viewModel.delete(plan.id)
                editingPlan = null
            },
        )
    }
}
