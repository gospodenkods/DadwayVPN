package ru.dadway.xrayv2

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SearchView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import java.util.Locale

class AppRoutingActivity : AppCompatActivity() {
    private data class AppEntry(val label: String, val packageName: String, val icon: Drawable)

    private lateinit var adapter: AppsAdapter
    private lateinit var selectedCount: TextView
    private var mode = AppRoutingMode.ALL
    private val selectedPackages = linkedSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        applySavedTheme()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_app_routing)
        applySystemInsets()

        val saved = AppRoutingStore.read(this)
        mode = saved.mode
        selectedPackages += saved.packages
        selectedCount = findViewById(R.id.selectedAppsCount)
        adapter = AppsAdapter(loadLaunchableApps())
        findViewById<ListView>(R.id.installedAppsList).adapter = adapter

        findViewById<ImageButton>(R.id.appRoutingBackButton).setOnClickListener { finish() }
        val modeGroup = findViewById<RadioGroup>(R.id.routingModeGroup)
        findViewById<RadioButton>(
            if (mode == AppRoutingMode.ALL) R.id.allAppsRadio else R.id.selectedAppsRadio
        ).isChecked = true
        modeGroup.setOnCheckedChangeListener { _, checkedId ->
            mode = if (checkedId == R.id.selectedAppsRadio) AppRoutingMode.SELECTED else AppRoutingMode.ALL
            adapter.notifyDataSetChanged()
            updateSelectedCount()
        }
        findViewById<SearchView>(R.id.appsSearch).setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter(newText.orEmpty())
                return true
            }
        })
        findViewById<MaterialButton>(R.id.saveAppRoutingButton).setOnClickListener {
            if (mode == AppRoutingMode.SELECTED && selectedPackages.isEmpty()) {
                Toast.makeText(this, "Выберите хотя бы одно приложение", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            AppRoutingStore.save(this, mode, selectedPackages)
            setResult(RESULT_OK)
            Toast.makeText(this, "Настройки маршрутизации сохранены", Toast.LENGTH_SHORT).show()
            finish()
        }
        updateSelectedCount()
    }

    private fun loadLaunchableApps(): List<AppEntry> {
        val launcherIntents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),
        )
        return launcherIntents.asSequence()
            .flatMap(::queryActivities)
            .mapNotNull { info ->
                val packageName = info.activityInfo?.packageName ?: return@mapNotNull null
                if (packageName == this.packageName) return@mapNotNull null
                AppEntry(
                    label = info.loadLabel(packageManager).toString().ifBlank { packageName },
                    packageName = packageName,
                    icon = info.loadIcon(packageManager),
                )
            }
            .distinctBy(AppEntry::packageName)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, AppEntry::label))
            .toList()
    }

    private fun queryActivities(intent: Intent) = if (Build.VERSION.SDK_INT >= 33) {
        packageManager.queryIntentActivities(
            intent,
            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()),
        ).asSequence()
    } else {
        @Suppress("DEPRECATION")
        packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL).asSequence()
    }

    private fun updateSelectedCount() {
        selectedCount.text = if (mode == AppRoutingMode.ALL) "Все" else "Выбрано: ${selectedPackages.size}"
    }

    private fun applySavedTheme() {
        val savedMode = getSharedPreferences("dadway_ui", MODE_PRIVATE)
            .getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        if (AppCompatDelegate.getDefaultNightMode() != savedMode) {
            AppCompatDelegate.setDefaultNightMode(savedMode)
        }
    }

    private fun applySystemInsets() {
        val root = findViewById<View>(R.id.appRoutingRoot)
        val start = root.paddingStart
        val top = root.paddingTop
        val end = root.paddingEnd
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val safe = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            view.updatePadding(start + safe.left, top + safe.top, end + safe.right, bottom + safe.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    private inner class AppsAdapter(private val allApps: List<AppEntry>) : BaseAdapter() {
        private var visibleApps = allApps

        fun filter(query: String) {
            val normalized = query.trim().lowercase(Locale.getDefault())
            visibleApps = if (normalized.isEmpty()) allApps else allApps.filter {
                it.label.lowercase(Locale.getDefault()).contains(normalized) ||
                    it.packageName.lowercase(Locale.US).contains(normalized)
            }
            notifyDataSetChanged()
        }

        override fun getCount(): Int = visibleApps.size
        override fun getItem(position: Int): AppEntry = visibleApps[position]
        override fun getItemId(position: Int): Long = getItem(position).packageName.hashCode().toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: LayoutInflater.from(parent.context)
                .inflate(R.layout.item_routed_app, parent, false)
            val app = getItem(position)
            val toggle = view.findViewById<SwitchMaterial>(R.id.routedAppSwitch)
            view.findViewById<ImageView>(R.id.routedAppIcon).setImageDrawable(app.icon)
            view.findViewById<TextView>(R.id.routedAppName).text = app.label
            view.findViewById<TextView>(R.id.routedAppPackage).text = app.packageName
            toggle.setOnCheckedChangeListener(null)
            toggle.isChecked = app.packageName in selectedPackages
            toggle.isEnabled = mode == AppRoutingMode.SELECTED
            view.alpha = if (mode == AppRoutingMode.SELECTED) 1f else 0.72f
            toggle.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedPackages += app.packageName else selectedPackages -= app.packageName
                updateSelectedCount()
            }
            view.setOnClickListener {
                if (mode == AppRoutingMode.SELECTED) toggle.isChecked = !toggle.isChecked
            }
            return view
        }
    }
}
