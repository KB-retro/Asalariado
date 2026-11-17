package com.asalariado.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import android.view.WindowManager
import com.asalariado.app.data.SecureFiles
import com.asalariado.app.ui.LockScreen
import kotlinx.coroutines.Dispatchers
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import com.asalariado.app.widget.ShiftWidget
import com.asalariado.app.work.Scheduler
import kotlinx.coroutines.launch
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
import com.asalariado.app.ui.OnboardingCard
import com.asalariado.app.ui.Step
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

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    /** La app está bloqueada hasta que se pone la huella o el PIN del móvil. */
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applySecureFlag()
        if (savedInstanceState == null) handleShare(intent)
        Scheduler.schedule(this)
        // Borra copias temporales de nóminas y cifra las que quedaran de versiones anteriores.
        lifecycleScope.launch(Dispatchers.IO) { runCatching { SecureFiles.housekeeping(this@MainActivity) } }
        if (AppLock.enabled(this)) locked.value = true
        setContent {
            val isLocked by locked
            if (isLocked) {
                val ui by vm.ui.collectAsState()
                AsalariadoTheme(ui) { LockScreen(onUnlock = ::unlock) }
            } else {
                App(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Al volver tras un rato fuera, se vuelve a pedir el desbloqueo.
        if (AppLock.enabled(this) && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > AppLock.RELOCK_AFTER_MS) {
            locked.value = true
        }
        if (locked.value) unlock()
    }

    private fun unlock() {
        if (prompting) return
        if (!AppLock.available(this)) {
            // Se quitó el bloqueo del móvil: no se puede comprobar, así que no se deja la app cerrada para siempre.
            locked.value = false
            return
        }
        prompting = true
        AppLock.authenticate(this, onSuccess = { prompting = false; locked.value = false }, onFail = { prompting = false })
    }

    /** Pide la huella o el PIN para activar el bloqueo (así se comprueba que funciona). */
    fun confirmLock(onOk: () -> Unit) = AppLock.authenticate(this, onSuccess = onOk)

    /** Oculta el contenido en las apps recientes si se ha pedido (también impide hacer capturas). */
    fun applySecureFlag() {
        if (AppLock.hideInRecents(this)) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = System.currentTimeMillis()
        // Al salir de la app se refresca el widget con los últimos cambios.
        lifecycleScope.launch { runCatching { ShiftWidget().updateAll(this@MainActivity) } }
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
    Calendar("Turnos", Icons.Default.CalendarMonth),
    Medical("Médico", Icons.Default.LocalHospital),
    Payroll("Nómina", Icons.Default.Euro),
    Conditions("Contrato", Icons.Default.Tune),
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
        shared?.let { uri ->
            vm.sharedImage.value = null
            // Una nómina compartida va a Nómina; un calendario o cuadrante, a Importar.
            if (vm.isPayslipPdf(uri)) {
                tab = Tab.Payroll.ordinal
                vm.readPayslips(listOf(uri))
            } else {
                importUri = uri.toString()
                importing = true
            }
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
                                    icon = { Icon(t.icon, contentDescription = t.label) },
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
                            Tab.Calendar -> CalendarScreen(
                                vm, days, calendar, medical, stoppages, conditions, onImport = { importing = true },
                                onboarding = {
                                    val pass by vm.payslipPassword.collectAsState()
                                    OnboardingCard(
                                        listOf(
                                            Step("Elige tu escalón", "En Contrato: rellena jornal, prima y precios de la tabla.",
                                                conditions.escalon != null) { tab = Tab.Conditions.ordinal },
                                            Step("Importa el calendario laboral del año", "El PDF del convenio: festivos, vacaciones, días de 6 h…",
                                                calendar.isNotEmpty()) { importing = true },
                                            Step("Marca tu turno de esta semana", "Con los botones Mañana / Tarde de aquí abajo; las siguientes se alternan solas.",
                                                conditions.rotationAnchor != null) {},
                                            Step("Pon la contraseña de las nóminas", "En Ajustes, si os las mandan en PDF con contraseña.",
                                                pass != null) { tab = Tab.Settings.ordinal },
                                            Step("Importa tu última nómina", "En Nómina: así se ajusta el IRPF y se comprueba que cuadra.",
                                                payslips.isNotEmpty()) { tab = Tab.Payroll.ordinal },
                                        ),
                                    )
                                },
                            )
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
