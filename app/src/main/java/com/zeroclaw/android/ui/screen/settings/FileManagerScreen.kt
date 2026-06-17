package com.zeroclaw.android.ui.screen.settings

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zeroclaw.android.R
import com.zeroclaw.android.ZeroClawApplication
import java.io.File

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
    var deleteTarget by remember { mutableStateOf<File?>(null) }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = edgeMargin),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Filled.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = currentDir.absolutePath,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (currentDir.parentFile != null && currentDir != workspaceDir) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                    ) {
                        IconButton(onClick = {
                            currentDir = currentDir.parentFile!!
                            entries = currentDir.listFiles()?.toList() ?: emptyList()
                        }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "..")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("..")
                    }
                }
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        text = context.getString(R.string.file_manager_empty_dir),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }

            val sorted = entries.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            items(sorted, key = { it.absolutePath }) { file ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector =
                            if (file.isDirectory) {
                                Icons.Filled.Folder
                            } else if (file.name.endsWith(".toml")) {
                                Icons.Filled.Description
                            } else {
                                Icons.Filled.InsertDriveFile
                            },
                        contentDescription = null,
                        tint =
                            if (file.isDirectory) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { deleteTarget = file }) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "Delete ${file.name}",
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (file.isFile) {
                        TextButton(onClick = {
                            try {
                                fileContent = file.readText()
                            } catch (e: Exception) {
                                Toast
                                    .makeText(
                                        context,
                                        context.getString(R.string.file_manager_file_read_error),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            }
                        }) {
                            Text("View")
                        }
                    }
                }
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
                    val ok = target.delete()
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

    fileContent?.let { content ->
        AlertDialog(
            onDismissRequest = { fileContent = null },
            title = { Text("File Content") },
            text = {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 40,
                )
            },
            confirmButton = {
                TextButton(onClick = { fileContent = null }) {
                    Text(context.getString(R.string.common_close))
                }
            },
        )
    }
}
