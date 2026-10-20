package com.shutixing.app.ui.bankdetail

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shutixing.app.data.export.BankExporter
import com.shutixing.app.domain.answer.AnswerChecker
import com.shutixing.app.domain.model.Option
import com.shutixing.app.domain.model.Question
import com.shutixing.app.domain.model.QuestionType
import com.shutixing.app.parser.model.ParsedQuestion
import com.shutixing.app.ui.components.AppCard
import com.shutixing.app.ui.components.GradientButton
import com.shutixing.app.ui.components.QuestionTypeBadge
import com.shutixing.app.ui.theme.Amber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun BankDetailScreen(
    viewModel: BankDetailViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editorTarget by remember { mutableStateOf<Question?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showDeleteBank by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val data = viewModel.exportData()
                if (data != null) {
                    runCatching {
                        withContext(Dispatchers.IO) {
                            context.contentResolver.openOutputStream(uri)?.use { stream ->
                                BankExporter.write(stream, data.first, data.second)
                            }
                        }
                    }.onSuccess {
                        Toast.makeText(context, "题库已导出", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, contentDescription = "返回") }
            Column(Modifier.weight(1f)) {
                Text(
                    state.bankName.ifBlank { "题库" },
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${state.questions.size} 道题",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { editorTarget = null; showAdd = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "添加题目")
            }
            IconButton(
                onClick = { exportLauncher.launch("${state.bankName.ifBlank { "题库" }}.shuatixing.json") },
            ) {
                Icon(Icons.Rounded.FileDownload, contentDescription = "导出题库")
            }
            IconButton(onClick = { showDeleteBank = true }) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = "删除题库")
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索题目") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DetailFilterChip("全部", state.filter == DetailFilter.ALL) { viewModel.setFilter(DetailFilter.ALL) }
                DetailFilterChip("收藏", state.filter == DetailFilter.FAVORITE) { viewModel.setFilter(DetailFilter.FAVORITE) }
                DetailFilterChip("单选", state.filter == DetailFilter.SINGLE) { viewModel.setFilter(DetailFilter.SINGLE) }
                DetailFilterChip("多选", state.filter == DetailFilter.MULTIPLE) { viewModel.setFilter(DetailFilter.MULTIPLE) }
                DetailFilterChip("判断", state.filter == DetailFilter.JUDGE) { viewModel.setFilter(DetailFilter.JUDGE) }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (state.filtered.isEmpty()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (state.questions.isEmpty()) "题库还是空的，点击右上角添加题目" else "没有匹配的题目",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.filtered, key = { it.id }) { question ->
                    AppCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editorTarget = question },
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                QuestionTypeBadge(question.type)
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    question.stem,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "答案：${displayAnswer(question)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { viewModel.toggleFavorite(question) }) {
                                Icon(
                                    imageVector = if (question.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                    contentDescription = "收藏",
                                    tint = if (question.isFavorite) Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (editorTarget != null) {
        val target = editorTarget!!
        QuestionEditDialog(
            initial = target,
            onDismiss = { editorTarget = null },
            onSave = { parsed ->
                viewModel.updateQuestion(
                    target.copy(
                        type = parsed.type,
                        stem = parsed.stem,
                        options = parsed.options,
                        answer = parsed.answer,
                        explanation = parsed.explanation,
                    ),
                )
                editorTarget = null
            },
            onDelete = {
                viewModel.deleteQuestion(target.id)
                editorTarget = null
            },
        )
    }

    if (showAdd) {
        QuestionEditDialog(
            initial = null,
            onDismiss = { showAdd = false },
            onSave = { parsed ->
                viewModel.addQuestion(parsed)
                showAdd = false
            },
        )
    }

    if (showDeleteBank) {
        AlertDialog(
            onDismissRequest = { showDeleteBank = false },
            title = { Text("删除题库？") },
            text = { Text("题库和其中的刷题记录会一并删除，无法恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteBank = false
                        viewModel.deleteBank(onBack)
                    },
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBank = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DetailFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(999.dp),
        border = null,
    )
}

@Composable
fun QuestionEditDialog(
    initial: Question?,
    onDismiss: () -> Unit,
    onSave: (ParsedQuestion) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val initialOptions = initial?.options?.takeIf { it.isNotEmpty() }
        ?: listOf(Option("A", ""), Option("B", ""), Option("C", ""), Option("D", ""))
    val options = remember(initial?.id) {
        mutableStateListOf<Option>().apply { addAll(initialOptions) }
    }
    var type by remember(initial?.id) { mutableStateOf(initial?.type ?: QuestionType.SINGLE) }
    var stem by remember(initial?.id) { mutableStateOf(initial?.stem.orEmpty()) }
    var answer by remember(initial?.id) {
        mutableStateOf(
            when (initial?.answer) {
                "T" -> "对"
                "F" -> "错"
                else -> initial?.answer.orEmpty()
            },
        )
    }
    var explanation by remember(initial?.id) { mutableStateOf(initial?.explanation.orEmpty()) }
    var errorText by remember(initial?.id) { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 640.dp),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    if (initial == null) "添加题目" else "编辑题目",
                    style = MaterialTheme.typography.titleLarge,
                )
                OutlinedTextField(
                    value = stem,
                    onValueChange = { stem = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("题干") },
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuestionType.entries.forEach { entry ->
                        FilterChip(
                            selected = type == entry,
                            onClick = { type = entry },
                            label = {
                                Text(
                                    when (entry) {
                                        QuestionType.SINGLE -> "单选"
                                        QuestionType.MULTIPLE -> "多选"
                                        QuestionType.JUDGE -> "判断"
                                    },
                                )
                            },
                            shape = RoundedCornerShape(999.dp),
                        )
                    }
                }

                if (type != QuestionType.JUDGE) {
                    options.forEachIndexed { index, option ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(option.key, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = option.text,
                                onValueChange = { value ->
                                    options[index] = option.copy(text = value)
                                },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                placeholder = { Text("选项 ${option.key}") },
                            )
                            TextButton(
                                onClick = {
                                    options.removeAt(index)
                                    options.forEachIndexed { i, o ->
                                        options[i] = o.copy(key = ('A' + i).toString())
                                    }
                                },
                                enabled = options.size > 2,
                            ) { Text("删") }
                        }
                    }
                    if (options.size < 8) {
                        TextButton(
                            onClick = {
                                options.add(Option(('A' + options.size).toString(), ""))
                            },
                        ) { Text("添加选项") }
                    }
                }

                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("答案") },
                    supportingText = {
                        Text(
                            when (type) {
                                QuestionType.JUDGE -> "填写“对”或“错”"
                                QuestionType.MULTIPLE -> "多选请连写，例如 AC"
                                QuestionType.SINGLE -> "填写选项字母，例如 B"
                            },
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("解析（可选）") },
                    minLines = 2,
                    shape = RoundedCornerShape(14.dp),
                )
                errorText?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (onDelete != null) {
                        TextButton(onClick = onDelete) {
                            Text("删除题目", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        GradientButton(
                            text = "保存",
                            onClick = {
                                val normalized = AnswerChecker.normalize(type, answer)
                                val filled = options.map { Option(it.key, it.text.trim()) }.filter { it.text.isNotEmpty() }
                                val error = when {
                                    stem.isBlank() -> "题干不能为空"
                                    normalized.isEmpty() -> "请填写有效答案"
                                    type != QuestionType.JUDGE && filled.isEmpty() -> "请至少填写一个选项"
                                    type != QuestionType.JUDGE &&
                                        normalized.toSet().any { letter -> filled.none { it.key == letter.toString() } } ->
                                        "答案字母不在选项中"
                                    else -> null
                                }
                                if (error != null) {
                                    errorText = error
                                } else {
                                    onSave(
                                        ParsedQuestion(
                                            type = type,
                                            stem = stem.trim(),
                                            options = if (type == QuestionType.JUDGE) emptyList() else filled,
                                            answer = normalized,
                                            explanation = explanation.trim().takeIf { it.isNotEmpty() },
                                        ),
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun displayAnswer(question: Question): String =
    if (question.type == QuestionType.JUDGE) {
        if (question.answer == "T") "正确" else "错误"
    } else {
        question.answer.toList().joinToString("、")
    }
