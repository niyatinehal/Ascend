package app.levelup2027.ui.routine

import androidx.compose.runtime.Composable
import app.levelup2027.domain.Block
import app.levelup2027.domain.PresetKind
import app.levelup2027.ui.AppViewModel

/** The "describe your day" slot for [PresetEditorPanel]; null hides it. Filled in with AI drafting. */
fun describeSlot(vm: AppViewModel, kind: PresetKind): (@Composable (setBlocks: (List<Block>, List<String>) -> Unit) -> Unit)? = null
