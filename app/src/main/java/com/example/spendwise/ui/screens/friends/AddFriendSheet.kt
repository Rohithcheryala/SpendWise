package com.example.spendwise.ui.screens.friends

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.PersonSearch
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.spendwise.core.contacts.Contact
import com.example.spendwise.ui.components.FriendAvatar

private enum class AddFriendMode { CONTACTS, NAME }

/**
 * Add-friend bottom sheet. Default mode reads the device address book (gated
 * behind the READ_CONTACTS runtime permission); the name tab keeps the manual
 * entry path. Tapping a contact creates a person counterparty with the phone
 * number saved as an alias.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddFriendSheet(
    contacts: List<Contact>,
    contactsLoading: Boolean,
    onLoadContacts: () -> Unit,
    onAdd: (String, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current

    var mode by remember { mutableStateOf(AddFriendMode.CONTACTS) }
    var name by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var hasReadContacts by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasReadContacts = granted
        if (granted) onLoadContacts()
    }

    LaunchedEffect(hasReadContacts) {
        if (hasReadContacts) onLoadContacts()
    }

    val filteredContacts = remember(contacts, query) {
        if (query.isBlank()) {
            contacts
        } else {
            val digits = query.filter { it.isDigit() }
            contacts.filter {
                it.name.contains(query, ignoreCase = true) ||
                    (digits.isNotEmpty() &&
                        it.phoneNumber.filter(Char::isDigit).contains(digits))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Add Friend",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = mode == AddFriendMode.CONTACTS,
                    onClick = {
                        mode = AddFriendMode.CONTACTS
                        if (hasReadContacts) onLoadContacts()
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) {
                    Text("From contacts")
                }
                SegmentedButton(
                    selected = mode == AddFriendMode.NAME,
                    onClick = { mode = AddFriendMode.NAME },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) {
                    Text("By name")
                }
            }

            Spacer(Modifier.height(12.dp))

            when (mode) {
                AddFriendMode.CONTACTS -> ContactsMode(
                    contacts = contacts,
                    filteredContacts = filteredContacts,
                    contactsLoading = contactsLoading,
                    hasReadContacts = hasReadContacts,
                    query = query,
                    onQueryChange = { query = it },
                    onAllowContacts = {
                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    },
                    onAdd = onAdd,
                )

                AddFriendMode.NAME -> NameMode(
                    name = name,
                    onNameChange = { name = it },
                    onAdd = onAdd,
                )
            }
        }
    }
}

@Composable
private fun ContactsMode(
    contacts: List<Contact>,
    filteredContacts: List<Contact>,
    contactsLoading: Boolean,
    hasReadContacts: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onAllowContacts: () -> Unit,
    onAdd: (String, String?) -> Unit
) {
    if (!hasReadContacts) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.PersonSearch,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
            Text(
                text = "Allow access to your contacts to pick " +
                    "friends straight from your address book.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Button(onClick = onAllowContacts) {
                Text("Allow contacts access")
            }
        }
        return
    }

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text("Search contacts...") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(Modifier.height(8.dp))

    when {
        contactsLoading && contacts.isEmpty() -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }

        filteredContacts.isEmpty() -> {
            Text(
                text = if (contacts.isEmpty()) "No contacts found."
                else "No contacts match \"$query\".",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
            )
        }

        else -> {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
            ) {
                items(
                    filteredContacts,
                    key = { "${it.id}-${it.phoneNumber}" }
                ) { contact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAdd(contact.name, contact.phoneNumber) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FriendAvatar(
                            name = contact.name,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (contact.phoneNumber.isNotBlank()) {
                                Text(
                                    text = contact.phoneNumber,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { onAdd(contact.name, contact.phoneNumber) }) {
                            Icon(
                                Icons.Outlined.PersonAdd,
                                contentDescription = "Add ${contact.name}"
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun NameMode(
    name: String,
    onNameChange: (String) -> Unit,
    onAdd: (String, String?) -> Unit
) {
    OutlinedTextField(
        value = name,
        onValueChange = onNameChange,
        label = { Text("Friend's Name") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
    Spacer(Modifier.height(16.dp))
    Button(
        onClick = { onAdd(name.trim(), null) },
        enabled = name.isNotBlank(),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Add")
    }
    Spacer(Modifier.height(24.dp))
}
