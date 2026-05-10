package com.kian.khup.output.ui.dailyplan.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kian.khup.core.data.db.entities.DailyPlan

@Composable
fun EditPlanDialog(
    plan: DailyPlan,
    onDismiss: () -> Unit,
    onSave: (title: String, note: String?) -> Unit,
    onDelete: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(plan.title) }
    var note by rememberSaveable { mutableStateOf(plan.note ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑计划") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 100) title = it },
                    label = { Text("标题") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 500) note = it },
                    label = { Text("备注（可选）") },
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), note.trim().ifBlank { null })
                    }
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("删除") }
        },
    )
}
