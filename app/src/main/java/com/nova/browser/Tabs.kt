package com.nova.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** لون ثابت لكل موقع (يُشتق من اسم النطاق) لتمييز البطاقات. */
private fun hostColor(host: String): Color = Color.hsv((host.hashCode().toLong().let { if (it < 0) -it else it } % 360).toFloat(), 0.55f, 0.72f)

/** شاشة التبويبات: شبكة بطاقات بمعاينة مصغّرة للصفحة، إغلاق فردي وإغلاق الكل، وتمرير تلقائي للتبويب الحالي. */
@Composable
fun TabSwitcher(
    tabs: List<BrowserTab>, current: Int,
    onSelect: (Int) -> Unit, onClose: (Int) -> Unit, onNew: () -> Unit, onCloseAll: () -> Unit, onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = (current / 2) * 2)
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                RoundBtn(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, L("رجوع")) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(L("التبويبات"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("" + tabs.size + L(" مفتوحة"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                TextButton(onClick = onCloseAll) { Text(L("إغلاق الكل")) }
                FilledTonalButton(onClick = onNew) { Icon(Icons.Default.Add, null); Spacer(Modifier.width(6.dp)); Text(L("جديد")) }
                Spacer(Modifier.width(8.dp))
            }
            LazyVerticalGrid(
                state = gridState, columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp, top = 4.dp)
            ) {
                itemsIndexed(tabs, key = { _, t -> t.id }) { i, t ->
                    val sel = i == current
                    val blank = t.url.isBlank()
                    val host = if (blank) "nova" else hostOf(t.url)
                    val accent = remember(host) { hostColor(host) }
                    Surface(
                        onClick = { onSelect(i) }, shape = RoundedCornerShape(26.dp), color = cs.surfaceContainerHigh,
                        border = BorderStroke(if (sel) 2.dp else 1.dp, if (sel) cs.primary else cs.outlineVariant),
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.74f).animateItem()
                    ) {
                        Column(Modifier.fillMaxSize()) {
                            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 2.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(22.dp).clip(CircleShape).background(accent), contentAlignment = Alignment.Center) {
                                    Text(host.take(1).uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    if (blank) L("صفحة البداية") else t.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { onClose(i) }, modifier = Modifier.size(38.dp)) {
                                    Icon(Icons.Default.Close, L("إغلاق"), Modifier.size(16.dp))
                                }
                            }
                            Box(
                                Modifier.weight(1f).fillMaxWidth().padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                                    .clip(RoundedCornerShape(20.dp)).background(cs.surfaceContainerHighest)
                            ) {
                                val th = t.thumb
                                if (th != null && !blank) {
                                    val img = remember(th) { th.asImageBitmap() }
                                    Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
                                } else {
                                    Box(
                                        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.30f), Color.Transparent))),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(if (blank) "✦" else host.take(1).uppercase(), fontSize = 34.sp, color = accent, fontWeight = FontWeight.Bold)
                                    }
                                }
                                if (!blank) Text(
                                    host, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(10.dp))
                                        .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
