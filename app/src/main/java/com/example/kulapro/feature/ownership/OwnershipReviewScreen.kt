package com.example.kulapro.feature.ownership

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.kulapro.data.model.OwnershipRequest
import com.example.kulapro.data.model.RequestType
import com.example.kulapro.data.repository.OwnershipRepository
import com.example.kulapro.data.repository.OwnershipRepositoryFirestore
import com.example.kulapro.data.repository.Result
import com.example.kulapro.ui.components.AnimatedListItem
import com.example.kulapro.ui.components.EmptyState
import com.example.kulapro.ui.components.MessageHost
import com.example.kulapro.ui.components.rememberListEntryAnimator
import com.example.kulapro.ui.components.PrimaryButton
import com.example.kulapro.ui.components.SecondaryButton
import com.example.kulapro.ui.components.rememberMessageHostState
import java.text.DateFormat
import kotlinx.coroutines.launch

/**
 * Where ownership requests land, and where they are decided.
 *
 * Reachable only when the signed-in user's claim says they review. That claim is the one
 * thing the app cannot grant, which is the point: everything else can be handed out from in
 * here, so the right to hand things out has to come from outside.
 *
 * Approving writes the owner onto the restaurant, and for a new listing creates it first.
 * The requester can manage it from their next read, with no script and no deploy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OwnershipReviewScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    repository: OwnershipRepository = remember { OwnershipRepositoryFirestore() },
) {
    val scope = rememberCoroutineScope()
    val messages = rememberMessageHostState()
    var busyRequestId by remember { mutableStateOf<String?>(null) }
    var rejecting by remember { mutableStateOf<OwnershipRequest?>(null) }
    val entryAnimator = rememberListEntryAnimator()

    val requests by produceState(initialValue = emptyList<OwnershipRequest>(), repository) {
        repository.pendingRequests().collect { value = it }
    }

    rejecting?.let { request ->
        RejectDialog(
            request = request,
            onDismiss = { rejecting = null },
            onReject = { note ->
                rejecting = null
                busyRequestId = request.id
                scope.launch {
                    when (val result = repository.reject(request, note)) {
                        is Result.Success -> messages.showSuccess("Request declined.")
                        is Result.Failure -> messages.showError(result.message)
                    }
                    busyRequestId = null
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { MessageHost(messages) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Ownership requests")
                        Text(
                            text = if (requests.isEmpty()) {
                                "Nothing waiting"
                            } else {
                                "${requests.size} waiting on you"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    // Absent when this is a tab of the admin portal rather than a push from
                    // the profile screen, where there is somewhere to go back to.
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (requests.isEmpty()) {
            EmptyState(
                title = "No requests waiting",
                description = "When someone asks to manage a restaurant it will appear here.",
                icon = Icons.Outlined.Inbox,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(requests, key = { _, pending -> pending.id }) { index, request ->
                AnimatedListItem(index = index, key = request.id, animator = entryAnimator) {
                    RequestCard(
                        request = request,
                        isBusy = busyRequestId == request.id,
                        onApprove = {
                            busyRequestId = request.id
                            scope.launch {
                                when (val result = repository.approve(request)) {
                                    is Result.Success -> messages.showSuccess(
                                        "${request.restaurantName.ifBlank { "The restaurant" }} " +
                                            "is now managed by ${request.displayName}.",
                                    )

                                    is Result.Failure -> messages.showError(result.message)
                                }
                                busyRequestId = null
                            }
                        },
                        onReject = { rejecting = request },
                    )
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    request: OwnershipRequest,
    isBusy: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val dateFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = request.restaurantName.ifBlank { "Unnamed restaurant" },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TypeBadge(request.typeEnum)
            }

            Text(
                text = dateFormat.format(request.createdAt.toDate()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Detail("From", "${request.displayName} (${request.userEmail})")
            Detail("Says they are", request.role)
            Detail("Reachable on", request.contactPhone)
            if (request.evidence.isNotBlank()) {
                Detail("To confirm it", request.evidence)
            }

            request.proposed?.let { proposed ->
                Detail("Address", proposed.address)
                if (proposed.cuisine.isNotBlank()) Detail("Cuisine", proposed.cuisine)
                if (proposed.capacityPerSlot > 0) {
                    Detail("Seats a sitting", "${proposed.capacityPerSlot}")
                }
                if (proposed.description.isNotBlank()) {
                    Detail("Description", proposed.description)
                }
            }

            Text(
                text = if (request.typeEnum == RequestType.NEW_LISTING) {
                    "Approving creates this listing and puts them in charge of it."
                } else {
                    "Approving puts them in charge of the existing listing."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )

            PrimaryButton(
                text = "Approve",
                loadingText = "Approving",
                loading = isBusy,
                onClick = onApprove,
            )
            SecondaryButton(text = "Decline", onClick = onReject, enabled = !isBusy)
        }
    }
}

@Composable
private fun Detail(label: String, value: String) {
    if (value.isBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(LABEL_WEIGHT),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(VALUE_WEIGHT),
        )
    }
}

@Composable
private fun TypeBadge(type: RequestType) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (type == RequestType.NEW_LISTING) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
    ) {
        Text(
            text = if (type == RequestType.NEW_LISTING) "New listing" else "Claim",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = if (type == RequestType.NEW_LISTING) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/**
 * Declining asks for a reason.
 *
 * The note goes back to the requester. A refusal with no reason gives them nothing to fix
 * and guarantees they simply ask again.
 */
@Composable
private fun RejectDialog(
    request: OwnershipRequest,
    onDismiss: () -> Unit,
    onReject: (String) -> Unit,
) {
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Decline this request") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${request.displayName} will see what you write here, so say what " +
                        "would change your mind.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Reason") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onReject(note) },
                enabled = note.isNotBlank(),
            ) { Text("Decline") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private const val LABEL_WEIGHT = 0.4f
private const val VALUE_WEIGHT = 0.6f
