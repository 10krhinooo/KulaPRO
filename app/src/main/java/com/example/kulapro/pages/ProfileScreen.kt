package com.example.kulapro.pages

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.AddBusiness
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.example.kulapro.BottomNavigationBar
import com.example.kulapro.Routes
import com.example.kulapro.data.model.OwnershipRequest
import com.example.kulapro.data.repository.AuthRepository
import com.example.kulapro.data.repository.AuthRepositoryFirebase
import com.example.kulapro.data.repository.OwnershipRepository
import com.example.kulapro.data.repository.OwnershipRepositoryFirestore
import com.example.kulapro.data.repository.ProfileRepository
import com.example.kulapro.data.repository.Result
import com.example.kulapro.feature.ownership.MyRequestsCard
import com.example.kulapro.ui.components.KulaPasswordField
import com.example.kulapro.ui.components.KulaTextField
import com.example.kulapro.ui.components.MessageHost
import com.example.kulapro.ui.components.PrimaryButton
import com.example.kulapro.ui.components.ProfileAvatar
import com.example.kulapro.ui.components.SecondaryButton
import com.example.kulapro.ui.components.SignInPrompt
import com.example.kulapro.ui.components.rememberMessageHostState
import com.example.kulapro.ui.components.report
import com.example.kulapro.ui.theme.Motion
import com.example.kulapro.util.Validators
import kotlinx.coroutines.launch

