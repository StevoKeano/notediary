package com.notediary

import android.app.DatePickerDialog
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DiaryViewModel(private val dao: DiaryDao) : ViewModel() {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<DiaryEntry>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) dao.getAllEntries()
            else dao.search(sanitizeQuery(query))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun sanitizeQuery(query: String): String {
        return query.trim()
            .replace("'", "''")
            .split("\\s+".toRegex())
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
    }

    fun saveEntry(id: Long?, date: String, content: String) {
        viewModelScope.launch {
            if (id == null || id == 0L) {
                dao.insert(DiaryEntry(date = date, content = content.trim()))
            } else {
                dao.update(DiaryEntry(id = id, date = date, content = content.trim()))
            }
        }
    }

    fun deleteEntry(entry: DiaryEntry) {
        viewModelScope.launch { dao.delete(entry) }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}

class DiaryViewModelFactory(private val dao: DiaryDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return DiaryViewModel(dao) as T
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = DiaryDatabase.getInstance(this)
        val dao = db.diaryDao()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DiaryApp(dao)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryApp(dao: DiaryDao) {
    val viewModel: DiaryViewModel = viewModel(factory = DiaryViewModelFactory(dao))
    val entries by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var selectedEntryId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(getToday()) }
    var content by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(if (isEditing) "Edit Entry" else "NoteDiary")
            },
            actions = {
                if (isEditing) {
                    TextButton(onClick = {
                        selectedEntryId = null
                        date = getToday()
                        content = ""
                        isEditing = false
                    }) {
                        Text("Cancel")
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            DateField(
                date = date,
                onDateChanged = { date = it }
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 150.dp),
                placeholder = { Text("Write your diary entry...") },
                maxLines = 20
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    if (content.isNotBlank()) {
                        viewModel.saveEntry(selectedEntryId, date, content)
                        selectedEntryId = null
                        date = getToday()
                        content = ""
                        isEditing = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = content.isNotBlank()
            ) {
                Text(if (isEditing) "Update" else "Save")
            }
        }

        Divider()

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            placeholder = { Text("Search entries by any word...") },
            singleLine = true
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
        ) {
            items(entries, key = { it.id }) { entry ->
                EntryCard(
                    entry = entry,
                    onClick = {
                        selectedEntryId = entry.id
                        date = entry.date
                        content = entry.content
                        isEditing = true
                    },
                    onDelete = { viewModel.deleteEntry(entry) }
                )
            }
        }
    }
}

@Composable
fun DateField(date: String, onDateChanged: (String) -> Unit) {
    val context = LocalContext.current
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    OutlinedTextField(
        value = date,
        onValueChange = {},
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val cal = Calendar.getInstance()
                try {
                    cal.time = sdf.parse(date) ?: Calendar.getInstance().time
                } catch (_: Exception) {
                }
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        onDateChanged("%04d-%02d-%02d".format(year, month + 1, day))
                    },
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH),
                    cal.get(Calendar.DAY_OF_MONTH)
                ).show()
            },
        label = { Text("Date") },
        readOnly = true,
        singleLine = true
    )
}

@Composable
fun EntryCard(entry: DiaryEntry, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.date,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.content,
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete entry"
                )
            }
        }
    }
}

private fun getToday(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return sdf.format(Date())
}
