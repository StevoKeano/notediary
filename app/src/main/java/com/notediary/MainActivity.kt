package com.notediary

import android.app.Application
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.ZoneOffset

class DiaryViewModel(application: Application, private val dao: DiaryDao) : AndroidViewModel(application) {
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _newEntryId = MutableStateFlow<Long?>(null)
    val newEntryId: StateFlow<Long?> = _newEntryId.asStateFlow()

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

    fun ensureEntryExists(entryId: Long?, date: String, content: String, onResult: (Long) -> Unit) {
        if (entryId != null && entryId != 0L) {
            onResult(entryId)
        } else if (content.isNotBlank()) {
            viewModelScope.launch {
                val newId = dao.insert(DiaryEntry(date = date, content = content.trim()))
                _newEntryId.value = newId
                onResult(newId)
            }
        }
    }

    fun deleteEntry(entry: DiaryEntry) {
        viewModelScope.launch {
            val images = dao.getImagesForEntry(entry.id)
            images.forEach { File(it.imagePath).delete() }
            dao.delete(entry)
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun addAttachment(entryId: Long, uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val path = saveFileToInternalStorage(uri, mimeType) ?: return@launch
            dao.insertImage(DiaryImage(entryId = entryId, imagePath = path, mimeType = mimeType))
        }
    }

    fun removeImage(image: DiaryImage) {
        viewModelScope.launch {
            File(image.imagePath).delete()
            dao.deleteImage(image)
        }
    }

    fun getImagesForEntry(entryId: Long) = dao.getImagesForEntryFlow(entryId)

    private fun saveFileToInternalStorage(uri: Uri, mimeType: String): String? {
        val context = getApplication<Application>()
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val ext = when {
            mimeType.startsWith("image/") -> mimeType.substringAfterLast("/", "jpg")
            else -> "bin"
        }
        val dir = File(context.filesDir, "attachments").also { it.mkdirs() }
        val file = File(dir, "file_${System.currentTimeMillis()}_${(0..9999).random()}.$ext")
        file.outputStream().use { output -> inputStream.copyTo(output) }
        return file.absolutePath
    }
}

class DiaryViewModelFactory(
    private val application: Application,
    private val dao: DiaryDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        return DiaryViewModel(application, dao) as T
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
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: DiaryViewModel = viewModel(factory = DiaryViewModelFactory(app, dao))
    val entries by viewModel.searchResults.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var selectedEntryId by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(getToday()) }
    var content by remember { mutableStateOf("") }
    var isEditing by remember { mutableStateOf(false) }
    var images by remember { mutableStateOf<List<DiaryImage>>(emptyList()) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<DiaryEntry?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.ensureEntryExists(selectedEntryId, date, content) { entryId ->
                selectedEntryId = entryId
                isEditing = true
                viewModel.addAttachment(entryId, it)
            }
        }
    }

    fun resetForm() {
        selectedEntryId = null
        date = getToday()
        content = ""
        isEditing = false
        images = emptyList()
    }

    fun loadEntry(entry: DiaryEntry) {
        selectedEntryId = entry.id
        date = entry.date
        content = entry.content
        isEditing = true
    }

    LaunchedEffect(selectedEntryId) {
        if (selectedEntryId != null) {
            viewModel.getImagesForEntry(selectedEntryId!!).collect { images = it }
        } else {
            images = emptyList()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.newEntryId.collect { id ->
            if (id != null) {
                selectedEntryId = id
                isEditing = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (isEditing) "Edit Entry" else "NoteDiary") },
            actions = {
                if (isEditing) {
                    TextButton(onClick = { resetForm() }) {
                        Text("Cancel")
                    }
                }
            }
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            DateField(date = date, onDateChanged = { date = it })
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp),
                placeholder = { Text("Write your diary entry...") },
                maxLines = 20
            )
            if (images.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                AttachmentGallery(
                    images = images,
                    onDelete = { viewModel.removeImage(it) }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (content.isNotBlank()) {
                            viewModel.saveEntry(selectedEntryId, date, content)
                            resetForm()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = content.isNotBlank()
                ) {
                    Text(if (isEditing) "Update" else "Save")
                }
                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) }
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Attach")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        HorizontalDivider()

        Column(modifier = Modifier.weight(1f)) {
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
                        onClick = { loadEntry(entry) },
                        onDelete = {
                            entryToDelete = entry
                            showDeleteConfirm = true
                        }
                    )
                }
            }
        }
    }

    if (showDeleteConfirm && entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Entry") },
            text = { Text("Delete this entry permanently? This cannot be undone.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteEntry(entryToDelete!!)
                    showDeleteConfirm = false
                    entryToDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(date: String, onDateChanged: (String) -> Unit) {
    var showDatePicker by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = date,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Date") },
            readOnly = true,
            singleLine = true
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable { showDatePicker = true }
        )
    }

    if (showDatePicker) {
        val initialMillis = try {
            java.time.LocalDate.parse(date).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: Exception) { null }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val text = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
                            .toString()
                        onDateChanged(text)
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
fun AttachmentGallery(images: List<DiaryImage>, onDelete: (DiaryImage) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        images.forEach { image ->
            Box(modifier = Modifier.size(100.dp)) {
                if (image.mimeType.startsWith("image/")) {
                    AsyncImage(
                        model = File(image.imagePath),
                        contentDescription = "Attachment",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = image.imagePath.substringAfterLast("/").substringBeforeLast("."),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
                IconButton(
                    onClick = { onDelete(image) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                            shape = RoundedCornerShape(12.dp)
                        )
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
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
    return java.time.LocalDate.now().toString()
}
