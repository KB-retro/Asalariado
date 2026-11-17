package com.asalariado.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.asalariado.app.ui.SettingsScreen
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.asalariado.app.ui.AsalariadoTheme
import com.asalariado.app.ui.CalendarScreen
import com.asalariado.app.ui.ConditionsScreen
import com.asalariado.app.ui.ImportScreen
import com.asalariado.app.ui.MedicalScreen
import com.asalariado.app.ui.PayrollScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleShare(intent)
        setContent { App(vm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        if (uri != null) vm.sharedImage.value = uri
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Calendar("Calendario", Icons.Default.CalendarMonth),
    Medical("Médico", Icons.Default.LocalHospital),
    Payroll("Nómina", Icons.Default.Euro),
    Conditions("Condiciones", Icons.Default.Tune),
    Settings("Ajustes", Icons.Default.Settings),
}

@Composable
private fun App(vm: AppViewModel) {
    val days by vm.days.collectAsState()
    val calendar by vm.calendar.collectAsState()
    val medical by vm.medical.collectAsState()
    val payslips by vm.payslips.collectAsState()
    val extraPayslips by vm.extraPayslips.collectAsState()
    val stoppages by vm.stoppages.collectAsState()
    val conditions by vm.conditions.collectAsState()
    val shared by vm.sharedImage.collectAsState()
    val ui by vm.ui.collectAsState()
    val message by vm.message.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var importUri by rememberSaveable { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(shared) {
        shared?.let {
            importUri = it.toString()
            importing = true
            vm.sharedImage.value = null
        }
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.message.value = null }
    }

    AsalariadoTheme(ui) {
        AnimatedContent(
            targetState = importing,
            transitionSpec = {
                (slideInVertically { it / 6 } + fadeIn()) togetherWith (fadeOut())
            },
            label = "import",
        ) { isImporting ->
            if (isImporting) {
                BackHandler { vm.clearImport(); importing = false; importUri = null }
                ImportScreen(vm, days, conditions, importUri?.let(Uri::parse)) { importing = false; importUri = null }
            } else {
                Scaffold(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onBackground,
                    snackbarHost = { SnackbarHost(snackbar) },
                    bottomBar = {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.95f)) {
                            Tab.entries.forEachIndexed { i, t ->
                                NavigationBarItem(
                                    selected = tab == i,
                                    onClick = { tab = i },
                                    icon = { Icon(t.icon, null) },
                                    label = { Text(t.label, maxLines = 1) },
                                    alwaysShowLabel = false,
                                )
                            }
                        }
                    },
                ) { pad ->
                    AnimatedContent(
                        targetState = tab,
                        modifier = Modifier.padding(pad),
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally { w -> dir * w / 5 } + fadeIn()) togetherWith
                                (slideOutHorizontally { w -> -dir * w / 5 } + fadeOut())
                        },
                        label = "tabs",
                    ) { t ->
                        when (Tab.entries[t]) {
                            Tab.Calendar -> CalendarScreen(vm, days, calendar, medical, stoppages, conditions, onImport = { importing = true })
                            Tab.Medical -> MedicalScreen(vm, medical, conditions)
                            Tab.Payroll -> PayrollScreen(vm, days, calendar, medical, payslips, extraPayslips, stoppages, conditions, vm::savePayslip)
                            Tab.Conditions -> ConditionsScreen(conditions, vm::saveConditions)
                            Tab.Settings -> SettingsScreen(vm, ui)
                        }
                    }
                }
            }
        }
    }
}