/**
 * Profile and credential management.
 *
 * Each credential change is its own collapsible section rather than every field being visible
 * at once, which is what made the first version read as a form with no clear task. Firebase
 * refuses credential changes without a recent sign-in, so both sections ask for the current
 * password and re-authenticate before acting.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage(
    navController: NavController,
    onSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    onListRestaurant: () -> Unit = {},
    /** Null unless this user's claims name a restaurant they may run. */
    onOpenHosting: (() -> Unit)? = null,
    /** Null for everyone who does not run the platform, which is almost everyone. */
    onOpenAdmin: (() -> Unit)? = null,
    appContext: android.content.Context = LocalContext.current.applicationContext,
    authRepository: AuthRepository = remember { AuthRepositoryFirebase(appContext) },
    profileRepository: ProfileRepository = authRepository as ProfileRepository,
    ownershipRepository: OwnershipRepository = remember { OwnershipRepositoryFirestore() },
) {
    val scope = rememberCoroutineScope()
    val messages = rememberMessageHostState()
    // Collected rather than read once, so signing out immediately returns this screen to
    // its guest state instead of showing a stale account.
    val signedInUserId by authRepository.authState()
        .collectAsStateWithLifecycle(initialValue = authRepository.currentUserId)
    val email = signedInUserId?.let { authRepository.currentUserEmail }

    // Keyed on the signed-in user so signing out clears the previous account's requests
    // rather than showing them to whoever signs in next.
    val myRequests by produceState(
        initialValue = emptyList<OwnershipRequest>(),
        signedInUserId,
    ) {
        value = emptyList()
        if (signedInUserId != null) {
            ownershipRepository.myRequests().collect { value = it }
        }
    }

    var expandedSection by remember { mutableStateOf<String?>(null) }
    var isBusy by remember { mutableStateOf(false) }
    var isUploadingPhoto by remember { mutableStateOf(false) }

    val profile by profileRepository.profileFlow().collectAsStateWithLifecycle(initialValue = null)

    // PickVisualMedia goes through the system photo picker, so the app never needs
    // READ_MEDIA_IMAGES and the user only ever shares the single image they chose.
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isUploadingPhoto = true
        scope.launch {
            val result = profileRepository.updateProfilePhoto(uri)
            isUploadingPhoto = false
            if (result is Result.Failure) {
                messages.showError(result.message)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            BottomNavigationBar(navController = navController, currentRoute = Routes.PROFILE)
        },
        topBar = { TopAppBar(title = { Text("Profile") }) },
        snackbarHost = { MessageHost(messages) },
    ) { padding ->
        if (email == null) {
            // A guest still gets the app links. Settings holds the theme and the reminder
            // preferences, and neither of those is anybody's account, so putting them
            // behind a sign-in wall would take away a setting for no reason.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SignInPrompt(
                    title = "Sign in to manage your profile",
                    description = "Add a photo, keep your details up to date, " +
                        "and review the places you have been.",
                    onSignIn = onSignIn,
                )
                AppLinks(navController = navController)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileAvatar(
                photoUrl = profile?.photoUrl.orEmpty(),
                fallbackText = profile?.displayName?.ifBlank { null } ?: email,
                uploading = isUploadingPhoto,
                onEditClick = {
                    photoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )

            Text(
                text = profile?.displayName?.ifBlank { null } ?: email ?: "Not signed in",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = email.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            AnimatedVisibility(visible = profile?.photoUrl.orEmpty().isNotBlank()) {
                TextButton(
                    onClick = {
                        scope.launch {
                            isUploadingPhoto = true
                            val result = profileRepository.removeProfilePhoto()
                            isUploadingPhoto = false
                            if (result is Result.Failure) {
                                messages.showError(result.message)
                            }
                        }
                    },
                ) { Text("Remove photo") }
            }

            ExpandableSection(
                title = "Your details",
                subtitle = "Name and phone number",
                icon = Icons.Rounded.Person,
                expanded = expandedSection == SECTION_DETAILS,
                onToggle = {
                    expandedSection =
                        if (expandedSection == SECTION_DETAILS) null else SECTION_DETAILS
                },
            ) {
                DetailsForm(
                    initialName = profile?.displayName.orEmpty(),
                    initialPhone = profile?.phone.orEmpty(),
                    isBusy = isBusy,
                    onSubmit = { name, phone ->
                        isBusy = true
                        scope.launch {
                            val result = profileRepository.updateProfileDetails(name, phone)
                            isBusy = false
                            messages.report(result, "Details saved")
                            if (result is Result.Success) expandedSection = null
                        }
                    },
                )
            }

            ExpandableSection(
                title = "Change email",
                subtitle = "Send a confirmation to a new address",
                icon = Icons.Rounded.Email,
                expanded = expandedSection == SECTION_EMAIL,
                onToggle = {
                    expandedSection = if (expandedSection == SECTION_EMAIL) null else SECTION_EMAIL
                },
            ) {
                ChangeEmailForm(
                    isBusy = isBusy,
                    onSubmit = { newEmail, currentPassword ->
                        isBusy = true
                        scope.launch {
                            val result = authRepository.updateEmail(newEmail, currentPassword)
                            isBusy = false
                            // The address only changes once the new inbox is confirmed,
                            // so do not claim it already has.
                            messages.report(
                                result,
                                "Check $newEmail to confirm the change",
                            )
                            if (result is Result.Success) expandedSection = null
                        }
                    },
                )
            }

            ExpandableSection(
                title = "Change password",
                subtitle = "Set a new password for this account",
                icon = Icons.Rounded.Lock,
                expanded = expandedSection == SECTION_PASSWORD,
                onToggle = {
                    expandedSection =
                        if (expandedSection == SECTION_PASSWORD) null else SECTION_PASSWORD
                },
            ) {
                ChangePasswordForm(
                    isBusy = isBusy,
                    onSubmit = { newPassword, currentPassword ->
                        isBusy = true
                        scope.launch {
                            val result =
                                authRepository.updatePassword(newPassword, currentPassword)
                            isBusy = false
                            messages.report(result, "Password changed")
                            if (result is Result.Success) expandedSection = null
                        }
                    },
                )
            }

            MyRequestsCard(requests = myRequests)

            // The three views of this app, entered from here rather than from Home. Each is
            // offered only to someone whose claims already allow it, so the app never shows
            // a door the security rules would refuse to open.
            onOpenHosting?.let { hosting ->
                NavigationRow(
                    title = "Restaurant hosting",
                    subtitle = "Tonight's covers, bookings, menu and capacity",
                    icon = Icons.Outlined.Storefront,
                    onClick = hosting,
                )
            }

            onOpenAdmin?.let { admin ->
                NavigationRow(
                    title = "Admin console",
                    subtitle = "Ownership requests and how the platform is running",
                    icon = Icons.Outlined.AdminPanelSettings,
                    onClick = admin,
                )
            }

            // Offered to everyone: most diners never tap it, and the ones who do are the
            // restaurants this app is short of.
            NavigationRow(
                title = "List your restaurant",
                subtitle = "Claim a listing or add one that is missing",
                icon = Icons.Outlined.AddBusiness,
                onClick = onListRestaurant,
            )

            AppLinks(navController = navController)

            SecondaryButton(
                text = "Sign out",
                onClick = {
                    authRepository.signOut()
                    // Signing out returns the user to browsing as a guest rather than to a
                    // sign-in wall they cannot get past.
                    navController.navigate(Routes.HOME) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                },
            )
        }
    }
}

/**
 * Settings and About.
 *
 * Both used to sit as icons in Home's top bar, beside the wordmark and the search button,
 * where they competed for room with the one control that screen is actually for. Neither is
 * about finding somewhere to eat, and both are about the person using the app, so they
 * belong on the screen that already is.
 */
@Composable
private fun AppLinks(navController: NavController, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        NavigationRow(
            title = "Settings",
            subtitle = "Theme, colour and booking reminders",
            icon = Icons.Rounded.Settings,
            onClick = { navController.navigate(Routes.SETTINGS) },
        )
        NavigationRow(
            title = "About",
            subtitle = "Version, licences and what this app is for",
            icon = Icons.Rounded.Info,
            onClick = { navController.navigate(Routes.ABOUT) },
        )
    }
}

