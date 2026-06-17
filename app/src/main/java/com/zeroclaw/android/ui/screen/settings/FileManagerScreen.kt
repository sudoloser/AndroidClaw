package com.zeroclaw.android.ui.screen.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.R
import com.zeroclaw.android.ZeroClawApplication
import java.io.File

/**
 * Full-screen workspace file browser rooted at `filesDir/workspace`.
 *
 * Supports directory navigation, file creation/deletion, rename, move,
 * text editing, and file upload via the system document picker.
 *
 * @param edgeMargin Horizontal padding based on window width size class.
 * @param modifier Modifier applied to the root layout.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    edgeMargin: Dp,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as ZeroClawApplication }
    val dataDir = remember { context.filesDir }
    val workspaceDir = remember { File(dataDir, "workspace") }

    var currentDir by remember { mutableStateOf(workspaceDir) }
    var entries by remember(currentDir) { mutableStateOf(currentDir.listFiles()?.toList() ?: emptyList()) }

    var fileContent by remember { mutableStateOf<String?>(null) }
    var editingFile by remember { mutableStateOf<File?>(null) }
    var editText by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<File?>(null) }
    var renameTarget by remember { mutableStateOf<File?>(null) }
    var renameText by remember { mutableStateOf("") }
    var moveTarget by remember { mutableStateOf<File?>(null) }
    var moveDest by remember { mutableStateOf("") }
    var showCreateFile by remember { mutableStateOf(false) }
    var createFileName by remember { mutableStateOf("") }
    var showCreateFolder by remember { mutableStateOf(false) }
    var createFolderName by remember { mutableStateOf("") }

    val uploadLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                val fileName = getFileName(context, uri) ?: "uploaded_file"
                val destFile = File(currentDir, fileName)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                entries = currentDir.listFiles()?.toList() ?: emptyList()
                Toast
                    .makeText(context, context.getString(R.string.file_manager_upload_success), Toast.LENGTH_SHORT)
                    .show()
            } catch (e: Exception) {
                Toast
                    .makeText(
                        context,
                        context.getString(R.string.file_manager_upload_error) + ": " + e.message,
                        Toast.LENGTH_LONG,
                    ).show()
            }
        }

    val folderUploadLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            Toast
                .makeText(
                    context,
                    context.getString(R.string.file_manager_folder_import_hint),
                    Toast.LENGTH_LONG,
                ).show()
        }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = currentDir.absolutePath,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            navigationIcon = {
                if (currentDir.parentFile != null && currentDir != workspaceDir) {
                    IconButton(onClick = {
                        currentDir = currentDir.parentFile!!
                        entries = currentDir.listFiles()?.toList() ?: emptyList()
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "..")
                    }
                }
            },
            actions = {
                IconButton(onClick = { showCreateFile = true }) {
                    Icon(Icons.Filled.NoteAdd, contentDescription = "New File")
                }
                IconButton(onClick = { showCreateFolder = true }) {
                    Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder")
                }
                IconButton(onClick = { uploadLauncher.launch(arrayOf("*/*")) }) {
                    Icon(Icons.Filled.FileUpload, contentDescription = "Upload File")
                }
            },
        )

        HorizontalDivider()

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            val sorted =
                entries.sortedWith(
                    compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() },
                )
            items(sorted, key = { it.absolutePath }) { file ->
                FileItemRow(
                    file = file,
                    onNavigate = {
                        if (file.isDirectory) {
                            currentDir = file
                            entries = currentDir.listFiles()?.toList() ?: emptyList()
                        }
                    },
                    onEdit = {
                        if (file.isFile) {
                            try {
                                val text = file.readText()
                                editText = text
                                editingFile = file
                            } catch (e: Exception) {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.file_manager_file_read_error),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }
                    },
                    onRename = {
                        renameTarget = file
                        renameText = file.name
                    },
                    onMove = {
                        moveTarget = file
                        moveDest = file.parent
                    },
                    onDelete = { deleteTarget = file },
                )
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text(context.getString(R.string.file_manager_delete_confirm_title)) },
            text = {
                Text(
                    context.getString(
                        R.string.file_manager_delete_confirm_message,
                        target.name,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val ok = target.deleteRecursively()
                    deleteTarget = null
                    entries = currentDir.listFiles()?.toList() ?: emptyList()
                    val msg =
                        if (ok) {
                            context.getString(R.string.file_manager_delete_success)
                        } else {
                            context.getString(R.string.file_manager_delete_error)
                        }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }) {
                    Text(context.getString(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(context.getString(R.string.common_cancel))
                }
            },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(context.getString(R.string.file_manager_rename_title)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text(context.getString(R.string.file_manager_rename_label)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val newName = renameText.trim()
                    if (newName.isNotEmpty() && newName != target.name) {
                        val dest = File(target.parentFile, newName)
                        val ok = target.renameTo(dest)
                        if (ok) {
                            entries = currentDir.listFiles()?.toList() ?: emptyList()
                            Toast
                                .makeText(context, context.getString(R.string.file_manager_rename_success), Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            Toast
                                .makeText(context, context.getString(R.string.file_manager_rename_error), Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    renameTarget = null
                }) {
                    Text(context.getString(R.string.common_rename))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text(context.getString(R.string.common_cancel))
                }
            },
        )
    }

    moveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { moveTarget = null },
            title = { Text(context.getString(R.string.file_manager_move_title)) },
            text = {
                Column {
                    Text(context.getString(R.string.file_manager_move_description, target.name))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = moveDest,
                        onValueChange = { moveDest = it },
                        singleLine = true,
                        label = { Text(context.getString(R.string.file_manager_move_label)) },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val destPath = moveDest.trim()
                    if (destPath.isNotEmpty()) {
                        val destDir = File(destPath)
                        if (destDir.isDirectory || destDir.mkdirs()) {
                            val destFile = File(destDir, target.name)
                            val ok = target.renameTo(destFile)
                            if (ok) {
                                entries = currentDir.listFiles()?.toList() ?: emptyList()
                                Toast
                                    .makeText(context, context.getString(R.string.file_manager_move_success), Toast.LENGTH_SHORT)
                                    .show()
                            } else {
                                Toast
                                    .makeText(context, context.getString(R.string.file_manager_move_error), Toast.LENGTH_SHORT)
                                    .show()
                            }
                        } else {
                            Toast
                                .makeText(context, context.getString(R.string.file_manager_move_error), Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    moveTarget = null
                }) {
                    Text(context.getString(R.string.file_manager_move_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { moveTarget = null }) {
                    Text(context.getString(R.string.common_cancel))
                }
            },
        )
    }

    if (showCreateFile) {
        AlertDialog(
            onDismissRequest = { showCreateFile = false; createFileName = "" },
            title = { Text(context.getString(R.string.file_manager_create_file_title)) },
            text = {
                OutlinedTextField(
                    value = createFileName,
                    onValueChange = { createFileName = it },
                    singleLine = true,
                    label = { Text(context.getString(R.string.file_manager_create_file_label)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = createFileName.trim()
                    if (name.isNotEmpty()) {
                        val f = File(currentDir, name)
                        try {
                            f.createNewFile()
                            entries = currentDir.listFiles()?.toList() ?: emptyList()
                            Toast
                                .makeText(context, context.getString(R.string.file_manager_create_success), Toast.LENGTH_SHORT)
                                .show()
                        } catch (e: Exception) {
                            Toast
                                .makeText(
                                    context,
                                    context.getString(R.string.file_manager_create_error) + ": " + e.message,
                                    Toast.LENGTH_LONG,
                                ).show()
                        }
                    }
                    showCreateFile = false
                    createFileName = ""
                }) {
                    Text(context.getString(R.string.file_manager_create_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFile = false; createFileName = "" }) {
                    Text(context.getString(R.string.common_cancel))
                }
            },
        )
    }

    if (showCreateFolder) {
        AlertDialog(
            onDismissRequest = { showCreateFolder = false; createFolderName = "" },
            title = { Text(context.getString(R.string.file_manager_create_folder_title)) },
            text = {
                OutlinedTextField(
                    value = createFolderName,
                    onValueChange = { createFolderName = it },
                    singleLine = true,
                    label = { Text(context.getString(R.string.file_manager_create_folder_label)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = createFolderName.trim()
                    if (name.isNotEmpty()) {
                        val dir = File(currentDir, name)
                        val ok = dir.mkdir()
                        if (ok) {
                            entries = currentDir.listFiles()?.toList() ?: emptyList()
                            Toast
                                .makeText(context, context.getString(R.string.file_manager_create_success), Toast.LENGTH_SHORT)
                                .show()
                        } else {
                            Toast
                                .makeText(context, context.getString(R.string.file_manager_create_error), Toast.LENGTH_SHORT)
                                .show()
                        }
                    }
                    showCreateFolder = false
                    createFolderName = ""
                }) {
                    Text(context.getString(R.string.file_manager_create_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolder = false; createFolderName = "" }) {
                    Text(context.getString(R.string.common_cancel))
                }
            },
        )
    }

    if (editingFile != null) {
        AlertDialog(
            onDismissRequest = { editingFile = null },
            title = { Text(editingFile!!.name) },
            text = {
                OutlinedTextField(
                    value = editText,
                    onValueChange = { editText = it },
                    minLines = 12,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    try {
                        editingFile!!.writeText(editText)
                        entries = currentDir.listFiles()?.toList() ?: emptyList()
                        Toast
                            .makeText(context, context.getString(R.string.file_manager_save_success), Toast.LENGTH_SHORT)
                            .show()
                    } catch (e: Exception) {
                        Toast
                            .makeText(
                                context,
                                context.getString(R.string.file_manager_save_error) + ": " + e.message,
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                    editingFile = null
                }) {
                    Text(context.getString(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingFile = null }) {
                    Text(context.getString(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun FileItemRow(
    file: File,
    onNavigate: () -> Unit,
    onEdit: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Icon(
            imageVector =
                if (file.isDirectory) Icons.Filled.Folder
                else if (file.name.endsWith(".toml")) Icons.Filled.Description
                else Icons.Filled.InsertDriveFile,
            contentDescription = null,
            tint =
                if (file.isDirectory) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = file.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (file.isDirectory) {
            IconButton(onClick = onNavigate) {
                Icon(
                    Icons.Filled.Folder,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (file.isFile) {
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Edit")
            }
        }
        IconButton(onClick = onRename) {
            Icon(
                Icons.Filled.DriveFileRenameOutline,
                contentDescription = "Rename",
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        IconButton(onClick = onMove) {
            Icon(
                Icons.Filled.FileUpload,
                contentDescription = "Move",
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Delete ${file.name}",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun getFileName(
    context: android.content.Context,
    uri: Uri,
): String? {
    var name: String? = null
    val cursor =
        context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = it.getString(idx)
        }
    }
    if (name == null) {
        name = uri.lastPathSegment?.substringAfterLast('/')
    }
    return name
}
