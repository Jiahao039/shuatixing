package com.shutixing.app.ui.importwizard

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.domain.model.Question
import com.shutixing.app.parser.model.ParseWarning
import com.shutixing.app.ui.bankdetail.QuestionEditDialog
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.PillChip
import com.shutixing.app.ui.components.ProgressRing
import com.shutixing.app.ui.components.QuestionTypeBadge
import com.shutixing.app.ui.theme.CorrectGreen

@Composable
fun ImportScreen(
    viewModel: ImportViewModel,
    onDone: (String?) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var editingIndex by remember { mutableStateOf<Int?>(null) }

    val pickLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.parse(uri, queryDisplayName(context, uri))
        }
    }

    when (state.phase) {
        ImportPhase.IDLE -> IdleContent { pickLauncher.launch(arrayOf("application/pdf")) }
        ImportPhase.PARSING -> ParsingContent(state)
        ImportPhase.REVIEW -> ReviewContent(
            state = state,
            onPick = { pickLauncher.launch(arrayOf("application/pdf")) },
            onToggle = viewModel::toggleInclude,
            onEdit = { editingIndex = it },
            onBankName = viewModel::setBankName,
            onSelectBank = viewModel::selectTarget,
            onSkipDuplicates = viewModel::setSkipDuplicates,
            onImport = viewModel::importQuestions,
        )
        ImportPhase.IMPORTING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        ImportPhase.DONE -> DoneContent(state, onDone = { onDone(state.importedBankId) }, onAgain = viewModel::reset)
        ImportPhase.ERROR -> ErrorContent(state.error.orEmpty(), viewModel::reset)
    }

    editingIndex?.let { index ->
        val item = state.items.getOrNull(index)
        if (item != null) {
            val draft = Question(
                id = "draft-$index",
                bankId = "",
                type = item.question.type,
                stem = item.question.stem,
                options = item.question.options,
                answer = item.question.answer,
                explanation = item.question.explanation,
            )
            QuestionEditDialog(
                initial = draft,
                onDismiss = { editingIndex = null },
                onSave = { parsed ->
                    viewModel.editItem(index, parsed)
                    editingIndex = null
                },
            )
        }
    }
}

@Composable
private fun IdleContent(onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.PictureAsPdf,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("导入 PDF 题库", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "支持文字版 PDF，自动识别题目、选项、答案和解析；导入前可逐题校对。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        GradientButton("选择 PDF 文件", onClick = onPick, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun ParsingContent(state: ImportUiState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ProgressRing(progress = state.progress, diameter = 160.dp, strokeWidth = 14.dp) {
            Text(
                "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("正在解析 PDF…", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            state.fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReviewContent(
    state: ImportUiState,
    onPick: () -> Unit,
    onToggle: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onBankName: (String) -> Unit,
    onSelectBank: (String?) -> Unit,
    onSkipDuplicates: (Boolean) -> Unit,
    onImport: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text("校对题目", style = MaterialTheme.typography.headlineLarge)
            Text(
                "识别 ${state.items.size} 题 · 重复 ${state.duplicateCount} 题 · 已选 ${state.selectedCount} 题",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.bankName,
                onValueChange = onBankName,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("题库名称") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                enabled = state.targetBankId == null,
            )
            if (state.banks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.targetBankId == null,
                        onClick = { onSelectBank(null) },
                        label = { Text("新建题库") },
                        shape = RoundedCornerShape(999.dp),
                    )
                    state.banks.forEach { bank ->
                        FilterChip(
                            selected = state.targetBankId == bank.id,
                            onClick = { onSelectBank(bank.id) },
                            label = { Text(bank.name) },
                            shape = RoundedCornerShape(999.dp),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("跳过重复题目", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                Switch(checked = state.skipDuplicates, onCheckedChange = onSkipDuplicates)
            }
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(state.items) { index, item ->
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Checkbox(checked = item.include, onCheckedChange = { onToggle(index) })
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                QuestionTypeBadge(item.question.type)
                                if (item.duplicate) {
                                    Spacer(Modifier.width(6.dp))
                                    PillChip(
                                        "重复",
                                        container = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                                        contentColor = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                item.question.stem.ifBlank { "（空题干）" },
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            val warnings = item.question.warnings
                            if (warnings.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    warnings.joinToString(" · ", transform = ::warningLabel),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        IconButton(onClick = { onEdit(index) }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "编辑")
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedButton(
                onClick = onPick,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) { Text("重新选择") }
            GradientButton(
                text = "导入 ${state.selectedCount} 题",
                onClick = onImport,
                enabled = state.selectedCount > 0,
                modifier = Modifier.weight(2f),
            )
        }
    }
}

@Composable
private fun DoneContent(state: ImportUiState, onDone: () -> Unit, onAgain: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = CorrectGreen,
        )
        Spacer(Modifier.height(16.dp))
        Text("导入成功", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            "已导入 ${state.importedCount} 道题",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        GradientButton("完成", onClick = onDone, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onAgain) { Text("继续导入其他题库") }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Rounded.PictureAsPdf,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(16.dp))
        Text("解析失败", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        GradientButton("重新选择文件", onClick = onRetry, modifier = Modifier.fillMaxWidth())
    }
}

private fun warningLabel(warning: ParseWarning): String = when (warning) {
    ParseWarning.MISSING_ANSWER -> "缺答案"
    ParseWarning.INVALID_ANSWER -> "答案无效"
    ParseWarning.ANSWER_NOT_IN_OPTIONS -> "答案不在选项"
    ParseWarning.MISSING_OPTIONS -> "缺选项"
    ParseWarning.EMPTY_STEM -> "题干为空"
}

private fun queryDisplayName(context: Context, uri: Uri): String {
    context.contentResolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index)
        }
    }
    return "题库.pdf"
}