/** A place to go, named and explained, with the same shape as the sections above it. */
@Composable
private fun NavigationRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) CHEVRON_EXPANDED_DEGREES else 0f,
        animationSpec = Motion.smooth(),
        label = "chevronRotation",
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun DetailsForm(
    initialName: String,
    initialPhone: String,
    isBusy: Boolean,
    onSubmit: (String, String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    var phone by remember(initialPhone) { mutableStateOf(initialPhone) }

    KulaTextField(
        value = name,
        onValueChange = { name = it },
        label = "Display name",
        helperText = "Shown on reviews you leave",
        enabled = !isBusy,
    )
    KulaTextField(
        value = phone,
        onValueChange = { phone = it },
        label = "Phone number",
        helperText = "Restaurants use this if they need to reach you",
        keyboardType = KeyboardType.Phone,
        enabled = !isBusy,
    )
    PrimaryButton(
        text = "Save details",
        loadingText = "Saving",
        loading = isBusy,
        onClick = { onSubmit(name.trim(), phone.trim()) },
    )
}

@Composable
private fun ChangeEmailForm(isBusy: Boolean, onSubmit: (String, String) -> Unit) {
    var newEmail by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }

    KulaTextField(
        value = newEmail,
        onValueChange = {
            newEmail = it
            emailError = null
        },
        label = "New email address",
        error = emailError,
        keyboardType = KeyboardType.Email,
        enabled = !isBusy,
    )
    KulaPasswordField(
        value = currentPassword,
        onValueChange = { currentPassword = it },
        label = "Current password",
        helperText = "Needed to confirm it is you",
        enabled = !isBusy,
    )
    PrimaryButton(
        text = "Send confirmation",
        loadingText = "Sending",
        loading = isBusy,
        enabled = newEmail.isNotBlank() && currentPassword.isNotBlank(),
        onClick = {
            emailError = Validators.emailError(newEmail)
            if (emailError == null) onSubmit(newEmail.trim(), currentPassword)
        },
    )
}

@Composable
private fun ChangePasswordForm(isBusy: Boolean, onSubmit: (String, String) -> Unit) {
    var newPassword by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    KulaPasswordField(
        value = currentPassword,
        onValueChange = { currentPassword = it },
        label = "Current password",
        enabled = !isBusy,
    )
    KulaPasswordField(
        value = newPassword,
        onValueChange = {
            newPassword = it
            passwordError = null
        },
        label = "New password",
        error = passwordError,
        enabled = !isBusy,
    )
    AnimatedVisibility(visible = newPassword.isNotEmpty()) {
        com.example.kulapro.ui.components.PasswordStrengthMeter(password = newPassword)
    }
    PrimaryButton(
        text = "Update password",
        loadingText = "Updating",
        loading = isBusy,
        enabled = newPassword.isNotBlank() && currentPassword.isNotBlank(),
        onClick = {
            passwordError = Validators.passwordError(newPassword)
            if (passwordError == null) onSubmit(newPassword, currentPassword)
        },
    )
}

private const val SECTION_DETAILS = "details"
private const val SECTION_EMAIL = "email"
private const val SECTION_PASSWORD = "password"
private const val CHEVRON_EXPANDED_DEGREES = 180f
