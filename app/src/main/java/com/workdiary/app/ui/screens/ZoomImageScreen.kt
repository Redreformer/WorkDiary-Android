package com.workdiary.app.ui.screens

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.Image
import com.workdiary.app.data.models.ZoomItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Full-screen photo viewer composable.
 *
 * Mirrors `ZoomableImageView.swift` + `ZoomableScrollView.swift`.
 *
 * - Displays a horizontally-paged set of the three photo slots for a given day.
 * - The viewer opens at the slot index stored in [item].
 * - Each slot supports **pinch-to-zoom**, **pan** (when zoomed), and **double-tap** to toggle zoom.
 * - A **Delete** button deletes the currently-visible photo slot.
 * - A **Close** button dismisses the viewer.
 *
 * ### Navigation strategy
 * The [ZoomImageScreen] is shown as a full-screen [Dialog] (equivalent to iOS `fullScreenCover`),
 * controlled by the [CalendarViewModel]'s `selectedZoomItem` state.
 *
 * @param item        The tapped [ZoomItem] (carries the start page index).
 * @param allBitmaps  All three per-day bitmaps (null = empty slot). Size must be exactly 3.
 * @param onDelete    Called with the slot index to delete when the user confirms.
 * @param onDismiss   Called when the viewer is closed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomImageScreen(
    item: ZoomItem,
    allBitmaps: List<Bitmap?>,
    onDelete: (slotIndex: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    require(allBitmaps.size == 3) { "allBitmaps must have exactly 3 elements (one per slot)" }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        ZoomImageScreenContent(
            initialPage = item.index.coerceIn(0, 2),
            allBitmaps  = allBitmaps,
            onDelete    = onDelete,
            onDismiss   = onDismiss,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Screen content (split out for testability)
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ZoomImageScreenContent(
    initialPage: Int,
    allBitmaps: List<Bitmap?>,
    onDelete: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = initialPage) { 3 }
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Photo?") },
            text  = { Text("Are you sure you want to delete this photo?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(pagerState.currentPage)
                    showDeleteDialog = false
                    onDismiss()
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {

        // ── Photo pager ───────────────────────────────────────────────────
        HorizontalPager(
            state    = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val bitmap = allBitmaps.getOrNull(page)
            if (bitmap != null) {
                ZoomablePhoto(bitmap = bitmap, modifier = Modifier.fillMaxSize())
            } else {
                EmptyPhotoSlot(slotIndex = page)
            }
        }

        // ── Top bar: Close ────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            IconButton(
                onClick  = onDismiss,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                )
            }
        }

        // ── Bottom bar: page dots + Delete ────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Page indicator
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { i ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == i) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == i) Color.White
                                else Color.White.copy(alpha = 0.4f)
                            ),
                    )
                }
            }

            // Delete button (only visible when current slot has a photo)
            AnimatedVisibility(
                visible = allBitmaps.getOrNull(pagerState.currentPage) != null,
                enter   = fadeIn(),
                exit    = fadeOut(),
            ) {
                Button(
                    onClick = { showDeleteDialog = true },
                    shape   = RoundedCornerShape(16.dp),
                    colors  = ButtonDefaults.buttonColors(
                        containerColor = Color.Black.copy(alpha = 0.6f),
                        contentColor   = Color(0xFFFF5252),
                    ),
                    modifier = Modifier.height(52.dp),
                ) {
                    Icon(
                        imageVector        = Icons.Filled.Delete,
                        contentDescription = "Delete Photo",
                        modifier           = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text       = "Delete Photo",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 14.sp,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Zoomable image
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Displays a [Bitmap] with:
 * - **Pinch-to-zoom** (1x–5x)
 * - **Pan** (when scale > 1)
 * - **Double-tap** to toggle between 1x and 2.5x zoom
 *
 * Mirrors `ZoomableScrollView.swift`.
 */
@Composable
private fun ZoomablePhoto(
    bitmap: Bitmap,
    modifier: Modifier = Modifier,
) {
    var scale   by remember { mutableFloatStateOf(1f) }
    var offset  by remember { mutableStateOf(Offset.Zero) }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Image(
            bitmap           = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale     = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX      = scale
                    scaleY      = scale
                    translationX = offset.x
                    translationY = offset.y
                }
                .pointerInput(Unit) {
                    // Pinch-to-zoom + pan (concurrent with double-tap below)
                    coroutineScope {
                        launch {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = newScale
                                // Only pan when zoomed
                                if (newScale > 1f) offset += pan
                                else offset = Offset.Zero
                            }
                        }
                        launch {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale  = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 2.5f
                                    }
                                }
                            )
                        }
                    }
                },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Empty slot placeholder
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptyPhotoSlot(slotIndex: Int) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text     = "📷",
                fontSize = 48.sp,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text  = "No photo in Slot ${slotIndex + 1}",
                color = Color.Gray,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
