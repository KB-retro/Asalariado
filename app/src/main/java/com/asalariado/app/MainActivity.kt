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
        setContent { AsalariadoTheme { App(vm) } }
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
}

@Composable
private fun App(vm: AppViewModel) {
    val days by vm.days.collectAsState()
    val calendar by vm.calendar.collectAsState()
    val medical by vm.medical.collectAsState()
    val payslips by vm.payslips.collectAsState()
    val conditions by vm.conditions.collectAsState()
    val shared by vm.sharedImage.collectAsState()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var importing by rememberSaveable { mutableStateOf(false) }
    var importUri by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(shared) {
        shared?.let {
            importUri = it.toString()
            importing = true
            vm.sharedImage.value = null
        }
    }

    if (importing) {
        BackHandler { vm.clearImport(); importing = false; importUri = null }
        ImportScreen(vm, days, conditions, importUri?.let(Uri::parse)) { importing = false; importUri = null }
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        androidx.compose.foundation.layout.Box(m) {
            when (Tab.entries[tab]) {
                Tab.Calendar -> CalendarScreen(vm, days, calendar, medical, conditions, onImport = { importing = true })
                Tab.Medical -> MedicalScreen(vm, medical, conditions)
                Tab.Payroll -> PayrollScreen(days, calendar, medical, payslips, conditions, vm::savePayslip)
                Tab.Conditions -> ConditionsScreen(conditions, vm::saveConditions)
            }
        }
    }
}
