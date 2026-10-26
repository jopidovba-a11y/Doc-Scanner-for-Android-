package com.example.docscanner
import android.app.Activity
import android.content.Intent
import androidx.compose.runtime.saveable.rememberSaveable
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

@Composable
fun App(vm: ScanViewModel = viewModel()) {
    var editing by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().systemBarsPadding()) {
        if (editing) EditorScreen(vm, onBack = { editing = false })
        else ScanScreen(vm, onNext = { editing = true })
    }
}

// ======================= Screen 1: scan =======================

@Composable
fun ScanScreen(vm: ScanViewModel, onNext: () -> Unit) {
    val activity = LocalContext.current as ComponentActivity

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            GmsDocumentScanningResult.fromActivityResultIntent(result.data)
                ?.pages?.forEach { vm.importUri(it.imageUri) }
        }
    }

    fun startScan() {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(20)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { launcher.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener {
                Toast.makeText(activity, "Scanner unavailable: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Doc Scanner", style = MaterialTheme.typography.headlineSmall)
        Button(onClick = { startScan() }, modifier = Modifier.fillMaxWidth()) {
            Text(if (vm.photos.isEmpty()) "Scan documents" else "Scan more")
        }
        Text("${vm.photos.size} scan(s). Tap ✕ to remove one.", style = MaterialTheme.typography.bodySmall)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            gridItems(vm.photos, key = { it.id }) { photo ->
                PhotoThumb(vm, photo, 110.dp, onClick = {}, onRemove = { vm.removePhoto(photo) })
            }
        }

        Button(
            onClick = onNext,
            enabled = vm.photos.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("Next: arrange pages") }
    }
}

@Composable
fun PhotoThumb(vm: ScanViewModel, photo: Photo, size: Dp, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    Box(
        Modifier.size(size).background(Color(0xFFEEEEEE)).clickable(onClick = onClick)
    ) {
        val bmp = remember(photo.id) { vm.bitmap(photo.id, 0, 300) }
        if (bmp != null) {
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        if (onRemove != null) {
            Text(
                "✕",
                Modifier.align(Alignment.TopEnd).clickable(onClick = onRemove)
                    .background(Color(0xAAFFFFFF)).padding(6.dp)
            )
        }
    }
}

// ======================= Screen 2: arrange + export =======================

@Composable
fun EditorScreen(vm: ScanViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var splitFiles by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val page = vm.pages[vm.currentPage]
    val sel = vm.selectedItem

    Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {

        // Page navigation
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← Scans") }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { vm.goToPage(vm.currentPage - 1) }, enabled = vm.currentPage > 0) { Text("◀") }
            Text("Page ${vm.currentPage + 1}/${vm.pages.size}")
            TextButton(
                onClick = { vm.goToPage(vm.currentPage + 1) },
                enabled = vm.currentPage < vm.pages.lastIndex
            ) { Text("▶") }
            TextButton(onClick = { vm.addPage() }) { Text("+ Page") }
            TextButton(onClick = { vm.deletePage() }, enabled = vm.pages.size > 1) { Text("Delete") }
        }

        // Page canvas
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            key(page.id) { PageCanvas(vm, page, Modifier.aspectRatio(595f / 842f)) }
        }

        // Controls for the selected image
        if (sel != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = {
                    vm.updateItem(sel.copy(rotation = (sel.rotation + 90) % 360))
                }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Rotate") }
                OutlinedButton(onClick = {
                    val bmp = vm.bitmap(sel.photoId, sel.rotation, 400)
                    val mm = if (bmp != null && bmp.width >= bmp.height) 85.6f else 54f
                    vm.updateItem(sel.copy(width = mm / 210f)) // A4 width = 210 mm
                }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("ID size") }
                OutlinedButton(onClick = {
                    vm.updateItem(sel.copy(cx = 0.5f))
                }, contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Center") }
                OutlinedButton(onClick = { vm.deleteSelected() },
                    contentPadding = PaddingValues(horizontal = 8.dp)) { Text("Remove") }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${(sel.width * 210).roundToInt()} mm", Modifier.width(60.dp))
                Slider(
                    value = sel.width,
                    onValueChange = { vm.updateItem(sel.copy(width = it)) },
                    valueRange = 0.05f..1f
                )
            }
        } else {
            Text("Tap a scan below to add it to this page. Drag to move, pinch to resize.",
                style = MaterialTheme.typography.bodySmall)
        }

        // Scan tray
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.photos, key = { it.id }) { photo ->
                PhotoThumb(vm, photo, 72.dp, onClick = { vm.placeOnCurrentPage(photo) })
            }
        }

        // Export
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = splitFiles, onCheckedChange = { splitFiles = it })
            Text("One PDF per page", Modifier.weight(1f))
            Button(enabled = !exporting, onClick = {
                scope.launch {
                    exporting = true
                    try {
                        val uris = withContext(Dispatchers.IO) {
                            PdfExporter.export(context, vm.photos.toList(), vm.pages.toList(), splitFiles)
                        }
                        if (uris.isEmpty()) {
                            Toast.makeText(context, "Nothing to export", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Saved to Downloads", Toast.LENGTH_SHORT).show()
                            val send = Intent(
                                if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
                            ).apply {
                                type = "application/pdf"
                                if (uris.size == 1) putExtra(Intent.EXTRA_STREAM, uris[0])
                                else putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(send, "Share PDF"))
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    exporting = false
                }
            }) { Text(if (exporting) "Exporting…" else "Export PDF") }
        }
    }
}

@Composable
fun PageCanvas(vm: ScanViewModel, page: PdfPage, modifier: Modifier) {
    BoxWithConstraints(
        modifier
            .background(Color.White)
            .border(1.dp, Color.Gray)
            .clipToBounds()
            .pointerInput(Unit) { detectTapGestures { vm.selectedId = null } }
    ) {
        val pageW = constraints.maxWidth.toFloat()
        val pageH = constraints.maxHeight.toFloat()
        page.items.forEach { item ->
            key(item.id) { PlacedImageView(vm, item, pageW, pageH) }
        }
    }
}

@Composable
fun PlacedImageView(vm: ScanViewModel, item: PlacedImage, pageW: Float, pageH: Float) {
    val bmp = remember(item.photoId, item.rotation) { vm.bitmap(item.photoId, item.rotation, 1200) } ?: return
    val current by rememberUpdatedState(item) // gesture lambdas must see the latest item
    val density = LocalDensity.current

    val w = item.width * pageW
    val h = w * bmp.height / bmp.width
    val selected = vm.selectedId == item.id

    Image(
        bitmap = bmp.asImageBitmap(),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier = Modifier
            .offset { IntOffset((item.cx * pageW - w / 2).roundToInt(), (item.cy * pageH - h / 2).roundToInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .then(if (selected) Modifier.border(2.dp, Color(0xFF1E88E5)) else Modifier)
            .pointerInput(item.id, pageW, pageH) {
                detectTapGestures { vm.selectedId = current.id }
            }
            .pointerInput(item.id, pageW, pageH) {
                detectTransformGestures { _, pan, zoom, _ ->
                    vm.selectedId = current.id
                    vm.updateItem(
                        current.copy(
                            cx = current.cx + pan.x / pageW,
                            cy = current.cy + pan.y / pageH,
                            width = (current.width * zoom).coerceIn(0.05f, 1.5f)
                        )
                    )
                }
            }
    )
}